package com.naviveylin.core

import android.graphics.Bitmap
import com.framstag.libosmscout.client.OSMScoutClient

/**
 * Shared utility for rendering libosmscout maps to Android [Bitmap].
 *
 * Lives in [:core] so both [:app] and [:auto] modules can use it
 * without circular dependencies.
 */
object MapRenderUtil {

    /**
     * Render the map to an ARGB_8888 [Bitmap].
     *
     * @param client       initialized [OSMScoutClient] instance
     * @param width        output bitmap width in pixels
     * @param height       output bitmap height in pixels
     * @param lat          center latitude
     * @param lon          center longitude
     * @param angle        map rotation angle in degrees (0 = north up)
     * @param magnification map magnification level
     * @param routeLats    route polyline latitudes (nullable)
     * @param routeLons    route polyline longitudes (nullable)
     * @param favoriteLats favorite marker latitudes (nullable)
     * @param favoriteLons favorite marker longitudes (nullable)
     * @param searchSelLat search selection latitude (Double.NaN if none)
     * @param searchSelLon search selection longitude (Double.NaN if none)
     * @param trackLats    track polyline latitudes (nullable)
     * @param trackLons    track polyline longitudes (nullable)
     * @return rendered [Bitmap] with ARGB_8888 config, or null on error
     */
    fun renderToBitmap(
        client: OSMScoutClient,
        width: Int,
        height: Int,
        lat: Double,
        lon: Double,
        angle: Double,
        magnification: Int,
        routeLats: DoubleArray? = null,
        routeLons: DoubleArray? = null,
        favoriteLats: DoubleArray? = null,
        favoriteLons: DoubleArray? = null,
        searchSelLat: Double = Double.NaN,
        searchSelLon: Double = Double.NaN,
        trackLats: DoubleArray? = null,
        trackLons: DoubleArray? = null
    ): Bitmap? {
        val hasOverlays = (favoriteLats != null && favoriteLats.isNotEmpty()) ||
                !searchSelLat.isNaN() ||
                (routeLats != null && routeLats.isNotEmpty()) ||
                (trackLats != null && trackLats.isNotEmpty())

        val pixels = if (hasOverlays) {
            client.renderWithRouteAndPois(
                width, height, lat, lon, angle, magnification,
                routeLats, routeLons,
                favoriteLats, favoriteLons,
                searchSelLat, searchSelLon,
                trackLats, trackLons
            )
        } else {
            client.render(width, height, lat, lon, angle, magnification)
        } ?: return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
