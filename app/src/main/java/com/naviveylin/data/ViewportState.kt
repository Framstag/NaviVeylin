package com.naviveylin.data

import kotlinx.serialization.Serializable

/** Current viewport center, zoom, and rotation for the map display. */
@Serializable
data class ViewportState(
    val centerLat: Double = DEFAULT_LAT,
    val centerLon: Double = DEFAULT_LON,
    /** Magnification scale factor (2^z, fractional z from continuous pinch). */
    val magnification: Double = DEFAULT_MAG.toDouble(),
    /** Map rotation in radians, matching the native `MercatorProjection::Set` angle convention. */
    val angle: Double = DEFAULT_ANGLE
) {
    companion object {
        /** Default center: Dortmund, Germany. */
        const val DEFAULT_LAT = 51.5136
        const val DEFAULT_LON = 7.4653
        const val DEFAULT_MAG = 8
        /** Default rotation: north-up. */
        const val DEFAULT_ANGLE = 0.0
    }
}
