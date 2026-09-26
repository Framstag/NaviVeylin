package com.naviveylin.auto

import android.graphics.Rect
import android.util.Log
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.CarSurfaceOwner
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.SpeedStaleness
import kotlin.math.roundToInt
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
    carContext: CarContext,
    /**
     * Resolved dark presentation (preference x host day/night signal) of the
     * session. Drives the map's stylesheet variant and the app-drawn overlay
     * palettes (compass rose); see [NavigationScreen] for the same wiring.
     */
    private val resolvedDark: StateFlow<Boolean>
) : Screen(carContext) {

    private val scope = carScreenScope("FreeDrivingScreen")
    private var streetJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()
    private val drivingModeProvider = entryPoint.autoDrivingModeProvider()

    /**
     * Session-scoped car surface owner (spec: car-host-fault-isolation — Single-owner
     * car surface): the session's host forwards the car surface and gesture events to
     * this screen while it is attached, and the host — not the screen — releases the
     * surface.
     */
    private val surfaceHost = entryPoint.autoSurfaceHost()

    private val streetNameUpdater = StreetNameUpdater()
    private val autoZoomController = AutoZoomController()

    /**
     * Host pan-mode handling (spec: auto/map-pan): disengages follow and
     * suspends auto-zoom on pan entry, re-engages follow on exit, and
     * converts pan/pinch gestures to viewport changes. Lazy so the renderer
     * is not forced before the surface is available.
     */
    private val panHandler: MapPanHandler by lazy {
        MapPanHandler({ rendererGate.rendererOrNull() }, autoZoomController) { surfaceWidth to surfaceHeight }
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    // Host-reported stable/visible areas (surface pixels; empty = unknown):
    // the compass rose and the attribution stay inside the stable area so
    // host chrome never covers them. The street-name pill anchors inside the
    // guaranteed-visible band (design D8, street-name-host-views): top edge
    // from the CURRENTLY-visible top (the real coverage — the stable area can
    // over-reserve a top band the host never draws), bottom from the stable
    // area's bottom (its reported band matches the real task bar).
    private val stableArea = Rect()
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

    /**
     * Wall-clock time of the last processed fix — staleness guard for the
     * badge (spec: gps-speed-priority — stationary reads zero). The provider
     * goes silent at standstill, so this ages until the next fix.
     */
    @Volatile
    private var lastFixTimeMs: Long = 0L

    /** Latest road speed limit (km/h) for the limit sign; NaN when undefined. */
    @Volatile
    private var maxSpeedKmH: Double = Double.NaN

    /** Auto-zoom from the shared settings (default on). */
    @Volatile
    private var autoZoomEnabled: Boolean = true

    /** Free-driving vehicle anchor from the shared settings (default center). */
    @Volatile
    private var freeDrivingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    /** Overspeed warning delta (km/h) from the shared settings (default 5). */
    @Volatile
    private var overspeedWarningDeltaKmh: Int = 5

    /**
     * Effective speed/bearing derivation (spec: auto-smooth-follow — "Fix
     * feed from follow-mode screens"): GPS value when valid, else
     * movement-derived, else the last effective value. Shared with
     * [NavigationScreen] via core [com.naviveylin.core.AutoPositionUtil].
     */
    private val fixDerivation = AutoFixDerivation()

    /**
     * (Re)load the shared settings — auto-zoom, overspeed and the
     * free-driving anchor — on init and on every re-visibility. A changed
     * anchor re-frames follow mode live (no screen restart) and, because the
     * street-name label placement is derived from the anchor at draw time,
     * re-places the label with it (spec: auto/free-driving — "Free-driving
     * anchor applies when the setting changes during a session", "Street
     * label follows the anchor"). A failed re-read keeps the previously
     * applied values and is logged; it does not disrupt driving (spec:
     * "Free-driving anchor survives a settings re-read failure").
     */
    /**
     * (Re)push the resolved dark presentation to the native stylesheet and force a
     * full render when it actually changed (spec: auto-map-layout — Compass rose
     * follows the resolved surface presentation). Skips until the renderer exists:
     * the native client may still be building off the main thread, and applying the
     * stylesheet flag must never first-touch it here.
     */
    private fun pushDark(force: Boolean = false) {
        if (rendererGate.rendererOrNull() == null) return
        val dark = resolvedDark.value
        if (!daylightApplier.needsPush(dark, force)) return
        // Publish, do not push: setStyleSheetFlag reloads the style variant on the DB
        // thread, and this runs from a host callback. The collector started in init
        // applies it on a background dispatcher, and flips the overlay palette with it.
        rendererGate.requestDaylightPush(dark, force)
    }

    private fun loadSettings() {
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { settings ->
                    val wasAutoZoomEnabled = autoZoomEnabled
                    autoZoomEnabled = settings.autoZoomEnabled
                    overspeedWarningDeltaKmh = settings.overspeedWarningDeltaKmh
                    val newAnchor = anchorDiff(freeDrivingAnchor, settings.freeDrivingAnchorId)
                    if (newAnchor != null) {
                        freeDrivingAnchor = newAnchor
                        rendererGate.setFollowAnchor(freeDrivingAnchor)
                        rendererGate.requestRender()
                        Log.d(TAG, "applied free-driving anchor $freeDrivingAnchor")
                    }
                    // Host panel edge (left in LTR, right in RTL) — visible-area anchor.
                    rendererGate.setHostPaneRtl(isHostPaneOnRight(carContext))
                    Log.d(TAG, "settings loaded: autoZoomEnabled=$autoZoomEnabled anchor=$freeDrivingAnchor")
                    // Re-enabling auto-zoom applies the target NOW, from the last known
                    // speed, instead of waiting for the next fix (spec: auto-speed-zoom —
                    // Auto-zoom re-enabled after a manual zoom; design D5).
                    if (!wasAutoZoomEnabled && autoZoomEnabled) {
                        applyAutoZoomOnReEnable(currentSpeedKmH)
                    }
                }
                .onFailure { Log.w(TAG, "loading settings failed", it) }
        }
    }

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

    /**
     * Applies the resolved presentation to the native stylesheet `daylight` flag
     * (deduped). Mirrors [NavigationScreen]: the car surface owns its own
     * renderer, so without this the free-driving map kept the daylight variant at
     * night and the rose could never reach a night treatment.
     */
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
     * Shared-state observations for this screen (spec: auto/screen-observation;
     * design D2): [CarScreenObservations] owns how long each observation lives,
     * [FreeDrivingScreenObservations] owns what is observed. Started in `onStart`,
     * stopped in `onStop`, so a background round trip can never leave a duplicate
     * observer behind.
     */
    private val observations = CarScreenObservations()
    private val screenObservations = FreeDrivingScreenObservations(
        observations = observations,
        locationProvider = locationProvider,
        basemapNotifier = entryPoint.basemapReloadNotifier(),
        resolvedDark = resolvedDark,
        onFix = ::onGpsFix,
        onDark = { pushDark() },
        onBasemapRevision = { rendererGate.invalidateData() }
    )

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        // BACK and the system back action both leave free driving (spec:
        // "Exit free driving" — back returns to the map view).
        enableBackNavigation()

        // Async renderer init (design D2): first-touch the native client
        // singleton off the main thread and build the renderer (fixed fallback
        // viewport — free driving centers on the vehicle via follow).
        rendererInitJob = scope.launch {
            // Everything that touches the native client stays on a background
            // dispatcher (spec: auto-map-renderer — Renderer initialization off the
            // car-app main thread).
            val client = withContext(Dispatchers.Default) {
                val client = entryPoint.autoClientProvider().client()
                Log.d(TAG, "Free driving renderer ready")
                client
            }
            // Constructed and published on the main thread with no suspension in
            // between, so a cancellation during the background work can never drop an
            // already-constructed renderer (spec: auto-map-renderer — "Renderer
            // constructed while the screen is being destroyed").
            rendererGate.publish(
                AutoMapRenderer(
                    client,
                    carContext.resources.displayMetrics.densityDpi.toDouble(),
                    DEFAULT_LAT,
                    DEFAULT_LON,
                    DEFAULT_AA_ZOOM
                )
            )
        }

        // Stylesheet day/night pushes (spec: car-host-fault-isolation — Host callbacks
        // answer promptly): the request is published by pushDark, and the native flag is set
        // here, off the main thread — including the one the host's surface delivery asks for.
        scope.launch {
            rendererGate.daylightPush.collect { request ->
                if (request == null) return@collect
                val applied = withContext(Dispatchers.Default) {
                    runCatching { daylightApplier.apply(request.dark, request.force) }
                        .onFailure { Log.w(TAG, "setStyleSheetFlag failed", it) }
                        .getOrDefault(false)
                }
                // A changed variant invalidates the rendered frame (the overrun buffer holds
                // the previous variant). The overlay palette flips with it, so the app-drawn
                // overlays and the map always show the same presentation.
                if (applied) {
                    rendererGate.setDarkPresentation(request.dark)
                    rendererGate.invalidateStyle()
                }
            }
        }

        // Renderer readiness: wire surface-failure recovery once the renderer
        // exists (must never first-touch the native client on the main
        // thread).
        scope.launch {
            rendererGate.renderer.collect { renderer ->
                if (renderer == null) return@collect
                // Re-push the presentation once the renderer exists: an
                // observation that fired before readiness (or before the client
                // was built) is otherwise lost for the whole screen lifetime.
                pushDark()
                // If the host delivered a surface we cannot lock (AAOS emulator
                // quirk: it locks surfaces it re-delivers after a screen
                // transition), drop it and ask the host for a fresh one.
                // Throttled inside the renderer and capped per screen start so a
                // persistently-bad surface does not invalidate the template
                // forever.
                renderer.onSurfaceFailed = {
                    // Reported from the render thread; the car-app library wants the
                    // refresh on the main thread (spec: car-host-fault-isolation —
                    // Template invalidation is main-thread only).
                    postTemplateRefresh {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                            surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
                        ) {
                            surfaceRefreshAttempts++
                            Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                            invalidate()
                        }
                    }
                }
            }
        }

        // Shared settings: auto-zoom (speed → magnification) applies live.
        loadSettings()

        // Stale-speed ticker (spec: gps-speed-priority — stationary reads
        // zero). At standstill the provider goes silent (min-distance
        // throttling), so the badge must decay to 0 once no fresh fix arrives
        // instead of pinning the last pre-stop speed forever (mirror of the
        // AANavigationController/phone-VM tickers; the screen's own volatile
        // is not covered by them). Dispatchers.Default + real clock: must not
        // feed the (virtual) test scheduler with an endless delay loop.
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(SPEED_STALE_TICK_MS)
                if (SpeedStaleness.isStale(lastFixTimeMs, System.currentTimeMillis()) &&
                    currentSpeedKmH != 0.0
                ) {
                    currentSpeedKmH = 0.0
                    Log.d(TAG, "stale fix — free-driving badge speed zeroed")
                    rendererGate.requestRender()
                }
            }
        }

        // Surface overlays: compass rose (rotates with the map) + current
        // speed readout below it (badge shows the current speed — free driving
        // has no speed-limit data) + the street-name label. No navigation hint
        // panel — free driving has no target. The street pill anchors to the
        // edge of the guaranteed-visible band opposite the vehicle anchor row
        // (row rule, design D2/D8): the band is the surface minus the host
        // chrome insets from the stable area (AAOS status/task bars), so the
        // pill is never hidden under host chrome; bottom-row anchor → top of
        // the band, top/middle rows → bottom.
        rendererGate.setOverlayDrawer({ canvas, w, h ->
            val density = (surfaceDpi / 160.0).toFloat()
            val (topInset, bottomInset) = hostInsetsPx()
            SurfaceIndicators.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                stableBounds = stableArea,
                density = density,
                angleRadians = headingRadians ?: 0.0,
                // Rose palette of the presentation actually applied to this
                // surface's map variant — same frame, so the rose and the map can
                // never disagree (spec: auto-map-layout — Compass rose follows the
                // resolved surface presentation).
                darkPresentation = rendererGate.currentDarkPresentation(),
                currentKmH = currentSpeedKmH,
                maxKmH = maxSpeedKmH,
                overLimitDeltaKmh = overspeedWarningDeltaKmh,
                drawSpeedLimitSign = true
            )
            StreetNameLabel.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                name = streetName.orEmpty(),
                placement = StreetNameLabel.placementFor(freeDrivingAnchor),
                topInset = topInset,
                bottomInset = bottomInset
            )
            SurfaceAttribution.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                usableBounds = stableArea
            )
        })

        // Surface vote for the shared free-driving flag (design: retain-on-death).
        // Published on entry; cleared on explicit exit ([exitFreeDriving]) — the
        // screen's onDestroy is deliberately silent so a session destroy
        // mid-free-drive keeps the flag (and the ongoing notification) alive.
        drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                surfaceHost.attach(surfaceOwner)
                rendererGate.resume()
                // Re-read the shared settings on re-visibility (spec:
                // auto/free-driving — "Free-driving anchor applies when the
                // setting changes during a session"): a new anchor chosen in the
                // settings screens re-frames follow mode without a restart.
                loadSettings()
                screenObservations.start()
            }
            override fun onStop(owner: LifecycleOwner) {
                // Every observation ends with the started period (spec:
                // auto/screen-observation — One instance of each observation per
                // started period). The speed-staleness ticker is not one of them: it
                // must keep running while stopped (spec — Work that must continue
                // while stopped is not scoped to the started period).
                observations.stop()
                rendererGate.pause()
                // Stop drawing on the session's surface (spec: auto-map-renderer — A
                // stopped renderer holds no surface or frame buffer): detach the surface
                // reference (re-acquired through the session on start) and drop the
                // overrun frame buffer. Detach only: the session owns the surface's
                // lifetime, so a screen stopped underneath a pushed screen never releases
                // the queue the incoming screen draws through.
                rendererGate.detachSurface()
                surfaceHost.detach(surfaceOwner)
                // NOTE: no unregisterSurfaceCallback() here. The car-app
                // library starts the new screen BEFORE stopping the old one
                // (ScreenManager.pushInternal), so an onStop unregister would
                // null the surface callback the new screen just registered —
                // the host then re-delivers a second surface and locks both
                // (IAE on every lockCanvas). Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                // A destroy without a stop must not leave an observation running.
                observations.stop()
                surfaceHost.detach(surfaceOwner)
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

    /**
     * Re-enable path (spec: auto-speed-zoom — Auto-zoom re-enabled after a manual zoom;
     * design D5): resolve the target from the shared rule ONCE and commit it
     * transition-eligible, so a step larger than the blit window is walked across rendered
     * frames instead of landing in one frame.
     */
    private fun applyAutoZoomOnReEnable(speedKmH: Double) {
        val newZoom = autoZoomOnReEnable(
            wasEnabled = false,
            enabled = true,
            speedKmH = speedKmH,
            panning = panHandler.panning,
            controller = autoZoomController
        ) ?: return
        applyZoomCommit(newZoom, "re-enabled speed=$speedKmH")
    }

    /** Commit an auto-zoom magnification, transition-eligible (design D1/D2). */
    private fun applyZoomCommit(newZoom: Double, reason: String) {
        commitAutoZoom(rendererGate, newZoom, reason)
    }

    /**
     * Apply a GPS fix: marker, heading-up rotation, auto-zoom, street name.
     * GPX replay tracks usually lack a GPS bearing — fall back to the
     * movement direction between fixes so heading-up still engages.
     */
    private fun onGpsFix(pos: AutoPosition) {
        val nowMs = System.currentTimeMillis()
        lastFixTimeMs = nowMs
        val (speed, bearing) = fixDerivation.derive(pos, nowMs)
        Log.d(
            TAG,
            "GPS fix lat=${pos.lat} lon=${pos.lon} bearing=${pos.bearing} " +
                "effBearing=$bearing speedKmH=${pos.speedKmH} effSpeed=$speed"
        )
        currentSpeedKmH = speed
        rendererGate.setGpsMarker(
            pos.lat, pos.lon, bearing, pos.accuracy,
            speedKmH = speed,
            timeMs = nowMs
        )

        // Heading-up always (spec: "Heading-up orientation"), independent of
        // the shared navNorthUp setting.
        val rawAngle = headingAngleRadians(bearing)
        if (rawAngle != null) {
            headingRadians = rawAngle
        }
        // Speed-driven auto-zoom (shared setting; speed → magnification).
        // The feed is gated on !panning (design D2) so the controller state
        // stays frozen during a pan; the commit is gated on !panning too
        // (design D1) so a band-crossing zoom can never re-engage follow
        // mid-pan.
        val newZoom = autoZoomTarget(panHandler.panning, autoZoomEnabled, speed, autoZoomController)

        // Heading commit deadband (design D2): the rotation is re-committed only
        // when the smoothed heading moved beyond the deadband from the COMMITTED
        // rotation — below it the fix commits a centre-only change served by the
        // overrun blit instead of a full native render (~1 forced render per fix
        // for a ~0.9°/s signal before the change; spec: auto-smooth-follow —
        // Heading commit deadband / Re-anchor below the heading deadband is
        // served by a blit).
        val committedAngle = rendererGate.rendererOrNull()?.viewportState?.value?.angle
        val commitAngle = resolveCommittedAngle(panHandler.panning, rawAngle, committedAngle)

        if (shouldCommitViewport(panHandler.panning, commitAngle, committedAngle, newZoom)) {
            val commitRenderer = rendererGate.rendererOrNull() ?: return
            // setViewport disengages follow mode, so re-engage + re-center
            // right after (same pattern as NavigationScreen). While panned the
            // commit is gated off — heading-up is always computed here, so
            // without the gate every fix would re-engage follow and yank the
            // map back (spec: auto/map-pan — follow suspended while panned).
            val vp = commitRenderer.viewportState.value
            // Keep the CURRENT FRACTIONAL magnification when the controller has no new
            // target (spec: auto-speed-zoom — Fractional target is committed, not
            // rounded; change `aa-follow-framing-and-zoom-parity`). `vp.zoom` is the
            // integer model level only: using it here rounded the committed
            // magnification to the whole level on every fix without a zoom target, so
            // the display oscillated between (e.g.) 13.08 and 13.00 once per fix —
            // the map breathing in and out radially about the anchor.
            val zoom = newZoom ?: vp.zoomFraction
            // The integer slot keeps the viewport model level; the 5th arg is
            // the fractional render magnification (spec: auto-speed-zoom —
            // Smooth zoom transitions delta — same shape as pinch zoomStep).
            // `commitAngle ?: vp.angle` keeps the committed rotation when the
            // fix's heading change is below the deadband.
            rendererGate.setViewport(vp.lat, vp.lon, zoom.roundToInt(), commitAngle ?: vp.angle, zoom, walkZoom = newZoom != null)
            // Re-engage follow WITHOUT snapping: the extrapolation loop eases
            // the display to the fix (smooth correction, spec:
            // auto-smooth-follow). reCenter() would snap the map back per fix.
            rendererGate.reengageFollow()
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
                    entryPoint.autoClientProvider().client().getRoadAt(pos.lat, pos.lon, pos.bearing)
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
                rendererGate.requestRender()
            }
        }
    }

    private fun exitFreeDriving() {
        Log.d(TAG, "Exit free driving")
        drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        guardedHostCall("pop (exit free driving)") { screenManager.pop() }
    }

    private fun onZoomIn() {
        val renderer = rendererGate.rendererOrNull() ?: return
        autoZoomController.suspend()
        val vp = renderer.viewportState.value
        val zoom = (vp.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "free driving zoom+ $zoom (auto-zoom suspended)")
        rendererGate.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        rendererGate.reCenter()
    }

    private fun onZoomOut() {
        val renderer = rendererGate.rendererOrNull() ?: return
        autoZoomController.suspend()
        val vp = renderer.viewportState.value
        val zoom = (vp.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        Log.d(TAG, "free driving zoom- $zoom (auto-zoom suspended)")
        rendererGate.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        rendererGate.reCenter()
    }

    /**
     * Car-surface owner for this screen (spec: car-host-fault-isolation — Single-owner
     * car surface; design D1). Attached to the session's [surfaceHost] on start and
     * detached on stop; the host hands over a retained surface immediately on attach.
     */
    private val surfaceOwner: CarSurfaceOwner = object : CarSurfaceOwner {
        override fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) {
            surfaceWidth = width
            surfaceHeight = height
            surfaceDpi = dpi.takeIf { it > 0.0 } ?: DEFAULT_DPI
            Log.d(TAG, "Free driving surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
            DiagnosticsLog.log(
                "FREEDRIVE",
                "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
            )
            // The delivered DPI reaches the renderer through the gate — a host callback
            // must not resolve the client (spec: car-host-fault-isolation — Host callbacks
            // answer promptly), and every render request carries the value.
            rendererGate.onSurfaceAvailable(surface, surfaceWidth, surfaceHeight, surfaceDpi)
            // Host chrome (the AAOS status/task bars) is derived from the
            // host's stable area once delivered; until then the insets are 0.
            rendererGate.setHostTopInset(hostInsetsPx().first)
            rendererGate.setHostBottomInset(hostInsetsPx().second)
            }

            override fun onCarSurfaceDestroyed() {
                Log.d(TAG, "Free driving surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                visibleArea.setEmpty()
                rendererGate.setHostTopInset(0)
                rendererGate.setHostBottomInset(0)
                rendererGate.onSurfaceDestroyed()
            }

            override fun onCarVisibleAreaChanged(visible: Rect) {
                // The pill's TOP edge anchors to the currently-visible top (the
                // real coverage) rather than the stable top — some hosts
                // over-reserve a top band in the stable area that is not
                // actually drawn, which pushed the label down (design D8
                // revision, street-name-host-views). Bottom stays stable-based.
                visibleArea.set(visible)
                val (topInset, _) = hostInsetsPx()
                Log.d(TAG, "visible area $visible -> pillTopInset=$topInset")
                rendererGate.requestRender()
            }

            override fun onCarStableAreaChanged(stable: Rect) {
                stableArea.set(stable)
                val (topInset, bottomInset) = hostInsetsPx()
                Log.d(TAG, "stable area $stable -> topInset=$topInset bottomInset=$bottomInset")
                // The street pill anchors inside the guaranteed-visible band
                // (design D8) and bottom-row follow anchors clamp above the
                // host's stable-area bottom (design: anchor-per-surface-visible-area
                // AA vertical clamp) — the AAOS bars are host chrome, not surface.
                rendererGate.setHostTopInset(topInset)
                rendererGate.setHostBottomInset(bottomInset)
                rendererGate.requestRender()
            }

            // Pan gestures (spec: auto/map-pan): the host forwards them only
            // while pan mode is active; the handler converts them to viewport
            // changes and gates on its own panning flag.
            override fun onCarScroll(distanceX: Float, distanceY: Float) {
                panHandler.onScroll(distanceX, distanceY)
            }

            override fun onCarScale(focusX: Float, focusY: Float, scaleFactor: Float) {
                panHandler.onScale(focusX, focusY, scaleFactor)
            }
    }

    /**
     * Host-covered bands at the surface top/bottom (px) that the pill anchors
     * inside (design D8): the TOP inset is the currently-visible top (the real
     * coverage; fallback: the stable-area top, then 0) — the stable area can
     * over-reserve a top band that the host never actually draws, which pushed
     * the label too far down; the BOTTOM inset stays from the stable area (its
     * reported bottom matches the real task-bar band). (0, 0) when unknown.
     */
    private fun hostInsetsPx(): Pair<Int, Int> {
        if (surfaceHeight <= 0) return 0 to 0
        return HostInsets.topInset(visibleArea, stableArea) to
            HostInsets.fromStableArea(surfaceHeight, stableArea).second
    }

    companion object {
        /**
         * Commit an auto-zoom magnification immediately and transition-eligible (spec:
         * auto-speed-zoom — Auto-zoom entry transition / Auto-zoom re-enabled after a manual
         * zoom; design D1/D2, D5): `setViewport` disengages follow mode, so follow is re-engaged
         * right after without snapping (the displayed position is the follow target — the same
         * pattern as the fix path). Returns false when the renderer is not ready yet. Shared by
         * both car screens, so their re-enable/entry commits cannot drift.
         */
        internal fun commitAutoZoom(
            rendererGate: RendererGate,
            magnification: Double,
            reason: String
        ): Boolean {
            val ready = rendererGate.rendererOrNull() ?: return false
            val vp = ready.viewportState.value
            rendererGate.setViewport(
                vp.lat, vp.lon, magnification.roundToInt(), vp.angle, magnification, walkZoom = true
            )
            rendererGate.reengageFollow()
            Log.d(TAG, "autoZoom commit ($reason) mag=$magnification")
            return true
        }

        /**
         * Speed-driven auto-zoom target: never while panned (spec:
         * auto/map-pan — auto-zoom suspended while panned), otherwise whenever auto-zoom is
         * enabled. THE single rule both car screens use ([NavigationScreen] calls it) — gating
         * the feed (not just the commit) freezes the controller state during the pan, so a
         * band-crossing re-engage mid-pan cannot advance its stability state and commit a zoom
         * the driver did not ask for right after pan exit (design D2).
         *
         * An UNKNOWN speed is passed through, not rejected: [AutoZoomController] resolves it to
         * its seeded 20 km/h default, so the first position estimate already yields a
         * "reasonable initial zoom" (spec: auto-speed-zoom — Speed unknown; parity with the
         * phone's `MapCanvasViewModel.lastValidSpeedKmH` seed).
         */
        fun autoZoomTarget(
            panning: Boolean,
            autoZoomEnabled: Boolean,
            speedKmH: Double,
            controller: AutoZoomController
        ): Double? =
            if (!panning && autoZoomEnabled) controller.onSpeed(speedKmH) else null

        /**
         * Re-enable decision (spec: auto-speed-zoom — Auto-zoom re-enabled after a manual
         * zoom; design D5): turning auto-zoom back on applies the target immediately from
         * the LAST KNOWN speed instead of waiting for the next fix, so the transition
         * starts without a delay. `null` only when the setting did not flip on. A speed that is
         * not known yet is no longer a no-op: the controller's seeded 20 km/h default resolves it
         * to a target (spec: auto-speed-zoom — Speed unknown / Auto-zoom re-enabled after a manual
         * zoom), so the transition begins without waiting for the first fix. Shared by both car
         * screens (`NavigationScreen` calls it too), so the two cannot drift.
         */
        fun autoZoomOnReEnable(
            wasEnabled: Boolean,
            enabled: Boolean,
            speedKmH: Double,
            panning: Boolean,
            controller: AutoZoomController
        ): Double? =
            if (!wasEnabled && enabled) {
                autoZoomTarget(panning, enabled, speedKmH, controller)
            } else {
                null
            }

        /**
         * Anchor change decision for the free-driving settings reload (spec:
         * auto/free-driving — "Free-driving anchor applies when the setting
         * changes during a session"): the resolved anchor when it differs
         * from the currently applied one, `null` when unchanged or
         * unresolvable. A `null` re-frame decision is the same no-op as a
         * failed re-read — only the success path mutates state (spec:
         * "Free-driving anchor survives a settings re-read failure").
         */
        internal fun anchorDiff(
            current: VehicleAnchorPosition,
            newAnchorId: String
        ): VehicleAnchorPosition? {
            val resolved = VehicleAnchorPosition.fromId(newAnchorId)
            return resolved.takeIf { it != current }
        }

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

        /** Stale-speed ticker cadence (1 Hz, same as the phone/controller). */
        private const val SPEED_STALE_TICK_MS = 1_000L

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
