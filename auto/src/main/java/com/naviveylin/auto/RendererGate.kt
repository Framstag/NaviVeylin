package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.VehicleAnchorPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Buffers renderer-bound state until the [AutoMapRenderer] is ready
 * (spec: auto-map-renderer — "Renderer initialization off the car-app main
 * thread"; design D1/D5).
 *
 * The renderer is created asynchronously (native client first-touch and the
 * initial-viewport resolution run on a background dispatcher), so there is a
 * window between screen start and renderer readiness. Every renderer-bound
 * state change during that window is captured here as a bounded last-wins
 * slot and replayed once on [publish] in a defined order:
 *
 *   surface → dark → viewport intents → marker/favorites → frames
 *
 * Once the renderer is published, setters apply directly (no buffering).
 * [destroy] cancels the whole window: a late [publish] is shut down
 * immediately and buffered state is dropped (spec scenario "Renderer still
 * initializing when the screen stops").
 *
 * All methods run on the screen's main thread (lifecycle observers, surface
 * callbacks, collectors); [publish] runs on the same thread.
 */
internal class RendererGate {

    private data class SurfacePending(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val dpi: Double
    )

    private data class ViewportPending(
        val lat: Double,
        val lon: Double,
        val zoom: Int,
        val angle: Double,
        val zoomFraction: Double
    )

    private data class GpsMarkerPending(
        val lat: Double,
        val lon: Double,
        val bearing: Double,
        val accuracy: Double,
        val speedKmH: Double,
        val timeMs: Long
    )

    private val _renderer = MutableStateFlow<AutoMapRenderer?>(null)

    /** Read-only handle; non-null once [publish] delivered the renderer. */
    val renderer: StateFlow<AutoMapRenderer?> = _renderer.asStateFlow()

    private var surface: SurfacePending? = null
    private var dark: Boolean? = null
    private var followAnchor: VehicleAnchorPosition? = null
    private var hostPaneRtl: Boolean? = null
    private var reCenter = false
    private var viewport: ViewportPending? = null
    private var reengageFollow = false
    private var destinationMarker: DestinationMarkerPending? = null
    private var gpsMarker: GpsMarkerPending? = null
    private var favorites: List<FavoriteLocation>? = null
    private var routeLats: DoubleArray? = null
    private var routeLons: DoubleArray? = null
    private var overlayDrawer: ((Canvas, Int, Int) -> Unit)? = null
    private var resume = false
    private var invalidateStyle = false
    private var invalidateData = false
    private var requestRender = false

    private var cancelled = false

    private data class DestinationMarkerPending(val lat: Double, val lon: Double, val name: String?)

    /**
     * Deliver the renderer. Pending slots replay in order (design D5); if the
     * gate was [destroy]ed first, the renderer is shut down immediately.
     */
    fun publish(renderer: AutoMapRenderer) {
        if (cancelled) {
            renderer.shutdown()
            return
        }
        _renderer.value = renderer

        surface?.let {
            renderer.updateProjectionDpi(it.dpi)
            renderer.onSurfaceCreated(it.surface, it.width, it.height)
            surface = null
        }
        dark?.let {
            renderer.setDarkPresentation(it)
            dark = null
        }
        if (reCenter) {
            renderer.reCenter()
            reCenter = false
        }
        followAnchor?.let {
            renderer.setFollowAnchor(it)
            followAnchor = null
        }
        hostPaneRtl?.let {
            renderer.setHostPaneRtl(it)
            hostPaneRtl = null
        }
        viewport?.let {
            renderer.setViewport(it.lat, it.lon, it.zoom, it.angle, it.zoomFraction)
            viewport = null
        }
        if (reengageFollow) {
            renderer.reengageFollow()
            reengageFollow = false
        }
        destinationMarker?.let {
            renderer.setDestinationMarker(it.lat, it.lon, it.name)
            destinationMarker = null
        }
        gpsMarker?.let {
            renderer.setGpsMarker(it.lat, it.lon, it.bearing, it.accuracy, it.speedKmH, it.timeMs)
            gpsMarker = null
        }
        favorites?.let {
            renderer.setFavoriteLocations(it)
            favorites = null
        }
        if (routeLats != null || routeLons != null) {
            renderer.setRoute(routeLats, routeLons)
            routeLats = null
            routeLons = null
        }
        overlayDrawer?.let {
            renderer.overlayDrawer = it
            overlayDrawer = null
        }
        if (resume) {
            renderer.resume()
            resume = false
        }
        if (invalidateStyle) {
            renderer.invalidateStyle()
            invalidateStyle = false
        }
        if (invalidateData) {
            renderer.invalidateData()
            invalidateData = false
        }
        if (requestRender) {
            renderer.requestRender()
            requestRender = false
        }
    }

    /** Drop all pending state; a late [publish] is shut down instead. */
    fun destroy() {
        cancelled = true
        _renderer.value?.shutdown()
        _renderer.value = null
        clearPending()
    }

    /** The published renderer for synchronous reads (gestures), or null when not ready. */
    fun rendererOrNull(): AutoMapRenderer? =
        if (cancelled) null else _renderer.value

    /** Whether the published renderer is in follow mode; false before readiness. */
    fun isFollowMode(): Boolean = rendererOrNull()?.isFollowMode() ?: false

    /** Buffered surface DPI from before readiness, or null (used by the init coroutine). */
    fun pendingSurfaceDpi(): Double? = surface?.dpi

    fun onSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) {
        val ready = rendererOrNull()
        if (ready != null) {
            ready.updateProjectionDpi(dpi)
            ready.onSurfaceCreated(surface, width, height)
        } else {
            this.surface = SurfacePending(surface, width, height, dpi)
        }
    }

    fun onSurfaceDestroyed() {
        surface = null
        rendererOrNull()?.onSurfaceDestroyed()
    }

    fun setDarkPresentation(dark: Boolean) {
        rendererOrNull()?.setDarkPresentation(dark) ?: run { this.dark = dark }
    }

    fun setFollowAnchor(anchor: VehicleAnchorPosition) {
        val ready = rendererOrNull()
        if (ready != null) ready.setFollowAnchor(anchor) else followAnchor = anchor
    }

    fun setHostPaneRtl(rtl: Boolean) {
        val ready = rendererOrNull()
        if (ready != null) ready.setHostPaneRtl(rtl) else hostPaneRtl = rtl
    }

    fun reengageFollow() {
        val ready = rendererOrNull()
        if (ready != null) ready.reengageFollow() else reengageFollow = true
    }

    fun reCenter() {
        val ready = rendererOrNull()
        if (ready != null) ready.reCenter() else reCenter = true
    }

    fun setViewport(lat: Double, lon: Double, zoom: Int, angle: Double, zoomFraction: Double = zoom.toDouble()) {
        val ready = rendererOrNull()
        if (ready != null) {
            ready.setViewport(lat, lon, zoom, angle, zoomFraction)
        } else {
            viewport = ViewportPending(lat, lon, zoom, angle, zoomFraction)
        }
    }

    fun setDestinationMarker(lat: Double, lon: Double, name: String?) {
        val ready = rendererOrNull()
        if (ready != null) ready.setDestinationMarker(lat, lon, name)
        else destinationMarker = DestinationMarkerPending(lat, lon, name)
    }

    fun setGpsMarker(
        lat: Double,
        lon: Double,
        bearing: Double,
        accuracy: Double,
        speedKmH: Double = Double.NaN,
        timeMs: Long = System.currentTimeMillis()
    ) {
        val ready = rendererOrNull()
        if (ready != null) {
            ready.setGpsMarker(lat, lon, bearing, accuracy, speedKmH, timeMs)
        } else {
            gpsMarker = GpsMarkerPending(lat, lon, bearing, accuracy, speedKmH, timeMs)
        }
    }

    fun setFavoriteLocations(favorites: List<FavoriteLocation>) {
        val ready = rendererOrNull()
        if (ready != null) ready.setFavoriteLocations(favorites) else this.favorites = favorites
    }

    fun setRoute(routeLats: DoubleArray?, routeLons: DoubleArray?) {
        val ready = rendererOrNull()
        if (ready != null) {
            ready.setRoute(routeLats, routeLons)
        } else {
            this.routeLats = routeLats
            this.routeLons = routeLons
        }
    }

    fun setOverlayDrawer(drawer: ((Canvas, Int, Int) -> Unit)?) {
        val ready = rendererOrNull()
        if (ready != null) ready.overlayDrawer = drawer else overlayDrawer = drawer
    }

    fun resume() {
        val ready = rendererOrNull()
        if (ready != null) ready.resume() else resume = true
    }

    fun pause() {
        rendererOrNull()?.pause()
    }

    fun releaseSurface() {
        rendererOrNull()?.releaseSurface()
    }

    fun invalidateStyle() {
        val ready = rendererOrNull()
        if (ready != null) ready.invalidateStyle() else invalidateStyle = true
    }

    fun invalidateData() {
        val ready = rendererOrNull()
        if (ready != null) ready.invalidateData() else invalidateData = true
    }

    fun requestRender() {
        val ready = rendererOrNull()
        if (ready != null) ready.requestRender() else requestRender = true
    }

    private fun clearPending() {
        surface = null
        dark = null
        followAnchor = null
        hostPaneRtl = null
        reCenter = false
        viewport = null
        reengageFollow = false
        destinationMarker = null
        gpsMarker = null
        favorites = null
        routeLats = null
        routeLons = null
        overlayDrawer = null
        resume = false
        invalidateStyle = false
        invalidateData = false
        requestRender = false
    }
}
