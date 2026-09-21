package com.naviveylin.auto

import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * What the navigation screen observes (spec: auto/screen-observation; design D2).
 *
 * This owns *what* is observed and [CarScreenObservations] owns *how long* it
 * lives: the screen starts this in `onStart` and stops it in `onStop`, so each
 * source is observed exactly once per started period and nothing runs while the
 * screen is stopped. The observed sources are the navigation state (template
 * rebuilds, route polyline, destination marker, the navigation-manager trip), the
 * GPS feed (marker, follow-mode viewport commit, street fallback), the resolved
 * day/night presentation, and basemap data revisions.
 *
 * The effects stay on the screen (they read and write its state and drive its
 * renderer), so they arrive as callbacks — this class is testable without a
 * `CarContext`.
 */
internal class NavigationScreenObservations(
    private val observations: CarScreenObservations,
    private val navigationViewModel: NavigationViewModel,
    private val locationProvider: AutoLocationProvider,
    private val basemapNotifier: BasemapReloadNotifier,
    private val resolvedDark: StateFlow<Boolean>,
    private val onNavigationState: (NavigationState) -> Unit,
    private val onFix: (AutoPosition) -> Unit,
    private val onDark: (Boolean) -> Unit,
    private val onBasemapRevision: () -> Unit
) {

    /** Establish the screen's observations for the started period that began. */
    fun start() {
        observations.start()
        observations.observe(KEY_NAVIGATION_STATE) {
            navigationViewModel.state.collect { state ->
                onNavigationState(state)
            }
        }
        observations.observe(KEY_POSITION) {
            locationProvider.position().collect { pos ->
                if (pos != null) onFix(pos)
            }
        }
        observations.observe(KEY_DARK) {
            resolvedDark.collect { onDark(it) }
        }
        observations.observe(KEY_BASEMAP) {
            basemapNotifier.revision.collect { revision ->
                if (revision > 0L) onBasemapRevision()
            }
        }
    }

    private companion object {
        const val KEY_NAVIGATION_STATE = "navigation-state"
        const val KEY_POSITION = "position"
        const val KEY_DARK = "dark"
        const val KEY_BASEMAP = "basemap"
    }
}
