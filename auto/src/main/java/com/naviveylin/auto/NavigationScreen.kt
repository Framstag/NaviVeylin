package com.naviveylin.auto

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.AutoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen that displays turn-by-turn navigation via [NavigationTemplate].
 * Observes [NavigationViewModel.state] and invalidates the template on changes.
 *
 * Layout (spec: auto-map-layout): the left-edge map action strip holds the
 * stop action (X — stops navigation) and the route-description action; the
 * right-edge action strip holds the visualisation buttons (zoom). The host
 * instruction panel renders the current-step maneuver + distance, the next
 * step and lane guidance (spec: auto/navigation-view); the map surface
 * carries only the compass rose, the speed badge ([SurfaceIndicators]) and
 * the current street name ([StreetNameLabel]). Stop action + system back
 * are the leave affordances during navigation (spec: auto/navigation-view —
 * "Leave navigation at any time"); the map action strip carries no back
 * button (see init).
 */
class NavigationScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var lastState: NavigationState? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()

    /** Applies the shared map style to the native client (deduped). */
    private val styleApplier = CarStyleApplier { style ->
        entryPoint.autoClientProvider().client().loadStyleSheet(style)
    }

    private var surfaceWidth = 0

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    // Host-reported stable area (surface pixels; empty = unknown): the hint
    // panel stays inside it so host chrome never covers the hints.
    private val stableArea = Rect()

    /** Navigation orientation: north-up unless the shared setting says otherwise. */
    private var navNorthUp = true

    /** Lane guidance visibility from the shared settings (default on). */
    private var laneHintsEnabled = true

    /** Last route geometry ref — route re-renders on change (new navigation/reroute). */
    private var lastRouteLats: DoubleArray? = null

    /** Speed-driven auto-zoom from the shared settings (default on). */
    private val autoZoomController = AutoZoomController()
    private var autoZoomEnabled: Boolean = true

    /** Throttled street-name resolution (same pattern as free driving). */
    private val streetNameUpdater = StreetNameUpdater()
    private var streetName: String? = null
    private var streetJob: Job? = null

    private val mapRenderer: AutoMapRenderer by lazy {
        val client = entryPoint.autoClientProvider().client()
        // The native renderer projects with the client's configured physical
        // DPI (from the phone display metrics), not the car surface DPI.
        val renderDpi = carContext.resources.displayMetrics.densityDpi.toDouble()
        AutoMapRenderer(
            client,
            renderDpi,
            DEFAULT_LAT,
            DEFAULT_LON,
            DEFAULT_AA_ZOOM
        )
    }

    init {
        // Back during navigation stops it; the session observer then pops the
        // screen back to the root menu.
        carContext.getOnBackPressedDispatcher().addCallback(
            this,
            backCallback { navigationViewModel.stopNavigation() }
        )

        // Surface overlays: right-edge indicators (rotating compass rose +
        // speed badge) and the current street-name label at the bottom.
        // The host instruction panel renders the navigation instructions
        // (maneuver, steps, lanes) — nothing instruction-related on the surface.
        mapRenderer.overlayDrawer = { canvas, w, h ->
            val state = lastState
            val density = (surfaceDpi / 160.0).toFloat()
            if (state != null) {
                val headingAngle = if (navNorthUp) 0.0 else state.position?.bearing?.let { -Math.toRadians(it) } ?: 0.0
                SurfaceIndicators.draw(
                    canvas = canvas,
                    surfaceWidth = w,
                    surfaceHeight = h,
                    stableBounds = stableArea,
                    density = density,
                    angleRadians = headingAngle,
                    currentKmH = state.currentSpeedKmH,
                    maxKmH = state.maxSpeedKmH,
                    // Navigation shows the current speed in the badge and the
                    // speed limit as the round sign below it (spec:
                    // auto-map-layout — "Speed-limit indicator during navigation").
                    drawSpeedLimitSign = true
                )
            }
            StreetNameLabel.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                usableBounds = stableArea,
                name = streetName.orEmpty()
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

        // Shared settings: lane hints + navigation orientation + auto-zoom.
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { settings ->
                    laneHintsEnabled = settings.laneHintsEnabled
                    navNorthUp = settings.navNorthUp
                    autoZoomEnabled = settings.autoZoomEnabled
                    // Apply the shared map style (loadStyleSheet blocks on the
                    // native DB thread — run off the main thread; deduped).
                    val style = settings.styleSheet
                    scope.launch(Dispatchers.Default) {
                        if (!styleApplier.apply(style)) {
                            Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
                        }
                    }
                    mapRenderer.requestRender()
                }
                .onFailure { Log.w(TAG, "loading settings failed", it) }
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
                autoZoomController.suspend()
                mapRenderer.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start.
                mapRenderer.releaseSurface()
                // NOTE: no unregisterSurfaceCallback() here — see
                // FreeDrivingScreen: an onStop unregister nulls the callback
                // the next screen registered (car-app starts the new screen
                // before stopping the old one), forcing a second surface
                // delivery that the host locks. Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                stopObserving()
                autoZoomController.suspend()
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
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "NavigationTemplate build failed", e)
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun buildTemplate(): NavigationTemplate {
        val state = navigationViewModel.state.value
        if (!state.isNavigating) {
            return NavigationTemplateFactory.buildNavigationTemplate(false, null, null, null)
        }
        // Host instruction panel (spec: auto/navigation-view): current-step
        // maneuver + distance, next-next step, lane guidance.
        val routingInfo = NavigationTemplateMapper.routingInfoFromState(
            state,
            ManeuverGlyphs::forTurnType,
            includeLanes = laneHintsEnabled,
            laneImageFor = ManeuverGlyphs::lanesImage
        )
        return NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true,
            travelEstimate = buildTravelEstimate(state),
            mapActionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.stopAction { navigationViewModel.stopNavigation() })
                .addAction(NavigationScreenActions.routeListAction { onShowRouteDescription() })
                .build(),
            actionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.zoomInAction { onZoomIn() })
                .addAction(NavigationScreenActions.zoomOutAction { onZoomOut() })
                .addAction(MapStripActions.infoAction {
                    screenManager.push(AboutScreen(carContext))
                })
                .build(),
            routingInfo = routingInfo
        )
    }

    /** Push the route-description list; navigation keeps running underneath. */
    private fun onShowRouteDescription() {
        Log.d(TAG, "Show route description")
        screenManager.push(RouteDescriptionScreen(carContext, navigationViewModel))
    }

    /** Start observing state when screen becomes visible. */
    fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            navigationViewModel.state
                .collect { state ->
                    val changed = hasStateChanged(state)
                    lastState = state
                    // Route polyline for the map renderer ("_route" style);
                    // re-renders when a new route (navigation start/reroute)
                    // arrives and clears when navigation stops.
                    if (state.routeLats !== lastRouteLats) {
                        lastRouteLats = state.routeLats
                        mapRenderer.setRoute(state.routeLats, state.routeLons)
                    }
                    // Destination marker follows the navigation context
                    // (spec: auto-destination-details); NaN clears it.
                    mapRenderer.setDestinationMarker(state.destLat, state.destLon, state.destinationName)
                    if (changed) {
                        invalidate()
                        // Redraw the surface hints without waiting for a GPS tick.
                        mapRenderer.requestRender()
                        // Live settings: lane hints + orientation + auto-zoom
                        // apply without a screen restart (throttled by state changes).
                        scope.launch {
                            runCatching { settingsProvider.load() }
                                .onSuccess { settings ->
                                    if (settings.laneHintsEnabled != laneHintsEnabled ||
                                        settings.navNorthUp != navNorthUp ||
                                        settings.autoZoomEnabled != autoZoomEnabled
                                    ) {
                                        laneHintsEnabled = settings.laneHintsEnabled
                                        navNorthUp = settings.navNorthUp
                                        autoZoomEnabled = settings.autoZoomEnabled
                                        mapRenderer.requestRender()
                                    }
                                }
                                .onFailure { Log.w(TAG, "settings reload failed", it) }
                        }
                    }
                }
        }
        // GPS position: prefer the AA location provider (the AA-only process
        // has no phone UI mirroring into navigationViewModel).
        scope.launch {
            locationProvider.position().collect { pos ->
                if (pos != null) {
                    mapRenderer.setGpsMarker(pos.lat, pos.lon, pos.bearing, pos.accuracy)
                    // Speed-driven auto-zoom (shared setting) + heading-up
                    // rotation: one viewport commit per fix.
                    val zoom = if (autoZoomEnabled && pos.speedKmH >= 0.0) {
                        autoZoomController.onSpeed(pos.speedKmH)
                    } else {
                        null
                    }
                    val angle = if (!navNorthUp && pos.bearing >= 0.0) {
                        -Math.toRadians(pos.bearing)
                    } else {
                        null
                    }
                    if (angle != null || zoom != null) {
                        val vp = mapRenderer.viewportState.value
                        val newZoom = zoom ?: vp.zoom
                        mapRenderer.setViewport(
                            vp.lat, vp.lon, newZoom, angle ?: vp.angle, newZoom.toDouble()
                        )
                        mapRenderer.reCenter()
                        if (zoom != null) {
                            Log.d(TAG, "nav autoZoom commit speed=${pos.speedKmH} mag=$zoom")
                        }
                    }
                    resolveStreetName(pos)
                }
            }
        }
    }

    /**
     * Throttled reverse geocode for the current street name (spec:
     * auto/navigation-view — "Current street name shown during navigation").
     * Native lookup off the main thread; failures keep the last label,
     * unnamed roads clear it (design D4, same as free driving).
     */
    private fun resolveStreetName(pos: AutoPosition) {
        if (!streetNameUpdater.shouldGeocode(pos.lat, pos.lon)) return
        if (streetJob?.isActive == true) return
        streetJob = scope.launch {
            val name = withContext(Dispatchers.Default) {
                try {
                    val address = entryPoint.autoClientProvider().client().getAddressAt(pos.lat, pos.lon)
                    streetNameUpdater.streetFromAddress(address)
                } catch (e: Exception) {
                    Log.w(TAG, "street name lookup failed", e)
                    null
                }
            }
            if (name != null) {
                streetNameUpdater.markGeocoded(pos.lat, pos.lon)
            }
            streetName = name
            mapRenderer.requestRender()
        }
    }

    /** Stop observing state when screen is hidden. */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun hasStateChanged(newState: NavigationState): Boolean {
        return NavigationTemplateMapper.hasStateChanged(lastState, newState)
    }

    private fun buildTravelEstimate(state: NavigationState): TravelEstimate {
        // remainingDistance arrives in meters from the native engine
        // (NavigationListener.onArrivalEstimate); rounded + unit-split for
        // display (km above 1 km), the host renders per the given unit.
        val remainingDistance = NavigationTemplateMapper.distanceForDisplay(state.remainingDistance)
        val remainingTimeSeconds = ((state.etaMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
        val arrivalDate = java.util.Date(state.etaMillis)
        val zonedDateTime = java.time.ZonedDateTime.ofInstant(
            arrivalDate.toInstant(),
            java.time.ZoneId.systemDefault()
        )

        return TravelEstimate.Builder(remainingDistance, zonedDateTime)
            .setRemainingTimeSeconds(remainingTimeSeconds)
            .build()
    }

    private fun onZoomIn() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "nav zoom+ $zoom")
        mapRenderer.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        mapRenderer.reCenter()
    }

    private fun onZoomOut() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        Log.d(TAG, "nav zoom- $zoom")
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
                Log.d(TAG, "Nav surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
                DiagnosticsLog.log(
                    "NAV",
                    "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
                )
                runCatching { entryPoint.autoClientProvider().client().setMapDpi(surfaceDpi) }
                    .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                mapRenderer.updateProjectionDpi(surfaceDpi)
                mapRenderer.onSurfaceCreated(surface, surfaceWidth, surfaceHeight)
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Nav surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                mapRenderer.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visibleArea: Rect) {
                if (stableArea.isEmpty()) stableArea.set(visibleArea)
                mapRenderer.requestRender()
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                stableArea.set(newStableArea)
                mapRenderer.requestRender()
            }
        })
    }

    private fun unregisterSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(null)
    }

    companion object {
        private const val TAG = "NavigationScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

        /**
         * Back affordance during navigation: stops navigation (spec:
         * auto/navigation-view — "Leave navigation at any time"). Factory so
         * the handler behavior is unit-testable without a live CarContext.
         */
        fun backCallback(onBack: () -> Unit): androidx.activity.OnBackPressedCallback =
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBack()
                }
            }

        /** Fallback center (Dortmund — same as the phone app default). */
        private const val DEFAULT_LAT = 51.5136
        private const val DEFAULT_LON = 7.4653

        /**
         * Fallback zoom for the navigation surface before GPS arrives. Kept
         * high (street level): navigation starts with a maneuver, and the
         * first meters are the most demanding — better to show more detail
         * than less (speed-driven auto-zoom then adjusts to the drive).
         */
        private const val DEFAULT_AA_ZOOM = 17
    }
}
