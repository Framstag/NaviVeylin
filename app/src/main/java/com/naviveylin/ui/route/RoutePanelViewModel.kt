package com.naviveylin.ui.route

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface RouteState {
    data object Idle : RouteState
    data object Calculating : RouteState
    data object Done : RouteState
    data class Error(val message: String) : RouteState
}

enum class ActiveField { START, DEST, NONE }

/** Parsed step info for display. */
data class RouteStepDisplay(
    val instruction: String,       // clean description, no brackets
    val distanceText: String,       // e.g. "1.2 km"
    val timeText: String,          // e.g. "5 min" or ""
    val turnType: com.framstag.libosmscout.client.TurnType
)

data class RoutePanelUiState(
    val startLocation: LocationEntry? = null,
    val destLocation: LocationEntry? = null,
    val vehicle: Vehicle = Vehicle.CAR,
    val routeState: RouteState = RouteState.Idle,
    val routeEntry: RouteEntry? = null,
    val routeSteps: List<RouteStepDisplay> = emptyList(),
    val error: String? = null,
    val activeField: ActiveField = ActiveField.NONE,
    val searchQuery: String = "",
    val searchResults: List<LocationEntry> = emptyList(),
    val isSearching: Boolean = false,
    val gpsAvailable: Boolean = false,
    val showSummaryDialog: Boolean = false,
    val activeStepIndex: Int? = null,
    val isNavigating: Boolean = false
)

data class RouteResult(
    val routeLats: DoubleArray,
    val routeLons: DoubleArray,
    val startLat: Double,
    val startLon: Double,
    val destLat: Double,
    val destLon: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RouteResult) return false
        return startLat == other.startLat && startLon == other.startLon &&
                destLat == other.destLat && destLon == other.destLon &&
                routeLats.contentEquals(other.routeLats) &&
                routeLons.contentEquals(other.routeLons)
    }

    override fun hashCode(): Int {
        var result = routeLats.contentHashCode()
        result = 31 * result + routeLons.contentHashCode()
        result = 31 * result + startLat.hashCode()
        result = 31 * result + startLon.hashCode()
        result = 31 * result + destLat.hashCode()
        result = 31 * result + destLon.hashCode()
        return result
    }
}

@HiltViewModel
class RoutePanelViewModel @Inject constructor(
    private val client: OSMScoutClient,
    val favoriteRepository: FavoriteRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val locationService: LocationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutePanelUiState())
    val uiState: StateFlow<RoutePanelUiState> = _uiState.asStateFlow()

    private val _routeResultFlow = MutableStateFlow<RouteResult?>(null)
    val routeResultFlow: StateFlow<RouteResult?> = _routeResultFlow.asStateFlow()

    private val _clearRouteSignal = MutableStateFlow(0)
    val clearRouteSignal: StateFlow<Int> = _clearRouteSignal.asStateFlow()

    /** Fires when a route calculation completes successfully. No stale value. */
    private val _routeCalculatedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val routeCalculatedEvent: SharedFlow<Unit> = _routeCalculatedEvent.asSharedFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            locationService.location.collect { loc ->
                _uiState.value = _uiState.value.copy(gpsAvailable = loc != null)
            }
        }
    }

    fun setActiveField(field: ActiveField) {
        _uiState.value = _uiState.value.copy(
            activeField = field,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = withContext(Dispatchers.Default) {
                try { client.searchLocations(query, 20, OSMScoutClient.NO_ADMIN_REGION)?.toList() ?: emptyList() }
                catch (e: Exception) { Log.e(TAG, "searchLocations failed", e); emptyList() }
            }
            _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun selectSearchResult(entry: LocationEntry) {
        // Capture the query before the state copy below clears it. Only real
        // search selections (non-blank query) are recorded; "Current Location"
        // is selected from an empty query and must not be recorded.
        val query = _uiState.value.searchQuery
        val field = _uiState.value.activeField
        when (field) {
            ActiveField.START -> setStartLocation(entry)
            ActiveField.DEST -> setDestLocation(entry)
            ActiveField.NONE -> {}
        }
        _uiState.value = _uiState.value.copy(
            activeField = ActiveField.NONE,
            searchQuery = "",
            searchResults = emptyList()
        )
        if (query.isNotBlank()) {
            viewModelScope.launch { searchHistoryRepository.record(query) }
        }
    }

    fun selectCurrentLocation() {
        val loc = locationService.location.value ?: return
        val entry = LocationEntry().apply {
            label = "Current Location"
            lat = loc.latitude; lon = loc.longitude; matchQuality = "coordinate"
        }
        selectSearchResult(entry)
    }

    fun setStartLocation(entry: LocationEntry) {
        clearRouteIfNeeded()
        _uiState.value = _uiState.value.copy(startLocation = entry)
    }

    fun setDestLocation(entry: LocationEntry) {
        clearRouteIfNeeded()
        _uiState.value = _uiState.value.copy(destLocation = entry)
    }

    /** If a route is calculated and user changes start/dest, clear the route. */
    private fun clearRouteIfNeeded() {
        val s = _uiState.value
        if (s.routeState == RouteState.Done || s.routeEntry != null) {
            _uiState.value = s.copy(
                routeState = RouteState.Idle,
                routeEntry = null,
                routeSteps = emptyList(),
                error = null,
                showSummaryDialog = false
            )
            _routeResultFlow.value = null
            _clearRouteSignal.value = _clearRouteSignal.value + 1
        }
    }

    fun setVehicle(vehicle: Vehicle) {
        _uiState.value = _uiState.value.copy(vehicle = vehicle)
    }

    fun swapStartDest() {
        val s = _uiState.value
        _uiState.value = s.copy(
            startLocation = s.destLocation,
            destLocation = s.startLocation
        )
    }

    fun calculateRoute() {
        val s = _uiState.value
        val start = s.startLocation ?: return
        val dest = s.destLocation ?: return
        _uiState.value = s.copy(routeState = RouteState.Calculating, error = null)

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val profile = RoutingProfile(s.vehicle)
                client.calculateRouteWithProfile(
                    start.lat, start.lon, dest.lat, dest.lon, profile,
                    object : RouteCallback {
                        override fun onProgress(percent: Int) {
                        }

                        override fun onSuccess(route: RouteEntry) {
                            viewModelScope.launch {
                                val steps = if (route.descriptions != null) {
                                    route.descriptions!!
                                        .filter { !it.startsWith("---") }
                                        .map { desc -> parseStepDisplay(desc) }
                                } else emptyList()

                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Done,
                                    routeEntry = route,
                                    routeSteps = steps,
                                    error = null,
                                    showSummaryDialog = !_uiState.value.isNavigating
                                )
                                _routeResultFlow.value = RouteResult(
                                    routeLats = route.latitudes,
                                    routeLons = route.longitudes,
                                    startLat = start.lat, startLon = start.lon,
                                    destLat = dest.lat, destLon = dest.lon
                                )
                                _routeCalculatedEvent.tryEmit(Unit)
                            }
                        }

                        override fun onError(message: String) {
                            viewModelScope.launch {
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Error(message), error = message
                                )
                            }
                        }

                        override fun onCancel() {
                            viewModelScope.launch {
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Idle, error = null
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    fun cancelRoute() {
        client.cancelRoute()
        _uiState.value = _uiState.value.copy(routeState = RouteState.Idle, error = null)
    }

    fun showSummaryDialog() { _uiState.value = _uiState.value.copy(showSummaryDialog = true) }

    fun dismissSummaryDialog() { _uiState.value = _uiState.value.copy(showSummaryDialog = false) }

    fun setActiveStepIndex(index: Int) { _uiState.value = _uiState.value.copy(activeStepIndex = index) }

    /** Mark navigation as active/inactive (suppresses summary dialog on reroute). */
    fun setNavigating(navigating: Boolean) {
        _uiState.value = _uiState.value.copy(isNavigating = navigating)
    }

    fun clearRoute() {
        _uiState.value = RoutePanelUiState(gpsAvailable = _uiState.value.gpsAvailable)
        _routeResultFlow.value = null
        _clearRouteSignal.value = _clearRouteSignal.value + 1
    }

    companion object {
        private const val TAG = "RoutePanelVM"
    }
}

/**
 * Parse a native description line into a [RouteStepDisplay].
 * Native format: "Turn left into Main Street  [1.2 km, 5 min]"
 */
internal fun parseStepDisplay(desc: String): RouteStepDisplay {
    val bracketIdx = desc.lastIndexOf("  [")
    if (bracketIdx < 0) return RouteStepDisplay(desc, "", "", com.framstag.libosmscout.client.TurnType.STRAIGHT_ON)

    val instruction = desc.substring(0, bracketIdx)
    val bracket = desc.substring(bracketIdx + 2) // "[1.2 km, 5 min]"

    // Parse "[1.2 km, 5 min]" or "[800 m]" or "[0.0 km]"
    val inner = bracket.removeSurrounding("[", "]")
    val parts = inner.split(", ")
    val distanceText = parts.getOrElse(0) { "" }
    val timeText = parts.getOrElse(1) { "" }

    return RouteStepDisplay(
        instruction = instruction,
        distanceText = distanceText,
        timeText = timeText,
        turnType = parseTurnType(instruction)
    )
}

/**
 * Infer [TurnType] from the instruction text.
 * Native descriptions start with phrases like "Turn left", "Turn right", etc.
 */
internal fun parseTurnType(instruction: String): com.framstag.libosmscout.client.TurnType {
    val lower = instruction.lowercase()
    return when {
        // Check start/depart first
        lower.startsWith("start") || lower.startsWith("depart") -> com.framstag.libosmscout.client.TurnType.START
        // Check destination/arrive
        lower.startsWith("destination") || lower.startsWith("arrive") ||
            lower.startsWith("target") || lower.contains("reached") -> com.framstag.libosmscout.client.TurnType.TARGET_REACHED
        // Check turn directions BEFORE road type keywords (descriptions contain "highway_*" road names)
        lower.startsWith("sharp left") -> com.framstag.libosmscout.client.TurnType.SHARP_LEFT
        lower.startsWith("sharp right") -> com.framstag.libosmscout.client.TurnType.SHARP_RIGHT
        lower.startsWith("slight left") || lower.startsWith("slightly left") -> com.framstag.libosmscout.client.TurnType.SLIGHTLY_LEFT
        lower.startsWith("slight right") || lower.startsWith("slightly right") -> com.framstag.libosmscout.client.TurnType.SLIGHTLY_RIGHT
        lower.startsWith("turn left") || lower.startsWith("bear left") ||
            lower.startsWith("left") -> com.framstag.libosmscout.client.TurnType.LEFT
        lower.startsWith("turn right") || lower.startsWith("bear right") ||
            lower.startsWith("right") -> com.framstag.libosmscout.client.TurnType.RIGHT
        lower.startsWith("enter roundabout") || lower.startsWith("enter the roundabout") -> com.framstag.libosmscout.client.TurnType.ROUNDABOUT_ENTER
        lower.startsWith("leave roundabout") || lower.startsWith("leave the roundabout") ||
            lower.startsWith("exit roundabout") -> com.framstag.libosmscout.client.TurnType.ROUNDABOUT_LEAVE
        // Road type keywords (checked last to avoid false matches on road names)
        lower.contains("motorway") || lower.contains("highway") -> com.framstag.libosmscout.client.TurnType.MOTORWAY_ENTER
        lower.contains("straight") || lower.contains("continue") -> com.framstag.libosmscout.client.TurnType.STRAIGHT_ON
        else -> com.framstag.libosmscout.client.TurnType.STRAIGHT_ON
    }
}
