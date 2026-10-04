package com.naviveylin.ui.route

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.RouteCallback
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.RoutingProfile
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.R
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.location.LocationService
import com.naviveylin.core.LocationGrant
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * State of the route-planning session (spec: `route-planning-session`).
 *
 * - [INACTIVE]: no session on the map — no overlay, no route drawn by a session.
 * - [EDITING]: a session is open without a calculated route; fields are editable,
 *   calculate is offered. Covers "calculating" and "calculation failed" as the
 *   panel's existing `routeState` already models those.
 * - [REVIEWING]: a session is open on a calculated route; the step list is
 *   analysable and Start Navigation is offered.
 * - [STOPPED]: navigation was stopped inside the session and the grace period is
 *   running (Restart / End now).
 *
 * Whether a step is currently analysed is the panel's own `analysedStepIndex`, not a
 * session state: it is an overlay on top of reviewing, and a second enum for it would
 * be able to contradict the selection.
 */
enum class RouteSessionState { INACTIVE, EDITING, REVIEWING, STOPPED }

/**
 * Size of the session's overlay on the phone (spec: `route-planning-session` — Session
 * overlay anchors). [HIDDEN] is the session's "map free for analysis" state: only a
 * route-ready affordance remains.
 */
enum class RouteOverlayAnchor { EXPANDED, COMPACT, HIDDEN }

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
    /**
     * True when the last route request was refused because the precise location
     * grant is missing (spec: `location-permissions` — Starting navigation requires
     * precise location). Drives the phone's upgrade dialog; cleared when the route
     * is calculated or the user dismisses the dialog.
     */
    val preciseLocationRequired: Boolean = false,
    val activeField: ActiveField = ActiveField.NONE,
    val searchQuery: String = "",
    val searchResults: List<LocationEntry> = emptyList(),
    val isSearching: Boolean = false,
    /**
     * Point the current start/destination result list was ranked and measured
     * against — the last known GPS fix, else the map center the screen reported
     * (spec: search-result-ranking — "Distance reference for ordering and
     * display"), null when neither is available.
     */
    val searchReference: SearchReference? = null,
    val gpsAvailable: Boolean = false,
    val activeStepIndex: Int? = null,
    val isNavigating: Boolean = false,
    /**
     * Manoeuvre positions of the current route's steps, index-aligned with
     * [routeSteps] (spec: `route-analysis`). Empty when the route carries no
     * positions, in which case no step can be located on the map.
     */
    val stepAnchors: List<StepAnchor> = emptyList(),
    /**
     * The overlay's current anchor (spec: `route-planning-session` — Session overlay
     * anchors). Reported by the overlay as it is dragged/hidden, and read by the map for
     * the fit's free area. Defaults to [RouteOverlayAnchor.HIDDEN] so a session whose
     * overlay is not composed claims no part of the canvas.
     */
    val overlayAnchor: RouteOverlayAnchor = RouteOverlayAnchor.HIDDEN,
    /**
     * The step the user is analysing, or null when no step is selected (spec:
     * `route-analysis`).
     */
    val analysedStepIndex: Int? = null,
    /**
     * Polyline vertex range of the analysed step, both ends inclusive; null when
     * no step is analysed or the step has no locatable position.
     */
    val analysedSegment: IntRange? = null
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
    private val locationService: LocationService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutePanelUiState())
    val uiState: StateFlow<RoutePanelUiState> = _uiState.asStateFlow()

    /**
     * Background dispatcher for search / route-callback work. Test hook:
     * point it at a TestDispatcher shared with the test's `runTest`.
     */
    @VisibleForTesting
    internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    private val _routeResultFlow = MutableStateFlow<RouteResult?>(null)
    val routeResultFlow: StateFlow<RouteResult?> = _routeResultFlow.asStateFlow()

    private val _clearRouteSignal = MutableStateFlow(0)
    val clearRouteSignal: StateFlow<Int> = _clearRouteSignal.asStateFlow()

    /**
     * Manoeuvre position of the analysed step, or null when nothing is analysed
     * (spec: `route-analysis` — the map camera moves onto the analysed manoeuvre).
     * The map observes this instead of the whole panel state, so an unrelated state
     * change (a keystroke in a field, a vehicle switch) never moves the camera.
     */
    private val _analysedAnchor = MutableStateFlow<StepAnchor?>(null)
    val analysedAnchor: StateFlow<StepAnchor?> = _analysedAnchor.asStateFlow()

    /**
     * The polyline vertex range the analysed step owns, so the map can fit exactly that segment
     * (spec: `route-analysis` — the camera has to show the analysed segment, not a fixed zoom
     * level around one point).
     */
    private val _analysedSegmentRange = MutableStateFlow<IntRange?>(null)
    val analysedSegmentRange: StateFlow<IntRange?> = _analysedSegmentRange.asStateFlow()

    /**
     * The session's state on the map (spec: `route-planning-session`). Owned here, and
     * the map follows it: opening, reviewing, the stopped-state grace and the two exits
     * all change it, so the session's lifetime has one owner.
     */
    private val _sessionState = MutableStateFlow(RouteSessionState.INACTIVE)
    val sessionState: StateFlow<RouteSessionState> = _sessionState.asStateFlow()

    /** The single grace deadline of the stopped state; null while none is running. */
    private var graceJob: Job? = null

    /** Report the overlay's anchor (idempotent). */
    fun setOverlayAnchor(anchor: RouteOverlayAnchor) {
        if (_uiState.value.overlayAnchor != anchor) {
            _uiState.value = _uiState.value.copy(overlayAnchor = anchor)
        }
    }

    /**
     * Whether the calculated route is currently drawn on the map. Stopping
     * navigation hides the route (spec: stop-navigation-hides-route) while
     * keeping the panel state; restarting navigation shows it again.
     */
    private val _routeVisible = MutableStateFlow(true)
    val routeVisible: StateFlow<Boolean> = _routeVisible.asStateFlow()

    /** Fires when a route calculation completes successfully. No stale value. */
    private val _routeCalculatedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val routeCalculatedEvent: SharedFlow<Unit> = _routeCalculatedEvent.asSharedFlow()

    /**
     * Fires when a route calculation fails. Fire-and-forget with a single
     * buffer slot: the phone surfaces the message via snackbar while
     * navigating (spec: reroute-route-visibility). When the route panel is
     * open, the in-panel [RouteState.Error] text already covers the failure.
     */
    private val _routeErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routeErrorEvent: SharedFlow<String> = _routeErrorEvent.asSharedFlow()

    private var searchJob: Job? = null

    /**
     * Map center reported by the screen, used as the search reference only when
     * no GPS fix exists (spec: search-result-ranking).
     */
    private var fallbackSearchCenter: SearchReference? = null

    init {
        viewModelScope.launch {
            locationService.location.collect { loc ->
                _uiState.value = _uiState.value.copy(gpsAvailable = loc != null)
            }
        }
    }

    /** Report the map center the screen currently shows (search reference fallback). */
    fun setFallbackSearchCenter(lat: Double, lon: Double) {
        fallbackSearchCenter = if (lat.isFinite() && lon.isFinite()) SearchReference(lat, lon) else null
    }

    /**
     * Reference point for the start/destination result list: the last known GPS
     * fix, else the map center; null when neither is usable.
     */
    private fun searchDistanceReference(): SearchReference? {
        val fix = locationService.location.value
        if (fix != null && fix.lat.isFinite() && fix.lon.isFinite()) {
            return SearchReference(fix.lat, fix.lon)
        }
        return fallbackSearchCenter
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
            val criteria = SearchQueryParser.criteriaOf(query)
            val reference = searchDistanceReference()
            val results = withContext(defaultDispatcher) {
                try {
                    // Larger candidate set than displayed, so the ranking can
                    // promote a result the backend's order put past the page
                    // (spec: search-result-ranking).
                    val raw = client.searchLocations(
                        query,
                        SearchResultRanker.CANDIDATE_LIMIT,
                        OSMScoutClient.NO_ADMIN_REGION
                    )?.toList() ?: emptyList()
                    SearchResultRanker.rankForDisplay(raw, criteria, reference)
                } catch (e: Exception) {
                    Log.e(TAG, "searchLocations failed", e)
                    emptyList()
                }
            }
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                isSearching = false,
                searchReference = reference
            )
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
            label = context.getString(R.string.current_location)
            lat = loc.lat; lon = loc.lon; matchQuality = "coordinate"
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

    /**
     * Update start and destination for a programmatic reroute without
     * clearing the currently drawn route (spec: reroute-route-visibility).
     *
     * Unlike [setStartLocation]/[setDestLocation] this must NOT call
     * [clearRouteIfNeeded]: the map keeps drawing the last route until the
     * reroute calculation lands. On success the new [RouteResult] replaces
     * it via [routeResultFlow]; on failure the last route stays visible and
     * the error is surfaced through [routeErrorEvent].
     */
    fun updateLocationsForReroute(start: LocationEntry, dest: LocationEntry) {
        _uiState.value = _uiState.value.copy(startLocation = start, destLocation = dest)
    }

    /** If a route is calculated and user changes start/dest, clear the route. */
    private fun clearRouteIfNeeded() {
        val s = _uiState.value
        if (s.routeState == RouteState.Done || s.routeEntry != null) {
            _uiState.value = s.copy(
                routeState = RouteState.Idle,
                routeEntry = null,
                routeSteps = emptyList(),
                error = null
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

        // Navigation gate (spec: `location-permissions` — Starting navigation
        // requires precise location): with only the approximate grant no route
        // request reaches the routing engine, because the start point could be a
        // kilometre off. The map, free driving, search and favourites are unaffected.
        if (!LocationGrant.hasPrecise(context)) {
            val message = context.getString(
                com.naviveylin.core.R.string.location_precise_required_navigation
            )
            Log.w(TAG, "calculateRoute: refused, precise location not granted")
            _uiState.value = s.copy(
                routeState = RouteState.Error(message),
                error = message,
                preciseLocationRequired = true
            )
            _routeErrorEvent.tryEmit(message)
            return
        }

        _uiState.value = s.copy(
            routeState = RouteState.Calculating,
            error = null,
            preciseLocationRequired = false
        )

        viewModelScope.launch {
            withContext(defaultDispatcher) {
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
                                    stepAnchors = instructionAnchors(route),
                                    // A finished calculation starts at the route's current step
                                    // (owner finding, 2026-10-03): the min overlay then shows
                                    // a real entry and its position from the start, while the
                                    // camera keeps the overview fit — only an explicit step
                                    // selection moves it.
                                    analysedStepIndex = currentStepOf(steps),
                                    analysedSegment = segmentOf(
                                        _uiState.value.copy(
                                            routeEntry = route,
                                            routeSteps = steps,
                                            stepAnchors = instructionAnchors(route)
                                        ),
                                        currentStepOf(steps) ?: 0
                                    ),
                                    error = null,
                                    preciseLocationRequired = false
                                )
                                _analysedAnchor.value = null
                                _analysedSegmentRange.value = _uiState.value.analysedSegment
                                if (_sessionState.value != RouteSessionState.INACTIVE) {
                                    _sessionState.value = RouteSessionState.REVIEWING
                                    // A finished calculation is a result, and the result is the
                                    // route's step list: the card lands in its max state
                                    // (owner directive, 2026-10-03 — the list belongs at the
                                    // bottom), so a fresh calculation never leaves the user in
                                    // the min overlay without it.
                                    setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
                                }
                                _routeVisible.value = true
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
                                Log.e(TAG, "Route calculation failed: $message")
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Error(message), error = message
                                )
                                _routeErrorEvent.tryEmit(message)
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

    /** Dismiss the phone's precise-location upgrade dialog. */
    fun dismissPreciseLocationRequirement() {
        if (_uiState.value.preciseLocationRequired) {
            _uiState.value = _uiState.value.copy(preciseLocationRequired = false)
        }
    }

    fun cancelRoute() {
        client.cancelRoute()
        _uiState.value = _uiState.value.copy(routeState = RouteState.Idle, error = null)
    }

    fun setActiveStepIndex(index: Int) { _uiState.value = _uiState.value.copy(activeStepIndex = index) }

    /**
     * Mark a step as analysed and resolve the polyline segment it owns (spec:
     * `route-analysis`). A step with no locatable position is still analysed — the
     * list marks it — but carries no segment. An out-of-range index changes nothing.
     */
    fun analyseStep(index: Int) {
        val state = _uiState.value
        if (index !in state.routeSteps.indices) return
        val segment = segmentOf(state, index)
        _uiState.value = state.copy(
            analysedStepIndex = index,
            analysedSegment = segment
        )
        // Publish the segment **before** the anchor: the anchor is what a consumer reacts to (the
        // map focuses the analysed step's leg when it changes), so the order is the contract —
        // publishing the anchor first let the map read the *previous* step's range and move the
        // camera onto the wrong leg (device measurement, 2026-10-03: the fit logged `range=9..28`
        // while the highlight drew `range=28..32`).
        _analysedSegmentRange.value = segment
        _analysedAnchor.value = state.stepAnchors.getOrNull(index)
    }

    /** Drop the analysed step (spec: `route-analysis`). */
    fun clearAnalysedStep() {
        _uiState.value = _uiState.value.copy(analysedStepIndex = null, analysedSegment = null)
        _analysedSegmentRange.value = null
        _analysedAnchor.value = null
    }

    /**
     * Toggle the analysed step: tapping the analysed step again drops the selection,
     * tapping another step analyses it (spec: `route-analysis`). Kept here rather than
     * in the overlay so the rule has one home and is unit-testable.
     */
    fun toggleAnalysedStep(index: Int) {
        if (_uiState.value.analysedStepIndex == index) clearAnalysedStep() else analyseStep(index)
    }

    /**
     * The step navigator's forward control (spec: `route-analysis` — Step navigator):
     * nothing analysed starts at the first step, otherwise the next one; at the last step
     * it is a no-op. Kept here rather than in the overlay so the bound has one home.
     */
    fun analyseNextStep() {
        val state = _uiState.value
        val count = state.routeSteps.size
        if (count == 0) return
        val current = state.analysedStepIndex
        val next = if (current == null) 0 else current + 1
        if (next > count - 1) return
        analyseStep(next)
    }

    /**
     * The step navigator's back control (spec: `route-analysis` — Step navigator): the
     * previous step; with nothing analysed, or on the first step, it is a no-op.
     */
    fun analysePreviousStep() {
        val current = _uiState.value.analysedStepIndex ?: return
        if (current <= 0) return
        analyseStep(current - 1)
    }

    /**
     * The step a fresh route starts on: the first one the native layer gave a distance or a
     * time for. The route's start line ("Start: …  []") carries neither, and presenting it
     * left the min overlay's instruction detail empty (owner finding, 2026-10-03).
     */
    private fun currentStepOf(steps: List<RouteStepDisplay>): Int? =
        steps.indexOfFirst { it.distanceText.isNotEmpty() || it.timeText.isNotEmpty() }
            .takeIf { it >= 0 }
            ?: steps.indices.firstOrNull()

    /**
     * The polyline vertex range the step at [index] owns, or null when there is no
     * route or no locatable position for that step.
     */
    private fun segmentOf(state: RoutePanelUiState, index: Int): IntRange? {
        val route = state.routeEntry ?: return null
        val range = stepSegments(
            polylineLats = route.latitudes ?: return null,
            polylineLons = route.longitudes ?: return null,
            stepCount = state.routeSteps.size,
            anchors = state.stepAnchors
        ).getOrNull(index) ?: return null
        return if (range.isEmpty()) null else range
    }

    /**
     * Open a session on the map (spec: `route-planning-session`). A session that is
     * already active keeps its state; opened during navigation it reviews the active
     * route read-only.
     */
    fun openSession() {
        if (_sessionState.value != RouteSessionState.INACTIVE) return
        // A new session lands compact (spec: `route-planning-session` — anchors): the map
        // stays usable, and the overlay can be expanded or minimized from there.
        setOverlayAnchor(RouteOverlayAnchor.COMPACT)
        _sessionState.value = when {
            _uiState.value.isNavigating -> RouteSessionState.REVIEWING
            _uiState.value.routeState == RouteState.Done -> RouteSessionState.REVIEWING
            else -> RouteSessionState.EDITING
        }
    }

    /**
     * End the session as a cancel (spec: `route-planning-session` — the only exits are
     * Start Navigation and Cancel/End): the route leaves the map and the session state
     * resets. Ending a review of an active navigation does not touch the route.
     */
    fun endSession() {
        if (_sessionState.value == RouteSessionState.INACTIVE) return
        cancelGracePeriod()
        if (!_uiState.value.isNavigating) clearRoute()
        _sessionState.value = RouteSessionState.INACTIVE
        // No session means no overlay claim on the canvas.
        setOverlayAnchor(RouteOverlayAnchor.HIDDEN)
    }

    /**
     * Navigation started on the session's route: the session ends, but the route stays
     * drawn because navigation owns it (spec: `route-planning-session` — Start
     * Navigation ends the session and hands the map to navigation).
     */
    fun onNavigationStarted() {
        cancelGracePeriod()
        _uiState.value = _uiState.value.copy(isNavigating = true)
        _analysedAnchor.value = null
        // Navigation started: the analysis is over with the session (spec: `route-analysis`).
        _analysedSegmentRange.value = null
        _sessionState.value = RouteSessionState.INACTIVE
    }

    /**
     * Navigation was stopped: the session enters its stopped state, where the route
     * stays on the map for the grace period (spec: `route-planning-session` — Grace
     * period after navigation is stopped).
     */
    fun onNavigationStopped() {
        _uiState.value = _uiState.value.copy(isNavigating = false)
        if (_sessionState.value != RouteSessionState.INACTIVE) startGracePeriod()
    }

    /**
     * Navigation was restarted from the stopped state (spec: `route-planning-session`
     * — Restart resumes navigation): the grace deadline is dropped and the session
     * hands the route back to navigation without recalculating.
     */
    fun onNavigationRestarted() {
        cancelGracePeriod()
        onNavigationStarted()
    }

    /**
     * The grace period: one deadline in the session's scope. Expiry ends the session
     * (spec: `route-planning-session` — Grace expiry clears the route), and it is
     * idempotent — a session that already left the stopped state is left alone, so a
     * late tick cannot clear a route the user has started driving on.
     */
    private fun startGracePeriod() {
        _sessionState.value = RouteSessionState.STOPPED
        cancelGracePeriod()
        graceJob = viewModelScope.launch {
            delay(GRACE_PERIOD_MS)
            if (_sessionState.value == RouteSessionState.STOPPED) {
                Log.d(TAG, "session grace period expired - ending the session")
                endSession()
            }
        }
    }

    private fun cancelGracePeriod() {
        graceJob?.cancel()
        graceJob = null
    }

    /**
     * Mark navigation as active/inactive (suppresses summary dialog on reroute). The
     * session follows it: starting navigation ends the session and leaves the route to
     * navigation, stopping it moves an open session into its stopped state.
     */
    fun setNavigating(navigating: Boolean) {
        if (navigating) onNavigationStarted() else onNavigationStopped()
    }

    /**
     * Adopt a route the navigation engine acquired for itself — a reroute with no
     * surface participation, or a car-only start. The panel's route view then
     * reflects the session's route and the map draws the new geometry instead of
     * the previous one (spec: `navigation-controller` — Reroute handling).
     *
     * [RouteResult] start/destination are the polyline's first and last point, the
     * geometry the overview fit uses; the destination identity stays in the panel's
     * own state.
     */
    fun adoptRoute(route: RouteEntry, vehicle: Vehicle) {
        val steps = route.descriptions
            ?.filter { !it.startsWith("---") }
            ?.map { desc -> parseStepDisplay(desc) }
            ?: emptyList()
        _uiState.value = _uiState.value.copy(
            routeState = RouteState.Done,
            routeEntry = route,
            routeSteps = steps,
            stepAnchors = instructionAnchors(route),
            // A new route starts at its current step (owner finding, 2026-10-03: the min
            // overlay showed no usable entry before the first paging). The camera stays with
            // the overview fit — the step is the *current* one, not a camera request: only an
            // explicit step selection (`analyseStep`) moves the camera.
            analysedStepIndex = currentStepOf(steps),
            analysedSegment = segmentOf(
                _uiState.value.copy(
                    routeEntry = route,
                    routeSteps = steps,
                    stepAnchors = instructionAnchors(route)
                ),
                currentStepOf(steps) ?: 0
            ),
            vehicle = vehicle,
            error = null,
            preciseLocationRequired = false
        )
        _analysedAnchor.value = null
            _analysedSegmentRange.value = _uiState.value.analysedSegment
        _routeVisible.value = true
        val lats = route.latitudes
        val lons = route.longitudes
        if (lats.size >= 2 && lats.size == lons.size) {
            _routeResultFlow.value = RouteResult(
                routeLats = lats,
                routeLons = lons,
                startLat = lats.first(), startLon = lons.first(),
                destLat = lats.last(), destLon = lons.last()
            )
        }
    }

    fun clearRoute() {
        // The overlay's anchor survives a clear: the session stays open with the overlay
        // where the user put it.
        val anchor = _uiState.value.overlayAnchor
        _uiState.value = RoutePanelUiState(
            gpsAvailable = _uiState.value.gpsAvailable,
            overlayAnchor = anchor
        )
        _analysedAnchor.value = null
        _analysedSegmentRange.value = _uiState.value.analysedSegment
        // Clearing the route keeps the session open for a new plan (spec: route-panel-ui
        // — Clear route resets the panel state).
        if (_sessionState.value == RouteSessionState.REVIEWING) {
            _sessionState.value = RouteSessionState.EDITING
        }
        _routeResultFlow.value = null
        _routeVisible.value = true
        _clearRouteSignal.value = _clearRouteSignal.value + 1
    }

    /**
     * Hide the route from the map without resetting the panel state
     * (spec: stop-navigation-hides-route). The route stays available in the
     * panel (start/dest/vehicle/summary preserved) and can be shown again via
     * [showRouteOnMap].
     */
    fun clearRouteFromMap() {
        _routeVisible.value = false
        _clearRouteSignal.value = _clearRouteSignal.value + 1
    }

    /** Make the route visible on the map again (restart navigation). */
    fun showRouteOnMap() {
        _routeVisible.value = true
    }

    companion object {
        private const val TAG = "RoutePanelVM"

        /**
         * How long a stopped session keeps its route on the map before it ends itself
         * (spec: `route-planning-session` — Grace period after navigation is stopped).
         * Long enough for "restart that", short enough that no route lingers.
         */
        const val GRACE_PERIOD_MS = 45_000L
    }
}

/**
 * Parse a native description line into a [RouteStepDisplay].
 *
 * Native format: `Turn left into Main Street  [1.2 km, 5 min]`. Below a minute the native side
 * writes seconds (`45 s`) — before that, every step of a city route read `0 min` (owner finding,
 * 2026-10-03) — and a segment under a second writes no time at all. The route's start line
 * carries an empty bracket (`Start: …  []`): it has neither distance nor time, so the current
 * step is the first line that has one (see `currentStepOf`).
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
