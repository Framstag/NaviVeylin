package com.naviveylin.auto

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import kotlin.math.roundToInt
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.stringResolver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * the current street name ([StreetNameLabel]). The host ETA card stop button
 * (via [NavigationManagerController]) and system back are the leave
 * affordances during navigation (spec: auto/navigation-view —
 * "Leave navigation at any time"); the map action strip carries the
 * route-description action only, no stop or back button (see init).
 */
class NavigationScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    /** Host day/night state (see [NavigationSession.hostDark]). */
    private val hostDark: StateFlow<Boolean> = MutableStateFlow(carContext.isDarkMode())
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

    /** Applies the host day/night state to the stylesheet `daylight` flag (deduped). */
    private val daylightApplier = CarDaylightApplier { dark ->
        try {
            entryPoint.autoClientProvider().client().setStyleSheetFlag("daylight", !dark)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setStyleSheetFlag failed", e)
            false
        }
    }

    /**
     * (Re)push the host day/night state to the native stylesheet and force a
     * full render. The applier is reset first so a push that was silently
     * dropped by the native side (DB not initialized) is not deduped away.
     */
    private fun pushHostDark() {
        daylightApplier.reset()
        if (daylightApplier.apply(hostDark.value)) {
            mapRenderer.invalidateStyle()
        }
    }

    private var surfaceWidth = 0

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    // Host-reported stable area (surface pixels; empty = unknown): the hint
    // panel stays inside it so host chrome never covers the hints.
    private val stableArea = Rect()

    // Host-reported visible area (surface pixels; empty = unknown): the
    // *current* guaranteed-visible region, tracked separately from the stable
    // area (design D1) — the street-name label anchors to the more
    // conservative of the two.
    private val visibleArea = Rect()

    /**
     * The map label is safe only when the host delivered an area that clears
     * the surface bottom (design D5): otherwise the host ETA card may cover
     * it and the street name goes into the card via [TravelEstimate.setTripText]
     * instead.
     */
    private fun mapLabelSafe(): Boolean {
        val density = (surfaceDpi / 160.0).toFloat()
        return StreetNameLabel.isMapLabelSafe(stableArea, visibleArea, surfaceHeight, density)
    }

    /** Navigation orientation: north-up unless the shared setting says otherwise. */
    private var navNorthUp = true

    /** Lane guidance visibility from the shared settings (default on). */
    private var laneHintsEnabled = true

    /** Last route geometry ref — route re-renders on change (new navigation/reroute). */
    private var lastRouteLats: DoubleArray? = null

    /** Speed-driven auto-zoom from the shared settings (default on). */
    private val autoZoomController = AutoZoomController()
    private var autoZoomEnabled: Boolean = true

    /**
     * Host pan-mode handling (spec: auto/map-pan): disengages follow and
     * suspends auto-zoom on pan entry, re-engages follow on exit, and
     * converts pan/pinch gestures to viewport changes. Lazy so the renderer
     * is not forced before the surface is available.
     */
    private val panHandler: MapPanHandler by lazy {
        MapPanHandler(mapRenderer, autoZoomController) { surfaceWidth to surfaceHeight }
    }

    /** Throttled street-name resolution (same pattern as free driving). */
    private val streetNameUpdater = StreetNameUpdater()
    private var streetName: String? = null
    private var streetJob: Job? = null

    /**
     * Effective speed/bearing derivation (spec: auto-smooth-follow — "Fix
     * feed from follow-mode screens"): GPS value when valid, else
     * movement-derived, else the last effective value. Shared with free
     * driving via core [com.naviveylin.core.AutoPositionUtil]. The derived
     * speed feeds the renderer so the extrapolation loop (smooth scrolling)
     * runs — without it the routing map snaps per 1 Hz fix.
     */
    private val fixDerivation = AutoFixDerivation()

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
            // The map label is drawn only when the host geometry is safe
            // (design D5): a full-surface/empty stable+visible area means the
            // ETA card may cover the bottom-center, so the street name is
            // rendered by the host in the ETA card (setTripText) instead.
            if (mapLabelSafe()) {
                StreetNameLabel.draw(
                    canvas = canvas,
                    surfaceWidth = w,
                    surfaceHeight = h,
                    density = density,
                    stableBounds = stableArea,
                    visibleBounds = visibleArea,
                    name = streetName.orEmpty(),
                    // Navigation reserve: the host ETA card sits at the bottom
                    // (bottom-left on the user's head unit); the reserve keeps the
                    // label clear even when the host geometry is wrong (design D3).
                    bottomReserveDp = STREET_NAME_BOTTOM_RESERVE_DP
                )
            }
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
                        } else {
                            // DB is ready (style loaded) — (re)push the host
                            // day/night flag: the startup push may have been
                            // dropped while the DB was still initializing.
                            pushHostDark()
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
            SafeScreen.errorTemplate(carContext, e.message)
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
            laneImageFor = ManeuverGlyphs::lanesImage,
            resolver = carContext.stringResolver()
        )
        return NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true,
            travelEstimate = buildTravelEstimate(state),
            mapActionStrip = NavigationScreenActions.navigationMapActionStrip { onShowRouteDescription() },
            actionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.zoomInAction { onZoomIn() })
                .addAction(NavigationScreenActions.zoomOutAction { onZoomOut() })
                .build(),
            routingInfo = routingInfo,
            panModeListener = panHandler
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
                    // Current street from the route's way (spec:
                    // auto/navigation-view — street from the route, not an area
                    // search): updates when the road changes, without a template
                    // rebuild. The GPS-driven fallback (resolveStreetName) only
                    // runs when no road info is in state (off-route).
                    val roadText = streetNameFromState(state)
                    if (roadText != null && roadText != streetName) {
                        streetName = roadText
                        mapRenderer.requestRender()
                        // The host ETA card carries the street name when the map
                        // label is not safe (design D5) — needs a template rebuild.
                        if (!mapLabelSafe()) {
                            invalidate()
                        }
                    }
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
                    // Effective speed/bearing: GPS when valid, else derived
                    // from movement (spec: auto-smooth-follow — "Fix feed from
                    // follow-mode screens"). The speed opens the renderer's
                    // extrapolation gate so the map glides between 1 Hz fixes
                    // instead of snapping (spec: auto/navigation-view —
                    // "Smooth follow-mode scrolling during navigation").
                    val nowMs = System.currentTimeMillis()
                    val fix = effectiveFixArgs(pos, nowMs, fixDerivation)
                    mapRenderer.setGpsMarker(
                        fix.lat, fix.lon, fix.bearing, fix.accuracy,
                        speedKmH = fix.speedKmH,
                        timeMs = fix.timeMs
                    )
                    // Speed-driven auto-zoom (shared setting) + heading-up
                    // rotation: one viewport commit per fix. The zoom feed is
                    // gated on !panning (design D2) so the controller state
                    // stays frozen during a pan; the commit is gated on
                    // !panning too (design D1) so a band-crossing zoom can
                    // never re-engage follow mid-pan.
                    val zoom = autoZoomTarget(panHandler.panning, autoZoomEnabled, fix.speedKmH, autoZoomController)
                    val angle = if (shouldRotateHeadingUp(panHandler.panning, navNorthUp, fix.bearing)) {
                        -Math.toRadians(fix.bearing)
                    } else {
                        null
                    }
                    if (shouldCommitViewport(panHandler.panning, angle, zoom)) {
                        val vp = mapRenderer.viewportState.value
                        val newZoom = zoom ?: vp.zoom.toDouble()
                        // The integer slot keeps the viewport model level; the
                        // 5th arg is the fractional render magnification
                        // (spec: auto-speed-zoom — Smooth zoom transitions
                        // delta — same shape as pinch zoomStep).
                        mapRenderer.setViewport(
                            vp.lat, vp.lon, newZoom.roundToInt(), angle ?: vp.angle, newZoom
                        )
                        // Re-engage follow WITHOUT snapping (smooth correction
                        // via the extrapolation loop, spec: auto-smooth-follow).
                        mapRenderer.reengageFollow()
                        if (zoom != null) {
                            Log.d(TAG, "nav autoZoom commit speed=${pos.speedKmH} mag=$zoom")
                        }
                    }
                    resolveStreetName(pos)
                }
            }
        }

        // Host day/night: push the stylesheet `daylight` flag and re-render on
        // change (tunnel entry, dusk). Deduped by the applier; the native side
        // reloads the variant on its DB thread. invalidateStyle forces a full
        // render so the stale-variant overrun buffer is never blitted.
        scope.launch {
            hostDark.collect { dark ->
                if (daylightApplier.apply(dark)) {
                    mapRenderer.invalidateStyle()
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
     * Throttled street-name fallback for off-route / no-road-info states
     * (spec: auto/navigation-view — street from the route while on route, so
     * the GPS-driven lookup only runs when the route way is unavailable).
     * Bearing-aware native lookup off the main thread; failures keep the last
     * label, unnamed roads clear it (design D4, same as free driving).
     */
    private fun resolveStreetName(pos: AutoPosition) {
        // On route, the street comes from the route's way via state — the
        // GPS-driven fallback must not fight it.
        if (lastState?.currentRoadInfo != null) return
        if (!streetNameUpdater.shouldGeocode(pos.lat, pos.lon)) return
        if (streetJob?.isActive == true) return
        streetJob = scope.launch {
            val road = withContext(Dispatchers.Default) {
                try {
                    entryPoint.autoClientProvider().client().getRoadAt(pos.lat, pos.lon, pos.bearing)
                } catch (e: Exception) {
                    Log.w(TAG, "street name lookup failed", e)
                    null
                }
            }
            if (road != null) {
                streetNameUpdater.markGeocoded(pos.lat, pos.lon)
            }
            val name = road?.let { StreetNameUpdater.roadDisplayText(it.ref, it.name) }
            val changed = name != streetName
            streetName = name
            mapRenderer.requestRender()
            // The host ETA card carries the street name when the map label is
            // not safe (design D5) — the card text needs a template rebuild.
            if (changed && !mapLabelSafe()) {
                invalidate()
            }
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

        val builder = TravelEstimate.Builder(remainingDistance, zonedDateTime)
            .setRemainingTimeSeconds(remainingTimeSeconds)
        // Street name in the host ETA card when the map label is not safe
        // (design D5): the host positions the card, so the name is never
        // covered. When the map label is safe, no trip text — the map label
        // carries the name.
        tripTextFor(mapLabelSafe(), streetName)?.let { builder.setTripText(CarText.create(it)) }
        return builder.build()
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
                // Re-push the host day/night flag before the first render: the
                // startup push may have been dropped while the DB was still
                // initializing (warmup race).
                pushHostDark()
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Nav surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                visibleArea.setEmpty()
                mapRenderer.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visible: Rect) {
                // Tracked separately from the stable area: the visible area is
                // the *current* guaranteed-visible region (excludes the ETA
                // card while shown), the stable area accounts for occlusions
                // "as if always present". The street-name label anchors to
                // the more conservative of the two (design D1).
                val wasSafe = mapLabelSafe()
                visibleArea.set(visible)
                mapRenderer.requestRender()
                // Safety flip (design D5): the street name moves between the
                // map label and the host ETA card — rebuild the template.
                if (wasSafe != mapLabelSafe()) {
                    invalidate()
                }
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                val wasSafe = mapLabelSafe()
                stableArea.set(newStableArea)
                mapRenderer.requestRender()
                if (wasSafe != mapLabelSafe()) {
                    invalidate()
                }
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
         * Effective fix for the map renderer (spec: auto-smooth-follow —
         * "Fix feed from follow-mode screens"): GPS speed/bearing when valid,
         * else movement-derived via [AutoFixDerivation]. The derived speed is
         * what opens the renderer's extrapolation gate — passing nothing (the
         * NaN default) left the routing map snapping per fix. Exposed as a
         * pure seam for tests.
         */
        internal fun effectiveFixArgs(
            pos: AutoPosition,
            nowMs: Long,
            derivation: AutoFixDerivation
        ): GpsFixArgs {
            val (speed, bearing) = derivation.derive(pos, nowMs)
            return GpsFixArgs(
                lat = pos.lat,
                lon = pos.lon,
                bearing = bearing,
                accuracy = pos.accuracy,
                speedKmH = speed,
                timeMs = nowMs
            )
        }

        private const val TAG = "NavigationScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

        /**
         * Bottom reserve (dp) for the street-name label while navigating: a
         * safety margin above the resolved stable/visible bottom so the host
         * ETA card never covers the label (design D3). Free driving passes 0.
         */
        private const val STREET_NAME_BOTTOM_RESERVE_DP = 24f

        /**
         * Street name for the host ETA card (design D5): only when the map
         * label is not safe (the host delivered no area clearing the surface
         * bottom, so the card may cover the label). When the map label is
         * safe, the card carries no trip text — the map label shows the name.
         */
        fun tripTextFor(mapLabelSafe: Boolean, streetName: String?): String? =
            if (mapLabelSafe) null else streetName

        /**
         * Street label text from the shared navigation state (spec:
         * auto/navigation-view — street from the route's way, not an area
         * search): "ref name" from [NavigationState.currentRoadInfo], or null
         * when no road info is in state (off-route / not navigating — the
         * GPS-driven fallback then owns the label).
         */
        fun streetNameFromState(state: NavigationState?): String? =
            state?.currentRoadInfo?.let { StreetNameUpdater.roadDisplayText(it.ref, it.name) }

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

        /**
         * Heading-up rotation decision: rotate with the bearing unless the
         * map is panned (spec: auto/map-pan — rotation frozen while panned),
         * north-up is set, or the bearing is unknown.
         */
        fun shouldRotateHeadingUp(panning: Boolean, navNorthUp: Boolean, bearing: Double): Boolean =
            !panning && !navNorthUp && bearing >= 0.0

        /**
         * Speed-driven auto-zoom target: never while panned (spec:
         * auto/map-pan — auto-zoom suspended while panned), otherwise when
         * enabled and the speed is valid. Gating the feed (not just the
         * commit) freezes the controller state during the pan — a
         * band-crossing re-engage mid-pan would otherwise advance its
         * stability/cooldown state and could commit a zoom the driver did
         * not ask for right after pan exit (design D2).
         */
        fun autoZoomTarget(
            panning: Boolean,
            autoZoomEnabled: Boolean,
            speedKmH: Double,
            controller: AutoZoomController
        ): Double? =
            if (!panning && autoZoomEnabled && speedKmH >= 0.0) controller.onSpeed(speedKmH) else null

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

/**
 * Renderer fix parameters derived from a GPS fix on the navigation view
 * (spec: auto-smooth-follow — "Fix feed from follow-mode screens"). Pure
 * carrier so the derivation seam [NavigationScreen.effectiveFixArgs] is
 * testable without a live CarContext.
 */
internal data class GpsFixArgs(
    val lat: Double,
    val lon: Double,
    val bearing: Double,
    val accuracy: Double,
    val speedKmH: Double,
    val timeMs: Long
)
