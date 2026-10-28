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
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.maxSpeedKmhFor
import com.naviveylin.location.LocationService
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.FailureReason
import com.naviveylin.core.LocationGrant
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.stringResolver
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.core.navigationFailureText
import com.naviveylin.navigation.navigationFailureOf
import com.naviveylin.core.search.ClientSearchRequests
import com.naviveylin.core.search.SearchGate
import com.naviveylin.core.search.SearchGateState
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.core.search.SearchSlots
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

    /**
     * A route calculation is in flight. [percent] is the routing engine's progress through its
     * search, 0..99, and null until it has reported one — 0 is a legal value, so it must not
     * double as "unknown" (spec: `route-panel-ui` — Progress percentage during calculation).
     */
    data class Calculating(val percent: Int? = null) : RouteState
    data object Done : RouteState
    data class Error(val failure: NavigationFailure) : RouteState
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
 * overlay anchors (max and min)). There are two anchors: the card is the phone's whole
 * session surface, and a session that should free the map is ended, not hidden.
 */
enum class RouteOverlayAnchor { EXPANDED, COMPACT }

/**
 * One row of the route panel's step list, as the panel renders it.
 *
 * The row is derived from the route's structured steps (spec: `route-analysis` — Rows are derived
 * from the route's steps) and its text is localized from the manoeuvre's own values (spec:
 * `turn-instruction-localization` — Route analysis rows are localized from the step model).
 *
 * [distanceMeters] and [durationSeconds] are the run from the previous row's manoeuvre to this one,
 * folded over every step the row absorbed (spec: `osmscout-jni` — A calculated route publishes
 * structured route steps), formatted by the app with the shared formatters. Zero means "unknown":
 * the route's start line owns a zero-length leg, and a row without values shows none - never a
 * neighbour's and never the last geometry edge before its manoeuvre (owner finding, 2026-10-04:
 * "14 m" and "2 s" for one step of a 17,3 km route).
 *
 * [turnType], [kind] and [roundaboutExit] are the manoeuvre's own fields: the icon is chosen from
 * them, never from the row's text.
 */
data class RouteStepDisplay(
    val instruction: String,
    val distanceMeters: Double = 0.0,   // leg distance in metres, 0 = unknown
    val durationSeconds: Double = 0.0,  // leg travel time in seconds, 0 = unknown
    val turnType: com.framstag.libosmscout.client.TurnType =
        com.framstag.libosmscout.client.TurnType.STRAIGHT_ON,
    val kind: com.framstag.libosmscout.client.RouteStep.Kind =
        com.framstag.libosmscout.client.RouteStep.Kind.UNKNOWN,
    val roundaboutExit: Int? = null
) {
    /** Whether this row carries leg values at all. */
    val hasLegValues: Boolean
        get() = distanceMeters > 0.0 || durationSeconds > 0.0
}

data class RoutePanelUiState(
    val startLocation: LocationEntry? = null,
    val destLocation: LocationEntry? = null,
    val vehicle: Vehicle = Vehicle.CAR,
    val routeState: RouteState = RouteState.Idle,
    val routeEntry: RouteEntry? = null,
    val routeSteps: List<RouteStepDisplay> = emptyList(),
    val error: String? = null,
    /**
     * The failure behind the last route request, as the typed value the panel words and acts on
     * (spec: `routing-failure-reporting` — A failed route calculation reports a reason). [error] is
     * its rendered form and is being retired in its favour. Null while no failure is set.
     */
    val routeFailure: NavigationFailure? = null,
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
     * anchors (max and min)). Reported by the overlay as the user collapses or expands it,
     * and read by the map for the fit's free area. Defaults to the anchor a session opens
     * in; the card reports its real height as soon as it is composed.
     */
    val overlayAnchor: RouteOverlayAnchor = RouteOverlayAnchor.COMPACT,
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
    // Read, not watched: the vehicle's usual maximum speed is resolved per calculation, so a slider
    // change applies to the next one and never restarts a calculation in flight
    // (spec: `routing-max-speed` — A speed change does not restart a calculation).
    private val settingsStorage: SettingsStorage,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutePanelUiState())
    val uiState: StateFlow<RoutePanelUiState> = _uiState.asStateFlow()

    /**
     * The bridge, for the result rows' type artwork and address lookup — the picker shows the
     * same row as the search dialog (change `search-result-row-content`). Both lookups are
     * native: the rows call them off the main thread through the shared helpers.
     */
    internal val osmscoutClient: OSMScoutClient get() = client

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
    private val _routeErrorEvent = MutableSharedFlow<NavigationFailure>(extraBufferCapacity = 1)
    val routeErrorEvent: SharedFlow<NavigationFailure> = _routeErrorEvent.asSharedFlow()

    /**
     * The route panel's search request lifecycle: debounce, the minimum query length, supersede and
     * abandon (spec: `search-request-lifecycle`). The route field's search runs in its own slot, so a
     * places or car search never stops it and it never stops theirs.
     */
    private val searchGate = SearchGate(
        scope = viewModelScope,
        requests = ClientSearchRequests(client),
        slot = SearchSlots.ROUTE,
        requestDispatcher = { defaultDispatcher },
        search = { query, generation -> searchOnce(query, generation) }
    )

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
        // The gate's state becomes this panel's search state, so the debounce, the minimum length,
        // supersede and abandon are the shared rule rather than a per-surface copy of it.
        viewModelScope.launch {
            searchGate.state.collect { state -> onSearchStateChanged(state) }
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
        // Switching the active field abandons the running search: the field is cleared, so nobody
        // wants its result any more (spec: `search-request-lifecycle` — abandon without a successor).
        searchGate.abandon()
        _uiState.value = _uiState.value.copy(
            activeField = field,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        // The gate owns the debounce, the minimum length and the abandon: a query cleared or
        // shortened below the minimum stops the request outright.
        searchGate.request(query)
    }

    /** Maps the gate's state onto the panel's search state. */
    private fun onSearchStateChanged(state: SearchGateState<RouteSearchResult>) {
        when (state) {
            is SearchGateState.Idle -> Unit
            is SearchGateState.Searching ->
                _uiState.value = _uiState.value.copy(isSearching = true)
            is SearchGateState.Cleared ->
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            is SearchGateState.Completed ->
                _uiState.value = _uiState.value.copy(
                    searchResults = state.results.ranked,
                    isSearching = false,
                    searchReference = state.results.reference
                )
        }
    }

    /** One route-field search's outcome: the ranked list and the reference it was ranked against. */
    private data class RouteSearchResult(
        val ranked: List<LocationEntry>,
        val reference: SearchReference?
    )

    /**
     * The one search of [generation]'s request. The generation is what makes it abortable: a
     * superseded or abandoned route search stops at the native walk's next check.
     */
    private suspend fun searchOnce(query: String, generation: Long): RouteSearchResult {
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
                    OSMScoutClient.NO_ADMIN_REGION,
                    generation
                )?.toList() ?: emptyList()
                SearchResultRanker.rankForDisplay(raw, criteria, reference)
            } catch (e: Exception) {
                Log.e(TAG, "searchLocations failed", e)
                emptyList()
            }
        }
        return RouteSearchResult(results, reference)
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
            // While navigation is active the route belongs to navigation: opening the session or
            // touching a field must not wipe the route the map is drawing and the review is
            // showing (spec: `route-planning-session` — Reviewing a route during navigation is
            // read-only; the route belongs to navigation).
            if (s.isNavigating) return
            _uiState.value = s.copy(
                routeState = RouteState.Idle,
                routeEntry = null,
                routeSteps = emptyList(),
                error = null,
                routeFailure = null
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
            // A reason of its own, worded by the surface from the shared resource (spec:
            // `routing-failure-reporting` — Each reason is worded by the surface): the panel shows it
            // and the upgrade dialog offers the fix.
            val failure = NavigationFailure(
                reason = FailureReason.PRECISE_LOCATION_REQUIRED,
                origin = SurfaceOrigin.ENGINE
            )
            Log.w(TAG, "calculateRoute: refused, precise location not granted")
            _uiState.value = s.copy(
                routeState = RouteState.Error(failure),
                error = failureText(failure),
                routeFailure = failure,
                preciseLocationRequired = true
            )
            _routeErrorEvent.tryEmit(failure)
            return
        }

        _uiState.value = s.copy(
            routeState = RouteState.Calculating(),
            error = null,
            routeFailure = null,
            preciseLocationRequired = false
        )

        viewModelScope.launch {
            withContext(defaultDispatcher) {
                // The selected vehicle's own stored maximum speed: the settings file is the single
                // source of truth, so a change the user just made is what this calculation uses
                // (spec: `routing-max-speed` — Every calculation path carries the vehicle's maximum
                // speed).
                val profile = RoutingProfile(
                    s.vehicle,
                    settingsStorage.loadOnCallerDispatcher().maxSpeedKmhFor(s.vehicle)
                )
                client.calculateRouteWithProfile(
                    start.lat, start.lon, dest.lat, dest.lon, profile,
                    object : RouteCallback {
                        override fun onProgress(percent: Int) {
                            // Called from the routing thread (rate-limited in native, see
                            // JavaRoutingProgress): the state write is marshalled into the view
                            // model's scope, and only a changed percentage is published — the
                            // panel recomposes for it and nothing else would change
                            // (spec: `route-panel-ui` — Progress percentage during calculation).
                            viewModelScope.launch {
                                val current = _uiState.value.routeState
                                if (current !is RouteState.Calculating) return@launch
                                val capped = percent.coerceIn(0, 99)
                                if (current.percent == capped) return@launch
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Calculating(capped)
                                )
                            }
                        }

                        override fun onSuccess(route: RouteEntry) {
                            viewModelScope.launch {
                                val rows = buildRows(route)
                                val steps = rows.map { row -> row.toDisplay() }
                                val anchors = rowAnchors(rows)
                                logStepValues(route, rows, steps)

                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Done,
                                    routeEntry = route,
                                    routeSteps = steps,
                                    stepAnchors = anchors,
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
                                            stepAnchors = anchors
                                        ),
                                        currentStepOf(steps) ?: 0
                                    ),
                                    error = null,
                                    routeFailure = null,
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
                                // The result is always published: absent or too few polyline
                                // coordinates become an empty polyline, and the requested endpoints
                                // carry the camera fallback (spec: `route-map-overview` — Empty
                                // polyline falls back to endpoints; Degenerate route geometry
                                // degrades safely).
                                _routeResultFlow.value = calculatedRouteGeometry(
                                    route,
                                    startLat = start.lat, startLon = start.lon,
                                    destLat = dest.lat, destLon = dest.lon
                                )
                                _routeCalculatedEvent.tryEmit(Unit)
                            }
                        }

                        /**
                         * The routing layer named the cause (spec: `routing-failure-reporting` — A
                         * failed route calculation reports a reason): the kind becomes the failure the
                         * panel words and acts on.
                         */
                        override fun onErrorDetail(
                            kind: Int,
                            detail: String,
                            nearestRoadDistanceMeters: Double
                        ) {
                            viewModelScope.launch {
                                val failure = navigationFailureOf(
                                    kind = kind,
                                    detail = detail,
                                    nearestRoadDistanceMeters = nearestRoadDistanceMeters
                                )
                                Log.e(TAG, "Route calculation failed: reason=${failure.reason} detail=$detail")
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Error(failure),
                                    error = failureText(failure),
                                    routeFailure = failure
                                )
                                _routeErrorEvent.tryEmit(failure)
                            }
                        }

                        /**
                         * The fallback of the callback interface: reached only by a native library
                         * that calls the plain failure callback (see
                         * `RouteCallback.onErrorDetail`). The cause is then unknown, which is what
                         * the failure says.
                         */
                        override fun onError(message: String) {
                            onErrorDetail(RouteCallback.KIND_UNKNOWN, message, Double.NaN)
                        }

                        override fun onCancel() {
                            viewModelScope.launch {
                                _uiState.value = _uiState.value.copy(
                                    routeState = RouteState.Idle, error = null, routeFailure = null
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    /**
     * The text the panel shows for [failure]: the shared wording for the reason, with the failure's
     * nearest-road distance in the surface's own units and the device's number format (spec:
     * `routing-failure-reporting` — Each reason is worded by the surface).
     */
    private fun failureText(failure: NavigationFailure): String =
        navigationFailureText(
            context = context,
            failure = failure,
            unitMetersRes = R.string.distance_unit_m,
            unitKilometersRes = R.string.distance_unit_km
        )

    /** Dismiss the phone's precise-location upgrade dialog. */
    fun dismissPreciseLocationRequirement() {
        if (_uiState.value.preciseLocationRequired) {
            _uiState.value = _uiState.value.copy(preciseLocationRequired = false)
        }
    }

    fun cancelRoute() {
        client.cancelRoute()
        _uiState.value = _uiState.value.copy(
            routeState = RouteState.Idle, error = null, routeFailure = null
        )
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
        endAnalysis()
    }

    /**
     * End the analysis: the analysed step and its segment in the panel's state, the range the map's camera
     * fit reacts to, and the anchor that asks it to move. One place ends an analysis, so a session exit
     * cannot clear half of it: the exits that leave a route on the map — Start Navigation and End while
     * navigation runs — come through here, and `clearRoute()` resets the same three with the whole state
     * (spec: `route-analysis` — the analysis lives exactly as long as the session that owns it).
     */
    private fun endAnalysis() {
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
     * The step a fresh route starts on: the first one the native layer gave values for. The route's
     * start line owns a zero-length leg and carries no values, and presenting it left the min
     * overlay's instruction detail empty (owner finding, 2026-10-03).
     */
    private fun currentStepOf(steps: List<RouteStepDisplay>): Int? =
        steps.indexOfFirst { it.hasLegValues }
            .takeIf { it >= 0 }
            ?: steps.indices.firstOrNull()

    /**
     * The route's rows for display: derived from the route's structured steps (spec: `route-analysis`
     * — Rows are derived from the route's steps) and worded from the manoeuvre's own values in the
     * device language (spec: `turn-instruction-localization` — Route analysis rows are localized from
     * the step model).
     *
     * A route that carries no steps yields no rows at all — never rows invented from a description
     * line, and never text parsed back into structure. The full step list stays on
     * [RoutePanelUiState.routeEntry]: the rows are a view of it, not a replacement.
     */
    private fun buildSteps(route: RouteEntry): List<RouteStepDisplay> =
        buildRows(route).map { row -> row.toDisplay() }

    /** The derived rows of [route] (spec: `route-analysis` — Rows are derived from the route's steps). */
    private fun buildRows(route: RouteEntry): List<RouteRow> {
        val steps = route.steps ?: return emptyList()
        return routeRows(steps.toList())
    }

    /** One row as the panel renders it: localized text, folded values, the manoeuvre's own fields. */
    private fun RouteRow.toDisplay(): RouteStepDisplay {
        val resolver = context.stringResolver()
        return RouteStepDisplay(
            instruction = ManoeuvreLabels.description(resolver, manoeuvre),
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            turnType = rowTurnType(manoeuvre),
            kind = manoeuvre.kind,
            roundaboutExit = manoeuvre.roundaboutExit
        )
    }

    /**
     * Record what the route's rows add up to against the route's own totals, and the census that
     * settles the row policy, as numbers only (spec: `route-analysis` — Step values describe the
     * step's own leg; spec: `auto-diagnostics` — Diagnostics carry no coordinates).
     *
     * This is the measurement the change's device check reads: the per-row distances are the legs the
     * analysis highlights, so they have to add up to the route's distance. A bridge that measures
     * between route nodes instead of between instructions falls short by orders of magnitude, which no
     * screenshot would show.
     */
    private fun logStepValues(route: RouteEntry, rows: List<RouteRow>, displays: List<RouteStepDisplay>) {
        val steps = route.steps ?: return
        DiagnosticsLog.log(
            DiagnosticsLog.ROUTE_TAG,
            routeCensus(steps.toList(), rows)
        )
        if (displays.none { it.hasLegValues }) return
        DiagnosticsLog.log(
            DiagnosticsLog.ROUTE_TAG,
            stepValuesSummary(displays, route.distance, route.duration)
        )
        if (stepValuesDiverge(displays, route.distance)) {
            Log.w(
                TAG,
                "route analysis: row distances sum to ${displays.sumOf { it.distanceMeters }.toInt()} m " +
                    "but the route totals ${route.distance.toInt()} m (steps=${displays.size})"
            )
        }
    }

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
        if (!_uiState.value.isNavigating) clearRoute() else endAnalysis()
        _sessionState.value = RouteSessionState.INACTIVE
        // The surface closes with the session (spec: `route-planning-session` — Ending the
        // session removes its surface): the card's host closes it, and the next session
        // opens in the compact anchor (`openSession`).
    }

    /**
     * Navigation started on the session's route: the session ends, but the route stays
     * drawn because navigation owns it (spec: `route-planning-session` — Start
     * Navigation ends the session and hands the map to navigation).
     */
    fun onNavigationStarted() {
        cancelGracePeriod()
        _uiState.value = _uiState.value.copy(isNavigating = true)
        // Navigation started: the analysis is over with the session (spec: `route-analysis` — the analysis
        // lives exactly as long as its session). The analysed step, its camera anchor and its highlighted
        // segment all go; the route stays, because navigation owns it from here on
        // (spec: `route-planning-session` — Start navigation ends the session's analysis).
        endAnalysis()
        _sessionState.value = RouteSessionState.INACTIVE
    }

    /**
     * Navigation was stopped: an open session enters its stopped state, where the route
     * stays on the map for the grace period (spec: `route-planning-session` — Grace
     * period after navigation is stopped).
     *
     * Returns whether an open session took the route over for that grace window. The
     * caller that owns the map's route visibility must keep the route drawn when it did,
     * and clear it when no session was open (spec: `map-modes` — navigation end).
     */
    fun onNavigationStopped(): Boolean {
        _uiState.value = _uiState.value.copy(isNavigating = false)
        if (_sessionState.value == RouteSessionState.INACTIVE) return false
        startGracePeriod()
        return true
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
        val rows = buildRows(route)
        val steps = rows.map { row -> row.toDisplay() }
        val anchors = rowAnchors(rows)
        logStepValues(route, rows, steps)
        // A new route starts at its current step (owner finding, 2026-10-03: the min
        // overlay showed no usable entry before the first paging). The camera stays with
        // the overview fit — the step is the *current* one, not a camera request: only an
        // explicit step selection (`analyseStep`) moves the camera.
        //
        // An adoption is not a selection: with no session open nothing asked for an analysis, and a route
        // navigation owns carries none — otherwise every reroute re-lights the highlight the session's exit
        // ended (spec: `route-analysis` — an analysis is created only by the selector that owns it). The
        // route itself is adopted either way, and a selection from the previous route never survives the
        // swap, because its step indices do not address this one.
        val analysedIndex =
            if (_sessionState.value == RouteSessionState.INACTIVE) null else currentStepOf(steps)
        val withRoute = _uiState.value.copy(routeEntry = route, routeSteps = steps, stepAnchors = anchors)
        val analysedSegment = analysedIndex?.let { segmentOf(withRoute, it) }
        _uiState.value = withRoute.copy(
            routeState = RouteState.Done,
            analysedStepIndex = analysedIndex,
            analysedSegment = analysedSegment,
            vehicle = vehicle,
            error = null,
            routeFailure = null,
            preciseLocationRequired = false
        )
        _analysedAnchor.value = null
        _analysedSegmentRange.value = analysedSegment
        _routeVisible.value = true
        // A route without usable geometry is adopted all the same: everything above is the
        // session, and no geometry is published, so the map keeps what it displayed
        // (spec: `route-map-overview` — Adoption of a route without geometry keeps the session;
        // spec: `navigation-engine` — Acquisition without usable polyline geometry).
        adoptedRouteGeometry(route)?.let { _routeResultFlow.value = it }
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
