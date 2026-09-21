package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.auto.R
import com.naviveylin.core.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Auto diagnostics screen: shows the most recent log entries
 * (crash traces + session events) newest-first so head-unit failures can be
 * inspected from the car without adb.
 *
 * The log file is read **on a background dispatcher** and the result published
 * into this screen's state (spec: auto-diagnostics — Reading diagnostics does not
 * block the UI): `onGetTemplate` is a car host callback, and reading the file
 * there put filesystem work on the host's answering path.
 */
class DiagnosticsScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    /** Loaded entries, newest first; null until the first background load returns. */
    private var entries: List<String>? = null

    init {
        enableBackNavigation()
        loadEntries()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                loadJob?.cancel()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): PaneTemplate = carPaneTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carPaneTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): PaneTemplate {
        // Never read the file here: this runs as a host callback on the main
        // thread, and the entries are already published into [entries].
        val loaded = entries
        val builder = Pane.Builder()

        when {
            loaded == null -> builder.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.loading))
                    .build()
            )

            loaded.isEmpty() -> builder.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.no_log_entries))
                    .build()
            )

            else -> loaded.forEach { line ->
                builder.addRow(
                    Row.Builder()
                        .setTitle(line.take(MAX_TITLE_CHARS))
                        .build()
                )
            }
        }

        val refreshAction = Action.Builder()
            .setTitle(carContext.getString(R.string.refresh))
            .setOnClickListener { loadEntries() }
            .build()

        return PaneTemplate.Builder(builder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.diagnostics))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(refreshAction)
                    .build()
            )
            .build()
    }

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
        private const val MAX_ROWS = 20
        private const val MAX_TITLE_CHARS = 200
    }
}
