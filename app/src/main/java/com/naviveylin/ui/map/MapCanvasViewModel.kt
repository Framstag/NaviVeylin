package com.naviveylin.ui.map

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.RenderMode
import com.naviveylin.data.SearchHistoryEntry
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportState
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationService
import com.naviveylin.ui.route.RoutePanelViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class GpsFixQuality {
    NONE,
    POOR,
    GOOD
}

data class MapCanvasUiState(
    val viewport: ViewportState = ViewportState(),
    val renderedBitmap: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<LocationEntry> = emptyList(),
    val isSearching: Boolean = false,
    /** Name of the resolved admin region scoping the search, null when unscoped. */
    val searchAdminRegionName: String? = null,
    val selectedLocation: LocationEntry? = null,
    val objectDescription: ObjectDescription? = null,
    val isLongPress: Boolean = false,
    val showDetailsSheet: Boolean = false,
    val showFavoritesSheet: Boolean = false,
    val showRoutePanel: Boolean = false,
    val routeStartLocation: LocationEntry? = null,
    val routeDestLocation: LocationEntry? = null,
    val snackbarMessage: String? = null,
    val canvasOverrun: Double = MapRenderer.DEFAULT_CANVAS_OVERRUN,
    val followMode: Boolean = false,
    val autoZoomEnabled: Boolean = true,
    val freeFormNorthUp: Boolean = true,
    val navNorthUp: Boolean = false,
    val keepScreenOn: Boolean = true,
    val darkModePreference: DarkModePreference = DarkModePreference.AUTOMATIC,
    val isDarkPresentation: Boolean = false,
    val gpsFixQuality: GpsFixQuality = GpsFixQuality.NONE,
    val laneHintsEnabled: Boolean = true,
    val renderMode: RenderMode = RenderMode.TILES,
    /** Last GPS location for marker overlay; null if unavailable. */
    val gpsLocation: android.location.Location? = null,
    /** Viewport that produced the currently visible bitmap. Marker overlay must use this. */
    val renderViewport: MapRenderer.RenderViewport? = null,
    /** Marker position for the overlay: nav-filtered in routing mode, raw GPS otherwise; NaN when unavailable. */
    val gpsMarkerLat: Double = Double.NaN,
    val gpsMarkerLon: Double = Double.NaN,
    /** Marker arrow bearing in degrees (freshest direction signal); < 0 = north-up arrow. */
    val gpsMarkerBearing: Double = Double.NaN,
    /** GPS horizontal accuracy in meters for the accuracy circle; <= 0 = no circle. */
    val gpsMarkerAccuracy: Double = 0.0
)

@HiltViewModel
class MapCanvasViewModel @Inject constructor(
    private val viewportStorage: ViewportStorage,
    private val settingsStorage: SettingsStorage,
    private val assetCopier: AssetCopier,
    private val client: OSMScoutClient,
    private val favoriteRepository: FavoriteRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val locationService: LocationService,
    private val darkModeController: DarkModeController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapCanvasUiState())
    val uiState: StateFlow<MapCanvasUiState> = _uiState.asStateFlow()

    /** Search history, youngest first. Loaded from disk on ViewModel init. */
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = searchHistoryRepository.history

    private val _gpsFixQuality = MutableStateFlow(GpsFixQuality.NONE)
    val gpsFixQuality: StateFlow<GpsFixQuality> = _gpsFixQuality.asStateFlow()

    // Admin-region scoping for search: resolved region handle + the position
    // it was resolved at (NaN = none). Reused across keystrokes of a search
    // session and re-resolved only after significant movement.
    private var searchAdminRegionHandle: Long = 0L
    private var searchAdminRegionLat: Double = Double.NaN
    private var searchAdminRegionLon: Double = Double.NaN
    private var searchAdminRegionName: String? = null

    /** Whether the search panel is currently open (drives eager region resolution). */
    private var searchPanelOpen: Boolean = false

    private var mapRenderer: MapRenderer? = null
    private var rendererScope: CoroutineScope? = null
    private var currentMapKey: String? = null
    private val epoch = AtomicLong(0)

    // Screen dimensions for zoom computation
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // Route panel view model — set via setRoutePanelViewModel() from screen
    private var _routePanelViewModel: RoutePanelViewModel? = null

    // Navigation view model — set via setNavigationViewModel() from screen
    private var _navigationViewModel: com.naviveylin.navigation.NavigationViewModel? = null

    // Navigation position for follow mode (from NavigationViewModel.positionFlow)
    private var _navPosition: com.framstag.libosmscout.client.NavigationPosition? = null

    // GPS follow throttle state
    private var lastFollowRenderMs: Long = 0L
    // Non-follow marker render tracking (marker moves > 5 m trigger a render)
    private var lastMarkerLat = Double.NaN
    private var lastMarkerLon = Double.NaN
    private var lastNonFollowMarkerRenderMs: Long = 0L
    private var lastGpsLat = Double.NaN
    private var lastGpsLon = Double.NaN
    private var lastGpsBearing = Double.NaN
    private var lastGpsTime: Long = 0L
    private var smoothedCenterLat = Double.NaN
    private var smoothedCenterLon = Double.NaN
    // No center smoothing in follow mode: the viewport center must stay on the
    // raw GPS fix so the marker does not drift away from the road/track. Only
    // large jumps (>500 m) reset directly to avoid lag after teleports.
    private val centerSmoothAlpha = 1.0
    private val centerSmoothMaxJumpM = 500.0

    // Auto-zoom state
    private var lastValidSpeedKmH: Double = 20.0
    private var autoZoomSuspended: Boolean = false
    private var lastSpeedBandIndex: Int = -1
    private var currentTargetMag: Double = 15.0

    // Zoom-change throttling to avoid visible "pumping" when speed or turn
    // geometry causes rapid successive magnification changes.
    private var lastAutoZoomCommitMs: Long = 0L
    private var pendingZoomTarget: Double = 15.0
    private var zoomTargetStableSamples: Int = 0
    private val ZOOM_COOLDOWN_MS = 2500L
    private val ZOOM_COMMIT_SAMPLES = 3
    private val ZOOM_HYSTERESIS_MAG = 1.0 // only commit when target differs by at least one full level

    // Course-over-ground state: derive map rotation and marker bearing from the
    // recent center track instead of the instantaneous GPS bearing, which is jumpy
    // on this GPX replay. A short low-pass filter keeps the heading stable on
    // straight segments while still following curves.
    private val COURSE_HISTORY_SIZE = 10
    private val courseLats = DoubleArray(COURSE_HISTORY_SIZE) { Double.NaN }
    private val courseLons = DoubleArray(COURSE_HISTORY_SIZE) { Double.NaN }
    private var courseIndex = 0
    private val MIN_COURSE_DISTANCE_M = 40.0
    /** Short distance used right after a reset/start so the new direction is established quickly. */
    private val MIN_COURSE_DISTANCE_FAST_M = 10.0
    /** Minimum segment length before it may trigger a turn reset (avoids GPS-noise resets). */
    private val MIN_SEGMENT_FOR_TURN_M = 8.0
    private val COURSE_TURN_RESET_DEG = 45.0
    private var lastCourseBearing = Double.NaN
    private var lastSmoothedBearing = Double.NaN
    private var lastUsedBearing = Double.NaN
    private var lastUsedAngle = Double.NaN
    /** Bearing of the most recent two-point segment; freshest direction signal for the marker. */
    private var lastSegmentBearing = Double.NaN
    private val COURSE_LOW_PASS_ALPHA = 0.3
    /** Faster low-pass while the course history is not yet stable (after reset/start). */
    private val COURSE_LOW_PASS_ALPHA_FAST = 0.7
    /** True once at least MIN_COURSE_DISTANCE_M of track is in the history. */
    private var courseStable = false
    private val MIN_BEARING_DELTA_DEG = 2.0
    // Per-render rotation limit. Renders run at the GPS fix cadence (~1/s), so
    // 90°/frame lets a sharp 90° turn complete in 1-2 frames instead of slowly
    // crawling (30°/frame took ~13 s for a 92° turn in the replay).
    private val MAX_ANGLE_RATE_DEG_PER_RENDER = 90.0

    // Turn tracking: distance past the last turn waypoint (negative = approaching, positive = past)
    private var turnPassedDistance: Double = Double.NaN

    // Debug log sampling counter
    private var logCount: Int = 0

    // Route geometry for curve detection
    private var routeLats: DoubleArray? = null
    private var routeLons: DoubleArray? = null

    /** Get the current navigation position for marker rendering. */
    fun getNavigationPosition(): com.framstag.libosmscout.client.NavigationPosition? = _navPosition

    /** Filter speed spikes: reject speed > 150 km/h, use last good speed. */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0 && rawSpeedKmH <= 150.0) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    /** Compute turn zoom boost floor: 16.0 if ≤ 2000m, 15.0 if ≤ 5000m, 0.0 otherwise. */
    private fun computeTurnBoost(turnDistanceMeters: Double): Double {
        if (turnDistanceMeters < 0 || turnDistanceMeters.isNaN()) return 0.0
        return when {
            turnDistanceMeters <= 2000.0 -> 16.0
            turnDistanceMeters <= 5000.0 -> 15.0
            else -> 0.0
        }
    }

    /**
     * Find nearest strong curve in route geometry ahead of current position.
     * Returns distance in meters to the curve, or NaN if none found within lookAhead.
     */
    private fun findNearestCurve(lat: Double, lon: Double, lookAheadMeters: Double = 500.0): Double {
        val rLats = routeLats ?: return Double.NaN
        val rLons = routeLons ?: return Double.NaN
        if (rLats.size < 3) return Double.NaN

        // Find closest point on route to current position
        var closestIdx = 0
        var closestDist = Double.MAX_VALUE
        for (i in rLats.indices) {
            val dlat = lat - rLats[i]
            val dlon = lon - rLons[i]
            val dist = dlat * dlat + dlon * dlon
            if (dist < closestDist) {
                closestDist = dist
                closestIdx = i
            }
        }

        // Look ahead for bearing changes > 30 degrees
        var accumulatedDist = 0.0
        for (i in closestIdx until rLats.size - 2) {
            val lat1 = rLats[i]
            val lon1 = rLons[i]
            val lat2 = rLats[i + 1]
            val lon2 = rLons[i + 1]
            val lat3 = rLats[i + 2]
            val lon3 = rLons[i + 2]

            val segDist = haversine(lat1, lon1, lat2, lon2)
            accumulatedDist += segDist
            if (accumulatedDist > lookAheadMeters) break

            val bearing1 = Math.toDegrees(Math.atan2(
                Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2)),
                Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1))
            ))

            val bearing2 = Math.toDegrees(Math.atan2(
                Math.sin(Math.toRadians(lon3 - lon2)) * Math.cos(Math.toRadians(lat3)),
                Math.cos(Math.toRadians(lat2)) * Math.sin(Math.toRadians(lat3)) -
                Math.sin(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lat3)) * Math.cos(Math.toRadians(lon3 - lon2))
            ))

            var delta = Math.abs(bearing1 - bearing2)
            if (delta > 180) delta = 360 - delta

            if (delta > 30.0) {
                return accumulatedDist
            }
        }

        return Double.NaN
    }

    /** Haversine distance between two coordinates in meters. */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** Toggle keep-screen-on during navigation. */
    fun onToggleKeepScreenOn(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(keepScreenOn = enabled)
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(keepScreenOn = enabled))
        }
    }

    /** Toggle lane hints on/off. */
    fun onToggleLaneHints(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(laneHintsEnabled = enabled)
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(laneHintsEnabled = enabled))
        }
    }

    /**
     * Set the map rendering mode (tile-cached vs direct). Persists the
     * selection and applies it immediately: the renderer is switched and a
     * forced full re-render clears any tiles/buffers from the other mode.
     */
    fun onSetRenderMode(mode: RenderMode) {
        _uiState.value = _uiState.value.copy(renderMode = mode)
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(renderMode = mode))
        }
        mapRenderer?.renderMode = mode
        mapRenderer?.invalidateStyle()
    }

    /** Feed the environment dimming signal (system night mode) from composition. */
    fun setEnvironmentDark(dark: Boolean) {
        darkModeController.setEnvironmentDark(dark)
    }

    /** Set the dark mode preference (On / Off / Automatic); persists via controller. */
    fun onSetDarkModePreference(preference: DarkModePreference) {
        darkModeController.setPreference(preference)
        _uiState.value = _uiState.value.copy(darkModePreference = preference)
    }

    /**
     * Push the resolved presentation to the native style sheet and force a full
     * re-render so no tiles/front buffer from the other variant survive.
     */
    private var lastPushedDark: Boolean? = null
    private var stylePushedToNative = false

    private fun pushDarkPresentation(dark: Boolean) {
        if (lastPushedDark == dark) return
        lastPushedDark = dark
        try {
            client.setStyleSheetFlag("daylight", !dark)
        } catch (e: Exception) {
            Log.e(TAG, "setStyleSheetFlag failed", e)
        }
        mapRenderer?.invalidateStyle()
    }

    /** Toggle auto-zoom on/off. */
    fun onToggleAutoZoom(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoZoomEnabled = enabled)
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(autoZoomEnabled = enabled))
        }
        if (enabled) {
            // Reset suspension state so zoom adjusts immediately
            autoZoomSuspended = false
            lastSpeedBandIndex = -1
        }
    }

    // Search query flow — debounced and collected for location search
    private val _searchQueryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch { searchHistoryRepository.load() }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .filter { it.length >= 2 || it.isEmpty() }
                .flatMapLatest { query ->
                    if (query.length < 2) {
                        _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
                        return@flatMapLatest flowOf(emptyList<LocationEntry>())
                    }
                    _uiState.value = _uiState.value.copy(isSearching = true)
                    flowOf(searchLocations(query))
                }
                .collect { results ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
        }

        // GPS fix quality: debounced to prevent flicker
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            locationService.location
                .map { loc ->
                    when {
                        loc == null || System.currentTimeMillis() - loc.time > GPS_FIX_FRESHNESS_MS -> GpsFixQuality.NONE
                        loc.accuracy > GPS_FIX_MAX_ACCURACY_M -> GpsFixQuality.POOR
                        else -> GpsFixQuality.GOOD
                    }
                }
                .distinctUntilChanged()
                .debounce(2000L)
                .collect { quality ->
                    if (quality != _gpsFixQuality.value) {
                        Log.d(TAG, "GPS fix quality: $quality")
                    }
                    _gpsFixQuality.value = quality
                    _uiState.value = _uiState.value.copy(gpsFixQuality = quality)

                    // Eagerly resolve the admin region once a GOOD fix is
                    // available while the search panel is open, so the region
                    // name appears without requiring a typed query.
                    if (quality == GpsFixQuality.GOOD && searchPanelOpen && searchAdminRegionHandle == 0L) {
                        viewModelScope.launch {
                            withContext(Dispatchers.Default) { currentSearchAdminRegionHandle() }
                        }
                    }
                }
        }

        // Resolved dark presentation drives theme state + native style sheet
        viewModelScope.launch {
            darkModeController.isDarkPresentation
                .collect { dark ->
                    _uiState.value = _uiState.value.copy(isDarkPresentation = dark)
                    pushDarkPresentation(dark)
                }
        }

        // Re-render when favorites change
        viewModelScope.launch {
            favoriteRepository.favorites.collect {
                val renderer = mapRenderer ?: return@collect
                val favs = favoriteRepository.favorites.value
                val allFavs = mutableListOf<com.framstag.libosmscout.client.FavoriteLocation>()
                for ((_, list) in favs) {
                    allFavs.addAll(list)
                }
                renderer.setFavoriteLocations(allFavs.toTypedArray())
            }
        }

        // Follow mode: keep map center and marker in sync.
        viewModelScope.launch {
            var lastRenderedLat = Double.NaN
            var lastRenderedLon = Double.NaN

            locationService.location.collect { loc ->
                if (loc == null) {
                    _uiState.value = _uiState.value.copy(gpsLocation = null)
                    mapRenderer?.clearGpsMarkerState()
                    lastMarkerLat = Double.NaN
                    lastMarkerLon = Double.NaN
                    return@collect
                }

                _uiState.value = _uiState.value.copy(gpsLocation = loc)

                if (logCount++ % 30 == 0) {
                    Log.d(TAG, "GPS loc=${"%.6f".format(loc.latitude)},${"%.6f".format(loc.longitude)} " +
                            "bearing=${if (loc.hasBearing()) "%.1f".format(loc.bearing) else "-"} " +
                            "follow=${_uiState.value.followMode}")
                }

                // Use navigation position if available (filtered by engine), else raw GPS
                val navPos = _navPosition
                val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
                val markerLat = if (isNavigating && navPos != null && !navPos.lat.isNaN()) navPos.lat else loc.latitude
                val markerLon = if (isNavigating && navPos != null && !navPos.lon.isNaN()) navPos.lon else loc.longitude

                // Deduplicate duplicate fixes delivered by multiple providers in the same
                // millisecond. Compare by timestamp and coarse coordinates only; bearing
                // may differ between Fused and LocationManager even for the same fix.
                val bearing = if (loc.hasBearing() && loc.bearing >= 0f) loc.bearing.toDouble() else -1.0
                val accuracy = if (loc.hasAccuracy() && loc.accuracy > 0f) loc.accuracy.toDouble() else -1.0
                val sameFix = (loc.time - lastGpsTime) < GPS_DEDUPE_MS &&
                        kotlin.math.abs(loc.latitude - lastGpsLat) < 1e-6 &&
                        kotlin.math.abs(loc.longitude - lastGpsLon) < 1e-6
                if (sameFix) {
                    // Still feed navigation engine, but skip render work.
                    _navigationViewModel?.processLocation(
                        loc.latitude, loc.longitude,
                        if (loc.hasSpeed()) loc.speed.toDouble().coerceAtLeast(0.0) else -1.0,
                        loc.accuracy.toDouble().coerceAtLeast(0.0),
                        loc.time
                    )
                    return@collect
                }
                lastGpsLat = loc.latitude
                lastGpsLon = loc.longitude
                lastGpsBearing = bearing
                lastGpsTime = loc.time

                // Feed navigation engine early so it sees every distinct fix.
                _navigationViewModel?.processLocation(
                    loc.latitude, loc.longitude,
                    if (loc.hasSpeed()) loc.speed.toDouble().coerceAtLeast(0.0) else -1.0,
                    loc.accuracy.toDouble().coerceAtLeast(0.0),
                    loc.time
                )

                if (!_uiState.value.followMode) {
                    // The marker rides with the next rendered frame (overlay input, never
                    // baked into tiles). A marker move > 5 m triggers a render so the marker
                    // follows the fix — same cadence as the old native setGpsMarker path.
                    mapRenderer?.setGpsMarkerState(markerLat, markerLon, bearing, accuracy)
                    val nowMs = System.currentTimeMillis()
                    val dist = distanceMeters(lastMarkerLat, lastMarkerLon, markerLat, markerLon)
                    if ((dist > 5.0 || lastMarkerLat.isNaN()) &&
                        nowMs - lastNonFollowMarkerRenderMs >= NON_FOLLOW_MARKER_RENDER_INTERVAL_MS
                    ) {
                        lastMarkerLat = markerLat
                        lastMarkerLon = markerLon
                        lastNonFollowMarkerRenderMs = nowMs
                        renderMap()
                    }
                    return@collect
                }

                // Update smoothed center on every distinct GPS fix so the EMA
                // never has to catch up after a coalesced tick.
                val smoothedLat = smoothCenter(markerLat, markerLon).first
                val smoothedLon = smoothCenter(markerLat, markerLon).second

                // In follow mode the GPS marker represents the current vehicle position
                // and must be drawn at the raw (or navigation-filtered) GPS fix, not at
                // the smoothed camera center. Otherwise the marker drifts away from the
                // road/track when the camera lags behind.
                val followMarkerLat = markerLat
                val followMarkerLon = markerLon

                // Derive map rotation and marker bearing from the recent track
                // (course-over-ground). The instantaneous GPS bearing is too jumpy
                // on this replay; the track direction is stable and follows the road.
                addCoursePoint(followMarkerLat, followMarkerLon)
                val (courseBearing, courseDist) = computeCourseBearing()
                // Use a fast low-pass while the history is not yet stable (after a
                // turn reset or start) so the map/marker align with the new driving
                // direction quickly; use the slow alpha once 40 m of track is stable.
                val courseAlpha = if (courseStable || courseDist >= MIN_COURSE_DISTANCE_M) {
                    COURSE_LOW_PASS_ALPHA
                } else {
                    COURSE_LOW_PASS_ALPHA_FAST
                }
                val smoothedBearing = smoothCourseBearing(courseBearing, courseAlpha)

                // Determine effective map angle based on orientation setting.
                // Use the actually rendered angle as the deadband reference so
                // tiny low-pass drift does not fight the front buffer. When no
                // valid course bearing is available yet, keep the previous used
                // angle so the map does not snap back to North-Up.
                val isNorthUp = if (isNavigating) _uiState.value.navNorthUp else _uiState.value.freeFormNorthUp
                val effectiveBearing = if (!smoothedBearing.isNaN()) smoothedBearing else lastUsedBearing
                // The marker arrow tracks the freshest direction signal so it points
                // along the new driving direction immediately after a turn, while the
                // map rotation uses the smoothed value and rotates at its own pace.
                // Priority: window course bearing -> latest segment bearing -> last used.
                val markerBearingRaw = when {
                    !courseBearing.isNaN() -> courseBearing
                    !lastSegmentBearing.isNaN() -> lastSegmentBearing
                    else -> lastUsedBearing
                }
                val markerBearing = if (!isNorthUp && !markerBearingRaw.isNaN()) markerBearingRaw else -1.0
                // Feed the marker state to the renderer: it is snapshotted into the next
                // render job and emitted with the front buffer, so the overlay marker always
                // matches the displayed map (no lead/jump while frames lag the live fix).
                mapRenderer?.setGpsMarkerState(followMarkerLat, followMarkerLon, markerBearing, accuracy)
                val smoothedAngle = if (!isNorthUp && !effectiveBearing.isNaN()) normalizeAngle(-Math.toRadians(effectiveBearing)) else Double.NaN
                val renderedAngle = normalizeAngle(mapRenderer?.renderedAngle ?: _uiState.value.viewport.angle)
                val angle = if (!isNorthUp && !smoothedAngle.isNaN()) {
                    val deltaDeg = Math.toDegrees(normalizeAngle(smoothedAngle - renderedAngle))
                    if (kotlin.math.abs(deltaDeg) < MIN_BEARING_DELTA_DEG) {
                        renderedAngle
                    } else {
                        val maxDeltaRad = Math.toRadians(MAX_ANGLE_RATE_DEG_PER_RENDER)
                        val deltaRad = normalizeAngle(smoothedAngle - renderedAngle)
                        val clampedDelta = deltaRad.coerceIn(-maxDeltaRad, maxDeltaRad)
                        normalizeAngle(renderedAngle + clampedDelta)
                    }
                } else {
                    // No valid course yet: stay at the last used angle (or North-Up if none).
                    if (!lastUsedAngle.isNaN()) lastUsedAngle else 0.0
                }
                if (!effectiveBearing.isNaN()) lastUsedBearing = effectiveBearing
                if (!angle.isNaN()) lastUsedAngle = angle

                // Distance since last actual render; used to decide whether a new
                // native render is worth the cost.
                val distanceMeters = if (!lastRenderedLat.isNaN()) {
                    distanceMeters(smoothedLat, smoothedLon, lastRenderedLat, lastRenderedLon)
                } else Double.POSITIVE_INFINITY

                val positionChanged = distanceMeters > 5.0 || lastRenderedLat.isNaN()
                val angleChanged = !isAngleSame(angle, _uiState.value.viewport.angle)
                val shouldRender = positionChanged || angleChanged

                // Always keep renderer's target viewport current so the next
                // render (coalesced or not) is centered on the latest position.
                mapRenderer?.prepareViewport(smoothedLat, smoothedLon, _uiState.value.viewport.magnification, angle)

                // Coalesce follow-mode renders so GPS ticks cannot overrun the render pipeline.
                val now = System.currentTimeMillis()
                val throttleElapsed = now - lastFollowRenderMs >= GPS_FOLLOW_RENDER_INTERVAL_MS
                if (!throttleElapsed && shouldRender) {
                    return@collect
                }

                if (!shouldRender) {
                    // Marker/center did not move enough; the overlay already shows the
                    // marker at the raw GPS fix so it stays on the road/track even when
                    // the camera barely shifts.
                    return@collect
                }

                // Compute the new magnification first so the viewport state is updated
                // once and only one render is requested. Two consecutive renders with
                // different zoom levels produced scaled "wrong zoom" frames.
                var newMag = _uiState.value.viewport.magnification
                var zoomCommitted = false
                if (positionChanged && _uiState.value.autoZoomEnabled) {
                    val navVm = _navigationViewModel
                    val navState = navVm?.state?.value
                    val rawSpeed = navState?.currentSpeedKmH ?: Double.NaN
                    val filteredSpeed = filterSpeed(rawSpeed)
                    val speedTarget = SpeedZoomTable.compute(filteredSpeed)

                    val turnDist = navState?.nextInstruction?.distanceTo ?: Double.NaN
                    val turnFloor = computeTurnBoost(turnDist)

                    val curveDist = findNearestCurve(markerLat, markerLon)
                    val curveFloor = computeTurnBoost(curveDist)

                    // Track turn waypoint passage for post-turn hold
                    if (!turnDist.isNaN() && turnDist < 0 && turnPassedDistance.isNaN()) {
                        turnPassedDistance = 0.0
                    } else if (!turnPassedDistance.isNaN()) {
                        turnPassedDistance += 5.0
                        if (turnPassedDistance > 600.0) {
                            turnPassedDistance = Double.NaN
                        }
                    } else if (!turnDist.isNaN() && turnDist > 0) {
                        turnPassedDistance = Double.NaN
                    }

                    val postTurnFloor = if (!turnPassedDistance.isNaN() && turnPassedDistance <= 600.0) 15.0 else 0.0
                    val finalTarget = maxOf(speedTarget, turnFloor, curveFloor, postTurnFloor)
                    val currentBand = SpeedZoomTable.bandIndex(filteredSpeed)

                    if (autoZoomSuspended && currentBand != lastSpeedBandIndex) {
                        autoZoomSuspended = false
                    }

                    if (!autoZoomSuspended) {
                        val cooldownElapsed = now - lastAutoZoomCommitMs >= ZOOM_COOLDOWN_MS
                        val targetInt = finalTarget.roundToInt().coerceIn(MIN_MAG, MAX_MAG)

                        if (targetInt == pendingZoomTarget.roundToInt()) {
                            zoomTargetStableSamples++
                        } else {
                            pendingZoomTarget = finalTarget
                            zoomTargetStableSamples = 0
                        }

                        val canCommit = cooldownElapsed && zoomTargetStableSamples >= ZOOM_COMMIT_SAMPLES
                        val diffMag = kotlin.math.abs(finalTarget - newMag.toDouble())
                        if (canCommit && diffMag >= ZOOM_HYSTERESIS_MAG && targetInt != newMag) {
                            newMag = targetInt
                            currentTargetMag = finalTarget
                            lastAutoZoomCommitMs = now
                            zoomTargetStableSamples = 0
                            zoomCommitted = true
                            Log.d(TAG, "autoZoom commit speed=${"%.1f".format(filteredSpeed)} " +
                                    "target=$finalTarget mag=$targetInt")
                        } else if (logCount++ % 30 == 0) {
                            Log.d(TAG, "autoZoom hold speed=${"%.1f".format(filteredSpeed)} " +
                                    "target=$finalTarget current=$newMag " +
                                    "stable=$zoomTargetStableSamples cooldown=${!cooldownElapsed} diff=$diffMag")
                        }
                        lastSpeedBandIndex = currentBand
                    }
                }

                // Center moves only when the GPS actually moved > 5 m. An exact
                // double comparison would re-render on every GPS tick (sub-meter
                // jitter) — that caused an endless render loop.
                val viewportMoved = positionChanged ||
                        angleChanged ||
                        newMag != _uiState.value.viewport.magnification

                if (viewportMoved) {
                    lastRenderedLat = smoothedLat
                    lastRenderedLon = smoothedLon
                    _uiState.value = _uiState.value.copy(
                        viewport = _uiState.value.viewport.copy(
                            centerLat = smoothedLat,
                            centerLon = smoothedLon,
                            angle = normalizeAngle(angle),
                            magnification = newMag
                        )
                    )
                    mapRenderer?.prepareViewport(smoothedLat, smoothedLon, newMag, angle)
                    // In follow mode the marker represents the current vehicle position and
                    // is drawn by the Compose overlay at the raw/navigation GPS fix. The
                    // viewport center is not smoothed, so the marker stays exactly on the
                    // road/track. The viewport moved — render it.
                    renderMap()
                    lastFollowRenderMs = System.currentTimeMillis()
                } else {
                    // No viewport motion; marker stays at the raw GPS fix via the overlay.
                }
            }
        }

        // Load persisted settings
        viewModelScope.launch {
            val settings = settingsStorage.load()
            darkModeController.restorePreference(settings.darkMode)
            _uiState.value = _uiState.value.copy(
                followMode = settings.followMode,
                autoZoomEnabled = settings.autoZoomEnabled,
                freeFormNorthUp = settings.freeFormNorthUp,
                navNorthUp = settings.navNorthUp,
                keepScreenOn = settings.keepScreenOn,
                darkModePreference = settings.darkMode,
                laneHintsEnabled = settings.laneHintsEnabled,
                renderMode = settings.renderMode
            )
        }

        // Collect route results from RoutePanelViewModel — pass to renderer
        // (Moved to setRoutePanelViewModel to avoid init-timing issues)

        // Collect clear route signals
        // (Moved to setRoutePanelViewModel)
    }

    /**
     * Returns the admin region handle to scope the current search with,
     * resolving or re-resolving it from the current GPS fix as needed.
     * Returns 0 when no usable fix exists or resolution fails — the search
     * then runs unconstrained.
     */
    internal fun currentSearchAdminRegionHandle(): Long =
        searchAdminRegionHandleForFix(locationService.location.value)

    /**
     * Pure scoping decision: resolves/reuses/releases the admin region handle
     * for the given GPS fix. Exposed internal for unit testing.
     */
    internal fun searchAdminRegionHandleForFix(fix: android.location.Location?): Long {
        if (fix == null ||
            System.currentTimeMillis() - fix.time > GPS_FIX_FRESHNESS_MS ||
            fix.accuracy > GPS_FIX_MAX_ACCURACY_M
        ) {
            releaseSearchAdminRegion()
            return 0L
        }

        val lat = fix.latitude
        val lon = fix.longitude

        // Reuse the cached region while the position has not moved significantly
        if (searchAdminRegionHandle != 0L &&
            !searchAdminRegionLat.isNaN() &&
            distanceMeters(searchAdminRegionLat, searchAdminRegionLon, lat, lon) <= ADMIN_REGION_MOVEMENT_THRESHOLD_M
        ) {
            return searchAdminRegionHandle
        }

        releaseSearchAdminRegion()
        searchAdminRegionHandle = try {
            val h = client.resolveAdminRegion(lat, lon)
            Log.d(TAG, "resolveAdminRegion(lat=$lat, lon=$lon) -> handle=$h")
            h
        } catch (e: Exception) {
            Log.e(TAG, "resolveAdminRegion failed", e)
            0L
        }
        if (searchAdminRegionHandle != 0L) {
            searchAdminRegionLat = lat
            searchAdminRegionLon = lon
            searchAdminRegionName = try {
                val name = client.getAdminRegionName(searchAdminRegionHandle)
                Log.d(TAG, "getAdminRegionName(handle=${searchAdminRegionHandle}) -> '$name'")
                name
            } catch (e: Exception) {
                Log.e(TAG, "getAdminRegionName failed", e)
                null
            }
        }
        pushSearchAdminRegionState()
        return searchAdminRegionHandle
    }

    private fun releaseSearchAdminRegion() {
        if (searchAdminRegionHandle != 0L) {
            try {
                client.releaseAdminRegion(searchAdminRegionHandle)
            } catch (e: Exception) {
                Log.e(TAG, "releaseAdminRegion failed", e)
            }
            searchAdminRegionHandle = 0L
        }
        searchAdminRegionLat = Double.NaN
        searchAdminRegionLon = Double.NaN
        searchAdminRegionName = null
        pushSearchAdminRegionState()
    }

    /** Mirror the resolved region name into the UI state. */
    private fun pushSearchAdminRegionState() {
        _uiState.value = _uiState.value.copy(searchAdminRegionName = searchAdminRegionName)
    }

    /**
     * Smooth the GPS center. With alpha 1.0 the center follows the raw fix
     * directly so the marker never drifts away from the road/track. For jumps
     * larger than CENTER_SMOOTH_MAX_JUMP_M the new fix is used directly so the
     * map does not lag after a teleport / first fix.
     */
    private fun smoothCenter(newLat: Double, newLon: Double): Pair<Double, Double> {
        if (smoothedCenterLat.isNaN()) {
            smoothedCenterLat = newLat
            smoothedCenterLon = newLon
            return Pair(newLat, newLon)
        }
        val dist = distanceMeters(newLat, newLon, smoothedCenterLat, smoothedCenterLon)
        if (dist > centerSmoothMaxJumpM) {
            smoothedCenterLat = newLat
            smoothedCenterLon = newLon
            return Pair(newLat, newLon)
        }
        smoothedCenterLat = smoothedCenterLat * (1.0 - centerSmoothAlpha) + newLat * centerSmoothAlpha
        smoothedCenterLon = smoothedCenterLon * (1.0 - centerSmoothAlpha) + newLon * centerSmoothAlpha
        return Pair(smoothedCenterLat, smoothedCenterLon)
    }

    private fun normalizeAngle(rad: Double): Double {
        var r = rad
        while (r <= -Math.PI) r += 2.0 * Math.PI
        while (r > Math.PI) r -= 2.0 * Math.PI
        return r
    }

    /** Compare two angles in radians, tolerating wrap-around and floating-point noise. */
    private fun isAngleSame(a: Double, b: Double): Boolean {
        return kotlin.math.abs(normalizeAngle(a - b)) < 1e-4
    }

    /** Normalize an angle in degrees to (-180,180]. */
    private fun normalizeAngleDeg(deg: Double): Double {
        var d = deg
        while (d <= -180.0) d += 360.0
        while (d > 180.0) d -= 360.0
        return d
    }

    /** Bearing in degrees [0,360) from (lat1,lon1) to (lat2,lon2). */
    private fun bearingFromCourse(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
        val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
                kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)
        var bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing
    }

    /** Append a new position to the course-over-ground history. */
    private fun addCoursePoint(lat: Double, lon: Double) {
        val newestIdx = (courseIndex - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
        if (!courseLats[newestIdx].isNaN()) {
            val dist = distanceMeters(lat, lon, courseLats[newestIdx], courseLons[newestIdx])
            if (dist > centerSmoothMaxJumpM) {
                // Teleport: clear stale history so the course bearing starts fresh.
                resetCourseHistory()
            } else {
                if (dist >= 2.0) {
                    lastSegmentBearing = bearingFromCourse(courseLats[newestIdx], courseLons[newestIdx], lat, lon)
                }
                if (dist >= MIN_SEGMENT_FOR_TURN_M && !lastSmoothedBearing.isNaN()) {
                    // If the latest segment turns sharply, the old history is from
                    // before the turn and must not influence the new course.
                    val diff = kotlin.math.abs(normalizeAngleDeg(lastSegmentBearing - lastSmoothedBearing))
                    if (diff > COURSE_TURN_RESET_DEG) {
                        resetCourseHistory()
                    }
                }
            }
        } else {
            // First point of a fresh history: no segment yet.
            lastSegmentBearing = Double.NaN
        }
        courseLats[courseIndex] = lat
        courseLons[courseIndex] = lon
        courseIndex = (courseIndex + 1) % COURSE_HISTORY_SIZE
    }

    private fun resetCourseHistory() {
        for (i in courseLats.indices) {
            courseLats[i] = Double.NaN
            courseLons[i] = Double.NaN
        }
        courseIndex = 0
        lastCourseBearing = Double.NaN
        lastSmoothedBearing = Double.NaN
        lastSegmentBearing = Double.NaN
        courseStable = false
    }

    /**
     * Compute course-over-ground bearing from the oldest history point that is at
     * least MIN_COURSE_DISTANCE_M (or the fast distance while history is not yet
     * stable) away from the newest point. Returns (bearing, distance). Falls back
     * to the last computed bearing when not enough distance has been accumulated.
     */
    private fun computeCourseBearing(): Pair<Double, Double> {
        val newestIdx = (courseIndex - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
        val newestLat = courseLats[newestIdx]
        val newestLon = courseLons[newestIdx]
        if (newestLat.isNaN()) return Pair(Double.NaN, 0.0)

        val minDist = if (courseStable) MIN_COURSE_DISTANCE_M else MIN_COURSE_DISTANCE_FAST_M
        var i = newestIdx
        var totalDist = 0.0
        var usedIdx = -1
        for (step in 1 until COURSE_HISTORY_SIZE) {
            val prev = (i - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
            if (courseLats[prev].isNaN()) break
            totalDist += distanceMeters(courseLats[prev], courseLons[prev], courseLats[i], courseLons[i])
            if (totalDist >= minDist) {
                usedIdx = prev
                break
            }
            i = prev
        }
        if (usedIdx >= 0) {
            val bearing = bearingFromCourse(courseLats[usedIdx], courseLons[usedIdx], newestLat, newestLon)
            lastCourseBearing = bearing
            if (totalDist >= MIN_COURSE_DISTANCE_M) courseStable = true
            return Pair(bearing, totalDist)
        }
        return Pair(lastCourseBearing.takeIf { !it.isNaN() } ?: Double.NaN, totalDist)
    }

    /**
     * Low-pass filter the course-over-ground bearing. Returns NaN when no valid
     * bearing is available (caller falls back to the last used bearing).
     */
    private fun smoothCourseBearing(newBearing: Double, alpha: Double): Double {
        if (newBearing.isNaN()) {
            return lastSmoothedBearing.takeIf { !it.isNaN() } ?: Double.NaN
        }
        val prev = lastSmoothedBearing
        val smoothed = if (prev.isNaN()) {
            newBearing
        } else {
            val diff = normalizeAngleDeg(newBearing - prev)
            val raw = prev + diff * alpha
            val norm = raw % 360.0
            if (norm < 0) norm + 360.0 else norm
        }
        lastSmoothedBearing = smoothed
        return smoothed
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2))
            .coerceIn(0.0, 1.0)
        return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    internal suspend fun searchLocations(query: String): List<LocationEntry> = withContext(Dispatchers.Default) {
        val handle = currentSearchAdminRegionHandle()
        Log.d(TAG, "searchLocations: query='$query', adminRegionHandle=$handle")
        try {
            val entries = client.searchLocations(query, 20, handle)
            entries?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "searchLocations failed", e)
            emptyList()
        }
    }

    /** Initialise with a map database path. Call once from the screen. */
    fun initMap(mapPath: String) {
        Log.d(TAG, "initMap: initialising with path=$mapPath")
        currentMapKey = mapPath.substringAfterLast('/')

        // Tear down any previous renderer so a re-entry (MAIN screen after
        // map downloads) initialises cleanly instead of stacking renderers.
        rendererScope?.cancel()
        mapRenderer?.shutdown()
        mapRenderer = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val density = context.resources.displayMetrics.densityDpi.toDouble()
            val stylesheetsDir = assetCopier.ensureStylesheets()
            Log.d(TAG, "initMap: density=$density, stylesheets=$stylesheetsDir")

            Log.d(TAG, "initMap: opening database...")
            val opened = try {
                client.openDatabase(mapPath).also { success ->
                    Log.d(TAG, "initMap: openDatabase returned $success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open database", e)
                false
            }

            if (!opened) {
                Log.e(TAG, "initMap: failed to open database at $mapPath")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not open map database"
                )
                return@launch
            }

            Log.d(TAG, "initMap: database opened successfully")

            // Open all other installed maps too, so every downloaded region
            // renders via viewport coverage — no switching needed (multi-map).
            try {
                val mapsDir = File(context.filesDir, "maps")
                val basemapDir = File(mapsDir, "basemap").absolutePath
                val installed = mapsDir.listFiles()
                    ?.filter { it.isDirectory && it.absolutePath != basemapDir }
                    ?.map { it.absolutePath }
                    ?: emptyList()
                for (dir in installed) {
                    if (dir != mapPath) {
                        client.openDatabase(dir)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "initMap: failed to open additional maps", e)
            }

            // Initialise favorites repository
            val favPath = context.filesDir.resolve(FAVORITES_FILE).absolutePath
            favoriteRepository.init(favPath)

            // Create MapRenderer on a dedicated background scope so heavy JNI renders
            // never block the main thread and the UI stays responsive.
            val rendererScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            this@MapCanvasViewModel.rendererScope = rendererScope
            val renderer = MapRenderer(client, density, rendererScope)
            renderer.renderMode = _uiState.value.renderMode
            // Re-apply the last known screen size: onSizeChanged only fires on size
            // CHANGE, so re-entering this screen (same size) would leave the new
            // renderer at 0x0 and every render would be skipped.
            renderer.screenWidth = screenWidth
            renderer.screenHeight = screenHeight
            mapRenderer = renderer

            // Wire frame emissions to UI state. Each frame carries bitmap + viewport +
            // marker snapshot as ONE atomic emission, so the overlay never sees state
            // from different frames (which would make the marker jump off the road).
            viewModelScope.launch {
                renderer.frameFlow.collect { frame ->
                    val bitmap = frame.bitmap
                    if (bitmap != null) {
                        // First frame: the DB may have opened after the initMap flag
                        // push (SetStyleFlag is a no-op until a DB is open) — re-apply once.
                        if (!stylePushedToNative) {
                            stylePushedToNative = true
                            lastPushedDark = null
                            pushDarkPresentation(darkModeController.isDarkPresentation.value)
                        }
                        _uiState.value = _uiState.value.copy(
                            renderedBitmap = bitmap.asImageBitmap(),
                            isLoading = false,
                            error = null,
                            renderViewport = frame.viewport,
                            gpsMarkerLat = frame.marker.lat,
                            gpsMarkerLon = frame.marker.lon,
                            gpsMarkerBearing = frame.marker.bearing,
                            gpsMarkerAccuracy = frame.marker.accuracy
                        )
                        Log.d(TAG, "frontBufferFlow: new bitmap " + bitmap.width + "x" + bitmap.height)
                    }
                }
            }

            // Wire view change listener for viewport persistence (per map)
            renderer.addViewChangeListener(object : MapRenderer.ViewChangeListener {
                override fun onViewChanged(lat: Double, lon: Double, mag: Int, angle: Double) {
                    viewModelScope.launch {
                        viewportStorage.save(currentMapKey ?: mapPath, ViewportState(lat, lon, mag))
                    }
                }
            })

            // Load persisted viewport for this map; fall back to the map's
            // bounding box center, then to the global default
            val saved = viewportStorage.load(currentMapKey ?: mapPath)
            val bbox = try {
                client.getDatabaseBoundingBox(mapPath)
            } catch (e: Exception) {
                Log.w(TAG, "initMap: bounding box lookup failed", e)
                null
            }
            val default = ViewportState()
            val restored = saved ?: if (bbox != null && bbox.size >= 4) {
                ViewportState(
                    centerLat = (bbox[0] + bbox[2]) / 2.0,
                    centerLon = (bbox[1] + bbox[3]) / 2.0,
                    magnification = default.magnification
                )
            } else {
                default
            }
            // A persisted world-zoom viewport (mag < 4) renders the whole globe in
            // native and can hang the render worker — clamp the restore to the same
            // floor the gesture/zoom controls enforce (specs: min magnification 4).
            val vp = restored.copy(magnification = restored.magnification.coerceIn(MIN_MAG, MAX_MAG))
            Log.d(
                TAG,
                "initMap: viewport lat=${vp.centerLat}, lon=${vp.centerLon}, mag=${vp.magnification} " +
                    (if (saved != null) "(saved)" else if (bbox != null) "(bbox)" else "(default)")
            )
            _uiState.value = _uiState.value.copy(viewport = vp, isLoading = false)

            // Set initial favorites on renderer
            val favs = favoriteRepository.favorites.value
            val allFavs = mutableListOf<com.framstag.libosmscout.client.FavoriteLocation>()
            for ((_, list) in favs) {
                allFavs.addAll(list)
            }
            if (allFavs.isNotEmpty()) {
                renderer.setFavoriteLocations(allFavs.toTypedArray())
            }

            // Apply resolved dark presentation to the style sheet so the first
            // render already uses the correct variant
            lastPushedDark = null
            pushDarkPresentation(darkModeController.isDarkPresentation.value)
            // The push above happens after the database is open, so SetStyleFlag
            // is effective — mark it as done so the first front-buffer frame does
            // not re-push and invalidate the freshly rendered tiles.
            stylePushedToNative = true

            Log.d(TAG, "initMap: triggering first render")
            renderer.requestRender(vp.centerLat, vp.centerLon, vp.magnification)
        }
    }

    /** Set screen dimensions on the renderer (called from composable). */
    fun setScreenSize(width: Int, height: Int) {
        val wasZero = screenWidth <= 0 || screenHeight <= 0
        screenWidth = width
        screenHeight = height
        mapRenderer?.let {
            it.screenWidth = width
            it.screenHeight = height
            // If first render was skipped due to zero size, trigger it now
            if (wasZero && width > 0 && height > 0) {
                val vp = _uiState.value.viewport
                renderMap()
            }
        }
    }

    /** Called when user types in the search field. */
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _searchQueryFlow.value = query
    }

    /** Called when user picks an entry from the search history: fills the search box. */
    fun onHistoryEntrySelected(text: String) {
        onSearchQueryChanged(text)
    }

    /** Called when user selects a favorite from the favorites sheet. */
    fun onFavoriteSelected(fav: com.framstag.libosmscout.client.FavoriteLocation) {
        Log.d(TAG, "onFavoriteSelected: name='${fav.name}', lat=${fav.lat}, lon=${fav.lon}")
        viewModelScope.launch {
            val entry = LocationEntry().apply {
                label = fav.name
                lat = fav.lat
                lon = fav.lon
                matchQuality = "favorite"
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = entry,
                isLongPress = false,
                showFavoritesSheet = false,
                showDetailsSheet = true,
                isLoading = true
            )
            updateCenter(fav.lat, fav.lon)

            // Fetch object description and bounding box in parallel
            val descDeferred = async {
                withContext(Dispatchers.Default) {
                    try {
                        client.getDescription(fav.lat, fav.lon, _uiState.value.viewport.magnification)
                    } catch (e: Exception) {
                        Log.e(TAG, "getDescription failed for favorite", e)
                        null
                    }
                }
            }
            val bboxDeferred = async {
                withContext(Dispatchers.Default) {
                    try {
                        client.getObjectBoundingBox(fav.lat, fav.lon, _uiState.value.viewport.magnification)
                    } catch (e: Exception) {
                        Log.e(TAG, "getObjectBoundingBox failed for favorite", e)
                        null
                    }
                }
            }

            val desc = descDeferred.await()
            val bbox = bboxDeferred.await()

            // Determine target zoom: area bounding box → compute, else fixed node zoom
            val targetMag = if (bbox != null && bbox.size == 4) {
                computeAreaZoom(bbox, screenWidth, screenHeight)
            } else {
                NODE_ZOOM
            }
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(magnification = targetMag)
            )

            if (desc != null && desc.entries.isNotEmpty()) {
                val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else fav.lat
                val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else fav.lon
                val objEntry = LocationEntry().apply {
                    this.label = fav.name
                    this.lat = objLat
                    this.lon = objLon
                    this.matchQuality = "favorite"
                }
                _uiState.value = _uiState.value.copy(
                    selectedLocation = objEntry,
                    objectDescription = desc,
                    isLoading = false
                )
                updateCenter(objLat, objLon)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            renderMap()
        }
    }

    /** Called when user selects a search result. */
    fun onSearchResultSelected(entry: LocationEntry) {
        Log.d(TAG, "onSearchResultSelected: label='${entry.label}', lat=${entry.lat}, lon=${entry.lon}")
        // Capture the query before the state copy below clears it. Only real
        // search selections (non-blank query) are recorded; convenience entries
        // like "Current Location" are selected from an empty query.
        val query = _uiState.value.searchQuery
        viewModelScope.launch {
            if (query.isNotBlank()) {
                searchHistoryRepository.record(query)
            }
            // Deactivate follow mode so the selected result stays visible and
            // subsequent GPS updates do not re-center the viewport.
            if (_uiState.value.followMode) {
                _uiState.value = _uiState.value.copy(followMode = false)
                val current = settingsStorage.load()
                settingsStorage.save(current.copy(followMode = false))
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = entry,
                objectDescription = null,
                isLongPress = false,
                searchQuery = "",
                searchResults = emptyList(),
                showDetailsSheet = true,
                isLoading = true
            )
            updateCenter(entry.lat, entry.lon)
            renderMap()

            // Fetch full object description at search result location
            val desc = withContext(Dispatchers.Default) {
                try {
                    client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification)
                } catch (e: Exception) {
                    Log.e(TAG, "getDescription failed for search result", e)
                    null
                }
            }
            if (desc != null && desc.entries.isNotEmpty()) {
                val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else entry.lat
                val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else entry.lon
                val objEntry = LocationEntry().apply {
                    this.label = entry.label
                    this.lat = objLat
                    this.lon = objLon
                    this.matchQuality = entry.matchQuality
                }
                _uiState.value = _uiState.value.copy(
                    selectedLocation = objEntry,
                    objectDescription = desc,
                    isLoading = false
                )
                updateCenter(objLat, objLon)
                renderMap()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Called when user long-presses on the map. */
    fun onLongPress(lat: Double, lon: Double) {
        Log.d(TAG, "onLongPress: lat=$lat, lon=$lon")
        viewModelScope.launch {
            val entry = LocationEntry().apply {
                this.label = "%.5f, %.5f".format(lat, lon)
                this.lat = lat
                this.lon = lon
                this.matchQuality = "coordinate"
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = entry,
                isLongPress = true,
                isLoading = true
            )
            val desc = withContext(Dispatchers.Default) {
                try {
                    client.getDescription(lat, lon, _uiState.value.viewport.magnification)
                } catch (e: Exception) {
                    Log.e(TAG, "getDescription failed", e)
                    null
                }
            }
            if (desc != null && desc.entries.isNotEmpty()) {
                val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else lat
                val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else lon
                val objEntry = LocationEntry().apply {
                    this.label = entry.label
                    this.lat = objLat
                    this.lon = objLon
                    this.matchQuality = "object"
                }
                _uiState.value = _uiState.value.copy(
                    selectedLocation = objEntry,
                    objectDescription = desc,
                    isLoading = false,
                    showDetailsSheet = true
                )
                updateCenter(objLat, objLon)
            } else {
                _uiState.value = _uiState.value.copy(
                    objectDescription = desc,
                    isLoading = false,
                    showDetailsSheet = false
                )
            }
            renderMap()
        }
    }

    /** Set the NavigationViewModel for location forwarding during navigation. */
    fun setNavigationViewModel(vm: com.naviveylin.navigation.NavigationViewModel) {
        _navigationViewModel = vm
        // Collect navigation position estimates for follow mode
        viewModelScope.launch {
            vm.positionFlow.collect { pos ->
                _navPosition = pos
            }
        }
    }

    /** Set the RoutePanelViewModel (injected via Hilt from the screen). */
    fun setRoutePanelViewModel(vm: RoutePanelViewModel) {
        _routePanelViewModel = vm

        // Collect route results for map rendering
        viewModelScope.launch {
            vm.routeResultFlow.collect { result ->
                if (result != null) {
                    mapRenderer?.setRoute(
                        result.routeLats, result.routeLons,
                        result.startLat, result.startLon,
                        result.destLat, result.destLon
                    )
                    // Store route geometry for curve detection
                    routeLats = result.routeLats
                    routeLons = result.routeLons
                    renderMap()
                }
            }
        }

        // Collect clear route signals
        viewModelScope.launch {
            vm.clearRouteSignal.collect {
                mapRenderer?.clearRoute()
                routeLats = null
                routeLons = null
                renderMap()
            }
        }
    }

    /** Open route panel with destination prefilled from details sheet, start = current location. */
    fun openRoutePanelWithStart(entry: LocationEntry?) {
        val vm = _routePanelViewModel ?: return
        if (entry != null) {
            vm.setDestLocation(entry)
            val loc = locationService.location.value
            if (loc != null) {
                val currentLoc = LocationEntry().apply {
                    label = "Current Location"
                    lat = loc.latitude
                    lon = loc.longitude
                    matchQuality = "coordinate"
                }
                vm.setStartLocation(currentLoc)
            }
        }
        _uiState.value = _uiState.value.copy(showRoutePanel = true)
    }

    /** Dismiss the route panel. */
    fun dismissRoutePanel() {
        _uiState.value = _uiState.value.copy(showRoutePanel = false)
    }

    /** Set route start location (from favorite picking). */
    fun setRouteStart(entry: LocationEntry) {
        _routePanelViewModel?.setStartLocation(entry)
    }

    /** Set route destination location (from favorite picking). */
    fun setRouteDest(entry: LocationEntry) {
        _routePanelViewModel?.setDestLocation(entry)
    }

    /** Dismiss the details sheet. */
    fun dismissDetailsSheet() {
        _uiState.value = _uiState.value.copy(
            showDetailsSheet = false,
            objectDescription = null,
            isLongPress = false
        )
    }

    /** Add selected location to favorites. Creates the group first if it is new. */
    fun addSelectedToFavorites(groupName: String, favName: String, isNewGroup: Boolean) {
        val loc = _uiState.value.selectedLocation ?: return
        viewModelScope.launch {
            // A "new group" name that already exists is an error: do not
            // silently add the favorite to the existing group.
            if (isNewGroup && groupName in favoriteRepository.getGroupNames()) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Group \"$groupName\" already exists"
                )
                return@launch
            }
            val success = favoriteRepository.addFavorite(groupName, favName, loc.lat, loc.lon)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Added to $groupName" else "Failed to add favorite"
            )
        }
    }

    /** Remove selected location from favorites. */
    fun removeSelectedFromFavorites() {
        val loc = _uiState.value.selectedLocation ?: return
        val existing = favoriteRepository.findFavoriteByLocation(loc.lat, loc.lon) ?: return
        viewModelScope.launch {
            val success = favoriteRepository.deleteFavorite(existing.first, existing.second.name)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Removed from favorites" else "Failed to remove"
            )
        }
    }

    /** Check if selected location is already a favorite. */
    fun isSelectedLocationFavorite(): Boolean {
        val loc = _uiState.value.selectedLocation ?: return false
        return favoriteRepository.findFavoriteByLocation(loc.lat, loc.lon) != null
    }

    /** Get all favorite group names. */
    fun getFavoriteGroupNames(): List<String> = favoriteRepository.getGroupNames()

    /** Toggle follow mode on/off. */
    fun onToggleFollowMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(followMode = enabled)
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(followMode = enabled))
        }
        if (enabled) {
            // Reset auto-zoom state for fresh navigation start
            autoZoomSuspended = false
            lastSpeedBandIndex = -1
            currentTargetMag = 15.0
            pendingZoomTarget = 15.0
            zoomTargetStableSamples = 0
            lastAutoZoomCommitMs = 0L
            turnPassedDistance = Double.NaN
            lastValidSpeedKmH = 20.0
            // Set initial magnification to routing-sensible default
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(magnification = 15)
            )

            // Immediately center on current GPS position
            val loc = locationService.location.value
            if (loc != null) {
                // Apply orientation: if north-up, reset angle; if follow-direction, use bearing
                val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
                val isNorthUp = if (isNavigating) _uiState.value.navNorthUp else _uiState.value.freeFormNorthUp
                val angle = if (!isNorthUp && loc.hasBearing() && loc.bearing >= 0f) {
                    -Math.toRadians(loc.bearing.toDouble())
                } else 0.0

                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        centerLat = loc.latitude,
                        centerLon = loc.longitude,
                        angle = angle
                    )
                )
                renderMap()
            }
        }
    }

    /** Disengage follow mode (called on manual pan/zoom). */
    fun disengageFollowMode() {
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
        }
    }

    /** Set free-form orientation: true = north-up, false = follow direction. */
    fun onSetFreeFormOrientation(northUp: Boolean) {
        _uiState.value = _uiState.value.copy(freeFormNorthUp = northUp)
        // Apply immediately: if north-up, reset angle; if follow-direction, use current bearing
        if (northUp) {
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(angle = 0.0)
            )
            renderMap()
        } else {
            val loc = locationService.location.value
            if (loc != null && loc.hasBearing() && loc.bearing >= 0f) {
                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        angle = -Math.toRadians(loc.bearing.toDouble())
                    )
                )
                renderMap()
            }
        }
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(freeFormNorthUp = northUp))
        }
    }

    /** Set navigation orientation: true = north-up, false = follow direction. */
    fun onSetNavOrientation(northUp: Boolean) {
        _uiState.value = _uiState.value.copy(navNorthUp = northUp)
        // Apply immediately
        if (northUp) {
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(angle = 0.0)
            )
            renderMap()
        } else {
            val navPos = _navPosition
            if (navPos != null && !navPos.bearing.isNaN()) {
                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        angle = -Math.toRadians(navPos.bearing)
                    )
                )
                renderMap()
            }
        }
        viewModelScope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(navNorthUp = northUp))
        }
    }

    /** Get current location for overlay rendering. */
    fun getCurrentLocation(): android.location.Location? = locationService.location.value



    /** Start GPS location updates. */
    fun startLocationUpdates() = locationService.startLocationUpdates()

    /** Stop GPS location updates. */
    fun stopLocationUpdates() = locationService.stopLocationUpdates()

    /** Check if location permission is granted. */
    fun hasLocationPermission(): Boolean = locationService.hasPermission

    /** Toggle favorites sheet visibility. */
    fun toggleFavoritesSheet() {
        _uiState.value = _uiState.value.copy(
            showFavoritesSheet = !_uiState.value.showFavoritesSheet
        )
    }

    /** Select the current GPS position as a search result (centers map + details sheet). */
    fun selectCurrentLocation() {
        val loc = locationService.location.value ?: return
        val entry = LocationEntry().apply {
            label = "Current Location"
            lat = loc.latitude
            lon = loc.longitude
            matchQuality = "coordinate"
        }
        onSearchResultSelected(entry)
    }

    /** Dismiss snackbar. */
    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    /** Show a snackbar message. */
    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    /**
     * Search panel opened: eagerly resolve the admin region (when a usable GPS
     * fix exists) so the region name shows above the search field immediately.
     */
    fun onSearchPanelOpened() {
        searchPanelOpen = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) { currentSearchAdminRegionHandle() }
        }
    }

    /** Clear search state. */
    fun clearSearch() {
        searchPanelOpen = false
        releaseSearchAdminRegion()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
        _searchQueryFlow.value = ""
    }

    /** Update center latitude (called from gesture handler). */
    fun updateCenter(lat: Double, lon: Double) {
        // Guard against corrupted gesture math (NaN/infinity or out-of-range
        // coordinates would make every subsequent render fail). Clamp to the
        // Mercator-valid latitude range — ±90 (the poles) breaks rendering.
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return
        val clat = lat.coerceIn(-85.0, 85.0)
        val clon = lon.coerceIn(-180.0, 180.0)
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(centerLat = clat, centerLon = clon)
        )
    }

    /** Update map rotation angle (called from two-finger rotation gesture). */
    fun updateAngle(angleRadians: Double) {
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(angle = angleRadians)
        )
    }

    /**
     * Manual two-finger rotation: disengage follow mode, clear the active
     * "always north" flag, and apply the rotation delta to the viewport angle.
     * The map stays at the manually set angle afterwards.
     */
    fun onManualRotation(angleDeltaRadians: Double) {
        onManualRotationStart()
        updateAngle(_uiState.value.viewport.angle + angleDeltaRadians)
    }

    /**
     * Disengage follow mode and clear the active "always north" flag when a
     * manual rotation gesture starts. The angle itself is applied visually
     * during the gesture and committed on gesture end.
     */
    fun onManualRotationStart() {
        disengageFollowMode()
        val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
        if (isNavigating) {
            if (_uiState.value.navNorthUp) {
                _uiState.value = _uiState.value.copy(navNorthUp = false)
            }
        } else {
            if (_uiState.value.freeFormNorthUp) {
                _uiState.value = _uiState.value.copy(freeFormNorthUp = false)
            }
        }
    }

    /** Update magnification (called from zoom controls or pinch). */
    fun updateMagnification(mag: Int) {
        val clamped = mag.coerceIn(MIN_MAG, MAX_MAG)
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(magnification = clamped)
        )
        // Detect user-initiated zoom → suspend auto-zoom
        if (_uiState.value.autoZoomEnabled && !autoZoomSuspended) {
            autoZoomSuspended = true
            val navVm = _navigationViewModel
            val rawSpeed = navVm?.state?.value?.currentSpeedKmH ?: Double.NaN
            lastSpeedBandIndex = if (!rawSpeed.isNaN() && rawSpeed >= 0) SpeedZoomTable.bandIndex(filterSpeed(rawSpeed)) else -1
        }
    }

    /** Increment magnification by 1. */
    fun zoomIn() = updateMagnification(_uiState.value.viewport.magnification + 1)

    /** Decrement magnification by 1. */
    fun zoomOut() = updateMagnification(_uiState.value.viewport.magnification - 1)

    /** Re-render the map with current viewport via MapRenderer. */
    fun renderMap(forceFullRender: Boolean = false) {
        val renderer = mapRenderer ?: run {
            Log.w(TAG, "renderMap: mapRenderer is null — no render")
            return
        }
        val vp = _uiState.value.viewport
        Log.d(TAG, "renderMap mag=" + vp.magnification + " center=" + vp.centerLat + "," + vp.centerLon)
        renderer.requestRender(vp.centerLat, vp.centerLon, vp.magnification, vp.angle, forceFullRender)
    }

    /** Persist current viewport to disk. */
    fun saveViewport() {
        viewModelScope.launch {
            viewportStorage.save(currentMapKey ?: "default", _uiState.value.viewport)
        }
    }

    /** Retry after error. */
    fun retryRender() = renderMap()

    /** Test hook: cancel all viewModel coroutines so runTest does not hang. */
    @VisibleForTesting
    internal fun cancelScopeForTest() {
        rendererScope?.cancel()
        mapRenderer?.shutdown()
        viewModelScope.cancel()
    }

    override fun onCleared() {
        releaseSearchAdminRegion()
        super.onCleared()
        rendererScope?.cancel()
        mapRenderer?.shutdown()
    }

    companion object {
        private const val TAG = "MapCanvasVM"
        private const val FAVORITES_FILE = "favorites.json"
        private const val GPS_FIX_FRESHNESS_MS = 5_000L
        private const val GPS_FIX_MAX_ACCURACY_M = 50f

        // Ignore duplicate GPS fixes with same coordinates and bearing within this window.
        private const val GPS_DEDUPE_MS = 100L
        // Minimum interval between follow-mode renders (coalesces GPS ticks).
        private const val GPS_FOLLOW_RENDER_INTERVAL_MS = 200L
        // Minimum interval between non-follow marker-move renders (old native throttle).
        private const val NON_FOLLOW_MARKER_RENDER_INTERVAL_MS = 1000L
        // Ignore bearing changes smaller than this for follow-mode angle updates.
        private const val MIN_BEARING_DELTA_DEG = 3.0

        // Movement threshold for re-resolving the search admin region (meters)
        private const val ADMIN_REGION_MOVEMENT_THRESHOLD_M = 500.0
        /** Minimum magnification for the zoom control (buttons, keys, scroll wheel).
         *  Floor of 4 matches the gesture range and the specs (map-pan-zoom, map-rotation-gesture):
         *  lower zooms render huge world tiles natively (z=2 ~5s, z=1 hangs), stalling the render worker. */
        const val MIN_MAG = 4
        /** Minimum magnification for the pinch/rotation gesture commit (keeps 4–20). */
        const val GESTURE_MIN_MAG = 4
        const val MAX_MAG = 20

        /** Clamp a magnification to the pinch/rotation gesture range (4–20). */
        fun clampGestureMagnification(mag: Int): Int = mag.coerceIn(GESTURE_MIN_MAG, MAX_MAG)
        private const val CANVAS_OVERRUN = 1.2

        /** Fixed zoom level for node-type favorites (points, POIs). */
        private const val NODE_ZOOM = 17

        /** Minimum zoom level for area-type favorites (prevents too-zoomed-out view). */
        private const val MIN_AREA_ZOOM = 14

        /**
         * Compute a magnification that fits the given bounding box within the viewport.
         *
         * @param bbox double[4] = [minLat, maxLat, minLon, maxLon]
         * @param vpWidth viewport width in pixels
         * @param vpHeight viewport height in pixels
         * @return magnification level clamped to [MIN_MAG, MAX_MAG]
         */
        fun computeAreaZoom(bbox: DoubleArray, vpWidth: Int, vpHeight: Int): Int {
            if (vpWidth <= 0 || vpHeight <= 0) return NODE_ZOOM

            val minLat = bbox[0]
            val maxLat = bbox[1]
            val minLon = bbox[2]
            val maxLon = bbox[3]

            val dLat = maxLat - minLat
            val dLon = maxLon - minLon
            if (dLat <= 0.0 || dLon <= 0.0) return NODE_ZOOM

            // Earth circumference at equator ~40075 km
            // Convert degree span to approximate meters
            val avgLat = (minLat + maxLat) / 2.0
            val latRad = Math.toRadians(avgLat)
            val metersPerDegLat = 111320.0
            val metersPerDegLon = 111320.0 * Math.cos(latRad)

            val heightMeters = dLat * metersPerDegLat
            val widthMeters = dLon * metersPerDegLon

            if (heightMeters <= 0.0 || widthMeters <= 0.0) return NODE_ZOOM

            // Use 80% of the smaller viewport dimension as the "fitting size"
            val fitSizePx = minOf(vpWidth, vpHeight) * 0.8

            // Resolution at zoom level: meters per pixel at equator
            // Base: at zoom 0, world is 256px. Each zoom doubles resolution.
            // Earth circumference ~40075016.686 m at equator
            // metersPerPixel = circumference / (256 * 2^zoom)
            // So zoom = log2(circumference / (256 * metersPerPixel))
            // We want metersPerPixel such that the larger dimension fits in fitSizePx
            val maxMeters = maxOf(heightMeters, widthMeters)
            val targetMetersPerPixel = maxMeters / fitSizePx

            val earthCircumference = 40075016.686
            val mag = Math.round(
                Math.log(earthCircumference / (256.0 * targetMetersPerPixel)) / Math.log(2.0)
            ).toInt()

            return mag.coerceIn(MIN_AREA_ZOOM, MAX_MAG)
        }
    }
}
