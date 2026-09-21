package com.naviveylin.auto

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.Surface
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.FollowDisplayState
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.MapRenderUtil
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.VehicleMarkerGeometry
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import kotlin.math.abs
import kotlin.math.pow
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

    // Resolved dark presentation (preference x host signal) — drives the
    // scheme-aware marker casing (white in day, deep blue-black in dark) and
    // is pushed by the screen alongside the native stylesheet daylight flag.
    @Volatile
    private var darkPresentation = false

    /** Update the dark presentation used by surface-drawn overlays (marker casing). */
    fun setDarkPresentation(dark: Boolean) {
        darkPresentation = dark
    }

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
    /**
     * Magnification the DISPLAYED content is currently scaled to (P3, change
     * `aa-follow-framing-and-zoom-parity`): it eases toward the pending target between
     * frames, so a committed magnification change reaches the eye across frames instead of
     * as a single-frame scale step. The frame's own magnification stays [overrunMag]; the
     * difference is applied as a scale about the follow anchor when drawing. 0.0 = not
     * initialised yet.
     */
    @Volatile private var displayMag = 0.0

    /**
     * Zoom transition target (spec: auto-speed-zoom — Auto-zoom entry transition): a
     * magnification a commit requested that is farther than the blit window
     * ([ZOOM_BLIT_LIMIT]) from the magnification on screen. The frame walks toward it in
     * blit-serviceable steps, one step per LANDED render, so no single frame carries the
     * whole change — entering free driving no longer snaps 13.0 -> 17.0 in one frame.
     * NaN = no transition pending.
     */
    @Volatile private var zoomWalkTarget = Double.NaN

    @Volatile private var overrunBitmap: Bitmap? = null
    @Volatile private var overrunLat = Double.NaN
    @Volatile private var overrunLon = Double.NaN
    @Volatile private var overrunMag = 0.0
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

    // Monotonic forward display (delta fix-aa-follow-vehicle-jumps): the same
    // forward-only hold rule the phone's display loop uses (spec:
    // auto-smooth-follow — forward-only displayed position). Owned by the
    // extrapolation loop thread; the @Volatile displayLat/displayLon fields
    // below remain the cross-thread source for the marker and the margin
    // render target.
    private val followDisplayState = FollowDisplayState(tauSec = EASE_TAU_SEC)

    // Seed-once latch (delta fix-aa-follow-vehicle-jumps): set when a stop
    // episode already re-seeded the display to the fix, so stationary GPS
    // jitter cannot re-anchor the viewport on every fix. Cleared when the
    // vehicle crosses the movement threshold again or the overrun is cleared.
    private var stationarySeedApplied = false
    @Volatile private var lastFixSpeedMs = Double.NaN
    @Volatile private var lastFixTimeMs = 0L
    @Volatile private var lastFixLat = Double.NaN
    @Volatile private var lastFixLon = Double.NaN
    private var extrapolationJob: Job? = null

    /** Zoom-transition loop (spec: auto-speed-zoom — Auto-zoom entry transition). */
    private var zoomWalkJob: Job? = null

    // True when the next render may be served by a sub-region blit (pure
    // viewport or canvas-overlay change). Overlay/native-content changes
    // (favorites, route, DPI, surface) force a full render.
    @Volatile private var blitEligible = false

    // Test-visible counters (spec: auto-smooth-follow verification): a small
    // viewport move must not increment [fullRenderCount].
    @Volatile internal var fullRenderCount = 0
    @Volatile internal var blitCount = 0

    // Whether the last committed viewport change was served by a sub-region
    // blit or a full native render (diagnostic, task 4.1).
    @Volatile internal var lastCommitWasBlit = true

    /** Test seam: when false, the async render/extrapolation loops do not run. */
    @Volatile internal var asyncLoopsEnabled = true

    // Follow mode
    @Volatile private var followMode = initialFollowMode

    /**
     * Follow-mode vehicle anchor (spec: auto/navigation-view — Vehicle anchor
     * during navigation; auto/free-driving — Follow mode activated). When
     * follow mode frames the map, the render target is the [anchorCenter] of
     * the displayed position, so the vehicle marker projects to the anchor
     * screen fraction instead of the surface center. Defaults to the surface
     * center = the pre-feature framing.
     */
    @Volatile private var followAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    /**
     * Host-covered band at the bottom of the surface (px): host chrome that
     * overlays the map bottom (e.g. the AAOS bottom control bar) but is not
     * part of the granted surface. Fed from the host's stable-area bottom
     * ([SurfaceCallback.onStableAreaChanged]); bottom-row anchor presets are
     * clamped above it (design: anchor-per-surface-visible-area, AA vertical
     * clamp). 0 = no bottom chrome known.
     */
    @Volatile
    private var hostBottomInsetPx = 0
    private var hostTopInsetPx = 0

    /**
     * True when the host draws its panel on the RIGHT edge (RTL layouts). Set by the
     * owning screen from the car context; the follow anchor resolves against the
     * surface minus the panel (same side convention as [paneOffsetCenter]).
     */
    @Volatile private var hostPaneRtl = false

    /**
     * Screen-space offset the currently DISPLAYED frame was blitted by (0 = a fresh
     * render). Overlays that represent map content must be shifted by the same
     * offset, otherwise they lead the content between commits (see
     * `markerScreenPosition`).
     */
    @Volatile private var blitOffsetX = 0.0
    @Volatile private var blitOffsetY = 0.0

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
        startZoomWalkLoop()
    }

    // Exposed viewport state for UI
    private val _viewportState = MutableStateFlow(
        ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle, viewportZoomFraction)
    )
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    /** Current viewport state. */
    data class ViewportState(
        val lat: Double,
        val lon: Double,
        val zoom: Int,
        val angle: Double,
        /**
         * The FRACTIONAL magnification the viewport was committed with
         * (spec: auto-speed-zoom — Fractional target is committed, not rounded;
         * change `aa-follow-framing-and-zoom-parity`). [zoom] is the integer model
         * level only; a consumer that commits a viewport without the current
         * magnification (e.g. a fix that carries no new zoom target) MUST use this
         * value — using [zoom] would silently round the magnification to the whole
         * level on that commit, and the display would oscillate between the
         * fractional and the rounded value once per fix.
         */
        val zoomFraction: Double
    )

    /**
     * Optional overlay drawn on the surface after the map bitmap and GPS
     * marker, e.g. the navigation hint panel (see
     * [com.naviveylin.core.ManeuverSymbols]).
     * Invoked on the render loop with the surface canvas and its size.
     */
    var overlayDrawer: ((Canvas, Int, Int) -> Unit)? = null

    /**
     * Called when the [Surface] is created (from [MapController.SurfaceCallback]).
     */
    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        synchronized(surfaceLock) {
            // The session owns the surface's lifetime (spec: car-host-fault-isolation
            // — Single-owner car surface): a renderer never releases the surface it
            // draws on — not on replace, not on stop, not on shutdown. Releasing it
            // here (as before) disconnected the buffer queue the host — or another
            // screen — was still using, which is what made the host re-deliver a
            // surface whose queue it owned.
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
            // Stop drawing only: the session releases the surface (see onSurfaceCreated).
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
            // auto-smooth-follow).
            val moving = !lastFixSpeedMs.isNaN() && lastFixSpeedMs > MOVEMENT_SPEED_MS
            // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
            // "Vehicle stops"): while stationary the display is FROZEN and the
            // viewport re-anchors to the fix exactly ONCE per stop episode
            // (the first stationary fix after moving, or before any display
            // exists) — GPS jitter on later stationary fixes must not re-frame
            // the map. The marker rides the frozen displayed position.
            if (!moving && fixMoved && !stationarySeedApplied) {
                stationarySeedApplied = true
                displayLat = lat
                displayLon = lon
                val (aLat, aLon) = anchorCenterFor(lat, lon)
                viewportLat = aLat
                viewportLon = aLon
                emitViewportState()
                blitEligible = true
                requestRender()
            } else if (moving) {
                stationarySeedApplied = false
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
        zoomFraction: Double = zoom.toDouble(),
        walkZoom: Boolean = false
    ) {
        followMode = false
        val committed = viewportZoomFraction
        // A zoom request farther than the blit window from what is on screen is WALKED
        // across rendered frames instead of being applied in one frame (spec:
        // auto-speed-zoom — Auto-zoom entry transition; design D1/D2). `walkZoom` marks
        // the commits whose magnification comes from auto-zoom, so the car gesture and
        // the zoom buttons keep their immediate response.
        val walked = walkZoom && committed > 0.0 && abs(zoomFraction - committed) > ZOOM_BLIT_LIMIT
        // A re-commit of the value already committed (a fix with no new zoom target)
        // must NOT cancel a pending walk: the screens commit the fraction they read back
        // from the viewport state on every fix.
        val reCommit = !walked && zoomFraction == committed && !zoomWalkTarget.isNaN()
        val zoomChanged = zoom != viewportZoom
        val angleChanged = angle != viewportAngle
        viewportLat = lat
        viewportLon = lon
        viewportAngle = angle
        when {
            walked -> zoomWalkTarget = zoomFraction
            reCommit -> Unit
            else -> {
                zoomWalkTarget = Double.NaN
                viewportZoom = zoom
                viewportZoomFraction = zoomFraction
            }
        }
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
    /**
     * Stop drawing on the current surface without releasing it (the session owns
     * the surface's lifetime — spec: car-host-fault-isolation, design D1).
     */
    fun detachSurface() {
        synchronized(surfaceLock) {
            android.util.Log.d(TAG, "renderer#$rendererId detaching surface: ${surface?.let { System.identityHashCode(it) }}")
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
            val (aLat, aLon) = anchorCenterFor(gpsMarkerLat, gpsMarkerLon)
            viewportLat = aLat
            viewportLon = aLon
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
     * The viewport is anchored on the anchor center of the DISPLAYED position
     * (and emitted), so the pending render (from the preceding [setViewport])
     * renders at the point the shown frame already had at the anchor: the commit
     * changes the centre by the display's own advance only, which the overrun blit
     * serves, and neither the map content nor the marker moves at the commit.
     * Anchoring on the raw fix instead (the previous rule) put the frame
     * `display - fix` px away from where the scene sat and the next blit pulled it
     * back — a ~1 Hz excursion of the extrapolation lead (change
     * `aa-follow-framing-and-zoom-parity`, P1). Before the first display frame the
     * raw fix is the fallback.
     * Unlike [reCenter], the display state is left untouched so the loop's
     * easing continues.
     */
    fun reengageFollow() {
        followMode = true
        if (gpsMarkerVisible) {
            // The follow render target is the anchor center of the DISPLAYED (eased
            // predicted) position — the same point the extrapolation loop advances
            // and blits to — never the raw fix (spec: auto-smooth-follow — Display
            // center extrapolation; change `aa-follow-framing-and-zoom-parity`, P1).
            // Anchoring on the raw fix lands the committed frame (display - fix) px
            // away from where the displayed frame already sat, so the whole scene
            // (map AND marker, which rides the display) jumps by that lead at the
            // commit and the next blit tick pulls it back ~90 ms later: a ~1 Hz
            // excursion of the extrapolation lead (measured 4.3 px mean / 13.1 px max
            // at mag 15-16 over 90 fixes). markerPosition() falls back to the raw fix
            // while no displayed position exists yet.
            val (targetLat, targetLon) = markerPosition()
            val (aLat, aLon) = anchorCenterFor(targetLat, targetLon)
            viewportLat = aLat
            viewportLon = aLon
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

    /**
     * True while a full native render is running (spec: car-host-fault-isolation —
     * Bounded periodic render work; design D7): the extrapolation loop must not queue
     * another full render behind a running one.
     */
    @Volatile
    private var renderInFlight = false

    /**
     * Duration of the last full native render (ms). The request interval stretches to
     * it, so on a slow head unit the loop does not saturate the render thread by
     * re-requesting a frame that cannot keep up.
     */
    @Volatile
    private var lastRenderDurationMs = 0L

    /**
     * Whether the extrapolation loop may request another full render (spec:
     * car-host-fault-isolation — Bounded periodic render work; design D7): never while a
     * full render is in flight, and not before the request interval — which is the base
     * constant or the measured render duration, whichever is longer. Exposed (with
     * injectable state) for deterministic tests.
     */
    internal fun shouldRequestFullRender(
        nowMs: Long,
        lastRequestMs: Long,
        renderInFlightNow: Boolean = renderInFlight,
        lastDurationMs: Long = lastRenderDurationMs
    ): Boolean = !renderInFlightNow &&
        nowMs - lastRequestMs > maxOf(RENDER_REQUEST_INTERVAL_MS, lastDurationMs)

    /** Whether a full render is running right now — exposed for tests. */
    internal fun isRenderInFlight(): Boolean = renderInFlight

    /** Duration of the last full render in ms — exposed for tests. */
    internal fun lastRenderDuration(): Long = lastRenderDurationMs

    // Throttle for the follow diagnostic (mirrors the phone's follow log).
    private var followLogCount = 0

    /**
     * Clean up resources.
     */
    fun shutdown() {
        isShutdown = true
        zoomWalkTarget = Double.NaN
        renderJob?.cancel()
        extrapolationJob?.cancel()
        zoomWalkJob?.cancel()
        scope.cancel()
        synchronized(surfaceLock) {
            // No release: the session owns the surface (spec: car-host-fault-isolation).
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

    /**
     * Force the next frame to a full native render, bypassing the overrun
     * blit. Used after a style/variant change (daylight flag): the overrun
     * buffer holds pixels from the previous variant and must not be blitted
     * (spec: auto-map-renderer — no patterns from the previous variant).
     * Mirrors the phone [MapRenderer.invalidateStyle] contract.
     */
    fun invalidateStyle() {
        if (isShutdown) return
        blitEligible = false
        requestRender()
    }

    /**
     * Force the next frame to a full native render after map data changed
     * (e.g. the basemap was downloaded, updated, or deleted while the app
     * runs). Mirrors the phone [MapRenderer.invalidateData] contract: the
     * overrun buffer may hold pixels rendered without the new data and must
     * not be blitted.
     */
    fun invalidateData() {
        if (isShutdown) return
        blitEligible = false
        requestRender()
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
                    // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-
                    // follow — "Vehicle stops"): the displayed position and
                    // marker stay FROZEN at the last position while the gate
                    // is closed — no NaN reset, no raw-fix fallback. The
                    // resume continues from the frozen position (see
                    // extrapolationTick's gate guard + state re-sync).
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
     * Zoom-transition loop (spec: auto-speed-zoom — Auto-zoom entry transition; design D2).
     *
     * Deliberately NOT the extrapolation loop: that one is gated on vehicle movement
     * ([MOVEMENT_SPEED_MS], [extrapolationGateActive]), which would stall a transition
     * started while parked — entering free driving at standstill would keep the old
     * magnification until the vehicle moves. This loop only needs a usable surface, and
     * while the extrapolation loop is closed it also owns the displayed-magnification
     * easing; otherwise the frames would land at the new magnification while the displayed
     * scale stayed behind (the overrun blit can only show one blit window of it).
     */
    private fun startZoomWalkLoop() {
        zoomWalkJob = scope.launch {
            var lastFrameMs = 0L
            while (isActive) {
                val usable = asyncLoopsEnabled && !isShutdown && !paused && !surfaceFailed &&
                    surface != null
                if (usable) {
                    val nowMs = System.currentTimeMillis()
                    val dtSec = if (lastFrameMs > 0) {
                        (nowMs - lastFrameMs) / 1000.0
                    } else {
                        ZOOM_WALK_FRAME_MS / 1000.0
                    }
                    lastFrameMs = nowMs
                    if (!extrapolationGateActive(nowMs)) advanceDisplayedMagnification(dtSec)
                    advanceZoomWalk()
                } else {
                    lastFrameMs = 0L
                }
                delay(ZOOM_WALK_FRAME_MS)
            }
        }
    }

    /**
     * One step of a pending zoom transition (spec: auto-speed-zoom — Auto-zoom entry
     * transition; design D2). Render-synchronous: a step is committed only once the
     * previous step's frame has LANDED ([overrunMag] caught up with the committed
     * magnification), so the committed value never leads the frame by more than one blit
     * window and the display never has to snap. Steps are bounded by [ZOOM_BLIT_LIMIT],
     * and the last step lands EXACTLY on the requested magnification. Returns the
     * committed magnification when a step was committed, else null. Exposed for tests.
     */
    internal fun advanceZoomWalk(): Double? {
        val target = zoomWalkTarget
        if (target.isNaN()) return null
        if (overrunMag <= 0.0) {
            // No frame on screen yet (cold start): nothing to transition from, so the
            // request lands directly — the spec's "jumps directly to the target instead of
            // smoothing from the default map zoom".
            zoomWalkTarget = Double.NaN
            commitZoom(target)
            return target
        }
        val committed = viewportZoomFraction
        // Render-synchronous: wait for the previous step's frame.
        if (abs(overrunMag - committed) > ZOOM_WALK_SETTLE) return null
        val delta = target - committed
        if (abs(delta) < ZOOM_WALK_SETTLE) {
            zoomWalkTarget = Double.NaN
            return null
        }
        // Landing step: reach the requested value exactly, not through an arithmetic sum.
        val next = if (abs(delta) <= ZOOM_BLIT_LIMIT) {
            target
        } else {
            committed + delta.coerceIn(-ZOOM_BLIT_LIMIT, ZOOM_BLIT_LIMIT)
        }
        if (next == target) zoomWalkTarget = Double.NaN
        commitZoom(next)
        return next
    }

    /** Commit a walked magnification: full render at [fraction] (a zoom change cannot be
     *  served by a blit of the previous frame) + viewport state for the screens. */
    private fun commitZoom(fraction: Double) {
        viewportZoomFraction = fraction
        viewportZoom = fraction.roundToInt()
        blitEligible = false
        emitViewportState()
        requestRender()
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
        // The follow diagnostic (throttled, 1 line / 30 ticks) carries the
        // display step, frame target, offset and counters; the per-tick traces
        // were removed when the on-device investigation closed (task 10.3).
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Vehicle stops" / "Vehicle resumes after a stop"): while the gate
        // is closed (stopped / paused / follow disengaged / no surface) the
        // displayed position is FROZEN — no easing toward the fix, no display
        // mutation at all. The resume continues from the frozen position.
        if (!extrapolationGateActive(nowMs)) return
        val surf = surface ?: return
        val w = surfaceWidth
        val h = surfaceHeight
        if (w <= 0 || h <= 0) return
        val predicted = followPrediction.predictedPosition(nowMs)
        if (predicted.first.isNaN() || predicted.second.isNaN()) return
        // Re-sync after a frozen gap (stop / gate-off / surface change): if the
        // volatile display was re-seeded or cleared while the loop did not
        // tick, reset the state so the next advance continues from the current
        // displayed position instead of a stale one.
        if (displayLat.isNaN() || displayLon.isNaN() ||
            abs(displayLat - followDisplayState.lat) > 1e-12 ||
            abs(displayLon - followDisplayState.lon) > 1e-12
        ) {
            followDisplayState.reset()
        }
        // Forward-only displayed position (delta fix-aa-follow-vehicle-jumps):
        // FollowDisplayState holds when the target drops behind the display
        // along the direction of travel (fix-arrival overshoot on curves /
        // decelerations) and eases forward otherwise — no backward correction
        // slide at the 1 Hz fix cadence. `gpsMarkerBearing` is the effective
        // bearing (NaN skips the rule, matching the phone). The result is
        // copied back into the @Volatile fields, the single cross-thread
        // source for the marker and the margin render target.
        val (displayedLat, displayedLon) = followDisplayState.advance(
            predicted.first, predicted.second, dtSec, gpsMarkerBearing
        )
        displayLat = displayedLat
        displayLon = displayedLon
        // P3: move the DISPLAYED magnification toward the committed target across frames.
        advanceDisplayedMagnification(dtSec)
        synchronized(surfaceLock) {
            // Read + blit under the shared lock: a concurrent full render
            // recycles the overrun bitmap when it swaps in a new one — drawing
            // a recycled bitmap crashes. Holding the lock from the read
            // through the draw serializes against fullRender's swap.
            val current = overrunBitmap ?: return
            val resolved = resolvedFollowAnchor()
            val offset = FollowPrediction.displayOffsetPx(
                displayLat, displayLon,
                overrunLat, overrunLon, overrunMag, overrunAngle,
                current.width, current.height, w, h, projectionDpi,
                // Single resolved anchor (spec: auto-smooth-follow — Single
                // resolved anchor in the AA follow blit): the SAME fraction
                // anchorCenterFor commits the frame on. The raw preset differs
                // from it for pane-band presets (clampAnchorOutOfPane against
                // the host's 40% leading band) and a mismatch keeps the offset
                // permanently outside the overrun margin — every tick becomes a
                // full native render instead of a sub-region blit.
                resolved.fx, resolved.fy
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
                        " clamped=" + offset.clamped +
                        // Displayed frame vs pending render target (change
                        // `overlay-projects-against-displayed-frame`): the overlays
                        // project against the `frame` values below. A `frame` that
                        // differs from `pending` is the window in which a viewport
                        // write has not been committed yet — before this change the
                        // marker/pin sat that far off the map content in that window.
                        " frame=" + "%.6f".format(overrunLat) + "," + "%.6f".format(overrunLon) +
                        " mag=" + "%.2f".format(overrunMag) + " ang=" + "%.3f".format(overrunAngle) +
                        " pending=" + "%.6f".format(viewportLat) + "," + "%.6f".format(viewportLon) +
                        " mag=" + "%.2f".format(viewportZoomFraction) + " ang=" + "%.3f".format(viewportAngle) +
                        // Committed-vs-displayed deltas + last-commit path (task 4.1):
                        // `dAng` is the pending rotation minus the displayed frame's
                        // rotation (normalized), `dMag` the pending minus the displayed
                        // magnification, `last` whether the previous commit was blitted
                        // or rendered — the on-device ranking of P2/P3 from one line.
                        " dAng=" + "%.3f".format(angleDeltaRadians(viewportAngle, overrunAngle)) +
                        " dMag=" + "%.3f".format(viewportZoomFraction - overrunMag) +
                        " last=" + (if (lastCommitWasBlit) "blit" else "render") +
                        " renders=" + fullRenderCount + " blits=" + blitCount
                )
            }
            if (offset.clamped) {
                // Display hit the overrun margin → full render at the display
                // position (throttled so a fast vehicle cannot queue renders).
                if (shouldRequestFullRender(nowMs, lastRenderRequestMs)) {
                    lastRenderRequestMs = nowMs
                    blitEligible = false
                    val (aLat, aLon) = anchorCenterFor(displayLat, displayLon)
                    viewportLat = aLat
                    viewportLon = aLon
                    emitViewportState()
                    requestRender()
                }
                return
            }
            blitToSurface(surf, current, offset.clampedX, offset.clampedY, w, h, resolved.fx, resolved.fy)
        }
    }

    /**
     * P3: ease [displayMag] toward the pending magnification at the display rate. Outside
     * the blit-able window the frame has no pixels to scale (a zoom-out past the rendered
     * area), so the displayed magnification snaps to the frame's own and the commit lands
     * the target with a full render instead.
     */
    private fun advanceDisplayedMagnification(dtSec: Double) {
        val frame = overrunMag
        if (frame <= 0.0) return
        if (displayMag <= 0.0) {
            displayMag = frame
            return
        }
        val target = viewportZoomFraction
        if (abs(target - frame) > ZOOM_BLIT_LIMIT || abs(displayMag - frame) > ZOOM_BLIT_LIMIT) {
            displayMag = frame
            return
        }
        val step = target - displayMag
        if (abs(step) < 1e-4) {
            displayMag = target
            return
        }
        displayMag += step * FollowPrediction.easeAlpha(dtSec, ZOOM_EASE_TAU_SEC)
    }

    /**
     * Nearest int of a draw offset. `Double.roundToInt()` THROWS on NaN (unlike Java's
     * `Math.round`), and a NaN offset means "no displayed position yet" — no shift.
     */
    private fun roundOffset(v: Double): Int = if (v.isNaN()) 0 else v.roundToInt()

    /**
     * Scale applied when drawing the displayed frame for P3: the difference between the
     * displayed magnification and the frame's own, clamped to the blit-able window (the
     * buffer must keep covering the surface). 1.0 when nothing is pending or in browse mode.
     */
    private fun displayedScale(): Float {
        if (!followMode || displayMag <= 0.0 || overrunMag <= 0.0) return 1f
        val delta = (displayMag - overrunMag).coerceIn(-ZOOM_BLIT_LIMIT, ZOOM_BLIT_LIMIT)
        if (abs(delta) < 1e-4) return 1f
        return 2.0.pow(delta).toFloat()
    }

    /** Current displayed magnification (P3) — exposed for tests. */
    internal fun displayedMagnification(): Double = displayMag

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
                viewportZoomFraction == overrunMag && viewportAngle == overrunAngle
            ) {
                // Single resolved anchor (spec: auto-smooth-follow — Single resolved
                // anchor in the AA follow blit): the SAME fraction anchorCenterFor
                // commits the frame on. The raw preset differs from it for pane-band
                // presets (clampAnchorOutOfPane against the host's 40% leading band) and
                // a mismatch keeps the offset permanently outside the overrun margin —
                // every tick becomes a full native render instead of a sub-region blit.
                val resolvedAnchor = resolvedFollowAnchor()
                // The offset is measured on the DISPLAYED vehicle position, not on the
                // frame center (change `overlay-projects-against-displayed-frame`,
                // spec: auto-map-renderer — Map re-renders on viewport change;
                // auto-smooth-follow — Sub-region blit on viewport change):
                // displayOffsetPx measures a displayed point against the frame the
                // bitmap was rendered with, and the frame is rendered
                // anchor-centered, so this yields the vehicle's drift from its anchor.
                // A frame center is not a point of the rendered bitmap: passing it
                // charges the offset with the anchor displacement ((fy-0.5)*h, up to
                // 0.4x the surface height), which exceeds the overrun margin for every
                // preset away from the center — and then every follow viewport change
                // falls back to a full native render instead of a blit.
                val (blitLat, blitLon) = if (followMode) markerPosition() else viewportLat to viewportLon
                val offset = FollowPrediction.displayOffsetPx(
                    blitLat, blitLon,
                    overrunLat, overrunLon, overrunMag, overrunAngle,
                    ob.width, ob.height, w, h, projectionDpi,
                    if (followMode) resolvedAnchor.fx else 0.5,
                    if (followMode) resolvedAnchor.fy else 0.5
                )
                if (!offset.clamped) {
                    // Viewport change within the overrun region → blit, no
                    // native render (spec: auto-map-renderer, design D2).
                    blitEligible = false
                    if (!followMode) {
                        displayLat = viewportLat
                        displayLon = viewportLon
                    }
                    blitToSurface(
                        surf, ob, offset.clampedX, offset.clampedY, w, h,
                        if (followMode) resolvedAnchor.fx else 0.5,
                        if (followMode) resolvedAnchor.fy else 0.5
                    )
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
        if (!blit) {
            // Mark the render in flight for the extrapolation loop and measure it, so
            // the next request can wait for the duration the render actually takes
            // (spec: car-host-fault-isolation — Bounded periodic render work).
            renderInFlight = true
            val renderStartedMs = System.currentTimeMillis()
            try {
                fullRender(surf, w, h)
            } finally {
                lastRenderDurationMs = System.currentTimeMillis() - renderStartedMs
                renderInFlight = false
            }
        }
    }

    /**
     * Full native render at overrun size (design D1): render at ~1.2x the
     * surface size, keep the bitmap as the overrun buffer, and draw the
     * visible region to the surface.
     */
    private fun fullRender(surf: Surface, w: Int, h: Int) {
        val renderW = (w * OVERRUN_FACTOR).toInt()
        val renderH = (h * OVERRUN_FACTOR).toInt()

        // Follow mode renders at the CURRENT displayed position, not at the frame
        // target the last fix wrote (spec: smooth-follow — Single follow center;
        // change `aa-follow-framing-and-zoom-parity`). The render runs 100 ms+ after
        // the fix that set the target, and the display keeps advancing in between
        // (plus ticks at ~11 Hz), so a render at the fix-time target is NOT continuous
        // with the blit that preceded it: the frame is anchor-centred on the fix while
        // the scene was placed so the DISPLAY sat at the anchor — the content and the
        // marker then shift by the display's advance since that fix (up to a full fix
        // interval = 25 m at 90 km/h, tens of px at car magnifications) and the next
        // blit moves them again: a sub-second up-and-back that stops when the vehicle
        // stands still. A render centred on the display's own anchor center is
        // continuous with the blit by construction (the blit had already put the
        // display at the anchor).
        if (followMode) {
            val (dLat, dLon) = markerPosition()
            if (!dLat.isNaN() && !dLon.isNaN()) {
                val (aLat, aLon) = anchorCenterFor(dLat, dLon)
                viewportLat = aLat
                viewportLon = aLon
                emitViewportState()
            }
        }

        // Snapshot the frame parameters ONCE (change
        // `overlay-projects-against-displayed-frame`, design D1). The native
        // render runs outside [surfaceLock] — by design, so the extrapolation
        // loop keeps blitting the old frame while it is in flight — so the
        // pending target can be written during the render: a fix re-anchor
        // (`setViewport` + `reengageFollow`), the loop's clamp branch, or an
        // auto-zoom commit. Re-reading the target afterwards to label the
        // committed frame would publish a center/mag/rotation the pixels were
        // NOT rendered at, and the overlays, the blit offset and the diagnostic
        // are all derived from that label: the marker would detach from the map
        // for the whole inter-commit window (~1 s), i.e. exactly the defect this
        // change removes, only intermittently.
        val frameLat = viewportLat
        val frameLon = viewportLon
        val frameAngle = viewportAngle
        val frameMag = viewportZoomFraction

        // The GPS marker is NOT passed to the native renderer (the JNI
        // setGpsMarker export was removed upstream in favor of Kotlin-side
        // overlays); it is drawn on the canvas in drawToSurface.
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = renderW,
            height = renderH,
            lat = frameLat,
            lon = frameLon,
            angle = frameAngle,
            magnification = 2.0.pow(frameMag),
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
                "render center=$frameLat,$frameLon mag=$frameMag -> " +
                    if (bitmap != null) "bitmap ${bitmap.width}x${bitmap.height}" else "NULL"
            )
        }

        if (bitmap != null) {
            synchronized(surfaceLock) {
                overrunBitmap?.recycle()
                overrunBitmap = bitmap
                // The SNAPSHOT, not the (possibly newer) pending target: this is
                // what the pixels show.
                overrunLat = frameLat
                overrunLon = frameLon
                overrunMag = frameMag
                overrunAngle = frameAngle
                // P3: the displayed-magnification state starts at the frame's own value; later
                // commits leave it easing (a committed step then reaches the eye across
                // frames instead of in one).
                if (displayMag <= 0.0) displayMag = frameMag
                if (!followMode) {
                    // In follow mode the extrapolation loop owns the display
                    // (the eased predicted position); a render triggered by a
                    // transient follow-off (heading-up setViewport) must not
                    // yank the display back to the render target — that
                    // shows up as the vehicle "pumping" between the target
                    // and the fix every fix.
                    displayLat = frameLat
                    displayLon = frameLon
                }
            }
            fullRenderCount++
            // The last committed viewport change was served by a full native
            // render (diagnostic, task 4.1).
            lastCommitWasBlit = false
            // A freshly rendered frame is drawn with the SAME placement rule as a blit:
            // the offset that puts the current DISPLAYED position on the resolved anchor
            // (spec: smooth-follow — Anchor-centered follow framing; change
            // `aa-follow-framing-and-zoom-parity`). Drawing it unshifted is only correct
            // when the display still sits exactly on the frame's own anchor position —
            // but the native render takes 25-300 ms, during which the display advances,
            // so an unshifted draw leaves the whole scene (map AND marker, which rides the
            // same offset) that advance away from where the previous frame had it: a ~5 px
            // jump up at every commit that the next blit tick moves back — measured on
            // device as an unexplained `dy -55 -> -60` step for an unchanged frame center.
            val (drawOx, drawOy) = if (followMode) {
                val (mLat, mLon) = markerPosition()
                if (mLat.isNaN() || mLon.isNaN()) {
                    // No displayed position yet (before the first fix/tick): nothing to
                    // place, so draw centered.
                    0.0 to 0.0
                } else {
                    val resolved = resolvedFollowAnchor()
                    val off = FollowPrediction.displayOffsetPx(
                        mLat, mLon,
                        frameLat, frameLon, frameMag, frameAngle,
                        bitmap.width, bitmap.height, w, h, projectionDpi,
                        resolved.fx, resolved.fy
                    )
                    off.clampedX to off.clampedY
                }
            } else {
                0.0 to 0.0
            }
            val stillCurrent = synchronized(surfaceLock) { isCurrentSurface(surf) }
            if (stillCurrent) {
                drawToSurface(surf, bitmap, w, h, drawOx, drawOy)
            } else {
                // The native render ran outside the lock and outlived its surface
                // (destroyed or replaced meanwhile): the frame belongs to a surface
                // the session already released, so it is dropped instead of drawn
                // (spec: car-host-fault-isolation — "After release the system SHALL
                // NOT lock or draw the released surface").
                android.util.Log.d(TAG, "renderer#$rendererId dropping frame: surface changed during the render")
            }
        }
    }

    /**
     * Draw a full-render bitmap to the surface at the given blit offset: the offset
     * places the current displayed position on the resolved anchor, so a fresh frame
     * lands exactly where the blitted frames had the scene (offset 0 for browse mode,
     * where the frame center IS the displayed position).
     */
    private fun drawToSurface(surf: Surface, bitmap: Bitmap, w: Int, h: Int, ox: Double, oy: Double) {
        // The host reuses ONE display surface across screens, so multiple
        // renderers (MapScreen + FreeDrivingScreen) can lock the same Surface
        // concurrently → IllegalArgumentException from lockCanvas ("surface
        // already locked"). Serialize lock/draw/unlock across all renderers.
        // The map-content overlays MUST be shifted by the same offset as the frame
        // they are drawn on. The offset MUST be published under
        // [surfaceLock] together with the frame it describes: assigning it outside
        // the lock lets a concurrent blit overwrite it between the assignment and
        // the lock, so the frame would be drawn with one offset and its overlays
        // with another (a one-frame marker pop the width of the offset).
        synchronized(surfaceLock) {
            blitOffsetX = ox
            blitOffsetY = oy
            var canvas: Canvas? = null
            try {
                // Surface.isValid() false = the host destroyed the underlying
                // buffer queue; lockCanvas would throw IAE. Skip the frame
                // (throttled diagnostics) instead of failing every render.
                if (!surf.isValid) {
                    reportFailureIfCurrent(surf, null, "surface invalid (isValid=false)")
                    return
                }
                canvas = surf.lockCanvas(null)
                if (canvas == null) {
                    // Same condition as a failed lock (already-locked or
                    // invalid surface); treat it as a dead surface.
                    reportFailureIfCurrent(surf, null, "lockCanvas returned null")
                    return
                }
                android.util.Log.d(TAG, "renderer#$rendererId lock OK surface=${System.identityHashCode(surf)}")
                // roundToInt, not toInt: truncation toward zero leaves the CONTENT up to
                // 1 px off the exact placement while the overlays use the exact fractional
                // offset (measured bias +0.9 px in the drawn-frame continuity check).
                val dx = ((w - bitmap.width) / 2f).roundToInt() - roundOffset(ox)
                val dy = ((h - bitmap.height) / 2f).roundToInt() - roundOffset(oy)
                // P3 (see blitToSurface): the scale that eases the displayed magnification
                // toward the committed target, about the follow anchor.
                val scale = displayedScale()
                val anchorX = if (followMode) resolvedFollowAnchor().fx else 0.5
                val anchorY = if (followMode) resolvedFollowAnchor().fy else 0.5
                canvas.save()
                if (scale != 1f) {
                    val ax = (anchorX * w).toFloat()
                    val ay = (anchorY * h).toFloat()
                    canvas.translate(ax, ay)
                    canvas.scale(scale, scale)
                    canvas.translate(-ax, -ay)
                }
                canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
                canvas.restore()
                drawGpsMarker(canvas, w, h)
                drawDestinationMarker(canvas, w, h)
                overlayDrawer?.invoke(canvas, w, h)
            } catch (e: Exception) {
                // Surface may be invalid (e.g., during lifecycle transitions)
                // or locked by the host. Log the full stack trace — the
                // message alone is often null.
                reportFailureIfCurrent(surf, e, "lockCanvas failed")
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
    private fun blitToSurface(
        surf: Surface,
        bitmap: Bitmap,
        ox: Double,
        oy: Double,
        w: Int,
        h: Int,
        anchorX: Double,
        anchorY: Double
    ) {
        // Remember the offset the displayed frame is shifted by: every overlay that
        // represents map content (vehicle marker, destination pin) has to be shifted
        // by the same amount, otherwise it leads the content between commits.
        // Published under [surfaceLock] with the draw (see [drawToSurface]): an
        // assignment outside the lock can be overwritten by a concurrent render,
        // leaving the frame drawn with one offset and its overlays with another
        // (a one-frame marker pop).
        synchronized(surfaceLock) {
            blitOffsetX = ox
            blitOffsetY = oy
            var canvas: Canvas? = null
            try {
                if (!surf.isValid) {
                    reportFailureIfCurrent(surf, null, "surface invalid (isValid=false)")
                    return
                }
                canvas = surf.lockCanvas(null)
                if (canvas == null) {
                    reportFailureIfCurrent(surf, null, "lockCanvas returned null")
                    return
                }
                val dx = ((w - bitmap.width) / 2f).roundToInt() - roundOffset(ox)
                val dy = ((h - bitmap.height) / 2f).roundToInt() - roundOffset(oy)
                // P3: apply the displayed-magnification scale about the follow anchor, so a
                // committed magnification change reaches the eye across frames instead of as
                // one scale step (the anchors themselves stay unscaled). A scale of 1 is a
                // no-op — the frame is already at the committed magnification.
                val scale = displayedScale()
                canvas.save()
                if (scale != 1f) {
                    val ax = (anchorX * w).toFloat()
                    val ay = (anchorY * h).toFloat()
                    canvas.translate(ax, ay)
                    canvas.scale(scale, scale)
                    canvas.translate(-ax, -ay)
                }
                canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), null)
                canvas.restore()
                drawGpsMarker(canvas, w, h)
                drawDestinationMarker(canvas, w, h)
                overlayDrawer?.invoke(canvas, w, h)
            } catch (e: Exception) {
                reportFailureIfCurrent(surf, e, "blit lockCanvas failed")
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
        // The last committed viewport change was served by a sub-region blit
        // (diagnostic, task 4.1).
        lastCommitWasBlit = true
    }

    /** Recycle and drop the overrun buffer (surface change / shutdown). */
    private fun clearOverrunBuffer() {
        overrunBitmap?.recycle()
        overrunBitmap = null
        overrunLat = Double.NaN
        overrunLon = Double.NaN
        overrunMag = 0.0
        overrunAngle = 0.0
        displayLat = Double.NaN
        displayLon = Double.NaN
        followDisplayState.reset()
    }

    /**
     * Whether [surf] is still the surface this renderer draws on. A frame whose
     * surface was destroyed or replaced while the native render was in flight must be
     * dropped (spec: car-host-fault-isolation — "After release the system SHALL NOT
     * lock or draw the released surface"). Exposed for tests.
     */
    internal fun isCurrentSurface(surf: Surface): Boolean = surface === surf

    /**
     * Whether this renderer currently considers its surface dead (a failed lock or an
     * invalid surface). Exposed for tests: the stop path clears it (spec:
     * auto-map-renderer — A stopped renderer holds no surface or frame buffer).
     */
    internal fun isSurfaceFailed(): Boolean = surfaceFailed

    /**
     * Report a draw failure only while [surf] is still the renderer's surface: a
     * surface that was destroyed or replaced while a render was in flight is stale,
     * not a failure of the current one — reporting it would set `surfaceFailed` and
     * stop the render loop for the live surface (observed on the automotive AVD:
     * a background round trip replaced the surface and the map stayed frozen).
     */
    private fun reportFailureIfCurrent(surf: Surface, e: Throwable?, why: String) {
        if (isCurrentSurface(surf)) {
            reportSurfaceFailure(surf, e, why)
        } else {
            android.util.Log.d(
                TAG,
                "renderer#$rendererId $why on a replaced surface ${System.identityHashCode(surf)} — ignored"
            )
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
     * same projection the native renderer used for the map bitmap. In follow
     * mode the marker rides the displayed (eased predicted) position so it
     * glides with the blitted map (spec: auto-smooth-follow, design D7).
     */
    private fun drawGpsMarker(canvas: Canvas, w: Int, h: Int) {
        if (!gpsMarkerVisible || gpsMarkerLat.isNaN() || gpsMarkerLon.isNaN()) return

        val (x, y) = markerScreenPosition(w, h)

        if (x.isNaN() || y.isNaN()) return
        if (x < -200 || x > w + 200 || y < -200 || y > h + 200) return

        val density = (projectionDpi / 160.0).toFloat()
        val minRadius = 4f * density
        val accuracyThreshold = 20f * density

        // Meters per pixel at the rendered magnification (pixels-per-radian
        // times earth radius). Used for the accuracy circle.
        val scale = ProjectionUtils.computeScale(displayedMag(), w.toDouble(), projectionDpi).scale
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
        val screenBearingDeg = ProjectionUtils.screenBearing(rawBearing, displayedAngle()).toFloat()

        // Unified marker (spec: gps-location-marker, cross-variant parity) —
        // the same shape and palette as the phone overlay, driven by
        // VehicleMarkerGeometry (38 dp density-aware, casing + rim + gradient
        // + soft shadow; legible on both daylight and dark map variants).
        val hPx = VehicleMarkerGeometry.SIZE_DP * density / 2f

        fun buildCore(): Path {
            val path = Path()
            val vertices = VehicleMarkerGeometry.outlineVertices()
            val (x0, y0) = vertices[0]
            val (x1, y1) = vertices[1]
            path.moveTo(x0 * hPx, y0 * hPx)
            path.quadTo(0f, -hPx, x1 * hPx, y1 * hPx)
            for (i in 2 until vertices.size) {
                val (x, y) = vertices[i]
                path.lineTo(x * hPx, y * hPx)
            }
            path.close()
            return path
        }

        fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        // Soft blurred shadow, offset in screen space (drawn before the
        // arrow, rotated to the bearing) — floating-chip depth.
        val shadowOffsetPx = VehicleMarkerGeometry.SHADOW_OFFSET_DP * density
        canvas.save()
        canvas.translate(centerX + shadowOffsetPx, centerY + shadowOffsetPx)
        canvas.rotate(screenBearingDeg)
        val shadowPaint = fillPaint(VehicleMarkerGeometry.COLOR_SHADOW.toInt()).apply {
            maskFilter = BlurMaskFilter(
                VehicleMarkerGeometry.SHADOW_BLUR_DP * density,
                BlurMaskFilter.Blur.NORMAL
            )
        }
        canvas.drawPath(buildCore(), shadowPaint)
        canvas.restore()

        // Layered arrow (casing -> rim -> gradient core), rotated about position.
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(screenBearingDeg)

        // White casing ring: core scaled about the center. In dark
        // presentation the casing turns deep blue-black (`COLOR_CASING_DARK`)
        // so no stencil-white halo shows against dark land — the silhouette
        // stays clean-cut (user feedback on unified-vehicle-marker). Dark
        // rides the resolved dark presentation pushed by the screen.
        val casing = buildCore()
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(VehicleMarkerGeometry.CASING_SCALE, VehicleMarkerGeometry.CASING_SCALE)
        casing.transform(scaleMatrix)
        canvas.drawPath(
            casing,
            fillPaint(if (darkPresentation) VehicleMarkerGeometry.COLOR_CASING_DARK.toInt() else VehicleMarkerGeometry.COLOR_CASING.toInt())
        )

        // Dark accent rim: stroke around the core (tri-layer arrow).
        val core = buildCore()
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = VehicleMarkerGeometry.COLOR_RIM.toInt()
            strokeWidth = VehicleMarkerGeometry.RIM_WIDTH_H * hPx
        }
        canvas.drawPath(core, rimPaint)

        // Core: vertical gradient, light from above. Dark presentation uses the
        // lighter dark-presentation stops so the marker reads on dark land
        // instead of blending into it (spec: gps-location-marker, scenario
        // "Arrow legible on dark map").
        val (gradientTop, gradientBottom) = VehicleMarkerGeometry.gradientColors(darkPresentation)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, -hPx, 0f, VehicleMarkerGeometry.TAIL_Y * hPx,
                intArrayOf(
                    gradientTop.toInt(),
                    gradientBottom.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(core, gradientPaint)
        canvas.restore()
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
            vpLat, vpLon, displayedMag(), w, h, projectionDpi, displayedAngle()
        )
        val (destX0, destY0) = vp.geoToScreenRotated(destMarkerLat, destMarkerLon)
        val x = destX0 - blitOffsetX
        val y = destY0 - blitOffsetY
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

    /**
     * Surface position of the GPS marker: the displayed (eased predicted) position
     * projected against the DISPLAYED frame — the bitmap currently on the surface —
     * and shifted by the offset that frame was blitted with, i.e. the position of
     * the map content the marker rides (spec: gps-location-marker — Marker projects
     * against displayed bitmap viewport; changes `anchor-per-surface-visible-area`,
     * `overlay-projects-against-displayed-frame`).
     *
     * Two defects met here: projecting against the frame target alone left the marker
     * ahead of the content by the blit offset between commits, and projecting against
     * a PENDING frame target detached the marker from the content for one render
     * latency after every viewport write (a fix re-anchor, a heading rotation, an
     * auto-zoom band change) and snapped it back on the commit. Both are covered now:
     * displayed*() reads the committed frame and the blit offset is subtracted.
     * Exposed for tests.
     */
    internal fun markerScreenPosition(w: Int, h: Int): Pair<Double, Double> {
        val (markerLat, markerLon) = markerPosition()
        val vp = ProjectionUtils.viewport(
            displayedLat(), displayedLon(), displayedMag(), w, h, projectionDpi, displayedAngle()
        )
        val (x, y) = vp.geoToScreenRotated(markerLat, markerLon)
        return (x - blitOffsetX) to (y - blitOffsetY)
    }

    /** Current blit offset of the displayed frame — exposed for tests. */
    internal fun blitOffset(): Pair<Double, Double> = blitOffsetX to blitOffsetY

    /** Test hook: pretend the displayed frame was blitted by (x, y). */
    @androidx.annotation.VisibleForTesting
    internal fun setBlitOffsetForTest(x: Double, y: Double) {
        blitOffsetX = x
        blitOffsetY = y
    }

    private fun emitViewportState() {
        _viewportState.value = ViewportState(viewportLat, viewportLon, viewportZoom, viewportAngle, viewportZoomFraction)
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
     * Set the follow-mode vehicle anchor preset. When follow mode frames the
     * map (navigation, free driving, re-center), the render target becomes the
     * anchor center of the displayed position so the vehicle marker projects
     * to the anchor screen fraction (spec: auto/navigation-view — Vehicle
     * anchor during navigation). Callers pass the mode's anchor from the
     * shared settings (routing vs free-driving). A pending render picks up the
     * new anchor on the next commit.
     */
    fun setFollowAnchor(anchor: VehicleAnchorPosition) {
        followAnchor = anchor
    }

    /** Current follow anchor — exposed for tests. */
    internal fun followAnchor(): VehicleAnchorPosition = followAnchor

    /**
     * Follow-framing center for a vehicle position: the [anchorCenter] of the
     * position under the current surface size, magnification and rotation, with the
     * preset clamped out of the host's panel region (spec: auto/navigation-view —
     * "Panel clearance via side anchor"; change `anchor-per-surface-visible-area`).
     * The host panel covers [PANE_FRACTION] of the surface width on the leading edge
     * (left in LTR, right in RTL — same convention as [paneOffsetCenter]), so a preset
     * inside that band moves to the nearest free position; every other preset keeps
     * its exact fraction, so the default framing is unchanged. Falls back to the raw
     * position when the surface is not ready.
     */
    private fun anchorCenterFor(lat: Double, lon: Double): Pair<Double, Double> {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return lat to lon
        val resolved = resolvedFollowAnchor()
        return anchorCenter(
            lat, lon, resolved.fx, resolved.fy,
            viewportZoomFraction, surfaceWidth, surfaceHeight, projectionDpi, viewportAngle
        )
    }

    /**
     * Tell the renderer on which side the host draws its panel (left in LTR, right
     * in RTL). A follow anchor inside that band is clamped out of it.
     */
    fun setHostPaneRtl(rtl: Boolean) {
        hostPaneRtl = rtl
    }

    /**
     * Set the host-covered top band (px, >= 0) the street-name pill (and,
     * as follow-up, a top-row follow anchor) must stay below — mirror of
     * [setHostBottomInset] (design D8, street-name-host-views). Re-renders.
     */
    fun setHostTopInset(px: Int) {
        val clamped = px.coerceAtLeast(0)
        if (clamped == hostTopInsetPx) return
        hostTopInsetPx = clamped
        requestRender()
    }

    /**
     * Set the host-covered bottom band (px, >= 0) the follow anchor must stay
     * above — bottom-row presets clamp into the visible area (design:
     * anchor-per-surface-visible-area AA vertical clamp). Re-frames follow.
     */
    fun setHostBottomInset(px: Int) {
        val clamped = px.coerceAtLeast(0)
        if (clamped == hostBottomInsetPx) return
        hostBottomInsetPx = clamped
        requestRender()
    }

    /** Current host top-inset flag — exposed for tests. */
    internal fun hostTopInset(): Int = hostTopInsetPx

    /** Current host-pane side flag — exposed for tests. */
    internal fun hostPaneRtl(): Boolean = hostPaneRtl

    /**
     * Resolved follow anchor fraction (host panel + bottom band clamped) —
     * exposed for tests.
     */
    internal fun resolvedFollowAnchor(): ResolvedAnchor {
        val paneInset = (surfaceWidth * PANE_FRACTION).toInt().coerceAtLeast(0)
        return resolveAnchorFraction(
            followAnchor,
            leftPx = if (hostPaneRtl) 0 else paneInset,
            rightPx = if (hostPaneRtl) paneInset else 0,
            bottomPx = hostBottomInsetPx,
            screenW = surfaceWidth,
            screenH = surfaceHeight
        )
    }

    /**
     * Center of the frame the overlays project against: the DISPLAYED frame's
     * center (the one the bitmap on the surface was rendered with), else the
     * pending render target while no frame exists yet. Exposed for tests.
     */
    internal fun markerViewport(): Pair<Double, Double> = displayedLat() to displayedLon()

    /**
     * Displayed-frame accessors (spec: gps-location-marker — Marker projects against
     * displayed bitmap viewport; change `overlay-projects-against-displayed-frame`):
     * center, magnification and rotation of the frame currently on the surface, with
     * the pending render target as the fallback before the first frame exists.
     *
     * The frame bookkeeping is published under [surfaceLock] by [fullRender] (commit)
     * and [blitToSurface] (offset), so a reader inside that lock — every canvas draw
     * happens there — sees one whole frame; the fields are @Volatile for readers
     * outside it. Overlays MUST project against these and not against the pending
     * target: the two differ for one render latency after each viewport write.
     */
    private fun displayedLat(): Double = if (overrunBitmap != null) overrunLat else viewportLat

    private fun displayedLon(): Double = if (overrunBitmap != null) overrunLon else viewportLon

    private fun displayedMag(): Double = if (overrunBitmap != null) overrunMag else viewportZoomFraction

    private fun displayedAngle(): Double = if (overrunBitmap != null) overrunAngle else viewportAngle

    /** Size of the current overrun buffer, or null when none. Exposed for tests. */
    internal fun overrunSize(): Pair<Int, Int>? =
        overrunBitmap?.let { it.width to it.height }

    /**
     * How many of this renderer's background jobs ([renderJob], [extrapolationJob],
     * [zoomWalkJob]) are still active. Exposed for the test teardown rule
     * (change `fix-auto-unit-test-heap-overflow`): a test that constructs a
     * renderer must shut it down, and the rule asserts this is 0 afterwards, so a
     * leaked render/extrapolation loop fails its own test class instead of
     * starving the `:auto` suite's heap later.
     *
     * `Job.isActive` is false as soon as [shutdown] calls `cancel()`, so the
     * assertion needs no waiting or retry.
     */
    internal fun activeBackgroundJobCount(): Int =
        listOfNotNull(renderJob, extrapolationJob, zoomWalkJob).count { it.isActive }

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

        // --- aa-follow-framing-and-zoom-parity P3: zoom transition ---

        /**
         * Largest magnification step the overrun blit can serve: the buffer is rendered at
         * [OVERRUN_FACTOR] times the surface, so scaling it down by 2^-limit must still
         * cover the surface — `log2(OVERRUN_FACTOR)` = 0.263, kept at 0.25 with margin. A
         * larger step has no pixels to scale (zooming out past the rendered area) and falls
         * back to a full native render at the committed magnification.
         */
        const val ZOOM_BLIT_LIMIT = 0.25

        /** Ease time constant (s) of the displayed magnification transition. */
        const val ZOOM_EASE_TAU_SEC = 0.25

        // --- aa-entry-zoom-animation: zoom transition walk ---

        /**
         * Walk loop period. The walk itself is render-synchronous (one step per landed
         * render), so this is only the granularity at which a landed frame is noticed —
         * short enough that the transition is not visibly paced by the poll.
         */
        const val ZOOM_WALK_FRAME_MS = 20L

        /** Tolerance for "the previous step's frame has landed" (committed == rendered). */
        const val ZOOM_WALK_SETTLE = 1e-3

        /** Throttle for full-render requests from the extrapolation loop
         *  (delta fix-aa-follow-vehicle-jumps): 200 ms = phone parity with
         *  `GPS_FOLLOW_RENDER_INTERVAL_MS`, shrinking the freeze-then-advance
         *  step at the overrun margin (was 500 ms + 100 ms debounce). */
        const val RENDER_REQUEST_INTERVAL_MS = 200L
    }
}
