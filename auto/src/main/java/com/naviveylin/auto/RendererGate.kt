package com.naviveylin.auto

import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.VehicleAnchorPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the car map is doing (spec: car-host-fault-isolation — A repeatedly faulting car renderer
 * recovers, then degrades visibly; design D5).
 *
 * Owned by [RendererGate] rather than by a renderer instance: the degraded state is produced by
 * replacing a renderer, so it has to outlive the instance that reported it.
 */
internal enum class RendererState {
    /** Frames are drawn; a confined fault costs only its own frame (or tick). */
    LIVE,

    /** A re-creation is in progress: the map shows its last frame until the new one draws. */
    RECOVERING,

    /** The re-creation budget is exhausted; the screen has to tell the driver the map is unavailable. */
    DEGRADED
}

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
        val zoomFraction: Double,
        val walkZoom: Boolean = false
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

    /**
     * DPI of the last car surface the host delivered (0 before one arrives). The
     * native client's DPI follows it, applied by the owning screen on a background
     * dispatcher — a host callback only retains state (spec: car-host-fault-isolation —
     * Host callbacks answer promptly; spec: auto-map-renderer — Renderer initialization
     * off the car-app main thread).
     */
    private val _surfaceDpi = MutableStateFlow(0.0)
    val surfaceDpi: StateFlow<Double> = _surfaceDpi.asStateFlow()

    /**
     * A stylesheet day/night flag the owning screen wants pushed to the native client.
     *
     * [force] re-pushes a value the native side may have dropped (a stylesheet was just
     * reloaded, or the database became ready after an earlier push). [token] makes two
     * requests with the same value distinct: a state flow suppresses an equal value, and
     * a dropped push must stay retryable.
     */
    data class DaylightPush(val dark: Boolean, val force: Boolean, val token: Long)

    private val _daylightPush = MutableStateFlow<DaylightPush?>(null)

    /**
     * Requests for a native stylesheet-flag push, applied by the owning screen's background
     * collector. Setting the flag reloads the style variant on the DB thread, so a host
     * callback (the surface delivery) publishes the request instead of running it (spec:
     * car-host-fault-isolation — Host callbacks answer promptly). Main thread.
     */
    val daylightPush: StateFlow<DaylightPush?> = _daylightPush.asStateFlow()

    private var daylightToken = 0L

    /** Publish a stylesheet-flag request; the screen's collector applies it off the main thread. */
    fun requestDaylightPush(dark: Boolean, force: Boolean = false) {
        _daylightPush.value = DaylightPush(dark, force, ++daylightToken)
    }

    /** Read-only handle; non-null once [publish] delivered the renderer. */
    val renderer: StateFlow<AutoMapRenderer?> = _renderer.asStateFlow()

    private var surface: SurfacePending? = null
    private var dark: Boolean? = null
    private var followAnchor: VehicleAnchorPosition? = null
    private var hostPaneRtl: Boolean? = null
    private var hostBottomInsetPx: Int? = null
    private var hostTopInsetPx: Int? = null
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
     * Deliver the renderer. Pending slots replay in order (design D5); if the gate was
     * [destroy]ed first, the renderer is shut down immediately.
     *
     * The slots are of two kinds (design D3 of the loop-recovery change), because a
     * re-created renderer has to be given the state the previous one displayed:
     *
     * - **state slots** keep their value and are replayed on *every* publish — presentation,
     *   follow anchor, pane geometry, viewport, marker/favorites/route/destination and the
     *   overlay drawer. A recovery that lost them would snap the map back to the initial
     *   center, zoom and follow mode;
     * - **one-shot intents** (re-center, re-engage follow, resume, invalidate, render request)
     *   are edge-triggered and cleared after being applied — replaying one would repeat an
     *   action the screen asked for once.
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
        }
        if (reCenter) {
            renderer.reCenter()
            reCenter = false
        }
        followAnchor?.let {
            renderer.setFollowAnchor(it)
        }
        hostPaneRtl?.let {
            renderer.setHostPaneRtl(it)
        }
        hostBottomInsetPx?.let {
            renderer.setHostBottomInset(it)
        }
        hostTopInsetPx?.let {
            renderer.setHostTopInset(it)
        }
        viewport?.let {
            renderer.setViewport(it.lat, it.lon, it.zoom, it.angle, it.zoomFraction, it.walkZoom)
        }
        if (reengageFollow) {
            renderer.reengageFollow()
            reengageFollow = false
        }
        destinationMarker?.let {
            renderer.setDestinationMarker(it.lat, it.lon, it.name)
        }
        gpsMarker?.let {
            renderer.setGpsMarker(it.lat, it.lon, it.bearing, it.accuracy, it.speedKmH, it.timeMs)
        }
        favorites?.let {
            renderer.setFavoriteLocations(it)
        }
        if (routeLats != null || routeLons != null) {
            renderer.setRoute(routeLats, routeLons)
        }
        overlayDrawer?.let {
            renderer.overlayDrawer = it
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
        renderSupervisor.suspend()
        _renderer.value?.shutdown()
        _renderer.value = null
        clearPending()
    }

    // --- Loop-fault recovery (spec: car-host-fault-isolation — A repeatedly faulting car
    // renderer recovers, then degrades visibly; design D3/D4/D5/D7) ---

    /**
     * The fault supervisor of the current started screen period (design D2/D7). Owned here so
     * it spans the renderer instances of the period: the re-creation budget must survive the
     * instance swap, otherwise "threshold reached again after the cap" could never happen.
     */
    internal val renderSupervisor = RenderLoopSupervisor()

    private val _rendererState = MutableStateFlow(RendererState.LIVE)

    /** Whether the map is drawing, being recovered, or could not be drawn at all (design D5). */
    val rendererState: StateFlow<RendererState> = _rendererState.asStateFlow()

    /**
     * Builds the replacement renderer for a recovery. Set by the owning screen, which holds the
     * native client and the construction arguments; without it a fault storm degrades directly
     * (no instance to swap in).
     */
    internal var rendererFactory: ((RenderLoopSupervisor) -> AutoMapRenderer)? = null

    /**
     * Re-attaches the session's surface to the re-created renderer. Set by the owning screen —
     * `SessionCarSurfaceHost.attach` re-delivers the held surface, so the renderer never holds,
     * releases or adopts a surface itself (spec: car-host-fault-isolation — Single-owner car
     * surface; design D4).
     */
    internal var reattachSurface: (() -> Unit)? = null

    /**
     * A re-creation was requested by the supervisor. Main thread only — the screen posts here
     * from the render thread's report (`onRendererRecoveryRequested`, the same shape as the
     * surface-failure callback).
     */
    internal fun onRendererRecoveryRequested() {
        if (cancelled) return
        val factory = rendererFactory ?: return markDegraded()
        _rendererState.value = RendererState.RECOVERING
        // The old instance's loops are what faulted: end them before the replacement draws.
        _renderer.value?.shutdown()
        _renderer.value = null
        publish(factory(renderSupervisor))
        _rendererState.value = RendererState.LIVE
        reattachSurface?.invoke()
    }

    /** The re-creation budget is exhausted: the map cannot be drawn (design D7). Main thread only. */
    internal fun markDegraded() {
        renderSupervisor.suspend()
        _rendererState.value = RendererState.DEGRADED
    }

    /**
     * A fresh started screen period: the fault streak and the re-creation budget are re-armed and
     * a degraded map gets another chance (design D7). Main thread only, from the screen's start.
     */
    internal fun startPeriod() {
        renderSupervisor.startPeriod()
        _rendererState.value = RendererState.LIVE
    }

    /** The published renderer for synchronous reads (gestures), or null when not ready. */
    fun rendererOrNull(): AutoMapRenderer? =
        if (cancelled) null else _renderer.value

    /** Whether the published renderer is in follow mode; false before readiness. */
    fun isFollowMode(): Boolean = rendererOrNull()?.isFollowMode() ?: false

    /** Buffered surface DPI from before readiness, or null (used by the init coroutine). */
    fun pendingSurfaceDpi(): Double? = surface?.dpi

    fun onSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) {
        _surfaceDpi.value = dpi
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

    /**
     * Presentation applied to the map variant and the app-drawn overlays of this
     * screen's renderer. Read by the surface drawers so an overlay palette always
     * matches the map underneath; `false` before a renderer exists (no overlay is
     * drawn then either).
     */
    fun currentDarkPresentation(): Boolean =
        rendererOrNull()?.currentDarkPresentation() ?: (dark ?: false)

    fun setFollowAnchor(anchor: VehicleAnchorPosition) {
        val ready = rendererOrNull()
        if (ready != null) ready.setFollowAnchor(anchor) else followAnchor = anchor
    }

    fun setHostPaneRtl(rtl: Boolean) {
        val ready = rendererOrNull()
        if (ready != null) ready.setHostPaneRtl(rtl) else hostPaneRtl = rtl
    }

    fun setHostBottomInset(px: Int) {
        val ready = rendererOrNull()
        if (ready != null) ready.setHostBottomInset(px) else hostBottomInsetPx = px
    }

    fun setHostTopInset(px: Int) {
        val ready = rendererOrNull()
        if (ready != null) ready.setHostTopInset(px) else hostTopInsetPx = px
    }

    fun reengageFollow() {
        val ready = rendererOrNull()
        if (ready != null) ready.reengageFollow() else reengageFollow = true
    }

    fun reCenter() {
        val ready = rendererOrNull()
        if (ready != null) ready.reCenter() else reCenter = true
    }

    fun setViewport(
        lat: Double,
        lon: Double,
        zoom: Int,
        angle: Double,
        zoomFraction: Double = zoom.toDouble(),
        walkZoom: Boolean = false
    ) {
        val ready = rendererOrNull()
        if (ready != null) {
            ready.setViewport(lat, lon, zoom, angle, zoomFraction, walkZoom)
        } else {
            viewport = ViewportPending(lat, lon, zoom, angle, zoomFraction, walkZoom)
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

    fun detachSurface() {
        rendererOrNull()?.detachSurface()
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
        hostBottomInsetPx = null
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
        _daylightPush.value = null
    }
}
