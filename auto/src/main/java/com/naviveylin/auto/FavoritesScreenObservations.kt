package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.DiagnosticsLog
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
 * **The resolved state is published before the collection starts.** The resolution has just ended, so
 * the values the flows hold are the store's real state; publishing them here — rather than only through
 * the combined flow's first emission — is what keeps a ready store from being rendered as loading for
 * the width of a channel hop (design D3). The collection then carries later changes; its own first
 * emission repeats the state just published, which is idempotent for the screen.
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
     * Dispatcher of the hop that touches the native client. Defaults to [Dispatchers.Default];
     * injectable so a case can own the hop it asserts (design D1).
     */
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onFavorites: (
        favorites: Map<String, List<FavoriteLocation>>,
        groupOrder: List<String>,
        starredOrder: List<StarredFavoriteLocation>,
        storeReady: Boolean
    ) -> Unit
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
            val sources = withContext(backgroundDispatcher) {
                DiagnosticsLog.log(
                    CAR_SCREEN_DIAG_TAG,
                    "favorites resolution entered after ${System.currentTimeMillis() - startedAt}ms " +
                        "(thread=${Thread.currentThread().name})"
                )
                FavoritesSources(
                    favorites = favoritesProvider.favoriteLocations(),
                    groupOrder = favoritesProvider.groupOrder(),
                    starredOrder = favoritesProvider.starredOrder(),
                    storeReady = favoritesProvider.storeReady()
                )
            }
            DiagnosticsLog.log(
                CAR_SCREEN_DIAG_TAG,
                "favorites resolution done after ${System.currentTimeMillis() - startedAt}ms " +
                    "(thread=${Thread.currentThread().name})"
            )
            // The store's state as the resolution left it: the current value of every source, so the
            // first template after this start carries it (design D3). The collection's own first
            // emission repeats this state, and `publishOnce` is what keeps the repetition from reaching
            // the screen as a second refresh — one applied state per start, which is what the
            // observation's "exactly once" guarantee counts.
            var publishedAt = 0L
            var lastPublished: FavoritesState? = null
            fun publishOnce(state: FavoritesState) {
                if (state == lastPublished) return
                lastPublished = state
                if (publishedAt == 0L) {
                    publishedAt = System.currentTimeMillis()
                    DiagnosticsLog.log(
                        CAR_SCREEN_DIAG_TAG,
                        "favorites state published after ${publishedAt - startedAt}ms " +
                            "(storeReady=${state.storeReady}, groups=${state.favorites.size}, " +
                            "favorites=${state.favorites.values.sumOf { it.size }}, " +
                            "starred=${state.starredOrder.size})"
                    )
                }
                publish(state)
            }

            publishOnce(FavoritesState.of(sources))
            combine(
                sources.favorites,
                sources.groupOrder,
                sources.starredOrder,
                sources.storeReady
            ) { favorites, order, starred, ready ->
                FavoritesState(favorites, order, starred, ready)
            }.collect { publishOnce(it) }
        }
    }

    /** One applied state: every source rides one emission, so the screen never renders a torn list. */
    private fun publish(state: FavoritesState) {
        onFavorites(state.favorites, state.groupOrder, state.starredOrder, state.storeReady)
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

/** The resolved flows of one started period, held so the resolution happens once per start. */
private class FavoritesSources(
    val favorites: StateFlow<Map<String, List<FavoriteLocation>>>,
    val groupOrder: StateFlow<List<String>>,
    val starredOrder: StateFlow<List<StarredFavoriteLocation>>,
    val storeReady: StateFlow<Boolean>
)

/** One combined emission of the list state; equal states are the same state (see `publishOnce`). */
private data class FavoritesState(
    val favorites: Map<String, List<FavoriteLocation>>,
    val groupOrder: List<String>,
    val starredOrder: List<StarredFavoriteLocation>,
    val storeReady: Boolean
) {
    companion object {
        /** The state the resolved flows already hold, without waiting for a combined emission. */
        fun of(sources: FavoritesSources): FavoritesState = FavoritesState(
            favorites = sources.favorites.value,
            groupOrder = sources.groupOrder.value,
            starredOrder = sources.starredOrder.value,
            storeReady = sources.storeReady.value
        )
    }
}
