package com.naviveylin.navigation

import android.content.Context
import android.util.Log
import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.NavigationController
import com.framstag.libosmscout.client.NavigationListener
import com.framstag.libosmscout.client.NavigationPosition
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.RoutingProfile
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.LocationGrant
import com.naviveylin.core.NavigationState
import com.naviveylin.core.RouteCalculation
import com.naviveylin.core.SpeedStaleness
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationLease
import com.naviveylin.location.LocationService
import com.naviveylin.location.SpeedSpikeFilter
import com.naviveylin.ui.route.routeLengthMeters
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The process-scoped navigation engine: exactly one per app process, owning the
 * single native [NavigationController] and the navigation session every surface
 * renders (spec: `navigation-engine` — Exactly one navigation engine per
 * process, One navigation state shared by all surfaces).
 *
 * It replaced the two former implementations (the phone's Activity-scoped
 * `NavigationViewModel` and the car's car-only navigation controller), which each
 * drove their own controller and mirrored their state into a shared broker:
 * exactly one of them ever owned a session, and which one was decided by
 * registration order (see `TODO.md` §46 for the defect history).
 *
 * - **State**: one [state] flow plus the [positionFlow] the map surfaces follow on.
 * - **Acquisition**: [start] takes a route a surface calculated (panel with
 *   vehicle profile/alternatives), [acquire] calculates one itself — the car-only
 *   path — and a confirmed reroute re-acquires with the profile and destination the
 *   session was started with, so no surface is needed (design D2).
 * - **Policy**: one merged reroute policy (the phone's numbers, design D3):
 *   `MAX_REROUTE_ACCURACY` 100 m, `TUNNEL_REROUTE_GUARD_MS` 30 s, the
 *   [RerouteConfirmationGate] confirmation/cooldown and the 2000 ms road-info
 *   throttle apply to every surface.
 * - **View state stays on the surface**: follow mode, free driving, viewport,
 *   zoom, rotation, anchor presets and overlay layout are not touched here
 *   (spec: `navigation-engine` — Per-surface view state never moves into the
 *   engine).
 *
 * Threading (`guidelines/Design.md` §4): one process-lifetime
 * `SupervisorJob + Dispatchers.Main` scope, never cancelled — the process is the
 * owner (design D6). Native calls run on the injected compute dispatcher
 * ([EngineDispatchers], `Dispatchers.Default` in production); state writes and
 * the flows stay main-thread confined, so surface collectors are main-confined.
 * The engine holds a location lease only while navigating and never calls
 * `startLocationUpdates()` itself (task 2.4).
 *
 * Timing and threading are seams, not literals: timing decisions read the injected
 * [EngineTimeSource] and off-main work runs on the injected [EngineDispatchers], so a
 * test moves time and schedules that work instead of waiting for it (spec:
 * `navigation-engine` — Engine lifecycle and threading).
 */
@Singleton
class NavigationEngine @Inject constructor(
    // The engine is constructed eagerly (the notification controller and the car
    // warmup resolve it at app start), so the OSMScoutClient is injected lazily and
    // built on first navigation — the engine does no work in init beyond its scope
    // and state (design D6).
    private val client: Lazy<OSMScoutClient>,
    private val locationService: LocationService,
    @param:ApplicationContext private val context: Context,
    private val timeSource: EngineTimeSource,
    private val dispatchers: EngineDispatchers
) : com.naviveylin.core.NavigationViewModel {

    /**
     * Process-lifetime scope (spec: `navigation-engine` — Engine lifecycle and
     * threading). The fault handler is part of the scope, so every child — the stale
     * speed ticker below, route acquisition, the location feed, road lookups — is
     * covered, present and future: an uncaught fault is confined to its own piece of
     * work instead of reaching the main thread's uncaught-exception handler, which
     * kills the process (and with it a live car session's templates host).
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + engineFaultHandler { message -> publishEngineFault(message) }
    )

    /**
     * The engine's scope, exposed to the fault-isolation test suite, which fails a
     * coroutine on it and asserts the fault is confined. Not an API for surfaces.
     */
    internal val engineScope: CoroutineScope get() = scope

    /**
     * Confine a fault raised by one of the engine's own coroutines (spec:
     * `navigation-engine` — Engine coroutine fault is confined; design D3): an active
     * attempt ends through the existing stop path — an engine that faults must not keep
     * presenting guidance from a possibly inconsistent state — and the fault is raised
     * as an engine-wide error, so every surface presenting navigation shows it. The
     * state write is dispatched to the main dispatcher that owns navigation-state
     * publication (guidelines/Design.md §4), because the handler can run on
     * `Dispatchers.Default` or `IO`. Never called with a state write of its own.
     */
    private fun publishEngineFault(message: String) {
        scope.launch(Dispatchers.Main) {
            Log.e(TAG, "confined engine fault: $message")
            if (_state.value.isNavigating) {
                stopNavigation()
            }
            _state.update { it.copy(
                errorMessage = message,
                errorOrigin = SurfaceOrigin.ENGINE
            ) }
        }
    }

    private val _state = MutableStateFlow(NavigationState())
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _positionFlow = MutableStateFlow<NavigationPosition?>(null)
    override val positionFlow: StateFlow<NavigationPosition?> = _positionFlow.asStateFlow()

    /**
     * The last route the **engine** acquired for itself ([acquire] /
     * [navigateTo] / a confirmed reroute). A surface that displays a route view
     * of its own (the phone route panel) adopts it so its view reflects the
     * session's route (spec: `navigation-controller` — Reroute handling, surface
     * route views). Null while no engine-acquired route is active.
     */
    private val _acquiredRoute = MutableStateFlow<RouteEntry?>(null)
    val acquiredRoute: StateFlow<RouteEntry?> = _acquiredRoute.asStateFlow()

    private var nativeController: NavigationController? = null

    /**
     * Location lease held while a navigation session is active
     * (spec: `navigation-engine` — No location retention while idle). Role name
     * [LocationConsumers.NAV_ENGINE]: one engine feeds navigation regardless of
     * which surface displays it.
     */
    private var navLease: LocationLease? = null

    /** Collector that forwards leased location fixes into the native controller. */
    private var locationJob: Job? = null

    /** Timestamp of the last fix forwarded to the native controller (feed dedupe). */
    private var lastFedFixTime = 0L

    // Road info lookup throttle
    private var lastRoadInfoTime = 0L
    private var lastRoadInfoLat = Double.NaN
    private var lastRoadInfoLon = Double.NaN

    // Last GPS accuracy from the location feed; extra guard with the tunnel guard
    private var lastGpsAccuracy: Double = -1.0

    // Last fix timestamp seen by the feed (staleness guard for the displayed
    // speed; spec: gps-speed-priority).
    private var lastFixTime = 0L

    // Last plausible speed, spike-filtered with stale-value decay. Shared pure
    // logic — see SpeedSpikeFilter (spec: speed-spike-filtering).
    private val speedSpikeFilter = SpeedSpikeFilter(maxPlausibleSpeedKmH = MAX_PLAUSIBLE_SPEED_KMH)

    // Reroute confirmation gate: fast first trigger + post-reroute cooldown.
    // Native controller fires every ~5s while off-route (RouteStateAgent.cpp:84).
    private val rerouteGate = RerouteConfirmationGate(
        minConfirmCount = MIN_REROUTE_CONFIRM_COUNT,
        minOffRouteDurationMs = MIN_OFF_ROUTE_DURATION_MS,
        confirmWindowMs = REROUTE_CONFIRM_WINDOW_MS,
        cooldownMs = REROUTE_COOLDOWN_MS,
        fastPathDistanceM = FAST_PATH_DISTANCE_M
    )

    // Ignore reroute requests shortly after tunnel/NoGpsSignal to avoid GPS noise
    // false positives.
    private var lastTunnelOrNoSignalTime = 0L
    private var lastOnRouteTime = 0L

    init {
        // Stale-speed ticker (spec: gps-speed-priority — stationary reads zero).
        // At standstill the provider goes silent (min-distance throttling), so the
        // last native speed would stay pinned forever; once no fresh fix arrives
        // beyond the staleness window the displayed speed decays to 0.
        // TICKER_DISPATCHER (a real dispatcher, never the test scheduler) + the
        // injected time source: the endless delay loop must not feed the (virtual)
        // test scheduler, and its staleness decision is moved by the test clock.
        scope.launch(TICKER_DISPATCHER) { runStaleSpeedTicker() }
    }

    /**
     * The stale-speed ticker loop: one [tickStaleness] per [intervalMs] for the process lifetime.
     *
     * In production it runs on [TICKER_DISPATCHER] (see the rationale at the launch site). It is
     * `internal` so a test can run the same loop on its own scheduler, advance virtual time by one
     * interval and cancel it — the wiring is then proven, not just the tick body (spec:
     * `navigation-engine` — The staleness tick reads the injected time source).
     */
    internal suspend fun runStaleSpeedTicker(intervalMs: Long = SPEED_STALE_TICK_MS) {
        while (true) {
            delay(intervalMs)
            tickStaleness()
        }
    }

    /**
     * One stale-speed tick: while navigating, a fix older than the staleness window decays the
     * displayed speed to zero (spec: gps-speed-priority — stationary reads zero). The guard reads
     * the state once; the publication goes through `update`, so a publisher that wrote in between
     * keeps its field (spec: `navigation-engine` — A background writer cannot revert a concurrent
     * publication), and the timing decision reads the injected time source (spec: `navigation-engine`
     * — Engine lifecycle and threading).
     */
    internal fun tickStaleness() {
        val current = _state.value
        if (!current.isNavigating) return
        if (!SpeedStaleness.isStale(lastFixTime, timeSource.nowMillis())) return
        _state.update { it.copy(currentSpeedKmH = 0.0) }
    }

    // ---------------------------------------------------------------------
    // Acquisition
    // ---------------------------------------------------------------------

    /**
     * Token of the newest route calculation request. It is published in
     * [com.naviveylin.core.RouteCalculation] and increases with every request, so a
     * callback of a calculation that a newer request superseded can be told apart
     * from the live one (spec: `route-calculation-feedback` — A superseded calculation
     * never clears the live calculation's state).
     */
    private var calculationToken = 0L

    /** Progress values published for the live calculation (measurement, see [endCalculation]). */
    private var calculationProgressCount = 0

    /** Wall clock at which the live calculation started (measurement, see [endCalculation]). */
    private var calculationStartedAtMillis = 0L

    /** Coordinate-free identity of what asked for the live calculation (measurement). */
    private var calculationTrigger = "acquisition"

    /**
     * Start navigation on a route a surface acquired (phone route panel with its
     * vehicle profile and alternatives, spec: `navigation-controller` — Start
     * navigation from route summary dialog). The profile is retained in the state
     * so a reroute re-acquires with it (spec: `navigation-engine` — Route
     * acquisition independent of a surface UI).
     */
    fun start(routeEntry: RouteEntry, vehicle: Vehicle) {
        startInternal(routeEntry, vehicle, keepRerouteCooldown = false)
    }

    /**
     * Shared start path. [keepRerouteCooldown] is true for the engine's own
     * reroute re-acquisition: the confirmation gate has already closed the episode
     * and started its cooldown, so resetting the gate here would let the very next
     * off-route report cascade into another reroute
     * (spec: `reroute-trigger` — cascade protection).
     *
     * The route's geometry is optional: the bridge hands over platform types, so a route whose
     * polyline is absent still starts navigation here — the destination falls back to the retained
     * one, the total distance is 0 (what `computeRouteDistance` answers for fewer than two points),
     * and the shared state publishes no geometry, which every consumer already accepts
     * (spec: `navigation-engine` — Acquisition without usable polyline geometry). With usable
     * geometry the total is the route's own length, never a second app-side sum of it
     * (spec: `osmscout-jni` — One route length for a calculated route).
     */
    private fun startInternal(routeEntry: RouteEntry, vehicle: Vehicle, keepRerouteCooldown: Boolean) {
        val handle = routeEntry.routeHandle
        if (handle == 0L) {
            Log.e(TAG, "start: routeHandle is 0, cannot start")
            return
        }

        acquireNavLease()
        startLocationFeed()

        // Stop any existing native controller before creating a new one. Without
        // this the old controller keeps emitting callbacks in parallel, causing
        // marker jumps, double reroutes, and corrupted navigation state.
        nativeController?.stop()
        nativeController = null

        resetGuards(keepRerouteCooldown = keepRerouteCooldown)
        // Fresh navigation must not immediately hit the stale-speed zero (no fix
        // of this session has arrived yet).
        lastFixTime = timeSource.nowMillis()

        val startLats = routeEntry.latitudes
        val startLons = routeEntry.longitudes
        val totalDistance = routeTotalDistanceMeters(routeEntry, startLats, startLons)
        // Destination identity belongs to the session, whoever acquired the route:
        // the route's last point is the destination, and a name recorded by a
        // deep-link request is kept. A self-sufficient reroute then re-acquires to
        // the retained destination without asking a surface; a geometry-less route keeps the
        // retained destination instead of failing
        // (spec: `navigation-engine` — Destination and vehicle are part of the
        // state; Route acquisition independent of a surface UI).
        val routeEndLat = startLats?.lastOrNull()
        val routeEndLon = startLons?.lastOrNull()
        _state.update { it.copy(
            isNavigating = true,
            currentStepIndex = 0,
            // Clear the previous route's steps (reroute restart): the old route's
            // last maneuver must not render over the new route until the engine
            // emits its first instruction list.
            instructions = emptyList(),
            nextInstruction = null,
            totalDistance = totalDistance,
            // Progress starts at 0%: remaining distance equals the total until the
            // first arrival estimate arrives.
            remainingDistance = totalDistance,
            navigationStartTimeMillis = timeSource.nowMillis(),
            // Route geometry for shared-state consumers (car parity and the
            // phone's route fit).
            routeLats = routeEntry.latitudes,
            routeLons = routeEntry.longitudes,
            vehicle = vehicle,
            destLat = routeEndLat ?: _state.value.destLat,
            destLon = routeEndLon ?: _state.value.destLon,
            // Navigation starting ends any calculation: the wait is over, whether this
            // route was calculated here or handed to the engine by a surface.
            calculation = null
        ) }

        scope.launch(Dispatchers.Main) {
            try {
                nativeController = client.get().startNavigationWithVehicle(
                    handle, vehicle, createListener()
                )
                Log.d(TAG, "start: started vehicle=$vehicle")
            } catch (e: Throwable) {
                // Throwable, not Exception: a native failure can be an Error
                // (UnsatisfiedLinkError, ExceptionInInitializerError, a class-init
                // failure), which an `Exception` catch would let escape (spec:
                // `navigation-engine` — Native failure is confined, not fatal).
                Log.e(TAG, "start failed", e)
                stopNavigation()
                reportError("Route calculation failed. Try again.", SurfaceOrigin.ENGINE)
            }
        }
    }

    /**
     * Acquire a route to [destLat]/[destLon] with [vehicle] and start navigation —
     * the surface-less path (car deep link, AAOS standalone, a reroute, and a phone
     * "navigate to" without a panel). The start position is the current navigation
     * position or the leased location service fix (spec: `auto-cross-device-sync` —
     * Car-only navigation start, GPS position available).
     */
    fun acquire(destLat: Double, destLon: Double, vehicle: Vehicle) {
        if (!LocationGrant.hasPrecise(context)) {
            _state.update { it.copy(
                errorMessage = context.getString(
                    com.naviveylin.core.R.string.location_precise_required_navigation
                ),
                errorOrigin = SurfaceOrigin.ENGINE
            ) }
            return
        }

        // Lease GPS for the attempt: the start position has to come from a fix, and
        // a deep link can arrive with no map surface visible (spec:
        // `location-updates-lease`). Released again if the attempt fails.
        acquireNavLease()

        val currentPos = _state.value.position
        val startLat: Double
        val startLon: Double
        if (currentPos != null && !currentPos.lat.isNaN()) {
            startLat = currentPos.lat
            startLon = currentPos.lon
        } else {
            val fix = locationService.location.value
            if (fix == null) {
                Log.e(TAG, "acquire: no GPS position available")
                releaseNavLease()
                _state.update { it.copy(
                    errorMessage = "GPS signal required. Please wait for GPS fix.",
                    errorOrigin = SurfaceOrigin.ENGINE
                ) }
                return
            }
            startLat = fix.lat
            startLon = fix.lon
        }

        _state.update { it.copy(errorMessage = null, errorOrigin = null) }
        acquire(startLat, startLon, destLat, destLon, vehicle)
    }

    /**
     * Acquire a route from an explicitly known [startLat]/[startLon] and start
     * navigation. The public [acquire] resolves its start from the active position
     * or the leased GPS fix and delegates here.
     */
    internal fun acquire(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        vehicle: Vehicle
    ) {
        calculateAndStart(startLat, startLon, destLat, destLon, vehicle, fromReroute = false)
    }

    /** Calculate a route with [vehicle] and start navigation on it. */
    private fun calculateAndStart(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        vehicle: Vehicle,
        fromReroute: Boolean
    ) {
        // Lease ownership belongs to the attempt that took it: a surface-less acquisition
        // leases GPS for its start position (`navigateTo`), while a reroute reuses the lease
        // the running navigation holds (`startInternal`) — releasing the latter would starve
        // live guidance (spec: `navigation-engine` — A failed route attempt releases only the
        // lease it took). Read here, on the thread every entry point runs on, so the decision
        // is taken before either failure handler can observe a changed navigation state.
        val ownsLease = !_state.value.isNavigating
        val token = beginCalculation(destLat, destLon, if (fromReroute) "reroute" else "acquisition")
        scope.launch(dispatchers.compute) {
            try {
                client.get().calculateRouteWithProfile(
                    startLat, startLon, destLat, destLon, RoutingProfile(vehicle),
                    object : RouteCallback {
                        override fun onProgress(percent: Int) {
                            // Called from the routing thread (rate-limited in native,
                            // see JavaRoutingProgress): the state write is marshalled to
                            // the dispatcher that owns navigation-state publication
                            // (guidelines/Design.md §4).
                            scope.launch(Dispatchers.Main) {
                                publishCalculationProgress(token, percent)
                            }
                        }

                        override fun onSuccess(route: RouteEntry) {
                            scope.launch(Dispatchers.Main) {
                                // A superseded calculation must not start navigation: its
                                // route handle is already invalid (native: a handle is valid
                                // only until the next calculation) and the driver asked for
                                // the newer destination last (spec: `route-calculation-feedback`
                                // — A superseded calculation never clears the live
                                // calculation's state).
                                if (!endCalculation(token, CalculationOutcome.OK)) {
                                    Log.d(TAG, "acquire: superseded route result ignored")
                                    return@launch
                                }
                                Log.d(TAG, "acquire: route calculated, starting navigation")
                                _state.update { it.copy(errorMessage = null, errorOrigin = null) }
                                _acquiredRoute.value = route
                                startInternal(route, vehicle, keepRerouteCooldown = fromReroute)
                            }
                        }

                        override fun onError(message: String) {
                            scope.launch(Dispatchers.Main) {
                                Log.e(TAG, "acquire: route calculation failed: $message")
                                if (!endCalculation(token, CalculationOutcome.ERROR)) return@launch
                                if (ownsLease) releaseNavLease()
                                _state.update { it.copy(
                                    errorMessage = (message ?: "").ifBlank {
                                        "Route calculation failed. Try again."
                                    },
                                    errorOrigin = SurfaceOrigin.ENGINE
                                ) }
                            }
                        }

                        override fun onCancel() {
                            scope.launch(Dispatchers.Main) {
                                // A cancel this engine asked for has already cleared the
                                // state; this path covers a cancellation by a newer native
                                // request. Either way only the live token clears state.
                                endCalculation(token, CalculationOutcome.CANCELLED)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                // Confines a native throwable that is not an Exception as well (spec:
                // `navigation-engine` — Native failure is confined, not fatal).
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "acquire failed", e)
                    if (!endCalculation(token, CalculationOutcome.ERROR)) return@withContext
                    if (ownsLease) releaseNavLease()
                    _state.update { it.copy(
                        errorMessage = e.message ?: "Route calculation failed. Try again.",
                        errorOrigin = SurfaceOrigin.ENGINE
                    ) }
                }
            }
        }
    }

    /**
     * Navigate to a destination from the current GPS position (car deep link,
     * car favourites, `AutoEntryPoint`). Records the destination identity in the
     * state and acquires the route itself.
     */
    override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) {
        _state.update { it.copy(
            destLat = destLat,
            destLon = destLon,
            destinationName = destinationName
        ) }
        acquire(destLat, destLon, Vehicle.CAR)
    }

    /**
     * Cancel the calculation in flight, if any (spec: `route-calculation-feedback` —
     * Cancelling a calculation aborts it and releases its resources).
     *
     * The state is cleared optimistically rather than after the native `onCancel`: the
     * native breaker is cooperative, so the aborted calculation may report much later
     * or never, and the surface that asked for the cancel must not keep waiting for it.
     * The token is gone by then, so a late callback cannot resurrect the state
     * ([publishCalculationProgress], [endCalculation]).
     */
    override fun cancelAcquisition() {
        val calculation = _state.value.calculation ?: return
        val ownsLease = !_state.value.isNavigating
        Log.d(TAG, "cancelAcquisition: cancelling token=${calculation.token}")
        scope.launch(dispatchers.compute) {
            try {
                client.get().cancelRoute()
            } catch (e: Throwable) {
                Log.e(TAG, "cancelAcquisition: native cancel failed", e)
            }
        }
        endCalculation(calculation.token, CalculationOutcome.CANCELLED)
        if (ownsLease) {
            // The surface-less acquisition leased GPS to obtain its start position; an
            // aborted attempt must not keep the updates alive (spec: `navigation-engine`
            // — No location retention while idle). A reroute reuses the lease the running
            // navigation holds — releasing it would starve live guidance.
            releaseNavLease()
        }
    }

    /**
     * Publish [destLat]/[destLon] as the calculation in flight and return its token.
     * The destination is part of the state so a surface can name what is being
     * calculated for (spec: `route-calculation-feedback` — In-flight route calculation
     * is part of the shared navigation state). [trigger] is the coordinate-free identity
     * of what asked for the calculation, for the measurement entry only
     * (spec: `auto-diagnostics`). Main-thread only.
     */
    private fun beginCalculation(destLat: Double, destLon: Double, trigger: String): Long {
        calculationToken += 1
        calculationProgressCount = 0
        calculationStartedAtMillis = timeSource.nowMillis()
        calculationTrigger = trigger
        _state.update { it.copy(
            calculation = RouteCalculation(
                token = calculationToken,
                destLat = destLat,
                destLon = destLon,
                percent = null
            )
        ) }
        return calculationToken
    }

    /**
     * Publish a progress value of the live calculation, if [token] still owns it.
     * Main-thread only; see the counting and dedupe rules inside
     * (spec: `route-calculation-feedback` — Route calculation progress is exposed as a
     * percentage).
     */
    private fun publishCalculationProgress(token: Long, percent: Int) {
        val current = _state.value.calculation
        if (current == null || current.token != token) return
        // Every report the live calculation makes is counted — the measurement asks how
        // much the routing thread paid (spec: `route-calculation-feedback` — Route
        // calculation is measured without coordinates) — but only a changed value is
        // written: the native side already rate-limits (`JavaRoutingProgress`), and a
        // new value costs a recomposition on the phone and a host template push on the
        // car.
        calculationProgressCount += 1
        val capped = percent.coerceIn(0, 99)
        if (current.percent == capped) return
        _state.update { it.copy(calculation = current.copy(percent = capped)) }
    }

    /**
     * Clear the calculation [token] owns and record its measurement entry.
     *
     * Returns whether [token] was still the live one: a calculation a newer request
     * superseded ends without touching the live state and without an entry — its start
     * time is gone with the state, so a duration it could report would be a lie
     * (spec: `route-calculation-feedback` — A superseded calculation never clears the
     * live calculation's state). Main-thread only.
     */
    private fun endCalculation(token: Long, outcome: CalculationOutcome): Boolean {
        val current = _state.value.calculation
        if (current == null || current.token != token) return false
        _state.update { it.copy(calculation = null) }
        // Coordinate-free by construction: identity (source) and numbers, never a
        // position (spec: `auto-diagnostics` — no coordinates in diagnostics).
        DiagnosticsLog.log(
            ROUTE_TAG,
            "calc done: source=$calculationTrigger duration=${timeSource.nowMillis() - calculationStartedAtMillis}ms " +
                "percents=$calculationProgressCount outcome=${outcome.entryValue}"
        )
        return true
    }

    /** How a route calculation ended — the measurement entry's outcome field. */
    private enum class CalculationOutcome(val entryValue: String) {
        OK("ok"),
        ERROR("error"),
        CANCELLED("cancelled")
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun stopNavigation() {
        nativeController?.stop()
        nativeController = null
        releaseNavLease()
        stopLocationFeed()
        _state.value = NavigationState()
        // The follow stream belongs to the session: a surface collecting it must not
        // keep following a position of the ended navigation.
        _positionFlow.value = null
        _acquiredRoute.value = null
        speedSpikeFilter.reset()
        resetGuards(keepRerouteCooldown = false)
        Log.d(TAG, "stopNavigation: stopped")
    }

    override fun clearError() {
        _state.update { it.copy(errorMessage = null, errorOrigin = null) }
    }

    override fun reportError(message: String, origin: SurfaceOrigin) {
        _state.update { it.copy(errorMessage = message, errorOrigin = origin) }
    }

    /** Acquire the navigation-scoped location lease (idempotent). */
    private fun acquireNavLease() {
        if (navLease == null) {
            navLease = locationService.acquire(LocationConsumers.NAV_ENGINE)
        }
    }

    /** Release the navigation-scoped location lease (idempotent). */
    private fun releaseNavLease() {
        navLease?.release()
        navLease = null
    }

    /**
     * Forward leased fixes into the native controller for the duration of a
     * navigation. The engine is the only feeder: a surface does not push fixes of
     * its own (spec: `navigation-engine` — One navigation engine per process).
     * Speed is converted to m/s, the unit `NavigationController.processLocation`
     * expects.
     */
    private fun startLocationFeed() {
        if (locationJob != null) return
        locationJob = scope.launch {
            locationService.location.collect { fix ->
                if (fix == null) return@collect
                // Deduplicate provider doubles (Fused + LocationManager may report
                // the same fix twice).
                if (fix.time == lastFedFixTime) return@collect
                lastFedFixTime = fix.time
                processLocation(
                    fix.lat,
                    fix.lon,
                    if (!fix.speedKmH.isNaN()) fix.speedKmH / 3.6 else -1.0,
                    fix.accuracy.coerceAtLeast(0.0),
                    fix.time
                )
            }
        }
    }

    private fun stopLocationFeed() {
        locationJob?.cancel()
        locationJob = null
        lastFedFixTime = 0L
    }

    /** Reset the reroute/throttle guards and speed state for a fresh route. */
    private fun resetGuards(keepRerouteCooldown: Boolean) {
        lastRoadInfoTime = 0L
        lastRoadInfoLat = Double.NaN
        lastRoadInfoLon = Double.NaN
        if (!keepRerouteCooldown) {
            // A fresh session starts a fresh episode, but the engine's own reroute
            // restart keeps the cooldown the gate already started.
            rerouteGate.reset()
        }
        lastTunnelOrNoSignalTime = 0L
        lastOnRouteTime = 0L
        lastGpsAccuracy = -1.0
    }

    /** Process a GPS location update during navigation. */
    internal fun processLocation(
        lat: Double,
        lon: Double,
        speedMs: Double,
        accuracy: Double,
        timestamp: Long
    ) {
        lastGpsAccuracy = accuracy
        lastFixTime = timestamp
        nativeController?.processLocation(lat, lon, speedMs, accuracy, timestamp)
    }

    // ---------------------------------------------------------------------
    // Native listener
    // ---------------------------------------------------------------------

    internal fun createListener(): NavigationListener {
        return object : NavigationListener {
            override fun onPositionEstimate(position: NavigationPosition) {
                scope.launch(Dispatchers.Main) {
                    val now = timeSource.nowMillis()
                    when (position.state) {
                        com.framstag.libosmscout.client.NavigationState.EstimateInTunnel,
                        com.framstag.libosmscout.client.NavigationState.NoGpsSignal ->
                            lastTunnelOrNoSignalTime = now
                        com.framstag.libosmscout.client.NavigationState.OnRoute -> lastOnRouteTime = now
                        else -> {}
                    }
                    _state.update { it.copy(position = position) }
                    _positionFlow.value = position
                    updateRoadInfoFromPosition(position)
                }
            }

            override fun onLaneUpdate(
                oneway: Boolean, count: Int, suggested: Boolean,
                suggestedFrom: Int, suggestedTo: Int, turn: String,
                turns: Array<LaneTurn>
            ) {
                scope.launch(Dispatchers.Main) {
                    _state.update { it.copy(
                        laneOneway = oneway,
                        laneCount = count,
                        laneSuggested = suggested,
                        laneSuggestedFrom = suggestedFrom,
                        laneSuggestedTo = suggestedTo,
                        laneTurns = turns.toList()
                    ) }
                }
            }

            override fun onNextRouteInstruction(instruction: RouteInstruction) {
                scope.launch(Dispatchers.Main) {
                    val idx = nextStepIndex(
                        _state.value.instructions,
                        _state.value.currentStepIndex,
                        instruction
                    )
                    _state.update { it.copy(
                        nextInstruction = instruction,
                        currentStepIndex = idx
                    ) }
                }
            }

            override fun onRouteInstructions(instructions: Array<RouteInstruction>) {
                scope.launch(Dispatchers.Main) {
                    _state.update { it.copy(
                        instructions = instructions.toList(),
                        currentStepIndex = 0,
                        isRerouting = false,
                        isOffRoute = false
                    ) }
                    if (instructions.isNotEmpty()) {
                        _state.update { it.copy(nextInstruction = instructions[0]) }
                    }
                }
            }

            override fun onArrivalEstimate(arrivalEstimate: Long, remainingDistance: Double) {
                scope.launch(Dispatchers.Main) {
                    _state.update { it.copy(
                        etaMillis = arrivalEstimate,
                        remainingDistance = remainingDistance
                    ) }
                }
            }

            override fun onCurrentSpeed(speedKmH: Double) {
                scope.launch(Dispatchers.Main) {
                    // Spike-filtered; native sends negative when unknown — normalize to NaN.
                    val filtered = speedSpikeFilter.filter(speedKmH)
                    _state.update { it.copy(
                        currentSpeedKmH = filtered.takeIf { it >= 0.0 } ?: Double.NaN
                    ) }
                }
            }

            override fun onMaxAllowedSpeed(maxSpeedKmH: Double) {
                scope.launch(Dispatchers.Main) {
                    // Native engine sends negative when unknown — normalize to NaN.
                    _state.update { it.copy(
                        maxSpeedKmH = maxSpeedKmH.takeIf { it > 0.0 } ?: Double.NaN
                    ) }
                }
            }

            override fun onTargetReached(bearing: Double, distance: Double) {
                // no-op
            }

            override fun onRerouteRequest(
                lat: Double, lon: Double, bearing: Double, destLat: Double, destLon: Double
            ) {
                scope.launch(Dispatchers.Main) {
                    val now = timeSource.nowMillis()

                    // Ignore reroute requests shortly after tunnel / no-GPS state or
                    // with poor GPS accuracy. Native PositionAgent estimates in
                    // tunnel; when GPS returns it may briefly report OffRoute due to
                    // a noisy fix. Don't treat that as a real deviation
                    // (spec: reroute-trigger — Noise guards preserved).
                    val recentTunnelOrNoSignal = now - lastTunnelOrNoSignalTime < TUNNEL_REROUTE_GUARD_MS
                    val poorAccuracy = lastGpsAccuracy < 0 || lastGpsAccuracy > MAX_REROUTE_ACCURACY
                    if (recentTunnelOrNoSignal || poorAccuracy) {
                        Log.d(
                            TAG,
                            "onRerouteRequest: ignored, tunnel/no-signal=$recentTunnelOrNoSignal " +
                                    "poorAccuracy=$poorAccuracy"
                        )
                        rerouteGate.reset()
                        return@launch
                    }

                    if (rerouteGate.needsDeviation()) {
                        // First report of an episode: compute the deviation for the
                        // fast path. Pure math on Dispatchers.Default; the decision
                        // resumes on Main.
                        val lats = _state.value.routeLats
                        val lons = _state.value.routeLons
                        scope.launch(dispatchers.compute) {
                            val deviation = if (lats != null && lons != null) {
                                distanceToPolyline(lat, lon, lats, lons)
                            } else {
                                Double.NaN
                            }
                            withContext(Dispatchers.Main) {
                                if (rerouteGate.onRequest(deviation)) {
                                    confirmReroute(lat, lon, destLat, destLon)
                                }
                            }
                        }
                    } else {
                        if (rerouteGate.onRequest(null)) {
                            confirmReroute(lat, lon, destLat, destLon)
                        }
                    }
                }
            }

            override fun onError(message: String) {
                scope.launch(Dispatchers.Main) {
                    Log.e(TAG, "Navigation error: $message")
                }
            }
        }
    }

    /**
     * Start a reroute calculation once the confirmation gate accepts an off-route
     * report. Self-sufficient: the retained destination and the vehicle profile of
     * the running session are used, so no surface participates
     * (spec: `navigation-engine` — Reroute without a surface UI).
     */
    private fun confirmReroute(lat: Double, lon: Double, destLat: Double, destLon: Double) {
        _state.update { it.copy(isRerouting = true, isOffRoute = true) }
        val vehicle = _state.value.vehicle ?: Vehicle.CAR
        val retainedDestLat = _state.value.destLat.takeIf { !it.isNaN() } ?: destLat
        val retainedDestLon = _state.value.destLon.takeIf { !it.isNaN() } ?: destLon
        Log.d(TAG, "onRerouteRequest: rerouting vehicle=$vehicle")
        calculateAndStart(lat, lon, retainedDestLat, retainedDestLon, vehicle, fromReroute = true)
    }

    /**
     * Look up road info for the given position estimate.
     *
     * On route with a resolved way, the engine's way IS the route's street — its
     * name/ref/type are used directly, no area search and no throttle (a free state
     * copy per estimate; spec: current-road-info). Off route or without way info, a
     * throttled bearing-aware lookup (`getRoadAt`) runs off the main thread
     * (spec: current-road-info off-route scenario).
     */
    internal fun updateRoadInfoFromPosition(position: NavigationPosition) {
        val onRoute = position.state == com.framstag.libosmscout.client.NavigationState.OnRoute
        if (onRoute && (position.wayName.isNotEmpty() || position.wayRef.isNotEmpty())) {
            _state.update { it.copy(
                currentRoadInfo = CurrentRoadInfo(position.wayRef, position.wayType, position.wayName)
            ) }
            return
        }

        val now = timeSource.nowMillis()
        if (now - lastRoadInfoTime < ROAD_INFO_THROTTLE_MS) return

        // Skip if position hasn't moved significantly (~50m at mid-latitudes)
        if (!lastRoadInfoLat.isNaN() && !lastRoadInfoLon.isNaN()) {
            val dx = position.lat - lastRoadInfoLat
            val dy = position.lon - lastRoadInfoLon
            if (dx * dx + dy * dy < 0.0005 * 0.0005) return
        }

        lastRoadInfoTime = now
        lastRoadInfoLat = position.lat
        lastRoadInfoLon = position.lon

        scope.launch(dispatchers.io) {
            try {
                val road = client.get().getRoadAt(position.lat, position.lon, position.bearing)
                // The lookup stays off the main thread; its result is published through the
                // main dispatcher that owns navigation-state publication, so this background
                // worker cannot publish a snapshot of its own (spec: `navigation-engine` —
                // Engine lifecycle and threading).
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(
                        currentRoadInfo = road?.let { info ->
                            CurrentRoadInfo(info.ref, info.typeName, info.name)
                        } ?: null
                    ) }
                }
            } catch (e: Throwable) {
                // A road-name lookup is cosmetic: confine it (including a native
                // Error) and keep navigating (spec: `navigation-engine` — Native
                // failure is confined, not fatal).
                Log.e(TAG, "Road info lookup failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "NavigationEngine"

        /**
         * Diagnostics tag of the route-calculation measurement entries (spec:
         * `route-calculation-feedback` — Route calculation is measured without
         * coordinates).
         */
        internal const val ROUTE_TAG = "ROUTE"

        /**
         * Frequency of the stale-speed decay check (spec: gps-speed-priority). `internal` so a test
         * drives the same interval through [runStaleSpeedTicker] instead of pinning its own copy.
         */
        internal const val SPEED_STALE_TICK_MS = 1_000L

        /**
         * Home of the stale-speed ticker loop: a real dispatcher, never the test scheduler — an
         * endless `delay` loop on the test scheduler would hang every `runTest` (spec:
         * `navigation-engine` — Engine lifecycle and threading).
         */
        private val TICKER_DISPATCHER = Dispatchers.Default
        internal const val ROAD_INFO_THROTTLE_MS = 2000L
        internal const val MAX_REROUTE_ACCURACY = 100.0
        internal const val MIN_REROUTE_CONFIRM_COUNT = 2
        internal const val REROUTE_CONFIRM_WINDOW_MS = 60_000L
        internal const val MIN_OFF_ROUTE_DURATION_MS = 10_000L
        internal const val REROUTE_COOLDOWN_MS = 25_000L
        internal const val FAST_PATH_DISTANCE_M = 50.0
        internal const val TUNNEL_REROUTE_GUARD_MS = 30_000L

        /** Plausibility cap for the speed display filter (Autobahn ~200+). */
        internal const val MAX_PLAUSIBLE_SPEED_KMH = 250.0

        /** Compute total route distance in meters from lat/lon arrays using haversine. */
        fun computeRouteDistance(lats: DoubleArray, lons: DoubleArray): Double {
            if (lats.size < 2) return 0.0
            val R = 6371000.0
            var total = 0.0
            for (i in 0 until lats.size - 1) {
                val dlat = Math.toRadians(lats[i + 1] - lats[i])
                val dlon = Math.toRadians(lons[i + 1] - lons[i])
                val a = sin(dlat / 2) * sin(dlat / 2) +
                        cos(Math.toRadians(lats[i])) * cos(Math.toRadians(lats[i + 1])) *
                        sin(dlon / 2) * sin(dlon / 2)
                val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                total += R * c
            }
            return total
        }

        /**
         * The total distance navigation publishes for a route (spec: `osmscout-jni` — One route length
         * for a calculated route): the route's own length as every other surface reads it
         * ([routeLengthMeters]), so the routing-status progress denominator and the car trip state the
         * same length the card's statistic and the step list show.
         *
         * A route without usable geometry keeps the documented 0 even when the bridge handed over a
         * length — the progress a 0 denominator produces is what that case has always published — and a
         * route whose data carries no length at all falls back to its polyline sum, which is the
         * behaviour this replaces (spec: `navigation-engine` — Acquisition without usable polyline
         * geometry).
         */
        fun routeTotalDistanceMeters(
            route: RouteEntry,
            lats: DoubleArray?,
            lons: DoubleArray?
        ): Double {
            if (lats == null || lons == null || lats.size < 2) return 0.0
            val length = routeLengthMeters(route)
            return if (length > 0.0) length else computeRouteDistance(lats, lons)
        }
    }
}
