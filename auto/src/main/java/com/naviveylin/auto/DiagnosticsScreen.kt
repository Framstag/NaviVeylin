package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.auto.R
import com.naviveylin.core.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto diagnostics screen: shows the most recent log entries
 * (crash traces + session events) newest-first so head-unit failures can be
 * inspected from the car without adb.
 *
 * The log file is read **on a background dispatcher** and the result published
 * into this screen's state (spec: auto-diagnostics — Reading diagnostics does not
 * block the UI): `onGetTemplate` is a car host callback, and reading the file
 * there put filesystem work on the host's answering path.
 *
 * The screen is a car **list**, not a pane, and presents at most the row capacity the connected host
 * displays, keeping the rest of its recent-entries window behind its page row (spec:
 * `car-list-row-limit` — A car list never presents more rows than the host displays; Items beyond the
 * capacity stay reachable; design D1/D2/D3). Two measured facts force that shape: a host silently ignores
 * the rows over its list capacity (the library's own contract, and the AAOS host's measured number is
 * 100 while the library's floor is 6), and `PaneTemplate` rows are **not actionable** and do not scroll,
 * so a pane can neither show the 20-entry window nor offer the remainder (`guidelines/UI.md` §3).
 *
 * The 20-entry window itself is deliberate and unchanged: the diagnostics file holds the full history,
 * and this screen is a bounded recent-entries view of it (`guidelines/Logging.md` §3).
 */
class DiagnosticsScreen(
    carContext: CarContext,
    /** The page of the recent-entries window to show; 0 is its newest page (spec: `car-list-row-limit`). */
    private val page: Int = 0
) : Screen(carContext) {

    private val scope = carScreenScope("DiagnosticsScreen")
    private var loadJob: Job? = null

    /** Loaded entries, newest first; null until the first background load returns. */
    private var entries: List<String>? = null

    /**
     * The row capacity this screen presents (spec: `car-list-row-limit`; design D2). Starts at the library
     * default so the first template — which the host may ask for before the query returns — is already
     * bounded, and is replaced by the host's answer on the screen's own scope.
     */
    private var rowCapacity: Int = CarListCapacityReader.LIBRARY_DEFAULT

    init {
        enableBackNavigation()
        loadEntries()
        readRowCapacity()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                loadJob?.cancel()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /**
     * Ask the host for this screen's row capacity, off the host thread, and re-render with the answer
     * (spec: `car-list-row-limit` — A car list never presents more rows than the host displays; design D2).
     * A host below car API 2, a host whose query fails and an unusable answer all take the library's
     * default instead ([CarListCapacityReader]), which the reader records as a coordinate-free diagnostics
     * line. Every page row opens a new instance of this screen, so each page asks for itself.
     */
    private fun readRowCapacity() {
        scope.launch {
            val capacity = withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }
            if (capacity != rowCapacity) {
                rowCapacity = capacity
                invalidate()
            }
        }
    }

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        // Never read the file here: this runs as a host callback on the main
        // thread, and the entries are already published into [entries].
        val loaded = entries
        val itemList = ItemList.Builder()

        // Disclosure first (spec: auto-diagnostics — Export and viewers disclose the log
        // contents): the driver must be able to read what this log holds before scrolling
        // entries. Same wording as the phone dialog, from the shared `:core` string.
        itemList.addItem(
            Row.Builder()
                .setTitle(carContext.getString(com.naviveylin.core.R.string.diagnostics_disclosure))
                .build()
        )

        when {
            loaded == null -> itemList.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.loading))
                    .build()
            )

            loaded.isEmpty() -> itemList.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.no_log_entries))
                    .build()
            )

            else -> {
                // The recent-entries window, capped at the host's capacity, with the entries this page
                // does not show counted in its final row (spec: `car-list-row-limit` — A car list never
                // presents more rows than the host displays; Items beyond the capacity stay reachable).
                // The arithmetic is the mapper's, so the pages partition the window: every shown entry is
                // reachable exactly once.
                val window = FavoritesScreenMapper.page(loaded, capacity = rowCapacity, page = page)
                for (entry in window.rows) {
                    itemList.addItem(
                        Row.Builder()
                            .setTitle(entry.take(MAX_TITLE_CHARS))
                            .build()
                    )
                }
                if (window.hasFurtherPage) {
                    itemList.addItem(pageRow(window.remaining))
                }
            }
        }

        val refreshAction = Action.Builder()
            .setTitle(carContext.getString(R.string.refresh))
            .setOnClickListener { loadEntries() }
            .build()

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.diagnostics))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(refreshAction)
                    .build()
            )
            .build()
    }

    /**
     * The final row of a page whose window holds more entries than it shows: it names how many entries it
     * does not show and opens the next page of the same window (spec: `car-list-row-limit` — The final row
     * opens the next page; design D3).
     *
     * The next page is a pushed screen of the same class, so the host's back navigation returns to the page
     * that opened it (spec: "Back navigation returns to the previous page").
     */
    private fun pageRow(remaining: Int): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.more_items, remaining))
        .setOnClickListener {
            armScreenPush(carContext, scope, "DiagnosticsScreen (page)") {
                DiagnosticsScreen(carContext, page = page + 1)
            }
        }
        .build()

    /** (Re)load the newest entries off the main thread and refresh the template. */
    private fun loadEntries() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val loaded = DiagnosticsLog.readEntriesAsync().takeLast(MAX_ROWS).asReversed()
            entries = loaded
            invalidate()
        }
    }

    companion object {
        /** The recent-entries window this screen shows, across all its pages (`guidelines/Logging.md` §3). */
        private const val MAX_ROWS = 20
        private const val MAX_TITLE_CHARS = 200
    }
}
