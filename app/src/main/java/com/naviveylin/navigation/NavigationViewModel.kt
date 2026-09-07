package com.naviveylin.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.naviveylin.core.NavigationState
import com.naviveylin.location.LocationService
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteState
import com.naviveylin.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val client: OSMScoutClient,
    private val stateProvider: NavigationStateProvider,
    private val locationService: LocationService,
    @param:ApplicationContext private val context: Context
) : ViewModel(), com.naviveylin.core.NavigationViewModel {

    private val _state = MutableStateFlow(NavigationState())
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    /** Flow of navigation position estimates for map centering. */
    private val _positionFlow = MutableStateFlow<NavigationPosition?>(null)
    val positionFlow: StateFlow<NavigationPosition?> = _positionFlow.asStateFlow()

    private var nativeController: NavigationController? = null
    private var routePanelViewModel: RoutePanelViewModel? = null
    private var onFollowModeChanged: ((Boolean) -> Unit)? = null

    // Internal flag — not part of shared NavigationState
    private var showFirstInstructionOnStart: Boolean = false

    init {
        // Wire state to the singleton provider for Android Auto
        stateProvider.observe(this)
        // Ensure GPS updates run even when the phone UI is not open, so
        // navigation can start from the car (deep link / car-only flow).
        // Permission-guarded no-op without ACCESS_FINE_LOCATION.
        locationService.startLocationUpdates()
    }

    // Road info lookup throttle
    private var lastRoadInfoTime = 0L
    private var lastRoadInfoLat = Double.NaN
    private var lastRoadInfoLon = Double.NaN

    // Last GPS accuracy from processLocation; used as extra guard with tunnel guard
    private var lastGpsAccuracy: Double = -1.0

    // Last plausible speed for the spike filter (same rule as AA/MapCanvasViewModel).
    private var lastValidSpeedKmH = Double.NaN

    /**
     * Filter speed spikes: reject speed > 150 km/h, keep the last good speed
     * (same rule as MapCanvasViewModel — GPS jumps otherwise produce absurd
     * readings on the speed display).
     */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0.0 && rawSpeedKmH <= 150.0) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    // Reroute confirmation gate: fast first trigger + post-reroute cooldown.
    // Native controller fires every ~5s while off-route (RouteStateAgent.cpp:84).
    private val rerouteGate = RerouteConfirmationGate(
        minConfirmCount = MIN_REROUTE_CONFIRM_COUNT,
        minOffRouteDurationMs = MIN_OFF_ROUTE_DURATION_MS,
        confirmWindowMs = REROUTE_CONFIRM_WINDOW_MS,
        cooldownMs = REROUTE_COOLDOWN_MS,
        fastPathDistanceM = FAST_PATH_DISTANCE_M
    )

    // Ignore reroute requests shortly after tunnel/NoGpsSignal to avoid GPS noise false positives
    private var lastTunnelOrNoSignalTime = 0L
    private var lastOnRouteTime = 0L

    /** Callback for follow mode toggling — set by MapCanvasScreen. */
    fun setFollowModeCallback(cb: (Boolean) -> Unit) {
        onFollowModeChanged = cb
    }

    /** Set RoutePanelViewModel for reroute support. */
    fun setRoutePanelViewModel(vm: RoutePanelViewModel) {
        routePanelViewModel = vm
        // Auto-start navigation on new route when reroute is confirmed.
        // Don't force follow mode on reroute — avoid map rotation / snap jump.
        viewModelScope.launch {
            vm.routeCalculatedEvent.collect {
                if (_state.value.isRerouting) {
                    val state = vm.uiState.value
                    if (state.routeEntry != null) {
                        Log.d(TAG, "Reroute confirmed, starting navigation")
                        startNavigation(state.routeEntry, state.vehicle, forceFollowMode = false)
                    }
                }
            }
        }
    }

    /** Start navigation on a calculated route. */
    fun startNavigation(routeEntry: RouteEntry, vehicle: Vehicle, forceFollowMode: Boolean = true) {
        val handle = routeEntry.routeHandle
        if (handle == 0L) {
            Log.e(TAG, "startNavigation: routeHandle is 0, cannot start")
            return
        }

        // Restarting navigation redraws the route on the map (it may have been
        // hidden by a previous stop — spec: stop-navigation-hides-route).
        routePanelViewModel?.showRouteOnMap()

        // Stop any existing native controller before creating a new one.
        // Without this the old controller keeps emitting callbacks in parallel,
        // causing marker jumps, double reroutes, and corrupted navigation state.
        nativeController?.stop()
        nativeController = null

        // Reset reroute confirmation and guard state for fresh route
        rerouteGate.reset()
        lastTunnelOrNoSignalTime = 0L
        lastOnRouteTime = 0L
        lastGpsAccuracy = -1.0

        val totalDistance = computeRouteDistance(routeEntry.latitudes, routeEntry.longitudes)
        _state.value = _state.value.copy(isNavigating = true, currentStepIndex = 0,
            totalDistance = totalDistance,
            // Progress starts at 0%: remaining distance equals the total until
            // the first arrival estimate arrives (routing-progress-indicator).
            remainingDistance = totalDistance,
            navigationStartTimeMillis = System.currentTimeMillis(),
            // Route geometry for shared state consumers (car parity): the car
            // controller stores the polyline (AANavigationController.kt), so the
            // phone must too. The phone map still draws via routeResultFlow.
            routeLats = routeEntry.latitudes,
            routeLons = routeEntry.longitudes)
        routePanelViewModel?.setActiveStepIndex(0)
        showFirstInstructionOnStart = true
        if (forceFollowMode) {
            onFollowModeChanged?.invoke(true)
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                nativeController = client.startNavigationWithVehicle(
                    handle, vehicle, createListener()
                )
                Log.d(TAG, "startNavigation: started")
            } catch (e: Exception) {
                Log.e(TAG, "startNavigation failed", e)
                _state.value = NavigationState()
                onFollowModeChanged?.invoke(false)
            }
        }
    }

    override fun stopNavigation() {
        nativeController?.stop()
        nativeController = null
        _state.value = NavigationState()
        onFollowModeChanged?.invoke(false)
        lastRoadInfoTime = 0L
        lastRoadInfoLat = Double.NaN
        lastRoadInfoLon = Double.NaN
        rerouteGate.reset()
        lastTunnelOrNoSignalTime = 0L
        lastOnRouteTime = 0L
        lastGpsAccuracy = -1.0
        Log.d(TAG, "stopNavigation: stopped")
    }

    override fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun reportError(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) {
        // Record destination identity for the car screen (name/address when
        // known, else coordinates-only display).
        _state.value = _state.value.copy(
            destLat = destLat,
            destLon = destLon,
            destinationName = destinationName
        )
        // Resolve start position: active navigation estimate first, then LocationService GPS.
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
                _state.value = _state.value.copy(errorMessage = "GPS signal required. Please wait for GPS fix.")
                return
            }
            startLat = loc.lat
            startLon = loc.lon
        }

        // Clear any previous error
        _state.value = _state.value.copy(errorMessage = null)

        val vm = routePanelViewModel
        if (vm == null) {
            // Car-only path: no phone UI wired — calculate the route directly
            // via the JNI client and start navigation.
            startDirectRoute(startLat, startLon, destLat, destLon)
            return
        }

        val startLoc = com.framstag.libosmscout.client.LocationEntry().apply {
            label = context.getString(R.string.current_location)
            lat = startLat
            lon = startLon
            matchQuality = "match"
        }
        val destLoc = com.framstag.libosmscout.client.LocationEntry().apply {
            label = context.getString(R.string.destination)
            lat = destLat
            lon = destLon
            matchQuality = "match"
        }

        vm.setStartLocation(startLoc)
        vm.setDestLocation(destLoc)
        vm.calculateRoute()

        // Observe route result: start nav on success, show error on failure
        viewModelScope.launch {
            vm.uiState.collect { uiState ->
                when (uiState.routeState) {
                    is RouteState.Done -> {
                        if (uiState.routeEntry != null) {
                            Log.d(TAG, "navigateTo: route calculated, starting navigation")
                            _state.value = _state.value.copy(errorMessage = null)
                            startNavigation(uiState.routeEntry, uiState.vehicle)
                        }
                    }
                    is RouteState.Error -> {
                        val msg = (uiState.routeState as RouteState.Error).message
                        Log.e(TAG, "navigateTo: route calculation failed: $msg")
                        _state.value = _state.value.copy(
                            errorMessage = msg ?: "Route calculation failed. Try again."
                        )
                    }
                    else -> { /* Calculating, Idle — no action */ }
                }
            }
        }
    }

    /**
     * Calculate a route directly via the JNI client and start navigation.
     *
     * Used when the phone [RoutePanelViewModel] is not wired (navigation
     * started from the car without the phone UI being open). Defaults to
     * the car routing profile.
     */
    internal fun startDirectRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val profile = RoutingProfile(Vehicle.CAR)
                client.calculateRouteWithProfile(
                    startLat, startLon, destLat, destLon, profile,
                    object : RouteCallback {
                        override fun onProgress(percent: Int) {
                        }

                        override fun onSuccess(route: RouteEntry) {
                            viewModelScope.launch(Dispatchers.Main) {
                                Log.d(TAG, "startDirectRoute: route calculated, starting navigation")
                                _state.value = _state.value.copy(errorMessage = null)
                                startNavigation(route, Vehicle.CAR, forceFollowMode = false)
                            }
                        }

                        override fun onError(message: String) {
                            viewModelScope.launch(Dispatchers.Main) {
                                Log.e(TAG, "startDirectRoute: route calculation failed: $message")
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

    /** Process a GPS location update during navigation. */
    fun processLocation(lat: Double, lon: Double, speed: Double, accuracy: Double, timestamp: Long) {
        lastGpsAccuracy = accuracy
        nativeController?.processLocation(lat, lon, speed, accuracy, timestamp)
    }

    private fun createListener(): NavigationListener {
        return object : NavigationListener {
            override fun onPositionEstimate(position: NavigationPosition) {
                viewModelScope.launch(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    when (position.state) {
                        com.framstag.libosmscout.client.NavigationState.EstimateInTunnel,
                        com.framstag.libosmscout.client.NavigationState.NoGpsSignal -> lastTunnelOrNoSignalTime = now
                        com.framstag.libosmscout.client.NavigationState.OnRoute -> lastOnRouteTime = now
                        else -> {}
                    }
                    _state.value = _state.value.copy(position = position)
                    showFirstInstructionOnStart = false
                    _positionFlow.value = position
                    updateRoadInfoFromPosition(position.lat, position.lon)
                }
            }

            override fun onLaneUpdate(oneway: Boolean, count: Int, suggested: Boolean,
                                           suggestedFrom: Int, suggestedTo: Int, turn: String,
                                           turns: Array<LaneTurn>) {
                viewModelScope.launch(Dispatchers.Main) {
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
                viewModelScope.launch(Dispatchers.Main) {
                    val idx = _state.value.instructions.indexOfFirst {
                        it.description == instruction.description
                    }
                    val newIndex = if (idx >= 0) idx else _state.value.currentStepIndex
                    _state.value = _state.value.copy(
                        nextInstruction = instruction,
                        currentStepIndex = newIndex
                    )
                    // Keep the route panel summary's active step in sync
                    // (spec: routing-summary — active step highlighting).
                    routePanelViewModel?.setActiveStepIndex(newIndex)
                }
            }

            override fun onRouteInstructions(instructions: Array<RouteInstruction>) {
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        instructions = instructions.toList(),
                        currentStepIndex = 0,
                        isRerouting = false,
                        isOffRoute = false
                    )
                    routePanelViewModel?.setActiveStepIndex(0)
                    if (instructions.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            nextInstruction = instructions[0]
                        )
                    }
                }
            }

            override fun onArrivalEstimate(arrivalEstimate: Long, remainingDistance: Double) {
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        etaMillis = arrivalEstimate,
                        remainingDistance = remainingDistance
                    )
                }
            }

            override fun onCurrentSpeed(speedKmH: Double) {
                viewModelScope.launch(Dispatchers.Main) {
                    // Spike-filtered; native sends negative when unknown — normalize to NaN.
                    val filtered = filterSpeed(speedKmH)
                    _state.value = _state.value.copy(currentSpeedKmH = filtered.takeIf { it >= 0.0 } ?: Double.NaN)
                }
            }

            override fun onMaxAllowedSpeed(maxSpeedKmH: Double) {
                viewModelScope.launch(Dispatchers.Main) {
                    // Native engine sends negative when unknown — normalize to NaN.
                    _state.value = _state.value.copy(maxSpeedKmH = maxSpeedKmH.takeIf { it > 0.0 } ?: Double.NaN)
                }
            }

            override fun onTargetReached(bearing: Double, distance: Double) {
                // no-op
            }

            override fun onRerouteRequest(lat: Double, lon: Double, bearing: Double, destLat: Double, destLon: Double) {
                viewModelScope.launch(Dispatchers.Main) {
                    val now = System.currentTimeMillis()

                    // Ignore reroute requests shortly after tunnel / no-GPS state or with poor GPS accuracy.
                    // Native PositionAgent estimates in tunnel; when GPS returns it may briefly
                    // report OffRoute due to noisy fix. Don't treat that as a real deviation.
                    val recentTunnelOrNoSignal = now - lastTunnelOrNoSignalTime < TUNNEL_REROUTE_GUARD_MS
                    val poorAccuracy = lastGpsAccuracy < 0 || lastGpsAccuracy > MAX_REROUTE_ACCURACY
                    if (recentTunnelOrNoSignal || poorAccuracy) {
                        Log.d(TAG, "onRerouteRequest: ignored, tunnel/no-signal=$recentTunnelOrNoSignal poorAccuracy=$poorAccuracy")
                        rerouteGate.reset()
                        return@launch
                    }

                    val vm = routePanelViewModel ?: return@launch
                    if (rerouteGate.needsDeviation()) {
                        // First report of an episode: compute deviation for the fast path.
                        // Pure math on Dispatchers.Default; decision resumes on Main.
                        val entry = vm.uiState.value.routeEntry
                        val lats = entry?.latitudes
                        val lons = entry?.longitudes
                        viewModelScope.launch(Dispatchers.Default) {
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
                viewModelScope.launch(Dispatchers.Main) {
                    Log.e(TAG, "Navigation error: $message")
                }
            }
        }
    }

    /**
     * Start a reroute calculation from the current position to the original
     * destination. Called once the confirmation gate accepts an off-route report.
     */
    private fun confirmReroute(lat: Double, lon: Double, destLat: Double, destLon: Double) {
        _state.value = _state.value.copy(isRerouting = true, isOffRoute = true)
        Log.d(TAG, "onRerouteRequest: rerouting")
        val vm = routePanelViewModel ?: return
        // Set current position as start, keep original destination
        val currentLoc = com.framstag.libosmscout.client.LocationEntry().apply {
            this.label = context.getString(R.string.current_location)
            this.lat = lat
            this.lon = lon
            this.matchQuality = "coordinate"
        }
        val destLoc = com.framstag.libosmscout.client.LocationEntry().apply {
            this.label = context.getString(R.string.destination)
            this.lat = destLat
            this.lon = destLon
            this.matchQuality = "coordinate"
        }
        vm.updateLocationsForReroute(currentLoc, destLoc)
        vm.calculateRoute()
        vm.dismissSummaryDialog() // Don't show summary dialog during navigation
    }

    override fun onCleared() {
        super.onCleared()
        stopNavigation()
    }

    /**
     * Look up road info at the given coordinate using the description service.
     * Throttled to avoid DB queries on every position update.
     */
    private fun updateRoadInfoFromPosition(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastRoadInfoTime < ROAD_INFO_THROTTLE_MS) return

        // Skip if position hasn't moved significantly (~50m at mid-latitudes)
        if (!lastRoadInfoLat.isNaN() && !lastRoadInfoLon.isNaN()) {
            val dx = lat - lastRoadInfoLat
            val dy = lon - lastRoadInfoLon
            if (dx * dx + dy * dy < 0.0005 * 0.0005) return
        }

        lastRoadInfoTime = now
        lastRoadInfoLat = lat
        lastRoadInfoLon = lon

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val desc = client.getDescription(lat, lon, ROAD_INFO_MAGNIFICATION)
                if (desc == null) {
                    _state.value = _state.value.copy(currentRoadInfo = null)
                    return@launch
                }

                var ref = ""
                var typeName = ""
                var name = ""
                for (entry in desc.entries) {
                    if (entry.sectionKey != "General") continue
                    when (entry.labelKey) {
                        "NameRef" -> ref = entry.value
                        "Type" -> typeName = entry.value
                        "Name" -> name = entry.value
                    }
                }
                val info = CurrentRoadInfo(ref, typeName, name)
                _state.value = _state.value.copy(currentRoadInfo = info)
            } catch (e: Exception) {
                Log.e(TAG, "Road info lookup failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "NavigationVM"
        private const val ROAD_INFO_THROTTLE_MS = 2000L
        private const val ROAD_INFO_MAGNIFICATION = 15
        private const val MAX_REROUTE_ACCURACY = 100.0
        private const val MIN_REROUTE_CONFIRM_COUNT = 2
        private const val REROUTE_CONFIRM_WINDOW_MS = 60_000L
        private const val MIN_OFF_ROUTE_DURATION_MS = 10_000L
        private const val REROUTE_COOLDOWN_MS = 25_000L
        private const val FAST_PATH_DISTANCE_M = 50.0
        private const val TUNNEL_REROUTE_GUARD_MS = 30_000L

        /** Plausibility cap for the speed display filter (Autobahn ~200+). */
        private const val MAX_PLAUSIBLE_SPEED_KMH = 250.0

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
    }
}
