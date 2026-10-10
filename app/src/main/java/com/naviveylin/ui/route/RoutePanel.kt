package com.naviveylin.ui.route

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.Vehicle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.naviveylin.R
import com.naviveylin.core.ResultMarkings
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.formatStepDurationText
import com.naviveylin.core.search.ResultMarking
import com.naviveylin.core.search.SearchQueryParser
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.SearchResultRanker
import com.naviveylin.util.formatDistanceKm
import kotlin.math.roundToInt

/**
 * Whether the session's overlay docks to the side instead of using the phone card
 * (spec: `route-planning-session` — Overlay docks when width allows). Extracted pure so
 * the placement rule is unit-testable; the host test window cannot be made wide without
 * changing the Robolectric sandbox, which the JNI stub's classloader rule forbids.
 */
internal fun useDockedPanel(widthPx: Float, heightPx: Float): Boolean = widthPx > heightPx

/**
 * Share of the screen height the phone card takes when expanded, so the map keeps the
 * other 55 % for the route (spec: `route-planning-session` — anchors; design D8/D10).
 */
internal const val EXPANDED_CARD_FRACTION = 0.45f

/** Share the min card takes: only the analysed step, so the map keeps most of the screen. */
internal const val COMPACT_CARD_FRACTION = 0.18f

/** Cap for the min card, so a tall screen does not hand it more than one line's height. */
private const val COMPACT_CARD_MAX_DP = 160f

/** Test tag of the phone card, so a test can assert where it sits on the screen. */
const val ROUTE_PANEL_CARD_TAG = "routePanelCard"

/**
 * The phone card's height for [anchor] on a screen [screenHeightDp] dp tall. Deterministic
 * on purpose (design D8): the first implementation let the content decide, and the same
 * panel then covered 36 % (edit) or 48 % (route) under a fit that assumed 22 % — 614 px of
 * the route sat behind the card on the device (2026-10-03).
 */
internal fun phoneCardHeightDp(anchor: RouteOverlayAnchor, screenHeightDp: Float): Float =
    when (anchor) {
        RouteOverlayAnchor.EXPANDED -> screenHeightDp * EXPANDED_CARD_FRACTION
        RouteOverlayAnchor.COMPACT ->
            minOf(COMPACT_CARD_MAX_DP, screenHeightDp * COMPACT_CARD_FRACTION)
    }

/**
 * Whether a session that ended by itself must close its surface (spec: `route-planning-session` —
 * Ending the session removes its surface; "Grace expiry closes an open surface", "No surface
 * survives the session"). The session can end without the user asking for it — its grace expiring
 * is the one path that does — so the host has to be told; otherwise the card stays on screen with
 * no session behind it (measured on the device, `TODO.md` §140).
 */
internal fun sessionEndClosesTheSurface(session: RouteSessionState, panelShown: Boolean): Boolean =
    session == RouteSessionState.INACTIVE && panelShown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePanel(
    viewModel: RoutePanelViewModel,
    onOpenFavoritePicker: (ActiveField) -> Unit,
    onStartNavigation: () -> Unit = {},
    onStopNavigation: () -> Unit = {},
    // Restart from the stopped state: navigation resumes on the same route, no recalculation
    // (spec: `route-planning-session` — Grace period after navigation is stopped).
    onRestartNavigation: () -> Unit = {},
    // The session's exit (spec: `route-planning-session` — the session's only exits, and
    // "Ending the session removes its surface"). The card ends the session itself and asks
    // the host to close the surface: `endSession()` alone left the surface flag set, which
    // is how a minimised card could survive its session as an unclosable pill.
    onEndSession: () -> Unit = {},
    onOverlayHeightChanged: (Int) -> Unit = {},
    isNavigating: Boolean = false,
    centerLat: Double,
    centerLon: Double
) {
    val state by viewModel.uiState.collectAsState()
    // The session state is the owner of the stopped state (spec: `route-planning-session` — Grace
    // period after navigation is stopped): while it lasts, the surface offers Restart and End
    // instead of the planning actions.
    val sessionState by viewModel.sessionState.collectAsState()
    // One read of "navigation is active" for the whole panel: the screen passes the
    // navigation view model's value, the session carries its own flag, and the panel
    // must never offer an edit while either says navigation is running (spec:
    // route-planning-session — Reviewing a route during navigation is read-only).
    val navigationActive = isNavigating || state.isNavigating
    // The session owns the anchor (spec: route-planning-session — Session overlay anchors)
    // and the content follows it. Each anchor is a *fixed* card height, not a sheet
    // position: Material3's partially expanded anchor is a fraction of the content, so it
    // cannot express "compact" — the device measured 36 % (edit) and 48 % (route) for what
    // the session called compact, and ~97 % expanded, which hid the map entirely and left
    // the overview fit (nominal 0.22) 614 px short (2026-10-03; design D8).
    val anchor = state.overlayAnchor
    val compact = anchor != RouteOverlayAnchor.EXPANDED

    // The card reports the height it covers; when it leaves composition it covers nothing.
    // Without this reset the map kept the last card height for its overview fit and for the
    // right-side control column's inset after a session ended (spec `route-map-overview` —
    // Ending the session frees the whole map without moving the camera).
    val currentOnOverlayHeightChanged by rememberUpdatedState(onOverlayHeightChanged)
    DisposableEffect(Unit) {
        onDispose { currentOnOverlayHeightChanged(0) }
    }

    // The map center is the search reference fallback when no GPS fix exists
    // (spec: search-result-ranking — distance reference).
    LaunchedEffect(centerLat, centerLon) {
        viewModel.setFallbackSearchCenter(centerLat, centerLon)
    }

    // The session's content is one composable used by both frames: the phone's fixed-height
    // card and the docked side panel of a wide layout (spec: route-planning-session —
    // Overlay docks when width allows). [docked] decides the wide-only content: the
    // selectable step list and the vehicle selector stay out of the phone card, whose map
    // area they would eat (design D10 — the phone has no list surface).
    val body: @Composable (Boolean) -> Unit = { docked ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ---- Title with the anchored-overlay controls ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // The header already carries the destination while a route is on screen: the
                        // card's height belongs to the list, so the title row is the one place that
                        // is always visible (owner request, 2026-10-03).
                        text = state.destLocation?.label?.takeIf { it.isNotEmpty() }
                            ?.let { stringResource(R.string.route_with_destination, it) }
                            ?: stringResource(R.string.route),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("routeHeaderTitle")
                    )
                    // …and the two statistics on their own line, smaller (owner request,
                    // 2026-10-03: the single dense line was hard to read). They used to be the
                    // list's headline block, which cost ~90 dp of the card's height.
                    state.routeEntry?.let { route ->
                        // One length for one route: the sum of the steps below, not the bridge's
                        // separately accumulated figure (`routeLengthMeters`, spec: `osmscout-jni` —
                        // One route length for a calculated route).
                        val routeLength = routeLengthMeters(route)
                        Text(
                            text = stringResource(
                                if (com.naviveylin.core.distanceUsesKilometers(routeLength)) {
                                    R.string.distance_unit_km
                                } else {
                                    R.string.distance_unit_m
                                },
                                com.naviveylin.core.formatDistanceNumber(routeLength)
                            ) + " · " + com.naviveylin.core.formatDurationText(route.duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.testTag("routeHeaderStats")
                        )
                    }
                }
                if (compact) {
                    IconButton(
                        onClick = { viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED) },
                        modifier = Modifier.testTag("routeOverlayExpand")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.route_overlay_expand)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT) },
                        modifier = Modifier.testTag("routeOverlayCollapse")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.route_overlay_collapse)
                        )
                    }
                }
                // The close control ends the session: same meaning in both anchors, with its
                // own content description (owner finding, 2026-10-05 — the glyph was read as
                // the exit while it minimised, and the minimised state then had no exit).
                // Collapsing is the toggle above; the two affordances are distinct.
                IconButton(
                    onClick = onEndSession,
                    modifier = Modifier.testTag("routeEndSessionHeader")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.route_end_session)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reading a calculated route: the two editable field rows collapse into one line,
            // because the card's fixed height belongs to the route list (owner directive,
            // 2026-10-03 — the list has to start on screen; the device run showed its header
            // at the very fold). Tapping the line opens the fields for editing. While a field
            // is being edited, or before a route exists, the fields render as usual.
            val showRouteBar = !docked && state.routeEntry != null &&
                state.activeField == ActiveField.NONE
            if (showRouteBar) {
                Text(
                    text = "${state.startLocation?.label.orEmpty()}  →  " +
                        state.destLocation?.label.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setActiveField(ActiveField.DEST) }
                        .padding(vertical = 6.dp)
                        .testTag("routeBar")
                )
            } else {
            // ---- Fields with the swap button to the right, vertically
            // centered between start and destination ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // ---- Start field ----
                    val startFieldValue = if (state.activeField == ActiveField.START) {
                        state.searchQuery
                    } else {
                        state.startLocation?.label ?: ""
                    }
                    RouteSearchField(
                        value = startFieldValue,
                        placeholder = stringResource(R.string.start_location),
                        isActive = state.activeField == ActiveField.START,
                        requestFocus = state.activeField == ActiveField.START,
                        // Reviewing an active navigation is read-only (spec:
                        // route-planning-session — Reviewing a route during navigation).
                        enabled = !navigationActive,
                        onFocus = { viewModel.setActiveField(ActiveField.START) },
                        onBlur = {
                            if (viewModel.uiState.value.activeField == ActiveField.START) {
                                viewModel.setActiveField(ActiveField.NONE)
                            }
                        },
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onClear = {
                            viewModel.setStartLocation(
                                LocationEntry().apply { label = "" }
                            )
                            viewModel.setActiveField(ActiveField.START)
                        }
                    )

                    // Start search results
                    if (state.activeField == ActiveField.START) {
                        RouteSearchResults(
                            query = state.searchQuery,
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            gpsAvailable = state.gpsAvailable,
                            reference = state.searchReference,
                            onSelectCurrentLocation = { viewModel.selectCurrentLocation() },
                            onSelectFavorite = { onOpenFavoritePicker(ActiveField.START) },
                            onSelectResult = { viewModel.selectSearchResult(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ---- Destination field ----
                    val destFieldValue = if (state.activeField == ActiveField.DEST) {
                        state.searchQuery
                    } else {
                        state.destLocation?.label ?: ""
                    }
                    RouteSearchField(
                        value = destFieldValue,
                        placeholder = stringResource(R.string.destination),
                        isActive = state.activeField == ActiveField.DEST,
                        requestFocus = state.activeField == ActiveField.DEST,
                        enabled = !navigationActive,
                        onFocus = { viewModel.setActiveField(ActiveField.DEST) },
                        onBlur = {
                            if (viewModel.uiState.value.activeField == ActiveField.DEST) {
                                viewModel.setActiveField(ActiveField.NONE)
                            }
                        },
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onClear = {
                            viewModel.setDestLocation(
                                LocationEntry().apply { label = "" }
                            )
                            viewModel.setActiveField(ActiveField.DEST)
                        }
                    )

                    // Dest search results
                    if (state.activeField == ActiveField.DEST) {
                        RouteSearchResults(
                            query = state.searchQuery,
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            gpsAvailable = state.gpsAvailable,
                            reference = state.searchReference,
                            onSelectCurrentLocation = { viewModel.selectCurrentLocation() },
                            onSelectFavorite = { onOpenFavoritePicker(ActiveField.DEST) },
                            onSelectResult = { viewModel.selectSearchResult(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ---- Swap button ----
                FilledTonalIconButton(
                    onClick = { viewModel.swapStartDest() },
                    enabled = state.startLocation != null && state.destLocation != null
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.swap_start_dest)
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The vehicle selector is a pre-calculation control: once a route exists it makes
            // room for the route list (owner directive: MAX shows the list, and the list has to
            // start on screen), so it stays visible while editing and in the docked layout,
            // which has the height for both.
            if (docked || state.routeEntry == null) {
                // ---- Vehicle selector ----
                Text(
                    text = stringResource(R.string.vehicle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleButton(
                        label = stringResource(R.string.vehicle_car),
                        selected = state.vehicle == Vehicle.CAR,
                        onClick = { viewModel.setVehicle(Vehicle.CAR) }
                    )
                    VehicleButton(
                        label = stringResource(R.string.vehicle_bicycle),
                        selected = state.vehicle == Vehicle.BICYCLE,
                        onClick = { viewModel.setVehicle(Vehicle.BICYCLE) }
                    )
                    VehicleButton(
                        label = stringResource(R.string.vehicle_pedestrian),
                        selected = state.vehicle == Vehicle.PEDESTRIAN,
                        onClick = { viewModel.setVehicle(Vehicle.PEDESTRIAN) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        }
    }

    // The phone's step list is the MAX card's content (owner directive 2026-10-03): the rows
    // scroll inside the card, a tap analyses that step and collapses the card to MIN, and the
    // analysed step stays marked, so the map shows its manoeuvre and highlighted segment next.
    // It brings the headline distance and duration with it, which is where the phone shows
    // them (spec: `route-analysis` — Step selection).
    val bodyList: @Composable () -> Unit = {
        if (state.routeEntry != null && state.routeSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            RouteSummary(
                routeEntry = state.routeEntry!!,
                steps = state.routeSteps,
                activeStepIndex = if (navigationActive) state.activeStepIndex else null,
                // The card's header carries the two statistics, so the list does not repeat them
                // (owner choice, 2026-10-03).
                showStats = false,
                // The card scrolls as a whole; a nested vertical scrollable would be measured
                // with an infinite height inside it (found by the MAX tests, 2026-10-03).
                scrollable = false,
                analysedStepIndex = state.analysedStepIndex,
                onStepSelected = { index ->
                    viewModel.analyseStep(index)
                    viewModel.setOverlayAnchor(RouteOverlayAnchor.COMPACT)
                }
            )
        }
    }

    // The session's actions are their own element, pinned to the card's bottom edge instead
    // of living inside the scrolling content: with a fixed card height the device run
    // (2026-10-03) left "Navigation starten" below the card's fold, so the primary control
    // was unreachable without scrolling the card. The docked layout keeps them in flow (its
    // whole column scrolls) and its step list follows them.
    val bodyActions: @Composable (Boolean) -> Unit = { docked ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // ---- Action buttons ----
            when (state.routeState) {
                is RouteState.Idle -> {
                    // Read-only review during navigation: no calculate, no clear
                    // (spec: route-planning-session).
                    if (!navigationActive) {
                        // The two actions share one row: the card is a fixed-height share of
                        // the screen, and stacked full-width buttons pushed the statistics
                        // (and the primary action itself) out of the card on the device
                        // (2026-10-03).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.calculateRoute() },
                                enabled = state.startLocation != null && state.destLocation != null &&
                                        state.startLocation!!.label.isNotEmpty() &&
                                        state.destLocation!!.label.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.calculate))
                            }

                            if (state.routeEntry != null) {
                                OutlinedButton(
                                    onClick = { viewModel.clearRoute() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.clear_route))
                                }
                            }
                        }
                    }
                }

                is RouteState.Calculating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = (state.routeState as RouteState.Calculating).percent?.let {
                                stringResource(R.string.calculating_route_percent, it)
                            } ?: stringResource(R.string.calculating_route),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelRoute() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                is RouteState.Done -> {
                    // With a route on screen the endpoints are what a max view can change:
                    // "Berechnen" only recalculated the same route (owner finding, 2026-10-03),
                    // so the secondary action opens the start/target fields instead. The
                    // primary action starts navigation; both share one row.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!navigationActive) {
                            // Reviewing: the endpoints are what can change, so the secondary
                            // action opens the fields. While a field is being edited the same
                            // slot is the calculation again (otherwise a changed destination
                            // could not be recalculated at all).
                            if (state.activeField == ActiveField.NONE) {
                                OutlinedButton(
                                    onClick = { viewModel.setActiveField(ActiveField.DEST) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.route_edit_endpoints))
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.calculateRoute() },
                                    enabled = state.startLocation != null &&
                                            state.destLocation != null &&
                                            state.startLocation!!.label.isNotEmpty() &&
                                            state.destLocation!!.label.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.calculate))
                                }
                            }
                        }
                        if (!navigationActive) {
                            Button(
                                onClick = onStartNavigation,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.start_navigation))
                            }
                        }
                    }
                    // The way *out* of the session without starting navigation (owner finding,
                    // 2026-10-03: "I do not see how I can leave the stateful route analysis
                    // without starting the navigation"). The header's close only minimises
                    // (the route stays, the pill brings it back) and "Start/Ziel ändern" keeps
                    // the session too, so the session's own exit needs its own labelled control
                    // (spec: route-planning-session — the exits are Start Navigation and
                    // Cancel/End). It is a text button on its own line: three buttons in one row
                    // would clip their labels at phone width.
                    if (!navigationActive) {
                        TextButton(
                            onClick = onEndSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("routeEndSession")
                        ) {
                            Text(stringResource(R.string.route_end_session))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // The selectable step list is the wide layout's selector; the phone's
                    // selector is the pinned navigator below the card, so the phone never
                    // renders a list that would have to be scrolled inside the card
                    // (spec: `route-analysis` — Step selection; design D10).
                    if (docked && state.routeEntry != null) {
                        RouteSummary(
                            routeEntry = state.routeEntry!!,
                            steps = state.routeSteps,
                            activeStepIndex = if (navigationActive) state.activeStepIndex else null,
                            // The docked panel's column scrolls as a whole (same reason as the
                            // phone card: a nested vertical scrollable is measured infinite).
                            scrollable = false,
                            // Tapping the analysed step again clears the analysis; any other
                            // step becomes the analysed one (spec: `route-analysis`).
                            analysedStepIndex = state.analysedStepIndex,
                            onStepSelected = { index -> viewModel.toggleAnalysedStep(index) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // The clear action keeps its own row only in the docked layout: on the
                    // phone the two-button row above is what the fixed card's height can
                    // afford without pushing the statistics out, and the session's cancel
                    // exit (system back) already clears the route (spec:
                    // route-planning-session — the session's exits).
                    if (docked && !navigationActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearRoute() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.clear_route))
                        }
                    }
                }

                is RouteState.Error -> {
                    Text(
                        text = state.error ?: stringResource(R.string.route_calculation_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.calculateRoute() },
                        enabled = state.startLocation != null && state.destLocation != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            // While navigation is active the review always offers its Stop action, whatever the
            // panel's own route state is: the route belongs to navigation, so a session opened
            // during navigation can end it even before it has adopted the route, and the review
            // is never a panel with no way out (spec: `route-planning-session` — Reviewing a route
            // during navigation is read-only: the review offers the Stop Navigation action).
            if (navigationActive) {
                ReviewStopAction(onStopNavigation = onStopNavigation)
            }

            // ---- Turn-by-turn instructions live in the summary above (and in the
            // docked panel's step list); the phone has no second surface (D10) ----
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Landscape/wide (spec: landscape-layout — detect orientation with
        // BoxWithConstraints, never the deprecated orientation API): the panel docks to the
        // side so the map keeps the remaining area for analysis.
        if (useDockedPanel(maxWidth.value, maxHeight.value)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(360.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        body(true)
                        if (sessionState == RouteSessionState.STOPPED) {
                            StoppedActions(
                                onRestartNavigation = onRestartNavigation,
                                onEndSession = onEndSession,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        } else {
                            bodyActions(true)
                        }
                    }
                }
            }
        } else {
            // The phone card has two modes (owner directive, 2026-10-03). MAX carries the
            // route's step list and the session actions inside a capped height, so the map
            // keeps its share; MIN is the small overlay with only the analysed step — its
            // position, its name and the two step controls. Selecting a step in MAX collapses
            // to MIN, so what the user looks at next is the map with that manoeuvre and its
            // highlighted segment; tapping the step name in MIN brings MAX back. With no route
            // (editing) there is nothing to minimise to, so the card stays in MAX.
            val maxMode = anchor == RouteOverlayAnchor.EXPANDED ||
                state.routeEntry == null ||
                state.activeField != ActiveField.NONE
            val maxCapDp = phoneCardHeightDp(RouteOverlayAnchor.EXPANDED, maxHeight.value)
            val cardCapDp = if (maxMode) {
                maxCapDp
            } else {
                phoneCardHeightDp(RouteOverlayAnchor.COMPACT, maxHeight.value)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Both states hug their content and take the cap only as an upper bound: a
                    // fixed max height left empty space below the list and a fixed min strip did
                    // the same above the navigation bar (owner findings, 2026-10-03). The
                    // measured height still reaches the map for the overview fit.
                    .heightIn(max = cardCapDp.dp)
                    .testTag(ROUTE_PANEL_CARD_TAG)
                    .onSizeChanged { onOverlayHeightChanged(it.height) },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        // MIN wraps (its content decides the height, the cap only bounds it);
                        // MAX wraps too, bounded by its scroll region plus the action band.
                        .fillMaxWidth()
                        // The card reaches the screen's bottom edge; only its content is
                        // lifted above the navigation bar. (The deleted summary dialog put
                        // this padding on its surface instead and left a scrim strip above
                        // the navigation bar — device finding 2.)
                        .navigationBarsPadding()
                ) {
                    if (maxMode) {
                        Column(
                            modifier = Modifier
                                // The pinned action band keeps the height its own content
                                // needs — it is measured first and the scrolling content takes
                                // what is left of the cap. A fixed reservation for the band
                                // (`cardCap - 120 dp`) handed it less than that once a large
                                // font scale grew the action labels, so the last action of the
                                // band was squeezed out of the card (device, 1080x2400, font
                                // scale 2.0: the labelled End action left the card and reached
                                // no UI dump at all — TODO.md §138).
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            body(false)
                            bodyList()
                        }
                        // The actions are pinned: starting navigation never depends on a
                        // scroll position (device finding 2026-10-03).
                        if (sessionState == RouteSessionState.STOPPED) {
                            StoppedActions(
                                onRestartNavigation = onRestartNavigation,
                                onEndSession = onEndSession,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 24.dp)
                            )
                        } else {
                            bodyActions(false)
                        }
                    } else {
                        if (sessionState == RouteSessionState.STOPPED) {
                            // MIN carries no header: the stopped state's two exits are the strip's
                            // content for as long as the grace runs.
                            StoppedActions(
                                onRestartNavigation = onRestartNavigation,
                                onEndSession = onEndSession,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            StepNavigator(
                                steps = state.routeSteps,
                                analysedIndex = state.analysedStepIndex,
                                onPrevious = { viewModel.analysePreviousStep() },
                                onNext = { viewModel.analyseNextStep() },
                                onStepNameClick = {
                                    viewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
                                },
                                onEndSession = onEndSession
                            )
                            // The review's Stop is offered in MIN too: the session card is the phone's
                            // only session surface, and a session opened while navigation runs lands
                            // COMPACT (openSession) — a review that could not end the navigation it
                            // reviews would have no way out (spec: `route-planning-session` —
                            // Reviewing a route during navigation is read-only: Stop Navigation SHALL
                            // be offered; measured 2026-10-06, task 5.1 run (b): the strip showed the
                            // step navigator and End only).
                            if (navigationActive) {
                                ReviewStopAction(
                                    onStopNavigation = onStopNavigation,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The phone's step selector (spec: `route-analysis` — Step navigator; design D10): the
 * analysed step's instruction, distance and duration, where it sits in the route, and the
 * two controls that move the selection. Pinned to the card's bottom edge, so the map above
 * keeps showing the route while the selection moves the camera (never a full-height list).
 */
@Composable
private fun StepNavigator(
    steps: List<RouteStepDisplay>,
    analysedIndex: Int?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStepNameClick: () -> Unit,
    onEndSession: () -> Unit
) {
    val count = steps.size
    if (count == 0) return
    // The route always has a current step (the first one after a calculation), so the
    // indicator always names one (owner finding, 2026-10-03: "– / n" before the first paging
    // was wrong).
    val index = (analysedIndex ?: 0).coerceIn(0, count - 1)
    val step = steps.getOrNull(index)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = index > 0,
                modifier = Modifier.testTag("routeStepPrevious")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.route_step_previous)
                )
            }
            // The step name is the way back to the list (owner directive: in MIN a tap on the
            // step name shows the list again).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onStepNameClick() }
                    .testTag("analysedStepName")
            ) {
                Text(
                    text = step?.instruction ?: stringResource(R.string.route_step_none),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("analysedStepText")
                )
                val detail = listOfNotNull(
                    step?.takeIf { it.distanceMeters > 0.0 }?.let { stepWithValues ->
                        stringResource(
                            if (distanceUsesKilometers(stepWithValues.distanceMeters))
                                R.string.distance_unit_km
                            else R.string.distance_unit_m,
                            formatDistanceNumber(stepWithValues.distanceMeters)
                        )
                    },
                    step?.let { formatStepDurationText(it.durationSeconds) }?.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    // The indicator agrees with the current step, which always exists.
                    text = stringResource(R.string.route_step_progress, index + 1, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("analysedStepProgress")
                )
            }
            IconButton(
                onClick = onNext,
                enabled = index < count - 1,
                modifier = Modifier.testTag("routeStepNext")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.route_step_next)
                )
            }
            // MIN is the analysis view and carries no header, so its exit lives here: the
            // explicit End action the session owes the user (spec: route-planning-session —
            // "the user starts navigation or ends the session", and the exits are Cancel, the
            // system back gesture or an explicit End action). Without it the only way out of a
            // minimised analysis was to open the list again (owner finding, 2026-10-03).
            IconButton(
                onClick = onEndSession,
                modifier = Modifier.testTag("routeEndSessionCompact")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.route_end_session)
                )
            }
        }
    }
}

/**
 * The stopped state's actions (spec: `route-planning-session` — Grace period after navigation is
 * stopped): Restart resumes navigation on the route the session still holds, End ends the session
 * immediately and clears the route. They stand in for the planning actions for as long as the
 * grace runs, in both phone anchors and in the docked panel.
 */
/**
 * The read-only review's Stop action — the session's way out of the navigation it reviews
 * (spec: `route-planning-session` — Reviewing a route during navigation is read-only: Stop
 * Navigation SHALL be offered). One composable for both phone frames (the MAX action band and the
 * compact strip), so the two anchors cannot drift apart.
 */
@Composable
private fun ReviewStopAction(
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onStopNavigation,
        modifier = modifier
            .fillMaxWidth()
            .testTag("stopNavigationReview")
    ) {
        Text(stringResource(R.string.stop_navigation))
    }
}

@Composable
private fun StoppedActions(
    onRestartNavigation: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRestartNavigation,
            modifier = Modifier
                .weight(1f)
                .testTag("restartNavigation")
        ) {
            Text(stringResource(R.string.restart_navigation))
        }
        OutlinedButton(
            onClick = onEndSession,
            modifier = Modifier
                .weight(1f)
                .testTag("routeEndSessionStopped")
        ) {
            Text(stringResource(R.string.route_end_session))
        }
    }
}

@Composable
private fun RouteSearchField(
    value: String,
    placeholder: String,
    isActive: Boolean,
    onFocus: () -> Unit,
    onBlur: () -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean = true,
    requestFocus: Boolean = false
) {
    val isReadOnly = value.isNotEmpty() && !isActive
    // A field that is opened programmatically (tapping the read-only route line, which is how
    // the edit path is reached while a route is on screen) must take focus itself: an
    // unfocused field reports a focus loss straight away and closes the edit state again.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (isActive) {
                onQueryChanged(newValue)
            } else if (!isReadOnly) {
                onFocus()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                // Tap on an empty field must open the results popup (with the
                // convenience entries) immediately — not only after the first
                // keystroke. Losing focus closes the popup again.
                if (focusState.isFocused) {
                    onFocus()
                } else {
                    onBlur()
                }
            },
        placeholder = { Text(placeholder) },
        readOnly = isReadOnly,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        },
        singleLine = true,
        enabled = enabled
    )
}

@Composable
private fun RouteSearchResults(
    query: String,
    results: List<LocationEntry>,
    isSearching: Boolean,
    gpsAvailable: Boolean,
    reference: SearchReference?,
    onSelectCurrentLocation: () -> Unit,
    onSelectFavorite: () -> Unit,
    onSelectResult: (LocationEntry) -> Unit
) {
    // The perfect-match fact is derived from the same query the ranking used, so
    // the marking and the order agree (spec: search-result-ranking).
    val criteria = remember(query) { SearchQueryParser.criteriaOf(query) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
    ) {
        // Convenience entries (Current Location, Select Favorite) are shown
        // only while the query is empty. Typing a query lists search results
        // only; clearing the field restores both entries immediately.
        if (query.isEmpty()) {
            // First entry: Current Location (if GPS available)
            if (gpsAvailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectCurrentLocation)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.current_location),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }

            // Second entry: Select Favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectFavorite)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = stringResource(R.string.select_favorite),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()
        }

        // Search results
        when {
            isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                        .size(24.dp)
                )
            }

            query.length >= 2 && results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            results.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(results) { entry ->
                        // Distance from the ranking reference, right-aligned in a
                        // smaller font (spec: location-search — Result distance
                        // display); the same point the order was computed from.
                        val meters = SearchResultRanker.distanceMeters(entry, reference)
                        val distanceText = if (meters != null) {
                            stringResource(R.string.distance_unit_km, formatDistanceKm(meters))
                        } else {
                            null
                        }
                        val isPerfect = SearchResultRanker.isPerfectMatch(entry, criteria)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onSelectResult(entry) })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPerfect) {
                                Image(
                                    bitmap = ResultMarkings.bitmapFor(ResultMarking.PERFECT).asImageBitmap(),
                                    contentDescription = stringResource(R.string.search_result_exact_match),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (entry.adminRegionHierarchy != null && entry.adminRegionHierarchy.isNotEmpty()) {
                                    Text(
                                        text = entry.adminRegionHierarchy,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (distanceText != null) {
                                Text(
                                    text = distanceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color
        ),
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
