package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.ProjectionUtils
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
        }
    }

    /**
     * Update the GPS position marker.
     */
    fun setGpsMarker(lat: Double, lon: Double, bearing: Double, accuracy: Double) {
        if (lat.isNaN() || lon.isNaN()) {
            gpsMarkerVisible = false
        } else {
            gpsMarkerLat = lat
            gpsMarkerLon = lon
            gpsMarkerBearing = bearing
            gpsMarkerAccuracy = accuracy
            gpsMarkerVisible = true
        }
        if (followMode) {
            viewportLat = lat
            viewportLon = lon
            emitViewportState()
        }
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
        requestRender()
    }

    /**
     * Set the route polyline for map rendering (native "_route" style);
     * null clears it. Re-renders on change.
     */
    fun setRoute(routeLats: DoubleArray?, routeLons: DoubleArray?) {
        this.routeLats = routeLats
        this.routeLons = routeLons
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
        viewportLat = lat
        viewportLon = lon
        viewportZoom = zoom
        viewportZoomFraction = zoomFraction
        viewportAngle = angle
        emitViewportState()
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
     */
    fun reCenter() {
        followMode = true
        if (gpsMarkerVisible) {
            viewportLat = gpsMarkerLat
            viewportLon = gpsMarkerLon
            emitViewportState()
            requestRender()
        }
    }

    /** Whether follow mode is currently active. */
    fun isFollowMode(): Boolean = followMode

    // Throttle for file-backed diagnostics (pan events are frequent).
    private var lastRenderLogMs = 0L
    private val RENDER_LOG_INTERVAL_MS = 1000L

    /**
     * Clean up resources.
     */
    fun shutdown() {
        isShutdown = true
        renderJob?.cancel()
        scope.cancel()
        synchronized(surfaceLock) {
            surface?.release()
            surface = null
            surfaceWidth = 0
            surfaceHeight = 0
            surfaceFailed = false
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

    private fun renderFrame() {
        val surf = surface ?: return
        val w = surfaceWidth
        val h = surfaceHeight
        if (w <= 0 || h <= 0) return

        // The GPS marker is NOT passed to the native renderer (the JNI
        // setGpsMarker export was removed upstream in favor of Kotlin-side
        // overlays); it is drawn on the canvas in drawToSurface.
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = w,
            height = h,
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
            drawToSurface(surf, bitmap)
            bitmap.recycle()
        }
    }

    private fun drawToSurface(surf: Surface, bitmap: Bitmap) {
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
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                drawGpsMarker(canvas, bitmap.width, bitmap.height)
                drawDestinationMarker(canvas, bitmap.width, bitmap.height)
                overlayDrawer?.invoke(canvas, bitmap.width, bitmap.height)
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
     * same projection the native renderer used for the map bitmap.
     */
    private fun drawGpsMarker(canvas: Canvas, w: Int, h: Int) {
        if (!gpsMarkerVisible || gpsMarkerLat.isNaN() || gpsMarkerLon.isNaN()) return

        val vp = ProjectionUtils.viewport(
            viewportLat, viewportLon, viewportZoom, w, h, projectionDpi, viewportAngle
        )
        val (x, y) = vp.geoToScreenRotated(gpsMarkerLat, gpsMarkerLon)
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
     * auto-destination-details).
     */
    private fun drawDestinationMarker(canvas: Canvas, w: Int, h: Int) {
        if (!destMarkerVisible || destMarkerLat.isNaN() || destMarkerLon.isNaN()) return

        val vp = ProjectionUtils.viewport(
            viewportLat, viewportLon, viewportZoom, w, h, projectionDpi, viewportAngle
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
    }
}
