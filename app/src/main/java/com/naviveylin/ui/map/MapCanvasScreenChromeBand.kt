package com.naviveylin.ui.map

import androidx.compose.foundation.layout.BoxScope
import com.naviveylin.core.NavigationState
import com.naviveylin.core.FreeDrivingStatus
import androidx.compose.runtime.MutableState
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.naviveylin.ui.attribution.OsmAttributionOverlay
import com.naviveylin.ui.navigation.NavigationStateOverlay
import com.naviveylin.ui.navigation.NextTurnOverlay


/**
 * The phone map screen's [MapLayer.CHROME] band content: the orientation-aware overlay layout with the
 * action column, compass, speed widget, zoom controls, re-center and drive-mode buttons, street-name
 * pill, turn card, routing status, OSM attribution and car-session indicator, plus the single
 * [LocalOverlayWidthProbe] provider that spans the whole band. Moved out of `MapCanvasScreen.kt`
 * verbatim by `2026-10-25-fix-map-canvas-screen-file-size`; the band wrapper stayed in the screen.
 */

@Composable
internal fun BoxScope.MapCanvasChromeBandContent(
    state: MapCanvasUiState,
    viewModel: MapCanvasViewModel,
    navState: NavigationState,
    freeDrivingStatus: FreeDrivingStatus?,
    carSessionActive: Boolean,
    isLandscape: Boolean,
    speedInput: SpeedWidgetInput?,
    distanceProgressPercent: Int?,
    timeProgressPercent: Int?,
    reCenterAction: () -> Unit,
    modeToggleAction: () -> Unit,
    toggleOrientationAction: () -> Unit,
    animateDiscreteZoomToCenter: () -> Unit,
    attributionInteractionTickState: MutableState<Int>,
    stopNavigation: () -> Unit,
    onMenuExpanded: () -> Unit,
    onShowNavDetails: () -> Unit,
    onRightInset: (Int) -> Unit,
    onTopInset: (Int) -> Unit,
    onBottomInset: (Int) -> Unit,
    onPillInset: (Int) -> Unit
) {
    // One overlay-width provider for the whole chrome band (spec: smooth-follow —
    // visible-area scenarios): the band composes the right-side widget column in
    // its browse branches and in its navigation branch, one at a time, and the
    // navigation branch must publish the column's measured width too — a band that
    // composes while navigation is already active (the car-session resume) has no
    // remembered inset to fall back on (TODO.md §151). The browse block below is
    // the first branch of the band, not its owner.
    CompositionLocalProvider(
        LocalOverlayWidthProbe provides { width -> onRightInset(width) }
    ) {
    // Orientation-aware overlay layout
    // Orientation-aware overlay layout.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape: action buttons top-left, state controls on right.
            // Hidden during navigation so the turn instruction can start at
            // the left edge without overlapping the right-side widgets.
            if (!navState.isNavigating) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    MapActionColumn(
                        isLandscape = true,
                        onToggleMenu = { onMenuExpanded() },
                        onOpenSearch = { viewModel.openSearch() },
                        onToggleFavorites = { viewModel.toggleFavoritesSheet() }
                    )
                }
            }

            // Right side: single bottom-anchored widget column (compass
            // directly above the speed widget, then location options, then
            // zoom at the bottom below all other controls) — same placement
            // as the routing view (spec: phone-align-controls-in-all-modes).
            // Hidden during navigation — the routing layout owns the right side.
            if (!navState.isNavigating) {
                MapRightWidgetColumn(
                    isLandscape = true,
                    bottomInset = with(LocalDensity.current) {
                        state.overlayCoveredPx.toDp()
                    },
                    mapAngleRadians = state.viewport.angle,
                    gpsFixQuality = state.gpsFixQuality,
                    isDarkPresentation = state.isDarkPresentation,
                    onCenterClick = reCenterAction,
                    onToggleOrientation = toggleOrientationAction,
                    speedInput = speedInput,
                    overspeedWarningDeltaKmh = state.overspeedWarningDeltaKmh,
                    driveToggle = {
                        DriveModeButton(
                            mode = viewModel.mode,
                            onToggle = modeToggleAction
                        )
                    },
                    canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                    canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                    currentMag = state.viewport.magnification,
                    onZoomIn = {
                        android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                        attributionInteractionTickState.value++
                        viewModel.disengageFollowMode()
                        viewModel.zoomIn()
                        viewModel.renderMap()
                        animateDiscreteZoomToCenter()
                    },
                    onZoomOut = {
                        android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                        attributionInteractionTickState.value++
                        viewModel.disengageFollowMode()
                        viewModel.zoomOut()
                        viewModel.renderMap()
                        animateDiscreteZoomToCenter()
                    },
                    locationOptions = {
                        LocationOptionsOverlay(
                            mode = viewModel.mode,
                            freeFormNorthUp = state.freeFormNorthUp,
                            onSetFreeFormOrientation = { northUp ->
                                viewModel.onSetFreeFormOrientation(northUp)
                            },
                            navNorthUp = state.navNorthUp,
                            onSetNavOrientation = { northUp ->
                                viewModel.onSetNavOrientation(northUp)
                            },
                            autoZoomEnabled = state.autoZoomEnabled,
                            onToggleAutoZoom = { enabled ->
                                viewModel.onToggleAutoZoom(enabled)
                            },
                            keepScreenOn = state.keepScreenOn,
                            onToggleKeepScreenOn = { enabled ->
                                viewModel.onToggleKeepScreenOn(enabled)
                            },
                            darkModePreference = state.darkModePreference,
                            onSetDarkModePreference = { pref ->
                                viewModel.onSetDarkModePreference(pref)
                            },
                            ambientLightSensitivity = state.ambientLightSensitivity,
                            onSetAmbientLightSensitivity = { level ->
                                viewModel.onSetAmbientLightSensitivity(level)
                            },
                            laneHintsEnabled = state.laneHintsEnabled,
                            onToggleLaneHints = { enabled ->
                                viewModel.onToggleLaneHints(enabled)
                            },
                            overspeedWarningDeltaKmh = state.overspeedWarningDeltaKmh,
                            onSetOverspeedWarningDelta = { delta ->
                                viewModel.onSetOverspeedWarningDelta(delta)
                            },
                            carMaxSpeedKmh = state.carMaxSpeedKmh,
                            onSetCarMaxSpeed = { kmh ->
                                viewModel.onSetCarMaxSpeed(kmh)
                            },
                            bicycleMaxSpeedKmh = state.bicycleMaxSpeedKmh,
                            onSetBicycleMaxSpeed = { kmh ->
                                viewModel.onSetBicycleMaxSpeed(kmh)
                            },
                            footMaxSpeedKmh = state.footMaxSpeedKmh,
                            onSetFootMaxSpeed = { kmh ->
                                viewModel.onSetFootMaxSpeed(kmh)
                            },
                            routingAnchor = state.routingAnchor,
                            onSetRoutingAnchor = { anchor ->
                                viewModel.setRoutingAnchor(anchor)
                            },
                            freeDrivingAnchor = state.freeDrivingAnchor,
                            onSetFreeDrivingAnchor = { anchor ->
                                viewModel.setFreeDrivingAnchor(anchor)
                            },
                            renderMode = state.renderMode,
                            onSetRenderMode = { mode ->
                                viewModel.onSetRenderMode(mode)
                            },
                            availableStyles = state.availableStyleSheets,
                            styleSheet = state.styleSheet,
                            onSetStyleSheet = { style ->
                                viewModel.onStyleSheetSelected(style)
                            },
                            preferSymbolPoiIcons = state.preferSymbolPoiIcons,
                            onSetPreferSymbolPoiIcons = { preferSymbols ->
                                viewModel.onSetPreferSymbolPoiIcons(preferSymbols)
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 44.dp)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                )
            }

            // Bottom-left: re-center (action) when follow off, or auto-zoom
            // suspended, when GPS available — free-form only; during
            // navigation the button sits above the routing status bar
            // (see navigation overlay branch).
            if (!navState.isNavigating &&
                MapCanvasViewModel.shouldShowReCenterButton(
                    viewModel.mode, state.driveSuspended, state.browseReCenterVisible
                ) && state.gpsFixQuality != GpsFixQuality.NONE) {
                MapReCenterButton(
                    onReCenter = reCenterAction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 12.dp)
                        .navigationBarsPadding()
                )
            }
        } else {
            // Portrait: action column top-left, view column top-right.
            // Action column hidden during navigation (see landscape).
            if (!navState.isNavigating) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    MapActionColumn(
                        isLandscape = false,
                        onToggleMenu = { onMenuExpanded() },
                        onOpenSearch = { viewModel.openSearch() },
                        onToggleFavorites = { viewModel.toggleFavoritesSheet() }
                    )
                }
            }

            // Portrait: right-side widget column, bottom-anchored like the
            // routing view (spec: phone-align-controls-in-all-modes).
            // Hidden during navigation — the routing layout owns the right side.
            if (!navState.isNavigating) {
                MapRightWidgetColumn(
                    isLandscape = false,
                    bottomInset = with(LocalDensity.current) {
                        state.overlayCoveredPx.toDp()
                    },
                    mapAngleRadians = state.viewport.angle,
                    gpsFixQuality = state.gpsFixQuality,
                    isDarkPresentation = state.isDarkPresentation,
                    onCenterClick = reCenterAction,
                    onToggleOrientation = toggleOrientationAction,
                    speedInput = speedInput,
                    overspeedWarningDeltaKmh = state.overspeedWarningDeltaKmh,
                    driveToggle = {
                        DriveModeButton(
                            mode = viewModel.mode,
                            onToggle = modeToggleAction
                        )
                    },
                    canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                    canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                    currentMag = state.viewport.magnification,
                    onZoomIn = {
                        android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                        attributionInteractionTickState.value++
                        viewModel.disengageFollowMode()
                        viewModel.zoomIn()
                        viewModel.renderMap()
                        animateDiscreteZoomToCenter()
                    },
                    onZoomOut = {
                        android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                        attributionInteractionTickState.value++
                        viewModel.disengageFollowMode()
                        viewModel.zoomOut()
                        viewModel.renderMap()
                        animateDiscreteZoomToCenter()
                    },
                    locationOptions = {
                        LocationOptionsOverlay(
                            mode = viewModel.mode,
                            freeFormNorthUp = state.freeFormNorthUp,
                            onSetFreeFormOrientation = { northUp ->
                                viewModel.onSetFreeFormOrientation(northUp)
                            },
                            navNorthUp = state.navNorthUp,
                            onSetNavOrientation = { northUp ->
                                viewModel.onSetNavOrientation(northUp)
                            },
                            autoZoomEnabled = state.autoZoomEnabled,
                            onToggleAutoZoom = { enabled ->
                                viewModel.onToggleAutoZoom(enabled)
                            },
                            keepScreenOn = state.keepScreenOn,
                            onToggleKeepScreenOn = { enabled ->
                                viewModel.onToggleKeepScreenOn(enabled)
                            },
                            darkModePreference = state.darkModePreference,
                            onSetDarkModePreference = { pref ->
                                viewModel.onSetDarkModePreference(pref)
                            },
                            ambientLightSensitivity = state.ambientLightSensitivity,
                            onSetAmbientLightSensitivity = { level ->
                                viewModel.onSetAmbientLightSensitivity(level)
                            },
                            laneHintsEnabled = state.laneHintsEnabled,
                            onToggleLaneHints = { enabled ->
                                viewModel.onToggleLaneHints(enabled)
                            },
                            overspeedWarningDeltaKmh = state.overspeedWarningDeltaKmh,
                            onSetOverspeedWarningDelta = { delta ->
                                viewModel.onSetOverspeedWarningDelta(delta)
                            },
                            carMaxSpeedKmh = state.carMaxSpeedKmh,
                            onSetCarMaxSpeed = { kmh ->
                                viewModel.onSetCarMaxSpeed(kmh)
                            },
                            bicycleMaxSpeedKmh = state.bicycleMaxSpeedKmh,
                            onSetBicycleMaxSpeed = { kmh ->
                                viewModel.onSetBicycleMaxSpeed(kmh)
                            },
                            footMaxSpeedKmh = state.footMaxSpeedKmh,
                            onSetFootMaxSpeed = { kmh ->
                                viewModel.onSetFootMaxSpeed(kmh)
                            },
                            routingAnchor = state.routingAnchor,
                            onSetRoutingAnchor = { anchor ->
                                viewModel.setRoutingAnchor(anchor)
                            },
                            freeDrivingAnchor = state.freeDrivingAnchor,
                            onSetFreeDrivingAnchor = { anchor ->
                                viewModel.setFreeDrivingAnchor(anchor)
                            },
                            renderMode = state.renderMode,
                            onSetRenderMode = { mode ->
                                viewModel.onSetRenderMode(mode)
                            },
                            availableStyles = state.availableStyleSheets,
                            styleSheet = state.styleSheet,
                            onSetStyleSheet = { style ->
                                viewModel.onStyleSheetSelected(style)
                            },
                            preferSymbolPoiIcons = state.preferSymbolPoiIcons,
                            onSetPreferSymbolPoiIcons = { preferSymbols ->
                                viewModel.onSetPreferSymbolPoiIcons(preferSymbols)
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 44.dp)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                )
            }

            // Bottom-left: re-center (action) when follow off, or auto-zoom
            // suspended, when GPS available — free-form only; during
            // navigation the button sits above the routing status bar
            // (see navigation overlay branch).
            if (!navState.isNavigating &&
                MapCanvasViewModel.shouldShowReCenterButton(
                    viewModel.mode, state.driveSuspended, state.browseReCenterVisible
                ) && state.gpsFixQuality != GpsFixQuality.NONE) {
                MapReCenterButton(
                    onReCenter = reCenterAction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 12.dp)
                        .navigationBarsPadding()
                )
            }
        }

    }

    // Car-session advisory (spec: `car-session-presence`): shown while a car
    // session is live, on the free centre-left strip that no action column,
    // widget column, turn card or status card occupies. Informational only.
    if (carSessionActive) {
        CarSessionIndicator(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )
    }

    // OSM attribution notice (bottom-right corner, per OSMF Attribution
    // Guidelines). Auto-hides after 5s of no interaction; any map
    // interaction re-shows it and restarts the timer.
    OsmAttributionOverlay(
        interactionTick = attributionInteractionTickState.value,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = 8.dp)
            .navigationBarsPadding()
    )
    // Free-driving street label (spec: current-road-info): bottom-center
    // pill by default, top-center when the active follow anchor preset is
    // in the bottom row (so the pill never covers the vehicle marker);
    // shown only when no route is active (the navigation road-info row
    // covers the navigating case).
    if (!navState.isNavigating) {
        FreeDrivingStreetPill(
            roadText = freeDrivingStatus?.roadText,
            anchor = state.activeFollowAnchor,
            onPillInset = onPillInset
        )
    }
    if (navState.isNavigating) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top: full-width turn hints
            NextTurnOverlay(
                instruction = navState.nextInstruction,
                laneOneway = navState.laneOneway,
                laneCount = navState.laneCount,
                laneSuggested = navState.laneSuggested,
                laneSuggestedFrom = navState.laneSuggestedFrom,
                laneSuggestedTo = navState.laneSuggestedTo,
                laneTurns = navState.laneTurns,
                laneHintsEnabled = state.laneHintsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onTopInset(it.height) }
            )
            // Middle: right-side widget column (compass directly above the
            // speed widget, zoom at the bottom below all other controls),
            // bottom-anchored above the routing status.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    MapRightWidgetColumn(
                        isLandscape = isLandscape,
                        bottomInset = with(LocalDensity.current) {
                            state.overlayCoveredPx.toDp()
                        },
                        mapAngleRadians = state.viewport.angle,
                        gpsFixQuality = state.gpsFixQuality,
                        isDarkPresentation = state.isDarkPresentation,
                        onCenterClick = {
                            val loc = viewModel.getCurrentLocation()
                            if (loc != null) {
                                viewModel.onToggleFollowMode(true)
                                viewModel.updateCenter(loc.lat, loc.lon)
                            } else {
                                viewModel.showSnackbar("No GPS location available")
                            }
                        },
                        onToggleOrientation = {
                            viewModel.onSetNavOrientation(!state.navNorthUp)
                        },
                        speedInput = speedInput,
                        overspeedWarningDeltaKmh = state.overspeedWarningDeltaKmh,
                        reserveSpeedSlot = true,
                        locationOptions = {
                            LocationOptionsOverlay(
                                mode = viewModel.mode,
                                freeFormNorthUp = state.freeFormNorthUp,
                                onSetFreeFormOrientation = { northUp ->
                                    viewModel.onSetFreeFormOrientation(northUp)
                                },
                                navNorthUp = state.navNorthUp,
                                onSetNavOrientation = { northUp ->
                                    viewModel.onSetNavOrientation(northUp)
                                },
                                autoZoomEnabled = state.autoZoomEnabled,
                                onToggleAutoZoom = { enabled ->
                                    viewModel.onToggleAutoZoom(enabled)
                                },
                                keepScreenOn = state.keepScreenOn,
                                onToggleKeepScreenOn = { enabled ->
                                    viewModel.onToggleKeepScreenOn(enabled)
                                },
                                darkModePreference = state.darkModePreference,
                                onSetDarkModePreference = { pref ->
                                    viewModel.onSetDarkModePreference(pref)
                                },
                                ambientLightSensitivity = state.ambientLightSensitivity,
                                onSetAmbientLightSensitivity = { level ->
                                    viewModel.onSetAmbientLightSensitivity(level)
                                },
                                laneHintsEnabled = state.laneHintsEnabled,
                                onToggleLaneHints = { enabled ->
                                    viewModel.onToggleLaneHints(enabled)
                                },
                                // Usual maximum speed per vehicle: required by the sheet, which
                                // renders the sliders in every mode — the routing view must wire
                                // them like the other surfaces do (spec: location-options-ui).
                                carMaxSpeedKmh = state.carMaxSpeedKmh,
                                onSetCarMaxSpeed = { kmh ->
                                    viewModel.onSetCarMaxSpeed(kmh)
                                },
                                bicycleMaxSpeedKmh = state.bicycleMaxSpeedKmh,
                                onSetBicycleMaxSpeed = { kmh ->
                                    viewModel.onSetBicycleMaxSpeed(kmh)
                                },
                                footMaxSpeedKmh = state.footMaxSpeedKmh,
                                onSetFootMaxSpeed = { kmh ->
                                    viewModel.onSetFootMaxSpeed(kmh)
                                },
                                renderMode = state.renderMode,
                                onSetRenderMode = { mode ->
                                    viewModel.onSetRenderMode(mode)
                                },
                                availableStyles = state.availableStyleSheets,
                                styleSheet = state.styleSheet,
                                onSetStyleSheet = { style ->
                                    viewModel.onStyleSheetSelected(style)
                                },
                                preferSymbolPoiIcons = state.preferSymbolPoiIcons,
                                onSetPreferSymbolPoiIcons = { preferSymbols ->
                                    viewModel.onSetPreferSymbolPoiIcons(preferSymbols)
                                },
                                // Vehicle anchor rows in the ROUTING view too: the
                                // rows are rendered in every mode, so leaving the
                                // defaults here made the picker a no-op during
                                // navigation (spec: location-options-ui — anchor
                                // rows; change fix-anchor-picker-in-navigation).
                                routingAnchor = state.routingAnchor,
                                onSetRoutingAnchor = { anchor ->
                                    viewModel.setRoutingAnchor(anchor)
                                },
                                freeDrivingAnchor = state.freeDrivingAnchor,
                                onSetFreeDrivingAnchor = { anchor ->
                                    viewModel.setFreeDrivingAnchor(anchor)
                                }
                            )
                        },
                        canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                        canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                        currentMag = state.viewport.magnification,
                        onZoomIn = {
                            android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                            attributionInteractionTickState.value++
                            viewModel.zoomIn()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        },
                        onZoomOut = {
                            android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                            attributionInteractionTickState.value++
                            viewModel.zoomOut()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        }
                    )
                }
                // Bottom-left, directly above the routing status bar: the
                // screen-bottom placement is covered by NavigationStateOverlay
                // during navigation, so anchor the re-center button here.
                if (MapCanvasViewModel.shouldShowReCenterButton(
                        viewModel.mode, state.driveSuspended, state.browseReCenterVisible
                    ) && state.gpsFixQuality != GpsFixQuality.NONE) {
                    MapReCenterButton(
                        onReCenter = reCenterAction,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                    )
                }
            }
            // Bottom: routing status, full width — but not while the route-planning session's own
            // card is shown. Both sit on the same bottom edge and the session card is composed in
            // the modal band, so the band would already keep the status card off it; composing the
            // status card over the review left it with no visible or tappable pixel at all before
            // the bands existed (measured 2026-10-06, task 5.1 run (b): the card's layout bounds
            // `top=2059 h=341` lay inside the status card's band, and not one pixel of the card's
            // own content was drawn). One band, one owner (guidelines/Design.md §12): the session
            // offers the route review with its own Stop, and the status card returns when the
            // session's surface closes. The guard stays as the band's owner statement.
            if (!state.showRoutePanel) {
                NavigationStateOverlay(
                    remainingDistance = navState.remainingDistance,
                    etaMillis = navState.etaMillis,
                    currentRoadInfo = navState.currentRoadInfo,
                    distanceProgressPercent = distanceProgressPercent,
                    timeProgressPercent = timeProgressPercent,
                    isRerouting = navState.isRerouting,
                    isOffRoute = navState.isOffRoute,
                    onStopNavigation = stopNavigation,
                    onClick = { onShowNavDetails() },
                    modifier = Modifier.onSizeChanged { onBottomInset(it.height) }
                )
            }
        }
    }
    }
}
