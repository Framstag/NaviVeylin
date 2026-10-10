package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * What the destination-details screen observes (spec: auto/screen-observation; design D2).
 *
 * This owns *what* is observed and [CarScreenObservations] owns *how long* it lives: the screen
 * starts this in `onStart` and stops it in `onStop`, so each source is observed exactly once per
 * started period and nothing runs while the screen is stopped. The observed sources are the ones the
 * preview and the favorite action row consume: the GPS position feed (marker + viewport fit),
 * favorites (marker overlay and the add/remove row) and basemap data revisions (re-render after a
 * download/update/delete).
 *
 * The effects stay on the screen — they write its state fields and drive its renderer gate — so they
 * arrive as callbacks, which keeps this class testable without a `CarContext`. The screen's
 * constructor-time work (renderer construction and publish, the one-shot reverse-geocode/describe
 * load, the renderer-surface-failure wiring) is *not* an observation and stays on the screen's own
 * scope (design D3).
 */
internal class DetailsScreenObservations(
    private val observations: CarScreenObservations,
    private val locationProvider: AutoLocationProvider,
    private val favoritesProvider: AutoFavoritesProvider,
    /**
     * Dispatcher of the hop that touches the native client: resolving this provider builds the Hilt
     * `OSMScoutClient` on first use, and this body runs on the observation scope's main dispatcher —
     * the car-app host thread (spec: auto-map-renderer — A screen observation resolves its provider off
     * the host thread). Defaults to [Dispatchers.Default]; injectable so a case can own the hop.
     */
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val basemapNotifier: BasemapReloadNotifier,
    private val onFix: (AutoPosition) -> Unit,
    private val onFavorites: (Map<String, List<FavoriteLocation>>) -> Unit,
    private val onBasemapRevision: () -> Unit
) {

    /**
     * Establish the screen's observations for the started period that began.
     *
     * Every source is a [StateFlow], so a re-established observation delivers the current value
     * immediately — that is how a change made while the screen was stopped reaches the screen once on
     * start (spec: auto/screen-observation — Observations are re-established with the current state
     * on start).
     */
    fun start() {
        observations.start()
        observations.observe(KEY_POSITION) {
            locationProvider.position().collect { pos ->
                if (pos != null) onFix(pos)
            }
        }
        observations.observe(KEY_FAVORITES) {
            val favorites = withContext(backgroundDispatcher) { favoritesProvider.favoriteLocations() }
            favorites.collect { value ->
                onFavorites(value)
            }
        }
        observations.observe(KEY_BASEMAP) {
            basemapNotifier.revision.collect { revision ->
                if (revision > 0L) onBasemapRevision()
            }
        }
    }

    private companion object {
        const val KEY_POSITION = "position"
        const val KEY_FAVORITES = "favorites"
        const val KEY_BASEMAP = "basemap"
    }
}
