package com.naviveylin.auto

import android.util.Log
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import kotlinx.coroutines.flow.StateFlow

/**
 * What the free-driving screen observes (spec: auto/screen-observation; design D2).
 *
 * This owns *what* is observed and [CarScreenObservations] owns *how long* it lives:
 * the screen starts this in `onStart` and stops it in `onStop`, so each source is
 * observed exactly once per started period and nothing runs while the screen is
 * stopped. The observed sources are the GPS feed (marker, follow-mode viewport
 * commit, speed badge, street name), the resolved dark presentation (map variant
 * and overlay palettes) and basemap data revisions.
 *
 * The effects stay on the screen (they read and write its state and drive its
 * renderer), so they arrive as callbacks — this class is testable without a
 * `CarContext`. One bad fix must never kill the collect flow (the badge and the
 * marker would freeze at the last good fix), which is why the fix effect is confined
 * here rather than in the screen.
 *
 * The screen's speed-staleness ticker deliberately does **not** live here: it must
 * keep running while the screen is stopped, so a stale speed still decays to zero
 * after a background round trip (spec: auto/screen-observation — Work that must
 * continue while stopped is not scoped to the started period; spec:
 * gps-speed-priority).
 */
internal class FreeDrivingScreenObservations(
    private val observations: CarScreenObservations,
    private val locationProvider: AutoLocationProvider,
    private val basemapNotifier: BasemapReloadNotifier,
    private val resolvedDark: StateFlow<Boolean>,
    private val onFix: (AutoPosition) -> Unit,
    private val onDark: (Boolean) -> Unit,
    private val onBasemapRevision: () -> Unit
) {

    /** Establish the screen's observations for the started period that began. */
    fun start() {
        observations.start()
        observations.observe(KEY_POSITION) {
            locationProvider.position().collect { pos ->
                if (pos != null) {
                    // A single bad fix must never kill the collect flow —
                    // otherwise the marker freezes at the last good fix.
                    runCatching { onFix(pos) }
                        .onFailure { Log.w(TAG, "onGpsFix failed", it) }
                }
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
        /** Same tag the screen used before the observation moved here. */
        const val TAG = "FreeDrivingScreen"

        const val KEY_POSITION = "position"
        const val KEY_DARK = "dark"
        const val KEY_BASEMAP = "basemap"
    }
}
