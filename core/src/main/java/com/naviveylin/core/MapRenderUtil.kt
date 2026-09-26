package com.naviveylin.core

import android.graphics.Bitmap
import com.framstag.libosmscout.client.OSMScoutClient

/**
 * Shared utility for rendering libosmscout maps to Android [Bitmap].
 *
 * Lives in [:core] so both [:app] and [:auto] modules can use it
 * without circular dependencies.
 *
 * Two entry points share the same pixel path:
 * - [renderToBitmap] allocates the bitmap it returns; the caller owns it.
 * - [renderInto] writes into a bitmap the caller already holds — the pooled form used by
 *   both renderers ([RenderBitmapPool]), so a render allocates no target
 *   (spec: `render-performance` — Reusable render target for map frames).
 */
object MapRenderUtil {

    /**
     * Render the map to a new ARGB_8888 [Bitmap] owned by the caller.
     *
     * @param client       initialized [OSMScoutClient] instance
     * @param width        output bitmap width in pixels
     * @param height       output bitmap height in pixels
     * @param lat          center latitude
     * @param lon          center longitude
     * @param angle        map rotation angle in degrees (0 = north up)
     * @param magnification magnification scale factor (2^z; fractional z allowed)
     * @param dpi          physical DPI of the display the frame is rendered for — the
     *                     native projection uses it (spec: `render-projection-dpi`)
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
        magnification: Double,
        dpi: Double,
        routeLats: DoubleArray? = null,
        routeLons: DoubleArray? = null,
        favoriteLats: DoubleArray? = null,
        favoriteLons: DoubleArray? = null,
        searchSelLat: Double = Double.NaN,
        searchSelLon: Double = Double.NaN,
        trackLats: DoubleArray? = null,
        trackLons: DoubleArray? = null
    ): Bitmap? {
        val pixels = renderPixels(
            client = client,
            width = width,
            height = height,
            lat = lat,
            lon = lon,
            angle = angle,
            magnification = magnification,
            dpi = dpi,
            routeLats = routeLats,
            routeLons = routeLons,
            favoriteLats = favoriteLats,
            favoriteLons = favoriteLons,
            searchSelLat = searchSelLat,
            searchSelLon = searchSelLon,
            trackLats = trackLats,
            trackLons = trackLons
        ) ?: return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Render the map into [target] — the pooled form: the caller obtained the bitmap from
     * [RenderBitmapPool], and the target's own size is the render size.
     *
     * The target's pixels are overwritten on success. On failure (the native render returns
     * null) the target is left untouched and null is returned, so a caller can keep the
     * target for the next attempt. The caller owns the target and releases it back to the
     * pool when the frame is dropped; this function never recycles it.
     *
     * @param dpi          physical DPI of the display the frame is rendered for — the
     *                     native projection uses it (spec: `render-projection-dpi`)
     * @return [target] on success, null when the native render produced nothing.
     */
    fun renderInto(
        client: OSMScoutClient,
        target: Bitmap,
        lat: Double,
        lon: Double,
        angle: Double,
        magnification: Double,
        dpi: Double,
        routeLats: DoubleArray? = null,
        routeLons: DoubleArray? = null,
        favoriteLats: DoubleArray? = null,
        favoriteLons: DoubleArray? = null,
        searchSelLat: Double = Double.NaN,
        searchSelLon: Double = Double.NaN,
        trackLats: DoubleArray? = null,
        trackLons: DoubleArray? = null
    ): Bitmap? {
        val width = target.width
        val height = target.height
        val pixels = renderPixels(
            client = client,
            width = width,
            height = height,
            lat = lat,
            lon = lon,
            angle = angle,
            magnification = magnification,
            dpi = dpi,
            routeLats = routeLats,
            routeLons = routeLons,
            favoriteLats = favoriteLats,
            favoriteLons = favoriteLons,
            searchSelLat = searchSelLat,
            searchSelLon = searchSelLon,
            trackLats = trackLats,
            trackLons = trackLons
        ) ?: return null

        target.setPixels(pixels, 0, width, 0, 0, width, height)
        return target
    }

    /** The shared pixel path: the native render, overlay-aware, or null on error. */
    private fun renderPixels(
        client: OSMScoutClient,
        width: Int,
        height: Int,
        lat: Double,
        lon: Double,
        angle: Double,
        magnification: Double,
        dpi: Double,
        routeLats: DoubleArray?,
        routeLons: DoubleArray?,
        favoriteLats: DoubleArray?,
        favoriteLons: DoubleArray?,
        searchSelLat: Double,
        searchSelLon: Double,
        trackLats: DoubleArray?,
        trackLons: DoubleArray?
    ): IntArray? {
        val hasOverlays = (favoriteLats != null && favoriteLats.isNotEmpty()) ||
                !searchSelLat.isNaN() ||
                (routeLats != null && routeLats.isNotEmpty()) ||
                (trackLats != null && trackLats.isNotEmpty())

        return if (hasOverlays) {
            client.renderWithRouteAndPois(
                width, height, lat, lon, angle, magnification, dpi,
                routeLats, routeLons,
                favoriteLats, favoriteLons,
                searchSelLat, searchSelLon,
                trackLats, trackLons
            )
        } else {
            client.render(width, height, lat, lon, angle, magnification, dpi)
        }
    }
}
