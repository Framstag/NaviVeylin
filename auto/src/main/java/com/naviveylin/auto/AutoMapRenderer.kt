package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.ProjectionUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Current destination marker state (spec: auto-destination-details). */
internal data class DestinationMarkerState(
    val lat: Double,
    val lon: Double,
    val name: String?,
    val visible: Boolean
)

/**
 * Renders libosmscout maps to an Android [Surface] for display on Android Auto.
 *
 * Designed to be used with [MapController.SurfaceCallback] from a [MapScreen].
 * Runs a render loop on [Dispatchers.Default] with debounced viewport changes.
 *
 * @param projectionDpi the DPI the native renderer projects with (the client's
 *   configured physical DPI, NOT the car surface DPI). Used to draw the GPS
 *   marker overlay in the same projection as the map bitmap.
 */
class AutoMapRenderer(
    private val client: OSMScoutClient,
    initialProjectionDpi: Double,
    initialLat: Double = DEFAULT_LATITUDE,
    initialLon: Double = DEFAULT_LONGITUDE,
    initialZoom: Int = DEFAULT_ZOOM,
    /** Start in follow mode (re-center on GPS fixes); false for "show location" maps. */
    initialFollowMode: Boolean = true
) {

    /**
     * DPI used for gestures/marker overlay, kept in sync with the native
     * render DPI ([OSMScoutClient.setMapDpi]) once the car surface arrives.
     */
    @Volatile
    var projectionDpi: Double = initialProjectionDpi
        private set

    /** Update the projection DPI (call alongside [OSMScoutClient.setMapDpi]). */
    fun updateProjectionDpi(dpi: Double) {
        if (dpi > 0 && dpi != projectionDpi) {
            projectionDpi = dpi
            // The overrun buffer was projected at the old DPI — full render.
            blitEligible = false
            requestRender()
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    @Volatile private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var isShutdown = false

    /** Unique id per renderer instance, to tell renderers apart in logs. */
    private val rendererId = nextRendererId++

    // Viewport state
    @Volatile private var viewportLat = initialLat
    @Volatile private var viewportLon = initialLon
    @Volatile private var viewportZoom = initialZoom
    @Volatile private var viewportZoomFraction = initialZoom.toDouble()
    @Volatile private var viewportAngle = 0.0

    // Overlay data
    @Volatile private var gpsMarkerLat = Double.NaN
    @Volatile private var gpsMarkerLon = Double.NaN
    @Volatile private var gpsMarkerBearing = Double.NaN
    @Volatile private var gpsMarkerAccuracy = 0.0
    @Volatile private var gpsMarkerVisible = false

    @Volatile private var favoriteLats: DoubleArray? = null
    @Volatile private var favoriteLons: DoubleArray? = null
    @Volatile private var routeLats: DoubleArray? = null
    @Volatile private var routeLons: DoubleArray? = null

    // Destination marker (spec: auto-destination-details)
    @Volatile private var destMarkerLat = Double.NaN
    @Volatile private var destMarkerLon = Double.NaN
    @Volatile private var destMarkerName: String? = null
    @Volatile private var destMarkerVisible = false

    // Overrun buffer (spec: auto-smooth-follow): the last full native render at
    // OVERRUN_FACTOR x surface size, kept for sub-region blits. A viewport
    // change within the overrun region is served by drawing this buffer shifted
    // by the delta — no full native render (design D1/D2).
    @Volatile private var overrunBitmap: Bitmap? = null
    @Volatile private var overrunLat = Double.NaN
    @Volatile private var overrunLon = Double.NaN
    @Volatile private var overrunMag = 0
    @Volatile private var overrunAngle = 0.0

    // Displayed viewport center: the eased predicted position in follow mode,
    // equal to the overrun center right after a full render. The GPS marker is
    // drawn at this position so it glides with the blitted map (design D7).
    @Volatile private var displayLat = Double.NaN
    @Volatile private var displayLon = Double.NaN

    // Follow prediction (spec: auto-smooth-follow): extrapolates the displayed
    // position between 1 Hz GPS fixes and eases corrections on fix arrival.
    // Display-only — the navigation engine receives only real fixes (design D5).
    private val followPrediction = FollowPrediction()
    @Volatile private var lastFixSpeedMs = Double.NaN
    @Volatile private var lastFixTimeMs = 0L
    @Volatile private var lastFixLat = Double.NaN
    @Volatile private var lastFixLon = Double.NaN
    private var extrapolationJob: Job? = null

    // True when the next render may be served by a sub-region blit (pure
    // viewport or canvas-overlay change). Overlay/native-content changes
    // (favorites, route, DPI, surface) force a full render.
    @Volatile private var blitEligible = false

    // Test-visible counters (spec: auto-smooth-follow verification): a small
    // viewport move must not increment [fullRenderCount].
    @Volatile internal var fullRenderCount = 0
    @Volatile internal var blitCount = 0

    /** Test seam: when false, the async render/extrapolation loops do not run. */
    @Volatile internal var asyncLoopsEnabled = true

    // Follow mode
    @Volatile private var followMode = initialFollowMode

    /**
     * True while the owning screen is stopped: the render loop skips frames
     * so a stopped screen never locks the (shared) display surface — two
     * renderers locking the same surface race and throw
     * IllegalArgumentException from lockCanvas ("surface already locked").
     */
    @Volatile private var paused = false

    /**
     * True after the current surface failed to lock (host locked/destroyed
     * it). The render loop skips frames so we never hammer a dead surface
     * with 1s native renders; the host is expected to deliver a fresh
     * surface (onSurfaceCreated resets this).
     */
    @Volatile private var surfaceFailed = false

    /**
     * Invoked (throttled) when the current surface cannot be locked. The
     * owning screen uses this to ask the host for a fresh surface (e.g.
     * [androidx.car.app.Screen.invalidate]).
     */
    var onSurfaceFailed: (() -> Unit)? = null

    private val renderSignal = MutableStateFlow(0L)
    private var pendingRender = false

    // Throttle for surface-failure diagnostics (a dead surface fails every
    // frame; logging each one floods logcat).
    private var lastSurfaceFailureLogMs = 0L
    private var lastSurfaceFailureCallbackMs = 0L
    private val SURFACE_FAILURE_LOG_INTERVAL_MS = 5_000L
    private val SURFACE_FAILURE_CALLBACK_INTERVAL_MS = 5_000L

    init {
        startRenderLoop()
        startExtrapolationLoop()
    }

    // Exposed viewport state for UI
    private val _viewportState = MutableStateFlow(
        ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle)
    )
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    /** Current viewport state. */
    data class ViewportState(
        val lat: Double,
        val lon: Double,
        val zoom: Int,
        val angle: Double
    )

    /**
     * Optional overlay drawn on the surface after the map bitmap and GPS
     * marker, e.g. the navigation hint panel (see [NavigationHintsOverlay]).
     * Invoked on the render loop with the surface canvas and its size.
     */
    var overlayDrawer: ((Canvas, Int, Int) -> Unit)? = null

    /**
     * Called when the [Surface] is created (from [MapController.SurfaceCallback]).
     */
    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        synchronized(surfaceLock) {
            // The car-app API contract requires every Surface received via
            // onSurfaceAvailable to be released once replaced or destroyed.
            // NOT releasing (as before) keeps the old buffer queue alive and
            // the host then re-delivers a surface whose queue it still owns
            // → every lockCanvas throws IllegalArgumentException from
            // nativeLockCanvas.
            val previous = this.surface
            if (previous != null && previous !== surface) {
                android.util.Log.d(TAG, "renderer#$rendererId releasing replaced surface ${System.identityHashCode(previous)}")
                previous.release()
            }
            this.surface = surface
            this.surfaceWidth = width
            this.surfaceHeight = height
            surfaceFailed = false
            // A new surface may have a different size: the overrun buffer from
            // the old surface is stale — drop it and force a full render.
            clearOverrunBuffer()
            blitEligible = false
            android.util.Log.d(TAG, "renderer#$rendererId surface created: ${System.identityHashCode(surface)}")
        }
        requestRender()
    }

    /**
     * Called when the [Surface] is destroyed (from [MapController.SurfaceCallback]).
     */
    fun onSurfaceDestroyed() {
        synchronized(surfaceLock) {
            android.util.Log.d(TAG, "renderer#$rendererId surface destroyed: ${surface?.let { System.identityHashCode(it) }}")
            surface?.release()
            surface = null
            surfaceWidth = 0
            surfaceHeight = 0
            surfaceFailed = false
            clearOverrunBuffer()
        }
    }

    /**
     * Update the GPS position marker.
     *
     * @param speedKmH ground speed in km/h, or [Double.NaN] when unknown
     * @param timeMs fix receipt time in ms since epoch (used as the
     *   extrapolation base; GPS fix timestamps can be ahead of the system clock)
     */
    fun setGpsMarker(
        lat: Double,
        lon: Double,
        bearing: Double,
        accuracy: Double,
        speedKmH: Double = Double.NaN,
        timeMs: Long = System.currentTimeMillis()
    ) {
        if (lat.isNaN() || lon.isNaN()) {
            gpsMarkerVisible = false
            return
        }
        val fixMoved = lastFixLat.isNaN() ||
            abs(lat - lastFixLat) > 1e-6 || abs(lon - lastFixLon) > 1e-6
        gpsMarkerLat = lat
        gpsMarkerLon = lon
        gpsMarkerBearing = bearing
        gpsMarkerAccuracy = accuracy
        gpsMarkerVisible = true
        lastFixLat = lat
        lastFixLon = lon
        lastFixSpeedMs = if (speedKmH.isNaN()) Double.NaN else speedKmH / 3.6
        lastFixTimeMs = timeMs
        // Display-only prediction: the navigation engine never sees it.
        followPrediction.update(lat, lon, lastFixSpeedMs, bearing, timeMs)
        if (followMode) {
            // Follow mode: the extrapolation loop drives the display; the fix
            // only updates the prediction. A small GPS move is served by the
            // loop's blit — no viewport snap, no full render (spec:
            // auto-smooth-follow). When the vehicle is stationary the loop is
            // gated off, so snap the display to the fix so the marker lands on
            // the vehicle position.
            val moving = !lastFixSpeedMs.isNaN() && lastFixSpeedMs > MOVEMENT_SPEED_MS
            if (!moving && fixMoved) {
                displayLat = lat
                displayLon = lon
                viewportLat = lat
                viewportLon = lon
                emitViewportState()
                blitEligible = true
                requestRender()
            }
            return
        }
        // Non-follow: marker-only update — the map content is unchanged, so the
        // next frame can be served by a blit (the marker is a canvas overlay).
        blitEligible = true
        requestRender()
    }

    /**
     * Update favorite location markers.
     */
    fun setFavoriteLocations(favorites: List<FavoriteLocation>?) {
        if (favorites == null || favorites.isEmpty()) {
            favoriteLats = null
            favoriteLons = null
        } else {
            val lats = DoubleArray(favorites.size)
            val lons = DoubleArray(favorites.size)
            for (i in favorites.indices) {
                lats[i] = favorites[i].lat
                lons[i] = favorites[i].lon
            }
            favoriteLats = lats
            favoriteLons = lons
        }
        // Favorites are baked into the native render — a blit would show stale
        // markers.
        blitEligible = false
        requestRender()
    }

    /**
     * Set the route polyline for map rendering (native "_route" style);
     * null clears it. Re-renders on change.
     */
    fun setRoute(routeLats: DoubleArray?, routeLons: DoubleArray?) {
        this.routeLats = routeLats
        this.routeLons = routeLons
        // The route is baked into the native render — a blit would show a
        // stale polyline.
        blitEligible = false
        requestRender()
    }

    /**
     * Update the destination marker (spec: auto-destination-details).
     * NaN lat/lon hides the marker; [name] is drawn as a label when present.
     */
    fun setDestinationMarker(lat: Double, lon: Double, name: String?) {
        if (lat.isNaN() || lon.isNaN()) {
            destMarkerVisible = false
        } else {
            destMarkerLat = lat
            destMarkerLon = lon
            destMarkerName = name
            destMarkerVisible = true
        }
        // The destination marker is a canvas overlay (not native-rendered), so
        // a marker-only change can be served by a blit.
        blitEligible = true
        requestRender()
    }

    /** Current destination marker state (spec: auto-destination-details). */
    internal fun destinationMarkerState(): DestinationMarkerState =
        DestinationMarkerState(destMarkerLat, destMarkerLon, destMarkerName, destMarkerVisible)

    /**
     * Set the viewport center, zoom, and rotation.
     *
     * @param zoomFraction continuous (fractional) zoom for pinch gestures; the
     *   rendered [zoom] is its rounded value. Defaults to [zoom] for callers
     *   that only work with whole zoom levels (buttons, re-center).
     */
    fun setViewport(
        lat: Double,
        lon: Double,
        zoom: Int,
        angle: Double,
        zoomFraction: Double = zoom.toDouble()
    ) {
        followMode = false
        val zoomChanged = zoom != viewportZoom
        val angleChanged = angle != viewportAngle
        viewportLat = lat
        viewportLon = lon
        viewportZoom = zoom
        viewportZoomFraction = zoomFraction
        viewportAngle = angle
        emitViewportState()
        // A pure center change (same zoom/angle) can be served by a blit within
        // the overrun region; zoom/rotation changes need a full render.
        blitEligible = !zoomChanged && !angleChanged
        requestRender()
    }

    /**
     * Current continuous (fractional) zoom, used for pinch accumulation.
     */
    fun fractionalZoom(): Double = viewportZoomFraction

    /**
     * Compute the zoom step for a pinch scale factor.
     *
     * Uses continuous zoom accumulation so small pinch deltas never jump a
     * whole level (old behavior: any `scaleFactor > 1f` snapped +1 level).
     * Returns the new (fractional zoom, integer zoom) without mutating state;
     * call [setViewport] with the results.
     */
    fun zoomStep(scaleFactor: Float): Pair<Double, Int> {
        val newFraction = (
            viewportZoomFraction + kotlin.math.ln(scaleFactor.toDouble()) / kotlin.math.ln(2.0)
        ).coerceIn(MIN_ZOOM.toDouble(), MAX_ZOOM.toDouble())
        return newFraction to newFraction.roundToInt()
    }

    /**
     * Pause rendering (screen stopped). Pending renders are dropped; the
     * surface is never locked while paused.
     */
    fun pause() {
        android.util.Log.d(TAG, "renderer#$rendererId pause")
        paused = true
        pendingRender = false
    }

    /**
     * Release the current surface reference. The car-app API contract
     * requires every Surface received via onSurfaceAvailable to be released
     * once done with it; a screen that is stopped underneath a pushed screen
     * never receives onSurfaceDestroyed (the host notifies only the current
     * callback), so it must release on stop itself — otherwise the host's
     * buffer queue stays held and the next surface it delivers fails every
     * lockCanvas.
     */
    fun releaseSurface() {
        synchronized(surfaceLock) {
            android.util.Log.d(TAG, "renderer#$rendererId releasing surface: ${surface?.let { System.identityHashCode(it) }}")
            surface?.release()
            surface = null
            surfaceWidth = 0
            surfaceHeight = 0
            surfaceFailed = false
            clearOverrunBuffer()
        }
    }

    /** Resume rendering (screen started again). */
    fun resume() {
        android.util.Log.d(TAG, "renderer#$rendererId resume")
        paused = false
        requestRender()
    }

    /**
     * Re-center on GPS position and re-engage follow mode.
     *
     * Snaps the display to the fix (same as the phone's re-center: the map
     * renders at the fix and the display re-initializes there).
     */
    fun reCenter() {
        followMode = true
        if (gpsMarkerVisible) {
            viewportLat = gpsMarkerLat
            viewportLon = gpsMarkerLon
            displayLat = gpsMarkerLat
            displayLon = gpsMarkerLon
            emitViewportState()
            blitEligible = true
            requestRender()
        }
    }

    /**
     * Re-engage follow mode WITHOUT snapping the display to the fix.
     *
     * Used by screens that disengage follow transiently (heading-up rotation
     * via [setViewport]) and must not snap back per fix — the extrapolation
     * loop eases the display to the fix instead (same as the phone's
     * per-frame correction). [reCenter] keeps the snap for the user's
     * re-center action.
     *
     * The viewport is anchored to the fix (and emitted) so a pending render
     * (from the preceding [setViewport]) renders AT the fix — otherwise the
     * render would target the stale [viewportState] center and [fullRender]
     * would yank the eased display back there every fix (visible "pumping").
     * Unlike [reCenter], the display state is left untouched so the loop's
     * easing continues.
     */
    fun reengageFollow() {
        followMode = true
        if (gpsMarkerVisible) {
            viewportLat = gpsMarkerLat
            viewportLon = gpsMarkerLon
            emitViewportState()
        }
    }

    /** Whether follow mode is currently active. */
    fun isFollowMode(): Boolean = followMode

    // Throttle for file-backed diagnostics (pan events are frequent).
    private var lastRenderLogMs = 0L
    private val RENDER_LOG_INTERVAL_MS = 1000L

    // Throttle for full-render requests from the extrapolation loop (a fast
    // vehicle would otherwise queue a render every frame while clamped).
    private var lastRenderRequestMs = 0L

    // Throttle for the follow diagnostic (mirrors the phone's follow log).
    private var followLogCount = 0

    /**
     * Clean up resources.
     */
    fun shutdown() {
        isShutdown = true
        renderJob?.cancel()
        extrapolationJob?.cancel()
        scope.cancel()
        synchronized(surfaceLock) {
            surface?.release()
            surface = null
            surfaceWidth = 0
            surfaceHeight = 0
            surfaceFailed = false
            clearOverrunBuffer()
        }
    }

    /**
     * Request an asynchronous re-render of the surface (map + overlays).
     * No-op when shut down.
     */
    fun requestRender() {
        if (isShutdown) return
        pendingRender = true
        renderSignal.value = System.nanoTime()
    }

    private fun startRenderLoop() {
        renderJob = scope.launch {
            var lastRender = 0L
            renderSignal.collect { ts ->
                if (!asyncLoopsEnabled) return@collect
                if (isShutdown || paused || surfaceFailed) return@collect
                if (ts == lastRender) return@collect
                delay(RENDER_DEBOUNCE_MS)
                lastRender = renderSignal.value
                if (!pendingRender) return@collect
                pendingRender = false
                renderFrame()
            }
        }
    }

    /**
     * Gated extrapolation display loop (spec: auto-smooth-follow, design D3/D6).
     *
     * Runs at ~30 fps while the renderer is resumed, follow mode is active, the
     * vehicle is moving (speed > ~1 m/s), the fix is fresh, and the surface is
     * valid. Each tick eases the displayed position toward the prediction and
     * blits the overrun buffer by the delta; when the display hits the overrun
     * margin a full render is requested at the display position.
     */
    private fun startExtrapolationLoop() {
        extrapolationJob = scope.launch {
            var lastFrameMs = 0L
            while (isActive) {
                if (!asyncLoopsEnabled) {
                    lastFrameMs = 0L
                    delay(EXTRAPOLATION_FRAME_MS)
                    continue
                }
                if (isShutdown) break
                val nowMs = System.currentTimeMillis()
                if (!extrapolationGateActive(nowMs)) {
                    lastFrameMs = 0L
                    // Gated off (stationary / follow disengaged / paused): drop
                    // the display state so the marker falls back to the raw fix
                    // and the display re-initializes from the prediction on the
                    // next moving frame (same as the phone's else branch).
                    displayLat = Double.NaN
                    displayLon = Double.NaN
                    delay(EXTRAPOLATION_FRAME_MS)
                    continue
                }
                val dtSec = if (lastFrameMs > 0) (nowMs - lastFrameMs) / 1000.0 else 0.016
                lastFrameMs = nowMs
                extrapolationTick(nowMs, dtSec)
                delay(EXTRAPOLATION_FRAME_MS)
            }
        }
    }

    /**
     * Gate for the extrapolation loop (design D6): resumed + follow mode +
     * moving + valid surface. A stale fix does NOT gate the loop off —
     * [FollowPrediction] holds the position beyond its extrapolation window,
     * so the display eases back to the last fix instead of freezing ahead of
     * it (same behavior as the phone's per-frame loop). Exposed for
     * deterministic tests.
     */
    internal fun extrapolationGateActive(nowMs: Long): Boolean {
        if (paused || surfaceFailed || !followMode || surface == null) return false
        return !lastFixSpeedMs.isNaN() && lastFixSpeedMs > MOVEMENT_SPEED_MS
    }

    /**
     * One extrapolation tick: ease the displayed position toward the predicted
     * position and blit the overrun buffer by the delta. When the display hits
     * the overrun margin, request a full render at the display position.
     * Exposed for deterministic tests.
     */
    internal fun extrapolationTick(nowMs: Long, dtSec: Double) {
        val surf = surface ?: return
        val w = surfaceWidth
        val h = surfaceHeight
        if (w <= 0 || h <= 0) return
        val predicted = followPrediction.predictedPosition(nowMs)
        if (predicted.first.isNaN() || predicted.second.isNaN()) return
        // Correction easing (design D4): ease the display from the predicted
        // position toward the true fix on arrival instead of snapping.
        val alpha = FollowPrediction.easeAlpha(dtSec, EASE_TAU_SEC)
        if (displayLat.isNaN() || displayLon.isNaN()) {
            displayLat = predicted.first
            displayLon = predicted.second
        } else {
            displayLat += (predicted.first - displayLat) * alpha
            displayLon += (predicted.second - displayLon) * alpha
        }
        synchronized(surfaceLock) {
            // Read + blit under the shared lock: a concurrent full render
            // recycles the overrun bitmap when it swaps in a new one — drawing
            // a recycled bitmap crashes. Holding the lock from the read
            // through the draw serializes against fullRender's swap.
            val current = overrunBitmap ?: return
            val offset = FollowPrediction.displayOffsetPx(
                displayLat, displayLon,
                overrunLat, overrunLon, overrunMag, overrunAngle,
                current.width, current.height, w, h, projectionDpi
            )
            // Diagnostic (mirrors the phone's MapCanvasScreen follow log): fix
            // vs predicted vs displayed vs offset, so an AA logcat shows
            // exactly where the overshoot comes from.
            if (followLogCount++ % 30 == 0) {
                val dbg = followPrediction.debugState(nowMs)
                android.util.Log.d(
                    TAG,
                    "follow fix=" + "%.6f".format(lastFixLat) + "," + "%.6f".format(lastFixLon) +
                        " spd=" + (if (lastFixSpeedMs.isNaN()) "-" else "%.1f".format(lastFixSpeedMs * 3.6)) +
                        " eff=" + "%.1f".format(dbg.effectiveSpeedMs * 3.6) +
                        " dec=" + dbg.decelerating +
                        " stp=" + dbg.stopped +
                        " pred=" + "%.6f".format(predicted.first) + "," + "%.6f".format(predicted.second) +
                        " disp=" + "%.6f".format(displayLat) + "," + "%.6f".format(displayLon) +
                        " off=" + "%.1f".format(offset.clampedX) + "," + "%.1f".format(offset.clampedY) +
                        " clamped=" + offset.clamped
                )
            }
            if (offset.clamped) {
                // Display hit the overrun margin → full render at the display
                // position (throttled so a fast vehicle cannot queue renders).
                if (nowMs - lastRenderRequestMs > RENDER_REQUEST_INTERVAL_MS) {
                    lastRenderRequestMs = nowMs
                    blitEligible = false
                    viewportLat = displayLat
                    viewportLon = displayLon
                    emitViewportState()
                    requestRender()
                }
                return
            }
            blitToSurface(surf, current, offset.clampedX, offset.clampedY, w, h)
        }
    }

    /**
     * Render one frame: serve a viewport change within the overrun region by a
     * sub-region blit, otherwise perform a full native render at overrun size.
     * Exposed for deterministic tests.
     */
    internal fun renderFrame() {
        val surf = surface ?: return
        val w = surfaceWidth
        val h = surfaceHeight
        if (w <= 0 || h <= 0) return
        // Blit decision + draw under the shared lock (see extrapolationTick:
        // a concurrent full render recycles the overrun bitmap). The full
        // render itself runs OUTSIDE the lock so the extrapolation loop can
        // keep blitting the old frame while the native render is in flight.
        val blit = synchronized(surfaceLock) {
            val ob = overrunBitmap
            if (blitEligible && ob != null &&
                viewportZoom == overrunMag && viewportAngle == overrunAngle
            ) {
                val offset = FollowPrediction.displayOffsetPx(
                    viewportLat, viewportLon,
                    overrunLat, overrunLon, overrunMag, overrunAngle,
                    ob.width, ob.height, w, h, projectionDpi
                )
                if (!offset.clamped) {
                    // Viewport change within the overrun region → blit, no
                    // native render (spec: auto-map-renderer, design D2).
                    blitEligible = false
                    if (!followMode) {
                        displayLat = viewportLat
                        displayLon = viewportLon
                    }
                    blitToSurface(surf, ob, offset.clampedX, offset.clampedY, w, h)
                    true
                } else {
                    blitEligible = false
                    false
                }
            } else {
                blitEligible = false
                false
            }
        }
        if (!blit) fullRender(surf, w, h)
    }

    /**
     * Full native render at overrun size (design D1): render at ~1.2x the
     * surface size, keep the bitmap as the overrun buffer, and draw the
     * visible region to the surface.
     */
    private fun fullRender(surf: Surface, w: Int, h: Int) {
        val renderW = (w * OVERRUN_FACTOR).toInt()
        val renderH = (h * OVERRUN_FACTOR).toInt()

        // The GPS marker is NOT passed to the native renderer (the JNI
        // setGpsMarker export was removed upstream in favor of Kotlin-side
        // overlays); it is drawn on the canvas in drawToSurface.
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = renderW,
            height = renderH,
            lat = viewportLat,
            lon = viewportLon,
            angle = viewportAngle,
            magnification = viewportZoom,
            routeLats = routeLats,
            routeLons = routeLons,
            favoriteLats = favoriteLats,
            favoriteLons = favoriteLons
        )

        val now = System.currentTimeMillis()
        if (now - lastRenderLogMs > RENDER_LOG_INTERVAL_MS) {
            lastRenderLogMs = now
            com.naviveylin.core.DiagnosticsLog.log(
                "MAP",
                "render center=$viewportLat,$viewportLon mag=$viewportZoom -> " +
                    if (bitmap != null) "bitmap ${bitmap.width}x${bitmap.height}" else "NULL"
            )
        }

        if (bitmap != null) {
            synchronized(surfaceLock) {
                overrunBitmap?.recycle()
                overrunBitmap = bitmap
                overrunLat = viewportLat
                overrunLon = viewportLon
                overrunMag = viewportZoom
                overrunAngle = viewportAngle
                if (!followMode) {
                    // In follow mode the extrapolation loop owns the display
                    // (the eased predicted position); a render triggered by a
                    // transient follow-off (heading-up setViewport) must not
                    // yank the display back to the render target — that
                    // shows up as the vehicle "pumping" between the target
                    // and the fix every fix.
                    displayLat = viewportLat
                    displayLon = viewportLon
                }
            }
            fullRenderCount++
            drawToSurface(surf, bitmap, w, h)
        }
    }

    /**
     * Draw a full-render bitmap to the surface: the overrun-sized bitmap is
     * drawn centered (visible region = viewport center at overrun size).
     */
    private fun drawToSurface(surf: Surface, bitmap: Bitmap, w: Int, h: Int) {
        // The host reuses ONE display surface across screens, so multiple
        // renderers (MapScreen + FreeDrivingScreen) can lock the same Surface
        // concurrently → IllegalArgumentException from lockCanvas ("surface
        // already locked"). Serialize lock/draw/unlock across all renderers.
        synchronized(surfaceLock) {
            var canvas: Canvas? = null
            try {
                // Surface.isValid() false = the host destroyed the underlying
                // buffer queue; lockCanvas would throw IAE. Skip the frame
                // (throttled diagnostics) instead of failing every render.
                if (!surf.isValid) {
                    reportSurfaceFailure(surf, null, "surface invalid (isValid=false)")
                    return
                }
                canvas = surf.lockCanvas(null)
                if (canvas == null) {
                    // Same condition as a failed lock (already-locked or
                    // invalid surface); treat it as a dead surface.
                    reportSurfaceFailure(surf, null, "lockCanvas returned null")
                    return
                }
                android.util.Log.d(TAG, "renderer#$rendererId lock OK surface=${System.identityHashCode(surf)}")
                val dx = ((w - bitmap.width) / 2f).toInt()
                val dy = ((h - bitmap.height) / 2f).toInt()
                canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
                drawGpsMarker(canvas, w, h)
                drawDestinationMarker(canvas, w, h)
                overlayDrawer?.invoke(canvas, w, h)
            } catch (e: Exception) {
                // Surface may be invalid (e.g., during lifecycle transitions)
                // or locked by the host. Log the full stack trace — the
                // message alone is often null.
                reportSurfaceFailure(surf, e, "lockCanvas failed")
            } finally {
                // ALWAYS unlock: skipping unlock on an exception leaves the
                // surface permanently locked, so every later lockCanvas returns
                // null and the map freezes ("Failed to draw to surface: null").
                if (canvas != null) {
                    try {
                        surf.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "renderer#$rendererId failed to unlock surface", e)
                    }
                }
            }
        }
    }

    /**
     * Sub-region blit (design D2): draw the overrun buffer shifted by the
     * display offset directly to the surface — one draw call, no intermediate
     * bitmap, no native render. Serialized on the shared [surfaceLock]; a dead
     * surface goes through the same failure path as a full render.
     */
    private fun blitToSurface(surf: Surface, bitmap: Bitmap, ox: Double, oy: Double, w: Int, h: Int) {
        synchronized(surfaceLock) {
            var canvas: Canvas? = null
            try {
                if (!surf.isValid) {
                    reportSurfaceFailure(surf, null, "surface invalid (isValid=false)")
                    return
                }
                canvas = surf.lockCanvas(null)
                if (canvas == null) {
                    reportSurfaceFailure(surf, null, "lockCanvas returned null")
                    return
                }
                val dx = ((w - bitmap.width) / 2f).toInt() - ox.toInt()
                val dy = ((h - bitmap.height) / 2f).toInt() - oy.toInt()
                canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
                drawGpsMarker(canvas, w, h)
                drawDestinationMarker(canvas, w, h)
                overlayDrawer?.invoke(canvas, w, h)
            } catch (e: Exception) {
                reportSurfaceFailure(surf, e, "blit lockCanvas failed")
            } finally {
                if (canvas != null) {
                    try {
                        surf.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "renderer#$rendererId failed to unlock surface", e)
                    }
                }
            }
        }
        blitCount++
    }

    /** Recycle and drop the overrun buffer (surface change / shutdown). */
    private fun clearOverrunBuffer() {
        overrunBitmap?.recycle()
        overrunBitmap = null
        overrunLat = Double.NaN
        overrunLon = Double.NaN
        overrunMag = 0
        overrunAngle = 0.0
        displayLat = Double.NaN
        displayLon = Double.NaN
    }

    /**
     * Records that the current surface cannot be drawn to, stops the render
     * loop (via [surfaceFailed]) and notifies the owner. Throttled: a dead
     * surface fails every frame.
     */
    private fun reportSurfaceFailure(surf: Surface, e: Throwable?, why: String) {
        val now = System.currentTimeMillis()
        if (now - lastSurfaceFailureLogMs >= SURFACE_FAILURE_LOG_INTERVAL_MS) {
            lastSurfaceFailureLogMs = now
            val msg = "renderer#$rendererId $why valid=${surf.isValid} " +
                "surface=${System.identityHashCode(surf)} " +
                "size=${surfaceWidth}x$surfaceHeight"
            if (e != null) {
                android.util.Log.w(TAG, msg, e)
            } else {
                android.util.Log.w(TAG, msg)
            }
        }
        surfaceFailed = true
        if (now - lastSurfaceFailureCallbackMs >= SURFACE_FAILURE_CALLBACK_INTERVAL_MS) {
            lastSurfaceFailureCallbackMs = now
            onSurfaceFailed?.invoke()
        }
    }
    /**
     * Draws the GPS position marker (accuracy circle + bearing arrow) in the
     * same projection the native renderer used for the map bitmap. In follow
     * mode the marker rides the displayed (eased predicted) position so it
     * glides with the blitted map (spec: auto-smooth-follow, design D7).
     */
    private fun drawGpsMarker(canvas: Canvas, w: Int, h: Int) {
        if (!gpsMarkerVisible || gpsMarkerLat.isNaN() || gpsMarkerLon.isNaN()) return

        val (markerLat, markerLon) = markerPosition()
        val (vpLat, vpLon) = markerViewport()
        val vp = ProjectionUtils.viewport(
            vpLat, vpLon, viewportZoom, w, h, projectionDpi, viewportAngle
        )
        val (x, y) = vp.geoToScreenRotated(markerLat, markerLon)

        if (x.isNaN() || y.isNaN()) return
        if (x < -200 || x > w + 200 || y < -200 || y > h + 200) return

        val density = (projectionDpi / 160.0).toFloat()
        val arrowSize = 14f * density
        val minRadius = 4f * density
        val accuracyThreshold = 20f * density

        // Meters per pixel at the rendered magnification (pixels-per-radian
        // times earth radius). Used for the accuracy circle.
        val scale = ProjectionUtils.computeScale(viewportZoom, w.toDouble(), projectionDpi).scale
        val metersPerPixel = ProjectionUtils.EARTH_RADIUS / scale
        val accuracyRadiusPx = if (gpsMarkerAccuracy > 0.0 && metersPerPixel > 0.0) {
            (gpsMarkerAccuracy / metersPerPixel).coerceAtLeast(minRadius.toDouble()).toFloat()
        } else {
            0f
        }

        val centerX = x.toFloat()
        val centerY = y.toFloat()

        if (accuracyRadiusPx >= accuracyThreshold) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0x1A2196F3.toInt()
            }
            canvas.drawCircle(centerX, centerY, accuracyRadiusPx, fill)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0x662196F3.toInt()
                strokeWidth = 1.5f * density
            }
            canvas.drawCircle(centerX, centerY, accuracyRadiusPx, border)
        }

        // Screen bearing: raw GPS bearing + map rotation (same convention as
        // the phone overlay's ProjectionUtils.screenBearing).
        val rawBearing = if (gpsMarkerBearing >= 0.0) gpsMarkerBearing else 0.0
        val screenBearingDeg = ProjectionUtils.screenBearing(rawBearing, viewportAngle)
        val dirRad = Math.toRadians(screenBearingDeg)
        val dirX = sin(dirRad).toFloat()
        val dirY = -cos(dirRad).toFloat()

        val tip = PointF(centerX + dirX * arrowSize, centerY + dirY * arrowSize)
        val back = PointF(centerX - dirX * arrowSize * 0.5f, centerY - dirY * arrowSize * 0.5f)
        val perpX = -dirY
        val perpY = dirX
        val left = PointF(back.x + perpX * arrowSize * 0.55f, back.y + perpY * arrowSize * 0.55f)
        val right = PointF(back.x - perpX * arrowSize * 0.55f, back.y - perpY * arrowSize * 0.55f)

        fun arrow(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        // Drop shadow, then the arrow itself.
        val shadow = Path().apply {
            moveTo(tip.x, tip.y + 2f * density)
            lineTo(left.x, left.y + 2f * density)
            lineTo(right.x, right.y + 2f * density)
            close()
        }
        canvas.drawPath(shadow, arrow(0x66000000.toInt()))

        val arrowPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
        canvas.drawPath(arrowPath, arrow(0xFF2196F3.toInt()))
    }

    /**
     * Draws the destination marker (pin + name label) in the same projection
     * the native renderer used for the map bitmap (spec:
     * auto-destination-details). In follow mode it projects against the
     * displayed center so it stays anchored to the blitted map.
     */
    private fun drawDestinationMarker(canvas: Canvas, w: Int, h: Int) {
        if (!destMarkerVisible || destMarkerLat.isNaN() || destMarkerLon.isNaN()) return

        val (vpLat, vpLon) = markerViewport()
        val vp = ProjectionUtils.viewport(
            vpLat, vpLon, viewportZoom, w, h, projectionDpi, viewportAngle
        )
        val (x, y) = vp.geoToScreenRotated(destMarkerLat, destMarkerLon)
        if (x.isNaN() || y.isNaN()) return
        if (x < -200 || x > w + 200 || y < -200 || y > h + 200) return

        val density = (projectionDpi / 160.0).toFloat()
        val pinRadius = 9f * density
        val centerX = x.toFloat()
        val centerY = y.toFloat()

        // Pin: dark outline + red fill, tip pointing at the location.
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFB71C1C.toInt()
        }
        val pin = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX - pinRadius, centerY - pinRadius * 2.2f)
            arcTo(
                centerX - pinRadius, centerY - pinRadius * 3.4f,
                centerX + pinRadius, centerY - pinRadius * 0.2f,
                180f, 180f, false
            )
            lineTo(centerX + pinRadius, centerY - pinRadius * 2.2f)
            close()
        }
        canvas.drawPath(pin, outline)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(centerX, centerY - pinRadius * 1.8f, pinRadius * 0.45f, inner)

        // Name label below the pin when known.
        val name = destMarkerName
        if (!name.isNullOrBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF212121.toInt()
                textSize = 13f * density
                isFakeBoldText = true
            }
            val label = name.take(MAX_DEST_LABEL_CHARS)
            val textWidth = textPaint.measureText(label)
            val labelY = centerY + pinRadius * 1.6f + textPaint.textSize
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xCCFFFFFF.toInt()
            }
            canvas.drawRoundRect(
                centerX - textWidth / 2f - 6f * density,
                labelY - textPaint.textSize - 4f * density,
                centerX + textWidth / 2f + 6f * density,
                labelY + 4f * density,
                4f * density, 4f * density, bg
            )
            canvas.drawText(label, centerX - textWidth / 2f, labelY, textPaint)
        }
    }

    private fun emitViewportState() {
        _viewportState.value = ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle)
    }

    /**
     * Position the GPS marker is drawn at: the displayed (eased predicted)
     * position in follow mode, else the raw fix. Exposed for tests.
     */
    internal fun markerPosition(): Pair<Double, Double> =
        if (followMode && !displayLat.isNaN() && !displayLon.isNaN()) {
            displayLat to displayLon
        } else {
            gpsMarkerLat to gpsMarkerLon
        }

    /**
     * Viewport the overlays project against: the displayed center in follow
     * mode, else the render target. Exposed for tests.
     */
    internal fun markerViewport(): Pair<Double, Double> =
        if (followMode && !displayLat.isNaN() && !displayLon.isNaN()) {
            displayLat to displayLon
        } else {
            viewportLat to viewportLon
        }

    /** Size of the current overrun buffer, or null when none. Exposed for tests. */
    internal fun overrunSize(): Pair<Int, Int>? =
        overrunBitmap?.let { it.width to it.height }

    companion object {
        private const val TAG = "AutoMapRenderer"
        private const val RENDER_DEBOUNCE_MS = 100L
        private const val DEFAULT_LATITUDE = 51.5142273
        private const val DEFAULT_LONGITUDE = 7.4652789
        private const val DEFAULT_ZOOM = 12

        private var nextRendererId = 0

        /** Serializes lock/draw/unlock across all renderer instances. */
        private val surfaceLock = Any()

        const val MIN_ZOOM = 1
        const val MAX_ZOOM = 20

        /** Max destination label characters drawn on the map surface. */
        private const val MAX_DEST_LABEL_CHARS = 40

        // --- auto-smooth-follow (spec: auto-smooth-follow) ---

        /** Overrun buffer scale: render at 1.2x surface size (design D1). */
        const val OVERRUN_FACTOR = 1.2

        /** Extrapolation loop period (~30 fps, design D3). */
        const val EXTRAPOLATION_FRAME_MS = 33L

        /** Correction-easing time constant (design D4): matches the phone's
         *  `FollowPrediction.easeAlpha` default (tau = 0.3 s). A shorter tau
         *  makes the display run further ahead of the fix and the fix-arrival
         *  correction jerk back — the phone's tuned value is 0.3 s. */
        const val EASE_TAU_SEC = 0.3

        /** Movement gate: extrapolation runs only above ~1.8 km/h (design D6),
         *  matching the phone's `fix.speedKmH > 1.8` check. */
        const val MOVEMENT_SPEED_MS = 0.5

        /** Throttle for full-render requests from the extrapolation loop. */
        const val RENDER_REQUEST_INTERVAL_MS = 500L
    }
}
