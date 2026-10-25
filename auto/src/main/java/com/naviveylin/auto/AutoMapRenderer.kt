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
import com.naviveylin.core.RenderBitmapPool
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.VehicleMarkerGeometry
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
class AutoMapRenderer internal constructor(
    internal val client: OSMScoutClient,
    initialProjectionDpi: Double,
    initialLat: Double = DEFAULT_LATITUDE,
    initialLon: Double = DEFAULT_LONGITUDE,
    initialZoom: Int = DEFAULT_ZOOM,
    /** Start in follow mode (re-center on GPS fixes); false for "show location" maps. */
    initialFollowMode: Boolean = true,
    /**
     * Per-iteration fault confinement and the re-creation ladder for this renderer's loops
     * (spec: car-host-fault-isolation — No fault escapes into the host path; A repeatedly
     * faulting car renderer recovers, then degrades visibly; design D1/D2).
     *
     * Owned by the renderer gate, so one supervisor spans the renderer instances of a
     * started screen period and the re-creation budget survives an instance swap.
     */
    internal val loops: RenderLoopSupervisor = RenderLoopSupervisor()
) {
    /**
     * DPI of the car surface this renderer draws on — the value its gestures, marker
     * overlay and every render request use (spec: `render-projection-dpi`). Updated when
     * the host delivers a surface.
     */
    @Volatile
    var projectionDpi: Double = initialProjectionDpi
        private set

    /** Update the projection DPI of the surface being drawn on (spec: `render-projection-dpi`). */
    fun updateProjectionDpi(dpi: Double) {
        if (dpi > 0 && dpi != projectionDpi) {
            projectionDpi = dpi
            // One line per change (not per frame): the DPI travels with the render
            // requests, so this is what the car surface is drawing with.
            android.util.Log.d(TAG, "renderer $rendererId projection dpi -> $dpi")
            // The overrun buffer was projected at the old DPI — full render.
            blitEligible = false
            requestRender()
        }
    }
    /**
     * The renderer's own scope. Runs native renders, blits and the overlay draw —
     * host-facing work whose fault must not reach the main thread's uncaught handler
     * (spec: car-host-fault-isolation — No fault escapes into the host path; a process
     * death during a car session takes the templates host down with it, TODO.md §51).
     * A confined fault ends the affected loop instead of the app process.
     */
    internal val scope = carScreenScope("AutoMapRenderer", Dispatchers.Default)
    internal var renderJob: Job? = null
    @Volatile internal var surface: Surface? = null
    internal var surfaceWidth = 0
    internal var surfaceHeight = 0
    internal var isShutdown = false

    /** Unique id per renderer instance, to tell renderers apart in logs. */
    internal val rendererId = nextRendererId++

    init {
        // One line per renderer instance: the projection DPI is part of every render
        // request (spec: `render-projection-dpi`).
        android.util.Log.d(TAG, "renderer $rendererId created: projection dpi=$projectionDpi")
    }

    // Viewport state
    @Volatile internal var viewportLat = initialLat
    @Volatile internal var viewportLon = initialLon
    @Volatile internal var viewportZoom = initialZoom
    @Volatile internal var viewportZoomFraction = initialZoom.toDouble()
    @Volatile internal var viewportAngle = 0.0

    // Overlay data
    @Volatile internal var gpsMarkerLat = Double.NaN
    @Volatile internal var gpsMarkerLon = Double.NaN
    @Volatile internal var gpsMarkerBearing = Double.NaN
    @Volatile internal var gpsMarkerAccuracy = 0.0
    @Volatile internal var gpsMarkerVisible = false

    // Resolved dark presentation (preference x host signal) — drives the
    // scheme-aware palettes of the surface-drawn overlays: the vehicle marker
    // casing (white in day, deep blue-black in dark) and the compass rose
    // palette. Set by the screen once the native stylesheet daylight flag has
    // actually been applied, so the overlays and the map variant never disagree.
    @Volatile
    internal var darkPresentation = false

    /** Update the dark presentation used by surface-drawn overlays. */
    fun setDarkPresentation(dark: Boolean) {
        darkPresentation = dark
    }

    /**
     * Presentation currently applied to the map variant and the overlays — the
     * value surface-drawn overlays (compass rose) must use, so the palette can
     * never belong to the other presentation than the map underneath.
     */
    fun currentDarkPresentation(): Boolean = darkPresentation

    @Volatile internal var favoriteLats: DoubleArray? = null
    @Volatile internal var favoriteLons: DoubleArray? = null
    @Volatile internal var routeLats: DoubleArray? = null
    @Volatile internal var routeLons: DoubleArray? = null

    // Destination marker (spec: auto-destination-details)
    @Volatile internal var destMarkerLat = Double.NaN
    @Volatile internal var destMarkerLon = Double.NaN
    @Volatile internal var destMarkerName: String? = null
    @Volatile internal var destMarkerVisible = false

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
    @Volatile internal var displayMag = 0.0

    /**
     * Zoom transition target (spec: auto-speed-zoom — Auto-zoom entry transition): a
     * magnification a commit requested that is farther than the blit window
     * ([ZOOM_BLIT_LIMIT]) from the magnification on screen. The frame walks toward it in
     * blit-serviceable steps, one step per LANDED render, so no single frame carries the
     * whole change — entering free driving no longer snaps 13.0 -> 17.0 in one frame.
     * NaN = no transition pending.
     */
    @Volatile internal var zoomWalkTarget = Double.NaN

    @Volatile internal var overrunBitmap: Bitmap? = null
    @Volatile internal var overrunLat = Double.NaN
    @Volatile internal var overrunLon = Double.NaN
    @Volatile internal var overrunMag = 0.0
    @Volatile internal var overrunAngle = 0.0

    // Displayed viewport center: the eased predicted position in follow mode,
    // equal to the overrun center right after a full render. The GPS marker is
    // drawn at this position so it glides with the blitted map (design D7).
    @Volatile internal var displayLat = Double.NaN
    @Volatile internal var displayLon = Double.NaN

    // Follow prediction (spec: auto-smooth-follow): extrapolates the displayed
    // position between 1 Hz GPS fixes and eases corrections on fix arrival.
    // Display-only — the navigation engine receives only real fixes (design D5).
    internal val followPrediction = FollowPrediction()

    // Monotonic forward display (delta fix-aa-follow-vehicle-jumps): the same
    // forward-only hold rule the phone's display loop uses (spec:
    // auto-smooth-follow — forward-only displayed position). Owned by the
    // extrapolation loop thread; the @Volatile displayLat/displayLon fields
    // below remain the cross-thread source for the marker and the margin
    // render target.
    internal val followDisplayState = FollowDisplayState(tauSec = EASE_TAU_SEC)

    // Seed-once latch (delta fix-aa-follow-vehicle-jumps): set when a stop
    // episode already re-seeded the display to the fix, so stationary GPS
    // jitter cannot re-anchor the viewport on every fix. Cleared when the
    // vehicle crosses the movement threshold again or the overrun is cleared.
    internal var stationarySeedApplied = false
    @Volatile internal var lastFixSpeedMs = Double.NaN
    @Volatile internal var lastFixTimeMs = 0L
    @Volatile internal var lastFixLat = Double.NaN
    @Volatile internal var lastFixLon = Double.NaN
    internal var extrapolationJob: Job? = null

    /** Zoom-transition loop (spec: auto-speed-zoom — Auto-zoom entry transition). */
    internal var zoomWalkJob: Job? = null

    // True when the next render may be served by a sub-region blit (pure
    // viewport or canvas-overlay change). Overlay/native-content changes
    // (favorites, route, DPI, surface) force a full render.
    @Volatile internal var blitEligible = false

    // Test-visible counters (spec: auto-smooth-follow verification): a small
    // viewport move must not increment [fullRenderCount], and a path that schedules a
    // frame it already has must be observable as a second [renderRequestCount] — the
    // render loop draws one frame per signal, so a duplicate request is a duplicate
    // frame in production even when a pending flag makes it invisible in sync tests.
    @Volatile internal var fullRenderCount = 0
    @Volatile internal var blitCount = 0
    @Volatile internal var renderRequestCount = 0

    // Whether the last committed viewport change was served by a sub-region
    // blit or a full native render (diagnostic, task 4.1).
    @Volatile internal var lastCommitWasBlit = true

    /** Test seam: when false, the async render/extrapolation loops do not run. */
    @Volatile internal var asyncLoopsEnabled = true

    /**
     * Turns the zoom-transition loop has taken, counted before its surface gate. Internal test seam
     * (spec `unit-test-suite-runtime` — Awaiting state, not a deadline): the loop is render-synchronous
     * and runs on a real dispatcher, so a case that must give it a chance to act awaits loop turns
     * instead of a wall-clock pause.
     */
    @Volatile internal var zoomWalkLoopTurns = 0

    // Follow mode
    @Volatile internal var followMode = initialFollowMode

    /**
     * Follow-mode vehicle anchor (spec: auto/navigation-view — Vehicle anchor
     * during navigation; auto/free-driving — Follow mode activated). When
     * follow mode frames the map, the render target is the [anchorCenter] of
     * the displayed position, so the vehicle marker projects to the anchor
     * screen fraction instead of the surface center. Defaults to the surface
     * center = the pre-feature framing.
     */
    @Volatile internal var followAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    /**
     * Host-covered band at the bottom of the surface (px): host chrome that
     * overlays the map bottom (e.g. the AAOS bottom control bar) but is not
     * part of the granted surface. Fed from the host's stable-area bottom
     * ([SurfaceCallback.onStableAreaChanged]); bottom-row anchor presets are
     * clamped above it (design: anchor-per-surface-visible-area, AA vertical
     * clamp). 0 = no bottom chrome known.
     */
    @Volatile
    internal var hostBottomInsetPx = 0
    internal var hostTopInsetPx = 0

    /**
     * True when the host draws its panel on the RIGHT edge (RTL layouts). Set by the
     * owning screen from the car context; the follow anchor resolves against the
     * surface minus the panel (same side convention as [paneOffsetCenter]).
     */
    @Volatile internal var hostPaneRtl = false

    /**
     * Screen-space offset the currently DISPLAYED frame was blitted by (0 = a fresh
     * render). Overlays that represent map content must be shifted by the same
     * offset, otherwise they lead the content between commits (see
     * `markerScreenPosition`).
     */
    @Volatile internal var blitOffsetX = 0.0
    @Volatile internal var blitOffsetY = 0.0

    /**
     * True while the owning screen is stopped: the render loop skips frames
     * so a stopped screen never locks the (shared) display surface — two
     * renderers locking the same surface race and throw
     * IllegalArgumentException from lockCanvas ("surface already locked").
     */
    @Volatile internal var paused = false

    /**
     * True after the current surface failed to lock (host locked/destroyed
     * it). The render loop skips frames so we never hammer a dead surface
     * with 1s native renders; the host is expected to deliver a fresh
     * surface (onSurfaceCreated resets this).
     */
    @Volatile internal var surfaceFailed = false

    /**
     * Invoked (throttled) when the current surface cannot be locked. The
     * owning screen uses this to ask the host for a fresh surface (e.g.
     * [androidx.car.app.Screen.invalidate]).
     */
    var onSurfaceFailed: (() -> Unit)? = null

    internal val renderSignal = MutableStateFlow(0L)
    internal var pendingRender = false

    // Throttle for surface-failure diagnostics (a dead surface fails every
    // frame; logging each one floods logcat).
    internal var lastSurfaceFailureLogMs = 0L
    internal var lastSurfaceFailureCallbackMs = 0L
    internal val SURFACE_FAILURE_LOG_INTERVAL_MS = 5_000L
    internal val SURFACE_FAILURE_CALLBACK_INTERVAL_MS = 5_000L

    init {
        // The supervisor runs on this renderer's dispatcher (the loops' thread); its
        // re-creation and degraded reports are handed to the owner, which posts them to the
        // main thread — the renderer gate is a main-thread object (design D3).
        loops.onRecoveryRequested { attempt -> onRendererRecoveryRequested?.invoke(attempt) }
        loops.onDegraded { loop, _ -> onRendererDegraded?.invoke(loop) }
        startRenderLoop()
        startExtrapolationLoop()
        startZoomWalkLoop()
    }

    /**
     * Reports that the loop fault storm reached the re-creation threshold (design D2). Invoked
     * from the render thread with the attempt number; the owner posts to the main thread.
     */
    internal var onRendererRecoveryRequested: ((attempt: Int) -> Unit)? = null

    /**
     * Reports that the re-creation budget is exhausted and the map cannot be drawn (design
     * D7). Invoked from the render thread; the owner posts to the main thread.
     */
    internal var onRendererDegraded: ((loop: RenderLoop) -> Unit)? = null

    // Exposed viewport state for UI
    internal val _viewportState = MutableStateFlow(
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
     * (and emitted), so the commit renders at the point the shown frame already had
     * at the anchor: the commit changes the centre by the display's own advance only,
     * which the overrun blit serves, and neither the map content nor the marker moves
     * at the commit. The re-engage requests that frame itself when none is pending —
     * a pan release (MapPanHandler) or a screen start/resume re-engages without a
     * preceding commit, so relying on one would leave the stale frame on the surface
     * (spec: auto-smooth-follow — A follow re-engage requests the frame it re-anchored).
     * When a commit did already request a frame for the same target, that frame renders
     * the re-anchored target (the target is read at render time) and no second frame is
     * scheduled.
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
            // Center-only commit (neither magnification nor rotation changed), so it is
            // blit-eligible like setViewport's pure-center case; the overrun coverage
            // check stays the authority on whether a full render is needed.
            blitEligible = true
            // Guarded: a frame that is already scheduled renders the target the fields
            // hold at render time, so requesting again would add a second frame to the
            // fix path (setViewport requested one microsecond ago). One re-engage, at
            // most one frame.
            if (!pendingRender) requestRender()
        }
    }

    /** Whether follow mode is currently active. */
    fun isFollowMode(): Boolean = followMode

    // Throttle for file-backed diagnostics (pan events are frequent).
    internal var lastRenderLogMs = 0L
    internal val RENDER_LOG_INTERVAL_MS = 1000L

    // Throttle for full-render requests from the extrapolation loop (a fast
    // vehicle would otherwise queue a render every frame while clamped).
    internal var lastRenderRequestMs = 0L

    /**
     * True while a full native render is running (spec: car-host-fault-isolation —
     * Bounded periodic render work; design D7): the extrapolation loop must not queue
     * another full render behind a running one.
     */
    @Volatile
    internal var renderInFlight = false

    /**
     * Duration of the last full native render (ms). The request interval stretches to
     * it, so on a slow head unit the loop does not saturate the render thread by
     * re-requesting a frame that cannot keep up.
     */
    @Volatile
    internal var lastRenderDurationMs = 0L

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
    internal var followLogCount = 0

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
        renderRequestCount++
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

    /**
     * Test seam (never set outside tests — `guidelines/Build.md` §6 forbids pacing a unit test
     * off a timer, so the confinement cases drive a real fault through this instead). The named
     * loop's next iteration throws before doing its work.
     */
    internal var testIterationFault: RenderLoop? = null

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

    /**
     * Center of the frame the overlays project against: the DISPLAYED frame's
     * center (the one the bitmap on the surface was rendered with), else the
     * pending render target while no frame exists yet. Exposed for tests.
     */
    internal fun markerViewport(): Pair<Double, Double> = displayedLat() to displayedLon()

    /**
     * Center of the frame currently on the surface, or null while no frame has been
     * committed. Unlike [markerViewport] this never falls back to the pending render
     * target: a caller that publishes a value as "the center of the last drawn frame"
     * (spec: search-result-ranking — Distance reference for ordering and display) must
     * not publish a center no frame ever displayed. Exposed for tests.
     */
    internal fun committedViewportCenter(): Pair<Double, Double>? =
        synchronized(surfaceLock) {
            if (overrunBitmap == null) null else overrunLat to overrunLon
        }

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

    @Volatile
    internal var markerDrawObjects: MarkerDrawObjects? = null

    @Volatile
    internal var markerDrawRebuilds = 0

    companion object {
        internal const val TAG = "AutoMapRenderer"
        internal const val RENDER_DEBOUNCE_MS = 100L
        private const val DEFAULT_LATITUDE = 51.5142273
        private const val DEFAULT_LONGITUDE = 7.4652789
        private const val DEFAULT_ZOOM = 12

        private var nextRendererId = 0

        /** Serializes lock/draw/unlock across all renderer instances. */
        internal val surfaceLock = Any()

        const val MIN_ZOOM = 1
        const val MAX_ZOOM = 20

        /** Max destination label characters drawn on the map surface. */
        internal const val MAX_DEST_LABEL_CHARS = 40

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

/**
 * The `MAP` render entry's message (spec: auto-diagnostics — A car map-render
 * diagnostic identifies the frame without a position; change
 * `fix-car-render-coordinate-redaction`): the committed frame's magnification, the
 * rendered bitmap's size and the projection DPI, and no position — the centre was
 * removed because the file-backed diagnostics stream is exportable and retained
 * (`TODO.md` §98). A pure seam, so the field contract is unit-tested without a
 * surface; formats with `Locale.ROOT` so a German device's decimal comma cannot make
 * the line ambiguous (`TODO.md` §19).
 */
internal fun renderLogMessage(
    frameMag: Double,
    bitmapWidth: Int?,
    bitmapHeight: Int?,
    surfaceDpi: Double
): String {
    val frame = if (bitmapWidth != null && bitmapHeight != null) {
        "bitmap ${bitmapWidth}x${bitmapHeight}"
    } else {
        "NULL"
    }
    return "render mag=" + String.format(Locale.ROOT, "%.2f", frameMag) +
        " dpi=" + String.format(Locale.ROOT, "%.0f", surfaceDpi) +
        " -> " + frame
}
