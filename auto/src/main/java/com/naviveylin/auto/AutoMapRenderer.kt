package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.MapRenderUtil
import kotlin.math.roundToInt
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
 */
class AutoMapRenderer(
    private val client: OSMScoutClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var isShutdown = false

    // Viewport state
    @Volatile private var viewportLat = DEFAULT_LATITUDE
    @Volatile private var viewportLon = DEFAULT_LONGITUDE
    @Volatile private var viewportZoom = DEFAULT_ZOOM
    @Volatile private var viewportZoomFraction = DEFAULT_ZOOM.toDouble()
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

        // Set GPS marker on client before rendering
        client.setGpsMarker(
            if (gpsMarkerVisible) gpsMarkerLat else Double.NaN,
            if (gpsMarkerVisible) gpsMarkerLon else Double.NaN,
            if (gpsMarkerVisible && gpsMarkerBearing >= 0.0) gpsMarkerBearing else -1.0,
            if (gpsMarkerVisible && gpsMarkerAccuracy > 0.0) gpsMarkerAccuracy else -1.0
        )

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

        if (bitmap != null) {
            drawToSurface(surf, bitmap)
            bitmap.recycle()
        }
    }

    private fun drawToSurface(surf: Surface, bitmap: Bitmap) {
        try {
            val canvas: Canvas = surf.lockCanvas(null)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            surf.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            // Surface may be invalid (e.g., during lifecycle transitions)
            android.util.Log.w(TAG, "Failed to draw to surface: ${e.message}")
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
        private const val DEFAULT_ZOOM = 5

        const val MIN_ZOOM = 1
        const val MAX_ZOOM = 20
    }
}
