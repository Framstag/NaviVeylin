package com.naviveylin.ui.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.BundledMapStyles.DEFAULT_STYLE_NAME
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.SearchReference
import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.RenderMode
import com.naviveylin.data.ViewportState
import com.naviveylin.location.GpsFix
import kotlinx.coroutines.flow.map

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
    /**
     * The car's usual maximum speed in km/h, 30-160, default 120 (spec: `routing-max-speed` —
     * A per-vehicle maximum speed is a persisted setting). One shared value: the sheet writes it
     * and every routing calculation for the car reads it, including the car's own destinations.
     */
    val carMaxSpeedKmh: Int = 120,
    /** The bicycle's usual maximum speed in km/h, 5-40, default 15 (spec: `routing-max-speed`). */
    val bicycleMaxSpeedKmh: Int = 15,
    /** The pedestrian's usual maximum speed in km/h, 2-15, default 5 (spec: `routing-max-speed`). */
    val footMaxSpeedKmh: Int = 5,
    /** Selected map stylesheet (name without the .oss postfix), e.g. "standard". */
    val styleSheet: String = DEFAULT_STYLE_NAME,
    /**
     * Draw the vector symbol of a style entry that carries both a raster icon and a symbol, instead
     * of its raster icon (spec: `map-styles` — Icon-versus-symbol preference is a persisted phone
     * setting). `false` keeps the raster icon in precedence.
     */
    val preferSymbolPoiIcons: Boolean = false,
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
