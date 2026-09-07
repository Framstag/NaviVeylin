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
    /**
     * True when the state holds renderable coordinates: no NaN/infinity, lat/lon
     * within Mercator-valid ranges, positive finite magnification, finite angle.
     * Used to reject uninitialized viewports before persisting them.
     */
    fun isValid(): Boolean =
        centerLat.isFinite() && centerLon.isFinite() &&
            centerLat in -90.0..90.0 && centerLon in -180.0..180.0 &&
            magnification.isFinite() && magnification > 0.0 &&
            angle.isFinite()

    companion object {
        /** Default center: Dortmund, Germany. */
        const val DEFAULT_LAT = 51.5136
        const val DEFAULT_LON = 7.4653
        const val DEFAULT_MAG = 8
        /** Default rotation: north-up. */
        const val DEFAULT_ANGLE = 0.0
    }
}
