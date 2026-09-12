package com.naviveylin.auto

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.DiagnosticsLog
import kotlin.math.roundToInt
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto free-driving screen (spec: auto/free-driving): a
 * destination-free [NavigationTemplate] showing a heading-up,
 * vehicle-following map with the GPS marker, compass rose, and current street
 * name. No turn-by-turn hints, no lane guidance, no travel estimate — there
 * is no target.
 *
 * Pushed on top of the map screen from the "Free driving" menu row; BACK and
 * the "Exit" action pop back to the map view. The screen never touches the
 * session's `isNavigating` state machine (design D1).
 */
class FreeDrivingScreen(
    carContext: CarContext
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var streetJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()
    private val client by lazy { entryPoint.autoClientProvider().client() }

    private val streetNameUpdater = StreetNameUpdater()
    private val autoZoomController = AutoZoomController()

    /**
     * Host pan-mode handling (spec: auto/map-pan): disengages follow and
     * suspends auto-zoom on pan entry, re-engages follow on exit, and
     * converts pan/pinch gestures to viewport changes. Lazy so the renderer
     * is not forced before the surface is available.
     */
    private val panHandler: MapPanHandler by lazy {
        MapPanHandler(mapRenderer, autoZoomController) { surfaceWidth to surfaceHeight }
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    // Host-reported stable area (surface pixels; empty = unknown): the street
    // label and compass rose stay inside it so host chrome never covers them.
    private val stableArea = Rect()

    // Host-reported visible area (surface pixels; empty = unknown): the
    // *current* guaranteed-visible region, tracked separately from the stable
    // area (design D1) — the street-name label anchors to the more
    // conservative of the two.
    private val visibleArea = Rect()

    /** Current street name label text; null/blank = nothing drawn. */
    @Volatile
    private var streetName: String? = null

    /** Latest heading in radians for the compass rose and viewport; null until a fix. */
    @Volatile
    private var headingRadians: Double? = null

    /** Latest ground speed (km/h) for the speed readout; NaN when unknown. */
    @Volatile
    private var currentSpeedKmH: Double = Double.NaN

    /** Latest road speed limit (km/h) for the limit sign; NaN when undefined. */
    @Volatile
    private var maxSpeedKmH: Double = Double.NaN

    /** Auto-zoom from the shared settings (default on). */
    @Volatile
    private var autoZoomEnabled: Boolean = true

    /**
     * Effective speed/bearing derivation (spec: auto-smooth-follow — "Fix
     * feed from follow-mode screens"): GPS value when valid, else
     * movement-derived, else the last effective value. Shared with
     * [NavigationScreen] via core [com.naviveylin.core.AutoPositionUtil].
     */
    private val fixDerivation = AutoFixDerivation()

    private val mapRenderer: AutoMapRenderer by lazy {
        val renderDpi = carContext.resources.displayMetrics.densityDpi.toDouble()
        AutoMapRenderer(
            client,
            renderDpi,
            DEFAULT_LAT,
            DEFAULT_LON,
            DEFAULT_AA_ZOOM
        )
    }

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        // BACK and the system back action both leave free driving (spec:
        // "Exit free driving" — back returns to the map view).
        enableBackNavigation()

        // Shared settings: auto-zoom (speed → magnification) applies live.
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { settings ->
                    autoZoomEnabled = settings.autoZoomEnabled
                    Log.d(TAG, "settings loaded: autoZoomEnabled=$autoZoomEnabled")
                }
                .onFailure { Log.w(TAG, "loading settings failed", it) }
        }

        // Surface overlays: compass rose (rotates with the map) + current
        // speed readout below it (badge shows the current speed — free driving
        // has no speed-limit data) + the street-name label at the bottom. No
        // navigation hint panel — free driving has no target.
        mapRenderer.overlayDrawer = { canvas, w, h ->
            val density = (surfaceDpi / 160.0).toFloat()
            SurfaceIndicators.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                stableBounds = stableArea,
                density = density,
                angleRadians = headingRadians ?: 0.0,
                currentKmH = currentSpeedKmH,
                maxKmH = maxSpeedKmH,
                drawSpeedLimitSign = true
            )
            StreetNameLabel.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                stableBounds = stableArea,
                visibleBounds = visibleArea,
                name = streetName.orEmpty()
                // No bottom reserve: free driving has no host ETA card (design D3).
            )
            SurfaceAttribution.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                usableBounds = stableArea
            )
        }

        // If the host delivered a surface we cannot lock (AAOS emulator quirk:
        // it locks surfaces it re-delivers after a screen transition), drop it
        // and ask the host for a fresh one. Throttled inside the renderer and
        // capped per screen start so a persistently-bad surface does not
        // invalidate the template forever.
        mapRenderer.onSurfaceFailed = {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
            ) {
                surfaceRefreshAttempts++
                Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                invalidate()
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                registerSurfaceCallback()
                mapRenderer.resume()
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
                mapRenderer.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start.
                mapRenderer.releaseSurface()
                // NOTE: no unregisterSurfaceCallback() here. The car-app
                // library starts the new screen BEFORE stopping the old one
                // (ScreenManager.pushInternal), so an onStop unregister would
                // null the surface callback the new screen just registered —
                // the host then re-delivers a second surface and locks both
                // (IAE on every lockCanvas). Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                stopObserving()
                unregisterSurfaceCallback()
                mapRenderer.shutdown()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        return try {
            buildTemplate()
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "FreeDrivingTemplate build failed", e)
            SafeScreen.errorTemplate(carContext, e.message)
        }
    }

    private fun buildTemplate(): NavigationTemplate {
        // NavigationTemplate: the ONLY template with no content slot — the map
        // fills the surface, no host-rendered panel/box (MapWithContentTemplate
        // showed a "Free driving" box, MapTemplate a "No items" placeholder).
        // The earlier belief that the emulator host locks the navigation
        // surface predates the renderer fixes (the old drawToSurface had no
        // finally-unlock, so one draw exception leaked the lock and every
        // later lockCanvas threw "already locked"); with the release/isValid/
        // invalidate fixes the surface renders. No NavigationInfo is passed,
        // so the host renders the bare map; free driving never touches the
        // session's isNavigating state machine (design D1). The host draws its
        // own compass — cosmetic overlap with the app's rose, accepted.
        return MapTemplateFactory.buildFullScreenTemplate(
            mapActionStrip = NavigationScreenActions.freeDrivingMapActionStrip { exitFreeDriving() },
            actionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.zoomInAction { onZoomIn() })
                .addAction(NavigationScreenActions.zoomOutAction { onZoomOut() })
                .build(),
            panModeListener = panHandler
        )
    }

    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            locationProvider.position().collect { pos ->
                if (pos != null) {
                    // A single bad fix must never kill the collect flow —
                    // otherwise the marker freezes at the last good fix.
                    runCatching { onGpsFix(pos) }
                        .onFailure { Log.w(TAG, "onGpsFix failed", it) }
                }
            }
        }

        // Basemap data changes (download/update/delete while the app runs):
        // re-render without an app restart (spec: basemap-loading).
        scope.launch {
            entryPoint.basemapReloadNotifier().revision.collect { revision ->
                if (revision > 0L) {
                    mapRenderer.invalidateData()
                }
            }
        }
    }

    /**
     * Apply a GPS fix: marker, heading-up rotation, auto-zoom, street name.
     * GPX replay tracks usually lack a GPS bearing — fall back to the
     * movement direction between fixes so heading-up still engages.
     */
    private fun onGpsFix(pos: AutoPosition) {
        val nowMs = System.currentTimeMillis()
        val (speed, bearing) = fixDerivation.derive(pos, nowMs)
        Log.d(
            TAG,
            "GPS fix lat=${pos.lat} lon=${pos.lon} bearing=${pos.bearing} " +
                "effBearing=$bearing speedKmH=${pos.speedKmH} effSpeed=$speed"
        )
        currentSpeedKmH = speed
        mapRenderer.setGpsMarker(
            pos.lat, pos.lon, bearing, pos.accuracy,
            speedKmH = speed,
            timeMs = nowMs
        )

        // Heading-up always (spec: "Heading-up orientation"), independent of
        // the shared navNorthUp setting.
        val angle = headingAngleRadians(bearing)
        if (angle != null) {
            headingRadians = angle
        }
        // Speed-driven auto-zoom (shared setting; speed → magnification).
        // The feed is gated on !panning (design D2) so the controller state
        // stays frozen during a pan; the commit is gated on !panning too
        // (design D1) so a band-crossing zoom can never re-engage follow
        // mid-pan.
        val newZoom = autoZoomTarget(panHandler.panning, autoZoomEnabled, speed, autoZoomController)

        if (shouldCommitViewport(panHandler.panning, angle, newZoom)) {
            // setViewport disengages follow mode, so re-engage + re-center
            // right after (same pattern as NavigationScreen). While panned the
            // commit is gated off — heading-up is always computed here, so
            // without the gate every fix would re-engage follow and yank the
            // map back (spec: auto/map-pan — follow suspended while panned).
            val vp = mapRenderer.viewportState.value
            val zoom = newZoom ?: vp.zoom.toDouble()
            // The integer slot keeps the viewport model level; the 5th arg is
            // the fractional render magnification (spec: auto-speed-zoom —
            // Smooth zoom transitions delta — same shape as pinch zoomStep).
            mapRenderer.setViewport(vp.lat, vp.lon, zoom.roundToInt(), angle ?: vp.angle, zoom)
            // Re-engage follow WITHOUT snapping: the extrapolation loop eases
            // the display to the fix (smooth correction, spec:
            // auto-smooth-follow). reCenter() would snap the map back per fix.
            mapRenderer.reengageFollow()
            if (newZoom != null) {
                Log.d(TAG, "autoZoom commit speed=${pos.speedKmH} mag=$newZoom")
            }
        }

        resolveStreetName(pos)
    }

    /**
     * Throttled bearing-aware road lookup (design D4): native call off the
     * main thread; on success update/clear the label and the limit sign and
     * mark the position as resolved — on failure keep the last values. One
     * `getRoadAt` call feeds both the street label (name + ref) and the
     * speed-limit sign, so they always describe the same road (spec:
     * auto/free-driving, road-lookup-bearing).
     */
    private fun resolveStreetName(pos: AutoPosition) {
        if (!streetNameUpdater.shouldGeocode(pos.lat, pos.lon)) return
        if (streetJob?.isActive == true) return
        streetJob = scope.launch {
            val road = withContext(Dispatchers.Default) {
                try {
                    client.getRoadAt(pos.lat, pos.lon, pos.bearing)
                } catch (e: Exception) {
                    Log.w(TAG, "road info lookup failed", e)
                    null
                }
            }
            if (road != null) {
                streetName = StreetNameUpdater.roadDisplayText(road.ref, road.name)
                // getRoadAt returns NaN when the road has no limit — hide the
                // sign (spec: "No sign without a limit").
                maxSpeedKmH = road.maxSpeedKmH.takeIf { !it.isNaN() } ?: Double.NaN
                streetNameUpdater.markGeocoded(pos.lat, pos.lon)
                mapRenderer.requestRender()
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun exitFreeDriving() {
        Log.d(TAG, "Exit free driving")
        screenManager.pop()
    }

    private fun onZoomIn() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "free driving zoom+ $zoom (auto-zoom suspended)")
        mapRenderer.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        mapRenderer.reCenter()
    }

    private fun onZoomOut() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        Log.d(TAG, "free driving zoom- $zoom (auto-zoom suspended)")
        mapRenderer.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        mapRenderer.reCenter()
    }

    private fun registerSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(object : SurfaceCallback {
            override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                val surface = surfaceContainer.surface ?: return
                surfaceWidth = surfaceContainer.width
                surfaceHeight = surfaceContainer.height
                surfaceDpi = surfaceContainer.dpi.takeIf { it > 0 }?.toDouble() ?: DEFAULT_DPI
                Log.d(TAG, "Free driving surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
                DiagnosticsLog.log(
                    "FREEDRIVE",
                    "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
                )
                runCatching { client.setMapDpi(surfaceDpi) }
                    .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                mapRenderer.updateProjectionDpi(surfaceDpi)
                mapRenderer.onSurfaceCreated(surface, surfaceWidth, surfaceHeight)
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Free driving surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                visibleArea.setEmpty()
                mapRenderer.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visible: Rect) {
                // Tracked separately from the stable area: the visible area is
                // the *current* guaranteed-visible region, the stable area
                // accounts for occlusions "as if always present". The
                // street-name label anchors to the more conservative of the
                // two (design D1).
                visibleArea.set(visible)
                mapRenderer.requestRender()
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                stableArea.set(newStableArea)
                mapRenderer.requestRender()
            }

            // Pan gestures (spec: auto/map-pan): the host forwards them only
            // while pan mode is active; the handler converts them to viewport
            // changes and gates on its own panning flag.
            override fun onScroll(distanceX: Float, distanceY: Float) {
                panHandler.onScroll(distanceX, distanceY)
            }

            override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
                panHandler.onScale(focusX, focusY, scaleFactor)
            }
        })
    }

    private fun unregisterSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(null)
    }

    companion object {
        /**
         * Speed-driven auto-zoom target: never while panned (spec:
         * auto/map-pan — auto-zoom suspended while panned), otherwise when
         * enabled and the speed is valid. Same gate as
         * [NavigationScreen.autoZoomTarget] — the two screens share the
         * pan-suspension semantics (design D2).
         */
        fun autoZoomTarget(
            panning: Boolean,
            autoZoomEnabled: Boolean,
            speedKmH: Double,
            controller: AutoZoomController
        ): Double? =
            if (!panning && autoZoomEnabled && speedKmH >= 0.0) controller.onSpeed(speedKmH) else null

        private const val TAG = "FreeDrivingScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

        /** Fallback center (Dortmund — same as the phone app default). */
        private const val DEFAULT_LAT = 51.5136
        private const val DEFAULT_LON = 7.4653

        /** City-level zoom for the surface before GPS arrives. */
        private const val DEFAULT_AA_ZOOM = 13

        /**
         * Heading-up viewport rotation in radians; null when the bearing is
         * unknown (NaN) or invalid (< 0), so the previous heading is kept.
         * NEGATIVE bearing: the native/Kotlin projections rotate by R(-angle),
         * so angle = -bearing makes the driving direction point up (same
         * convention as the phone app, MapCanvasViewModel). Extracted for
         * unit tests (spec: "Heading-up orientation").
         */
        internal fun headingAngleRadians(bearingDegrees: Double): Double? =
            if (bearingDegrees >= 0.0) -Math.toRadians(bearingDegrees) else null
    }
}
