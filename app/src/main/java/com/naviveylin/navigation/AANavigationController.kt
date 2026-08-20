package com.naviveylin.navigation

import android.util.Log
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    init {
        // Mirror into the shared provider so AA screens get live navigation
        // state and working actions.
        stateProvider.observe(this)
        // GPS updates must run even with no phone UI — feeds the native
        // position agent during navigation. Permission-guarded no-op without
        // ACCESS_FINE_LOCATION.
        locationService.startLocationUpdates()
        scope.launch {
            locationService.location.collect { loc ->
                if (loc != null) {
                    processLocation(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasSpeed()) loc.speed * 3.6 else -1.0,
                        if (loc.hasAccuracy()) loc.accuracy.toDouble() else -1.0,
                        loc.time
                    )
                }
            }
        }
    }

    override fun navigateTo(destLat: Double, destLon: Double) {
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
            startLat = loc.latitude
            startLon = loc.longitude
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
        scope.launch(Dispatchers.Default) {
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

        _state.value = _state.value.copy(
            isNavigating = true,
            currentStepIndex = 0,
            totalDistance = computeRouteDistance(routeEntry.latitudes, routeEntry.longitudes)
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
                    _state.value = _state.value.copy(
                        instructions = instructions.toList(),
                        currentStepIndex = 0,
                        isRerouting = false,
                        isOffRoute = false,
                        nextInstruction = instructions.firstOrNull()
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
                scope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(currentSpeedKmH = speedKmH)
                }
            }

            override fun onMaxAllowedSpeed(maxSpeedKmH: Double) {
                scope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(maxSpeedKmH = maxSpeedKmH)
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
        private const val MAX_REROUTE_ACCURACY = 25.0
        private const val TUNNEL_REROUTE_GUARD_MS = 15_000L
    }
}
