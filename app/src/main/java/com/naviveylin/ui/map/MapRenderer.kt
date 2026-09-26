package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.data.RenderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Render pipeline with double buffering, debounce, sub-region blit, and tile cache.
 *
 * Ported from JavaScout's MapRenderer.java. Uses coroutine-safe Channel for
 * job queueing instead of wait/notify to avoid blocking the main thread.
 */
class MapRenderer(
    private val client: OSMScoutClient,
    private val dpi: Double,
    private val scope: CoroutineScope
) {
    init {
        // One line per renderer instance: the projection DPI is part of every render
        // request (spec: `render-projection-dpi`), so this names the value the phone
        // canvas is drawing with in logcat without per-frame noise.
        Log.d(TAG, "MapRenderer created: projection dpi=$dpi")
    }

    private val panDebounceMs = 50L
    private val zoomDebounceMs = 200L
    private val rotateDebounceMs = 50L
    private val slowRenderThresholdMs = 500L

    // ---- Viewport state ----
    @Volatile var currentLat = DEFAULT_LATITUDE
    @Volatile var currentLon = DEFAULT_LONGITUDE
    @Volatile var currentMag = DEFAULT_MAGNIFICATION
    @Volatile var currentAngle = 0.0

    // ---- Render mode ----
    /**
     * Rendering strategy: TILES uses the geographic tile cache and renders only
     * missing tiles natively; DIRECT always renders the full viewport natively
     * (no tile cache). Read at job execution, so a switch applies at the next
     * render without touching queued jobs.
     */
    @Volatile var renderMode: RenderMode = RenderMode.TILES

    // ---- Canvas dimensions ----
    @Volatile var screenWidth = 0
    @Volatile var screenHeight = 0
    @Volatile var canvasOverrun = DEFAULT_CANVAS_OVERRUN

    // ---- Overlay data ----
    @Volatile private var favoriteLats: DoubleArray? = null
    @Volatile private var favoriteLons: DoubleArray? = null
    @Volatile private var searchSelectedLat = Double.NaN
    @Volatile private var searchSelectedLon = Double.NaN

    // ---- Route overlay data ----
    @Volatile private var routeLats: DoubleArray? = null
    @Volatile private var routeLons: DoubleArray? = null
    @Volatile private var routeStartLat = Double.NaN
    @Volatile private var routeStartLon = Double.NaN
    @Volatile private var routeDestLat = Double.NaN
    @Volatile private var routeDestLon = Double.NaN

    // ---- GPS marker state (overlay input, snapshotted per render job) ----
    // Storage only: never drawn natively, never triggers renders. The marker
    // rides with each emitted frame so the overlay stays on the road of the
    // displayed bitmap (no lead/jump while frames lag the live fix).
    @Volatile private var gpsMarkerLat = Double.NaN
    @Volatile private var gpsMarkerLon = Double.NaN
    @Volatile private var gpsMarkerBearing = Double.NaN
    @Volatile private var gpsMarkerAccuracy = 0.0

    // ---- Double buffers ----
    private val bufferLock = ReentrantLock()
    private var backBuffer: Bitmap? = null
    private var frontBuffer: Bitmap? = null
    private var frontBufferEpoch = -1L
    private var frontBufferLat = 0.0
    private var frontBufferLon = 0.0
    private var frontBufferMag = 0.0
    /** The angle (radians) of the most recently rendered map. */
    val renderedAngle: Double get() = frontBufferAngle
    private var frontBufferAngle = 0.0

    // ---- Last emitted frame (reused when the front buffer did not change) ----
    private var frontBufferSeq = 0L
    private var lastEmittedSeq = -1L
    private var lastEmittedFrame: Bitmap? = null
    private var lastEmittedWidth = 0
    private var lastEmittedHeight = 0

    /** Magnification of the most recently completed native render (front buffer). */
    val lastRenderedMagnification: Double get() = frontBufferMag

    // ---- Tile cache ----
    private val tileCache = TileCache()

    // ---- Epoch for stale render detection ----
    private val epoch = AtomicLong(0)
    private val logCounter = AtomicInteger(0)

    // ---- Frame emitted to UI (atomic: bitmap + producing viewport + marker snapshot) ----

    /** Viewport state used for the currently visible map image (matches JavaScout current*). */
    data class RenderViewport(
        val lat: Double,
        val lon: Double,
        val mag: Double,
        val angle: Double
    )

    /** Marker state carried by the most recently emitted front buffer frame. */
    data class MarkerSnapshot(
        val lat: Double,
        val lon: Double,
        val bearing: Double,
        val accuracy: Double
    ) {
        val visible: Boolean get() = !lat.isNaN() && !lon.isNaN()
    }

    /** One atomic emission per rendered frame — bitmap, the viewport that produced it,
     *  and the marker state that rode with it. The overlay consumes all three together,
     *  so it can never combine state from different frames. */
    data class FrameState(
        val bitmap: Bitmap?,
        val viewport: RenderViewport,
        val marker: MarkerSnapshot
    )

    private val _frameFlow = MutableStateFlow(
        FrameState(
            null,
            RenderViewport(currentLat, currentLon, currentMag, currentAngle),
            MarkerSnapshot(Double.NaN, Double.NaN, Double.NaN, 0.0)
        )
    )
    val frameFlow: StateFlow<FrameState> = _frameFlow.asStateFlow()

    private val _currentViewportFlow = MutableStateFlow(RenderViewport(currentLat, currentLon, currentMag, currentAngle))
    val currentViewportFlow: StateFlow<RenderViewport> = _currentViewportFlow.asStateFlow()

    /** Last RENDERED viewport position (updated after each render completes). */
    @Volatile var renderedLat = DEFAULT_LATITUDE
    @Volatile var renderedLon = DEFAULT_LONGITUDE
    @Volatile var renderedMag = DEFAULT_MAGNIFICATION

    // ---- Render job ----
    private data class RenderJob(
        val lat: Double,
        val lon: Double,
        val mag: Double,
        val angle: Double,
        val forceFullRender: Boolean,
        val favoriteLats: DoubleArray?,
        val favoriteLons: DoubleArray?,
        val searchSelectedLat: Double,
        val searchSelectedLon: Double,
        val routeLats: DoubleArray?,
        val routeLons: DoubleArray?,
        val routeStartLat: Double,
        val routeStartLon: Double,
        val routeDestLat: Double,
        val routeDestLon: Double,
        val gpsMarkerLat: Double,
        val gpsMarkerLon: Double,
        val gpsMarkerBearing: Double,
        val gpsMarkerAccuracy: Double,
        val width: Int,
        val height: Int,
        val jobEpoch: Long,
        val queuedMs: Long
    ) {
        val hasOverlays: Boolean
            get() = (favoriteLats != null && favoriteLats!!.isNotEmpty()) ||
                    !searchSelectedLat.isNaN() ||
                    (routeLats != null && routeLats!!.isNotEmpty())
    }

    // ---- Pending render (viewport + marker snapshot at submit time) ----
    @Volatile private var pendingRender: PendingRender? = null

    private data class PendingRender(
        val lat: Double, val lon: Double, val mag: Double, val angle: Double,
        val forceFullRender: Boolean,
        val markerLat: Double, val markerLon: Double,
        val markerBearing: Double, val markerAccuracy: Double
    )

    // ---- Channels (coroutine-safe, non-blocking) ----
    private val debounceSignal = Channel<Unit>(Channel.CONFLATED)
    private val renderQueue = Channel<RenderJob>(Channel.CONFLATED)

    // ---- Jobs ----
    private var debounceJob: Job? = null
    private var renderJob: Job? = null
    private var isShutdown = false

    // ---- View change listeners ----
    private val listeners = mutableListOf<ViewChangeListener>()

    fun interface ViewChangeListener {
        fun onViewChanged(lat: Double, lon: Double, mag: Double, angle: Double)
    }

    fun addViewChangeListener(listener: ViewChangeListener) {
        listeners.add(listener)
    }

    fun removeViewChangeListener(listener: ViewChangeListener) {
        listeners.remove(listener)
    }

    // ---- Public API ----

    fun requestRenderPreserveRoute(lat: Double, lon: Double, mag: Double) {
        requestRenderPreserveRoute(lat, lon, mag, currentAngle)
    }

    fun requestRenderPreserveRoute(lat: Double, lon: Double, mag: Double, angle: Double) {
        val oldLat = currentLat; val oldLon = currentLon
        val oldMag = currentMag; val oldAngle = currentAngle
        currentLat = lat; currentLon = lon; currentMag = mag; currentAngle = angle
        emitCurrentViewport()
        submitDebounced(lat, lon, mag, angle, oldLat, oldLon, oldMag, oldAngle, forceFullRender = false)
    }

    fun requestRender(lat: Double, lon: Double, mag: Double) {
        requestRender(lat, lon, mag, currentAngle)
    }

    fun requestRender(lat: Double, lon: Double, mag: Double, angle: Double) {
        requestRender(lat, lon, mag, angle, forceFullRender = false)
    }

    fun requestRender(lat: Double, lon: Double, mag: Double, angle: Double, forceFullRender: Boolean) {
        val oldLat = currentLat; val oldLon = currentLon
        val oldMag = currentMag; val oldAngle = currentAngle
        currentLat = lat; currentLon = lon; currentMag = mag; currentAngle = angle
        emitCurrentViewport()
        if (DEBUG_RENDER_HOT_PATH) {
            Log.d(TAG, "requestRender mag=" + mag + " (was " + oldMag + ") center=" + lat + "," + lon)
        }
        submitDebounced(lat, lon, mag, angle, oldLat, oldLon, oldMag, oldAngle, forceFullRender)
    }

    /**
     * Render [mag] immediately, bypassing the pan/zoom debounce (spec: smooth-zoom -
     * Eased zoom animation on discrete zoom input; design D3).
     *
     * Used for one walked magnification step: a step is already paced by the LANDING
     * of the previous step's frame, so the debounce would only add latency
     * (`zoomDebounceMs` per step, ~16 steps for a four-level entry).
     *
     * A pending debounced request is RE-TARGETED to this magnification instead of
     * being dropped: a zoom request that fired later would render the PREVIOUS
     * magnification after this one and step the display backward (the walk compares
     * against the front buffer's magnification, so a backward frame stalls it). A
     * pending forced overlay render keeps its flag, so a route/favourites/search
     * update in flight is never lost (spec: map-render - Forced overlay renders are
     * never dropped).
     */
    fun requestRenderImmediate(lat: Double, lon: Double, mag: Double, angle: Double) {
        val oldMag = currentMag
        currentLat = lat; currentLon = lon; currentMag = mag; currentAngle = angle
        emitCurrentViewport()
        Log.d(
            TAG,
            "requestRenderImmediate mag=" + mag + " (was " + oldMag + ") center=" + lat + "," + lon
        )
        val pending = pendingRender
        if (pending != null) {
            pendingRender = pending.copy(lat = lat, lon = lon, mag = mag, angle = angle)
        }
        enqueueRenderJob(
            lat, lon, mag, angle,
            forceFullRender = pending?.forceFullRender == true,
            markerLat = gpsMarkerLat,
            markerLon = gpsMarkerLon,
            markerBearing = gpsMarkerBearing,
            markerAccuracy = gpsMarkerAccuracy
        )
    }

    private fun emitCurrentViewport() {
        _currentViewportFlow.value = RenderViewport(currentLat, currentLon, currentMag, currentAngle)
    }

    fun setRoute(
        routeLats: DoubleArray?,
        routeLons: DoubleArray?,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ) {
        this.routeLats = routeLats
        this.routeLons = routeLons
        routeStartLat = startLat
        routeStartLon = startLon
        routeDestLat = destLat
        routeDestLon = destLon
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    fun clearRoute() {
        routeLats = null
        routeLons = null
        routeStartLat = Double.NaN
        routeStartLon = Double.NaN
        routeDestLat = Double.NaN
        routeDestLon = Double.NaN
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    fun setFavoriteLocations(favorites: Array<com.framstag.libosmscout.client.FavoriteLocation>) {
        if (favorites.isNotEmpty()) {
            val lats = DoubleArray(favorites.size)
            val lons = DoubleArray(favorites.size)
            for (i in favorites.indices) {
                lats[i] = favorites[i].lat
                lons[i] = favorites[i].lon
            }
            favoriteLats = lats
            favoriteLons = lons
        } else {
            favoriteLats = null
            favoriteLons = null
        }
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    fun setSearchSelected(lat: Double, lon: Double) {
        searchSelectedLat = lat
        searchSelectedLon = lon
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    fun clearSearchSelected() {
        searchSelectedLat = Double.NaN
        searchSelectedLon = Double.NaN
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    /**
     * Record the marker state for the next render job. Storage only — the marker
     * is drawn by the Compose overlay from the frame snapshot, never natively.
     */
    fun setGpsMarkerState(lat: Double, lon: Double, bearing: Double, accuracy: Double) {
        gpsMarkerLat = lat
        gpsMarkerLon = lon
        gpsMarkerBearing = bearing
        gpsMarkerAccuracy = accuracy
    }

    /** Clear the marker; the next emitted frame carries no marker. */
    fun clearGpsMarkerState() {
        gpsMarkerLat = Double.NaN
        gpsMarkerLon = Double.NaN
        gpsMarkerBearing = Double.NaN
        gpsMarkerAccuracy = 0.0
        bufferLock.withLock {
            emitFrame(
                RenderViewport(frontBufferLat, frontBufferLon, frontBufferMag, frontBufferAngle),
                MarkerSnapshot(Double.NaN, Double.NaN, Double.NaN, 0.0)
            )
        }
    }

    /**
     * Emit the current front buffer to the UI. The bitmap handed to Compose is
     * reused when the front buffer content is unchanged since the last emission
     * (same front-buffer sequence) AND the previously emitted bitmap has the same
     * dimensions as the buffer being emitted — unchanged frames allocate no new
     * bitmap. The emitted bitmap is always an independent copy (never shares
     * backing storage with the front buffer, which the next render overwrites).
     * The previous emitted bitmap is never recycled here: Compose may still be
     * drawing it; GC reclaims it once unreferenced.
     */
    private fun emitFrame(viewport: RenderViewport, marker: MarkerSnapshot) {
        val fb = frontBuffer ?: return
        val reuse = frontBufferSeq == lastEmittedSeq &&
            lastEmittedFrame != null &&
            lastEmittedWidth == fb.width &&
            lastEmittedHeight == fb.height
        if (!reuse) {
            lastEmittedFrame = fb.copy(Bitmap.Config.ARGB_8888, true)
            lastEmittedSeq = frontBufferSeq
            lastEmittedWidth = fb.width
            lastEmittedHeight = fb.height
        }
        _frameFlow.value = FrameState(lastEmittedFrame, viewport, marker)
    }

    /**
     * Update the renderer's target viewport without submitting a render. Use this
     * in follow mode so the next render is centered on the new position, not the
     * previous frame's center.
     */
    fun prepareViewport(lat: Double, lon: Double, mag: Double, angle: Double) {
        currentLat = lat
        currentLon = lon
        currentMag = mag
        currentAngle = normalizeAngle(angle)
        emitCurrentViewport()
        Log.d(TAG, "prepareViewport lat=${"%.6f".format(lat)} lon=${"%.6f".format(lon)} mag=$mag angle=${Math.toDegrees(currentAngle)}")
    }

    /**
     * Invalidate all cached tiles and force a full re-render.
     * Used after a style sheet change (e.g. daylight flag) so no tiles or
     * front-buffer content from the previous style variant survive.
     */
    fun invalidateStyle() {
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    /**
     * Invalidate all cached tiles and force a full re-render after map data
     * changed (e.g. the basemap was downloaded, updated, or deleted while the
     * app runs). Same mechanics as [invalidateStyle]: cached tiles were
     * rendered without the new data and must not survive.
     */
    fun invalidateData() {
        epoch.incrementAndGet()
        tileCache.clear()
        submitDebounced(currentLat, currentLon, currentMag, currentAngle,
            currentLat, currentLon, currentMag, currentAngle, forceFullRender = true)
    }

    fun shutdown() {
        isShutdown = true
        debounceJob?.cancel()
        renderJob?.cancel()
        debounceSignal.close()
        renderQueue.close()
        bufferLock.withLock {
            backBuffer?.recycle(); frontBuffer?.recycle()
            backBuffer = null; frontBuffer = null
        }
        tileCache.clear()
    }

    // ---- Internal: Debounce ----

    private fun submitDebounced(
        lat: Double, lon: Double, mag: Double, angle: Double,
        oldLat: Double, oldLon: Double, oldMag: Double, oldAngle: Double,
        forceFullRender: Boolean
    ) {
        val isZoom = mag != oldMag || angle != oldAngle || forceFullRender

        if (DEBUG_RENDER_HOT_PATH) {
            Log.d(TAG, "submitDebounced mag=" + mag + " (old " + oldMag + ") zoom=" + isZoom + " force=" + forceFullRender + " frontMag=" + frontBufferMag)
        }

        // A window shift inside the overrun frame is served by the DISPLAY (the
        // frame is drawn at its display offset, see spec canvas-overrun — a pan
        // inside the margin moves the window without native work). The renderer
        // only decides whether a render is still needed: covered → no job.
        var windowCovered = false
        if (frontBuffer != null) {
            windowCovered = overrunWindowCovers(lat, lon, mag, angle)
        }
        if (!isZoom && windowCovered) {
            // No render is needed, but a pending forced render
            // (forceFullRender=true: route set/clear, favorites, search selection,
            // stylesheet switch, epoch bump) changed overlays, not tiles — discarding
            // it would leave the new overlay undrawn until some gesture triggers a
            // full render (stale route after reroute). Keep forced renders so they
            // execute after the debounce even when the camera never moved.
            if (pendingRender?.forceFullRender != true) {
                pendingRender = null
            }
            return
        }

        pendingRender = PendingRender(lat, lon, mag, angle, forceFullRender,
            gpsMarkerLat, gpsMarkerLon, gpsMarkerBearing, gpsMarkerAccuracy)
        debounceSignal.trySend(Unit)

        if (debounceJob == null || debounceJob!!.isCompleted) {
            startDebounceLoop()
        }
    }

    private fun startDebounceLoop() {
        debounceJob = scope.launch {
            while (!isShutdown) {
                // Wait for signal
                debounceSignal.receive()
                if (isShutdown) break

                // Determine debounce duration from current pending render type.
                // Compare against the FRONT BUFFER mag (currentMag is already
                // updated to the requested mag by requestRender, so comparing
                // against it would always report "not a zoom").
                val req = pendingRender ?: continue
                val isZoom = req.mag != frontBufferMag
                val isRotate = req.angle != frontBufferAngle
                val timeout = when {
                    isZoom -> zoomDebounceMs
                    isRotate -> rotateDebounceMs
                    else -> panDebounceMs
                }

                delay(timeout)
                if (isShutdown) break

                val finalReq = pendingRender ?: continue
                pendingRender = null
                Log.d(TAG, "debounce enqueue mag=" + finalReq.mag + " (zoom=" + isZoom + ", timeout=" + timeout + ")")
                enqueueRenderJob(finalReq.lat, finalReq.lon, finalReq.mag, finalReq.angle, finalReq.forceFullRender,
                    finalReq.markerLat, finalReq.markerLon, finalReq.markerBearing, finalReq.markerAccuracy)
            }
        }
    }

    // ---- Internal: Render job queue ----

    private fun enqueueRenderJob(
        lat: Double, lon: Double, mag: Double, angle: Double, forceFullRender: Boolean,
        markerLat: Double, markerLon: Double, markerBearing: Double, markerAccuracy: Double
    ) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "enqueueRenderJob skipped: screen " + screenWidth + "x" + screenHeight)
            return
        }
        // Render at overrun size for sub-region blit during pan.
        // extractCenterRegion() extracts the visible screen-sized portion.
        val renderW = (screenWidth * canvasOverrun).toInt()
        val renderH = (screenHeight * canvasOverrun).toInt()
        val jobEpoch = epoch.get()

        val job = RenderJob(lat, lon, mag, angle, forceFullRender,
            favoriteLats, favoriteLons,
            searchSelectedLat, searchSelectedLon,
            routeLats, routeLons,
            routeStartLat, routeStartLon,
            routeDestLat, routeDestLon,
            markerLat, markerLon, markerBearing, markerAccuracy,
            renderW, renderH, jobEpoch, System.currentTimeMillis())

        renderQueue.trySend(job)

        if (renderJob == null || renderJob!!.isCompleted) {
            startRenderLoop()
        }
    }

    private fun startRenderLoop() {
        renderJob = scope.launch {
            for (job in renderQueue) {
                if (isShutdown) break
                executeRender(job)
            }
        }
    }

    // ---- Internal: Geographic tile cache ----

    /** Pixel size of one geographic tile in the render buffer (256px @ 96dpi scaled by dpi). */
    internal val tileSizePx: Int
        get() = (256.0 * dpi / ProjectionUtils.REFERENCE_DPI).roundToInt()

    /** Inverse Mercator: yFrac in [0,1] from the top of the world → latitude in degrees. */
    internal fun mercatorInv(yFrac: Double): Double =
        Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * yFrac))))

    internal fun tileX(lon: Double, n: Long): Long {
        val x = ((lon + 180.0) / 360.0 * n).toLong()
        return x.coerceIn(0, n - 1)
    }

    internal fun tileY(lat: Double, n: Long): Long {
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toLong()
        return y.coerceIn(0, n - 1)
    }

    internal fun tileTopLeft(x: Long, y: Long, n: Long): Pair<Double, Double> {
        val lon = x / n.toDouble() * 360.0 - 180.0
        val lat = mercatorInv(y / n.toDouble())
        return Pair(lat, lon)
    }

    /**
     * Compose the viewport from cached geographic tiles into [result], rendering only
     * missing tiles via the native renderer. Returns false when the tile path cannot
     * serve the viewport (antimeridian, tile render failure) — the caller then releases
     * the target and falls back to a full render.
     *
     * [result] is a pooled render target (spec: `render-performance` — Reusable render
     * target for map frames); this function never releases or recycles it.
     */
    private suspend fun renderFromTiles(job: RenderJob, result: Bitmap): Boolean {
        // Render at the overrun size (job.width/height) so the emitted frame has
        // margin around the visible region — the follow-mode display loop offsets
        // within that margin to scroll smoothly between GPS fixes.
        val W = result.width; val H = result.height
        if (W <= 0 || H <= 0) return false
        val vp = ProjectionUtils.viewport(job.lat, job.lon, job.mag, W, H, dpi, job.angle)
        val rotated = job.angle != 0.0
        // Visible geo bounds. With rotation the top-left/bottom-right diagonal
        // alone misses the other two corners, so use all four screen corners.
        val corners = if (rotated) {
            listOf(
                vp.screenToGeoRotated(0.0, 0.0),
                vp.screenToGeoRotated(W.toDouble(), 0.0),
                vp.screenToGeoRotated(0.0, H.toDouble()),
                vp.screenToGeoRotated(W.toDouble(), H.toDouble())
            )
        } else {
            listOf(
                vp.screenToGeo(0.0, 0.0),
                vp.screenToGeo(W.toDouble(), H.toDouble())
            )
        }
        val minLat = corners.minOf { it.first }
        val maxLat = corners.maxOf { it.first }
        val minLon = corners.minOf { it.second }
        val maxLon = corners.maxOf { it.second }
        if (maxLon - minLon > 180.0) return false // antimeridian — fall back to full render
        val n = 1L shl floor(job.mag).toInt()
        val xMin = tileX(minLon, n); val xMax = tileX(maxLon, n)
        val yMin = tileY(maxLat, n); val yMax = tileY(minLat, n)
        if (xMax - xMin > 4 || yMax - yMin > 4) return false // sanity guard

        val canvas = Canvas(result)
        val curEpoch = epoch.get()
        val rotationDegrees = Math.toDegrees(job.angle).toFloat()
        var renderedAny = false
        for (y in yMin..yMax) {
            for (x in xMin..xMax) {
                // Abort between tiles when the renderer was shut down (e.g. a
                // re-entry created a new renderer): the blocking JNI render of the
                // current tile still finishes, but the old loop must not keep
                // rendering stale tiles and stall the new renderer (JNI mutex).
                coroutineContext.ensureActive()
                val key = TileCache.TileKey(floor(job.mag).toInt(), x.toInt(), y.toInt(), dpi)
                var tile = tileCache.getLogged(key, curEpoch)
                if (tile == null) {
                    val t0 = System.currentTimeMillis()
                    val pixels = renderTilePixels(x.toInt(), y.toInt(), floor(job.mag).toInt(), job)
                    if (pixels == null) return false
                    val renderMs = System.currentTimeMillis() - t0
                    tile = Bitmap.createBitmap(pixels, tileSizePx, tileSizePx, Bitmap.Config.ARGB_8888)
                    tileCache.put(key, tile, curEpoch)
                    Log.d(TAG, "tile rendered z=" + job.mag + " x=" + x + " y=" + y +
                            " (" + renderMs + "ms, " + tileSizePx + "x" + tileSizePx + ")")
                }
                val (tLat, tLon) = tileTopLeft(x, y, n)
                // continuous-pinch-zoom: tile bitmaps are rendered at their floor
                // integer level; at a fractional magnification the on-screen tile
                // size is 2^(z − floor(z))× the natural tile size. Drawing the
                // tile at its natural size leaves seams and shows shrunken tile
                // content ("smaller map rectangles").
                val tileDisplayPx = tileSizePx * Math.pow(
                    2.0, job.mag - floor(job.mag)
                ).toFloat()
                if (rotated) {
                    // Tiles are rendered north-up. Compose the rotated view by placing
                    // each tile at its north-up position and rotating the whole canvas
                    // about the VIEWPORT CENTER — this reproduces the projection exactly.
                    // Rotating each tile around its own corner instead shifts content by
                    // up to ~d*θ for tiles far from the center (d = distance from center,
                    // θ = rotation), which breaks overlay alignment: the Compose marker
                    // overlay projects about the center, so the map and the marker would
                    // disagree by that same error.
                    val (nux, nuy) = vp.geoToScreen(tLat, tLon)
                    val dst = RectF(
                        nux.toFloat(), nuy.toFloat(),
                        nux.toFloat() + tileDisplayPx, nuy.toFloat() + tileDisplayPx
                    )
                    canvas.save()
                    canvas.rotate(rotationDegrees, W / 2f, H / 2f)
                    canvas.drawBitmap(tile, null, dst, null)
                    canvas.restore()
                    Log.d(TAG, "tile copied z=" + job.mag + " x=" + x + " y=" + y +
                            " at (" + nux.toInt() + "," + nuy.toInt() + ") size=" + tileDisplayPx.toInt() +
                            " rot=" + rotationDegrees.toInt())
                } else {
                    val (px, py) = vp.geoToScreen(tLat, tLon)
                    val dst = RectF(
                        px.toFloat(), py.toFloat(),
                        px.toFloat() + tileDisplayPx, py.toFloat() + tileDisplayPx
                    )
                    canvas.drawBitmap(tile, null, dst, null)
                    Log.d(TAG, "tile copied z=" + job.mag + " x=" + x + " y=" + y +
                            " at (" + px.toInt() + "," + py.toInt() + ") size=" + tileDisplayPx.toInt())
                }
                renderedAny = true
            }
        }
        return renderedAny
    }

    /**
     * Render one geographic tile via the native renderer. A tile is just a
     * viewport centered on the tile at the tile's magnification level, sized
     * so the projection covers exactly one tile (256px @ 96dpi scaled by dpi).
     */
    private fun renderTilePixels(x: Int, y: Int, level: Int, job: RenderJob): IntArray? {
        val n = 1L shl level
        val lonMin = x / n.toDouble() * 360.0 - 180.0
        val lonMax = (x + 1) / n.toDouble() * 360.0 - 180.0
        // mercatorInv already applies the (1 - 2*yFrac) inversion — pass the raw
        // tile yFrac (y/n), NOT (1 - 2*y/n), or the yFrac gets double-inverted and
        // the tile is rendered ~0.5° too far south (wrong map content).
        val latMax = mercatorInv(y / n.toDouble())
        val latMin = mercatorInv((y + 1) / n.toDouble())
        val centerLat = (latMin + latMax) / 2.0
        val centerLon = (lonMin + lonMax) / 2.0
        return client.renderWithRouteAndPois(
            tileSizePx, tileSizePx, centerLat, centerLon, 0.0, 2.0.pow(level),
            dpi,
            job.routeLats, job.routeLons,
            job.favoriteLats, job.favoriteLons,
            job.searchSelectedLat, job.searchSelectedLon,
            null, null
        )
    }

    // ---- Internal: Execute render ----

    private suspend fun executeRender(job: RenderJob) {
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "executeRender start mag=" + job.mag + " epoch=" + job.jobEpoch + " curEpoch=" + epoch.get() +
                " " + job.width + "x" + job.height)

        // Tile path serves north-up views and serves as a fast live preview
        // during the rotation gesture. A forced full render (gesture end) uses
        // the native path so labels are drawn in the correct direction.
        // In DIRECT mode every render is a full native render — no tile cache.
        val tilePath = renderMode == RenderMode.TILES && (job.angle == 0.0 || !job.forceFullRender)
        var bitmap: Bitmap? = null
        if (tilePath && job.width > 0 && job.height > 0) {
            // The tile composition target comes from the pool (spec: render-performance —
            // Reusable render target for map frames) and is released again when the tile
            // path cannot serve this viewport.
            val tileTarget = RenderBitmapPool.acquire(job.width, job.height)
            val served = try {
                renderFromTiles(job, tileTarget)
            } catch (t: Throwable) {
                RenderBitmapPool.release(tileTarget)
                throw t
            }
            if (served) {
                bitmap = tileTarget
                Log.d(TAG, "executeRender: tile path served mag=" + job.mag)
            } else {
                RenderBitmapPool.release(tileTarget)
            }
        }
        if (bitmap == null) {
            for (attempt in 0 until 2) {
                val target = RenderBitmapPool.acquire(job.width, job.height)
                val rendered = try {
                    MapRenderUtil.renderInto(
                        client = client,
                        target = target,
                        lat = job.lat,
                        lon = job.lon,
                        angle = job.angle,
                        magnification = 2.0.pow(job.mag),
                        dpi = dpi,
                        routeLats = job.routeLats,
                        routeLons = job.routeLons,
                        favoriteLats = job.favoriteLats,
                        favoriteLons = job.favoriteLons,
                        searchSelLat = job.searchSelectedLat,
                        searchSelLon = job.searchSelectedLon
                    )
                } catch (e: Exception) {
                    RenderBitmapPool.release(target)
                    if (attempt == 0) {
                        Log.w(TAG, "JNI render failed (retrying): ${e.message}")
                        delay(100)
                        continue
                    }
                    Log.e(TAG, "JNI render failed: ${e.message}")
                    return
                } catch (t: Throwable) {
                    // Includes cancellation: never strand a pooled target.
                    RenderBitmapPool.release(target)
                    throw t
                }
                if (rendered == null) {
                    RenderBitmapPool.release(target)
                    break
                }
                bitmap = rendered
                break
            }
        }
        if (bitmap == null) {
            Log.e(TAG, "executeRender: JNI render returned null at mag=" + job.mag + " — front buffer NOT updated")
            return
        }
        if (job.jobEpoch != epoch.get()) {
            Log.d(TAG, "executeRender: stale epoch " + job.jobEpoch + " != " + epoch.get() + " — discarding mag=" + job.mag)
            RenderBitmapPool.release(bitmap)
            return
        }

        val elapsed = System.currentTimeMillis() - startMs
        val queueWait = startMs - job.queuedMs
        if (elapsed > slowRenderThresholdMs) {
            Log.w(TAG, "Slow render: ${elapsed}ms (queue ${queueWait}ms) at mag=${job.mag} (${job.width}x${job.height})")
        } else if (logCounter.incrementAndGet() % 20 == 0) {
            Log.d(TAG, "render complete ${elapsed}ms (queue ${queueWait}ms) mag=${job.mag} " +
                    "center=${"%.5f".format(job.lat)},${"%.5f".format(job.lon)} " +
                    "angle=${Math.toDegrees(job.angle)}")
        }

        val completionEpoch = epoch.get()
        if (tilePath) {
            // Tile path: bitmap is overrun-sized (rendered at job.width/height).
            // Copy it so the emitted front buffer and the stored front buffer do
            // not share backing storage with the tile-composition result that may
            // be recycled later.
            bufferLock.withLock {
                if (job.jobEpoch != epoch.get()) return@withLock
                frontBuffer?.recycle()
                frontBuffer = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                frontBufferSeq++
                frontBufferEpoch = job.jobEpoch
                frontBufferLat = job.lat; frontBufferLon = job.lon
                frontBufferMag = job.mag; frontBufferAngle = normalizeAngle(job.angle)
                renderedLat = job.lat; renderedLon = job.lon; renderedMag = job.mag
                emitFrame(
                    RenderViewport(frontBufferLat, frontBufferLon, frontBufferMag, frontBufferAngle),
                    MarkerSnapshot(job.gpsMarkerLat, job.gpsMarkerLon, job.gpsMarkerBearing, job.gpsMarkerAccuracy)
                )
            }
            // The composition target is returned to the pool whether or not this frame
            // was committed (spec: render-performance — Reusable render target).
            RenderBitmapPool.release(bitmap)
            Log.d(TAG, "executeRender: front buffer emitted (tiles) mag=" + job.mag + " (" + elapsed + "ms)")
        } else {
            // Rotated/full render path: swap into the double buffer, then extract
            // the screen-sized center region for display. Emit the finished frame
            // whenever epoch and magnification still match; angle drift during a
            // slow render is expected and the next job catches up.
            bufferLock.withLock {
                if (job.jobEpoch != epoch.get()) return@withLock

                if (backBuffer == null || backBuffer!!.width != job.width || backBuffer!!.height != job.height) {
                    backBuffer?.recycle()
                    backBuffer = Bitmap.createBitmap(job.width, job.height, Bitmap.Config.ARGB_8888)
                }
                // Blit rendered bitmap into backBuffer (GPU-accelerated copy, no
                // full-buffer IntArray round-trip). drawBitmap copies pixels into
                // backBuffer's own storage, so the pooled target can be released afterwards.
                Canvas(backBuffer!!).drawBitmap(bitmap!!, 0f, 0f, null)

                val tmp = frontBuffer
                frontBuffer = backBuffer
                backBuffer = tmp
                frontBufferSeq++
                frontBufferEpoch = job.jobEpoch
                frontBufferLat = job.lat; frontBufferLon = job.lon
                frontBufferMag = job.mag; frontBufferAngle = normalizeAngle(job.angle)
                renderedLat = job.lat; renderedLon = job.lon; renderedMag = job.mag
            }
            // Released whether the frame was committed or the epoch went stale: the pixels
            // live in backBuffer/the emitted copy from here on (spec: render-performance —
            // Reusable render target for map frames).
            RenderBitmapPool.release(bitmap)

            if (completionEpoch == epoch.get() && job.mag == frontBufferMag) {
                bufferLock.withLock {
                    emitFrame(
                        RenderViewport(frontBufferLat, frontBufferLon, frontBufferMag, frontBufferAngle),
                        MarkerSnapshot(job.gpsMarkerLat, job.gpsMarkerLon, job.gpsMarkerBearing, job.gpsMarkerAccuracy)
                    )
                }
                Log.d(TAG, "executeRender: front buffer emitted mag=" + job.mag + " (" + elapsed + "ms)")
            } else {
                Log.d(TAG, "executeRender: front buffer NOT emitted — completionEpoch=" + completionEpoch + " cur=" + epoch.get() +
                        " frontMag=" + frontBufferMag + " jobMag=" + job.mag + " jobAngle=" + job.angle)
            }
        }

        for (l in listeners) l.onViewChanged(job.lat, job.lon, job.mag, job.angle)
    }

    // ---- Internal: Overrun window (pan/follow display) ----

    /**
     * Whether the requested window is still served by the overrun frame in hand:
     * the requested center's rotated screen delta from the frame's own viewport
     * center stays inside the overrun margin (minus [BLIT_COVER_SLACK_PX]).
     *
     * Pure predicate — the display applies the offset itself (spec canvas-overrun:
     * Overrun window shift for pan); the renderer only decides whether a native
     * render is still needed. No pixels are copied and no frame is emitted here:
     * a covered request leaves the displayed frame exactly as it is.
     *
     * The margin/slack contract is shared with the display through
     * [FollowPrediction.displayOffsetPx] (canvas inflated by the slack), so the
     * coverage test and the applied clamp can never disagree — the display's clamp
     * is the same computation with the real canvas size.
     *
     * Takes the frame snapshot under [bufferLock] (bitmap + the viewport its pixels
     * were rendered with must match). The lock is never held across a native render —
     * only across the short buffer swap/copy — and the UI path calls this at most
     * once per render request, not per touch event.
     */
    internal fun overrunWindowCovers(
        newLat: Double, newLon: Double, newMag: Double, newAngle: Double
    ): Boolean {
        val sw = screenWidth; val sh = screenHeight
        if (sw <= 0 || sh <= 0) return false
        return bufferLock.withLock {
            val fb = frontBuffer ?: return@withLock false
            // A zoom or an angle change is never served by the overrun frame: the
            // frame in hand has the wrong magnification/rotation (spec map-render).
            if (newMag != frontBufferMag) return@withLock false
            if (newAngle != frontBufferAngle) return@withLock false

            // The window must stay inside the overrun margin MINUS the slack reserve
            // (a request at the very edge would otherwise be "covered" and the map
            // would stick there). A frame whose margin is not wider than the reserve
            // cannot serve any shift at all, and has no margin to clamp into.
            val marginX = (fb.width - sw) / 2.0
            val marginY = (fb.height - sh) / 2.0
            if (marginX <= BLIT_COVER_SLACK_PX || marginY <= BLIT_COVER_SLACK_PX) {
                return@withLock false
            }

            val offset = FollowPrediction.displayOffsetPx(
                newLat, newLon,
                frontBufferLat, frontBufferLon,
                frontBufferMag, frontBufferAngle,
                fb.width, fb.height,
                sw + (2 * BLIT_COVER_SLACK_PX).toInt(),
                sh + (2 * BLIT_COVER_SLACK_PX).toInt(),
                dpi
            )
            !offset.clamped
        }
    }

    private fun normalizeAngle(rad: Double): Double {
        var r = rad
        while (r <= -Math.PI) r += 2.0 * Math.PI
        while (r > Math.PI) r -= 2.0 * Math.PI
        return r
    }

    companion object {
        private const val TAG = "MapRenderer"

        /**
         * Renderer diagnostics on the per-request hot path (a gesture can issue
         * hundreds of requests per second). Off in normal runs — flip locally when
         * debugging the request/debounce path, never in a release build
         * (spec render-performance — Pan hot path stays off the frame budget).
         */
        internal const val DEBUG_RENDER_HOT_PATH = false

        const val DEFAULT_MAGNIFICATION = 5.0
        const val DEFAULT_LATITUDE = 51.5142273
        const val DEFAULT_LONGITUDE = 7.4652789
        const val DEFAULT_CANVAS_OVERRUN = 1.2

        /**
         * Margin (px) reserved around the visible window in the blit covered-check
         * for the follow-mode display loop's prediction offset. Without it a render
         * request at the overrun edge would be "covered" and the map would stick at
         * the edge instead of re-rendering.
         */
        const val BLIT_COVER_SLACK_PX = 32.0
    }
}
