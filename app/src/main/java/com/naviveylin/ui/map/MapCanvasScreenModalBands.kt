package com.naviveylin.ui.map

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.R
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.ui.about.AboutDialog
import com.naviveylin.ui.addressbook.AddressBookSearchContent
import com.naviveylin.ui.favorites.FavoritesSheet
import com.naviveylin.ui.navigation.NavigationDetailsOverlay
import com.naviveylin.ui.route.ActiveField
import com.naviveylin.ui.route.FavoritePickerDialog
import com.naviveylin.ui.route.RoutePanel
import com.naviveylin.ui.route.RoutePanelViewModel


import android.content.Context
import com.naviveylin.ui.route.RoutePanelUiState
import com.naviveylin.core.NavigationState
import com.naviveylin.data.AddressBookRationaleStore
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.layout.BoxScope



/**
 * The phone map screen's [MapLayer.MENU], [MapLayer.MODAL] and [MapLayer.SNACKBAR] band contents: the map
 * menu, every surface that opens over the map (search, location details and options, favourites,
 * route-planning session, navigation details, the permission and rationale dialogs, the favourite
 * picker) and the snackbar host. Moved out of `MapCanvasScreen.kt` verbatim by
 * `2026-10-25-fix-map-canvas-screen-file-size`; the band wrappers stayed in the screen, so the
 * back-handler registration order the layer stack depends on is still the screen's composition order.
 */

@Composable
internal fun MapCanvasMenuBandContent(
    menuExpandedState: MutableState<Boolean>,
    onNavigateToMapManager: () -> Unit,
    viewModel: MapCanvasViewModel,
    showAboutDialogState: MutableState<Boolean>,
    isLandscape: Boolean
) {
    // Animated map menu overlay (scrim + panel), anchored below the toaster button
    MapMenu(
        expanded = menuExpandedState.value,
        onDismiss = { menuExpandedState.value = false },
        onDownloadMaps = { onNavigateToMapManager() },
        onOpenFavorites = { viewModel.toggleFavoritesSheet() },
        onOpenSearch = { viewModel.openSearch() },
        onOpenAbout = { showAboutDialogState.value = true },
        toasterTopPadding = if (isLandscape) 8.dp else 4.dp
    )
}

@Composable
internal fun MapCanvasModalBandContent(
    state: MapCanvasUiState,
    viewModel: MapCanvasViewModel,
    routePanelViewModel: RoutePanelViewModel,
    routeState: RoutePanelUiState,
    navState: NavigationState,
    navigationViewModel: NavigationViewModel,
    context: Context,
    isLandscape: Boolean,
    distanceProgressPercent: Int?,
    timeProgressPercent: Int?,
    showAboutDialogState: MutableState<Boolean>,
    favoritePickerFieldState: MutableState<ActiveField?>,
    showFavoritePickerState: MutableState<Boolean>,
    onBottomInset: (Int) -> Unit,
    showPermissionRationaleState: MutableState<Boolean>,
    onPreciseLocationAction: () -> Unit,
    contactsPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    showAddressBookRationaleState: MutableState<Boolean>,
    addressBookRationaleStore: AddressBookRationaleStore,
    showNavDetailsState: MutableState<Boolean>,
    stopNavigation: () -> Unit,
    onNavigateToMapManager: () -> Unit
) {
    // Unified search dialog (spec: search-dialog) — one surface for
    // Places / POIs / Contacts search, opened by the search button, the
    // menu Search entry, the `/` key, and shared-location queries.
    if (state.searchOpen) {
        val addressBookViewModel: com.naviveylin.ui.addressbook.AddressBookViewModel =
            hiltViewModel()
        val addressBookState by addressBookViewModel.uiState.collectAsState()
        val history by viewModel.searchHistory.collectAsState()
        val favoriteGroups by viewModel.favoriteGroups.collectAsState()
        // Load contacts when the Contacts mode is entered (spec:
        // address-book-search — searchable list of persons with addresses).
        LaunchedEffect(state.searchMode) {
            if (state.searchMode == SearchMode.CONTACTS) {
                addressBookViewModel.start()
            }
        }
        SearchDialog(
            searchMode = state.searchMode,
            onModeSelected = { viewModel.setSearchMode(it) },
            onDismiss = { viewModel.closeSearch() },
            query = state.searchQuery,
            results = state.searchResults,
            isSearching = state.isSearching,
            gpsAvailable = state.gpsFixQuality != GpsFixQuality.NONE,
            adminRegionName = state.searchAdminRegionName,
            distanceReference = state.searchReference,
            historyEntries = history,
            favoriteGroups = favoriteGroups,
            onQueryChanged = { viewModel.onSearchQueryChanged(it) },
            onResultSelected = { entry ->
                viewModel.onSearchResultSelected(entry)
                viewModel.closeSearch()
            },
            onSelectCurrentLocation = {
                viewModel.selectCurrentLocation()
                viewModel.closeSearch()
            },
            onSelectFavorite = { fav ->
                viewModel.onFavoriteSelected(fav)
                viewModel.closeSearch()
            },
            onHistoryEntrySelected = { viewModel.onHistoryEntrySelected(it) },
            poiCategory = state.poiCategory,
            poiRadiusMeters = state.poiRadiusMeters,
            poiResults = state.poiResults,
            isPoiSearching = state.isPoiSearching,
            poiError = state.poiSearchError,
            client = viewModel.osmscoutClient,
            poiCenterLat = state.poiSearchCenterLat,
            poiCenterLon = state.poiSearchCenterLon,
            currentPosition = if (state.gpsMarkerLat.isNaN()) null else state.gpsMarkerLat to state.gpsMarkerLon,
            selectedPoi = if (state.poiSelectedLat.isNaN()) null else state.poiSelectedLat to state.poiSelectedLon,
            onPoiCategorySelected = { viewModel.onPoiCategorySelected(it) },
            onPoiRadiusChanged = { viewModel.onPoiRadiusChanged(it) },
            onPoiSearch = { viewModel.performPoiSearch() },
            onPoiEntryClick = { viewModel.onPoiEntryClick(it) },
            addressBookAvailable = state.addressBookAvailable,
            contactsQuery = addressBookState.query,
            onContactsQueryChanged = { addressBookViewModel.onQueryChanged(it) },
            contactsContent = {
                AddressBookSearchContent(
                    onResultSelected = { entry ->
                        viewModel.onAddressBookResultSelected(entry)
                    }
                )
            }
        )
    }

    // Candidate picker (long-press with multiple objects at the point).
    // Shown instead of the details sheet; selecting a row opens details.
    if (state.showCandidatePicker && state.candidateDescriptions.isNotEmpty()) {
        CandidatePickerSheet(
            candidates = state.candidateDescriptions,
            onCandidateSelected = { viewModel.onCandidateSelected(it) },
            onDismiss = { viewModel.dismissCandidatePicker() }
        )
    }

    // Location details dialog (full screen, spec: enhanced-details-sheet).
    // Its own BackHandler (registered later in composition than the
    // navigation-reject handler above) dismisses it on system back.
    if (state.showDetailsSheet && state.selectedLocation != null) {
        LocationDetailsDialog(
            entry = state.selectedLocation!!,
            client = viewModel.osmscoutClient,
            // Mini map starts 4 levels below the main map so the object's
            // surroundings are visible (main map is typically zoomed in).
            initialMag = (state.viewport.magnification - 4).coerceAtLeast(MapCanvasViewModel.MIN_MAG)
                .coerceIn(MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG),
            objectDescription = state.objectDescription,
            isFavorite = viewModel.isSelectedLocationFavorite(),
            groupNames = viewModel.getFavoriteGroupNames(),
            currentPosition = if (state.gpsMarkerLat.isNaN()) null else state.gpsMarkerLat to state.gpsMarkerLon,
            onAddToFavorites = { groupName, favName, isNewGroup ->
                viewModel.addSelectedToFavorites(groupName, favName, isNewGroup)
            },
            onRemoveFromFavorites = { viewModel.removeSelectedFromFavorites() },
            onRouteToLocation = { viewModel.openRoutePanelWithStart(state.selectedLocation) },
            onShowOnMap = { viewModel.showOnMap() },
            onDismiss = { viewModel.dismissDetailsSheet() }
        )
    }

    // About dialog
    if (showAboutDialogState.value) {
        AboutDialog(onDismiss = { showAboutDialogState.value = false })
    }

    // Favorites sheet (full-screen)
    if (state.showFavoritesSheet) {
        FavoritesSheet(
            mapCenterLat = state.viewport.centerLat,
            mapCenterLon = state.viewport.centerLon,
            onDismiss = { viewModel.toggleFavoritesSheet() },
            onFavoriteClick = { fav ->
                viewModel.onFavoriteSelected(fav)
            },
            onChipRouteTo = { fav ->
                val entry = LocationEntry().apply {
                    label = fav.name
                    lat = fav.lat
                    lon = fav.lon
                    matchQuality = "favorite"
                }
                viewModel.openRoutePanelWithStart(entry)
            }
        )
    }

    // The session card lives in the map's own window since the 2026-10-03 revision
    // (design D10), so the screen owns its dismissal: system back on a composed card is
    // the session's cancel exit (`dismissRoutePanel` → `endSession`; spec:
    // `route-planning-session` — the session's exits, `map-canvas-screen` — back dismisses
    // the topmost overlay). The deleted Material3 sheet brought its own back handler;
    // without this one, back would fall through to the activity. The card is the whole
    // phone surface now (two anchors), so back is captured whenever it is shown.
    BackHandler(enabled = state.showRoutePanel) { viewModel.dismissRoutePanel() }

    // Route panel — the session's single surface (design D10)
    if (state.showRoutePanel) {
        RoutePanel(
            viewModel = routePanelViewModel,
            onOpenFavoritePicker = { field ->
                favoritePickerFieldState.value = field
                showFavoritePickerState.value = true
            },
            // The card reports the height it covers — the fit uses that number and
            // re-runs when it changes (spec: `route-map-overview`). It is also the band's
            // inset while it is shown: the status card is not composed then, so its last
            // height must not keep the right-side controls inset (screen-local state).
            onOverlayHeightChanged = { height ->
                viewModel.setOverlayCoveredPx(height)
                onBottomInset(height)
            },
            // The card's exit: the session ends and the surface closes with it (spec:
            // `route-planning-session` — Ending the session removes its surface).
            onEndSession = { viewModel.dismissRoutePanel() },
            // A failure whose cause the map manager can fix offers that action (spec:
            // `routing-failure-reporting` — A reason whose cause the user can act on offers
            // the action). The panel closes with the session and the manager opens.
            onOpenMapManager = {
                viewModel.dismissRoutePanel()
                onNavigateToMapManager()
            },
            onStartNavigation = {
                val entry = routeState.routeEntry
                if (entry != null) {
                    navigationViewModel.start(entry, routeState.vehicle)
                    routePanelViewModel.setNavigating(true)
                    // Close the routing window when navigation starts.
                    viewModel.dismissRoutePanel()
                }
            },
            onStopNavigation = stopNavigation,
            // Restart from the stopped state: navigation resumes on the route the session still
            // holds, and the session hands it back without recalculating (spec:
            // `route-planning-session` — Restart resumes navigation).
            onRestartNavigation = {
                val entry = routeState.routeEntry
                if (entry != null) {
                    navigationViewModel.start(entry, routeState.vehicle)
                    routePanelViewModel.onNavigationRestarted()
                }
            },
            isNavigating = navState.isNavigating,
            centerLat = state.viewport.centerLat,
            centerLon = state.viewport.centerLon
        )
    }

    // Favorite picker dialog (for route field selection)
    if (showFavoritePickerState.value) {
        FavoritePickerDialog(
            favoriteRepository = routePanelViewModel.favoriteRepository,
            onFavoriteSelected = { entry ->
                when (favoritePickerFieldState.value) {
                    ActiveField.START -> {
                        viewModel.setRouteStart(entry)
                        routePanelViewModel.setActiveField(ActiveField.NONE)
                    }
                    ActiveField.DEST -> {
                        viewModel.setRouteDest(entry)
                        routePanelViewModel.setActiveField(ActiveField.NONE)
                    }
                    ActiveField.NONE -> {}
                    null -> {}
                }
                favoritePickerFieldState.value = null
                showFavoritePickerState.value = false
            },
            onDismiss = {
                favoritePickerFieldState.value = null
                showFavoritePickerState.value = false
            }
        )
    }

    // Permission rationale dialog
    if (showPermissionRationaleState.value) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleState.value = false },
            title = { Text(stringResource(R.string.location_permission_needed)) },
            text = {
                Text(
                    stringResource(R.string.location_permission_rationale)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleState.value = false
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    } catch (_: Exception) {}
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleState.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    // Navigation gate (spec: `location-permissions` — Starting navigation
    // requires precise location). The route request was refused; explain it and
    // offer the upgrade instead of letting a ~2 km start point into the engine.
    if (routeState.preciseLocationRequired) {
        PreciseLocationRequiredDialog(
            onDismiss = { routePanelViewModel.dismissPreciseLocationRequirement() },
            onUpgrade = {
                routePanelViewModel.dismissPreciseLocationRequirement()
                onPreciseLocationAction()
            }
        )
    }
    // Address-book (contacts) rationale dialog — shown once before the
    // first READ_CONTACTS request (spec: address-book-permission).
    if (showAddressBookRationaleState.value) {
        com.naviveylin.ui.addressbook.AddressBookRationaleDialog(
            onDismiss = {
                showAddressBookRationaleState.value = false
                addressBookRationaleStore.markShown()
            },
            onContinue = {
                showAddressBookRationaleState.value = false
                addressBookRationaleStore.markShown()
                try {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                } catch (_: Exception) {}
            },
            onNotNow = {
                showAddressBookRationaleState.value = false
                addressBookRationaleStore.markShown()
            }
        )
    }



    // Expanded routing status details — full-screen route description
    if (showNavDetailsState.value && navState.isNavigating) {
        NavigationDetailsOverlay(
            steps = navState.steps,
            currentStepIndex = navState.currentStepIndex,
            currentRoadInfo = navState.currentRoadInfo,
            remainingDistance = navState.remainingDistance,
            etaMillis = navState.etaMillis,
            distanceProgressPercent = distanceProgressPercent,
            timeProgressPercent = timeProgressPercent,
            onStopNavigation = stopNavigation,
            onDismiss = { showNavDetailsState.value = false }
        )
    }
}

@Composable
internal fun BoxScope.MapCanvasSnackbarBandContent(hostState: SnackbarHostState) {
    // Snackbar at bottom
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
