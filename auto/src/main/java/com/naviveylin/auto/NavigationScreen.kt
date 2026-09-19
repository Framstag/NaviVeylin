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
import com.naviveylin.core.AutoSettings
import kotlin.math.roundToInt
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.VehicleAnchorPosition
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
 * carries only the compass rose and the speed badge ([SurfaceIndicators]).
 * The current street name is rendered in the host ETA card via
 * `TravelEstimate.setTripText` (design D1, street-name-host-views), never
 * on the map surface. The host ETA card stop button
 * (via [NavigationManagerController]) and system back are the leave
 * affordances during navigation (spec: auto/navigation-view —
 * "Leave navigation at any time"); the map action strip carries the
 * route-description action only, no stop or back button (see init).
 */
class NavigationScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    /** Resolved dark presentation (preference × host signal; see [NavigationSession.resolvedDark]). */
    private val resolvedDark: StateFlow<Boolean> = MutableStateFlow(carContext.isDarkMode())
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
    private val styleApplier = CarStyleApplier(
        onLoadFailed = { style ->
            // A rejected stylesheet keeps the previous style active and is
            // reported through the shared seam + the car notice (spec:
            // map-styles — "Car reports the failure").
            Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
            reportCarStyleLoadFailure(
                appContext = carContext.applicationContext,
                client = entryPoint.autoClientProvider().client(),
                requestedStyle = style
            )?.let { styleLoadNotifier.notify(it) }
        },
        loadStyle = { style -> entryPoint.autoClientProvider().client().loadStyleSheet(style) }
    )

    /** Posts the car-side stylesheet-failure notice (non-blocking; no template change). */
    private val styleLoadNotifier = CarStyleLoadNotifier(carContext.applicationContext)

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
     * (Re)push the resolved dark presentation to the native stylesheet and
     * force a full render. The applier is reset first so a push that was
     * silently dropped by the native side (DB not initialized) is not deduped
     * away.
     */
    private fun pushDark() {
        // Skip until the renderer exists: the native client may still be
        // building off the main thread, and applying the stylesheet flag must
        // never first-touch it here. Re-pushed from the readiness observer and
        // after surface arrival once the init coroutine built the client.
        if (rendererGate.rendererOrNull() == null) return
        daylightApplier.reset()
        if (daylightApplier.apply(resolvedDark.value)) {
            rendererGate.invalidateStyle()
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

    /** Navigation orientation: north-up unless the shared setting says otherwise. */
    private var navNorthUp = true

    /** Lane guidance visibility from the shared settings (default on). */
    private var laneHintsEnabled = true

    /** Overspeed warning delta (km/h) from the shared settings (default 5). */
    private var overspeedWarningDeltaKmh = 5

    /** Last route geometry ref — route re-renders on change (new navigation/reroute). */
    private var lastRouteLats: DoubleArray? = null

    /** Speed-driven auto-zoom from the shared settings (default on). */
    private val autoZoomController = AutoZoomController()
    private var autoZoomEnabled: Boolean = true

    /**
     * Last speed fed to the auto-zoom controller — lets a re-enable apply the target
     * immediately instead of waiting for the next fix (spec: auto-speed-zoom —
     * Auto-zoom re-enabled after a manual zoom; design D5).
     */
    private var lastSpeedKmH: Double = Double.NaN

    /** Routing vehicle anchor from the shared settings (default center). */
    private var routingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    /**
     * Host pan-mode handling (spec: auto/map-pan): disengages follow and
     * suspends auto-zoom on pan entry, re-engages follow on exit, and
     * converts pan/pinch gestures to viewport changes. Lazy so the renderer
     * is not forced before the surface is available.
     */
    private val panHandler: MapPanHandler by lazy {
        MapPanHandler({ rendererGate.rendererOrNull() }, autoZoomController) { surfaceWidth to surfaceHeight }
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

    /**
     * Renderer-bound state buffers here until the renderer is ready (spec:
     * auto-map-renderer — "Renderer initialization off the car-app main
     * thread"): native client first-touch and the renderer construction run in
     * the [rendererInitJob] coroutine on a background dispatcher, so the
     * constructor never blocks the main thread and never first-touches the
     * Hilt native-client singleton on the host thread.
     */
    private val rendererGate = RendererGate()
    private var rendererInitJob: Job? = null

    init {
        // Back leaves the navigation view in EVERY state (design D3, spec:
        // auto/navigation-view — "Leave navigation at any time"): during
        // navigation it stops navigation (the session observer then pops the
        // screen back to the root menu); once navigation is already inactive
        // it must still leave — the not-navigating fallback template (bare
        // map + lone strip BACK) is otherwise a dead end whose only affordance
        // does nothing.
        carContext.getOnBackPressedDispatcher().addCallback(
            this,
            backCallback {
                navigationBackBehavior(
                    isNavigating = navigationViewModel.state.value.isNavigating,
                    onStopNavigation = { navigationViewModel.stopNavigation() },
                    onLeaveNavigationView = { screenManager.popToRoot() }
                )
            }
        )

        // Surface overlays: right-edge indicators (rotating compass rose +
        // speed badge) and the attribution. The street name is NOT drawn on
        // the surface during navigation — the host ETA card owns it via
        // setTripText (design D1). The host instruction panel renders the
        // navigation instructions (maneuver, steps, lanes) — nothing
        // instruction-related on the surface.
        rendererGate.setOverlayDrawer({ canvas, w, h ->
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
                    overLimitDeltaKmh = overspeedWarningDeltaKmh,
                    // Navigation shows the current speed in the badge and the
                    // speed limit as the round sign below it (spec:
                    // auto-map-layout — "Speed-limit indicator during navigation").
                    drawSpeedLimitSign = true
                )
            }
            // The street name is never drawn on the map surface during
            // navigation (design D1, spec: auto/navigation-view — "No
            // street-name label on the map surface"): it lives in the host
            // travel-estimate card via TravelEstimate.setTripText. The
            // surface carries only the compass rose, the speed badge and
            // the attribution.
            SurfaceAttribution.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                usableBounds = stableArea
            )
        })

        // Renderer readiness: wire surface-failure recovery and re-push the
        // day/night stylesheet flag once the renderer exists (the pre-ready
        // push is skipped — pushDark must never first-touch the native client
        // on the main thread).
        scope.launch {
            rendererGate.renderer.collect { renderer ->
                if (renderer == null) return@collect
                // If the host delivered a surface we cannot lock (AAOS emulator quirk:
                // it locks surfaces it re-delivers after a screen transition), drop it
                // and ask the host for a fresh one. Throttled inside the renderer and
                // capped per screen start so a persistently-bad surface does not
                // invalidate the template forever.
                renderer.onSurfaceFailed = {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
                    ) {
                        surfaceRefreshAttempts++
                        Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                        invalidate()
                    }
                }
                // The startup push was skipped while the client was still
                // building — apply the resolved presentation now.
                pushDark()
            }
        }

        // Async renderer init (design D2): first-touch the native client
        // singleton off the main thread and build the renderer (fixed fallback
        // viewport — navigation centers on the vehicle via follow).
        rendererInitJob = scope.launch {
            val renderer = withContext(Dispatchers.Default) {
                val client = entryPoint.autoClientProvider().client()
                rendererGate.pendingSurfaceDpi()?.let { dpi ->
                    runCatching { client.setMapDpi(dpi) }
                        .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                }
                Log.d(TAG, "Nav renderer ready")
                // The native renderer projects with the client's configured
                // physical DPI (from the phone display metrics), not the car
                // surface DPI.
                AutoMapRenderer(
                    client,
                    carContext.resources.displayMetrics.densityDpi.toDouble(),
                    DEFAULT_LAT,
                    DEFAULT_LON,
                    DEFAULT_AA_ZOOM
                )
            }
            rendererGate.publish(renderer)
        }

        // Shared settings: lane hints + navigation orientation + auto-zoom.
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { settings ->
                    laneHintsEnabled = settings.laneHintsEnabled
                    navNorthUp = settings.navNorthUp
                    autoZoomEnabled = settings.autoZoomEnabled
                    overspeedWarningDeltaKmh = settings.overspeedWarningDeltaKmh
                    // Apply the shared map style (loadStyleSheet blocks on the
                    // native DB thread — run off the main thread; deduped).
                    val style = settings.styleSheet
                    scope.launch(Dispatchers.Default) {
                        if (!styleApplier.apply(style)) {
                            Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
                        } else {
                            // DB is ready (style loaded) — (re)push the resolved
                            // day/night flag: the startup push may have been
                            // dropped while the DB was still initializing.
                            pushDark()
                        }
                    }
                    rendererGate.requestRender()
                }
                .onFailure { Log.w(TAG, "loading settings failed", it) }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                registerSurfaceCallback()
                rendererGate.resume()
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
                autoZoomController.suspend()
                rendererGate.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start. No-op while
                // the renderer is still initializing.
                rendererGate.releaseSurface()
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
                // Cancel a still-running init first, so no renderer work
                // continues after the screen is destroyed; the gate shuts
                // down a published renderer.
                rendererInitJob?.cancel()
                rendererGate.destroy()
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
        // Fresh settings on every resume: a routing anchor chosen in the
        // settings screens applies without waiting for a navigation-state
        // change (spec: auto/navigation-view — "Anchor applies on resume
        // without a maneuver change").
        reloadLiveSettings()
        observeJob = scope.launch {
            navigationViewModel.state
                .collect { state ->
                    val changed = hasStateChanged(state)
                    lastState = state
                    // Navigation ended while this screen is visible: leave
                    // immediately (design D2). The not-navigating fallback is
                    // a dead end — the lone strip BACK would stop nothing — so
                    // the screen exits itself instead of relying on the
                    // session observer, which may not be collecting yet (e.g.
                    // navigation ended during the warmup window). popToRoot is
                    // idempotent: the session pops too, and whichever runs
                    // first wins, the other is a no-op.
                    if (!state.isNavigating) {
                        screenManager.popToRoot()
                        return@collect
                    }
                    // Route polyline for the map renderer ("_route" style);
                    // re-renders when a new route (navigation start/reroute)
                    // arrives and clears when navigation stops.
                    if (state.routeLats !== lastRouteLats) {
                        lastRouteLats = state.routeLats
                        rendererGate.setRoute(state.routeLats, state.routeLons)
                    }
                    // Destination marker follows the navigation context
                    // (spec: auto-destination-details); NaN clears it.
                    rendererGate.setDestinationMarker(state.destLat, state.destLon, state.destinationName)
                    // Current street from the route's way (spec:
                    // auto/navigation-view — street from the route, not an
                    // area search): updates when the road changes. The
                    // ETA-card trip text carries it always (design D1) — a
                    // name change needs a template rebuild. The GPS-driven
                    // fallback (resolveStreetName) only runs when no road
                    // info is in state (off-route).
                    val roadText = streetNameFromState(state)
                    if (needsEtaCardRebuild(roadText != null && roadText != streetName)) {
                        streetName = roadText
                        rendererGate.requestRender()
                        invalidate()
                    }
                    if (changed) {
                        invalidate()
                        // Redraw the surface hints without waiting for a GPS tick.
                        rendererGate.requestRender()
                        // Live settings: lane hints + orientation + auto-zoom
                        // apply without a screen restart (throttled by state changes);
                        // each reload re-applies and only acts on change (see
                        // [applyLiveSettings]).
                        reloadLiveSettings()
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
                    rendererGate.setGpsMarker(
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
                    val commitRenderer = rendererGate.rendererOrNull()
                    if (commitRenderer != null) {
                        val zoom = FreeDrivingScreen.autoZoomTarget(panHandler.panning, autoZoomEnabled, fix.speedKmH, autoZoomController)
                        lastSpeedKmH = fix.speedKmH
                        val vp = commitRenderer.viewportState.value
                        val rawAngle = if (shouldRotateHeadingUp(panHandler.panning, navNorthUp, fix.bearing)) {
                            -Math.toRadians(fix.bearing)
                        } else {
                            null
                        }
                        // Heading commit deadband (design D2): the rotation is
                        // re-committed only when the smoothed heading moved beyond
                        // the deadband from the COMMITTED rotation — below it the
                        // fix commits a centre-only change served by the overrun
                        // blit instead of a full native render (spec:
                        // auto-smooth-follow — Heading commit deadband / Re-anchor
                        // below the heading deadband is served by a blit).
                        val commitAngle = resolveCommittedAngle(panHandler.panning, rawAngle, vp.angle)
                        if (shouldCommitViewport(panHandler.panning, commitAngle, vp.angle, zoom)) {
                            // Keep the CURRENT FRACTIONAL magnification when the
                            // controller has no new target (spec: auto-speed-zoom —
                            // Fractional target is committed, not rounded; change
                            // `aa-follow-framing-and-zoom-parity`): `vp.zoom` is the
                            // integer model level only, so using it rounded the
                            // committed magnification to the whole level on every fix
                            // without a zoom target and the display oscillated between
                            // the fractional and the rounded value.
                            val newZoom = zoom ?: vp.zoomFraction
                            // The integer slot keeps the viewport model level; the
                            // 5th arg is the fractional render magnification
                            // (spec: auto-speed-zoom — Smooth zoom transitions
                            // delta — same shape as pinch zoomStep).
                            // `commitAngle ?: vp.angle` keeps the committed rotation
                            // when the fix's heading change is below the deadband.
                            // `walkZoom` marks the auto-zoom commits: a step larger than
                            // the blit window is walked across rendered frames instead of
                            // landing in one frame (spec: auto-speed-zoom — Auto-zoom
                            // entry transition; design D1/D2).
                            rendererGate.setViewport(
                                vp.lat, vp.lon, newZoom.roundToInt(), commitAngle ?: vp.angle, newZoom,
                                walkZoom = zoom != null
                            )
                            // Re-engage follow WITHOUT snapping (smooth correction
                            // via the extrapolation loop, spec: auto-smooth-follow).
                            rendererGate.reengageFollow()
                            if (zoom != null) {
                                Log.d(TAG, "nav autoZoom commit speed=${pos.speedKmH} mag=$zoom")
                            }
                        }
                    }
                    resolveStreetName(pos)
                }
            }
        }

        // Resolved day/night: push the stylesheet `daylight` flag and re-render
        // on change (tunnel entry, dusk, or a dark mode preference change on
        // the car). Deduped by the applier; the native side reloads the variant
        // on its DB thread. invalidateStyle forces a full render so the
        // stale-variant overrun buffer is never blitted.
        scope.launch {
            resolvedDark.collect { dark ->
                rendererGate.setDarkPresentation(dark)
                // Stylesheet flag applies only once the client is built; a
                // pre-ready dark is re-pushed by the readiness observer via
                // pushDark() (which skips until ready).
                if (rendererGate.rendererOrNull() != null && daylightApplier.apply(dark)) {
                    rendererGate.invalidateStyle()
                }
            }
        }

        // Basemap data changes (download/update/delete while the app runs):
        // re-render without an app restart (spec: basemap-loading).
        scope.launch {
            entryPoint.basemapReloadNotifier().revision.collect { revision ->
                if (revision > 0L) {
                    rendererGate.invalidateData()
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
            rendererGate.requestRender()
            // The ETA-card trip text carries the street name always (design
            // D1) — a name change needs a template rebuild.
            if (needsEtaCardRebuild(changed)) {
                invalidate()
            }
        }
    }

    /** Stop observing state when screen is hidden. */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    /**
     * Apply the shared settings the navigation surface consumes live — lane
     * hints, orientation, auto-zoom, overspeed and the routing anchor. The
     * anchor re-frames follow mode when it changes; unchanged values are
     * no-ops (spec: auto/navigation-view — "Routing anchor applies when the
     * setting changes during navigation").
     */
    private fun applyLiveSettings(settings: AutoSettings) {
        if (settingsDiffer(
                settings,
                laneHintsEnabled,
                navNorthUp,
                autoZoomEnabled,
                overspeedWarningDeltaKmh,
                routingAnchor
            )
        ) {
            val wasAutoZoomEnabled = autoZoomEnabled
            laneHintsEnabled = settings.laneHintsEnabled
            navNorthUp = settings.navNorthUp
            autoZoomEnabled = settings.autoZoomEnabled
            overspeedWarningDeltaKmh = settings.overspeedWarningDeltaKmh
            routingAnchor = VehicleAnchorPosition.fromId(settings.routingAnchorId)
            rendererGate.setFollowAnchor(routingAnchor)
            // The host's panel edge (left in LTR, right in RTL): the
            // anchor resolves against the visible surface area.
            rendererGate.setHostPaneRtl(isHostPaneOnRight(carContext))
            rendererGate.requestRender()
            // Re-enabling auto-zoom applies the target NOW, from the last known speed,
            // instead of waiting for the next fix (spec: auto-speed-zoom — Auto-zoom
            // re-enabled after a manual zoom; design D5).
            if (!wasAutoZoomEnabled && autoZoomEnabled) {
                applyAutoZoomOnReEnable(lastSpeedKmH)
            }
        }
    }

    /**
     * Re-enable path (spec: auto-speed-zoom — Auto-zoom re-enabled after a manual zoom;
     * design D5): resolve the target from the shared rule ONCE and commit it
     * transition-eligible. The rule itself lives in `FreeDrivingScreen` so the two car
     * screens cannot drift.
     */
    private fun applyAutoZoomOnReEnable(speedKmH: Double) {
        val newZoom = FreeDrivingScreen.autoZoomOnReEnable(
            wasEnabled = false,
            enabled = true,
            speedKmH = speedKmH,
            panning = panHandler.panning,
            controller = autoZoomController
        ) ?: return
        FreeDrivingScreen.commitAutoZoom(rendererGate, newZoom, "re-enabled speed=$speedKmH")
    }

    /** (Re)load the shared settings and apply them (init + every resume). */
    private fun reloadLiveSettings() {
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { applyLiveSettings(it) }
                .onFailure { Log.w(TAG, "settings reload failed", it) }
        }
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
        // Street name ALWAYS in the host ETA card via setTripText (design D1,
        // spec: auto/navigation-view — the host positions the card, so the
        // name is never covered and is never drawn on the map surface). No
        // trip text when no name is available (spec: "No street name when
        // unnamed").
        etaCardText(streetName)?.let { builder.setTripText(CarText.create(it)) }
        return builder.build()
    }

    private fun onZoomIn() {
        val renderer = rendererGate.rendererOrNull() ?: return
        autoZoomController.suspend()
        val vp = renderer.viewportState.value
        val zoom = (vp.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "nav zoom+ $zoom")
        rendererGate.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        rendererGate.reCenter()
    }

    private fun onZoomOut() {
        val renderer = rendererGate.rendererOrNull() ?: return
        autoZoomController.suspend()
        val vp = renderer.viewportState.value
        val zoom = (vp.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        Log.d(TAG, "nav zoom- $zoom")
        rendererGate.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        rendererGate.reCenter()
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
                // Render at the CAR display's DPI — the client is built with
                // the phone metrics, which would scale the map ~1.8x too
                // zoomed on a ~236-dpi head unit. Takes effect on the next
                // render. Only safe on the main thread once the client is built
                // (renderer published); a pre-ready DPI is applied by the
                // off-main init coroutine and replayed with the surface.
                if (rendererGate.rendererOrNull() != null) {
                    runCatching { entryPoint.autoClientProvider().client().setMapDpi(surfaceDpi) }
                        .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                }
                rendererGate.onSurfaceAvailable(surface, surfaceWidth, surfaceHeight, surfaceDpi)
                // Bottom chrome (the AAOS bottom bar) is derived from the host's
                // stable area once delivered; until then the inset is 0.
                rendererGate.setHostBottomInset(hostBottomInsetPx())
                // Re-push the resolved day/night flag before the first render: the
                // startup push may have been dropped while the DB was still
                // initializing (warmup race).
                pushDark()
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Nav surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                rendererGate.setHostBottomInset(0)
                rendererGate.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visible: Rect) {
                // No longer feeds the street name — the ETA card owns it
                // (design D1). Keep the surface refresh on host-area changes.
                rendererGate.requestRender()
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                stableArea.set(newStableArea)
                // Bottom-row anchor presets clamp above the host's stable-area
                // bottom (design: anchor-per-surface-visible-area AA vertical
                // clamp) — the AAOS bottom bar is host chrome, not surface.
                rendererGate.setHostBottomInset(hostBottomInsetPx())
                rendererGate.requestRender()
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

    /**
     * Host-covered band at the surface bottom (px): the stable area's bottom
     * edge, i.e. how much of the surface the host's bottom chrome covers
     * (0 when unknown/empty or the stable area spans the full surface).
     */
    private fun hostBottomInsetPx(): Int {
        if (surfaceHeight <= 0) return 0
        val stableBottom = stableArea.takeIf { !it.isEmpty() }?.bottom ?: surfaceHeight
        return (surfaceHeight - stableBottom).coerceAtLeast(0)
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

        /**
         * Live-settings diff for the navigation surface (spec:
         * auto/navigation-view — "Routing anchor applies when the setting
         * changes during navigation"): true when any consumed value (lane
         * hints, orientation, auto-zoom, overspeed, routing anchor) differs
         * from the currently applied one. Pure seam for tests — the anchor
         * change must be detected without a navigation-state change.
         */
        internal fun settingsDiffer(
            settings: AutoSettings,
            laneHintsEnabled: Boolean,
            navNorthUp: Boolean,
            autoZoomEnabled: Boolean,
            overspeedWarningDeltaKmh: Int,
            routingAnchor: VehicleAnchorPosition
        ): Boolean =
            settings.laneHintsEnabled != laneHintsEnabled ||
                settings.navNorthUp != navNorthUp ||
                settings.autoZoomEnabled != autoZoomEnabled ||
                settings.overspeedWarningDeltaKmh != overspeedWarningDeltaKmh ||
                VehicleAnchorPosition.fromId(settings.routingAnchorId) != routingAnchor

        private const val TAG = "NavigationScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

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
         * ETA-card trip text (design D1, street-name-host-views): ALWAYS the
         * street name when available — the host positions the card, so the
         * name is never covered and never competes with surface geometry.
         * Null when no name is available (spec: "No street name when
         * unnamed"). This is what replaced the old gated `tripTextFor`
         * (which only set trip text when the map label was unsafe).
         */
        fun etaCardText(streetName: String?): String? =
            streetName?.takeIf { it.isNotBlank() }

        /**
         * Street-name change → template rebuild (design D1): unconditional —
         * the ETA card always owns the name, so every change needs a rebuild
         * (there is no map-surface path left to skip). Replaced the old
         * `if (!streetNameOnSurface()) invalidate()` gating.
         */
        fun needsEtaCardRebuild(changed: Boolean): Boolean = changed

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
         * Back behavior decision (design D3, spec: auto/navigation-view —
         * "Leave navigation at any time"): while navigating, back stops
         * navigation; once navigation is already inactive, back leaves the
         * (dead-end fallback) navigation view. Pure seam so the branching is
         * unit-testable without a live CarContext.
         */
        fun navigationBackBehavior(
            isNavigating: Boolean,
            onStopNavigation: () -> Unit,
            onLeaveNavigationView: () -> Unit
        ) {
            if (isNavigating) onStopNavigation() else onLeaveNavigationView()
        }

        /**
         * Heading-up rotation decision: rotate with the bearing unless the
         * map is panned (spec: auto/map-pan — rotation frozen while panned),
         * north-up is set, or the bearing is unknown.
         */
        fun shouldRotateHeadingUp(panning: Boolean, navNorthUp: Boolean, bearing: Double): Boolean =
            !panning && !navNorthUp && bearing >= 0.0

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
