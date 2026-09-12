package com.naviveylin.navigation

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
import com.naviveylin.core.AutoNavigationController
import com.naviveylin.core.NavigationState
import com.naviveylin.location.LocationService
import com.naviveylin.location.SpeedSpikeFilter
import com.naviveylin.core.SpeedStaleness
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AA-only navigation controller: real route calculation + turn-by-turn
 * navigation for the car session, mirroring the phone's
 * [NavigationViewModel] car-only path ([NavigationViewModel.startDirectRoute]).
 *
 * Wires itself into the shared [NavigationStateProvider] in [init], so every
 * AA screen (NavigationScreen, MapScreen, session observers) sees live state
 * and working [navigateTo] without the phone UI running.
 */
@Singleton
class AANavigationController @Inject constructor(
    private val client: OSMScoutClient,
    private val stateProvider: NavigationStateProvider,
    private val locationService: LocationService
) : AutoNavigationController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(NavigationState())
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var nativeController: NavigationController? = null

    // Reroute/tunnel guards (same constants as the phone VM).
    private var lastGpsAccuracy: Double = -1.0
    private var lastTunnelOrNoSignalTime = 0L
    private var lastOnRouteTime = 0L

    // Road-info lookup throttle (same rules as the phone VM).
    private var lastRoadInfoTime = 0L
    private var lastRoadInfoLat = Double.NaN
    private var lastRoadInfoLon = Double.NaN

    /** In-flight route calculation (coalesces reroute storms to one at a time). */
    private var routeJob: Job? = null

    /** Last reroute trigger time (throttle — the engine fires one per GPS fix while off-route). */
    private var lastRerouteTime = 0L

    // Last plausible speed, spike-filtered with stale-value decay. Shared
    // pure logic — see SpeedSpikeFilter (spec: speed-spike-filtering).
    private val speedSpikeFilter = SpeedSpikeFilter(maxPlausibleSpeedKmH = MAX_PLAUSIBLE_SPEED_KMH)

    // Last fix timestamp seen (staleness guard for the displayed speed;
    // spec: gps-speed-priority).
    private var lastFixTime = 0L

    init {
        // Mirror into the shared provider so AA screens get live navigation
        // state and working actions.
        stateProvider.observe(this)
        // GPS updates must run even with no phone UI — feeds the native
        // position agent during navigation. Permission-guarded no-op without
        // ACCESS_FINE_LOCATION.
        locationService.startLocationUpdates()
        scope.launch {
            locationService.location.collect { fix ->
                if (fix != null) {
                    lastFixTime = fix.time
                    val locSpeedKmH = if (!fix.speedKmH.isNaN()) fix.speedKmH else -1.0
                    processLocation(
                        fix.lat,
                        fix.lon,
                        locSpeedKmH,
                        fix.accuracy,
                        fix.time
                    )
                    // Display speed from the location provider (the emulator's
                    // simulated driving speed is sane; the engine's SpeedAgent
                    // derives absurd values from GPS jumps) — spike-filtered.
                    val filtered = speedSpikeFilter.filter(locSpeedKmH)
                    _state.value = _state.value.copy(
                        currentSpeedKmH = filtered.takeIf { it >= 0.0 } ?: Double.NaN
                    )
                }
            }
        }

        // Stale-speed ticker (spec: gps-speed-priority — stationary reads zero).
        // The provider goes silent at standstill (min-distance throttling), so
        // once no fresh fix arrives beyond the staleness window the displayed
        // speed decays to 0 instead of pinning the last delivered value.
        // Dispatchers.Default + real clock: must not feed the (virtual) test
        // scheduler with an endless delay loop.
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(SPEED_STALE_TICK_MS)
                if (_state.value.isNavigating &&
                    SpeedStaleness.isStale(lastFixTime, System.currentTimeMillis())
                ) {
                    _state.value = _state.value.copy(currentSpeedKmH = 0.0)
                }
            }
        }
    }

    override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) {
        // Record destination identity for the car screen.
        _state.value = _state.value.copy(
            destLat = destLat,
            destLon = destLon,
            destinationName = destinationName
        )
        // Resolve start position: active navigation estimate first, then GPS.
        val startLat: Double
        val startLon: Double
        val currentPos = _state.value.position
        if (currentPos != null) {
            startLat = currentPos.lat
            startLon = currentPos.lon
        } else {
            val loc = locationService.location.value
            if (loc == null) {
                Log.e(TAG, "navigateTo: no GPS position available")
                _state.value = _state.value.copy(
                    errorMessage = "GPS signal required. Please wait for GPS fix."
                )
                return
            }
            startLat = loc.lat
            startLon = loc.lon
        }

        _state.value = _state.value.copy(errorMessage = null)
        Log.d(TAG, "navigateTo: from ($startLat, $startLon) to ($destLat, $destLon)")
        startDirectRoute(startLat, startLon, destLat, destLon)
    }

    /** Calculate a route via the JNI client and start navigation (car profile). */
    private fun startDirectRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ) {
        // Coalesce reroute storms: a reroute request while a calculation is
        // already in flight is dropped — the engine fires onRerouteRequest
        // again on the next GPS fix. Keeps at most one native route
        // calculation running (concurrent JNI route calcs raced on the native
        // routing thread, SIGABRT from a destroyed joinable thread).
        if (routeJob?.isActive == true) return
        routeJob = scope.launch(Dispatchers.Default) {
            try {
                val profile = RoutingProfile(Vehicle.CAR)
                client.calculateRouteWithProfile(
                    startLat, startLon, destLat, destLon, profile,
                    object : RouteCallback {
                        override fun onProgress(percent: Int) {
                        }

                        override fun onSuccess(route: RouteEntry) {
                            scope.launch(Dispatchers.Main) {
                                Log.d(TAG, "route calculated, starting navigation")
                                _state.value = _state.value.copy(errorMessage = null)
                                startNavigation(route, Vehicle.CAR)
                            }
                        }

                        override fun onError(message: String) {
                            scope.launch(Dispatchers.Main) {
                                Log.e(TAG, "route calculation failed: $message")
                                _state.value = _state.value.copy(
                                    errorMessage = (message ?: "").ifBlank {
                                        "Route calculation failed. Try again."
                                    }
                                )
                            }
                        }

                        override fun onCancel() {
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "startDirectRoute failed", e)
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Route calculation failed. Try again."
                )
            }
        }
    }

    /** Start turn-by-turn navigation on a calculated route. */
    private fun startNavigation(routeEntry: RouteEntry, vehicle: Vehicle) {
        val handle = routeEntry.routeHandle
        if (handle == 0L) {
            Log.e(TAG, "startNavigation: routeHandle is 0, cannot start")
            _state.value = _state.value.copy(
                errorMessage = "Route calculation failed. Try again."
            )
            return
        }

        // Stop any existing controller before creating a new one.
        nativeController?.stop()
        nativeController = null

        lastGpsAccuracy = -1.0
        lastTunnelOrNoSignalTime = 0L
        lastOnRouteTime = 0L
        // Fresh navigation must not immediately hit the stale-speed zero
        // (no fix has arrived yet in this session).
        lastFixTime = System.currentTimeMillis()

        _state.value = _state.value.copy(
            isNavigating = true,
            currentStepIndex = 0,
            totalDistance = computeRouteDistance(routeEntry.latitudes, routeEntry.longitudes),
            // Route geometry for the map renderer ("_route" style).
            routeLats = routeEntry.latitudes,
            routeLons = routeEntry.longitudes
        )

        scope.launch(Dispatchers.Main) {
            try {
                nativeController = client.startNavigationWithVehicle(
                    handle, vehicle, createListener()
                )
                Log.d(TAG, "startNavigation: started")
            } catch (e: Exception) {
                Log.e(TAG, "startNavigation failed", e)
                _state.value = NavigationState()
            }
        }
    }

    override fun stopNavigation() {
        nativeController?.stop()
        nativeController = null
        _state.value = NavigationState()
        lastGpsAccuracy = -1.0
        Log.d(TAG, "stopNavigation: stopped")
    }

    override fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun reportError(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    override fun processLocation(lat: Double, lon: Double, speedKmH: Double, accuracy: Double, timestamp: Long) {
        lastGpsAccuracy = accuracy
        nativeController?.processLocation(lat, lon, speedKmH, accuracy, timestamp)
    }

    /**
     * Road info for the car surface (spec: current-road-info). On route with
     * a resolved way, the engine's way IS the route's street — its name/ref/type
     * are used directly, no area search and no throttle. Off route or without
     * way info, a throttled bearing-aware lookup (`getRoadAt`) runs off the
     * main thread. Mirrors the phone VM's rules so both surfaces agree.
     */
    internal fun updateRoadInfoFromPosition(position: NavigationPosition) {
        val onRoute = position.state == com.framstag.libosmscout.client.NavigationState.OnRoute
        if (onRoute && (position.wayName.isNotEmpty() || position.wayRef.isNotEmpty())) {
            _state.value = _state.value.copy(
                currentRoadInfo = CurrentRoadInfo(position.wayRef, position.wayType, position.wayName)
            )
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRoadInfoTime < ROAD_INFO_THROTTLE_MS) return
        if (!lastRoadInfoLat.isNaN() && !lastRoadInfoLon.isNaN()) {
            val dx = position.lat - lastRoadInfoLat
            val dy = position.lon - lastRoadInfoLon
            if (dx * dx + dy * dy < 0.0005 * 0.0005) return
        }
        lastRoadInfoTime = now
        lastRoadInfoLat = position.lat
        lastRoadInfoLon = position.lon

        scope.launch(Dispatchers.Default) {
            try {
                val road = client.getRoadAt(position.lat, position.lon, position.bearing)
                _state.value = _state.value.copy(
                    currentRoadInfo = road?.let { CurrentRoadInfo(it.ref, it.typeName, it.name) } ?: null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Road info lookup failed", e)
            }
        }
    }

    private fun createListener(): NavigationListener {
        return object : NavigationListener {
            override fun onPositionEstimate(position: NavigationPosition) {
                scope.launch(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    when (position.state) {
                        com.framstag.libosmscout.client.NavigationState.EstimateInTunnel,
                        com.framstag.libosmscout.client.NavigationState.NoGpsSignal -> lastTunnelOrNoSignalTime = now
                        com.framstag.libosmscout.client.NavigationState.OnRoute -> lastOnRouteTime = now
                        else -> {}
                    }
                    _state.value = _state.value.copy(position = position)
                    updateRoadInfoFromPosition(position)
                }
            }

            override fun onLaneUpdate(
                oneway: Boolean, count: Int, suggested: Boolean,
                suggestedFrom: Int, suggestedTo: Int, turn: String,
                turns: Array<LaneTurn>
            ) {
                scope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        laneOneway = oneway,
                        laneCount = count,
                        laneSuggested = suggested,
                        laneSuggestedFrom = suggestedFrom,
                        laneSuggestedTo = suggestedTo,
                        laneTurns = turns.toList()
                    )
                }
            }

            override fun onNextRouteInstruction(instruction: RouteInstruction) {
                scope.launch(Dispatchers.Main) {
                    val idx = _state.value.instructions.indexOfFirst {
                        it.description == instruction.description
                    }
                    _state.value = _state.value.copy(
                        nextInstruction = instruction,
                        currentStepIndex = if (idx >= 0) idx else _state.value.currentStepIndex
                    )
                }
            }

            override fun onRouteInstructions(instructions: Array<RouteInstruction>) {
                scope.launch(Dispatchers.Main) {
                    // The first entry is usually the Start instruction with
                    // distanceTo 0 — pick the first real turn so the panel
                    // never shows "0 m" before live updates arrive.
                    val firstReal = instructions.firstOrNull { it.distanceTo > 0.0 }
                    _state.value = _state.value.copy(
                        instructions = instructions.toList(),
                        currentStepIndex = 0,
                        isRerouting = false,
                        isOffRoute = false,
                        nextInstruction = firstReal ?: instructions.firstOrNull()
                    )
                }
            }

            override fun onArrivalEstimate(arrivalEstimate: Long, remainingDistance: Double) {
                scope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        etaMillis = arrivalEstimate,
                        remainingDistance = remainingDistance
                    )
                }
            }

            override fun onCurrentSpeed(speedKmH: Double) {
                // Engine-derived speed (jumpy on emulator GPS) — display uses
                // the spike-filtered location-provider speed instead.
            }

            override fun onMaxAllowedSpeed(maxSpeedKmH: Double) {
                scope.launch(Dispatchers.Main) {
                    // Native engine sends negative when unknown — normalize to NaN.
                    _state.value = _state.value.copy(maxSpeedKmH = maxSpeedKmH.takeIf { it > 0.0 } ?: Double.NaN)
                }
            }

            override fun onTargetReached(bearing: Double, distance: Double) {
                // no-op (phone VM keeps state via arrival estimate)
            }

            override fun onRerouteRequest(lat: Double, lon: Double, bearing: Double, destLat: Double, destLon: Double) {
                scope.launch(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    // Ignore reroute requests shortly after tunnel / no-GPS or
                    // with poor accuracy (same guard as the phone VM).
                    val recentTunnelOrNoSignal = now - lastTunnelOrNoSignalTime < TUNNEL_REROUTE_GUARD_MS
                    val poorAccuracy = lastGpsAccuracy < 0 || lastGpsAccuracy > MAX_REROUTE_ACCURACY
                    if (recentTunnelOrNoSignal || poorAccuracy) {
                        Log.d(TAG, "onRerouteRequest: ignored, tunnel/no-signal=$recentTunnelOrNoSignal poorAccuracy=$poorAccuracy")
                        return@launch
                    }
                    Log.d(TAG, "onRerouteRequest: rerouting from ($lat, $lon) to ($destLat, $destLon)")
                    // Throttle: the engine fires a reroute on every off-route
                    // GPS fix — without a minimum interval each fix spawns a
                    // new route calculation (reroute storm).
                    if (now - lastRerouteTime < REROUTE_MIN_INTERVAL_MS) {
                        return@launch
                    }
                    lastRerouteTime = now
                    _state.value = _state.value.copy(isRerouting = true, isOffRoute = true)
                    // No phone RoutePanelViewModel in the AA process — recalculate
                    // the direct route from the current position.
                    startDirectRoute(lat, lon, destLat, destLon)
                }
            }

            override fun onError(message: String) {
                Log.e(TAG, "Navigation error: $message")
            }
        }
    }

    private fun computeRouteDistance(lats: DoubleArray, lons: DoubleArray): Double {
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

    companion object {
        private const val TAG = "AANavigationController"

        /** Min interval between reroute recalculations (engine fires per fix). */
        private const val REROUTE_MIN_INTERVAL_MS = 10_000L

        /** Frequency of the stale-speed decay check (spec: gps-speed-priority). */
        private const val SPEED_STALE_TICK_MS = 1_000L

        /** Plausibility cap for the speed display filter (Autobahn ~200+). */
        private const val MAX_PLAUSIBLE_SPEED_KMH = 250.0

        private const val MAX_REROUTE_ACCURACY = 25.0
        private const val TUNNEL_REROUTE_GUARD_MS = 15_000L

        /** Min interval between road-info lookups (off-route fallback). */
        private const val ROAD_INFO_THROTTLE_MS = 2000L
    }
}
