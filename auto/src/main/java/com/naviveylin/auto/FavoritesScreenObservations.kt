package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.FavoritesSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * What the car favorites list observes (spec: auto/screen-observation; design D6).
 *
 * This owns *what* is observed and [CarScreenObservations] owns *how long* it lives: the screen
 * starts this in `onStart` and stops it in `onStop`, so the list state is observed exactly once per
 * started period and a stopped screen issues no template invalidation. The observed sources are the
 * favorites, the stored group order, the stored starred order and the store's readiness; the two orders
 * ride the same observation as the favorites because all four are combined into one list state, and the
 * order flows are separate sources for a reason — a reorder leaves the group map's contents equal, so
 * the map flow may not re-emit (`auto-favorites` — AA place list follows the stored group order).
 *
 * **The provider is resolved on [backgroundDispatcher], never on the dispatcher this body runs on.**
 * This body executes on the observation scope's main dispatcher, which is the car-app host thread, and
 * the first touch of the favorites provider resolves the Hilt `OSMScoutClient` singleton — stylesheet
 * sync, `System.loadLibrary`, the native client build (`MapDownloadModule.provideOSMScoutClient`). A
 * session that opens this screen before the warmup finished that build would otherwise block the host
 * thread on the singleton for seconds, showing the loading row for exactly that time
 * (spec: auto-map-renderer — "A screen observation resolves its provider off the host thread";
 * design D1). The parameter mirrors `MapScreen`'s `backgroundDispatcher`.
 *
 * **The observation establishes the store it reads** (issue #995; spec `auto-favorites` — The observation
 * establishes the store it reads): the readiness this screen renders is produced by the store's one `init`
 * call, whose only car caller used to be the session's warmup step. A warmup that timed out before that
 * step, was cancelled or failed left the process with an unready store, so every favourites screen showed
 * the loading row for its whole lifetime. The hop therefore asks [AutoFavoritesProvider.storeReady] first
 * and, while it is false, calls [AutoFavoritesProvider.init] with [favoritesPath] — the same file the
 * session's warmup opens — once for the started period, on the same background hop that resolves the
 * provider, never on the host thread.
 *
 * **A failed hop is retried once** (spec `auto-favorites` — A failed resolution is retried once before the
 * screen is told): [CarScreenObservations] confines a fault to its observation but does not repeat it, so a
 * single transient failure used to wedge the started period on the loading row. The hop is attempted twice;
 * the fault is logged with the observation's key, a second failure ends the observation and the screen is
 * handed the failure state so it can report the store as unavailable rather than as still loading. A
 * cancellation is never retried: it is the period ending, not a fault.
 *
 * **The resolved state is published before the collection starts.** The resolution has just ended, so
 * the values the flows hold are the store's real state; publishing them here — rather than only through
 * the combined flow's first emission — is what keeps a ready store from being rendered as loading for
 * the width of a channel hop (design D3). The collection then carries later changes; its own first
 * emission repeats the state just published, which is idempotent for the screen.
 *
 * **An already-resolved store's state is published before the hop, not after it.** The hop is a
 * `withContext` onto a shared background dispatcher, and on a cold session the map's first frame
 * (stylesheet and type config parse, tile retrieval, bitmap render) holds those threads and the CPU:
 * measured on the AAOS emulator, `favorites resolution entered` arrived 623–1715 ms after
 * `favorites observation start` while the store had been ready for seconds, so the loading row stayed on
 * screen although the list was known (issue #110). The provider can hand out that state without a
 * dispatch ([AutoFavoritesProvider.snapshotIfResolved]), so the screen publishes it at the start of the
 * period; the hop's own publish then repeats it and `publishOnce` drops the repetition. The hop still
 * runs and still establishes the collection — nothing here resolves the provider on the host thread.
 *
 * The effects stay on the screen (they write its state fields and call `invalidate()`, which is
 * main-thread only), so they arrive as one callback — this class is testable without a `CarContext`.
 *
 * A change received while the screen is stopped is not lost: all sources are `StateFlow`s, so the
 * observation re-established on the next start delivers the current value once (`auto-favorites` — a
 * reorder received while the list is stopped is applied on the next start).
 */
internal class FavoritesScreenObservations(
    private val observations: CarScreenObservations,
    private val favoritesProvider: AutoFavoritesProvider,
    /**
     * The favourites file the screen initialises an unready store with: the same path the session's
     * warmup opens, so the observation and the warmup read one store and cannot drift apart (issue #995).
     */
    private val favoritesPath: String,
    /**
     * Dispatcher of the hop that touches the native client. Defaults to [Dispatchers.Default];
     * injectable so a case can own the hop it asserts (design D1).
     */
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onFavorites: (FavoritesListState) -> Unit
) {

    /** Establish the screen's observation for the started period that began. */
    fun start() {
        observations.start()
        observations.observe(KEY_FAVORITES) {
            val startedAt = System.currentTimeMillis()
            // Coordinate-free measurement of the wait this observation carries: the start of a started
            // period and the moment the store's state reaches the screen (identity, counts and durations
            // only — `guidelines/Logging.md` §3, spec: auto-diagnostics).
            DiagnosticsLog.log(CAR_SCREEN_DIAG_TAG, "favorites observation start")
            // The store's state as the observation leaves it, so the first template after this start
            // carries it (design D3). `publishOnce` keeps a repeated state from reaching the screen as a
            // second refresh — one applied state per start, which is what the observation's "exactly
            // once" guarantee counts.
            var publishedAt = 0L
            var lastPublished: FavoritesListState? = null
            fun publishOnce(state: FavoritesListState) {
                if (state == lastPublished) return
                lastPublished = state
                if (publishedAt == 0L) {
                    publishedAt = System.currentTimeMillis()
                    DiagnosticsLog.log(
                        CAR_SCREEN_DIAG_TAG,
                        "favorites state published after ${publishedAt - startedAt}ms " +
                            "(storeReady=${state.storeReady}, failed=${state.failed}, " +
                            "groups=${state.favorites.size}, " +
                            "favorites=${state.favorites.values.sumOf { it.size }}, " +
                            "starred=${state.starredOrder.size})"
                    )
                }
                publish(state)
            }

            // A resolved provider's current state needs no dispatch, and the hop is exactly what the
            // map's first frame delays: publishing it here is what keeps the first template off the
            // loading row when the store is already ready (issue #110). An unresolved provider answers
            // null, so nothing is invented and the loading row stands until the hop's own publish.
            favoritesProvider.snapshotIfResolved()?.let { publishOnce(FavoritesListState.of(it)) }

            val sources = resolveSources(startedAt)
            if (sources == null) {
                // The observation cannot produce a state, so it reports that instead of leaving the
                // screen on a loading row that would never change (issue #995, spec `auto-favorites` —
                // A store that cannot be observed is reported, not left loading).
                publishOnce(FavoritesListState(failed = true))
                return@observe
            }
            DiagnosticsLog.log(
                CAR_SCREEN_DIAG_TAG,
                "favorites resolution done after ${System.currentTimeMillis() - startedAt}ms " +
                    "(thread=${Thread.currentThread().name})"
            )
            publishOnce(FavoritesListState.of(sources))
            combine(
                sources.favorites,
                sources.groupOrder,
                sources.starredOrder,
                sources.storeReady
            ) { favorites, order, starred, ready ->
                FavoritesListState(favorites, order, starred, ready)
            }.collect { publishOnce(it) }
        }
    }

    /**
     * One started period's resolution, attempted twice: the sources of the first hop that does not fault,
     * or null once both attempts faulted.
     *
     * The retry is the second half of issue #995 and belongs to the same observation: a fault is confined
     * by [CarScreenObservations] but was never repeated, so one transient failure wedged the period. Each
     * attempt is logged with its own identity (`guidelines/Logging.md` §3 — counts, names and durations,
     * never coordinates); a cancellation is rethrown rather than retried, because it is the period ending.
     */
    private suspend fun resolveSources(startedAt: Long): FavoritesSources? {
        val first = attemptResolution(startedAt, attempt = 1)
        if (first != null) return first
        DiagnosticsLog.log(CAR_SCREEN_DIAG_TAG, "favorites resolution retrying once")
        val second = attemptResolution(startedAt, attempt = 2)
        if (second == null) {
            DiagnosticsLog.log(
                CAR_SCREEN_DIAG_TAG,
                "favorites resolution failed twice after ${System.currentTimeMillis() - startedAt}ms; " +
                    "reporting the store unavailable"
            )
        }
        return second
    }

    /** One hop's resolution: the sources, or null when this attempt faulted. */
    private suspend fun attemptResolution(startedAt: Long, attempt: Int): FavoritesSources? =
        try {
            withContext(backgroundDispatcher) {
                DiagnosticsLog.log(
                    CAR_SCREEN_DIAG_TAG,
                    "favorites resolution entered after ${System.currentTimeMillis() - startedAt}ms " +
                        "(attempt=$attempt, thread=${Thread.currentThread().name})"
                )
                // The readiness the screen renders, and the reason this hop exists: a store whose
                // readiness no warmup produced is established here, once for this started period.
                val ready = favoritesProvider.storeReady()
                if (!ready.value) {
                    DiagnosticsLog.log(
                        CAR_SCREEN_DIAG_TAG,
                        "favorites store unready after ${System.currentTimeMillis() - startedAt}ms; " +
                            "initialising (thread=${Thread.currentThread().name})"
                    )
                    val initialised = favoritesProvider.init(favoritesPath)
                    DiagnosticsLog.log(
                        CAR_SCREEN_DIAG_TAG,
                        "favorites initialisation returned $initialised after " +
                            "${System.currentTimeMillis() - startedAt}ms " +
                            "(storeReady=${ready.value})"
                    )
                }
                FavoritesSources(
                    favorites = favoritesProvider.favoriteLocations(),
                    groupOrder = favoritesProvider.groupOrder(),
                    starredOrder = favoritesProvider.starredOrder(),
                    storeReady = ready
                )
            }
        } catch (e: CancellationException) {
            // The started period ended. Not a fault, and not this observation's to report.
            throw e
        } catch (e: Exception) {
            DiagnosticsLog.log(
                CAR_SCREEN_DIAG_TAG,
                "favorites resolution attempt $attempt failed after " +
                    "${System.currentTimeMillis() - startedAt}ms (${e.javaClass.simpleName})"
            )
            null
        }

    /** One applied state: every source rides one emission, so the screen never renders a torn list. */
    private fun publish(state: FavoritesListState) {
        onFavorites(state)
    }

    private companion object {
        /**
         * One key for the whole list state: the sources are combined into the one list the screen
         * renders (design D6), so the screen holds one observation rather than several collectors that
         * would have to agree before a rebuild.
         */
        const val KEY_FAVORITES = "favorites"
    }
}

/**
 * The favourites file name the session's warmup and the screen's observation open — one declaration, so
 * the two call sites cannot drift apart (issue #995). The file lives in the application's `filesDir`.
 */
internal const val FAVORITES_FILE = "favorites.json"

/** The resolved flows of one started period, held so the resolution happens once per start. */
internal class FavoritesSources(
    val favorites: StateFlow<Map<String, List<FavoriteLocation>>>,
    val groupOrder: StateFlow<List<String>>,
    val starredOrder: StateFlow<List<StarredFavoriteLocation>>,
    val storeReady: StateFlow<Boolean>
)

/**
 * One state of the car favourites list: the four observed sources, plus whether the observation could not
 * produce them at all (`failed`, issue #995). Equal states are the same state (see `publishOnce`).
 *
 * The failure state is a state of the list rather than an absent one, because the two mean different rows:
 * "the store is initialising" is the loading row, and no loading row may outlive the observation that would
 * have ended it (spec `auto-favorites` — A store that cannot be observed is reported, not left loading).
 */
internal data class FavoritesListState(
    val favorites: Map<String, List<FavoriteLocation>> = emptyMap(),
    val groupOrder: List<String> = emptyList(),
    val starredOrder: List<StarredFavoriteLocation> = emptyList(),
    val storeReady: Boolean = false,
    val failed: Boolean = false
) {
    companion object {
        /** The state the resolved flows already hold, without waiting for a combined emission. */
        fun of(sources: FavoritesSources): FavoritesListState = FavoritesListState(
            favorites = sources.favorites.value,
            groupOrder = sources.groupOrder.value,
            starredOrder = sources.starredOrder.value,
            storeReady = sources.storeReady.value
        )

        /** The state a resolved provider handed out, without a dispatch (issue #110). */
        fun of(snapshot: FavoritesSnapshot): FavoritesListState = FavoritesListState(
            favorites = snapshot.favorites,
            groupOrder = snapshot.groupOrder,
            starredOrder = snapshot.starredOrder,
            storeReady = snapshot.storeReady
        )
    }
}