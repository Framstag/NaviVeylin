package com.naviveylin.ui.map

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.R
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.data.ViewportState
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Set the NavigationViewModel for location forwarding during navigation. */
fun MapCanvasViewModel.setNavigationViewModel(vm: com.naviveylin.navigation.NavigationViewModel) {
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
                viewportStorageKey()?.let { mapKey ->
                    viewportStorage.save(
                        mapKey,
                        ViewportState(endCenterLat, endCenterLon, endMagnification)
                    )
                }
                Log.d(MapCanvasViewModel.TAG, "setNavigationViewModel: navigation ended, " +
                        "mode restored follow=${_uiState.value.followMode} suspended=${_uiState.value.driveSuspended}, " +
                        "mag=$endMagnification")
                renderMap()
            }
        }
    }
}

/** Set the RoutePanelViewModel (injected via Hilt from the screen). */
fun MapCanvasViewModel.setRoutePanelViewModel(vm: RoutePanelViewModel) {
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
internal fun MapCanvasViewModel.canFitRouteOverview(): Boolean =
    _navigationViewModel?.state?.value?.isNavigating != true && !_uiState.value.followMode

/**
 * Apply the one-shot route overview fit for a freshly arrived result (spec:
 * `route-map-overview` — the session owns the fit). No settle delay: there is no
 * cross-surface measurement left to wait for, so the fit runs in the session's own
 * turn. The driving guard is still re-checked — navigation start or a follow
 * re-engage that happened in between wins over the overview (spec R2).
 */
internal fun MapCanvasViewModel.applyRouteOverviewFit(
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
            Log.d(MapCanvasViewModel.TAG, "route overview fit skipped: navigating or following")
            return@launch
        }
        fitViewportToRoute(routeLats, routeLons, startLat, startLon, destLat, destLon)
        lastFittedCoveredPx = overlayCoveredPx
    }
}

/** Open route panel with destination prefilled from details sheet, start = current location. */
fun MapCanvasViewModel.openRoutePanelWithStart(entry: LocationEntry?) {
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
fun MapCanvasViewModel.dismissRoutePanel() {
    _uiState.value = _uiState.value.copy(showRoutePanel = false)
    _routePanelViewModel?.endSession()
}

/** Set route start location (from favorite picking). */
fun MapCanvasViewModel.setRouteStart(entry: LocationEntry) {
    _routePanelViewModel?.setStartLocation(entry)
}

/** Set route destination location (from favorite picking). */
fun MapCanvasViewModel.setRouteDest(entry: LocationEntry) {
    _routePanelViewModel?.setDestLocation(entry)
}

/**
 * Dismiss the details sheet. A plain dismiss (swipe/back) of a details
 * sheet opened from POI search reopens the search dialog in POIs mode with
 * its results intact; from the address book it reopens in Contacts mode. If
 * a route was started from the details sheet, the dialog stays closed
 * (spec: selective action closes both dialogs).
 */
fun MapCanvasViewModel.dismissDetailsSheet() {
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
fun MapCanvasViewModel.showOnMap() {
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
fun MapCanvasViewModel.addSelectedToFavorites(groupName: String, favName: String, isNewGroup: Boolean) {
    val loc = _uiState.value.selectedLocation ?: return
    viewModelScope.launch {
        // A "new group" name that already exists is an error: do not
        // silently add the favorite to the existing group.
        if (isNewGroup && groupName in favoriteRepository.getGroupNames()) {
            _uiState.value = _uiState.value.copy(
                snackbarMessage = context.getString(R.string.group_already_exists, groupName)
            )
            return@launch
        }
        val success = favoriteRepository.addFavorite(groupName, favName, loc.lat, loc.lon)
        _uiState.value = _uiState.value.copy(
            snackbarMessage = if (success) {
                context.getString(R.string.favorite_added_to_group, groupName)
            } else {
                context.getString(R.string.favorite_add_failed)
            }
        )
    }
}

/** Remove selected location from favorites. */
fun MapCanvasViewModel.removeSelectedFromFavorites() {
    val loc = _uiState.value.selectedLocation ?: return
    val existing = favoriteRepository.findFavoriteByLocation(loc.lat, loc.lon) ?: return
    viewModelScope.launch {
        val success = favoriteRepository.deleteFavorite(existing.first, existing.second.name)
        _uiState.value = _uiState.value.copy(
            snackbarMessage = if (success) {
                context.getString(R.string.favorite_removed)
            } else {
                context.getString(R.string.favorite_remove_failed)
            }
        )
    }
}

/** Check if selected location is already a favorite. */
fun MapCanvasViewModel.isSelectedLocationFavorite(): Boolean {
    val loc = _uiState.value.selectedLocation ?: return false
    return favoriteRepository.findFavoriteByLocation(loc.lat, loc.lon) != null
}

/** Get all favorite group names. */
fun MapCanvasViewModel.getFavoriteGroupNames(): List<String> = favoriteRepository.getGroupNames()

/**
 * Record the mode this surface is in for the navigation that is about to start
 * (spec: `map-modes` — navigation end restores prior mode). Called by the navigation adapter
 * *before* it forces follow on; idempotent, so a second session start inside a running one
 * cannot overwrite the recorded mode with a value navigation imposed.
 */
internal fun MapCanvasViewModel.snapshotPreNavigationMode() {
    if (preNavigationFollow != null) return
    preNavigationFollow = _uiState.value.followMode
    preNavigationSuspended = _uiState.value.driveSuspended
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
internal fun MapCanvasViewModel.focusCameraOnSegment(lat: Double, lon: Double, range: IntRange?) {
    if (lat.isNaN() || lon.isNaN()) return
    val bbox = segmentBBox(range) ?: doubleArrayOf(lat, lat, lon, lon)
    val midLat = (bbox[0] + bbox[1]) / 2.0
    val midLon = (bbox[2] + bbox[3]) / 2.0
    val coveredPx = overlayCoveredPx.coerceIn(0, maxOf(screenHeight, 0))
    val marginPx = (MapCanvasViewModel.MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()
    val fitMag = verifiedAreaFit(
        bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi,
        minZoom = MapCanvasViewModel.MIN_MAG, angleRad = _uiState.value.viewport.angle,
        coveredPx = coveredPx, marginPx = marginPx
    )
    val targetMag = minOf(
        maxOf(_uiState.value.viewport.magnification, MapCanvasViewModel.MANOEUVRE_FOCUS_MAG),
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
internal fun MapCanvasViewModel.logSegmentFit(range: IntRange?, bbox: DoubleArray, coveredPx: Int, marginPx: Int) {
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
        MapCanvasViewModel.TAG,
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
internal fun MapCanvasViewModel.refitManoeuvreFocus(bbox: DoubleArray, midLat: Double, midLon: Double, coveredPx: Int) {
    val marginPx = (MapCanvasViewModel.MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()
    val fitMag = verifiedAreaFit(
        bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi,
        minZoom = MapCanvasViewModel.MIN_MAG, angleRad = _uiState.value.viewport.angle,
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
internal fun MapCanvasViewModel.placeManoeuvreOnBand(midLat: Double, midLon: Double, coveredPx: Int) {
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
internal fun MapCanvasViewModel.segmentBBox(range: IntRange?): DoubleArray? {
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
 * The session overlay reports the height it occupies: the fit shifts for exactly that
 * many free-map pixels, and a change while a route is reviewed re-fits — so switching
 * the card's state can never leave the route behind the card (spec:
 * `route-map-overview`). Ignored while navigating or following, and ignored when the
 * height did not change, so a user-moved viewport stays where the user put it.
 */
fun MapCanvasViewModel.setOverlayCoveredPx(px: Int) {
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
internal fun MapCanvasViewModel.sessionHoldsCamera(): Boolean =
    _routePanelViewModel?.sessionState?.value?.let { it != RouteSessionState.INACTIVE } == true

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
fun MapCanvasViewModel.fitViewportToRoute(
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
    val markerMarginPx = (MapCanvasViewModel.MARKER_MARGIN_DP * projectionDpi / 160.0).roundToInt()

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
        minZoom = MapCanvasViewModel.MIN_MAG, angleRad = angle, coveredPx = coveredPx,
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
    Log.d(MapCanvasViewModel.TAG, "fitViewportToRoute mag=" + mag + " coveredPx=" + coveredPx)
}
