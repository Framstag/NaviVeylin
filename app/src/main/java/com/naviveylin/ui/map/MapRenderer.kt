package com.naviveylin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.data.RenderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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
    @Volatile var canvasOverrun = 1.2

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
    private var frontBufferMag = 0
    /** The angle (radians) of the most recently rendered map. */
    val renderedAngle: Double get() = frontBufferAngle
    private var frontBufferAngle = 0.0

    /** Magnification of the most recently completed native render (front buffer). */
    val lastRenderedMagnification: Int get() = frontBufferMag

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
        val mag: Int,
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
        val mag: Int,
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
        val lat: Double, val lon: Double, val mag: Int, val angle: Double,
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
        fun onViewChanged(lat: Double, lon: Double, mag: Int, angle: Double)
    }

    fun addViewChangeListener(listener: ViewChangeListener) {
        listeners.add(listener)
    }

    fun removeViewChangeListener(listener: ViewChangeListener) {
        listeners.remove(listener)
    }

    // ---- Public API ----

    fun requestRenderPreserveRoute(lat: Double, lon: Double, mag: Int) {
        requestRenderPreserveRoute(lat, lon, mag, currentAngle)
    }

    fun requestRenderPreserveRoute(lat: Double, lon: Double, mag: Int, angle: Double) {
        val oldLat = currentLat; val oldLon = currentLon
        val oldMag = currentMag; val oldAngle = currentAngle
        currentLat = lat; currentLon = lon; currentMag = mag; currentAngle = angle
        emitCurrentViewport()
        submitDebounced(lat, lon, mag, angle, oldLat, oldLon, oldMag, oldAngle, forceFullRender = false)
    }

    fun requestRender(lat: Double, lon: Double, mag: Int) {
        requestRender(lat, lon, mag, currentAngle)
    }

    fun requestRender(lat: Double, lon: Double, mag: Int, angle: Double) {
        requestRender(lat, lon, mag, angle, forceFullRender = false)
    }

    fun requestRender(lat: Double, lon: Double, mag: Int, angle: Double, forceFullRender: Boolean) {
        val oldLat = currentLat; val oldLon = currentLon
        val oldMag = currentMag; val oldAngle = currentAngle
        currentLat = lat; currentLon = lon; currentMag = mag; currentAngle = angle
        emitCurrentViewport()
        Log.d(TAG, "requestRender mag=" + mag + " (was " + oldMag + ") center=" + lat + "," + lon)
        submitDebounced(lat, lon, mag, angle, oldLat, oldLon, oldMag, oldAngle, forceFullRender)
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
            _frameFlow.value = FrameState(
                frontBuffer?.copy(Bitmap.Config.ARGB_8888, true),
                RenderViewport(frontBufferLat, frontBufferLon, frontBufferMag, frontBufferAngle),
                MarkerSnapshot(Double.NaN, Double.NaN, Double.NaN, 0.0)
            )
        }
    }

    /**
     * Update the renderer's target viewport without submitting a render. Use this
     * in follow mode so the next render is centered on the new position, not the
     * previous frame's center.
     */
    fun prepareViewport(lat: Double, lon: Double, mag: Int, angle: Double) {
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
        lat: Double, lon: Double, mag: Int, angle: Double,
        oldLat: Double, oldLon: Double, oldMag: Int, oldAngle: Double,
        forceFullRender: Boolean
    ) {
        val isZoom = mag != oldMag || angle != oldAngle || forceFullRender

        Log.d(TAG, "submitDebounced mag=" + mag + " (old " + oldMag + ") zoom=" + isZoom + " force=" + forceFullRender + " frontMag=" + frontBufferMag)

        if (logCounter.incrementAndGet() % 20 == 0) {
            Log.d(TAG, "submit lat=" + lat + " lon=" + lon + " mag=" + mag + " zoom=" + isZoom)
        }

        // Try sub-region blit for all changes (handles both pan and zoom placeholder)
        var blitCovered = false
        bufferLock.withLock {
            if (frontBuffer != null) {
                blitCovered = trySubRegionBlit(lat, lon, mag, angle)
            }
        }
        // Only skip full render if pan is fully within overrun buffer (blitCovered=true)
        // For zoom changes, trySubRegionBlit returns false (always triggers full render)
        if (!isZoom && blitCovered) {
            pendingRender = null
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
        lat: Double, lon: Double, mag: Int, angle: Double, forceFullRender: Boolean,
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
     * Render the viewport from cached geographic tiles, rendering only missing
     * tiles via the native renderer. Returns null when the tile path cannot
     * serve the viewport (antimeridian, tile render failure) — the caller then
     * falls back to a full render.
     */
    private suspend fun renderFromTiles(job: RenderJob): Bitmap? {
        val W = screenWidth; val H = screenHeight
        if (W <= 0 || H <= 0) return null
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
        if (maxLon - minLon > 180.0) return null // antimeridian — fall back to full render
        val n = 1L shl job.mag
        val xMin = tileX(minLon, n); val xMax = tileX(maxLon, n)
        val yMin = tileY(maxLat, n); val yMax = tileY(minLat, n)
        if (xMax - xMin > 4 || yMax - yMin > 4) return null // sanity guard

        val result = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
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
                val key = TileCache.TileKey(job.mag, x.toInt(), y.toInt())
                var tile = tileCache.getLogged(key, curEpoch)
                if (tile == null) {
                    val t0 = System.currentTimeMillis()
                    val pixels = renderTilePixels(x.toInt(), y.toInt(), job.mag, job)
                    if (pixels == null) return null
                    val renderMs = System.currentTimeMillis() - t0
                    tile = Bitmap.createBitmap(pixels, tileSizePx, tileSizePx, Bitmap.Config.ARGB_8888)
                    tileCache.put(key, tile, curEpoch)
                    Log.d(TAG, "tile rendered z=" + job.mag + " x=" + x + " y=" + y +
                            " (" + renderMs + "ms, " + tileSizePx + "x" + tileSizePx + ")")
                }
                val (tLat, tLon) = tileTopLeft(x, y, n)
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
                    canvas.save()
                    canvas.rotate(rotationDegrees, W / 2f, H / 2f)
                    canvas.drawBitmap(tile, nux.toFloat(), nuy.toFloat(), null)
                    canvas.restore()
                    Log.d(TAG, "tile copied z=" + job.mag + " x=" + x + " y=" + y +
                            " at (" + nux.toInt() + "," + nuy.toInt() + ") rot=" + rotationDegrees.toInt())
                } else {
                    val (px, py) = vp.geoToScreen(tLat, tLon)
                    canvas.drawBitmap(tile, px.toFloat(), py.toFloat(), null)
                    Log.d(TAG, "tile copied z=" + job.mag + " x=" + x + " y=" + y +
                            " at (" + px.toInt() + "," + py.toInt() + ")")
                }
                renderedAny = true
            }
        }
        return if (renderedAny) result else null
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
            tileSizePx, tileSizePx, centerLat, centerLon, 0.0, level,
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
        if (tilePath) {
            bitmap = renderFromTiles(job)
            if (bitmap != null) {
                Log.d(TAG, "executeRender: tile path served mag=" + job.mag)
            }
        }
        if (bitmap == null) {
            for (attempt in 0 until 2) {
                try {
                    bitmap = MapRenderUtil.renderToBitmap(
                        client = client,
                        width = job.width,
                        height = job.height,
                        lat = job.lat,
                        lon = job.lon,
                        angle = job.angle,
                        magnification = job.mag,
                        routeLats = job.routeLats,
                        routeLons = job.routeLons,
                        favoriteLats = job.favoriteLats,
                        favoriteLons = job.favoriteLons,
                        searchSelLat = job.searchSelectedLat,
                        searchSelLon = job.searchSelectedLon
                    )
                    break
                } catch (e: Exception) {
                    if (attempt == 0) {
                        Log.w(TAG, "JNI render failed (retrying): ${e.message}")
                        delay(100)
                    } else {
                        Log.e(TAG, "JNI render failed: ${e.message}")
                        return
                    }
                }
            }
        }
        if (bitmap == null) {
            Log.e(TAG, "executeRender: JNI render returned null at mag=" + job.mag + " — front buffer NOT updated")
            return
        }
        if (job.jobEpoch != epoch.get()) {
            Log.d(TAG, "executeRender: stale epoch " + job.jobEpoch + " != " + epoch.get() + " — discarding mag=" + job.mag)
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
            // Tile path: bitmap is already screen-sized. Copy it so the emitted
            // front buffer and the stored front buffer do not share backing storage
            // with the tile-composition result that may be recycled later.
            bufferLock.withLock {
                if (job.jobEpoch != epoch.get()) return@withLock
                frontBuffer?.recycle()
                frontBuffer = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                frontBufferEpoch = job.jobEpoch
                frontBufferLat = job.lat; frontBufferLon = job.lon
                frontBufferMag = job.mag; frontBufferAngle = normalizeAngle(job.angle)
                renderedLat = job.lat; renderedLon = job.lon; renderedMag = job.mag
                _frameFlow.value = FrameState(
                    frontBuffer?.copy(Bitmap.Config.ARGB_8888, true),
                    RenderViewport(frontBufferLat, frontBufferLon, frontBufferMag, frontBufferAngle),
                    MarkerSnapshot(job.gpsMarkerLat, job.gpsMarkerLon, job.gpsMarkerBearing, job.gpsMarkerAccuracy)
                )
            }
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
                // Copy rendered bitmap pixels into backBuffer
                val pixels = IntArray(job.width * job.height)
                bitmap!!.getPixels(pixels, 0, job.width, 0, 0, job.width, job.height)
                backBuffer!!.setPixels(pixels, 0, job.width, 0, 0, job.width, job.height)
                bitmap.recycle()

                val tmp = frontBuffer
                frontBuffer = backBuffer
                backBuffer = tmp
                frontBufferEpoch = job.jobEpoch
                frontBufferLat = job.lat; frontBufferLon = job.lon
                frontBufferMag = job.mag; frontBufferAngle = normalizeAngle(job.angle)
                renderedLat = job.lat; renderedLon = job.lon; renderedMag = job.mag
            }

            if (completionEpoch == epoch.get() && job.mag == frontBufferMag) {
                bufferLock.withLock {
                    val fb = frontBuffer ?: return@withLock
                    _frameFlow.value = FrameState(
                        extractCenterRegion(fb),
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

    // ---- Internal: Sub-region blit ----

    private fun trySubRegionBlit(
        newLat: Double, newLon: Double, newMag: Int, newAngle: Double
    ): Boolean {
        val fb = frontBuffer ?: return false
        val fbW = fb.width; val fbH = fb.height
        val sw = screenWidth; val sh = screenHeight
        if (sw <= 0 || sh <= 0) return false

        if (newMag != frontBufferMag) {
            // Do not show a scaled placeholder on zoom changes. A scaled old bitmap has
            // the wrong magnification and looks like a "wrong zoom level" frame while the
            // new native render is in progress. Keep the previous correct frame until the
            // render at the new magnification is ready.
            currentLat = newLat; currentLon = newLon; currentMag = newMag; currentAngle = newAngle
            emitCurrentViewport()
            if (logCounter.incrementAndGet() % 20 == 0) {
                Log.d(TAG, "zoom deferred mag=${frontBufferMag}->${newMag} center=${"%.5f".format(newLat)},${"%.5f".format(newLon)}")
            }
            return false
        }

        if (newAngle != frontBufferAngle) return false

        val (ocx, ocy) = ProjectionUtils.geoToScreen(frontBufferLat, frontBufferLon, sw, sh, frontBufferMag, frontBufferLat, frontBufferLon, dpi)
        val (ncx, ncy) = ProjectionUtils.geoToScreen(newLat, newLon, sw, sh, frontBufferMag, frontBufferLat, frontBufferLon, dpi)
        val dx = ncx - ocx; val dy = ncy - ocy
        // In a rotated viewport the screen-space shift of a geo delta is the
        // unrotated shift rotated by the viewport angle. Without this the blit
        // shifts the map content by the wrong amount (up to ~sin(angle)*move
        // horizontally), causing visible left/right jumps in follow mode.
        val cosA = kotlin.math.cos(frontBufferAngle)
        val sinA = kotlin.math.sin(frontBufferAngle)
        val dxR = dx * cosA - dy * sinA
        val dyR = dx * sinA + dy * cosA

        val viewLeft = fbW / 2.0 - sw / 2.0 + dxR
        val viewTop = fbH / 2.0 - sh / 2.0 + dyR
        val srcX = viewLeft; val srcY = viewTop
        val isx = srcX.coerceAtLeast(0.0); val isy = srcY.coerceAtLeast(0.0)
        val iw = minOf(fbW - isx, sw.toDouble()).coerceAtLeast(0.0)
        val ih = minOf(fbH - isy, sh.toDouble()).coerceAtLeast(0.0)

        currentLat = newLat; currentLon = newLon
        emitCurrentViewport()

        if (iw > 0 && ih > 0) {
            val region = Bitmap.createBitmap(fb, isx.toInt(), isy.toInt(), iw.toInt(), ih.toInt())
            // createBitmap can share the backing buffer with fb. Recycling that shared view
            // would free fb's pixels while Compose may still be drawing it, so copy first.
            val regionCopy = region.copy(Bitmap.Config.ARGB_8888, true)
            region.recycle()
            val result = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(result).apply {
                drawBitmap(regionCopy, (isx - srcX).toFloat(), (isy - srcY).toFloat(), null)
                setBitmap(null)
            }
            regionCopy.recycle()
            _frameFlow.value = FrameState(
                result,
                RenderViewport(currentLat, currentLon, currentMag, frontBufferAngle),
                MarkerSnapshot(gpsMarkerLat, gpsMarkerLon, gpsMarkerBearing, gpsMarkerAccuracy)
            )
        }

        return viewLeft >= 0 && viewTop >= 0 && viewLeft + sw <= fbW && viewTop + sh <= fbH
    }

    private fun extractCenterRegion(fb: Bitmap): Bitmap {
        val sw = screenWidth; val sh = screenHeight
        val fbW = fb.width; val fbH = fb.height
        val region = if (fbW == sw && fbH == sh) {
            fb
        } else {
            Bitmap.createBitmap(fb, (fbW - sw) / 2, (fbH - sh) / 2, sw, sh)
        }
        // Copy into an independent bitmap. createBitmap shares the backing pixel
        // storage with fb; the next render overwrites that storage via
        // backBuffer.setPixels() while Compose may still be drawing this frame,
        // which shows the new content at a stale crop offset (visible left/right
        // jumps). The tile path already copies before recycle.
        return region.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun normalizeAngle(rad: Double): Double {
        var r = rad
        while (r <= -Math.PI) r += 2.0 * Math.PI
        while (r > Math.PI) r -= 2.0 * Math.PI
        return r
    }

    companion object {
        private const val TAG = "MapRenderer"
        const val DEFAULT_MAGNIFICATION = 5
        const val DEFAULT_LATITUDE = 51.5142273
        const val DEFAULT_LONGITUDE = 7.4652789
        const val DEFAULT_CANVAS_OVERRUN = 1.2
    }
}
