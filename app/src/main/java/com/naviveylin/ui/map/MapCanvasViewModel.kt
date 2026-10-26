package com.naviveylin.ui.map

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.RoadInfo
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.FreeDrivingStatus
import com.naviveylin.core.FreeDrivingStatusProvider
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.ZoomWalk
import com.naviveylin.core.search.ClientSearchRequests
import com.naviveylin.core.search.FavoriteSearchMerger
import com.naviveylin.core.search.SearchGate
import com.naviveylin.core.search.SearchRegionScope
import com.naviveylin.core.search.SearchSlots
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryEntry
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportState
import com.naviveylin.data.ViewportStorage
import com.naviveylin.location.LocationLease
import com.naviveylin.location.LocationService
import com.naviveylin.core.FixFreshness
import com.naviveylin.navigation.CarSessionPresenceImpl
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.cos

@HiltViewModel
class MapCanvasViewModel @Inject constructor(
    internal val viewportStorage: ViewportStorage,
    internal val settingsStorage: SettingsStorage,
    internal val assetCopier: AssetCopier,
    internal val client: OSMScoutClient,
    internal val favoriteRepository: FavoriteRepository,
    internal val searchHistoryRepository: SearchHistoryRepository,
    internal val locationService: LocationService,
    internal val darkModeController: DarkModeController,
    internal val sharedLocationHandler: SharedLocationHandler,
    internal val basemapReloadNotifier: BasemapReloadNotifier,
    internal val drivingModeProvider: DrivingModeProvider? = null,
    /**
     * Shared free-driving road/speed status (spec: `current-road-info` — One
     * free-driving road/speed status feeds every surface and the notification).
     * The default instance exists for direct construction in tests; production
     * injects the process-scoped binding.
     */
    internal val freeDrivingStatusProvider: FreeDrivingStatusProvider = FreeDrivingStatusProvider(),
    /**
     * Car-session presence, the source of the phone's suspension (spec: `map-canvas-screen`).
     * The default (an inert instance) exists for direct construction in tests; production
     * injects the process-scoped binding.
     */
    internal val carSessionPresence: CarSessionPresence = CarSessionPresenceImpl(),
    /**
     * The clock the dwell decisions read (spec: `unit-test-suite-runtime` — Injected time instead of a
     * real clock): the BROWSE re-center dwell window compares against this seam, so a case moves time
     * instead of sleeping. The default exists for direct construction in tests; production injects the
     * process-scoped binding.
     */
    internal val timeSource: EngineTimeSource = EngineTimeSource.System,
    @param:ApplicationContext internal val context: Context
) : ViewModel() {

    internal val _uiState = MutableStateFlow(MapCanvasUiState())

    val uiState: StateFlow<MapCanvasUiState> = _uiState.asStateFlow()

    /**
     * The shared free-driving road/speed status (spec: `current-road-info` — One
     * free-driving road/speed status feeds every surface and the notification).
     * The on-screen street label reads this, not a private copy of the road, so it
     * cannot disagree with the ongoing notification.
     */
    val freeDrivingStatus: StateFlow<FreeDrivingStatus?> = freeDrivingStatusProvider.status

    /**
     * The last road the phone resolved at a fix, or null when none was found
     * there. Carried between the fix path (which publishes the fresh speed) and
     * the asynchronous road lookup (which publishes the fresh road).
     */
    internal var freeDrivingRoad: RoadInfo? = null

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

    internal val _gpsFixQuality = MutableStateFlow(GpsFixQuality.NONE)

    val gpsFixQuality: StateFlow<GpsFixQuality> = _gpsFixQuality.asStateFlow()

    /** Merges favorite hits with native search results (spec: favorite-search). */
    internal val favoriteSearchMerger = FavoriteSearchMerger()

    // Admin-region scoping for search: resolved region handle + the position
    // it was resolved at (NaN = none). Reused across keystrokes of a search
    // session and re-resolved only after significant movement.
    internal var searchAdminRegionHandle: Long = 0L

    internal var searchAdminRegionLat: Double = Double.NaN

    internal var searchAdminRegionLon: Double = Double.NaN

    internal var searchAdminRegionName: String? = null

    /** Whether the search panel is currently open (drives eager region resolution). */
    internal var searchPanelOpen: Boolean = false

    internal var mapRenderer: MapRenderer? = null

    internal var rendererScope: CoroutineScope? = null

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

    internal var currentMapKey: String? = null

    /**
     * True once [initMap] has applied the persisted viewport restore to
     * [_uiState]. [saveViewport] no-ops until then, so a lifecycle save
     * during the restore window (initMap suspended at the viewport load)
     * can never overwrite the previously persisted viewport with the
     * uninitialized default (spec: viewport-persist, "Pause during restore
     * does not clobber").
     */
    internal var viewportRestored = false

    /**
     * The in-flight [initMap] coroutine. Cancelled on re-entry (map path
     * change: MAIN screen after a download/update/delete, basemap reload) so
     * a superseded init — still suspended between DB open and viewport
     * restore — can never apply its stale restore, re-arm [viewportRestored],
     * or overwrite [mapRenderer] with a second renderer (spec:
     * viewport-persist, "Re-entry supersedes an in-flight map init").
     */
    internal var initJob: Job? = null

    internal val epoch = AtomicLong(0)

    // Screen dimensions for zoom computation
    internal var screenWidth: Int = 0

    internal var screenHeight: Int = 0

    // Route panel view model — set via setRoutePanelViewModel() from screen
    internal var _routePanelViewModel: RoutePanelViewModel? = null

    // In-flight POI search job (cancelled on re-search and sheet close)
    /**
     * The POI search's request lifecycle. It is button-triggered, so it has no debounce and no
     * minimum text, but it still runs in its own slot with its own generation: leaving the POI view
     * abandons it, and a places or car search never stops it (spec: `search-request-lifecycle`,
     * spec: `poi-search`).
     */
    internal val poiSearchGate = SearchGate(
        scope = viewModelScope,
        requests = ClientSearchRequests(client),
        slot = SearchSlots.POI,
        debounceMs = 0L,
        minQueryLength = 0,
        requestDispatcher = { defaultDispatcher },
        search = { category, generation -> searchPoisOnce(category, generation) }
    )

    // Viewport snapshot taken when POI search opens; restored when the sheet closes
    internal var poiViewportSnapshot: ViewportState? = null

    // Navigation view model — set via setNavigationViewModel() from screen
    internal var _navigationViewModel: com.naviveylin.navigation.NavigationViewModel? = null

    // Navigation position for follow mode (from NavigationViewModel.positionFlow)
    internal var _navPosition: com.framstag.libosmscout.client.NavigationPosition? = null

    // GPS follow throttle state
    internal var lastFollowRenderMs: Long = 0L

    // Non-follow marker render tracking (marker moves > 5 m trigger a render)
    internal var lastMarkerLat = Double.NaN

    internal var lastMarkerLon = Double.NaN

    internal var lastNonFollowMarkerRenderMs: Long = 0L

    // Max-speed resolution throttle (spec: map-speed-widget): re-resolve the
    // road's max speed only after significant movement or a cooldown.
    internal var lastMaxSpeedLat = Double.NaN

    internal var lastMaxSpeedLon = Double.NaN

    internal var lastMaxSpeedResolveMs = 0L

    // Current-road resolution throttle (spec: current-road-info free driving).
    internal var lastRoadLat = Double.NaN

    internal var lastRoadLon = Double.NaN

    internal var lastRoadResolveMs = 0L

    internal var lastGpsLat = Double.NaN

    internal var lastGpsLon = Double.NaN

    internal var lastGpsTime: Long = 0L

    /** Last fix timestamp for the follow-mode stale-speed decay (spec: gps-speed-priority). */
    internal var lastSpeedFixTime: Long = 0L

    internal var smoothedCenterLat = Double.NaN

    internal var smoothedCenterLon = Double.NaN

    // No center smoothing in follow mode: the viewport center must stay on the
    // raw GPS fix so the marker does not drift away from the road/track. Only
    // large jumps (>500 m) reset directly to avoid lag after teleports.
    internal val centerSmoothAlpha = 1.0

    internal val centerSmoothMaxJumpM = 500.0

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

    // Auto-zoom state
    internal var lastValidSpeedKmH: Double = Double.NaN

    internal var autoZoomSuspended: Boolean = false

    // Vehicle anchors from the shared settings (spec: smooth-follow — Vehicle
    // position anchor): routing while guidance is active, free-driving otherwise.
    internal var routingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    internal var freeDrivingAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    // Regions of the map canvas covered by the phone's own overlays (px), pushed by
    // the screen on every real size change (spec: smooth-follow — visible-area
    // scenarios). 0 = nothing measured = the preset fraction itself, which is the
    // pre-feature framing and the AA behavior.
    internal var overlayInsetLeftPx: Int = 0

    internal var overlayInsetTopPx: Int = 0

    internal var overlayInsetRightPx: Int = 0

    internal var overlayInsetBottomPx: Int = 0

    /** Projection DPI of the phone display (the value the renderer renders at). */
    internal val projectionDpi: Double
        get() = context.resources.displayMetrics.densityDpi.toDouble()

    internal var lastSpeedBandIndex: Int = -1

    internal var currentTargetMag: Double = 15.0

    // Zoom-change throttling (spec: auto-speed-zoom — Smooth zoom
    // transitions): lastAutoZoomCommitMs == 0L means "no commit yet" (direct
    // jump to target on the first fix, spec's "Speed unknown" scenario);
    // after that the zoom converges at most MAX_ZOOM_STEP_PER_UPDATE levels
    // per position update via SpeedZoomTable.stepToward (fractional, no
    // integer rounding). The epsilon no-op makes constant speeds commit-nothing.
    internal var lastAutoZoomCommitMs: Long = 0L

    // Bounded magnification walk (spec: smooth-zoom - Eased zoom animation on discrete
    // zoom input): the viewport records the FINAL requested magnification while the
    // render pipeline and the display animation advance to it in steps the frame in
    // hand can serve - one step per LANDED render. Owns the step arithmetic only;
    // scheduling lives here and in the frame collector.
    internal val zoomWalk = ZoomWalk()

    // Bearing fallbacks: last used bearing/angle survive when the location
    // layer has no fresh bearing yet (e.g. standstill).
    internal var lastUsedBearing = Double.NaN

    internal var lastUsedAngle = Double.NaN

    // Deadband vs the rendered angle: map rotation only moves when the smoothed
    // bearing exceeds this — prevents re-rendering on every small change.
    internal val MIN_BEARING_DELTA_DEG = 2.0

    // Per-render rotation limit. Renders run at the GPS fix cadence (~1/s), so
    // 90°/frame lets a sharp 90° turn complete in 1-2 frames instead of slowly
    // crawling (30°/frame took ~13 s for a 92° turn in the replay).
    internal val MAX_ANGLE_RATE_DEG_PER_RENDER = 90.0

    // Turn tracking: distance past the last turn waypoint (negative = approaching, positive = past)
    internal var turnPassedDistance: Double = Double.NaN

    // Debug log sampling counter
    internal var logCount: Int = 0

    // Route geometry for curve detection
    internal var routeLats: DoubleArray? = null

    internal var routeLons: DoubleArray? = null

    // Last route result the camera was fitted to (spec: route-map-overview —
    // stale re-emissions must not move a user-moved viewport). Reset to null
    // when the route is cleared so an identical re-calculation can refit.
    internal var lastFittedResult: RouteResult? = null

    // Pending route-overview fit (spec: route-map-overview, Decision 8). The fit
    // runs one settle delay after the result arrives so the route panel reports
    // its final measured height; a newer result, a cleared route, or a user
    // gesture inside the window cancels it.
    internal var routeFitJob: Job? = null

    /**
     * The stylesheet and `daylight` flag pair the native side holds, or `null` while nothing has
     * been applied yet. Both native calls reload the whole stylesheet set (`loadStyleSheet` and
     * `setStyleSheetFlag`), so this pair is the dedupe key (spec: map-styles - One stylesheet
     * load per active style and flag set; `guidelines/MapRendering.md` section 15/16). `styleName`
     * is `null` after a flag-only push: the flag does not say which stylesheet is installed.
     */
    internal var appliedMapStyle: AppliedMapStyle? = null

    /** True once the flag was pushed with a map database open (see [MapStyleApplyReason]). */
    internal var stylePushedToNative = false

    /**
     * Symbol/icon preference last handed to the native client. Starts at the client's own default, so
     * an app start that agrees with it issues no call.
     */
    internal var preferSymbolNative: Boolean = false

    /**
     * Set once the user changes the preference in this session. The start-up settings load then leaves
     * the value alone: it can resume after the change (a settings read is a suspension point), and the
     * user's newer choice must not be overwritten by the persisted one it replaced.
     */
    internal var preferSymbolSetByUser = false

    /**
     * Set once the user changes the dark mode preference in this session. The start-up settings load
     * then leaves the value alone: it can resume after the change (a settings read is a suspension
     * point), and the user's newer choice must not be overwritten by the persisted one it replaced
     * (spec: `dark-mode` — A user change outranks an in-flight settings read).
     */
    internal var darkModePreferenceSetByUser = false

    /**
     * Set once the user changes the ambient light sensitivity in this session; the start-up settings
     * load then leaves it alone, for the same reason as [darkModePreferenceSetByUser].
     */
    internal var ambientSensitivitySetByUser = false

    /**
     * The phone places search's request lifecycle: debounce, the minimum query length, supersede
     * and abandon, shared with the other surfaces (spec: `search-request-lifecycle`). A request is
     * a call of [SearchGate.request], so a repeated query text is a new request and still runs
     * (spec: `location-search` — A repeated query text still runs the search; spec: `search-history`
     * — History selection replays the search), and a superseded or abandoned request stops its
     * native walk instead of running to completion.
     */
    internal val searchGate = SearchGate(
        scope = viewModelScope,
        requests = ClientSearchRequests(client),
        slot = SearchSlots.PLACES,
        requestDispatcher = { defaultDispatcher },
        search = { query, generation -> mergeSearchResults(query, generation) }
    )

    /** Set once the map database is open and the renderer is ready. */
    internal val mapReady = MutableStateFlow(false)

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
    internal val fixQualityTicks = MutableStateFlow(0L)

    /**
     * How many fix-quality ticks have completed, counted for **every** tick rather than only for a
     * detected change. Internal test seam (spec `unit-test-suite-runtime` — Awaiting state, not a
     * deadline): the tick loop deliberately runs on a real dispatcher with the real clock, so a case that
     * needs "several ticks with nothing changing" awaits this counter instead of a wall-clock window.
     */
    internal val fixQualityTickCount = MutableStateFlow(0L)

    /**
     * Timestamp the browse offset first exceeded the appear threshold, or 0 while it is not beyond it
     * (spec: map-modes — Browse re-center). `@Volatile` because the shared re-evaluation tick reads it
     * from its own thread; the visibility decision itself stays on the collector's dispatcher
     * (design D2). It is cleared once the button is visible, so a revealed button costs no further tick
     * work.
     *
     * Declared before [init] for the same reason as [fixQualityTicks]: the ticker launched there runs
     * its body during construction on `Dispatchers.Main.immediate`.
     */
    @Volatile
    internal var browseReCenterBeyondSinceMs = 0L

    /**
     * Counter driving the browse re-center dwell completion (spec: map-modes — Browse re-center). The
     * appear dwell is armed by a state emission but must complete on elapsed time, because the failing
     * case has no later emission: a stationary vehicle goes silent (the provider's min-distance
     * throttling) and the user is not touching the map. Incremented only while an arm is pending, so a
     * settled viewport emits nothing.
     */
    internal val recenterDwellTicks = MutableStateFlow(0L)

    /**
     * How many dwell re-evaluations have been published, counted **every** time one is published.
     * Internal test seam (spec `unit-test-suite-runtime` — Awaiting state, not a deadline): the tick
     * runs on a real dispatcher with the real clock, so a case awaits this counter instead of a
     * wall-clock window.
     */
    internal val recenterDwellTickCount = MutableStateFlow(0L)

/**
 * The collectors this ViewModel owns, one call per topic. Each `wire*` function is declared beside the
 * code it drives (see the `MapCanvasViewModel*.kt` files), and the order below is the order the
 * collectors were started in, which is the order they first subscribed in.
 */
init {
    wireStartup()
    wireDrivingModeVotes()
    wireBasemapReload()
    wireCarSession()
    wireSharedLocation()
    wirePlaceSearch()
    wirePoiSearch()
    wireFixQuality()
    wireDarkPresentation()
    wireFavoriteChanges()
    wireStaleSpeed()
    wireBrowseReCenter()
    wireFollowMode()
    wirePersistedSettings()
    wireMapStyles()

    // The route results and the clear-route signals are collected in [MapCanvasViewModel.setRoutePanelViewModel] rather than here: collecting them from `init` ran before the screen had set the route panel up (init-timing), so both moved to the setter.
}

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
    internal var preNavigationFollow: Boolean? = null

    internal var preNavigationSuspended: Boolean? = null

    /** Lease held while the phone map surface wants GPS (spec: `location-updates-lease`). */
    internal var mapLease: LocationLease? = null

    /**
     * True while the camera sits on an explicitly selected manoeuvre, so the overlay's height
     * changes do not pull it back to the route overview. Cleared by any centre move (a user
     * pan, a fit) — see [focusCameraOnManoeuvre] for why it is set last there.
     */
    internal var manoeuvreFocusStanding = false

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
    internal var overlayCoveredPx: Int = 0

    /** Covered height the overview was last fitted for (guards a re-fit on a user move). */
    internal var lastFittedCoveredPx: Int = -1

    /** The endpoints of the route currently drawn, for a re-fit when the card changes. */
    internal var routeFitEndpoints: DoubleArray? = null

    /** The focused segment's bounding box and midpoint, for a card that grows under it. */
    internal var manoeuvreFocusBBox: DoubleArray? = null

    internal var manoeuvreFocusTarget: DoubleArray? = null

    // ---- Car-session suspension (spec: map-canvas-screen) ----

    /** The presence edge this ViewModel last applied, so a repeated value is not an edge. */
    internal var carSessionActive: Boolean = false

    /**
     * True while the phone map must not render (spec: map-canvas-screen — A car session
     * suspends the phone map). The request is refused, not deferred and not queued: a refused
     * request leaves no state behind that a later emission could turn into a frame.
     */
    internal val phoneMapRenderRefused: Boolean
        get() = _uiState.value.phoneMapSuspended

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

        internal const val TAG = "MapCanvasVM"
        internal const val FAVORITES_FILE = "favorites.json"

        /** Magnification applied when a drive preset (re)engages follow mode. */
        internal const val DRIVE_PRESET_MAG = 15.0

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
        internal const val SHARE_CANDIDATE_ZOOM = 16
        internal const val GPS_FIX_MAX_ACCURACY_M = 50f

        /** Frequency of the follow-mode stale-speed check (spec: gps-speed-priority). */
        internal const val SPEED_STALE_TICK_MS = 1_000L

        /** Frequency of the fix-quality re-evaluation without a new fix (spec: gps-fix-quality). */
        internal const val FIX_QUALITY_TICK_MS = 1_000L

        /** Plausibility cap for the speed filter (Autobahn ~200+). */
        internal const val MAX_PLAUSIBLE_SPEED_KMH = 250.0

        // Ignore duplicate GPS fixes with same coordinates and bearing within this window.
        internal const val GPS_DEDUPE_MS = 100L
        // Minimum interval between follow-mode renders (coalesces GPS ticks).
        internal const val GPS_FOLLOW_RENDER_INTERVAL_MS = 200L
        // Minimum interval between non-follow marker-move renders (old native throttle).
        internal const val NON_FOLLOW_MARKER_RENDER_INTERVAL_MS = 1000L
        // Ignore bearing changes smaller than this for follow-mode angle updates.
        internal const val MIN_BEARING_DELTA_DEG = 3.0

        // Movement threshold for re-resolving the search admin region now lives
        // in the shared rule (`SearchRegionScope`), which the car search uses too.

        // Max-speed resolution throttle for the follow-mode speed widget:
        // re-resolve only after significant movement or a cooldown.
        internal const val MAX_SPEED_RESOLVE_INTERVAL_MS = 5_000L
        internal const val MAX_SPEED_RESOLVE_MOVE_M = 50.0
        internal const val ROAD_RESOLVE_INTERVAL_MS = 5_000L
        internal const val ROAD_RESOLVE_MOVE_M = 50.0
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

        internal const val CANVAS_OVERRUN = 1.2

        /** Fixed zoom level for node-type favorites (points, POIs). */
        internal const val NODE_ZOOM = 17.0
        /** Meters per degree of latitude (spherical approximation, matches the fit math). */
        internal const val METERS_PER_DEG_LAT = 111320.0

        /** Earth circumference at the equator, used for the meters-per-pixel formula. */
        internal const val EARTH_CIRCUMFERENCE_M = 40075016.686

        /** Minimum zoom level for area-type favorites (prevents too-zoomed-out view). */
        internal const val MIN_AREA_ZOOM = 14.0

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
