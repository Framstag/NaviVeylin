package com.naviveylin.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The center of the car map's last drawn frame, published by whichever car screen
 * was drawing and read by every car search screen (spec: `search-result-ranking` —
 * Distance reference for ordering and display: the fallback reference is the
 * current car map viewport center).
 *
 * The center cannot be read from a renderer by the screen that needs it: a car
 * search is pushed *onto* the map, and the push stops the map screen, so the
 * screen that opened the search ({@code RootScreen}, {@code SearchHistoryScreen})
 * never holds the renderer that drew the driver's last frame. This provider is the
 * surface-independent seam for that one value — the same shape as
 * [DrivingModeProvider] and [FreeDrivingStatusProvider], and like them a plain
 * `:core` class bound as a singleton, reached by the car surface through
 * [AutoEntryPoint].
 *
 * Retain-on-stop: a map screen publishes the center of its last frame as it stops
 * drawing, and nothing clears the value — a search opened after the map is gone
 * measures from the last place the driver saw. [center] is null only before any car
 * map has drawn a frame, which is the spec's "neither a fix nor a fallback center
 * exists" case.
 */
class CarMapViewportProvider {

    private val _center = MutableStateFlow<Pair<Double, Double>?>(null)

    /** The last published center, or null before the first car map frame. */
    val center: StateFlow<Pair<Double, Double>?> = _center.asStateFlow()

    /**
     * Publish the center of the frame a car map screen just drew. A non-finite
     * value is dropped: a coordinate that cannot be a location must never become
     * the reference a distance is measured from.
     */
    @Synchronized
    fun publish(lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite()) return
        _center.value = lat to lon
    }
}
