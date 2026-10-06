package com.naviveylin.ui.map

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.ObjectDescription
import com.framstag.libosmscout.client.PoiEntry
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.R
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.BundledMapStyles
import com.naviveylin.core.BundledMapStyles.DEFAULT_STYLE_NAME
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.MapStyleLoadReporter
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.StartMapSelection
import com.naviveylin.core.TileCacheConfig
import com.naviveylin.core.formatCoordinatePair
import com.naviveylin.core.stringResolver
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.ZoomWalk
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import com.naviveylin.core.search.FavoriteSearchMerger
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.RegionFixReference
import com.naviveylin.core.search.RegionScopeDecision
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchRegionScope
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.core.search.resolveRegionScope
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.StructuredAddressSearch
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.RenderMode
import com.naviveylin.data.SearchHistoryEntry
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportState
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationLease
import com.naviveylin.location.LocationService
import com.naviveylin.core.FixFreshness
import com.naviveylin.core.SpeedStaleness
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.share.SharedLocationRequest
import com.naviveylin.ui.route.RouteOverlayAnchor
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import com.naviveylin.ui.route.RouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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

/** Search modes of the unified search dialog (spec: search-dialog — mode switch). */
enum class SearchMode {
    PLACES,
    POIS,
    CONTACTS
}

/**
 * Explicit map mode (spec: map-modes). Derived from the two existing sources
 * of truth — navigation state and follow mode — never stored independently, so
 * there is a single source for "am I navigating" and "am I following".
 */
enum class MapMode {
    /** Free-form map: follow off, north-up, last viewport. Default on start. */
    BROWSE,
    /** Follow on, auto-zoom on, heading-up — the one-tap drive preset. */
    FREE_DRIVE,
    /** Turn-by-turn route active; overrides follow mode. */
    NAVIGATION
}

data class MapCanvasUiState(
    val viewport: ViewportState = ViewportState(),
    /**
     * Magnification the render pipeline and the display zoom animation are on while a
     * bounded magnification walk is pending (spec: smooth-zoom - Eased zoom animation
     * on discrete zoom input). [viewport] keeps the FINAL requested magnification
     * (persisted, spec: smooth-zoom - Viewport records final magnification), while the
     * display advances to it in steps the frame in hand can serve. NaN = no walk.
     */
    val zoomWalkStepMag: Double = Double.NaN,
    val renderedBitmap: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<MergedSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Whether the unified search dialog is open. */
    val searchOpen: Boolean = false,
    /** Active search mode; Places is the initial mode on open (spec: search-dialog). */
    val searchMode: SearchMode = SearchMode.PLACES,
    /** Name of the resolved admin region scoping the search, null when unscoped. */
    val searchAdminRegionName: String? = null,
    val selectedLocation: LocationEntry? = null,
    val objectDescription: ObjectDescription? = null,
    val isLongPress: Boolean = false,
    val showDetailsSheet: Boolean = false,
    /** Ranked candidate objects at the long-press point, empty until a lookup runs. */
    val candidateDescriptions: List<ObjectDescription> = emptyList(),
    /** Whether the candidate picker sheet is shown (long-press with multiple objects). */
    val showCandidatePicker: Boolean = false,
    /** Selected POI category id, null while none is selected (never preselected). */
    val poiCategory: String? = null,
    /**
     * Reference point the current Places result list was ranked and measured
     * against (last known GPS fix, else the map center), null when neither is
     * available. Ranking and the displayed distance both read this one value,
     * so the numbers cannot contradict the order (spec: search-result-ranking
     * — "Distance reference for ordering and display").
     */
    val searchReference: SearchReference? = null,
    /** POI search radius in meters. */
    val poiRadiusMeters: Double = MapCanvasViewModel.DEFAULT_POI_RADIUS_METERS,
    /** POI search results, empty until the user triggers a search. */
    val poiResults: List<PoiEntry> = emptyList(),
    val isPoiSearching: Boolean = false,
    val poiSearchError: String? = null,
    /** Map center the POI search ran around; NaN until a search runs. */
    val poiSearchCenterLat: Double = Double.NaN,
    val poiSearchCenterLon: Double = Double.NaN,
    /** Position of the last clicked POI result, for the embedded-map highlight; NaN when none. */
    val poiSelectedLat: Double = Double.NaN,
    val poiSelectedLon: Double = Double.NaN,
    /** True while the details sheet was opened from POI search; drives close semantics. */
    val detailsFromPoiSearch: Boolean = false,
    /** True while the details sheet was opened from the address-book search; back returns to it. */
    val detailsFromAddressBook: Boolean = false,
    val showFavoritesSheet: Boolean = false,
    /** Whether READ_CONTACTS is granted; drives the Contacts search-mode visibility. */
    val addressBookAvailable: Boolean = false,
    val showRoutePanel: Boolean = false,
    val routeStartLocation: LocationEntry? = null,
    val routeDestLocation: LocationEntry? = null,
    val snackbarMessage: String? = null,
    val followMode: Boolean = false,
    /**
     * Active follow-mode vehicle anchor (spec: smooth-follow — Anchor-centered
     * follow framing): routing anchor while route guidance is active,
     * free-driving anchor otherwise. The ViewModel applies it to the follow
     * render target; the map canvas and the marker inherit it through the
     * emitted frame viewport. Kept in the state for the settings pickers.
     */
    val activeFollowAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT,
    /** Configured routing anchor (picker value; the active anchor follows guidance state). */
    val routingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT,
    /** Configured free-driving anchor (picker value; the active anchor follows guidance state). */
    val freeDrivingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT,
    /**
     * The active anchor resolved into a screen fraction inside the **visible map
     * area** (spec: smooth-follow — visible-area scenarios): the preset mapped into
     * the canvas minus the regions the phone's own overlays cover (next-turn card,
     * routing-status card, widget column, street-name pill). Published once and
     * consumed by BOTH the follow render target (ViewModel) and the marker
     * projection (screen) — never re-derived. Equals the preset fraction when no
     * overlay is measured.
     */
    val resolvedAnchor: ResolvedAnchor = ResolvedAnchor(
        VehicleAnchorPosition.DEFAULT.fx, VehicleAnchorPosition.DEFAULT.fy
    ),
    val autoZoomEnabled: Boolean = true,
    /**
     * Monotonic counter bumped exactly when the speed-driven auto-zoom
     * commits a magnification (spec: smooth-zoom — Eased zoom animation,
     * auto-zoom commit scenario). The screen observes it to start/retrack
     * the ~500 ms display animation. Never bumped by pinch/buttons/keys.
     */
    val autoZoomCommitTick: Int = 0,
    /** True while the FREE_DRIVE preset is suspended by a manual pan/zoom/rotate
     *  gesture; drives the re-center button so the driver can reset to the
     *  standard drive values (spec: map-modes — drive suspension and reset). */
    val driveSuspended: Boolean = false,
    /** True while the BROWSE map is not centered on the vehicle position;
     *  drives the re-center button (spec: map-modes — Browse re-center).
     *  Derived from the viewport, the canvas and the current fix — never from a
     *  remembered interaction — so it also covers a persisted viewport that is
     *  nowhere near the vehicle, and vehicle movement while browsing. */
    val browseReCenterVisible: Boolean = false,
    /**
     * True while the phone yields the map to a live car session (spec: `map-canvas-screen`
     * — The phone map canvas is suspended while a car session is active): the canvas is not
     * composed, no render is requested and the phone-owned render storage is released. The
     * shared native tile-data cache is NOT released — the car renders from it.
     */
    val phoneMapSuspended: Boolean = false,
    /**
     * True while the user asked for the phone map for the rest of the current car session
     * (the per-session override). Cleared when the session ends, so a later session suspends
     * again. Always false without a live session.
     */
    val phoneMapOverridden: Boolean = false,
    val freeFormNorthUp: Boolean = true,
    val navNorthUp: Boolean = false,
    val keepScreenOn: Boolean = true,
    val darkModePreference: DarkModePreference = DarkModePreference.AUTOMATIC,
    val ambientLightSensitivity: AmbientLightSensitivity = AmbientLightSensitivity.OFF,
    val isDarkPresentation: Boolean = false,
    val gpsFixQuality: GpsFixQuality = GpsFixQuality.NONE,
    val laneHintsEnabled: Boolean = true,
    val renderMode: RenderMode = RenderMode.TILES,
    /**
     * Overspeed warning delta (km/h, 0-30, default 5): the speed badge
     * warns when `current >= max + delta` (spec: map-speed-widget —
     * Overspeed warning color). Single global value shared with Android
     * Auto.
     */
    val overspeedWarningDeltaKmh: Int = 5,
    /** Selected map stylesheet (name without the .oss postfix), e.g. "standard". */
    val styleSheet: String = DEFAULT_STYLE_NAME,
    /** All bundled map styles offered by the picker (sorted, no .oss postfix). */
    val availableStyleSheets: List<String> = emptyList(),
    /** Last GPS fix for marker overlay; null if unavailable. */
    val gpsLocation: GpsFix? = null,
    /** Viewport that produced the currently visible bitmap. Marker overlay must use this. */
    val renderViewport: MapRenderer.RenderViewport? = null,
    /** Marker position for the overlay: nav-filtered in routing mode, raw GPS otherwise; NaN when unavailable. */
    val gpsMarkerLat: Double = Double.NaN,
    val gpsMarkerLon: Double = Double.NaN,
    /** Marker arrow bearing in degrees (freshest direction signal); < 0 = bearing unavailable (arrow points north). */
    val gpsMarkerBearing: Double = Double.NaN,
    /** GPS horizontal accuracy in meters for the accuracy circle; <= 0 = no circle. */
    val gpsMarkerAccuracy: Double = 0.0,
    /** Current vehicle speed from the GPS fix (km/h); NaN when unknown. Drives the on-map speed widget in follow mode. */
    val currentSpeedKmH: Double = Double.NaN,
    /** Max speed of the road at the GPS position (km/h); NaN when unknown. Drives the on-map speed widget in follow mode. */
    val maxSpeedKmH: Double = Double.NaN,
    /** Road at the GPS position from the bearing-aware lookup (spec: current-road-info free driving); null when none. */
    val currentRoadInfo: RoadInfo? = null,
    /**
     * Height the session's overlay covers, as the overlay reported it (spec:
     * `route-map-overview` — the fit uses the reported height; `phone-align-controls-in-all-modes`
     * — the right-side controls sit above it). 0 when no overlay is composed.
     */
    val overlayCoveredPx: Int = 0
) {
    /**
     * The magnification the display zoom animation eases toward (spec: smooth-zoom -
     * Eased zoom animation on discrete zoom input): the walk's current step while a
     * bounded magnification walk is pending, the committed viewport magnification
     * otherwise. The persisted [viewport] never carries a walked step.
     */
    val zoomAnimationTargetMag: Double
        get() = if (zoomWalkStepMag.isNaN()) viewport.magnification else zoomWalkStepMag
}

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
    private val sharedLocationHandler: SharedLocationHandler,
    private val basemapReloadNotifier: BasemapReloadNotifier,
    private val drivingModeProvider: DrivingModeProvider? = null,
    /**
     * Car-session presence, the source of the phone's suspension (spec: `map-canvas-screen`).
     * The default (an inert instance) exists for direct construction in tests; production
     * injects the process-scoped binding.
     */
    private val carSessionPresence: CarSessionPresence = CarSessionPresenceImpl(),
    /**
     * The clock the dwell decisions read (spec: `unit-test-suite-runtime` — Injected time instead of a
     * real clock): the BROWSE re-center dwell window compares against this seam, so a case moves time
     * instead of sleeping. The default exists for direct construction in tests; production injects the
     * process-scoped binding.
     */
    private val timeSource: EngineTimeSource = EngineTimeSource.System,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapCanvasUiState())
    val uiState: StateFlow<MapCanvasUiState> = _uiState.asStateFlow()

    /**
     * The current map mode (spec: map-modes), derived from navigation state and
     * follow mode — the single source of truth for the map's driving state.
     */
    val mode: MapMode
        get() = when {
            _navigationViewModel?.state?.value?.isNavigating == true -> MapMode.NAVIGATION
            _uiState.value.followMode || _uiState.value.driveSuspended -> MapMode.FREE_DRIVE
            else -> MapMode.BROWSE
        }

    /** Search history, youngest first. Loaded from disk on ViewModel init. */
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = searchHistoryRepository.history

    /** Favorites grouped by group name; drives the search-dialog suggestion rows. */
    val favoriteGroups: StateFlow<Map<String, List<com.framstag.libosmscout.client.FavoriteLocation>>> =
        favoriteRepository.favorites

    private val _gpsFixQuality = MutableStateFlow(GpsFixQuality.NONE)
    val gpsFixQuality: StateFlow<GpsFixQuality> = _gpsFixQuality.asStateFlow()

    /** Merges favorite hits with native search results (spec: favorite-search). */
    private val favoriteSearchMerger = FavoriteSearchMerger()

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

    /**
     * Background dispatcher for off-main work (search, POI lookup, admin
     * region resolution, candidate description fetch). Test hook: point it at
     * a TestDispatcher shared with the test's `runTest` so all coroutine work
     * runs on one scheduler (deterministic, no real thread pools).
     */
    @VisibleForTesting
    internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * The dispatcher the renderer's own scope runs on — its frame work and its debounce. Declared
     * separately from [defaultDispatcher] because a case may need to gate the ViewModel's
     * initialization while the renderer keeps running (the viewport-restore window), and because the
     * renderer scope is created once per `initMap`; a test drives it to move a debounce instead of
     * waiting real time for a frame (spec `unit-test-suite-runtime` — a unit test controls the time its
     * subject waits on; change `speed-up-test-iteration`, task 2.4).
     */
    internal var rendererDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Fix age limit behind the GPS fix quality (spec: `gps-fix-quality` — Fix availability and
     * quality tiers). Test hook: shorten it to observe the aged-out transition without waiting the
     * production 60 s.
     */
    @VisibleForTesting
    internal var fixAgeLimitMs: Long = FixFreshness.FIX_AGE_LIMIT_MS

    /**
     * Period of the fix-quality re-evaluation tick. Production is [FIX_QUALITY_TICK_MS]; test hook:
     * shorten it so a case sees several ticks inside a real-clock poll (the tick runs on the real
     * clock, so a tick-driven transition is waited for, not advanced to).
     */
    @VisibleForTesting
    internal var fixQualityTickMs: Long = FIX_QUALITY_TICK_MS

    /**
     * The OSMScoutClient, exposed for embeddable map widgets (e.g. [MiniMap])
     * that need their own renderer. Widgets only read from it; the main
     * map's viewport state stays untouched.
     */
    internal val osmscoutClient: OSMScoutClient get() = client
    private var currentMapKey: String? = null

    /**
     * True once [initMap] has applied the persisted viewport restore to
     * [_uiState]. [saveViewport] no-ops until then, so a lifecycle save
     * during the restore window (initMap suspended at the viewport load)
     * can never overwrite the previously persisted viewport with the
     * uninitialized default (spec: viewport-persist, "Pause during restore
     * does not clobber").
     */
    private var viewportRestored = false

    /**
     * The in-flight [initMap] coroutine. Cancelled on re-entry (map path
     * change: MAIN screen after a download/update/delete, basemap reload) so
     * a superseded init — still suspended between DB open and viewport
     * restore — can never apply its stale restore, re-arm [viewportRestored],
     * or overwrite [mapRenderer] with a second renderer (spec:
     * viewport-persist, "Re-entry supersedes an in-flight map init").
     */
    private var initJob: Job? = null
    private val epoch = AtomicLong(0)

    // Screen dimensions for zoom computation
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // Route panel view model — set via setRoutePanelViewModel() from screen
    private var _routePanelViewModel: RoutePanelViewModel? = null

    // In-flight POI search job (cancelled on re-search and sheet close)
    private var poiSearchJob: Job? = null

    // Viewport snapshot taken when POI search opens; restored when the sheet closes
    private var poiViewportSnapshot: ViewportState? = null

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

    // Max-speed resolution throttle (spec: map-speed-widget): re-resolve the
    // road's max speed only after significant movement or a cooldown.
    private var lastMaxSpeedLat = Double.NaN
    private var lastMaxSpeedLon = Double.NaN
    private var lastMaxSpeedResolveMs = 0L
    // Current-road resolution throttle (spec: current-road-info free driving).
    private var lastRoadLat = Double.NaN
    private var lastRoadLon = Double.NaN
    private var lastRoadResolveMs = 0L
    private var lastGpsLat = Double.NaN
    private var lastGpsLon = Double.NaN
    private var lastGpsTime: Long = 0L
    /** Last fix timestamp for the follow-mode stale-speed decay (spec: gps-speed-priority). */
    private var lastSpeedFixTime: Long = 0L
    private var smoothedCenterLat = Double.NaN
    private var smoothedCenterLon = Double.NaN
    // No center smoothing in follow mode: the viewport center must stay on the
    // raw GPS fix so the marker does not drift away from the road/track. Only
    // large jumps (>500 m) reset directly to avoid lag after teleports.
    private val centerSmoothAlpha = 1.0
    private val centerSmoothMaxJumpM = 500.0

    // ---- Single follow center (spec: smooth-follow — Prediction state update,
    // delta fix-follow-vehicle-jumps) ----
    // The displayed (eased predicted) position owned by the display loop.
    // MapCanvasScreen writes it every display frame; every follow render reads it
    // as the anchor-center source, so the per-fix and clamp-driven render paths
    // can no longer alternate between a raw-fix center and a predicted center
    // (the whole-frame jump). NaN until the first display frame — the marker fix
    // (the same position the display extrapolates from: engine position while
    // navigating, raw GPS otherwise) is the fallback.
    @Volatile
    internal var followDisplayLat: Double = Double.NaN
    @Volatile
    internal var followDisplayLon: Double = Double.NaN

    /** Follow render center: the displayed position once the display loop runs, else the marker fix. */
    private fun followRenderCenterLat(): Double =
        if (!followDisplayLat.isNaN()) followDisplayLat else smoothedCenterLat

    private fun followRenderCenterLon(): Double =
        if (!followDisplayLon.isNaN()) followDisplayLon else smoothedCenterLon

    // Auto-zoom state
    private var lastValidSpeedKmH: Double = 20.0
    private var autoZoomSuspended: Boolean = false

    // Vehicle anchors from the shared settings (spec: smooth-follow — Vehicle
    // position anchor): routing while guidance is active, free-driving otherwise.
    private var routingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT
    private var freeDrivingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    // Regions of the map canvas covered by the phone's own overlays (px), pushed by
    // the screen on every real size change (spec: smooth-follow — visible-area
    // scenarios). 0 = nothing measured = the preset fraction itself, which is the
    // pre-feature framing and the AA behavior.
    private var overlayInsetLeftPx: Int = 0
    private var overlayInsetTopPx: Int = 0
    private var overlayInsetRightPx: Int = 0
    private var overlayInsetBottomPx: Int = 0

    /** Projection DPI of the phone display (the value the renderer renders at). */
    private val projectionDpi: Double
        get() = context.resources.displayMetrics.densityDpi.toDouble()

    /**
     * Report the regions the map's own overlays cover (px), so the anchor preset
     * resolves against the **visible map area** rather than the whole canvas
     * (spec: smooth-follow — "Bottom anchor stays visible above the routing status
     * card" and the sibling scenarios). The screen calls this from
     * `Modifier.onSizeChanged`, i.e. only on a real size change; insets that did not
     * change cause no state emission.
     */
    fun setMapOverlayInsets(top: Int, bottom: Int, right: Int, left: Int = 0) {
        val t = top.coerceAtLeast(0)
        val b = bottom.coerceAtLeast(0)
        val r = right.coerceAtLeast(0)
        val l = left.coerceAtLeast(0)
        if (t == overlayInsetTopPx && b == overlayInsetBottomPx &&
            r == overlayInsetRightPx && l == overlayInsetLeftPx
        ) {
            return
        }
        overlayInsetTopPx = t
        overlayInsetBottomPx = b
        overlayInsetRightPx = r
        overlayInsetLeftPx = l
        publishResolvedAnchor()
    }

    /**
     * Resolve the active preset into the visible map area and publish it as
     * [MapCanvasUiState.resolvedAnchor] (single source used by the render target and
     * the marker projection). Called whenever the preset, the canvas size or the
     * overlay insets change.
     */
    private fun publishResolvedAnchor(
        anchor: VehicleAnchorPosition = _uiState.value.activeFollowAnchor
    ) {
        val resolved = resolveAnchorFraction(
            anchor,
            leftPx = overlayInsetLeftPx,
            topPx = overlayInsetTopPx,
            rightPx = overlayInsetRightPx,
            bottomPx = overlayInsetBottomPx,
            screenW = screenWidth,
            screenH = screenHeight
        )
        if (resolved != _uiState.value.resolvedAnchor) {
            _uiState.value = _uiState.value.copy(resolvedAnchor = resolved)
        }
    }

    /**
     * Anchor-centered render target for [lat]/[lon] under the active follow
     * anchor (spec: smooth-follow — Anchor-centered follow framing): the
     * viewport center that projects the position to the *resolved* anchor screen
     * fraction (visible-area mapped, see [publishResolvedAnchor]) under [angle].
     * The anchor is applied HERE and nowhere else — the follow blit then carries
     * the prediction drift only. Falls back to the raw position while the canvas
     * size is unknown.
     */
    internal fun followRenderTarget(
        lat: Double, lon: Double, mag: Double, angle: Double
    ): Pair<Double, Double> {
        if (screenWidth <= 0 || screenHeight <= 0) return lat to lon
        val anchor = _uiState.value.resolvedAnchor
        return anchorCenter(
            lat, lon, anchor.fx, anchor.fy, mag,
            screenWidth, screenHeight, projectionDpi, normalizeAngle(angle)
        )
    }

    /** Keep the internal suspension flag and its uiState mirror in sync. */
    private fun setAutoZoomSuspended(suspended: Boolean) {
        autoZoomSuspended = suspended
        _uiState.value = _uiState.value.copy(driveSuspended = suspended)
    }
    private var lastSpeedBandIndex: Int = -1
    private var currentTargetMag: Double = 15.0

    // Zoom-change throttling (spec: auto-speed-zoom — Smooth zoom
    // transitions): lastAutoZoomCommitMs == 0L means "no commit yet" (direct
    // jump to target on the first fix, spec's "Speed unknown" scenario);
    // after that the zoom converges at most MAX_ZOOM_STEP_PER_UPDATE levels
    // per position update via SpeedZoomTable.stepToward (fractional, no
    // integer rounding). The epsilon no-op makes constant speeds commit-nothing.
    private var lastAutoZoomCommitMs: Long = 0L

    // Bounded magnification walk (spec: smooth-zoom - Eased zoom animation on discrete
    // zoom input): the viewport records the FINAL requested magnification while the
    // render pipeline and the display animation advance to it in steps the frame in
    // hand can serve - one step per LANDED render. Owns the step arithmetic only;
    // scheduling lives here and in the frame collector.
    private val zoomWalk = ZoomWalk()

    // Bearing fallbacks: last used bearing/angle survive when the location
    // layer has no fresh bearing yet (e.g. standstill).
    private var lastUsedBearing = Double.NaN
    private var lastUsedAngle = Double.NaN
    // Deadband vs the rendered angle: map rotation only moves when the smoothed
    // bearing exceeds this — prevents re-rendering on every small change.
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

    // Last route result the camera was fitted to (spec: route-map-overview —
    // stale re-emissions must not move a user-moved viewport). Reset to null
    // when the route is cleared so an identical re-calculation can refit.
    private var lastFittedResult: RouteResult? = null

    // Pending route-overview fit (spec: route-map-overview, Decision 8). The fit
    // runs one settle delay after the result arrives so the route panel reports
    // its final measured height; a newer result, a cleared route, or a user
    // gesture inside the window cancels it.
    private var routeFitJob: Job? = null

    /** Get the current navigation position for marker rendering. */
    fun getNavigationPosition(): com.framstag.libosmscout.client.NavigationPosition? = _navPosition

    /** Filter speed spikes: reject speed above the plausibility ceiling, use last good speed. */
    private fun filterSpeed(rawSpeedKmH: Double): Double {
        if (rawSpeedKmH >= 0 && rawSpeedKmH <= MAX_PLAUSIBLE_SPEED_KMH) {
            lastValidSpeedKmH = rawSpeedKmH
        }
        return lastValidSpeedKmH
    }

    /**
     * Push the marker state to the renderer (for the next render job) AND to the
     * overlay state directly. The overlay must show the latest fix immediately —
     * waiting for the next rendered frame would lag the marker behind the vehicle
     * (throttled renders, no render on < 5 m movement). The overlay projects the
     * marker against the displayed frame's viewport, so a fresh fix on a stale map
     * is still anchored correctly.
     */
    private fun updateMarkerState(lat: Double, lon: Double, bearing: Double, accuracy: Double) {
        mapRenderer?.setGpsMarkerState(lat, lon, bearing, accuracy)
        _uiState.value = _uiState.value.copy(
            gpsMarkerLat = lat,
            gpsMarkerLon = lon,
            gpsMarkerBearing = bearing,
            gpsMarkerAccuracy = accuracy
        )
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
            settingsStorage.update { it.copy(keepScreenOn = enabled) }
        }
    }

    /** Toggle lane hints on/off. */
    fun onToggleLaneHints(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(laneHintsEnabled = enabled)
        viewModelScope.launch {
            settingsStorage.update { it.copy(laneHintsEnabled = enabled) }
        }
    }

    /**
     * Set the overspeed warning delta (km/h, 0-30). Applies to the speed
     * widget immediately and persists; the same global value drives the
     * Android Auto badge (spec: map-speed-widget — Overspeed warning color;
     * location-options-ui — Overspeed warning delta control).
     */
    fun onSetOverspeedWarningDelta(deltaKmh: Int) {
        _uiState.value = _uiState.value.copy(overspeedWarningDeltaKmh = deltaKmh)
        viewModelScope.launch {
            settingsStorage.update { it.copy(overspeedWarningDeltaKmh = deltaKmh) }
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
            settingsStorage.update { it.copy(renderMode = mode) }
        }
        mapRenderer?.renderMode = mode
        mapRenderer?.invalidateStyle()
    }

    /** Feed the environment dimming signal (system night mode) from composition. */
    fun setEnvironmentDark(dark: Boolean) {
        darkModeController.setEnvironmentDark(dark)
    }

    /** Feed the ambient light sensor classification (null = inactive). */
    fun setSensorDark(dark: Boolean?) {
        darkModeController.setSensorDark(dark)
    }

    /** Set the dark mode preference (On / Off / Automatic); persists via controller. */
    fun onSetDarkModePreference(preference: DarkModePreference) {
        darkModeController.setPreference(preference)
        _uiState.value = _uiState.value.copy(darkModePreference = preference)
    }

    /** Set the ambient light sensor sensitivity; persists via controller. */
    fun onSetAmbientLightSensitivity(sensitivity: AmbientLightSensitivity) {
        darkModeController.setSensorSensitivity(sensitivity)
        _uiState.value = _uiState.value.copy(ambientLightSensitivity = sensitivity)
    }

    /**
     * The stylesheet and `daylight` flag pair the native side holds, or `null` while nothing has
     * been applied yet. Both native calls reload the whole stylesheet set (`loadStyleSheet` and
     * `setStyleSheetFlag`), so this pair is the dedupe key (spec: map-styles - One stylesheet
     * load per active style and flag set; `guidelines/MapRendering.md` section 15/16). `styleName`
     * is `null` after a flag-only push: the flag does not say which stylesheet is installed.
     */
    private var appliedMapStyle: AppliedMapStyle? = null

    /** True once the flag was pushed with a map database open (see [MapStyleApplyReason]). */
    private var stylePushedToNative = false

    private data class AppliedMapStyle(val styleName: String?, val daylight: Boolean)

    /**
     * What justifies one application of the style/flag pair through [ensureMapStyle]. Only the
     * re-push reasons force native work for a pair that already matches the recorded one.
     */
    private enum class MapStyleApplyReason(
        /** Re-push the flag even when its value equals the recorded one. */
        val forceFlag: Boolean,
        /** Load the stylesheet even when the recorded one already is the requested one. */
        val forceLoad: Boolean = false,
    ) {
        /** A re-observation of the current state (init settings load, panel rebuild). */
        RE_OBSERVED(false),

        /** The user picked a style in the picker; applied even before a map opens. */
        STYLE_SWITCH(false),

        /**
         * A map database has just been opened: `SetStyleFlag` was a no-op before it existed, and
         * the freshly opened database carries no style config of its own, so both halves are
         * re-applied.
         */
        STARTUP_DB_READY(true, true),

        /** The first rendered frame after [STARTUP_DB_READY]: re-push the flag once. */
        FIRST_RENDERED_FRAME(true),
    }

    /** Resolved presentation the stylesheet `daylight` flag has to carry. */
    private fun resolvedDaylight(): Boolean = darkModeController.isDarkPresentation.value

    /**
     * The one entry point that applies the style/flag pair to the native client (spec: map-styles -
     * One stylesheet load per active style and flag set): a request whose pair equals the recorded
     * one performs no native call, unless [reason] says the native side may have dropped earlier
     * work. Pass `null` for [styleName] on a request that concerns only the flag.
     *
     * A rejected load keeps the previous style and clears the recorded stylesheet, so the next
     * request retries it. [allowDefaultFallback] is used on the startup path only: a persisted style
     * whose stylesheet cannot be loaded falls back to `standard`, so the first map display is never
     * empty (spec: map-styles - "Persisted style fails at startup").
     */
    private suspend fun ensureMapStyle(
        styleName: String?,
        daylight: Boolean,
        reason: MapStyleApplyReason,
        allowDefaultFallback: Boolean = false,
    ): Boolean {
        if (reason.forceFlag || appliedMapStyle?.daylight != daylight) {
            pushDaylight(daylight)
        }
        if (styleName != null && (reason.forceLoad || appliedMapStyle?.styleName != styleName)) {
            return loadMapStyle(styleName, daylight, allowDefaultFallback)
        }
        return true
    }

    /**
     * Pushes the `daylight` flag; the native side reloads the active stylesheet with it
     * (`guidelines/MapRendering.md` section 15). The recorded flag is updated only after the call
     * returned, so a later request re-pushes a value the native side never took.
     */
    private fun pushDaylight(daylight: Boolean) {
        try {
            client.setStyleSheetFlag(STYLE_FLAG_DAYLIGHT, !daylight)
            // The flag change reloads the active stylesheet; when that load is
            // rejected the previously active style stays in effect and the
            // failure is reported (spec: map-styles — "Style flag change fails").
            reportStyleLoadFailure(activeStyleSheet() ?: DEFAULT_STYLE_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "setStyleSheetFlag failed", e)
            return
        }
        appliedMapStyle = AppliedMapStyle(appliedMapStyle?.styleName, daylight)
        mapRenderer?.invalidateStyle()
    }

    /**
     * Load the map style [name] on the native database thread (blocking — runs
     * off the main thread) and re-render on success. On failure the native side
     * keeps the previous style and the failure is reported once through
     * [MapStyleLoadReporter]; the recorded stylesheet is cleared so the next request
     * retries it.
     *
     * [allowDefaultFallback] is used on the startup path only: a persisted style
     * whose stylesheet cannot be loaded falls back to the default `standard`
     * style, so the first map display is never empty (spec: map-styles —
     * "Persisted style fails at startup").
     */
    private suspend fun loadMapStyle(
        name: String,
        daylight: Boolean,
        allowDefaultFallback: Boolean,
    ): Boolean {
        val ok = withContext(defaultDispatcher) {
            try {
                client.loadStyleSheet(name)
            } catch (e: Exception) {
                Log.e(TAG, "loadStyleSheet failed for '$name'", e)
                false
            }
        }
        if (ok) {
            appliedMapStyle = AppliedMapStyle(name, daylight)
            mapRenderer?.invalidateStyle()
            return true
        }

        appliedMapStyle = AppliedMapStyle(null, daylight)
        Log.e(TAG, "loadStyleSheet returned false for '$name' — previous style kept")
        reportStyleLoadFailure(name)

        if (allowDefaultFallback && name != DEFAULT_STYLE_NAME) {
            Log.w(TAG, "Persisted style '$name' unavailable — falling back to '$DEFAULT_STYLE_NAME'")
            return loadMapStyle(DEFAULT_STYLE_NAME, daylight, allowDefaultFallback)
        }

        return false
    }

    /**
     * Reports one failed stylesheet load through the shared seam: a diagnostics
     * entry plus the non-blocking message shown next to the map (spec:
     * map-styles). Called once per load attempt; a report produced while no map
     * surface is visible stays in the UI state and is shown when the map is next
     * composed.
     */
    /** The native side's active stylesheet, or `null` when the client cannot answer. */
    private fun activeStyleSheet(): String? = try {
        client.getActiveStyleSheet()
    } catch (e: Exception) {
        Log.e(TAG, "getActiveStyleSheet failed", e)
        null
    }

    private fun reportStyleLoadFailure(requestedStyle: String) {
        val succeeded = try {
            client.wasLastStyleLoadSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "wasLastStyleLoadSuccessful failed", e)
            true
        }
        val activeStyle = activeStyleSheet()
        val message = MapStyleLoadReporter.reportFailure(
            resolver = context.stringResolver(),
            requestedStyle = requestedStyle,
            activeStyle = activeStyle,
            loadSucceeded = succeeded
        )
        if (message != null) {
            showSnackbar(message)
        }
    }

    /** Select and persist a map style; applies it to the renderer immediately. */
    fun onStyleSheetSelected(name: String) {
        _uiState.value = _uiState.value.copy(styleSheet = name)
        viewModelScope.launch {
            settingsStorage.update { it.copy(styleSheet = name) }
        }
        viewModelScope.launch {
            ensureMapStyle(name, resolvedDaylight(), MapStyleApplyReason.STYLE_SWITCH)
        }
    }

    /** Toggle auto-zoom on/off. */
    fun onToggleAutoZoom(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoZoomEnabled = enabled)
        viewModelScope.launch {
            settingsStorage.update { it.copy(autoZoomEnabled = enabled) }
        }
        if (enabled) {
            // Reset suspension state so zoom adjusts immediately
            setAutoZoomSuspended(false)
            lastSpeedBandIndex = -1
        }
    }

    /**
     * One user-triggered search request: the query text plus a monotonic sequence.
     *
     * The sequence makes a request an identity of its own, so a request whose text
     * repeats the previous one is still a *different* value: `StateFlow` conflates
     * equal values, and a plain query string would drop the replay of a recent-search
     * chip (spec: `location-search` — A repeated query text still runs the search;
     * spec: `search-history` — History selection replays the search).
     */
    private data class SearchRequest(val query: String, val seq: Long)

    /**
     * The search trigger — debounced and collected for location search.
     *
     * Every requester publishes a [SearchRequest]; the request carries its own text, so
     * the displayed query ([MapCanvasUiState.searchQuery]) and the search that actually
     * ran can never disagree (`guidelines/Design.md` §3 — one truth per value).
     */
    private val _searchRequest = MutableStateFlow(SearchRequest("", 0L))

    /** Set once the map database is open and the renderer is ready. */
    private val mapReady = MutableStateFlow(false)

    /**
     * Counter driving the fix-quality re-evaluation (spec: `gps-fix-quality` — Quality is
     * re-evaluated without a new fix). A `StateFlow` so the combined flow has a value immediately and
     * the first quality never waits for a tick.
     *
     * Declared **before** [init] on purpose: `viewModelScope` is `Dispatchers.Main.immediate`, so the
     * ticker the init block launches runs its body (and `combine`'s collectors) during construction,
     * and a field declared after the init block is still null there — a startup `NullPointerException`
     * that only an immediate main dispatcher exposes.
     */
    private val fixQualityTicks = MutableStateFlow(0L)

    /**
     * How many fix-quality ticks have completed, counted for **every** tick rather than only for a
     * detected change. Internal test seam (spec `unit-test-suite-runtime` — Awaiting state, not a
     * deadline): the tick loop deliberately runs on a real dispatcher with the real clock, so a case that
     * needs "several ticks with nothing changing" awaits this counter instead of a wall-clock window.
     */
    internal val fixQualityTickCount = MutableStateFlow(0L)

    init {
        viewModelScope.launch { searchHistoryRepository.load() }
        refreshAddressBookAvailability()

        // Publish FREE_DRIVE mode to the shared driving-state provider: the
        // phone surface's vote is `followMode || driveSuspended` (identical to
        // this ViewModel's mode derivation, spec: map-modes). One observer on
        // the source of truth — no per-transition publish sites to miss. The
        // provider is nullable only for Robolectric tests that omit it.
        val drivingModeProvider = drivingModeProvider
        if (drivingModeProvider != null) {
            viewModelScope.launch {
                _uiState.map { it.followMode || it.driveSuspended }
                    .distinctUntilChanged()
                    .collect { freeDriving ->
                        drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, freeDriving)
                    }
            }
        }

        // Basemap data changes (download/update/delete while the app runs):
        // invalidate cached tiles and force a re-render so the change shows
        // without an app restart (spec: basemap-loading — current view
        // re-renders with/without the basemap overlay active).
        viewModelScope.launch {
            basemapReloadNotifier.revision.collect { revision ->
                if (revision > 0L) {
                    mapRenderer?.invalidateData()
                }
            }
        }

        // Car-session suspension (spec: map-canvas-screen — The phone map canvas is
        // suspended while a car session is active). ONE collector owns both the render gate
        // and the storage release, so "no phone renders" and "storage released" can never
        // disagree. Runs on the main dispatcher (viewModelScope): the edge writes UI state.
        viewModelScope.launch {
            carSessionPresence.active.collect { active -> onCarSessionPresenceChanged(active) }
        }

        // Shared locations (share sheet / deep links): process when the map
        // screen is up. Coordinates go through the candidate flow; address text
        // opens the search panel with the query filled.
        viewModelScope.launch {
            sharedLocationHandler.request.collect { request ->
                if (request != null) processSharedLocation(request)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        viewModelScope.launch {
            _searchRequest
                .debounce(300L)
                .filter { it.query.length >= 2 || it.query.isEmpty() }
                .flatMapLatest { request ->
                    if (request.query.length < 2) {
                        _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
                        return@flatMapLatest flowOf(emptyList<MergedSearchResult>())
                    }
                    _uiState.value = _uiState.value.copy(isSearching = true)
                    flowOf(mergeSearchResults(request.query))
                }
                .collect { results ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
        }

        // Fix-quality re-evaluation tick (spec: gps-fix-quality — Quality is re-evaluated without a
        // new fix). A counter in a StateFlow, not a cold flow: the combined value must appear as soon
        // as the location flow has one, without waiting for a tick.
        //
        // The loop runs on Dispatchers.Default with the REAL clock — the rule the stale-speed ticker
        // below documents (TODO.md §40.C.16): an endless delay loop on the virtual clock of a test
        // scheduler would keep every `advanceUntilIdle` non-idle. It reports a tick **only** when its
        // own derivation differs from the published quality, because the collector below lives on the
        // main dispatcher: a tick per period would read the main dispatcher from a real thread pool
        // every second, and for a ViewModel that outlives its test that is a read against another
        // test's `Dispatchers.setMain`, failing that later test with `Dispatchers.Main is used
        // concurrently with setting it` (spec: gps-fix-quality — Fix-quality re-evaluation stays off
        // the main thread; TODO.md §121). An unchanged quality therefore costs no main-dispatcher
        // traffic at all, while a real change is detected within one tick and published through the
        // one debounced pipeline below. The comparison is against the published value, so ticks that
        // arrive while a change is still inside the debounce repeat it; the pipeline's own
        // `distinctUntilChanged()` drops them.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(fixQualityTickMs)
                fixQualityTickCount.value = fixQualityTickCount.value + 1L
                if (deriveFixQuality(locationService.location.value) != _gpsFixQuality.value) {
                    fixQualityTicks.value = fixQualityTicks.value + 1L
                }
            }
        }

        // GPS fix quality: re-derived on a slow tick as well as on every fix. The location source
        // goes silent when there is nothing new to report — location services switched off, or the
        // minimum-distance throttling at standstill — so a value computed only on emission would
        // stay latched at its last tier forever (spec: gps-fix-quality — Quality is re-evaluated
        // without a new fix). Debounced to prevent flicker; a tick that derives the same quality
        // publishes nothing.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(locationService.location, fixQualityTicks) { loc, _ -> loc }
                .map { loc -> deriveFixQuality(loc) }
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
                            withContext(defaultDispatcher) { currentSearchAdminRegionHandle() }
                        }
                    }
                }
        }

        // Resolved dark presentation drives theme state + the native style sheet flag. The flag
        // reloads the style variant natively; loading a stylesheet is not part of this path, so the
        // style name is deliberately left out of the request (spec: dark-mode - Unchanged
        // presentation reloads nothing; spec: map-styles - One stylesheet load per active style and
        // flag set).
        viewModelScope.launch {
            darkModeController.isDarkPresentation
                .collect { dark ->
                    _uiState.value = _uiState.value.copy(isDarkPresentation = dark)
                    ensureMapStyle(null, dark, MapStyleApplyReason.RE_OBSERVED)
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

        // Stale-speed decay (spec: gps-speed-priority — stationary reads zero).
        // The provider goes silent at standstill (min-distance throttling), so
        // the follow-mode speed must decay to 0 once no fresh fix arrives — the
        // location flow still delivers the LAST fix, which the collect below
        // would otherwise keep re-rendering with its pre-stop speed.
        // Runs on Dispatchers.Default with the REAL clock: the state holders
        // must not feed the (virtual) test scheduler with an endless delay
        // loop, and the follow-mode gate keeps tests that never enable follow
        // mode free of stray zero-writes.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(SPEED_STALE_TICK_MS)
                if (_uiState.value.followMode &&
                    SpeedStaleness.isStale(lastSpeedFixTime, System.currentTimeMillis())
                ) {
                    _uiState.value = _uiState.value.copy(currentSpeedKmH = 0.0)
                }
            }
        }

        // Browse re-center visibility (spec: map-modes — Browse re-center): one
        // collector re-derives it from the viewport, the canvas and the fix, so
        // every pan, zoom and position update is covered without hooking each
        // mutation site. refreshBrowseReCenter emits only on a visibility change,
        // so it does not re-trigger itself forever.
        viewModelScope.launch {
            uiState.collect { refreshBrowseReCenter(it) }
        }

        // Follow mode: keep map center and marker in sync.
        viewModelScope.launch {
            var lastRenderedLat = Double.NaN
            var lastRenderedLon = Double.NaN

            locationService.location.collect { fix ->
                if (fix == null) {
                    _uiState.value = _uiState.value.copy(
                        gpsLocation = null,
                        gpsMarkerLat = Double.NaN,
                        gpsMarkerLon = Double.NaN,
                        gpsMarkerBearing = Double.NaN,
                        gpsMarkerAccuracy = 0.0
                    )
                    mapRenderer?.clearGpsMarkerState()
                    lastMarkerLat = Double.NaN
                    lastMarkerLon = Double.NaN
                    return@collect
                }

                _uiState.value = _uiState.value.copy(
                    gpsLocation = fix,
                    currentSpeedKmH = fix.speedKmH
                )
                lastSpeedFixTime = fix.time

                if (logCount++ % 30 == 0) {
                    Log.d(TAG, "GPS fix acc=${"%.1f".format(fix.accuracy)} " +
                            "bearing=${if (!fix.markerBearing.isNaN()) "%.1f".format(fix.markerBearing) else "-"} " +
                            "speed=${if (!fix.speedKmH.isNaN()) "%.1f".format(fix.speedKmH) else "-"} " +
                            "follow=${_uiState.value.followMode}")
                }

                // Use navigation position if available (filtered by engine), else raw GPS
                val navPos = _navPosition
                val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
                // Vehicle anchor: routing while route guidance is active, else the
                // free-driving anchor (spec: smooth-follow — Vehicle position
                // anchor in follow mode).
                val activeAnchor = if (isNavigating) routingAnchor else freeDrivingAnchor
                if (activeAnchor != _uiState.value.activeFollowAnchor) {
                    _uiState.value = _uiState.value.copy(activeFollowAnchor = activeAnchor)
                    // Re-resolve into the visible map area for the new mode's preset.
                    publishResolvedAnchor(activeAnchor)
                }
                val markerLat = if (isNavigating && navPos != null && !navPos.lat.isNaN()) navPos.lat else fix.lat
                val markerLon = if (isNavigating && navPos != null && !navPos.lon.isNaN()) navPos.lon else fix.lon

                // Deduplicate duplicate fixes delivered by multiple providers in the same
                // millisecond. Compare by timestamp and coarse coordinates only; bearing
                // may differ between Fused and LocationManager even for the same fix.
                val freshestBearing = if (!fix.markerBearing.isNaN()) fix.markerBearing else -1.0
                val accuracy = fix.accuracy
                val sameFix = (fix.time - lastGpsTime) < GPS_DEDUPE_MS &&
                        kotlin.math.abs(fix.lat - lastGpsLat) < 1e-6 &&
                        kotlin.math.abs(fix.lon - lastGpsLon) < 1e-6
                if (sameFix) {
                    // Duplicate fix — skip render work. Navigation is fed by the
                    // engine's own location lease (spec: `navigation-engine` — one
                    // engine per process feeds the native controller), not by this
                    // map pipeline.
                    return@collect
                }
                lastGpsLat = fix.lat
                lastGpsLon = fix.lon
                lastGpsTime = fix.time

                // Resolve the road's max speed for the follow-mode speed widget
                // (throttled: only on significant movement or after a cooldown).
                resolveMaxSpeed(fix.lat, fix.lon)
                // Resolve the current road for the free-driving street label
                // (bearing-aware, throttled; spec: current-road-info).
                resolveCurrentRoad(fix.lat, fix.lon, fix.markerBearing)

                if (!_uiState.value.followMode ||
                    (mode == MapMode.FREE_DRIVE && _uiState.value.driveSuspended)
                ) {
                    // The marker is a Compose overlay: update it on every fix so it
                    // tracks the vehicle immediately, independent of the render cadence.
                    updateMarkerState(markerLat, markerLon, freshestBearing, accuracy)
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

                // Map rotation uses the location layer's smoothed bearing; the
                // marker arrow uses the freshest bearing (no added lag). Provider
                // knowledge (Fused vs LocationManager) stays inside LocationService.
                val isNorthUp = when (mode) {
                    MapMode.NAVIGATION, MapMode.FREE_DRIVE -> _uiState.value.navNorthUp
                    MapMode.BROWSE -> _uiState.value.freeFormNorthUp
                }
                val effectiveBearing = if (!fix.smoothedBearing.isNaN()) fix.smoothedBearing else lastUsedBearing
                // The marker arrow tracks the freshest direction signal so it points
                // along the new driving direction immediately after a turn, while the
                // map rotation uses the smoothed value and rotates at its own pace.
                val markerBearingRaw = if (!fix.markerBearing.isNaN()) fix.markerBearing else lastUsedBearing
                // Orientation mode must NOT influence the arrow: north-up is a map-rotation
                // choice, not a bearing-availability state (spec: gps-location-marker).
                val markerBearing = computeMarkerBearing(isNorthUp, markerBearingRaw)
                // Feed the marker state to the renderer (snapshotted into the next render
                // job) AND to the overlay state directly — the overlay must show the latest
                // fix immediately, not wait for the next rendered frame.
                updateMarkerState(followMarkerLat, followMarkerLon, markerBearing, accuracy)
                val smoothedAngle = if (!isNorthUp && !effectiveBearing.isNaN()) computeMapAngle(isNorthUp, effectiveBearing) else Double.NaN
                val renderedAngle = normalizeAngle(mapRenderer?.renderedAngle ?: _uiState.value.viewport.angle)
                // "Always north" commits 0 on EVERY update: the last-angle fallback is
                // a follow-direction rule only, so a stale heading-up angle can never
                // rotate the map while north-up is selected (spec: compass-settings —
                // "Switching to north-up while driving stops further rotation";
                // gps-render-coalescing — "Course unavailable").
                val angle = when {
                    isNorthUp -> 0.0
                    !smoothedAngle.isNaN() -> {
                        val deltaDeg = Math.toDegrees(normalizeAngle(smoothedAngle - renderedAngle))
                        if (kotlin.math.abs(deltaDeg) < MIN_BEARING_DELTA_DEG) {
                            renderedAngle
                        } else {
                            val maxDeltaRad = Math.toRadians(MAX_ANGLE_RATE_DEG_PER_RENDER)
                            val deltaRad = normalizeAngle(smoothedAngle - renderedAngle)
                            val clampedDelta = deltaRad.coerceIn(-maxDeltaRad, maxDeltaRad)
                            normalizeAngle(renderedAngle + clampedDelta)
                        }
                    }
                    // Follow direction without a valid course yet: stay at the last
                    // used follow-direction angle (or North-Up if none) so the map
                    // neither spins nor snaps to north (spec: gps-render-coalescing —
                    // "Keep last valid course bearing").
                    !lastUsedAngle.isNaN() -> lastUsedAngle
                    else -> 0.0
                }
                if (!effectiveBearing.isNaN()) lastUsedBearing = effectiveBearing
                // Only follow-direction angles are remembered: a north-up period must
                // not erase the last driving direction, so switching back to follow
                // direction without a usable bearing resumes it instead of snapping
                // to north (design D2).
                if (!isNorthUp && !angle.isNaN()) lastUsedAngle = angle

                // Distance since last actual render; used to decide whether a new
                // native render is worth the cost.
                val distanceMeters = if (!lastRenderedLat.isNaN()) {
                    distanceMeters(smoothedLat, smoothedLon, lastRenderedLat, lastRenderedLon)
                } else Double.POSITIVE_INFINITY

                val positionChanged = distanceMeters > 5.0 || lastRenderedLat.isNaN()
                val angleChanged = !isAngleSame(angle, _uiState.value.viewport.angle)
                // Single-follow-center (delta fix-follow-vehicle-jumps): position
                // scroll is owned by the display loop (blit within the overrun margin),
                // so a fix no longer triggers a render on its own. Renders fire for
                // rotation changes, zoom commits, and the initial frame before the
                // display loop has a position.
                val displayActive = !followDisplayLat.isNaN() && !followDisplayLon.isNaN()
                val shouldRender = (positionChanged && !displayActive) || angleChanged

                // Anchor-centered follow framing (spec: smooth-follow — Anchor-centered
                // follow framing): one place applies the anchor — the render target.
                // The follow blit then carries the prediction drift only, and the
                // marker overlay needs no anchor of its own. viewport.center stays the
                // geo position at the screen center (the anchor center in follow mode),
                // so gesture math and the mini-map keep working unchanged.
                //
                // Single follow center (delta fix-follow-vehicle-jumps): the frame is
                // anchored on the DISPLAYED (eased predicted) position, not on the raw
                // fix — the renderer target stays on the position the display loop
                // scrolls, and the marker (same source) stays on the road.
                fun followTarget(mag: Double) = followRenderTarget(
                    followRenderCenterLat(), followRenderCenterLon(), mag, angle
                )

                val (targetLat, targetLon) = followTarget(zoomWalk.renderMag(_uiState.value.viewport.magnification))

                // Always keep renderer's target viewport current so the next
                // render (coalesced or not) is centered on the anchor center.
                mapRenderer?.prepareViewport(targetLat, targetLon, zoomWalk.renderMag(_uiState.value.viewport.magnification), angle)

                // Coalesce follow-mode renders so GPS ticks cannot overrun the render pipeline.
                val now = timeSource.nowMillis()
                val throttleElapsed = now - lastFollowRenderMs >= GPS_FOLLOW_RENDER_INTERVAL_MS
                if (!throttleElapsed && shouldRender) {
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
                        setAutoZoomSuspended(false)
                    }

                    if (!autoZoomSuspended) {
                        // Design D1 (spec: auto-speed-zoom — Smooth zoom
                        // transitions): commit the fractional target (turn/
                        // curve/post-turn floors included) without integer
                        // rounding. The first commit jumps straight to the
                        // target (keeps the spec's "Speed unknown" scenario);
                        // later commits converge via a distance-proportional
                        // step (ZOOM_CONVERGENCE_GAIN × remaining gap, capped
                        // at ≤ 0.5 levels per update) — fast when far away,
                        // gentle when near, so speed-noise target jitter is
                        // damped instead of chased (no zoom "pumping").
                        // stepToward returns current unchanged below epsilon →
                        // no commit, and since the mag did not change, no
                        // re-render either.
                        val target = finalTarget.coerceIn(MIN_MAG, MAX_MAG)
                        val hasCommitted = lastAutoZoomCommitMs > 0L
                        val stepped = if (hasCommitted) SpeedZoomTable.stepToward(newMag, target) else target
                        if (stepped != newMag) {
                            newMag = stepped
                            currentTargetMag = target
                            lastAutoZoomCommitMs = now
                            zoomCommitted = true
                            // The speed target is recorded immediately, but the display
                            // must not land the whole difference in one frame: start (or
                            // extend) the bounded walk from the displayed frame.
                            beginZoomWalk(stepped)
                            Log.d(TAG, "autoZoom commit speed=" + "%.1f".format(filteredSpeed) +
                                    "target=$target mag=$stepped")
                        } else if (logCount++ % 30 == 0) {
                            Log.d(TAG, "autoZoom hold speed=" + "%.1f".format(filteredSpeed) +
                                    "target=$target current=$newMag")
                        }
                        lastSpeedBandIndex = currentBand
                    }
                }

                // Center renders only for rotation, zoom, or the initial frame — the
                // display loop scrolls position within the overrun margin (spec:
                // smooth-follow — Prediction state update). An exact double comparison
                // would re-render on every GPS tick (sub-meter jitter) — that caused
                // an endless render loop.
                val viewportMoved = shouldRender ||
                        newMag != _uiState.value.viewport.magnification

                if (viewportMoved) {
                    // Track the position the frame is anchored on (displayed center,
                    // or the marker fix before the display loop is active) so the next
                    // distance check is against the actual rendered center.
                    lastRenderedLat = followRenderCenterLat()
                    lastRenderedLon = followRenderCenterLon()
                    // Committed center = anchor center for the magnification the frame will
                    // actually be rendered at: the walk's current step while one is pending
                    // (a walk renders the step, not the recorded target).
                    val renderedMag = zoomWalk.renderMag(newMag)
                    val (commitLat, commitLon) = followTarget(renderedMag)
                    _uiState.value = _uiState.value.copy(
                        viewport = _uiState.value.viewport.copy(
                            centerLat = commitLat,
                            centerLon = commitLon,
                            angle = normalizeAngle(angle),
                            magnification = newMag
                        ),
                        // Auto-zoom display-animation tick (spec: smooth-zoom):
                        // bumped only on auto-zoom commits — the screen starts
                        // the ~500 ms center-anchored easing on this.
                        autoZoomCommitTick = if (zoomCommitted) {
                            _uiState.value.autoZoomCommitTick + 1
                        } else {
                            _uiState.value.autoZoomCommitTick
                        }
                    )
                    mapRenderer?.prepareViewport(commitLat, commitLon, newMag, angle)
                    // In follow mode the marker represents the current vehicle position and
                    // is drawn by the Compose overlay at the raw/navigation GPS fix, projected
                    // against the emitted frame viewport (which is anchor-centered). The
                    // viewport moved — render the anchor-centered frame.
                    renderMap()
                    lastFollowRenderMs = timeSource.nowMillis()
                } else {
                    // No viewport motion; marker stays at the raw GPS fix via the overlay.
                }
            }
        }

        // Load persisted settings
        viewModelScope.launch {
            val settings = settingsStorage.load()
            darkModeController.restorePreference(settings.darkMode)
            darkModeController.restoreSensorSensitivity(settings.ambientLightSensitivity)
            _uiState.value = _uiState.value.copy(
                // followMode is deliberately NOT restored: the app always starts
                // in BROWSE (spec: map-modes — always browse on start); free
                // drive is per-session intent entered via the mode toggle.
                autoZoomEnabled = settings.autoZoomEnabled,
                freeFormNorthUp = settings.freeFormNorthUp,
                navNorthUp = settings.navNorthUp,
                keepScreenOn = settings.keepScreenOn,
                darkModePreference = settings.darkMode,
                ambientLightSensitivity = settings.ambientLightSensitivity,
                laneHintsEnabled = settings.laneHintsEnabled,
                renderMode = settings.renderMode,
                overspeedWarningDeltaKmh = settings.overspeedWarningDeltaKmh,
                styleSheet = settings.styleSheet
            )
            routingAnchor = VehicleAnchorPosition.fromId(settings.routingAnchorId)
            freeDrivingAnchor = VehicleAnchorPosition.fromId(settings.freeDrivingAnchorId)
            _uiState.value = _uiState.value.copy(
                routingAnchor = routingAnchor,
                freeDrivingAnchor = freeDrivingAnchor,
                activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
            )
            // Resolve the persisted preset into the visible map area.
            publishResolvedAnchor()
            // If initMap already ran before this load finished (fast user flow),
            // re-apply the persisted style — initMap may have used the default.
            if (mapRenderer != null) {
                ensureMapStyle(
                    settings.styleSheet,
                    resolvedDaylight(),
                    MapStyleApplyReason.RE_OBSERVED
                )
            }
        }

        // Load the bundled map styles for the picker (off main; a directory
        // scan on the device stylesheet dir). basemap-render is the basemap's
        // internal stylesheet, not a user-selectable map style — exclude it
        // (spec: map-styles).
        viewModelScope.launch {
            val styles = withContext(defaultDispatcher) {
                try {
                    client.getAvailableStyleSheets()
                        .filterNot { it == BundledMapStyles.BASEMAP_STYLE_NAME }
                } catch (e: Exception) {
                    Log.w(TAG, "getAvailableStyleSheets failed", e)
                    emptyList()
                }
            }
            _uiState.value = _uiState.value.copy(availableStyleSheets = styles)
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
     * Pure scoping decision, delegated to the shared rule ([resolveRegionScope])
     * so the phone search panel and the car search scope identically (spec:
     * `location-search` — Search scoped by current admin region; spec:
     * `auto-search` — Car and phone region scoping parity). Exposed internal for
     * unit testing.
     */
    internal fun searchAdminRegionHandleForFix(fix: GpsFix?): Long {
        val outcome = resolveRegionScope(
            fix = fix?.let {
                RegionFixReference(lat = it.lat, lon = it.lon, accuracyMeters = it.accuracy)
            },
            previous = SearchRegionScope(
                handle = searchAdminRegionHandle,
                lat = searchAdminRegionLat,
                lon = searchAdminRegionLon,
                name = searchAdminRegionName
            ),
            releaseRegion = { handle ->
                try {
                    client.releaseAdminRegion(handle)
                } catch (e: Exception) {
                    Log.e(TAG, "releaseAdminRegion failed", e)
                }
            },
            resolveRegion = { lat, lon ->
                try {
                    val h = client.resolveAdminRegion(lat, lon)
                    Log.d(TAG, "resolveAdminRegion -> handle=$h")
                    h
                } catch (e: Exception) {
                    Log.e(TAG, "resolveAdminRegion failed", e)
                    0L
                }
            },
            scopeNameOf = { handle ->
                // Show the search scope region (the parent when sibling
                // expansion applies), not just the resolved region. Falls back
                // to the resolved region name when the scope lookup fails
                // (e.g. stale native library).
                try {
                    val scopeName = client.getAdminRegionScopeName(handle)
                    Log.d(TAG, "getAdminRegionScopeName(handle=$handle) -> '$scopeName'")
                    scopeName
                } catch (e: Exception) {
                    Log.e(TAG, "getAdminRegionScopeName failed", e)
                    null
                }
            },
            regionNameOf = { handle ->
                try {
                    client.getAdminRegionName(handle)
                } catch (e: Exception) {
                    Log.e(TAG, "getAdminRegionName failed", e)
                    null
                }
            }
        )

        if (outcome.decision == RegionScopeDecision.FIX_UNUSABLE) {
            Log.d(
                TAG,
                "searchAdminRegionHandleForFix: fix unusable (null=${fix == null}, " +
                    "accuracy=${fix?.accuracy})"
            )
        }
        if (outcome.decision != RegionScopeDecision.REUSED) {
            searchAdminRegionHandle = outcome.scope.handle
            searchAdminRegionLat = outcome.scope.lat
            searchAdminRegionLon = outcome.scope.lon
            searchAdminRegionName = outcome.scope.name
            pushSearchAdminRegionState()
        }
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

    /** Compare two angles in radians, tolerating wrap-around and floating-point noise. */
    private fun isAngleSame(a: Double, b: Double): Boolean {
        return kotlin.math.abs(normalizeAngle(a - b)) < 1e-4
    }

    /**
     * Whether turn-by-turn route guidance is currently active — selects the
     * routing vs free-driving vehicle anchor.
     */
    private fun isNavigatingNow(): Boolean =
        _navigationViewModel?.state?.value?.isNavigating == true

    /**
     * Set the routing vehicle anchor **for the phone** (spec: location-options-ui —
     * vehicle position controls). Persists through the shared settings storage in the
     * PHONE's own field, so a picker change here never alters Android Auto's anchor
     * (that one is configured on the car).
     */
    fun setRoutingAnchor(anchor: VehicleAnchorPosition) {
        routingAnchor = anchor
        _uiState.value = _uiState.value.copy(
            routingAnchor = anchor,
            activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
        )
        publishResolvedAnchor()
        viewModelScope.launch {
            settingsStorage.update { it.copy(routingAnchorId = anchor.id) }
        }
    }

    /**
     * Set the free-driving vehicle anchor **for the phone** (spec:
     * location-options-ui — vehicle position controls). Persists in the phone's own
     * field; Android Auto keeps its own value.
     */
    fun setFreeDrivingAnchor(anchor: VehicleAnchorPosition) {
        freeDrivingAnchor = anchor
        _uiState.value = _uiState.value.copy(
            freeDrivingAnchor = anchor,
            activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
        )
        publishResolvedAnchor()
        viewModelScope.launch {
            settingsStorage.update { it.copy(freeDrivingAnchorId = anchor.id) }
        }
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

    /**
     * Resolve the max speed of the road at [lat]/[lon] for the follow-mode
     * speed widget (spec: map-speed-widget). Throttled: re-resolves only after
     * significant movement or a cooldown, off the main thread. getMaxSpeedAt
     * returns a negative value when the road has no limit → NaN (unknown).
     */
    private fun resolveMaxSpeed(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val moved = lastMaxSpeedLat.isNaN() ||
            distanceMeters(lastMaxSpeedLat, lastMaxSpeedLon, lat, lon) > MAX_SPEED_RESOLVE_MOVE_M
        val cooldownElapsed = now - lastMaxSpeedResolveMs >= MAX_SPEED_RESOLVE_INTERVAL_MS
        if (!moved && !cooldownElapsed) return
        lastMaxSpeedLat = lat
        lastMaxSpeedLon = lon
        lastMaxSpeedResolveMs = now
        viewModelScope.launch(defaultDispatcher) {
            val maxSpeed = try {
                client.getMaxSpeedAt(lat, lon)
            } catch (e: Exception) {
                Log.w(TAG, "getMaxSpeedAt failed", e)
                Double.NaN
            }
            _uiState.value = _uiState.value.copy(
                maxSpeedKmH = maxSpeed.takeIf { it > 0.0 } ?: Double.NaN
            )
        }
    }

    /**
     * Resolve the road at [lat]/[lon] for the free-driving street label
     * (spec: current-road-info — current road shown when no route is active).
     * Bearing-aware native lookup, throttled like [resolveMaxSpeed]: re-resolves
     * only after significant movement or a cooldown, off the main thread.
     */
    private fun resolveCurrentRoad(lat: Double, lon: Double, bearing: Double) {
        val now = System.currentTimeMillis()
        val moved = lastRoadLat.isNaN() ||
            distanceMeters(lastRoadLat, lastRoadLon, lat, lon) > ROAD_RESOLVE_MOVE_M
        val cooldownElapsed = now - lastRoadResolveMs >= ROAD_RESOLVE_INTERVAL_MS
        if (!moved && !cooldownElapsed) return
        lastRoadLat = lat
        lastRoadLon = lon
        lastRoadResolveMs = now
        viewModelScope.launch(defaultDispatcher) {
            val road = try {
                client.getRoadAt(lat, lon, bearing)
            } catch (e: Exception) {
                Log.w(TAG, "getRoadAt failed", e)
                null
            }
            _uiState.value = _uiState.value.copy(currentRoadInfo = road)
        }
    }

    internal suspend fun searchLocations(query: String): List<LocationEntry> = withContext(defaultDispatcher) {
        val handle = currentSearchAdminRegionHandle()
        try {
            // A candidate set larger than the displayed list: the ranker needs
            // the candidates the backend's own order would have truncated away
            // (spec: search-result-ranking — "Candidate set larger than
            // displayed list").
            val startedNs = System.nanoTime()
            val entries = client.searchLocations(query, SearchResultRanker.CANDIDATE_LIMIT, handle)
            val results = entries?.toList() ?: emptyList()
            // Candidate count and elapsed native time: the device check for the
            // larger candidate set (design D3) reads this line.
            Log.d(
                TAG,
                "searchLocations: query='$query', adminRegionHandle=$handle, " +
                    "candidates=${results.size}, limit=${SearchResultRanker.CANDIDATE_LIMIT}, " +
                    "nativeMs=${(System.nanoTime() - startedNs) / 1_000_000}"
            )
            results
        } catch (e: Exception) {
            Log.e(TAG, "searchLocations failed", e)
            emptyList()
        }
    }

    /**
     * Reference point for search distances and ranking: the last known GPS fix
     * (however old — the region scope uses it the same way), else the current map
     * center; null when neither is usable, in which case the list is ordered
     * without distances and shows none (spec: search-result-ranking).
     */
    private fun searchDistanceReference(): SearchReference? {
        val fix = _uiState.value.gpsLocation
        if (fix != null && fix.lat.isFinite() && fix.lon.isFinite()) {
            return SearchReference(fix.lat, fix.lon)
        }
        val viewport = _uiState.value.viewport
        return if (viewport.centerLat.isFinite() && viewport.centerLon.isFinite()) {
            SearchReference(viewport.centerLat, viewport.centerLon)
        } else {
            null
        }
    }

    /**
     * Native location search merged with favorites matching the query
     * (spec: favorite-search): favorite hits first, native results marked
     * when they match a favorite, identical objects deduplicated.
     *
     * Native results are ranked by the match tier rule before merging (spec:
     * search-result-ranking), the perfect-match fact is attached to each row so
     * the marking and the order come from one decision, and the list is cut to
     * the displayed maximum as the best-ranked prefix of the candidate set.
     */
    internal suspend fun mergeSearchResults(query: String): List<MergedSearchResult> {
        val nativeResults = searchLocations(query)
        // Everything after the native string search is CPU work over up to
        // CANDIDATE_LIMIT entries plus a second native query (the structured form
        // search) — kept off the main thread together with the ranking
        // (guidelines/Design.md §4, design D12). The state write below stays on
        // the caller's context.
        val (merged, reference) = withContext(defaultDispatcher) {
            // Full formatted addresses (street + house + PLZ + city) resolve via
            // the structured form search first; its house-level results rank
            // above the raw string results, which drop such queries when a postal
            // code sits inside the query (spec: location-search "Full formatted
            // address resolution").
            val structured = StructuredAddressSearch.resolve(query, client)
            val combined = StructuredAddressSearch.merge(structured, nativeResults)
            val favorites = favoriteRepository.favorites.value.values.flatten()
            val criteria = SearchQueryParser.criteriaOf(query)
            val reference = searchDistanceReference()
            val ranked = SearchResultRanker.rank(combined, criteria, reference)
            val results = favoriteSearchMerger.merge(query, favorites, ranked)
                .map { result ->
                    result.copy(
                        isPerfectMatch = SearchResultRanker.isPerfectMatch(result.entry, criteria)
                    )
                }
                .take(SearchResultRanker.DISPLAY_LIMIT)
            results to reference
        }
        // Publish the reference the ranking used: the rows measure their
        // distance against the same point instead of recomputing it (one value,
        // so the numbers cannot contradict the order).
        _uiState.value = _uiState.value.copy(searchReference = reference)
        return merged
    }

    /**
     * The off-main half of [initMap]: the asset/stylesheet refresh, the database opens, the
     * tile-data-cache configuration, the favorites-store initialization and the viewport lookup.
     *
     * Runs on [defaultDispatcher] (spec: `map-render` — Map initialization keeps native and file
     * work off the main thread; `guidelines/Design.md` §4) and publishes no state, so a cancelled
     * initialization (map re-entry) can only discard its result — never leave a half-applied one,
     * because there is nothing to half-apply.
     *
     * A superseding init cancels this coroutine while a blocking native call may already be in
     * flight (a JNI call cannot be interrupted). The [coroutineContext.ensureActive] checks therefore
     * sit between the client-mutating steps: the cancelled initialization finishes the call it was in
     * and stops before the next one, and the key it restores the viewport for is the one it was
     * started with ([mapKey], captured synchronously by [initMap]).
     *
     * @return the resolved viewport, or `null` when the selected database path was rejected (the
     *   caller publishes the error state on the main thread).
     */
    private suspend fun initializeOffMain(mapPath: String, mapKey: String): MapInitResult? {
        val stylesheetsDir = assetCopier.ensureStylesheets()
        Log.d(TAG, "initMap: stylesheets=$stylesheetsDir")

        Log.d(TAG, "initMap: opening database...")
        val opened = try {
            client.openDatabase(mapPath).also { success ->
                Log.d(TAG, "initMap: openDatabase returned $success")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open database", e)
            false
        }
        if (!opened) return null

        Log.d(TAG, "initMap: database opened successfully")
        coroutineContext.ensureActive()

        // Configure the native tile data cache capacity (regional database
        // and any future basemap) through the shared seam (spec:
        // native-tile-data-cache). Stored in the client and re-applied to
        // every open database by the render path, so it also covers
        // databases that open asynchronously after this call. Perf-only and
        // idempotent; the highest requested value wins and is never lowered, so
        // a car session sharing this client cannot shrink it and a car-first
        // ordering cannot degrade the phone.
        when (NativeTileDataCache.apply(client, NativeTileDataCache.PHONE_TILES)) {
            TileCacheConfig.APPLIED ->
                Log.d(TAG, "initMap: tile data cache configured with " +
                        "${NativeTileDataCache.PHONE_TILES} tiles")
            TileCacheConfig.UNCHANGED -> Unit
            TileCacheConfig.REJECTED ->
                Log.w(TAG, "initMap: tile data cache kept at the higher value already configured")
            TileCacheConfig.FAILED ->
                Log.w(TAG, "initMap: setNativeDataCacheSize failed")
        }
        coroutineContext.ensureActive()

        // Open all other installed maps too, so every downloaded region
        // renders via viewport coverage — no switching needed (multi-map).
        try {
            val mapsDir = File(context.filesDir, "maps")
            // Shared with Android Auto (AutoServiceModule): every database
            // directory under maps/ (any depth), except the basemap overlay.
            val installed = InstalledMaps.findDatabaseDirectories(
                mapsDir.absolutePath,
                File(mapsDir, "basemap").absolutePath
            )
            // Record the database this screen opened, so the next start reopens it (spec:
            // start-map-selection — the last opened map is reopened at the next start; a map opened
            // without the map manager is recorded). The value is normalized to the database directory
            // the open path refers to, so a container path (an archive with its own top-level
            // directory) still matches the discovery's candidates. Runs in this off-main section and
            // through the serialized settings transaction, so a concurrent car write is not lost
            // (spec: settings-persistence).
            settingsStorage.update {
                it.copy(lastMapPath = StartMapSelection.databaseDirectoryFor(installed, mapPath))
            }
            coroutineContext.ensureActive()
            val additional = installed.filter { it != mapPath }
            if (additional.isNotEmpty()) {
                // One batch call: each openDatabase() call closes and reopens
                // every open database on the database thread, so a
                // per-directory loop costs one database-set change per
                // directory for a single logical set (spec:
                // native-database-open — A whole database set is registered in
                // one coordinated change).
                val registered = client.openDatabases(additional.toTypedArray())
                val notRegistered = additional.filterIndexed { index, _ ->
                    !registered.getOrElse(index) { false }
                }
                if (notRegistered.isNotEmpty()) {
                    Log.w(TAG, "initMap: not registered: $notRegistered")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "initMap: failed to open additional maps", e)
        }
        coroutineContext.ensureActive()

        // Initialise favorites repository
        val favPath = context.filesDir.resolve(FAVORITES_FILE).absolutePath
        favoriteRepository.init(favPath)
        coroutineContext.ensureActive()

        // Load persisted viewport for this map; fall back to the map's bounding box center, then to
        // the global default. Resolved HERE, before the caller creates a renderer, so an early
        // setScreenSize/renderMap cannot submit a render with the uninitialized default viewport
        // (which would clobber the restore).
        val saved = viewportStorage.load(mapKey)
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
        return MapInitResult(
            viewport = vp,
            fromSavedViewport = saved != null,
            fromBoundingBox = bbox != null
        )
    }

    /**
     * Result of [initializeOffMain]: the restored viewport plus which fallback produced it, for the
     * one log line that names it.
     */
    private class MapInitResult(
        val viewport: ViewportState,
        val fromSavedViewport: Boolean,
        val fromBoundingBox: Boolean
    )

    /** Initialise with a map database path. Call once from the screen. */
    fun initMap(mapPath: String) {
        Log.d(TAG, "initMap: initialising with path=$mapPath")
        currentMapKey = mapPath.substringAfterLast('/')
        // Captured synchronously: the off-main section (initializeOffMain) restores the viewport for
        // THIS initialization's map even if a re-entry overwrites currentMapKey before it runs.
        val mapKey = currentMapKey ?: mapPath
        // Re-arm the save guard: until the persisted viewport restore is
        // applied below, a lifecycle save must not write the default viewport.
        viewportRestored = false

        // Supersede any in-flight init: its coroutine may be suspended (e.g.
        // favoriteRepository.init runs withContext(defaultDispatcher)), and if
        // it resumed after the re-entry it would apply a stale restore, re-arm
        // the save guard, and overwrite mapRenderer with a second renderer.
        // Cancellation aborts it at its first suspension point.
        initJob?.cancel()

        // Tear down any previous renderer so a re-entry (MAIN screen after
        // map downloads) initialises cleanly instead of stacking renderers.
        rendererScope?.cancel()
        mapRenderer?.shutdown()
        mapRenderer = null

        initJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // The loading state is published first, on the main thread, so it covers the whole
            // off-main section below (spec: map-render — the loading state is observable before
            // the work starts). `density` is a plain resource read and stays here with the
            // renderer that consumes it.
            val density = context.resources.displayMetrics.densityDpi.toDouble()

            // Every native and filesystem call of initialization runs off the main thread
            // (`Design.md` §4 first rule; spec: map-render — Map initialization keeps native and
            // file work off the main thread). The section publishes no state, so a cancelled init
            // (map re-entry) can only discard its result, never leave a half-applied one behind.
            val init = withContext(defaultDispatcher) { initializeOffMain(mapPath, mapKey) }

            if (init == null) {
                Log.e(TAG, "initMap: failed to open database at $mapPath")
                mapReady.value = true
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not open map database"
                )
                return@launch
            }

            // The restored viewport is already resolved. Ordering below is load-bearing: the
            // renderer must not exist while the restore is applied (initializeOffMain resolves it
            // first), and the restored viewport is published before the render frame collector is
            // wired, so an early render cannot overwrite the restored center on disk.
            val vp = init.viewport
            Log.d(
                TAG,
                "initMap: viewport mag=${vp.magnification} " +
                    (if (init.fromSavedViewport) "(saved)" else if (init.fromBoundingBox) "(bbox)" else "(default)")
            )

            // Create MapRenderer on a dedicated background scope so heavy JNI renders
            // never block the main thread and the UI stays responsive. The scope takes the injectable
            // renderer dispatcher (not an inline Dispatchers.Default), so a test can run the renderer's
            // work — including its debounce — on its own scheduler.
            val rendererScope = CoroutineScope(SupervisorJob() + rendererDispatcher)
            this@MapCanvasViewModel.rendererScope = rendererScope
            val renderer = MapRenderer(client, density, rendererScope)
            renderer.renderMode = _uiState.value.renderMode
            // Re-apply the last known screen size: onSizeChanged only fires on size
            // CHANGE, so re-entering this screen (same size) would leave the new
            // renderer at 0x0 and every render would be skipped.
            renderer.screenWidth = screenWidth
            renderer.screenHeight = screenHeight
            mapRenderer = renderer

            // Sync the renderer to the restored viewport BEFORE any init-time
            // submit (dark/style pushes, favorites): those render at the
            // renderer's current viewport, which is DEFAULT (mag 5) until the
            // first state-driven renderMap. On re-entry there is no size-change
            // renderMap, so the first frame would show a zoomed-out map until a
            // manual refresh (observed after basemap reload + switch back).
            renderer.prepareViewport(vp.centerLat, vp.centerLon, vp.magnification, vp.angle)

            // Apply the restored viewport BEFORE wiring the view-change listener:
            // the listener persists every completed render, and an early render
            // with the uninitialized default viewport would overwrite the restored
            // center on disk (observed: viewport-<map>.json with center dropped).
            _uiState.value = _uiState.value.copy(viewport = vp, isLoading = false)
            // The restore is now visible to saveViewport — lifecycle saves may
            // persist from here on.
            viewportRestored = true

            // Wire frame emissions to UI state. Each frame carries bitmap + viewport +
            // marker snapshot as ONE atomic emission, so the overlay never sees state
            // from different frames (which would make the marker jump off the road).
            viewModelScope.launch {
                renderer.frameFlow.collect { frame -> applyRenderedFrame(frame) }
            }

            // Wire view change listener for viewport persistence (per map)
            renderer.addViewChangeListener(object : MapRenderer.ViewChangeListener {
                override fun onViewChanged(lat: Double, lon: Double, mag: Double, angle: Double) {
                    viewModelScope.launch {
                        // A walked step is never persisted (spec: smooth-zoom - Viewport
                        // records final magnification): while a walk is pending, persist
                        // the magnification it is heading for.
                        viewportStorage.save(
                            currentMapKey ?: mapPath,
                            ViewportState(lat, lon, zoomWalk.persistMag(mag))
                        )
                    }
                }
            })

            // Set initial favorites on renderer
            val favs = favoriteRepository.favorites.value
            val allFavs = mutableListOf<com.framstag.libosmscout.client.FavoriteLocation>()
            for ((_, list) in favs) {
                allFavs.addAll(list)
            }
            if (allFavs.isNotEmpty()) {
                renderer.setFavoriteLocations(allFavs.toTypedArray())
            }

            // Apply the resolved dark presentation and the persisted map style, in the order the
            // native side needs: the flag push happens after the database is open (`SetStyleFlag` is
            // a no-op before that), and the stylesheet load carries the flag set, so the first render
            // already uses the right variant and style (spec: map-styles - Persisted style applied on
            // start; `guidelines/MapRendering.md` section 15).
            ensureMapStyle(
                _uiState.value.styleSheet,
                resolvedDaylight(),
                MapStyleApplyReason.STARTUP_DB_READY,
                allowDefaultFallback = true
            )
            // The push above happens after the database is open, so SetStyleFlag
            // is effective — mark it as done so the first front-buffer frame does
            // not re-push and invalidate the freshly rendered tiles.
            stylePushedToNative = true

            // The style value comes from the settings load that ran at ViewModel init; its load is
            // part of the single `ensureMapStyle` call above.

            Log.d(TAG, "initMap: triggering first render")
            if (phoneMapRenderRefused) {
                Log.d(TAG, "initMap: first render refused — phone map suspended by a car session")
            } else {
                renderer.requestRender(vp.centerLat, vp.centerLon, vp.magnification)
            }
            mapReady.value = true
        }
    }

    /** Set screen dimensions on the renderer (called from composable). */
    fun setScreenSize(width: Int, height: Int) {
        val wasZero = screenWidth <= 0 || screenHeight <= 0
        screenWidth = width
        screenHeight = height
        // The visible-area resolution depends on the canvas size (rotation, fold).
        publishResolvedAnchor()
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
        publishSearchRequest(query)
    }

    /**
     * Publish a search request for [query]. The sequence advances on every call, so a
     * request repeating the previous text still runs (see [SearchRequest]).
     */
    private fun publishSearchRequest(query: String) {
        Log.d(TAG, "search request: chars=${query.length} seq=${_searchRequest.value.seq + 1L}")
        _searchRequest.value = SearchRequest(query, _searchRequest.value.seq + 1L)
    }

    /** Called when user picks an entry from the search history: fills the search box and replays the search. */
    fun onHistoryEntrySelected(text: String) {
        onSearchQueryChanged(text)
    }

    /** Called when user selects a favorite from the favorites sheet. */
    fun onFavoriteSelected(fav: com.framstag.libosmscout.client.FavoriteLocation) {
        Log.d(TAG, "onFavoriteSelected: name='${fav.name}' source=favourites")
        viewModelScope.launch {
            // Deactivate follow mode so the picked destination stays visible and a
            // deliberate route calculation always produces the overview (spec:
            // route-map-overview — favorite while free-driving). Mirrors the
            // search / POI / contacts selection paths.
            if (_uiState.value.followMode) {
                _uiState.value = _uiState.value.copy(followMode = false)
            }
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
                withContext(defaultDispatcher) {
                    try {
                        client.getDescription(fav.lat, fav.lon, _uiState.value.viewport.magnification.roundToInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "getDescription failed for favorite", e)
                        null
                    }
                }
            }
            val bboxDeferred = async {
                withContext(defaultDispatcher) {
                    try {
                        client.getObjectBoundingBox(fav.lat, fav.lon, _uiState.value.viewport.magnification.roundToInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "getObjectBoundingBox failed for favorite", e)
                        null
                    }
                }
            }

            val desc = descDeferred.await()
            val bbox = bboxDeferred.await()

            // Determine target zoom: area bounding box → compute, else fixed node zoom.
            // The camera is the favorite coordinate (see updateCenter above), which is
            // generally not the object bbox midpoint, so the fit is verified against
            // that camera and keeps the documented area-favorites floor
            // (spec: fav-auto-zoom — Bounding box zoom calculation; FitVerification.kt).
            val targetMag = if (bbox != null && bbox.size == 4) {
                verifiedAreaFit(
                    bbox, fav.lat, fav.lon, screenWidth, screenHeight, projectionDpi,
                    minZoom = MIN_AREA_ZOOM, angleRad = _uiState.value.viewport.angle
                )
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
        Log.d(TAG, "onSearchResultSelected: label='${entry.label}' source=search")
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
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = entry,
                objectDescription = null,
                isLongPress = false,
                searchQuery = "",
                searchResults = emptyList(),
                showDetailsSheet = true,
                detailsFromPoiSearch = false,
                isLoading = true
            )
            // The displayed query is empty again, so the trigger follows it: no committed
            // search text may be left behind for a later replay to collide with
            // (spec: `location-search` — A repeated query text still runs the search).
            publishSearchRequest("")
            updateCenter(entry.lat, entry.lon)
            renderMap()

            // Fetch full object description at search result location
            val desc = withContext(defaultDispatcher) {
                try {
                    client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
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
                    this.adminRegionHierarchy = entry.adminRegionHierarchy
                    this.name = entry.name
                    this.objectType = entry.objectType
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

    /**
     * Open the unified search dialog (spec: search-dialog — entry points).
     * Resets to Places mode; if the dialog was last in POIs mode, the POI
     * viewport snapshot is restored first. Eagerly resolves the admin region
     * when a usable GPS fix exists so the region name shows immediately.
     */
    fun openSearch() {
        if (_uiState.value.searchMode == SearchMode.POIS) {
            restorePoiViewport()
        }
        _uiState.value = _uiState.value.copy(
            searchOpen = true,
            searchMode = SearchMode.PLACES
        )
        onSearchPanelOpened()
    }

    /**
     * Close the unified search dialog. If the dialog is in POIs mode, the
     * in-flight search is cancelled and the map center/zoom restored to the
     * values at POI-mode entry (spec: viewport restored when POI search closes).
     */
    fun closeSearch() {
        if (_uiState.value.searchMode == SearchMode.POIS) {
            restorePoiViewport()
        }
        searchPanelOpen = false
        releaseSearchAdminRegion()
        _uiState.value = _uiState.value.copy(searchOpen = false)
    }

    /**
     * Switch the search dialog mode. Entering POIs mode snapshots the viewport
     * and resets POI state (no category preselected, no preloaded results);
     * leaving it restores the snapshot and clears the POI marker. Per-mode
     * state (query, results) is preserved for the session (spec: search-dialog
     * — mode switch preserves state).
     */
    fun setSearchMode(mode: SearchMode) {
        val s = _uiState.value
        if (mode == s.searchMode) return
        when {
            mode == SearchMode.POIS -> {
                poiSearchJob?.cancel()
                poiSearchJob = null
                poiViewportSnapshot = s.viewport
                _uiState.value = s.copy(
                    searchMode = mode,
                    poiCategory = null,
                    poiResults = emptyList(),
                    isPoiSearching = false,
                    poiSearchError = null
                )
            }
            s.searchMode == SearchMode.POIS -> {
                restorePoiViewport()
                _uiState.value = _uiState.value.copy(searchMode = mode)
            }
            else -> {
                _uiState.value = s.copy(searchMode = mode)
            }
        }
    }

    /**
     * Restore the POI-mode viewport snapshot (taken at POI-mode entry) and
     * clear the POI marker. Cancels any in-flight search.
     */
    private fun restorePoiViewport() {
        poiSearchJob?.cancel()
        poiSearchJob = null
        val snapshot = poiViewportSnapshot
        poiViewportSnapshot = null
        mapRenderer?.clearSearchSelected()
        if (snapshot != null) {
            _uiState.value = _uiState.value.copy(
                isPoiSearching = false,
                poiSelectedLat = Double.NaN,
                poiSelectedLon = Double.NaN,
                viewport = snapshot
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isPoiSearching = false,
                poiSelectedLat = Double.NaN,
                poiSelectedLon = Double.NaN
            )
        }
        renderMap()
    }

    /** Select a POI category. Never triggers a search (spec: no preloaded results). */
    fun onPoiCategorySelected(category: String?) {
        _uiState.value = _uiState.value.copy(poiCategory = category)
    }

    /** Change the POI search radius. Never triggers a search. */
    fun onPoiRadiusChanged(radiusMeters: Double) {
        _uiState.value = _uiState.value.copy(poiRadiusMeters = radiusMeters)
    }

    /**
     * Run the POI search for the selected category around the current map
     * center. Blocking JNI call runs off the main thread; a previous in-flight
     * search is cancelled.
     */
    fun performPoiSearch() {
        val s = _uiState.value
        val category = s.poiCategory ?: return
        val radiusMeters = s.poiRadiusMeters
        val lat = s.viewport.centerLat
        val lon = s.viewport.centerLon
        poiSearchJob?.cancel()
        _uiState.value = s.copy(
            isPoiSearching = true,
            poiSearchError = null,
            poiSearchCenterLat = lat,
            poiSearchCenterLon = lon,
            poiSelectedLat = Double.NaN,
            poiSelectedLon = Double.NaN
        )
        poiSearchJob = viewModelScope.launch {
            val results = withContext(defaultDispatcher) {
                try {
                    client.searchPOIs(category, lat, lon, radiusMeters, MAX_POI_RESULTS)?.toList() ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "POI search failed", e)
                    null
                }
            }
            if (results != null) {
                _uiState.value = _uiState.value.copy(isPoiSearching = false, poiResults = results)
            } else {
                _uiState.value = _uiState.value.copy(
                    isPoiSearching = false,
                    poiSearchError = "POI search failed"
                )
            }
        }
    }

    /**
     * Select a POI result: close the POI sheet, open the details sheet with
     * the object description fetched off-main, center the map on the POI, and
     * zoom to fit both the current location and the POI (spec: details via
     * single click). The POI is marked with the search-selection marker.
     */
    fun onPoiEntryClick(entry: PoiEntry) {
        Log.d(TAG, "onPoiEntryClick: label='${entry.label}' source=poi")
        viewModelScope.launch {
            // Disengage follow mode so a GPS fix does not yank the map away
            // while the user is looking at the details sheet.
            if (_uiState.value.followMode) {
                _uiState.value = _uiState.value.copy(followMode = false)
            }
            val locEntry = LocationEntry().apply {
                this.label = entry.label.ifEmpty { "(unnamed)" }
                this.lat = entry.lat
                this.lon = entry.lon
                this.matchQuality = "poi"
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = locEntry,
                objectDescription = null,
                isLongPress = false,
                searchOpen = false,
                searchMode = SearchMode.POIS,
                poiSelectedLat = entry.lat,
                poiSelectedLon = entry.lon,
                detailsFromPoiSearch = true,
                showDetailsSheet = true,
                isLoading = true
            )

            // Mark the POI, center the map on it, and fit current location + POI
            mapRenderer?.setSearchSelected(entry.lat, entry.lon)
            updateCenter(entry.lat, entry.lon)
            val fitMag = poiFitMagnification(entry)
            if (fitMag != _uiState.value.viewport.magnification) {
                // A camera fit lands directly - no walked zoom animation.
                updateMagnification(fitMag, walk = false)
            }
            renderMap()

            val desc = withContext(defaultDispatcher) {
                try {
                    client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
                } catch (e: Exception) {
                    Log.e(TAG, "getDescription failed for POI", e)
                    null
                }
            }
            if (desc != null && desc.entries.isNotEmpty()) {
                val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else entry.lat
                val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else entry.lon
                val objEntry = LocationEntry().apply {
                    this.label = locEntry.label
                    this.lat = objLat
                    this.lon = objLon
                    this.matchQuality = "object"
                    this.name = locEntry.label
                    this.adminRegionHierarchy = resolveRegionName(objLat, objLon)
                }
                _uiState.value = _uiState.value.copy(
                    selectedLocation = objEntry,
                    objectDescription = desc,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Magnification that fits the current location and the POI with ~30%
     * margin, or the current magnification when no GPS fix exists.
     *
     * The camera stays on the POI ([onPoiEntryClick] centers there), which is one
     * end of the fitted bbox rather than its midpoint, so the fit is verified
     * around that camera. Its floor is the render-stability minimum
     * ([MIN_MAG]), not the area-favorites floor: a POI whose distance does not fit
     * at 14 must still be shown together with the current location
     * (spec: poi-search — Distant POI zooms out below the area-favorites floor).
     */
    private fun poiFitMagnification(entry: PoiEntry): Double {
        val currentMag = _uiState.value.viewport.magnification
        val loc = locationService.location.value ?: return currentMag
        val lat1 = loc.lat
        val lon1 = loc.lon
        val dLat = kotlin.math.abs(entry.lat - lat1)
        val dLon = kotlin.math.abs(entry.lon - lon1)
        if (dLat < 1e-9 && dLon < 1e-9) return currentMag
        val marginLat = dLat * 0.3
        val marginLon = dLon * 0.3
        val bbox = doubleArrayOf(
            kotlin.math.min(lat1, entry.lat) - marginLat,
            kotlin.math.max(lat1, entry.lat) + marginLat,
            kotlin.math.min(lon1, entry.lon) - marginLon,
            kotlin.math.max(lon1, entry.lon) + marginLon
        )
        return verifiedAreaFit(
            bbox, entry.lat, entry.lon, screenWidth, screenHeight, projectionDpi,
            minZoom = MIN_MAG, angleRad = _uiState.value.viewport.angle
        )
    }

    /**
     * Resolve the admin region name for a location (one-shot; handle released).
     * Used to fill the details dialog's area/address for results that carry no
     * admin region hierarchy (POI search, long-press).
     */
    private fun resolveRegionName(lat: Double, lon: Double): String? {
        return try {
            val handle = client.resolveAdminRegion(lat, lon)
            if (handle == 0L) return null
            val name = client.getAdminRegionName(handle)
            client.releaseAdminRegion(handle)
            name
        } catch (e: Exception) {
            Log.e(TAG, "resolveRegionName failed", e)
            null
        }
    }

    /** Called when user long-presses on the map. */
    /**
     * Long press on the map.
     *
     * @param screenX press point in canvas pixels, or null when the caller has no screen
     *   point (tests). Diagnostics identity only: the file-backed entry records the press
     *   point and the magnification instead of a coordinate (spec: auto-diagnostics —
     *   Diagnostics carry no coordinates), which is what the screen→geo mapping check
     *   compares.
     */
    fun onLongPress(lat: Double, lon: Double, screenX: Int? = null, screenY: Int? = null) {
        val mapName = currentMapKey ?: "?"
        Log.d(TAG, "onLongPress x=${screenX ?: -1} y=${screenY ?: -1} map=$mapName")
        // File-backed diagnostics: verify the resolved point against the
        // clicked object (screen→geo mapping check).
        com.naviveylin.core.DiagnosticsLog.log(
            "LONGPRESS",
            "x=${screenX ?: -1} y=${screenY ?: -1} mag=${_uiState.value.viewport.magnification} map=$mapName"
        )
        showCandidatesFor(
            lat = lat,
            lon = lon,
            zoom = _uiState.value.viewport.magnification.roundToInt(),
            // Locale-stable pair (spec: i18n-l10n — Coordinate string is
            // locale-stable): the pair layout comes from the resource, the
            // locale does not follow the device.
            label = formatCoordinatePair(lat, lon, context.getString(R.string.coordinates_format))
        )
    }

    /**
     * Shared candidate flow for a coordinate: query the map database for
     * objects near the point and show the picker, or fall back to the raw
     * coordinate entry. Used by long-press and shared-location processing.
     *
     * @param openDetailsOnNoCandidates when no objects are found, open the
     *   details sheet on the raw coordinate instead of showing only the marker
     *   (share flow; long-press keeps the marker-only behavior).
     */
    private fun showCandidatesFor(
        lat: Double,
        lon: Double,
        zoom: Int,
        label: String,
        openDetailsOnNoCandidates: Boolean = false
    ) {
        viewModelScope.launch {
            val entry = LocationEntry().apply {
                this.label = label
                this.lat = lat
                this.lon = lon
                this.matchQuality = "coordinate"
            }
            _uiState.value = _uiState.value.copy(
                selectedLocation = entry,
                isLongPress = true,
                isLoading = true
            )
            val candidates = withContext(defaultDispatcher) {
                try {
                    client.getDescriptionCandidates(lat, lon, zoom)
                } catch (e: Exception) {
                    Log.e(TAG, "getDescriptionCandidates failed", e)
                    emptyList<ObjectDescription>()
                }
            }
            if (candidates.isNotEmpty()) {
                // Multiple (or any) candidates: show the picker, no details yet.
                _uiState.value = _uiState.value.copy(
                    candidateDescriptions = candidates,
                    showCandidatePicker = true,
                    isLoading = false,
                    showDetailsSheet = false,
                    detailsFromPoiSearch = false
                )
            } else {
                // No objects at the point: keep the coordinate entry, no picker.
                _uiState.value = _uiState.value.copy(
                    candidateDescriptions = emptyList(),
                    showCandidatePicker = false,
                    objectDescription = null,
                    isLoading = false,
                    showDetailsSheet = openDetailsOnNoCandidates,
                    detailsFromPoiSearch = false
                )
            }
            renderMap()
        }
    }

    /**
     * Process a shared location: wait for the map to be ready, then dispatch.
     * Coordinates go through the candidate flow (fixed high zoom, details on
     * the raw coordinate when nothing is found); address text opens the search
     * panel with the query filled.
     */
    private suspend fun processSharedLocation(request: SharedLocationRequest) {
        // Candidate lookup and search need the map database open.
        mapReady.first { it }
        sharedLocationHandler.consume()

        if (request.hasCoordinates) {
            val lat = request.lat!!
            val lon = request.lon!!
            Log.d(
                TAG,
                "Shared location: shape=coordinates " +
                    "labelHint=${if (request.label != null) "subject" else "none"} " +
                    "mag=${_uiState.value.viewport.magnification} map=${currentMapKey ?: "?"}"
            )
            updateCenter(lat, lon)
            showCandidatesFor(
                lat = lat,
                lon = lon,
                zoom = SHARE_CANDIDATE_ZOOM,
                label = request.label ?: String.format(Locale.US, "%.5f, %.5f", lat, lon),
                openDetailsOnNoCandidates = true
            )
        } else {
            val query = request.query
            if (!query.isNullOrBlank()) {
                // Shape, not text: a shared text can itself be a coordinate pair.
                Log.d(TAG, "Shared location: shape=query chars=${query.length}")
                onSearchQueryChanged(query)
                openSearch()
            }
        }
    }

    /**
     * Called when the user selects one candidate from the picker: open the
     * details sheet for that object and place the marker on the object center
     * (falling back to the press point when the object has no coordinates).
     */
    fun onCandidateSelected(desc: ObjectDescription) {
        val s = _uiState.value
        val pressLat = s.selectedLocation?.lat ?: Double.NaN
        val pressLon = s.selectedLocation?.lon ?: Double.NaN
        val objLat = if (!desc.objectLat.isNaN()) desc.objectLat else pressLat
        val objLon = if (!desc.objectLon.isNaN()) desc.objectLon else pressLon
        val objEntry = LocationEntry().apply {
            this.label = s.selectedLocation?.label ?: formatCoordinatePair(objLat, objLon)
            this.lat = objLat
            this.lon = objLon
            this.matchQuality = "object"
            this.adminRegionHierarchy = resolveRegionName(objLat, objLon)
        }
        _uiState.value = s.copy(
            selectedLocation = objEntry,
            objectDescription = desc,
            showDetailsSheet = true,
            isLongPress = true,
            showCandidatePicker = false,
            candidateDescriptions = emptyList(),
            detailsFromPoiSearch = false
        )
        updateCenter(objLat, objLon)
        renderMap()
    }

    /** Dismiss the candidate picker without opening details. */
    fun dismissCandidatePicker() {
        _uiState.value = _uiState.value.copy(
            candidateDescriptions = emptyList(),
            showCandidatePicker = false,
            showDetailsSheet = false,
            objectDescription = null,
            isLongPress = false,
            detailsFromPoiSearch = false
        )
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
        // Pre-navigation mode snapshot (spec: map-modes — navigation end
        // restores prior mode): startNavigation forces follow on, so remember
        // the follow/suspension state before navigation starts and restore it
        // when navigation stops — a browse-before-nav user lands back in BROWSE.
        viewModelScope.launch {
            vm.state.collect { navState ->
                if (navState.isNavigating && preNavigationFollow == null) {
                    // Fallback for a host that wired no snapshot callback: the adapter normally
                    // records the surface's mode when the session starts
                    // ([snapshotPreNavigationMode]), so this only covers the wiring gap.
                    preNavigationFollow = _uiState.value.followMode
                    preNavigationSuspended = _uiState.value.driveSuspended
                } else if (!navState.isNavigating && preNavigationFollow != null) {
                    val restoreFollow = preNavigationFollow!!
                    val restoreSuspended = preNavigationSuspended!!
                    preNavigationFollow = null
                    preNavigationSuspended = null
                    // Snapshot the exact position and zoom where routing ended
                    // BEFORE the restore below mutates state (spec: map-modes —
                    // "Navigation end keeps the viewport"). The representation
                    // preset touches only orientation flags; the snapshot is
                    // re-asserted afterwards so no intermediate update (render
                    // listener, lifecycle save, re-init) can substitute the
                    // persisted/app-start viewport.
                    val endCenterLat = _uiState.value.viewport.centerLat
                    val endCenterLon = _uiState.value.viewport.centerLon
                    val endMagnification = _uiState.value.viewport.magnification
                    _uiState.value = _uiState.value.copy(
                        followMode = restoreFollow,
                        driveSuspended = restoreSuspended
                    )
                    // Landing in BROWSE (follow off and not a suspended drive)
                    // must apply the BROWSE representation: north-up, no drift.
                    // Position and zoom stay where routing ended (spec:
                    // map-modes — navigation end restores prior mode). A
                    // suspended FREE_DRIVE (driveSuspended) keeps its flags.
                    if (!_uiState.value.followMode && !_uiState.value.driveSuspended) {
                        applyBrowseRepresentation()
                    }
                    // Re-assert the routing-end center/zoom (the preset must
                    // never move the viewport) and persist it immediately: any
                    // later map restore (app restart, screen recreation via the
                    // NavGraph key/back stack) then resumes where routing ended
                    // instead of jumping back to the app-start position (fixes
                    // "ending routing resets location and zoom to the start-of-
                    // app state").
                    _uiState.value = _uiState.value.copy(
                        viewport = _uiState.value.viewport.copy(
                            centerLat = endCenterLat,
                            centerLon = endCenterLon,
                            magnification = endMagnification
                        )
                    )
                    viewportStorage.save(
                        currentMapKey ?: "default",
                        ViewportState(endCenterLat, endCenterLon, endMagnification)
                    )
                    Log.d(TAG, "setNavigationViewModel: navigation ended, " +
                            "mode restored follow=${_uiState.value.followMode} suspended=${_uiState.value.driveSuspended}, " +
                            "mag=$endMagnification")
                    renderMap()
                }
            }
        }
    }

    /** Set the RoutePanelViewModel (injected via Hilt from the screen). */
    fun setRoutePanelViewModel(vm: RoutePanelViewModel) {
        _routePanelViewModel = vm

        // Collect route results for map rendering. The route is drawn only
        // while routeVisible is true: stopping navigation hides the route
        // without resetting the panel state (spec: stop-navigation-hides-route),
        // and restarting navigation (showRouteOnMap) re-emits via combine.
        viewModelScope.launch {
            combine(vm.routeResultFlow, vm.routeVisible) { result, visible -> result to visible }
                .collect { (result, visible) ->
                    if (result != null && visible) {
                        mapRenderer?.setRoute(
                            result.routeLats, result.routeLons,
                            result.startLat, result.startLon,
                            result.destLat, result.destLon
                        )
                        // Store route geometry for curve detection, and the fit's
                        // endpoints so a card-state change can re-fit the same route.
                        routeLats = result.routeLats
                        routeLons = result.routeLons
                        routeFitEndpoints = doubleArrayOf(
                            result.startLat, result.startLon, result.destLat, result.destLon
                        )
                        renderMap()
                        // Route-overview fit (spec: route-map-overview): fit once
                        // per result and never while navigating or following — the
                        // driver's viewport must not be yanked to an overview
                        // (reroute mid-drive, restart, free-drive).
                        if (result != lastFittedResult && canFitRouteOverview()) {
                            // Mark at arrival: re-emissions of the same result must
                            // never reschedule a fit (stale-result guard).
                            lastFittedResult = result
                            applyRouteOverviewFit(
                                result.routeLats, result.routeLons,
                                result.startLat, result.startLon,
                                result.destLat, result.destLon
                            )
                        }
                    }
                }
        }

        // Session camera lease (spec: `route-planning-session` — Session holds the
        // camera while active): opening a session suspends the drive preset, and the
        // lease also refuses a follow re-engage while it runs.
        viewModelScope.launch {
            vm.sessionState.collect { sessionState ->
                if (sessionState != RouteSessionState.INACTIVE && _uiState.value.followMode) {
                    _uiState.value = _uiState.value.copy(followMode = false)
                }
            }
        }

        // Analysed-step camera move (spec: `route-analysis` — "Selecting a step SHALL
        // move the map camera to that manoeuvre"). Only a newly analysed step moves
        // the camera; clearing the selection leaves the viewport where the user left
        // it.
        viewModelScope.launch {
            vm.analysedAnchor.collect { anchor ->
                if (anchor != null) {
                    focusCameraOnSegment(
                        anchor.lat, anchor.lon,
                        // One source for the pair: the panel publishes the segment before the anchor
                        // (its contract), and reading it from the state that carries the analysed
                        // step index keeps the two consistent even if a consumer is resumed
                        // between the two emissions.
                        vm.uiState.value.analysedSegment ?: vm.analysedSegmentRange.value
                    )
                }
            }
        }

        // Collect clear route signals. drop(1): the StateFlow's initial no-signal
        // value must not race the combine collector on re-subscription (screen
        // re-entry) — resetting lastFittedResult there would re-fit a stale
        // emission and move a user-moved viewport (spec: route-map-overview).
        viewModelScope.launch {
            vm.clearRouteSignal.drop(1).collect {
                mapRenderer?.clearRoute()
                routeLats = null
                routeLons = null
                // Allow an identical re-calculation to refit the overview, and
                // drop a pending fit for the cleared route.
                lastFittedResult = null
                routeFitEndpoints = null
                lastFittedCoveredPx = -1
                routeFitJob?.cancel()
                routeFitJob = null
                renderMap()
            }
        }

        // The route overview fit is applied by the session, once per result, with no
        // cross-surface measurement and no settle delay (spec: route-map-overview — the
        // session owns the camera).
    }

    /**
     * Whether a route overview may be fitted right now (spec: route-map-overview,
     * R2): never while navigating (reroute/restart) and never while follow mode
     * is engaged (free driving) — the driver's viewport must not be yanked.
     */
    private fun canFitRouteOverview(): Boolean =
        _navigationViewModel?.state?.value?.isNavigating != true && !_uiState.value.followMode

    /**
     * Apply the one-shot route overview fit for a freshly arrived result (spec:
     * `route-map-overview` — the session owns the fit). No settle delay: there is no
     * cross-surface measurement left to wait for, so the fit runs in the session's own
     * turn. The driving guard is still re-checked — navigation start or a follow
     * re-engage that happened in between wins over the overview (spec R2).
     */
    private fun applyRouteOverviewFit(
        routeLats: DoubleArray?,
        routeLons: DoubleArray?,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ) {
        routeFitJob?.cancel()
        routeFitJob = viewModelScope.launch {
            if (!canFitRouteOverview()) {
                Log.d(TAG, "route overview fit skipped: navigating or following")
                return@launch
            }
            fitViewportToRoute(routeLats, routeLons, startLat, startLon, destLat, destLon)
            lastFittedCoveredPx = overlayCoveredPx
        }
    }

    /** Open route panel with destination prefilled from details sheet, start = current location. */
    fun openRoutePanelWithStart(entry: LocationEntry?) {
        val vm = _routePanelViewModel ?: return
        // A user-chosen destination means an overview calculation: leave follow
        // mode (spec: route-map-overview) so the driving guard cannot suppress
        // the fit — the search / POI / contacts paths do the same.
        if (_uiState.value.followMode) {
            _uiState.value = _uiState.value.copy(followMode = false)
        }
        if (entry != null) {
            vm.setDestLocation(entry)
            val loc = locationService.location.value
            if (loc != null) {
                val currentLoc = LocationEntry().apply {
                    label = context.getString(R.string.current_location)
                    lat = loc.lat
                    lon = loc.lon
                    matchQuality = "coordinate"
                }
                vm.setStartLocation(currentLoc)
            }
        }
        _uiState.value = _uiState.value.copy(showRoutePanel = true)
        // The session state machine is the owner of the session's lifetime (spec:
        // route-planning-session); opening the overlay opens (or resumes) it.
        vm.openSession()
    }

    /**
     * Dismiss the route overlay: this is the session's cancel exit (spec:
     * `route-planning-session`), so the session ends and — unless navigation owns the
     * route — the route leaves the map.
     */
    fun dismissRoutePanel() {
        _uiState.value = _uiState.value.copy(showRoutePanel = false)
        _routePanelViewModel?.endSession()
    }

    /** Set route start location (from favorite picking). */
    fun setRouteStart(entry: LocationEntry) {
        _routePanelViewModel?.setStartLocation(entry)
    }

    /** Set route destination location (from favorite picking). */
    fun setRouteDest(entry: LocationEntry) {
        _routePanelViewModel?.setDestLocation(entry)
    }

    /**
     * Dismiss the details sheet. A plain dismiss (swipe/back) of a details
     * sheet opened from POI search reopens the search dialog in POIs mode with
     * its results intact; from the address book it reopens in Contacts mode. If
     * a route was started from the details sheet, the dialog stays closed
     * (spec: selective action closes both dialogs).
     */
    fun dismissDetailsSheet() {
        val s = _uiState.value
        val fromPoi = s.detailsFromPoiSearch
        val fromAddressBook = s.detailsFromAddressBook
        val reopen = !s.showRoutePanel
        _uiState.value = s.copy(
            showDetailsSheet = false,
            objectDescription = null,
            isLongPress = false,
            detailsFromPoiSearch = false,
            detailsFromAddressBook = false,
            searchOpen = if (reopen && (fromPoi || fromAddressBook)) true else s.searchOpen,
            searchMode = when {
                reopen && fromPoi -> SearchMode.POIS
                reopen && fromAddressBook -> SearchMode.CONTACTS
                else -> s.searchMode
            }
        )
    }

    /**
     * "Show on map" action from a details sheet opened via POI search: center
     * the map on the POI, close the details sheet, and keep the POI sheet
     * closed (spec: show action closes both dialogs).
     */
    fun showOnMap() {
        val loc = _uiState.value.selectedLocation ?: return
        updateCenter(loc.lat, loc.lon)
        renderMap()
        _uiState.value = _uiState.value.copy(
            showDetailsSheet = false,
            objectDescription = null,
            isLongPress = false,
            detailsFromPoiSearch = false,
            detailsFromAddressBook = false
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

    /**
     * The surface's own follow/suspension state before navigation forced follow on. Captured by
     * [snapshotPreNavigationMode], which the navigation adapter calls when a session *starts* — the
     * adapter is the one place that knows the session's start and where follow is forced on, so the
     * restore at the stop cannot observe the forced-on value. Taking the snapshot inside the forcing
     * instead left the ordering to two collectors of the same navigation state and recorded nothing
     * at all when a session held the camera and refused the forcing; either way the restore read a
     * value navigation had already imposed (measured 2026-10-06: `mode restored follow=true` after a
     * browse-before navigation; spec: `map-modes` — navigation end restores prior mode).
     */
    private var preNavigationFollow: Boolean? = null
    private var preNavigationSuspended: Boolean? = null

    /**
     * Record the mode this surface is in for the navigation that is about to start
     * (spec: `map-modes` — navigation end restores prior mode). Called by the navigation adapter
     * *before* it forces follow on; idempotent, so a second session start inside a running one
     * cannot overwrite the recorded mode with a value navigation imposed.
     */
    internal fun snapshotPreNavigationMode() {
        if (preNavigationFollow != null) return
        preNavigationFollow = _uiState.value.followMode
        preNavigationSuspended = _uiState.value.driveSuspended
    }

    fun onToggleFollowMode(enabled: Boolean) {
        // The session owns the camera while it is open (spec: route-planning-session —
        // Session holds the camera while active): a follow re-engage is refused there,
        // and the re-center affordance re-engages after the session has ended.
        if (enabled && sessionHoldsCamera()) return
        _uiState.value = _uiState.value.copy(followMode = enabled)
        if (enabled) {
            // Reset auto-zoom state for fresh navigation start
            setAutoZoomSuspended(false)
            lastSpeedBandIndex = -1
            currentTargetMag = 15.0
            lastAutoZoomCommitMs = 0L
            turnPassedDistance = Double.NaN
            lastValidSpeedKmH = 20.0
            // Set initial magnification to routing-sensible default
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(magnification = DRIVE_PRESET_MAG)
            )

            // Immediately center on current GPS position
            val loc = locationService.location.value
            if (loc != null) {
                // Apply orientation: if north-up, reset angle; if follow-direction, use bearing
                val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
                val isNorthUp = when (mode) {
                    MapMode.NAVIGATION, MapMode.FREE_DRIVE -> _uiState.value.navNorthUp
                    MapMode.BROWSE -> _uiState.value.freeFormNorthUp
                }
                val angle = if (!isNorthUp && !loc.markerBearing.isNaN()) {
                    computeMapAngle(isNorthUp, loc.markerBearing)
                } else 0.0

                // Anchor-centered from the first frame (spec: smooth-follow —
                // Anchor restored after manual pan or re-center).
                val (targetLat, targetLon) = followRenderTarget(
                    loc.lat, loc.lon, _uiState.value.viewport.magnification, angle
                )
                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        centerLat = targetLat,
                        centerLon = targetLon,
                        angle = angle
                    )
                )
                renderMap()
            }
        }
    }

    /**
     * Disengage follow mode (called on manual pan/zoom/rotate). In FREE_DRIVE
     * this suspends the drive preset (spec: map-modes — drive suspension). In
     * BROWSE it does nothing to the re-center button: that visibility is derived
     * from the viewport and the fix, not from a remembered interaction (spec:
     * map-modes — Browse re-center).
     */
    fun disengageFollowMode() {
        val s = _uiState.value
        if (s.followMode) {
            // Leave follow mode on the framing the user actually sees: the viewport
            // center becomes the displayed frame's center (the anchor center), so the
            // first pan/zoom continues from the shown frame instead of jumping by the
            // anchor offset (spec: smooth-follow — Anchor restored after manual pan or
            // re-center).
            val shown = s.renderViewport
            _uiState.value = s.copy(
                followMode = false,
                driveSuspended = true,
                viewport = if (shown != null) {
                    s.viewport.copy(centerLat = shown.lat, centerLon = shown.lon)
                } else {
                    s.viewport
                }
            )
        }
    }

    /**
     * Enter FREE_DRIVE mode (spec: map-modes — mode toggle): applies the drive
     * preset — follow on, auto-zoom on, heading-up, centered on the current GPS
     * position at the driving zoom. Runtime state only; per-mode sub-options
     * (auto-zoom, orientation) stay user-adjustable in the config sheet.
     */
    fun enterFreeDrive() {
        if (mode == MapMode.NAVIGATION) return
        val loc = locationService.location.value
        setAutoZoomSuspended(false)
        val angle = if (loc != null && !loc.markerBearing.isNaN()) {
            computeMapAngle(false, loc.markerBearing)
        } else 0.0
        // Anchor-centered framing for the drive preset (spec: smooth-follow —
        // Anchor-centered follow framing).
        val (targetLat, targetLon) = if (loc != null) {
            followRenderTarget(loc.lat, loc.lon, DRIVE_PRESET_MAG, angle)
        } else {
            _uiState.value.viewport.centerLat to _uiState.value.viewport.centerLon
        }
        _uiState.value = _uiState.value.copy(
            followMode = true,
            autoZoomEnabled = true,
            navNorthUp = false,
            driveSuspended = false,
            browseReCenterVisible = false,
            viewport = _uiState.value.viewport.copy(
                magnification = DRIVE_PRESET_MAG,
                centerLat = targetLat,
                centerLon = targetLon,
                angle = angle
            )
        )
        // Reset auto-zoom state for a fresh drive start
        lastSpeedBandIndex = -1
        currentTargetMag = 15.0
        lastAutoZoomCommitMs = 0L
        turnPassedDistance = Double.NaN
        lastValidSpeedKmH = 20.0
        renderMap()
    }

    /**
     * Apply the BROWSE representation preset (spec: map-modes — mode presets):
     * follow off, north-up orientation, drive suspension and browse-drift flags
     * cleared. The viewport center and zoom are intentionally kept where they
     * are — representation settings only, not a viewport restore.
     */
    private fun applyBrowseRepresentation() {
        _uiState.value = _uiState.value.copy(
            followMode = false,
            freeFormNorthUp = true,
            driveSuspended = false,
            browseReCenterVisible = false,
            viewport = _uiState.value.viewport.copy(angle = 0.0)
        )
    }

    /**
     * Exit FREE_DRIVE to BROWSE (spec: map-modes — mode toggle): follow off,
     * north-up, staying at the current position and zoom.
     */
    fun exitFreeDrive() {
        if (mode != MapMode.FREE_DRIVE) return
        applyBrowseRepresentation()
        renderMap()
    }

    /**
     * Reset a suspended FREE_DRIVE to the standard drive values (spec:
     * map-modes — drive suspension and reset): follow on, auto-zoom on,
     * heading-up, speed-based driving zoom.
     */
    fun resetDrivePreset() {
        if (mode != MapMode.FREE_DRIVE) return
        val loc = locationService.location.value
        setAutoZoomSuspended(false)
        val angle = if (loc != null && !loc.markerBearing.isNaN()) {
            computeMapAngle(false, loc.markerBearing)
        } else 0.0
        // Re-engage on the anchor-centered frame (spec: smooth-follow — Anchor
        // restored after manual pan or re-center).
        val (targetLat, targetLon) = if (loc != null) {
            followRenderTarget(loc.lat, loc.lon, _uiState.value.viewport.magnification, angle)
        } else {
            _uiState.value.viewport.centerLat to _uiState.value.viewport.centerLon
        }
        _uiState.value = _uiState.value.copy(
            followMode = true,
            autoZoomEnabled = true,
            navNorthUp = false,
            driveSuspended = false,
            viewport = _uiState.value.viewport.copy(
                centerLat = targetLat,
                centerLon = targetLon,
                angle = angle
            )
        )
        lastSpeedBandIndex = -1
        currentTargetMag = 15.0
        lastAutoZoomCommitMs = 0L
        turnPassedDistance = Double.NaN
        lastValidSpeedKmH = 20.0
        renderMap()
    }

    /**
     * Derive the quality of [loc] (spec: `gps-fix-quality` — Fix availability and quality tiers):
     * NONE without a fix, for a fix past [fixAgeLimitMs] and while the platform reports the location
     * source disabled; POOR/GOOD by the reported accuracy otherwise. One definition for both callers —
     * the fix-driven pipeline and the re-evaluation tick that decides whether a tick is needed.
     *
     * The source read is a system-server binder call, so it runs on [defaultDispatcher]: the tick
     * caller is already off the main thread (no hop), the pipeline caller is the main-confined
     * collector.
     */
    private suspend fun deriveFixQuality(loc: GpsFix?): GpsFixQuality = when {
        loc == null -> GpsFixQuality.NONE
        FixFreshness.isAgedOut(loc.time, timeSource.nowMillis(), fixAgeLimitMs) -> GpsFixQuality.NONE
        !withContext(defaultDispatcher) { locationService.isLocationSourceEnabled() } ->
            GpsFixQuality.NONE
        loc.accuracy > GPS_FIX_MAX_ACCURACY_M -> GpsFixQuality.POOR
        else -> GpsFixQuality.GOOD
    }

    /**
     * Timestamp the browse offset first exceeded the appear threshold, or 0 while
     * it is not beyond it (spec: map-modes — Browse re-center).
     */
    private var browseReCenterBeyondSinceMs = 0L

    /**
     * Re-derive the BROWSE re-center visibility from the current viewport, canvas
     * and fix (spec: map-modes — Browse re-center). The offset is measured on
     * every UI-state change, so panning, zooming and vehicle movement all count
     * and nothing has to be remembered. Appear edge: beyond
     * [RECENTER_SHOW_OFFSET_PX] continuously for [RECENTER_DWELL_MS]; hide edge:
     * below [RECENTER_HIDE_OFFSET_PX], immediately; between the two the current
     * visibility is held, so a shown button cannot be toggled off by hovering at
     * the appear threshold. There is no suspension point between the read and the
     * write, so the copy cannot clobber a concurrent update.
     */
    private fun refreshBrowseReCenter(state: MapCanvasUiState) {
        var offsetPx = Double.NaN
        val visible = if (mode != MapMode.BROWSE || state.gpsFixQuality == GpsFixQuality.NONE) {
            browseReCenterBeyondSinceMs = 0L
            false
        } else {
            offsetPx = browseCenterOffsetPx(
                state.viewport.centerLat, state.viewport.centerLon,
                state.viewport.magnification, state.viewport.angle,
                screenWidth, screenHeight, projectionDpi,
                state.gpsMarkerLat, state.gpsMarkerLon
            )
            when {
                offsetPx.isNaN() -> {
                    browseReCenterBeyondSinceMs = 0L
                    false
                }
                offsetPx < RECENTER_HIDE_OFFSET_PX -> {
                    browseReCenterBeyondSinceMs = 0L
                    false
                }
                state.browseReCenterVisible -> true
                offsetPx >= RECENTER_SHOW_OFFSET_PX -> {
                    val now = timeSource.nowMillis()
                    if (browseReCenterBeyondSinceMs == 0L) browseReCenterBeyondSinceMs = now
                    now - browseReCenterBeyondSinceMs >= RECENTER_DWELL_MS
                }
                else -> {
                    // Inside the hysteresis band and not shown yet: no dwell
                    // accrues, so position noise that keeps dipping below the
                    // appear threshold never reveals the button.
                    browseReCenterBeyondSinceMs = 0L
                    false
                }
            }
        }
        if (state.browseReCenterVisible != visible) {
            _uiState.value = state.copy(browseReCenterVisible = visible)
            logBrowseReCenter(offsetPx, visible)
        }
    }

    /**
     * Evidence for the derived re-center rule — no screenshot shows why the
     * button appeared. [com.naviveylin.core.DiagnosticsLog.log] only buffers, so
     * this never touches the file on the caller's thread.
     */
    private fun logBrowseReCenter(offsetPx: Double, visible: Boolean) {
        val offset = if (offsetPx.isNaN()) "-" else "%.1f".format(offsetPx)
        Log.d(TAG, "browse re-center off=" + offset + "px vis=" + visible)
        com.naviveylin.core.DiagnosticsLog.log(
            "RECENTER",
            "off=" + offset + "px vis=" + visible +
                " mag=%.2f".format(_uiState.value.viewport.magnification)
        )
    }

    /**
     * Re-center in BROWSE (spec: map-modes — Browse re-center): put the current
     * GPS position at the center of the map, stay in BROWSE, hide the button.
     * Deliberately not anchor-framed: the vehicle anchor presets configure the
     * driving modes, and an off-center target would immediately re-show the
     * button the user just dismissed.
     */
    fun recenterInBrowse() {
        if (mode != MapMode.BROWSE) return
        val loc = locationService.location.value
        if (loc != null) {
            val vp = _uiState.value.viewport
            _uiState.value = _uiState.value.copy(
                browseReCenterVisible = false,
                viewport = vp.copy(centerLat = loc.lat, centerLon = loc.lon)
            )
            renderMap()
        }
    }

    /**
     * Re-anchor the follow framing on a displayed (predicted) position and render
     * (spec: smooth-follow — Anchor-centered follow framing, Prediction state
     * update). The follow display loop calls this when the blit offset has reached
     * the overrun margin, so the frame is re-rendered with the display position
     * back inside the margin — still anchor-centered, so the vehicle does not jump
     * to the screen center.
     *
     * Single follow center (delta fix-follow-vehicle-jumps): this and the per-fix
     * zoom/rotation renders are the ONLY follow re-renders, and both anchor on
     * [MapCanvasViewModel.followDisplayLat/Lon] — a fix never re-commits the
     * follow center on its own.
     */
    fun renderFollowFrameAt(lat: Double, lon: Double) {
        if (!_uiState.value.followMode || sessionHoldsCamera()) return
        if (lat.isNaN() || lon.isNaN()) return
        val vp = _uiState.value.viewport
        val (targetLat, targetLon) = followRenderTarget(lat, lon, vp.magnification, vp.angle)
        _uiState.value = _uiState.value.copy(
            viewport = vp.copy(centerLat = targetLat, centerLon = targetLon)
        )
        renderMap()
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
            if (loc != null && !loc.markerBearing.isNaN()) {
                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        angle = -Math.toRadians(loc.markerBearing)
                    )
                )
                renderMap()
            }
        }
        viewModelScope.launch {
            settingsStorage.update { it.copy(freeFormNorthUp = northUp) }
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
            settingsStorage.update { it.copy(navNorthUp = northUp) }
        }
    }

    /** Get current location for overlay rendering. */
    fun getCurrentLocation(): GpsFix? = locationService.location.value

    /** Lease held while the phone map surface wants GPS (spec: `location-updates-lease`). */
    private var mapLease: LocationLease? = null



    /**
     * Lease GPS updates for the visible phone map (spec: `location-updates-lease`).
     * Idempotent, so the lifecycle callers (screen init, resume) can call it
     * freely; the matching [stopLocationUpdates] releases this surface's lease
     * without touching a lease held by navigation or by the car session.
     */
    fun startLocationUpdates() {
        if (mapLease == null) {
            mapLease = locationService.acquire(LocationConsumers.PHONE_MAP)
        }
    }

    /** Release the visible-phone-map GPS lease. */
    fun stopLocationUpdates() {
        mapLease?.release()
        mapLease = null
    }

    /** Check if location permission is granted. */
    fun hasLocationPermission(): Boolean = locationService.hasPermission

    /** Toggle favorites sheet visibility. */
    fun toggleFavoritesSheet() {
        _uiState.value = _uiState.value.copy(
            showFavoritesSheet = !_uiState.value.showFavoritesSheet
        )
    }

    /**
     * Re-check READ_CONTACTS and update [MapCanvasUiState.addressBookAvailable]
     * (spec: address-book-permission — permission state drives feature
     * visibility). Called on init, on permission result, and on resume so
     * later grants/revocations in system settings apply without a restart.
     */
    fun refreshAddressBookAvailability() {
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CONTACTS
            )
        if (_uiState.value.addressBookAvailable != granted) {
            _uiState.value = _uiState.value.copy(addressBookAvailable = granted)
        }
    }

    /**
     * Show the resolved address-book object in the existing details view
     * (spec: address-book-search — details view for the resolved object).
     * Mirrors search-result selection but without search history recording.
     */
    fun onAddressBookResultSelected(entry: LocationEntry) {
        Log.d(TAG, "onAddressBookResultSelected: label='${entry.label}' source=address-book")
        viewModelScope.launch {
            if (_uiState.value.followMode) {
                _uiState.value = _uiState.value.copy(followMode = false)
                settingsStorage.update { it.copy(followMode = false) }
            }
            _uiState.value = _uiState.value.copy(
                searchOpen = false,
                searchMode = SearchMode.CONTACTS,
                selectedLocation = entry,
                objectDescription = null,
                isLongPress = false,
                showDetailsSheet = true,
                detailsFromPoiSearch = false,
                detailsFromAddressBook = true,
                isLoading = true
            )
            updateCenter(entry.lat, entry.lon)
            renderMap()

            // Fetch full object description at the resolved location
            val desc = withContext(defaultDispatcher) {
                try {
                    client.getDescription(entry.lat, entry.lon, _uiState.value.viewport.magnification.roundToInt())
                } catch (e: Exception) {
                    Log.e(TAG, "getDescription failed for address-book result", e)
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
                    this.adminRegionHierarchy = entry.adminRegionHierarchy
                    this.name = entry.name
                    this.objectType = entry.objectType
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

    /** Select the current GPS position as a search result (centers map + details sheet). */
    fun selectCurrentLocation() {
        val loc = locationService.location.value ?: return
        val entry = LocationEntry().apply {
            label = context.getString(R.string.current_location)
            lat = loc.lat
            lon = loc.lon
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
            withContext(defaultDispatcher) { currentSearchAdminRegionHandle() }
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
        publishSearchRequest("")
    }

    /**
     * Move the camera onto an analysed manoeuvre (spec: `route-analysis`). A
     * magnification already at or beyond [MANOEUVRE_FOCUS_MAG] is kept — analysing a
     * step must never zoom the map out from under the user; otherwise the map zooms
     * in to a level at which the junction reads. The magnification goes through
     * [updateMagnification], so a large change is walked rather than jumped.
     */
    /**
     * Move the camera onto an analysed step so that the **whole analysed segment** ends up inside
     * the free map band above the overlay (owner requirement, 2026-10-03: "adjust the zoom level so
     * that the bounding box of the current segment is really completely inside the visible part").
     *
     * The magnification is the one that fits that segment's bounding box with the marker margin,
     * capped at [MANOEUVRE_FOCUS_MAG] so a short step still reads, and never closer than the user's
     * own zoom when that is already inside the segment's fit: a step too long for the current zoom
     * is zoomed *out* to fit — the earlier fixed focus level left parts of it behind the card.
     */
    private fun focusCameraOnSegment(lat: Double, lon: Double, range: IntRange?) {
        if (lat.isNaN() || lon.isNaN()) return
        val bbox = segmentBBox(range) ?: doubleArrayOf(lat, lat, lon, lon)
        val midLat = (bbox[0] + bbox[1]) / 2.0
        val midLon = (bbox[2] + bbox[3]) / 2.0
        val coveredPx = overlayCoveredPx.coerceIn(0, maxOf(screenHeight, 0))
        val marginPx = (MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()
        val fitMag = verifiedAreaFit(
            bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi,
            minZoom = MIN_MAG, angleRad = _uiState.value.viewport.angle,
            coveredPx = coveredPx, marginPx = marginPx
        )
        val targetMag = minOf(
            maxOf(_uiState.value.viewport.magnification, MANOEUVRE_FOCUS_MAG),
            fitMag
        )
        if (_uiState.value.viewport.magnification != targetMag) {
            updateMagnification(targetMag)
        }
        // Put the segment's midpoint on the free band's centre, exactly as the overview fit does:
        // centring it on the canvas put the lower part behind the card.
        placeManoeuvreOnBand(midLat, midLon, coveredPx)
        // The point and the box the placement is standing on, so a card that *grows* under the
        // user (a longer instruction wraps to two lines) can be honoured without a re-fit.
        manoeuvreFocusTarget = doubleArrayOf(midLat, midLon)
        manoeuvreFocusBBox = bbox
        // From here on the overlay's height changes must not trigger the overview re-fit: the min
        // card grows and shrinks with the instruction text, and a re-fit would zoom away from the
        // step the user is looking at (owner finding, 2026-10-03). The flag is cleared by any
        // centre move — the overview fit's own `updateCenter` included, so a re-fit still wins.
        manoeuvreFocusStanding = true
        renderMap()
        logSegmentFit(range, bbox, coveredPx, marginPx)
    }

    /**
     * Where the analysed segment actually ended up, in pixels — the numbers behind "the segment is
     * still not completely visible". Coordinate-free on purpose (spec: `auto-diagnostics`): step
     * indices, the pixel box, the free band and the verdict, never a position.
     */
    private fun logSegmentFit(range: IntRange?, bbox: DoubleArray, coveredPx: Int, marginPx: Int) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        val vp = _uiState.value.viewport
        val projected = ProjectionUtils.viewport(
            vp.centerLat, vp.centerLon, vp.magnification, screenWidth, screenHeight,
            projectionDpi, vp.angle
        )
        var x0 = Double.MAX_VALUE
        var x1 = -Double.MAX_VALUE
        var y0 = Double.MAX_VALUE
        var y1 = -Double.MAX_VALUE
        for (lat in doubleArrayOf(bbox[0], bbox[1])) {
            for (lon in doubleArrayOf(bbox[2], bbox[3])) {
                val (x, y) = projected.geoToScreenRotated(lat, lon)
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        val bandBottom = screenHeight - coveredPx
        val inside = x0 >= marginPx && x1 <= screenWidth - marginPx &&
            y0 >= marginPx && y1 <= bandBottom - marginPx
        Log.d(
            TAG,
            "segment focus: range=$range mag=${vp.magnification} covered=$coveredPx " +
                "band=[0,$bandBottom] margin=$marginPx " +
                "bboxPx=[${x0.toInt()},${x1.toInt()},${y0.toInt()},${y1.toInt()}] inside=$inside"
        )
    }

    /**
     * The card grew under a focused step: keep the user's zoom **unless the segment no longer
     * fits**, in which case widen until it does (never narrow — a layout change must not zoom the
     * user in), and re-place the step on the new band centre. Without this the analysed segment
     * slid behind the taller card (owner finding, 2026-10-03: "segment still cut off").
     */
    private fun refitManoeuvreFocus(bbox: DoubleArray, midLat: Double, midLon: Double, coveredPx: Int) {
        val marginPx = (MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()
        val fitMag = verifiedAreaFit(
            bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi,
            minZoom = MIN_MAG, angleRad = _uiState.value.viewport.angle,
            coveredPx = coveredPx, marginPx = marginPx
        )
        val current = _uiState.value.viewport.magnification
        if (fitMag < current) updateMagnification(fitMag)
        placeManoeuvreOnBand(midLat, midLon, coveredPx)
        manoeuvreFocusStanding = true
        renderMap()
    }

    /**
     * Put the geo point ([midLat], [midLon]) on the centre of the free band the overlay leaves
     * above it, at the *current* magnification: the point is projected with the viewport the map
     * is drawing in and the point that sits half the covered height below the canvas centre
     * becomes the new centre. No zoom is touched, so this is also what a card that grew under a
     * focused step uses (the segment must stay visible, but the user's zoom must stay too).
     */
    private fun placeManoeuvreOnBand(midLat: Double, midLon: Double, coveredPx: Int) {
        if (coveredPx > 0 && screenWidth > 0 && screenHeight > 0) {
            val vp = ProjectionUtils.viewport(
                midLat, midLon, _uiState.value.viewport.magnification,
                screenWidth, screenHeight, projectionDpi, _uiState.value.viewport.angle
            )
            val (centerLat, centerLon) = vp.screenToGeoRotated(
                screenWidth / 2.0, screenHeight / 2.0 + coveredPx / 2.0
            )
            updateCenter(centerLat, centerLon)
        } else {
            updateCenter(midLat, midLon)
        }
    }

    /** Bounding box of the polyline vertices in [range], or null when there is nothing to fit. */
    private fun segmentBBox(range: IntRange?): DoubleArray? {
        // No range (no segment for the analysed step, e.g. the start line) means "no leg": the
        // caller falls back to the step's own point, never to the whole route (a null range that
        // fitted the entire polyline would zoom the user out to the route overview).
        if (range == null) return null
        val lats = routeLats ?: return null
        val lons = routeLons ?: return null
        if (lats.size != lons.size || lats.isEmpty()) return null
        val first = range.first.coerceIn(0, lats.size - 1)
        val last = range.last.coerceIn(first, lats.size - 1)
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (i in first..last) {
            val la = lats[i]
            val lo = lons[i]
            if (la.isNaN() || lo.isNaN()) continue
            if (la < minLat) minLat = la
            if (la > maxLat) maxLat = la
            if (lo < minLon) minLon = lo
            if (lo > maxLon) maxLon = lo
        }
        if (minLat.isInfinite() || minLon.isInfinite()) return null
        return doubleArrayOf(minLat, maxLat, minLon, maxLon)
    }

    /**
     * True while the camera sits on an explicitly selected manoeuvre, so the overlay's height
     * changes do not pull it back to the route overview. Cleared by any centre move (a user
     * pan, a fit) — see [focusCameraOnManoeuvre] for why it is set last there.
     */
    private var manoeuvreFocusStanding = false

    /**
     * Covered height of the session overlay in pixels, as reported by the overlay itself
     * (spec: `route-map-overview` — the fit uses the height the overlay reports for the
     * state it is in). The phone card has fixed heights, so the report is stable. It is
     * deliberately NOT a nominal fraction of the screen: the first implementation assumed
     * 0.22 while the content-driven card covered 0.48, so the fit shifted 614 px too little
     * and the destination end of the route stayed behind the card (device measurement,
     * 2026-10-03: `fitViewportToRoute mag=10.0 coveredPx=528` with the card's top edge at
     * y=1258).
     */
    private var overlayCoveredPx: Int = 0

    /** Covered height the overview was last fitted for (guards a re-fit on a user move). */
    private var lastFittedCoveredPx: Int = -1

    /** The endpoints of the route currently drawn, for a re-fit when the card changes. */
    private var routeFitEndpoints: DoubleArray? = null

    /** The focused segment's bounding box and midpoint, for a card that grows under it. */
    private var manoeuvreFocusBBox: DoubleArray? = null
    private var manoeuvreFocusTarget: DoubleArray? = null

    /**
     * The session overlay reports the height it occupies: the fit shifts for exactly that
     * many free-map pixels, and a change while a route is reviewed re-fits — so switching
     * the card's state can never leave the route behind the card (spec:
     * `route-map-overview`). Ignored while navigating or following, and ignored when the
     * height did not change, so a user-moved viewport stays where the user put it.
     */
    fun setOverlayCoveredPx(px: Int) {
        val clamped = px.coerceIn(0, maxOf(screenHeight, 0))
        if (clamped == overlayCoveredPx) return
        val grew = clamped > overlayCoveredPx
        overlayCoveredPx = clamped
        _uiState.value = _uiState.value.copy(overlayCoveredPx = clamped)
        // Re-fit only when the card *grows*: a growing card can cover the route, while a
        // shrinking one only frees map area — and a shrink accompanies an explicit step
        // selection, whose manoeuvre focus must not be overridden by the overview fit (owner
        // finding, 2026-10-03: the first page after selecting a row showed the overview
        // instead of the manoeuvre, later pages were fine because the height had settled).
        if (!grew) return
        // A standing manoeuvre focus outranks the overview re-fit (it would zoom away from the
        // step the user is looking at), but a card that grew *under* that step covers its lower
        // part — the segment would be clipped again (owner finding, 2026-10-03: "segment still
        // cut off"). So the focused step is re-placed on the new band centre at the same
        // magnification: no zoom moves, nothing slides behind the card.
        if (manoeuvreFocusStanding) {
            val bbox = manoeuvreFocusBBox
            val target = manoeuvreFocusTarget
            if (bbox != null && target != null) {
                refitManoeuvreFocus(bbox, target[0], target[1], clamped)
            }
            return
        }
        if (clamped == lastFittedCoveredPx) return
        if (!canFitRouteOverview()) return
        val lats = routeLats ?: return
        val lons = routeLons ?: return
        val endpoints = routeFitEndpoints ?: return
        applyRouteOverviewFit(lats, lons, endpoints[0], endpoints[1], endpoints[2], endpoints[3])
    }

    /**
     * True while a route-planning session holds the camera (spec:
     * `route-planning-session` — Session holds the camera while active). No
     * follow-driven camera move may run while a session is open, and the follow toggle
     * is refused: the session owns the viewport until it ends.
     */
    internal fun sessionHoldsCamera(): Boolean =
        _routePanelViewModel?.sessionState?.value?.let { it != RouteSessionState.INACTIVE } == true

    /** Update center latitude (called from gesture handler). */
    fun updateCenter(lat: Double, lon: Double) {
        // Any centre move ends the "camera is on a manoeuvre" state: a user pan and the overview
        // fit both land here (the manoeuvre focus sets the flag *after* its own call).
        manoeuvreFocusStanding = false
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

    /**
     * Fit the camera to the route bounding box (spec: route-map-overview —
     * start, target, and polyline all visible). Centers on the bbox midpoint
     * and picks a magnification that fits the larger bbox dimension; the
     * area-favorites floor is overridden with [MIN_MAG] so long trips zoom
     * out far enough. Leaves the viewport untouched when the canvas size is
     * unknown or no usable coordinates exist (spec R3 — degenerate geometry).
     *
     * Sheet-aware (Decision 8): the fit uses the visible map area (canvas minus
     * the covered height of the open route panel) and moves the camera so the
     * bbox midpoint lands on the visible-area center — otherwise the lower part
     * of the route stays hidden behind the panel. A closed panel (coveredPx == 0)
     * keeps the full-canvas behavior; a fully covered canvas skips the fit.
     */
    fun fitViewportToRoute(
        routeLats: DoubleArray?,
        routeLons: DoubleArray?,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        if (routeLats == null || routeLons == null || routeLats.size != routeLons.size) return

        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        fun include(lat: Double, lon: Double) {
            if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }
        for (i in routeLats.indices) include(routeLats[i], routeLons[i])
        include(startLat, startLon)
        include(destLat, destLon)

        // Nothing usable (empty polyline and invalid endpoints).
        if (minLat.isInfinite() || minLon.isInfinite()) return

        // Free map area: the session's overlay reports the height it occupies for the
        // state it is in, so the fit moves the camera for exactly those free-map pixels
        // and no cross-surface measurement race exists (spec: `route-map-overview` — the
        // fit uses the reported height, not a nominal fraction).
        val coveredPx = overlayCoveredPx.coerceIn(0, screenHeight)
        if (coveredPx >= screenHeight) return

        // The start and target markers are drawn as pins around their coordinate, so the fit
        // keeps a marker-sized margin inside the free band: the verification used to accept a
        // bbox *exactly* at the band's edge, which clipped half a pin (owner finding,
        // 2026-10-03: "I would also expect start and end marker to be visible").
        val markerMarginPx = (MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()

        // Degenerate span (point/vertical/horizontal) degrades to NODE_ZOOM
        // inside computeAreaZoom — center still moves to the endpoints' midpoint.
        val bbox = doubleArrayOf(minLat, maxLat, minLon, maxLon)
        val midLat = (minLat + maxLat) / 2.0
        val midLon = (minLon + maxLon) / 2.0
        val angle = _uiState.value.viewport.angle
        // The overview must never clip the route. The shared fit seam
        // (`FitVerification.kt`) verifies the projected bbox around the camera it
        // will actually use and steps one whole level out while the bbox does not
        // stay inside the visible band — necessary because whole-level rounding
        // can move an exact fit up, and a rotated view needs a larger screen hull
        // than the north-up bbox suggests.
        val mag = verifiedAreaFit(
            bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi,
            minZoom = MIN_MAG, angleRad = angle, coveredPx = coveredPx,
            marginPx = markerMarginPx
        )
        // The camera center is drawn at the canvas center, so to put the bbox
        // midpoint on the visible-area center (coveredPx / 2 px above it) the
        // camera must sit that far below the midpoint on screen — the content
        // shifts up, clear of the panel. Projected with the renderer's own
        // projection (DPI- and rotation-aware), so the shift is exact.
        val fittedViewport = ProjectionUtils.viewport(
            midLat, midLon, mag, screenWidth, screenHeight, projectionDpi, angle
        )
        val (centerLat, centerLon) = fittedViewport.screenToGeoRotated(
            screenWidth / 2.0, screenHeight / 2.0 + coveredPx / 2.0
        )
        updateCenter(centerLat, centerLon)
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(magnification = mag)
        )
        renderMap()
        Log.d(TAG, "fitViewportToRoute mag=" + mag + " coveredPx=" + coveredPx)
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
        when (mode) {
            MapMode.NAVIGATION, MapMode.FREE_DRIVE -> {
                if (_uiState.value.navNorthUp) {
                    _uiState.value = _uiState.value.copy(navNorthUp = false)
                }
            }
            MapMode.BROWSE -> {
                if (_uiState.value.freeFormNorthUp) {
                    _uiState.value = _uiState.value.copy(freeFormNorthUp = false)
                }
            }
        }
    }

    /** Update magnification (called from zoom controls or pinch; fractional values
     *  allowed — the pinch gesture commits continuous magnification).
     *
     *  [walk] false is for programmatic camera fits (POI fit), which land directly and
     *  do not run the display zoom animation (spec: smooth-zoom - the bounded window
     *  applies to the animated zoom input paths). */
    fun updateMagnification(mag: Double, walk: Boolean = true) {
        val clamped = mag.coerceIn(MIN_MAG, MAX_MAG)
        commitMagnification(clamped, walk)
        // Detect user-initiated zoom: in the driving modes suspend the drive
        // preset (auto-zoom included; spec: map-modes — drive suspension and
        // reset). In BROWSE nothing has to be recorded — the re-center button
        // follows the measured offset between the map center and the vehicle
        // (spec: map-modes — Browse re-center).
        if (mode == MapMode.FREE_DRIVE || mode == MapMode.NAVIGATION) {
            if (_uiState.value.autoZoomEnabled && !autoZoomSuspended) {
                setAutoZoomSuspended(true)
                val navVm = _navigationViewModel
                val rawSpeed = navVm?.state?.value?.currentSpeedKmH ?: Double.NaN
                lastSpeedBandIndex = if (!rawSpeed.isNaN() && rawSpeed >= 0) SpeedZoomTable.bandIndex(filterSpeed(rawSpeed)) else -1
            }
        }
    }

    /** Increment magnification by 1 level (snaps to the next whole level above
     *  the current fractional magnification — discrete controls stay level-based). */
    fun zoomIn() {
        val snapped = kotlin.math.round(_uiState.value.viewport.magnification)
        updateMagnification(snapped + 1.0)
    }

    /** Decrement magnification by 1 level (snaps to the whole level below). */
    fun zoomOut() {
        val snapped = kotlin.math.round(_uiState.value.viewport.magnification)
        updateMagnification(snapped - 1.0)
    }

    /**
     * Record [target] as the viewport magnification (spec: smooth-zoom - Eased zoom
     * animation on discrete zoom input). The viewport records the FINAL magnification
     * immediately; when [walk] is set and the change is farther than the window the
     * displayed frame can serve, the render pipeline advances to it in bounded steps,
     * one step per landed render ([advanceZoomWalk]).
     */
    private fun commitMagnification(target: Double, walk: Boolean = true) {
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(magnification = target)
        )
        if (walk) beginZoomWalk(target) else cancelZoomWalk()
    }

    /**
     * Start (or re-target) a bounded magnification walk toward [target] from the frame
     * in hand. Only the walk's step is published - [MapCanvasUiState.viewport] already
     * carries the final magnification.
     */
    private fun beginZoomWalk(target: Double) {
        zoomWalk.cancel()
        val frontMag = _uiState.value.renderViewport?.mag ?: 0.0
        val step = zoomWalk.request(target, frontMag)
        _uiState.value = _uiState.value.copy(
            zoomWalkStepMag = if (zoomWalk.active) step else Double.NaN
        )
        Log.d(TAG, "zoomWalk request target=" + target + " step=" + step + " front=" + frontMag)
    }

    /** Abandon a pending walk (a commit that lands directly, e.g. a camera fit). */
    private fun cancelZoomWalk() {
        if (zoomWalk.active) Log.d(TAG, "zoomWalk cancelled")
        zoomWalk.cancel()
        if (!_uiState.value.zoomWalkStepMag.isNaN()) {
            _uiState.value = _uiState.value.copy(zoomWalkStepMag = Double.NaN)
        }
    }

    /**
     * A frame rendered at [frontMag] landed: commit the next bounded step when a walk is
     * pending (spec: smooth-zoom - the displayed frame never shows a magnification
     * farther than the frame in hand can serve). No-op without a pending walk.
     */
    private fun advanceZoomWalk(frontMag: Double) {
        val next = zoomWalk.onFrameLanded(frontMag) ?: return
        // The landing step is published as the walk's last step only while the walk is
        // still pending; once it is finished the display target falls back to the
        // recorded viewport magnification (which is the same value).
        _uiState.value = _uiState.value.copy(
            zoomWalkStepMag = if (zoomWalk.active) next else Double.NaN
        )
        Log.d(TAG, "zoomWalk step=" + next + " front=" + frontMag + " target=" + zoomWalk.target)
        renderMap()
    }

    /**
     * Applies one rendered frame to the UI state. A frame that arrives while the phone map is
     * suspended is discarded rather than published (spec: map-canvas-screen — A car session
     * suspends the phone map): a render that was in flight at the suspension edge must not put
     * a frame back into a surface that has given its storage up.
     */
    @VisibleForTesting
    internal suspend fun applyRenderedFrame(frame: MapRenderer.FrameState) {
        val bitmap = frame.bitmap ?: return
        if (phoneMapRenderRefused) {
            Log.d(TAG, "frameFlow: frame discarded, phone map suspended")
            return
        }
        // First frame: the DB may have opened after the initMap flag push (SetStyleFlag is a
        // no-op until a DB is open) — re-apply once.
        if (!stylePushedToNative) {
            stylePushedToNative = true
            ensureMapStyle(null, resolvedDaylight(), MapStyleApplyReason.FIRST_RENDERED_FRAME)
        }
        _uiState.value = _uiState.value.copy(
            renderedBitmap = bitmap.asImageBitmap(),
            isLoading = false,
            error = null,
            renderViewport = frame.viewport
            // gpsMarker* is intentionally NOT set here: the location collector updates it on
            // every fix (updateMarkerState) so the marker tracks the vehicle without waiting
            // for a render.
        )
        Log.d(TAG, "frontBufferFlow: new bitmap " + bitmap.width + "x" + bitmap.height)
        // A landed frame advances a pending magnification walk (spec: smooth-zoom - one step
        // per landed render).
        advanceZoomWalk(frame.viewport.mag)
    }

    // ---- Car-session suspension (spec: map-canvas-screen) ----

    /** The presence edge this ViewModel last applied, so a repeated value is not an edge. */
    private var carSessionActive: Boolean = false

    /**
     * True while the phone map must not render (spec: map-canvas-screen — A car session
     * suspends the phone map). The request is refused, not deferred and not queued: a refused
     * request leaves no state behind that a later emission could turn into a frame.
     */
    private val phoneMapRenderRefused: Boolean
        get() = _uiState.value.phoneMapSuspended

    /**
     * Applies a car-session presence edge: a rising edge suspends the phone map, a falling
     * edge resumes it. An unchanged value is not an edge, so the release and the resume run
     * once per session rather than once per emission.
     */
    private fun onCarSessionPresenceChanged(active: Boolean) {
        if (active == carSessionActive) return
        carSessionActive = active
        if (active) suspendPhoneMap() else resumePhoneMap("car session ended")
    }

    private fun suspendPhoneMap() {
        if (_uiState.value.phoneMapSuspended) return
        // The released frame must not stay referenced by the UI: the renderer drops its own
        // copy and the state drops the emitted one in the same transition, so nothing composes
        // a bitmap the renderer no longer owns.
        _uiState.value = _uiState.value.copy(
            phoneMapSuspended = true,
            phoneMapOverridden = false,
            renderedBitmap = null
        )
        val released = mapRenderer?.releaseRenderStorage()
        DiagnosticsLog.log(
            DiagnosticsLog.SESSION_TAG,
            "phone map suspended: car session active (presence=true, released tiles=" +
                "${released?.tilesCleared ?: 0} frameBuffers=${released?.frameBuffersReleased ?: 0} " +
                "idleTargets=${released?.idleTargetsReleased ?: 0}; shared native tile-data cache untouched)"
        )
    }

    /**
     * Ends the suspension and renders a fresh frame (design D5): the released frame buffers
     * leave nothing to reuse, and the frame is rendered at the CURRENT viewport and navigation
     * state — never at the state the suspension froze.
     */
    private fun resumePhoneMap(reason: String) {
        val state = _uiState.value
        val wasOverridden = state.phoneMapOverridden
        if (!state.phoneMapSuspended && !wasOverridden) return
        _uiState.value = state.copy(
            phoneMapSuspended = false,
            phoneMapOverridden = false,
            renderedBitmap = null
        )
        DiagnosticsLog.log(
            DiagnosticsLog.SESSION_TAG,
            "phone map resumed: $reason (presence=$carSessionActive, override=$wasOverridden)"
        )
        if (screenWidth > 0 && screenHeight > 0) renderMap()
    }

    /**
     * The user asked for the phone map for the rest of the car session (spec:
     * map-canvas-screen — The user brings the map back). No-op without a suspension: the
     * override exists only to lift one, and it is cleared at session end.
     */
    fun showMapDuringCarSession() {
        if (!_uiState.value.phoneMapSuspended) return
        _uiState.value = _uiState.value.copy(
            phoneMapSuspended = false,
            phoneMapOverridden = true,
            renderedBitmap = null
        )
        DiagnosticsLog.log(
            DiagnosticsLog.SESSION_TAG,
            "phone map override: map requested during a car session (presence=true)"
        )
        if (screenWidth > 0 && screenHeight > 0) renderMap()
    }

    /** Re-render the map with current viewport via MapRenderer. */
    fun renderMap(forceFullRender: Boolean = false) {
        if (phoneMapRenderRefused) {
            Log.d(TAG, "renderMap: refused — the phone map is suspended by a car session")
            return
        }
        val renderer = mapRenderer ?: run {
            Log.w(TAG, "renderMap: mapRenderer is null — no render")
            return
        }
        val vp = _uiState.value.viewport
        // A pending walk owns the rendered magnification: the viewport already carries
        // the FINAL target, so rendering it directly would land the whole difference in
        // one frame (spec: smooth-zoom - Eased zoom animation on discrete zoom input).
        val mag = zoomWalk.renderMag(vp.magnification)
        Log.d(TAG, "renderMap mag=" + mag + " (viewport " + vp.magnification + ")")
        if (zoomWalk.active && !forceFullRender) {
            // A walked step is paced by the landing of the previous step's frame, so the
            // pan/zoom debounce would only add latency (design D3). A forced overlay
            // render keeps its debounced path and its force flag.
            renderer.requestRenderImmediate(vp.centerLat, vp.centerLon, mag, vp.angle)
        } else {
            renderer.requestRender(vp.centerLat, vp.centerLon, mag, vp.angle, forceFullRender)
        }
    }

    /** Persist current viewport to disk. */
    fun saveViewport() {
        // No-op until initMap has applied the persisted viewport restore:
        // saving during the restore window would overwrite the previously
        // persisted viewport with the uninitialized default (the isValid()
        // guard in ViewportStorage passes — the default center is
        // valid-looking).
        if (!viewportRestored) {
            Log.d(TAG, "saveViewport: skipped, viewport restore not yet applied")
            return
        }
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

    /**
     * Test hook: inject a renderer without opening a map database, so the
     * route-visibility collectors (setRoutePanelViewModel) can be exercised
     * deterministically on the test scheduler.
     */
    @VisibleForTesting
    internal fun setMapRendererForTest(renderer: MapRenderer?) {
        mapRenderer = renderer
    }

    /**
     * Test hook: publish a landed frame at [mag] exactly as the frame collector does
     * (render viewport + walk advance), so the bounded magnification walk can be
     * exercised deterministically without the native render pipeline.
     */
    @VisibleForTesting
    internal fun publishRenderedFrameForTest(mag: Double) {
        val vp = _uiState.value.viewport
        val previous = _uiState.value.renderViewport
        _uiState.value = _uiState.value.copy(
            renderViewport = if (previous != null) {
                previous.copy(mag = mag)
            } else {
                MapRenderer.RenderViewport(vp.centerLat, vp.centerLon, mag, vp.angle)
            }
        )
        advanceZoomWalk(mag)
    }

    override fun onCleared() {
        releaseSearchAdminRegion()
        super.onCleared()
        rendererScope?.cancel()
        mapRenderer?.shutdown()
    }

    companion object {
        /**
         * Re-center button visibility. In the driving modes the viewport is no
         * longer auto-driven when the drive preset is suspended (FREE_DRIVE), or
         * when auto-zoom is suspended or follow was disengaged (NAVIGATION). In
         * BROWSE it is driven by the derived "the map is not centered on the
         * vehicle" condition (spec: map-modes — Browse re-center), not by a
         * remembered interaction.
         */
        internal fun shouldShowReCenterButton(
            mode: MapMode,
            driveSuspended: Boolean,
            browseReCenterVisible: Boolean
        ): Boolean = when (mode) {
            MapMode.FREE_DRIVE -> driveSuspended
            MapMode.BROWSE -> browseReCenterVisible
            MapMode.NAVIGATION -> driveSuspended
        }

        /**
         * Screen-pixel offset of [lat]/[lon] from the center of the map the given
         * viewport shows (spec: map-modes — Browse re-center). Uses the same
         * [ProjectionUtils] projection the marker overlay uses, but against the
         * committed viewport: "the map is centered on the vehicle" is a statement
         * about the framing the user asked for, not about a frame that may lag
         * behind a throttled render. Pure — unit-tested directly. Returns NaN when
         * the position or the canvas metrics are unknown.
         */
        internal fun browseCenterOffsetPx(
            centerLat: Double,
            centerLon: Double,
            mag: Double,
            angle: Double,
            screenW: Int,
            screenH: Int,
            dpi: Double,
            lat: Double,
            lon: Double
        ): Double {
            if (lat.isNaN() || lon.isNaN()) return Double.NaN
            if (screenW <= 0 || screenH <= 0 || dpi <= 0.0) return Double.NaN
            val projected = ProjectionUtils.viewport(
                centerLat, centerLon, mag, screenW, screenH, dpi, angle
            )
            val (sx, sy) = projected.geoToScreenRotated(lat, lon)
            if (sx.isNaN() || sy.isNaN()) return Double.NaN
            return kotlin.math.hypot(sx - screenW / 2.0, sy - screenH / 2.0)
        }

        private const val TAG = "MapCanvasVM"
        private const val FAVORITES_FILE = "favorites.json"

        /** Magnification applied when a drive preset (re)engages follow mode. */
        private const val DRIVE_PRESET_MAG = 15.0

        /**
         * Browse re-center: the vehicle must be this far from the map center
         * (screen px) before the button can appear. A screen measure, so the same
         * value means the same visible displacement at every magnification.
         * Internal so the rule tests calibrate against the same number instead of
         * duplicating it.
         */
        internal const val RECENTER_SHOW_OFFSET_PX = 24.0

        /** Browse re-center: hysteresis — the button hides below this offset. */
        internal const val RECENTER_HIDE_OFFSET_PX = 12.0

        /**
         * Browse re-center: the offset must stay beyond [RECENTER_SHOW_OFFSET_PX]
         * this long before the button appears. This damping is not cosmetic — at
         * high magnification the pixel offset is GPS noise (one screen pixel is
         * ~0.03 m at magnif 20), so no threshold pair is stable without it.
         */
        internal const val RECENTER_DWELL_MS = 1_000L

        /** Fixed high zoom for shared-location candidate lookup (street level). */
        private const val SHARE_CANDIDATE_ZOOM = 16
        private const val GPS_FIX_MAX_ACCURACY_M = 50f

        /** Frequency of the follow-mode stale-speed check (spec: gps-speed-priority). */
        private const val SPEED_STALE_TICK_MS = 1_000L

        /** Frequency of the fix-quality re-evaluation without a new fix (spec: gps-fix-quality). */
        private const val FIX_QUALITY_TICK_MS = 1_000L

        /** Plausibility cap for the speed filter (Autobahn ~200+). */
        private const val MAX_PLAUSIBLE_SPEED_KMH = 250.0

        // Ignore duplicate GPS fixes with same coordinates and bearing within this window.
        private const val GPS_DEDUPE_MS = 100L
        // Minimum interval between follow-mode renders (coalesces GPS ticks).
        internal const val GPS_FOLLOW_RENDER_INTERVAL_MS = 200L
        // Minimum interval between non-follow marker-move renders (old native throttle).
        private const val NON_FOLLOW_MARKER_RENDER_INTERVAL_MS = 1000L
        // Ignore bearing changes smaller than this for follow-mode angle updates.
        private const val MIN_BEARING_DELTA_DEG = 3.0

        // Movement threshold for re-resolving the search admin region now lives
        // in the shared rule (`SearchRegionScope`), which the car search uses too.

        // Max-speed resolution throttle for the follow-mode speed widget:
        // re-resolve only after significant movement or a cooldown.
        private const val MAX_SPEED_RESOLVE_INTERVAL_MS = 5_000L
        private const val MAX_SPEED_RESOLVE_MOVE_M = 50.0
        private const val ROAD_RESOLVE_INTERVAL_MS = 5_000L
        private const val ROAD_RESOLVE_MOVE_M = 50.0
        /** Minimum magnification for the zoom control (buttons, keys, scroll wheel).
         *  Floor of 4 matches the gesture range and the specs (map-pan-zoom, map-rotation-gesture):
         *  lower zooms render huge world tiles natively (z=2 ~5s, z=1 hangs), stalling the render worker. */
        const val MIN_MAG = 4.0

        /**
         * Pixels the route overview keeps clear of the free band's edges, so the start and
         * target marker pins are fully visible (owner finding, 2026-10-03).
         */
        const val MARKER_MARGIN_DP = 48.0
        /** Minimum magnification for the pinch/rotation gesture commit (keeps 4–20). */
        const val GESTURE_MIN_MAG = 4.0
        const val MAX_MAG = 20.0
        /**
         * Magnification an analysed route step is focused at (spec: `route-analysis` —
         * "the magnification SHALL be close enough to see the junction the manoeuvre
         * happens at"). A closer user zoom is kept, never reduced.
         */
        const val MANOEUVRE_FOCUS_MAG = 17.0
        /** Stylesheet flag selecting the daylight variant (`guidelines/MapRendering.md` section 15). */
        const val STYLE_FLAG_DAYLIGHT = "daylight"

        /** POI search radius steps in meters (mirrors JavaScout PoiSearchOverlay, extended to 100 km). */
        val POI_RADIUS_STEPS_M = doubleArrayOf(500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0)
        /** Default POI search radius: middle slider step (5 km, JavaScout default). */
        const val DEFAULT_POI_RADIUS_METERS = 5000.0
        /** Maximum number of POI results per search (JavaScout MAX_RESULTS). */
        const val MAX_POI_RESULTS = 100

        /** Clamp a magnification to the pinch/rotation gesture range (4–20). */
        fun clampGestureMagnification(mag: Double): Double = mag.coerceIn(GESTURE_MIN_MAG, MAX_MAG)

        private const val CANVAS_OVERRUN = 1.2

        /** Fixed zoom level for node-type favorites (points, POIs). */
        private const val NODE_ZOOM = 17.0
        /** Meters per degree of latitude (spherical approximation, matches the fit math). */
        private const val METERS_PER_DEG_LAT = 111320.0

        /** Earth circumference at the equator, used for the meters-per-pixel formula. */
        private const val EARTH_CIRCUMFERENCE_M = 40075016.686

        /** Minimum zoom level for area-type favorites (prevents too-zoomed-out view). */
        private const val MIN_AREA_ZOOM = 14.0

        /**
         * Compute a magnification that fits the given bounding box within the viewport.
         *
         * The magnification is defined against the renderer's ground resolution:
         * the renderer draws at the display DPI, which every render request carries
         * (`MapRenderer.dpi` → the native render's projection DPI; [ProjectionUtils]
         * scales by `REFERENCE_DPI / dpi`, confirmed by the tile
         * geometry in `MapRenderer.tileSizePx`) and Mercator ground distances
         * shrink by `cos(lat)` relative to the equator. Ignoring either factor
         * over-zooms — by `dpi / 96` and by `1 / cos(lat)` — so the fitted bbox
         * would overflow the viewport (≈2x at 48° north on a 160-dpi display).
         * Callers pass the DPI they render at; the default keeps the 96-dpi
         * reference used by pure math callers and tests.
         *
         * @param bbox double[4] = [minLat, maxLat, minLon, maxLon]
         * @param vpWidth viewport width in pixels
         * @param vpHeight viewport height in pixels (the *visible* map height when
         *   part of the canvas is covered by a sheet)
         * @param minZoom floor of the returned magnification; defaults to the
         *   area-favorites floor ([MIN_AREA_ZOOM]), route overviews pass
         *   [MIN_MAG] so long trips fit (spec: route-map-overview)
         * @param dpi display DPI the map is rendered at ([REFERENCE_DPI] = 96 when
         *   unknown); values <= 0 fall back to the reference
         * @return magnification level clamped to [minZoom, MAX_MAG]
         */
        fun computeAreaZoom(
            bbox: DoubleArray,
            vpWidth: Int,
            vpHeight: Int,
            minZoom: Double = MIN_AREA_ZOOM,
            dpi: Double = ProjectionUtils.REFERENCE_DPI
        ): Double {
            if (vpWidth <= 0 || vpHeight <= 0) return NODE_ZOOM

            val minLat = bbox[0]
            val maxLat = bbox[1]
            val minLon = bbox[2]
            val maxLon = bbox[3]

            val dLat = maxLat - minLat
            val dLon = maxLon - minLon
            if (dLat <= 0.0 || dLon <= 0.0) return NODE_ZOOM

            // Earth circumference at equator ~40075 km
            // Convert degree span to approximate meters (ground distance)
            val avgLat = (minLat + maxLat) / 2.0
            val latRad = Math.toRadians(avgLat)
            val cosLat = Math.cos(latRad)
            val metersPerDegLon = METERS_PER_DEG_LAT * cosLat

            val heightMeters = dLat * METERS_PER_DEG_LAT
            val widthMeters = dLon * metersPerDegLon

            if (heightMeters <= 0.0 || widthMeters <= 0.0) return NODE_ZOOM

            // Use 80% of the smaller viewport dimension as the "fitting size"
            val fitSizePx = minOf(vpWidth, vpHeight) * 0.8

            // Ground resolution at the route latitude:
            //   metersPerPixel = circumference / (256 * 2^mag)
            //                    * (REFERENCE_DPI / dpi) * cos(lat)
            // (equator-referenced Mercator resolution, corrected for the display
            // DPI the renderer draws at and for the Mercator ground shrink).
            // Solved for the magnification that fits maxMeters into fitSizePx.
            val maxMeters = maxOf(heightMeters, widthMeters)
            val targetMetersPerPixel = maxMeters / fitSizePx
            val dpiScale = if (dpi > 0.0) dpi / ProjectionUtils.REFERENCE_DPI else 1.0

            val mag = Math.round(
                Math.log(EARTH_CIRCUMFERENCE_M * cosLat / (256.0 * targetMetersPerPixel * dpiScale)) / Math.log(2.0)
            ).toInt()

            return mag.toDouble().coerceIn(minZoom, MAX_MAG)
        }
    }
}

/** Normalize an angle in radians to [-π, π]. */
internal fun normalizeAngle(rad: Double): Double {
    var r = rad
    while (r <= -Math.PI) r += 2.0 * Math.PI
    while (r > Math.PI) r -= 2.0 * Math.PI
    return r
}

/**
 * Marker arrow bearing in degrees for the GPS overlay.
 *
 * The arrow SHALL point in the direction of travel whenever a bearing is
 * available, regardless of the map orientation mode (spec: gps-location-marker).
 * [isNorthUp] is intentionally unused — it exists so callers and tests document
 * that orientation mode must NOT influence the arrow. Returns -1.0 (the
 * "bearing unavailable" sentinel, arrow points north) only for NaN input.
 */
internal fun computeMarkerBearing(isNorthUp: Boolean, rawBearing: Double): Double =
    if (!rawBearing.isNaN()) rawBearing else -1.0

/**
 * Map rotation angle in radians for the given orientation mode.
 *
 * North-up keeps the map at 0° regardless of bearing; follow-direction rotates
 * the map so the bearing points up (spec: compass-settings).
 */
internal fun computeMapAngle(isNorthUp: Boolean, bearing: Double): Double =
    if (isNorthUp) 0.0 else normalizeAngle(-Math.toRadians(bearing))
