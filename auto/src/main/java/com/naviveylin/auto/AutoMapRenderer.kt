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
    initialZoom: Int = DEFAULT_ZOOM
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
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var isShutdown = false

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

    // Follow mode
    @Volatile private var followMode = true

    private val renderSignal = MutableStateFlow(0L)
    private var pendingRender = false

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
     * Called when the [Surface] is created (from [MapController.SurfaceCallback]).
     */
    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.surfaceWidth = width
        this.surfaceHeight = height
        requestRender()
    }

    /**
     * Called when the [Surface] is destroyed (from [MapController.SurfaceCallback]).
     */
    fun onSurfaceDestroyed() {
        surface = null
        surfaceWidth = 0
        surfaceHeight = 0
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
        surface = null
    }

    private fun requestRender() {
        if (isShutdown) return
        pendingRender = true
        renderSignal.value = System.nanoTime()
    }

    private fun startRenderLoop() {
        renderJob = scope.launch {
            var lastRender = 0L
            renderSignal.collect { ts ->
                if (isShutdown) return@collect
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
        try {
            val canvas: Canvas = surf.lockCanvas(null)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            drawGpsMarker(canvas, bitmap.width, bitmap.height)
            surf.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            // Surface may be invalid (e.g., during lifecycle transitions)
            android.util.Log.w(TAG, "Failed to draw to surface: ${e.message}")
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

    private fun emitViewportState() {
        _viewportState.value = ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle)
    }

    companion object {
        private const val TAG = "AutoMapRenderer"
        private const val RENDER_DEBOUNCE_MS = 100L
        private const val DEFAULT_LATITUDE = 51.5142273
        private const val DEFAULT_LONGITUDE = 7.4652789
        private const val DEFAULT_ZOOM = 12

        const val MIN_ZOOM = 1
        const val MAX_ZOOM = 20
    }
}
