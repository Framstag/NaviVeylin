package com.naviveylin.ui.map

import android.util.Log
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.naviveylin.ui.addressbook.AddressBookSearchContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.naviveylin.ui.about.AboutDialog
import com.naviveylin.ui.attribution.OsmAttributionOverlay
import com.naviveylin.ui.favorites.FavoritesSheet
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.ui.navigation.NavigationDetailsOverlay
import com.naviveylin.ui.navigation.NavigationStateOverlay
import com.naviveylin.ui.navigation.NextTurnOverlay
import com.naviveylin.ui.route.ActiveField
import com.naviveylin.ui.route.FavoritePickerDialog
import com.naviveylin.ui.route.RoutePanel
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import com.naviveylin.ui.route.elapsedTimePercent
import com.naviveylin.ui.route.routeProgressPercent
import com.naviveylin.ui.route.sessionEndClosesTheSurface
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.FollowDisplayState
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.OverrunWindow
import com.naviveylin.core.FollowRefreshPolicy
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.LocationGrant
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.navigationFailureText
import com.naviveylin.core.ResolvedAnchor
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.ZoomAnimation
import com.naviveylin.core.ZoomWalk
import kotlinx.coroutines.isActive
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.RenderMode
import com.naviveylin.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val TAG = "MapCanvasScreen"

/**
 * The phone map screen's overlay layer stack (spec: map-canvas-screen — Phone map overlay layer
 * stack).
 *
 * The screen composes its content in these bands, and the band is the only thing that decides the
 * stacking: a band's [z] value is the `Modifier.zIndex` of the layer wrapper that holds the band's
 * elements, so an element's depth follows from the band it is composed in and never from where its
 * composable happens to sit in the file. [tag] names the band in UI dumps and tests.
 *
 * Order, back to front: [MAP] < [CHROME] < [MENU] < [MODAL] < [SNACKBAR]. The values are spaced by
 * ten so a band can be inserted later without renumbering. The rule itself, for the UI side, is
 * `guidelines/UI.md` §11 (phone overlay layering).
 */
internal enum class MapLayer(val z: Float, val tag: String) {
    /** Canvas bitmap, analysed-route highlight, GPS marker, loading and error state. */
    MAP(0f, "map-layer-map"),

    /**
     * The on-map elements that belong to the map: compass, speed widget, zoom controls,
     * re-center button, street-name pill, turn card, routing status card, OSM attribution and
     * car-session indicator.
     */
    CHROME(10f, "map-layer-chrome"),

    /** The map menu scrim and panel — above the chrome, so no on-map element shows over it. */
    MENU(20f, "map-layer-menu"),

    /**
     * The surfaces that open over the map: search dialog, location details, favorites sheet,
     * route-planning session surface and navigation details.
     */
    MODAL(30f, "map-layer-modal"),

    /** Snackbar messages — frontmost band of this screen. */
    SNACKBAR(40f, "map-layer-snackbar");

    companion object {
        /** Every band, back to front. */
        val backToFront: List<MapLayer> = entries.toList()
    }
}

@Composable
fun MapCanvasScreen(
    mapPath: String,
    onNavigateToMapManager: () -> Unit = {},
    viewModel: MapCanvasViewModel = hiltViewModel(),
    routePanelViewModel: RoutePanelViewModel = hiltViewModel(),
    navigationViewModel: NavigationViewModel = hiltViewModel(),
    carSessionPresence: CarSessionPresence = rememberCarSessionPresence()
) {
    val state by viewModel.uiState.collectAsState()
    // Shared free-driving road/speed status (spec: `current-road-info` — One
    // free-driving road/speed status feeds every surface and the notification):
    // the street label reads this, not a private copy, so it cannot disagree
    // with the ongoing notification.
    val freeDrivingStatus by viewModel.freeDrivingStatus.collectAsState()
    val routeState by routePanelViewModel.uiState.collectAsState()
    val navState by navigationViewModel.state.collectAsState()
    // Route progress for the routing status row (spec: `navigation-status-details` — Route
    // progress rings on the routing status row): the travelled share of the route's distance and
    // the elapsed share of the estimated travel time. Both are null outside navigation, so no
    // ring is drawn then (same spec — Progress rings not shown outside navigation). One pair
    // feeds the status card and the expanded details view, so the two phone surfaces cannot
    // disagree about the same journey.
    val distanceProgressPercent = if (navState.isNavigating) {
        routeProgressPercent(navState.totalDistance, navState.remainingDistance)
    } else null
    val timeProgressPercent = if (navState.isNavigating) {
        elapsedTimePercent(
            start = navState.navigationStartTimeMillis,
            eta = navState.etaMillis,
            now = System.currentTimeMillis()
        )
    } else null
    // Advisory only (spec: `car-session-presence`): informs the driver that a car
    // session is live; it disables nothing and gates no map or navigation action.
    val carSessionActive by carSessionPresence.active.collectAsState()
    // Wire RoutePanelViewModel into MapCanvasViewModel for route result collection
    LaunchedEffect(routePanelViewModel) {
        viewModel.setRoutePanelViewModel(routePanelViewModel)
    }
    // Wire NavigationViewModel follow mode callback
    LaunchedEffect(Unit) {
        navigationViewModel.setFollowModeCallback { enabled ->
            viewModel.onToggleFollowMode(enabled)
        }
        // The adapter records the surface's own mode when a session starts, before it forces
        // follow on (spec: `map-modes` — navigation end restores prior mode).
        navigationViewModel.setPreNavigationSnapshotCallback {
            viewModel.snapshotPreNavigationMode()
        }
        navigationViewModel.setRoutePanelViewModel(routePanelViewModel)
        viewModel.setNavigationViewModel(navigationViewModel)
    }

    // The surface closes with the session, whichever way the session ends (spec:
    // `route-planning-session` — Ending the session removes its surface, "Grace expiry closes an
    // open surface", "No surface survives the session"). The session can end by itself when its
    // grace expires; the host is told here, so no card outlives its session.
    LaunchedEffect(routePanelViewModel) {
        routePanelViewModel.sessionState.collect { session ->
            if (sessionEndClosesTheSurface(session, state.showRoutePanel)) {
                viewModel.dismissRoutePanel()
            }
        }
    }

    // Surface route-calc failures while navigating (spec: reroute-route-visibility):
    // the route panel is hidden during navigation, so the in-panel error text is
    // not visible — show a snackbar instead. Non-navigation failures keep the
    // in-panel error display and do not snackbar. The snackbar shows the failure's own
    // wording for its reason (spec: `routing-failure-reporting` — Each reason is worded by the
    // surface), not one sentence for every cause.
    val failureContext = LocalContext.current
    LaunchedEffect(routePanelViewModel) {
        routePanelViewModel.routeErrorEvent.collect { failure ->
            if (navigationViewModel.state.value.isNavigating) {
                viewModel.showSnackbar(navigationFailureText(
                    context = failureContext,
                    failure = failure,
                    unitMetersRes = R.string.distance_unit_m,
                    unitKilometersRes = R.string.distance_unit_km
                ))
            }
        }
    }

    // A failure the engine raised on the phone's behalf, shown where the user is looking (spec:
    // `navigation-engine` — Errors carry the surface that caused them, and an engine-wide error
    // reaches every surface that presents navigation). Route-panel failures never arrive here:
    // the panel path calls the client itself and publishes to its own state, while the engine path
    // publishes only to the shared state, so the two carriers can never show the same failure twice.
    val engineFailure = com.naviveylin.navigation.phoneFailureOf(navState)
    LaunchedEffect(engineFailure) {
        if (engineFailure != null) {
            viewModel.showSnackbar(navigationFailureText(
                context = failureContext,
                failure = engineFailure,
                unitMetersRes = R.string.distance_unit_m,
                unitKilometersRes = R.string.distance_unit_km
            ))
        }
    }

    // A live car session owns the map (spec: map-canvas-screen — The phone map canvas is
    // suspended while a car session is active): the phone composes the car-session surface
    // instead of the canvas. The branch sits AFTER the wiring effects above — the ViewModels
    // stay connected while the phone is suspended — and BEFORE every overlay, gesture state
    // and remembered frame below, so nothing of the map canvas (including the remembered
    // crossfade/hold frames) survives the suspension and the map is composed from scratch on
    // resume. The advisory pill below therefore only appears once the user has overridden the
    // suspension: it is the surface's compact form, never a second claim beside it.
    if (state.phoneMapSuspended) {
        CarSessionSurface(
            navigationState = navState,
            onShowMap = { viewModel.showMapDuringCarSession() }
        )
        return
    }

val menuExpandedState = remember { mutableStateOf(false) }
var menuExpanded by menuExpandedState
val showAboutDialogState = remember { mutableStateOf(false) }
var showAboutDialog by showAboutDialogState
val showFavoritePickerState = remember { mutableStateOf(false) }
var showFavoritePicker by showFavoritePickerState
val favoritePickerFieldState = remember { mutableStateOf<ActiveField?>(null) }
var favoritePickerField by favoritePickerFieldState
val showNavDetailsState = remember { mutableStateOf(false) }
var showNavDetails by showNavDetailsState
    val attributionInteractionTickState = remember { mutableStateOf(0) }
    var attributionInteractionTick by attributionInteractionTickState
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Location permission state
val showPermissionRationaleState = remember { mutableStateOf(false) }
var showPermissionRationale by showPermissionRationaleState
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }
    var canvasSize by canvasSizeState

    // Address-book (contacts) permission state: one-time rationale dialog
    // before the first request (spec: address-book-permission).
val showAddressBookRationaleState = remember { mutableStateOf(false) }
var showAddressBookRationale by showAddressBookRationaleState
    val addressBookRationaleStore = remember {
        com.naviveylin.data.AddressBookRationaleStore(context.applicationContext)
    }

    // While navigation is active, reject system back on the base map so the
    // app cannot be closed mid-route; the user must stop navigation first.
    // Overlays (favorites sheet, search panel, dialogs) register their own
    // handlers later in composition, so they still dismiss before this fires.
    BackHandler(enabled = navState.isNavigating) {
        viewModel.showSnackbar("Stop navigation before exiting")
    }

    // Live gesture transform: applied to the current bitmap during a multi-touch
    // gesture (pan/zoom/rotate). No render calls happen until the gesture ends;
    // onRenderRequested commits the accumulated changes and renders once.
    val gestureRotationState = remember { mutableStateOf(0f) }
    var gestureRotation by gestureRotationState
    val gestureZoomState = remember { mutableStateOf(1f) }
    var gestureZoom by gestureZoomState
    // smooth-zoom fold (design D3, one displayed-scale model): when a gesture
    // starts over a parked/frozen zoom-animation display scale, that scale is
    // folded into the gesture base here — the graphicsLayer gestureZoom then
    // carries the full visual scale and the draw-layer zoomAnimScale resets to
    // 1f, so the two transforms can never double-apply (caused jumpy pinch +
    // ghost frames after a button zoom).
    val gestureBaseScaleState = remember { mutableStateOf(1f) }
    var gestureBaseScale by gestureBaseScaleState
    // Continuous-pinch input smoothing: the detector's cumulative factor is noisy
    // (emulated pinch pointers teleport → the ratio jumps between zoom levels).
    // A short exponential smoothing on the applied factor damps the spikes; commit
    // uses the DISPLAYED factor so the rendered frame always matches the preview.
    val gestureSmoothFactorState = remember { mutableStateOf(1f) }
    var gestureSmoothFactor by gestureSmoothFactorState
    val gesturePanState = remember { mutableStateOf(Offset.Zero) }
    var gesturePan by gesturePanState
    val gestureCentroidState = remember { mutableStateOf(Offset.Zero) }
    var gestureCentroid by gestureCentroidState
    // Rotation/zoom pivot = the finger midpoint (design D1, spec
    // map-rotation-gesture — Rotation anchored at the finger midpoint). FROZEN
    // at the gesture-start midpoint (set on the first onGestureCentroid of a
    // gesture): re-applying the accumulated rotation around a MOVING pivot
    // re-anchors the transform each frame and jumps the map by
    // (I − s·R(θ))·ΔC (the archived 2026-08-15-bugfix-rotation defect). The
    // centroid drift is carried by the pan compensation instead. NOT reset at
    // gesture end: the derived rotation hold and the render-land crossfade keep
    // pivoting around the gesture-end midpoint (crossfadePivot) until the
    // committed render lands. Overwritten by the next gesture.
    val gesturePivotState = remember { mutableStateOf(Offset.Zero) }
    var gesturePivot by gesturePivotState
    // True while a multi-touch gesture is in progress (first onGestureCentroid
    // → onRenderRequested): gates the one-time pivot freeze at gesture start.
    val gestureActiveState = remember { mutableStateOf(false) }
    var gestureActive by gestureActiveState
    // Gesture-end midpoint (captured in onRenderRequested before the gesture
    // state resets): the rotation hold and the render-land crossfade pivot
    // around this point — the same focal point the commit (D2) uses, so the
    // held/crossfaded old frame aligns with the committed render.
    val crossfadePivotState = remember { mutableStateOf(Offset.Zero) }
    var crossfadePivot by crossfadePivotState
    // Rotation display-layer hold (design D1): at gesture end the accumulated
    // rotation stays applied visually (graphicsLayer rotationZ) until the
    // re-render at the committed angle lands — otherwise the old bitmap (old
    // angle) shows unrotated for the render duration and the map temporarily
    // jumps back to the pre-gesture angle. The hold ANGLE is derived per draw
    // from the collected state (committed − front-buffer angle) so the value is
    // atomic with the bitmap swap (no one-frame double-rotation twist when the
    // render lands); the flag toggles the derived rotation only in the
    // gesture-end → render-land window and is cleared when the front buffer
    // reaches the committed angle or on the next gesture start.
    val rotationHoldActiveState = remember { mutableStateOf(false) }
    var rotationHoldActive by rotationHoldActiveState
    // Rotation render-land crossfade: the old rotated frame fades into the new
    // native render (masking any tile-vs-native rasterization difference at the
    // swap) — mirror of the zoom crossfade below.
    val crossfadeAngleState = remember { mutableStateOf(0f) }
    var crossfadeAngle by crossfadeAngleState
    val lastFrontAngleState = remember { mutableStateOf(Double.NaN) }
    var lastFrontAngle by lastFrontAngleState

    // Follow-mode smooth scroll (spec: smooth-follow): extrapolate the displayed
    // position between 1 Hz GPS fixes and ease corrections on fix arrival. The
    // display loop runs only in follow mode while moving; the draw block applies
    // the offset within the overrun margin. Predicted positions are display-only
    // — the navigation engine keeps receiving real fixes.
    val followPrediction = remember { FollowPrediction() }
    // Monotonic forward display (spec: smooth-follow — Correction easing, delta
    // fix-follow-vehicle-jumps): eases toward the predicted position each frame
    // and HOLDS when the fix-arrival target drops behind — the display never
    // slides backward along travel (the per-fix jump sawtooth).
    val followDisplayState = remember { FollowDisplayState() }
    // Bounded-rate frame refresh (spec: smooth-follow — Follow frame refresh before the overrun
    // margin is exhausted): the follow frame is requested while margin remains, instead of only
    // once the offset is already clamped. One request outstanding; armed again when a new frame
    // lands in `ui.renderViewport`.
    val followRefresh = remember { FollowRefreshPolicy() }
    val lastFrameViewportState = remember { mutableStateOf<MapRenderer.RenderViewport?>(null) }
    var lastFrameViewport by lastFrameViewportState
    val followActiveState = remember { mutableStateOf(false) }
    var followActive by followActiveState
    val followDisplayLatState = remember { mutableStateOf(Double.NaN) }
    var followDisplayLat by followDisplayLatState
    val followDisplayLonState = remember { mutableStateOf(Double.NaN) }
    var followDisplayLon by followDisplayLonState
    val followOffsetXState = remember { mutableStateOf(0f) }
    var followOffsetX by followOffsetXState
    val followOffsetYState = remember { mutableStateOf(0f) }
    var followOffsetY by followOffsetYState

    // Single-finger pan display window (spec: map-pan-zoom — Touch-based pan,
    // canvas-overrun — Overrun window shift for pan). The panned center is
    // DISPLAY-ONLY state: it is never written to the ViewModel per pan event (that
    // would copy the whole UI state and recompose this screen at touch rate, spec
    // render-performance). It is committed at gesture end and whenever a render is
    // requested; the offset below is derived per frame against the frame actually
    // on screen, so offset and bitmap can never describe different generations.
    // Held after the gesture until a frame carrying the committed center lands
    // (the same hold semantics the zoom display scale uses): dropping it earlier
    // would snap the map back to the old frame's center.
    val panDisplayLatState = remember { mutableStateOf(Double.NaN) }
    var panDisplayLat by panDisplayLatState
    val panDisplayLonState = remember { mutableStateOf(Double.NaN) }
    var panDisplayLon by panDisplayLonState
    // True while a single-finger pan gesture is live (first onPan → onRenderRequested
    // or the first multi-touch event); gates the one-time gesture-start side
    // effects and the pan window derivation.
    val panGestureActiveState = remember { mutableStateOf(false) }
    var panGestureActive by panGestureActiveState
    val panOffsetXState = remember { mutableStateOf(0f) }
    var panOffsetX by panOffsetXState
    val panOffsetYState = remember { mutableStateOf(0f) }
    var panOffsetY by panOffsetYState
    // Timestamp of the last render request issued because the pan window was
    // saturated: a fast drag must not queue renders faster than they complete
    // (spec: render-performance — Pan hot path stays off the frame budget).
    val lastPanRenderRequestMsState = remember { mutableStateOf(0L) }
    var lastPanRenderRequestMs by lastPanRenderRequestMsState

    // Regions the phone's own overlays cover (px), measured so the follow anchor
    // resolves into the VISIBLE map area (spec: smooth-follow — visible-area
    // scenarios): turn card at the top, routing status at the bottom, widget column
    // on the right, street-name pill at the bottom in free driving.
    var overlayTopInset by remember { mutableIntStateOf(0) }
    var overlayBottomInset by remember { mutableIntStateOf(0) }
    var overlayRightInset by remember { mutableIntStateOf(0) }
    var overlayPillInset by remember { mutableIntStateOf(0) }
    val followLogCountState = remember { mutableStateOf(0) }
    var followLogCount by followLogCountState

    // smooth-zoom (spec: smooth-zoom): eased front-buffer zoom animation for
    // discrete zoom input (buttons, scroll wheel, keyboard — no double-tap
    // zoom exists). The animation scales the currently rendered bitmap around
    // the zoom anchor while the debounced native render at the target
    // magnification runs. Render completion hands over in the frame loop
    // below: immediate swap when the animation already holds the target
    // scale, crossfade from the scaled old frame otherwise (gesture case).
    // Display-only state: the navigation engine and the persisted viewport
    // never see the animated scale.
    val zoomAnim = remember { ZoomAnimation() }
    val zoomAnimScaleState = remember { mutableStateOf(1f) }
    var zoomAnimScale by zoomAnimScaleState
    val zoomAnchorState = remember { mutableStateOf(Offset.Zero) }
    var zoomAnchor by zoomAnchorState
    // Last magnification the display animation was retargeted to: a change (a walked
    // step, a manual commit) retracks the running animation from its current scale
    // instead of restarting it (spec: smooth-zoom - Stepped magnification change
    // retracks per step).
    val lastZoomAnimTargetState = remember { mutableStateOf(Double.NaN) }
    var lastZoomAnimTarget by lastZoomAnimTargetState
    val prevRenderedBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    var prevRenderedBitmap by prevRenderedBitmapState
    val lastFrontMagState = remember { mutableStateOf(-1.0) }
    var lastFrontMag by lastFrontMagState
    // Crossfade at render completion (zoom-transition-scaling delta): the old
    // front-buffer copy is drawn over the swapped-in frame, fading out.
    val crossfadeBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    var crossfadeBitmap by crossfadeBitmapState
    val crossfadeScaleState = remember { mutableStateOf(1f) }
    var crossfadeScale by crossfadeScaleState
    val crossfadeAnchorState = remember { mutableStateOf(Offset.Zero) }
    var crossfadeAnchor by crossfadeAnchorState
    val crossfadeStartMsState = remember { mutableStateOf(0L) }
    var crossfadeStartMs by crossfadeStartMsState
    val crossfadeAlphaState = remember { mutableStateOf(0f) }
    var crossfadeAlpha by crossfadeAlphaState

    /**
     * Ends a crossfade transition and gives up the frame copy it holds. The crossfade owns ONE
     * frame-sized copy at a time — created when a transition starts, replaced (never stacked)
     * when another transition supersedes it, and released here the moment the transition ends, so
     * a finished gesture leaves no frame reference or uploaded graphics allocation behind for the
     * session (spec: `render-performance` — Animation frame references are released when the
     * transition completes). [prevRenderedBitmap] is NOT a copy: it is the frame the UI state
     * already holds, used as the crossfade's source.
     */
    fun endCrossfade() {
        crossfadeBitmap = null
        crossfadeAlpha = 0f
        crossfadeAngle = 0f
        crossfadePivot = Offset.Zero
    }

    /**
     * Starts/retracks the zoom animation toward the committed magnification
     * (design D2/D3/D6). [anchor] is the screen point that must stay visually
     * fixed: screen center for buttons/keyboard/auto-zoom, cursor for the
     * wheel. Auto-zoom commits pass a longer duration (spec: smooth-zoom —
     * auto-zoom commits animate at ~500 ms).
     */
    fun animateDiscreteZoom(anchor: Offset, durationMs: Long = 250L) {
        val s = viewModel.uiState.value
        val frontMag = s.renderViewport?.mag ?: return
        // Bounded display scale (spec: smooth-zoom - Zoom animation never exposes
        // uncovered map area): a walked step is inside the window by construction, so
        // this only guards a magnification change that ever lands un-walked.
        val target = ZoomWalk.displayScale(s.zoomAnimationTargetMag, frontMag)
        val now = System.currentTimeMillis()
        if (zoomAnim.active) {
            zoomAnim.retrack(target, anchor.x, anchor.y, now, durationMs)
        } else {
            zoomAnim.start(zoomAnimScale, target, anchor.x, anchor.y, now, durationMs)
        }
        zoomAnchor = anchor
    }

    /**
     * The screen point a zoom animation keeps visually fixed (spec: smooth-zoom -
     * Geographic anchor stays fixed during zoom animation): the resolved follow anchor
     * pixel while a follow mode is active - the vehicle's own position on the map - and
     * the screen center otherwise. The marker overlay receives the same pivot, so the
     * vehicle cannot drift off its anchor slot while the animation plays.
     */
    fun zoomAnimationAnchor(): Offset =
        zoomAnimationAnchor(
            followMode = viewModel.uiState.value.followMode,
            anchor = viewModel.uiState.value.resolvedAnchor,
            canvasWidth = canvasSize.width,
            canvasHeight = canvasSize.height
        )

    /** Screen center (or follow anchor) for button/keyboard zoom and auto-zoom commits. */
    fun animateDiscreteZoomToCenter(durationMs: Long = 250L) {
        animateDiscreteZoom(zoomAnimationAnchor(), durationMs)
    }

    // Auto-zoom display animation (spec: smooth-zoom — auto-zoom commits
    // animate at the slower ~500 ms duration): every fractional auto-zoom
    // commit bumps autoZoomCommitTick; ease the front buffer toward the new
    // magnification while the native render lands. Consecutive commits
    // retrack the running animation without snapping (spec smooth-zoom —
    // Retracking on rapid zoom input).
    LaunchedEffect(viewModel.uiState.value.autoZoomCommitTick) {
        if (viewModel.uiState.value.autoZoomCommitTick > 0) {
            animateDiscreteZoomToCenter(AUTO_ZOOM_ANIMATION_MS)
        }
    }

    /**
     * A pinch gesture takes over the zoom display (design D3: gesture and
     * animation never run simultaneously) — park the animation at its current
     * scale and fold it into the gesture base (single displayed-scale model).
     */
    fun freezeZoomAnimationForGesture() {
        if (zoomAnim.active) {
            zoomAnim.finish(System.currentTimeMillis())
            zoomAnimScale = zoomAnim.currentScale(System.currentTimeMillis())
            Log.d(TAG, "smooth-zoom: gesture takes over, frozen scale=$zoomAnimScale anchor=$zoomAnchor")
        }
        if (zoomAnimScale != 1f) {
            gestureBaseScale = zoomAnimScale
            zoomAnimScale = 1f
            gestureZoom = gestureBaseScale
            Log.d(TAG, "smooth-zoom: fold frozen display scale into gesture base=$gestureBaseScale")
        }
    }

    /**
     * The frame in hand as the pan rules see it: the frame the UI actually displays
     * (emitted bitmap dimensions + emitted viewport), never the renderer's private
     * front buffer — the applied offset and the renderer's coverage predicate must
     * describe one frame (spec: canvas-overrun — Coverage and display never disagree
     * into a freeze).
     */
    fun panFrame(): PanWindowRules.Frame? {
        val s = viewModel.uiState.value
        val vp = s.renderViewport ?: return null
        val bitmap = s.renderedBitmap ?: return null
        return PanWindowRules.Frame(
            vp.lat, vp.lon, vp.mag, vp.angle, bitmap.width, bitmap.height
        )
    }

    /**
     * The pan display window's offset for the frame in hand, or null when there is no
     * usable frame to shift (no displayed frame yet, or a frame without an overrun
     * margin). Same inputs and helper as the per-frame derivation, so the gesture's
     * clamp decision and the applied offset agree (spec: canvas-overrun — Offset and
     * frame stay consistent).
     */
    fun panWindowOffset(): OverrunWindow.DisplayOffset? {
        val frame = panFrame() ?: return null
        return PanWindowRules.offsetFor(
            panDisplayLat, panDisplayLon, frame,
            canvasSize.width, canvasSize.height,
            context.resources.displayMetrics.densityDpi.toDouble()
        )
    }

    /** Commit the displayed (panned) center — the persisted viewport follows it. */
    fun commitPanCenter() {
        if (panDisplayLat.isNaN() || panDisplayLon.isNaN()) return
        viewModel.updateCenter(panDisplayLat, panDisplayLon)
    }

    /**
     * Re-center the frame on the displayed center when the pan window cannot follow
     * the finger any further (spec: canvas-overrun — Large pan triggers full
     * re-render). A missing/saturated window means the frame in hand cannot serve the
     * pan, so a render is needed. [force] requests it regardless of the clamp
     * (gesture hand-over); the throttle keeps a fast drag from queueing renders
     * faster than they complete.
     */
    fun requestPanRecenter(force: Boolean = false) {
        val window = panWindowOffset()
        if (window != null && !force && !window.clamped) return
        val now = System.currentTimeMillis()
        if (now - lastPanRenderRequestMs < PAN_RENDER_REQUEST_THROTTLE_MS) return
        lastPanRenderRequestMs = now
        // Bounded diagnostics (spec: render-performance — at most one gated line per
        // gesture plus one per unservable-window rejection): a rejected window is the
        // state that used to be invisible in logcat, and the throttle collapses the
        // remaining touch events into it instead of logging per event.
        if (PAN_DIAGNOSTICS_ENABLED && window == null) {
            Log.d(TAG, "pan: no usable frame window, render requested mag=" +
                viewModel.uiState.value.viewport.magnification)
        }
        commitPanCenter()
        viewModel.renderMap()
    }

    MapCanvasFollowDisplayLoop(
        viewModel = viewModel,
        context = context,
        zoomAnim = zoomAnim,
        followPrediction = followPrediction,
        followDisplayState = followDisplayState,
        followRefresh = followRefresh,
        endCrossfade = { endCrossfade() },
        animateDiscreteZoom = { anchor, durationMs -> animateDiscreteZoom(anchor, durationMs) },
        zoomAnimationAnchor = { zoomAnimationAnchor() },
        zoomAnchorState = zoomAnchorState,
        requestPanRecenter = { requestPanRecenter(it) },
        canvasSizeState = canvasSizeState,
        crossfadeAlphaState = crossfadeAlphaState,
        crossfadeAnchorState = crossfadeAnchorState,
        crossfadeAngleState = crossfadeAngleState,
        crossfadeBitmapState = crossfadeBitmapState,
        crossfadeScaleState = crossfadeScaleState,
        crossfadeStartMsState = crossfadeStartMsState,
        followActiveState = followActiveState,
        followDisplayLatState = followDisplayLatState,
        followDisplayLonState = followDisplayLonState,
        followLogCountState = followLogCountState,
        followOffsetXState = followOffsetXState,
        followOffsetYState = followOffsetYState,
        gestureActiveState = gestureActiveState,
        gestureBaseScaleState = gestureBaseScaleState,
        gestureZoomState = gestureZoomState,
        lastFrameViewportState = lastFrameViewportState,
        lastFrontAngleState = lastFrontAngleState,
        lastFrontMagState = lastFrontMagState,
        lastZoomAnimTargetState = lastZoomAnimTargetState,
        panDisplayLatState = panDisplayLatState,
        panDisplayLonState = panDisplayLonState,
        panGestureActiveState = panGestureActiveState,
        panOffsetXState = panOffsetXState,
        panOffsetYState = panOffsetYState,
        prevRenderedBitmapState = prevRenderedBitmapState,
        rotationHoldActiveState = rotationHoldActiveState,
        zoomAnimScaleState = zoomAnimScaleState
    )

    // Permission launcher. Both scopes are requested in ONE request: on API 31+ a
    // fine-only request is ignored by the platform, so the app would never receive
    // a fix (spec: `location-permissions` — Runtime permission request). An
    // approximate grant is a working state, not a failure.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.startLocationUpdates()
        } else {
            // Check if permanently denied
            if (!shouldShowRequestPermissionRationale(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showPermissionRationale = true
            }
        }
    }

    // Ask for coarse + fine (the only form the platform accepts on API 31+).
    val requestLocationPermission: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // App's system settings: the only route left once the platform stops asking.
    val openLocationSettings: () -> Unit = {
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            )
        } catch (_: Exception) {
            // No activity handles the settings intent — nothing else to offer.
        }
    }

    // Upgrade action for the navigation gate: re-request while the system can still
    // ask, otherwise send the user to the app's settings (spec: `location-permissions`
    // — Phone offers the upgrade action).
    val onPreciseLocationAction: () -> Unit = {
        when (preciseLocationAction(permanentlyDenied = showPermissionRationale)) {
            PreciseLocationAction.REQUEST_PERMISSION -> requestLocationPermission()
            PreciseLocationAction.OPEN_SETTINGS -> openLocationSettings()
        }
    }

    // Address-book permission launcher: result updates visibility (grant ->
    // available, deny -> hidden); the rationale dialog runs before any launch.
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshAddressBookAvailability()
    }

    // The phone's one stop path (spec: `navigation-status-details` — The status card's stop is the
    // phone's shared stop path; design D2). Every stop control — the routing status card, the
    // expanded description and the session's own Stop Navigation — ends navigation through this
    // lambda; the session adapter observes the engine and decides the rest, so no control clears
    // the route behind the session's back (spec: `route-planning-session` — Grace period after
    // navigation is stopped).
    val stopNavigation: () -> Unit = { navigationViewModel.stopNavigation() }

    // Request location permission on first composition if not granted. Any grant
    // (approximate included) is enough to start updating; the precise upgrade is
    // requested only where it is needed (spec: `location-permissions` — Approximate
    // grant is a working state).
    LaunchedEffect(Unit) {
        if (LocationGrant.isGranted(context)) {
            viewModel.startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }

    // Show the one-time address-book rationale dialog before the first
    // READ_CONTACTS request (spec: address-book-permission). Never shown again
    // once the decision is recorded, regardless of grant/deny outcome.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED &&
            !addressBookRationaleStore.wasShown()
        ) {
            showAddressBookRationale = true
        }
    }

    // Show snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Initialise map on first composition
    LaunchedEffect(mapPath) {
        viewModel.initMap(mapPath)
    }

    // Save viewport on pause, stop location updates
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.saveViewport()
                    viewModel.stopLocationUpdates()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-read the grant: it can change in system settings while the
                    // app lives, including an approximate → precise upgrade
                    // (spec: `location-permissions` — Upgrading from approximate to
                    // precise while running).
                    if (LocationGrant.isGranted(context)) {
                        viewModel.startLocationUpdates()
                    }
                    // Permission may have changed in system settings; keep the
                    // address-book menu visibility in sync (spec: address-book-permission).
                    viewModel.refreshAddressBookAvailability()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationUpdates()
        }
    }

    // Keep screen on while the app is in the foreground when the setting is enabled.
    KeepScreenOnEffect(state.keepScreenOn)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Orientation for the whole screen (spec: landscape-layout); the
        // overlay layout below re-derives it from its own constraints.
        val isLandscape = maxWidth > maxHeight

        val surfaceColor = MaterialTheme.colorScheme.surface

        // On-map speed widget (spec: map-speed-widget): navigation engine
        // values while navigating, GPS-derived values in follow mode.
        val speedInput = speedWidgetInput(
            isNavigating = navState.isNavigating,
            navCurrentSpeedKmH = navState.currentSpeedKmH,
            navMaxSpeedKmH = navState.maxSpeedKmH,
            followCurrentSpeedKmH = state.currentSpeedKmH,
            followMaxSpeedKmH = state.maxSpeedKmH,
            followMode = state.followMode
        )
        // Re-center action is mode-dependent (spec: map-modes): BROWSE centers
        // on GPS and stays in browse; FREE_DRIVE resets the suspended drive
        // preset; NAVIGATION re-engages follow on the current position.
        val reCenterAction = {
            when (viewModel.mode) {
                MapMode.BROWSE -> viewModel.recenterInBrowse()
                MapMode.FREE_DRIVE -> viewModel.resetDrivePreset()
                MapMode.NAVIGATION -> {
                    val loc = viewModel.getCurrentLocation()
                    if (loc != null) {
                        viewModel.onToggleFollowMode(true)
                        viewModel.updateCenter(loc.lat, loc.lon)
                        viewModel.renderMap()
                    } else {
                        viewModel.showSnackbar("No GPS location available")
                    }
                }
            }
        }
        // Mode toggle (spec: map-modes — mode toggle button): the compass
        // short-press switches BROWSE <-> FREE_DRIVE; hidden during NAVIGATION
        // (the nav layout uses the short-press for re-center instead).
        val modeToggleAction = {
            when (viewModel.mode) {
                MapMode.BROWSE -> viewModel.enterFreeDrive()
                MapMode.FREE_DRIVE -> viewModel.exitFreeDrive()
                MapMode.NAVIGATION -> Unit
            }
        }
        // Orientation toggle follows the active state's orientation setting
        // (spec: location-options-ui — orientation controls per state).
        val toggleOrientationAction = {
            when (viewModel.mode) {
                MapMode.BROWSE -> viewModel.onSetFreeFormOrientation(!state.freeFormNorthUp)
                MapMode.FREE_DRIVE, MapMode.NAVIGATION -> viewModel.onSetNavOrientation(!state.navNorthUp)
            }
        }

        // Map layer (spec: map-canvas-screen — Phone map overlay layer stack): the
        // rendered frame and its state are the backmost band. Every on-map element and
        // every surface that opens over the map is composed above it in its own band;
        // the band decides the stacking, never the position in this file.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MapLayer.MAP.z)
                .testTag(MapLayer.MAP.tag)
        ) {
            MapCanvasMapBandContent(
                state = state,
                viewModel = viewModel,
                routeState = routeState,
                surfaceColor = surfaceColor,
                context = context,
                animateDiscreteZoom = { animateDiscreteZoom(it) },
                animateDiscreteZoomToCenter = { animateDiscreteZoomToCenter() },
                freezeZoomAnimationForGesture = { freezeZoomAnimationForGesture() },
                panWindowOffset = { panWindowOffset() },
                commitPanCenter = { commitPanCenter() },
                requestPanRecenter = { requestPanRecenter(it) },
                attributionInteractionTickState = attributionInteractionTickState,
                canvasSizeState = canvasSizeState,
                crossfadeAlphaState = crossfadeAlphaState,
                crossfadeAnchorState = crossfadeAnchorState,
                crossfadeAngleState = crossfadeAngleState,
                crossfadeBitmapState = crossfadeBitmapState,
                crossfadePivotState = crossfadePivotState,
                crossfadeScaleState = crossfadeScaleState,
                followActiveState = followActiveState,
                followDisplayLatState = followDisplayLatState,
                followDisplayLonState = followDisplayLonState,
                followOffsetXState = followOffsetXState,
                followOffsetYState = followOffsetYState,
                gestureActiveState = gestureActiveState,
                gestureBaseScaleState = gestureBaseScaleState,
                gestureCentroidState = gestureCentroidState,
                gesturePanState = gesturePanState,
                gesturePivotState = gesturePivotState,
                gestureRotationState = gestureRotationState,
                gestureSmoothFactorState = gestureSmoothFactorState,
                gestureZoomState = gestureZoomState,
                lastPanRenderRequestMsState = lastPanRenderRequestMsState,
                panDisplayLatState = panDisplayLatState,
                panDisplayLonState = panDisplayLonState,
                panGestureActiveState = panGestureActiveState,
                panOffsetXState = panOffsetXState,
                panOffsetYState = panOffsetYState,
                rotationHoldActiveState = rotationHoldActiveState,
                zoomAnchorState = zoomAnchorState,
                zoomAnimScaleState = zoomAnimScaleState
            )
        }

        // Chrome layer (spec: map-canvas-screen — Phone map overlay layer stack): every
        // element that belongs to the map itself. A surface that opens over the map is
        // composed above the whole of this band, never between the map and this band.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MapLayer.CHROME.z)
                .testTag(MapLayer.CHROME.tag)
        ) {
            MapCanvasChromeBandContent(
                state = state,
                viewModel = viewModel,
                navState = navState,
                freeDrivingStatus = freeDrivingStatus,
                carSessionActive = carSessionActive,
                isLandscape = isLandscape,
                speedInput = speedInput,
                distanceProgressPercent = distanceProgressPercent,
                timeProgressPercent = timeProgressPercent,
                reCenterAction = reCenterAction,
                modeToggleAction = modeToggleAction,
                toggleOrientationAction = toggleOrientationAction,
                animateDiscreteZoomToCenter = { animateDiscreteZoomToCenter() },
                attributionInteractionTickState = attributionInteractionTickState,
                stopNavigation = stopNavigation,
                onMenuExpanded = { menuExpanded = true },
                onShowNavDetails = { showNavDetails = true },
                onRightInset = { overlayRightInset = it },
                onTopInset = { overlayTopInset = it },
                onBottomInset = { overlayBottomInset = it },
                onPillInset = { overlayPillInset = it }
            )
        }

        // Menu layer (spec: map-canvas-screen — The map menu is composed above the map
        // chrome): the menu scrim and panel cover the chrome, so no on-map element shows
        // over the menu. Composed after the chrome band and before the modal band, which
        // keeps the menu's back handler in its registration position relative to the
        // surfaces (spec: map-canvas-screen — System back dismisses topmost overlay).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MapLayer.MENU.z)
                .testTag(MapLayer.MENU.tag)
        ) {
            MapCanvasMenuBandContent(
                menuExpandedState = menuExpandedState,
                onNavigateToMapManager = onNavigateToMapManager,
                viewModel = viewModel,
                showAboutDialogState = showAboutDialogState,
                isLandscape = isLandscape
            )
        }

        // Inset block (spec: smooth-follow — visible-area scenarios): publishes what the
        // map overlays cover so the follow anchor stays inside the part of the map the driver
        // can actually see. It paints nothing, so it stays at screen level, not in a UI band.
        // Navigation overlays: full-width turn hints at the top, the right-side
        // widget column (compass directly above the speed widget, zoom at the
        // bottom below all other controls) spanning from above the routing status
        // up to the top, and the routing status covering the bottom of the window.
        //
        // Publishes what the map overlays cover so the follow anchor stays inside
        // the part of the map the driver can actually see (spec: smooth-follow —
        // "Bottom anchor stays visible above the routing status card"). Runs in every
        // mode: browse/free driving measure no turn/status card, so their insets are
        // the (usually zero) measured values of the widgets actually shown.
        // Only a bottom pill must reserve space below the follow anchor: when
        // the pill sits on top (bottom-row anchor), the bottom inset is zeroed.
        // Keyed on [pillAtTop] too — the pill size does not change on an anchor
        // toggle, so the inset decision must not depend on a size callback alone.
        val pillAtTop = state.activeFollowAnchor.fy == 0.9
        LaunchedEffect(navState.isNavigating, overlayTopInset, overlayBottomInset, overlayRightInset, overlayPillInset, pillAtTop) {
            viewModel.setMapOverlayInsets(
                top = if (navState.isNavigating) overlayTopInset else 0,
                bottom = if (navState.isNavigating) overlayBottomInset else if (pillAtTop) 0 else overlayPillInset,
                right = overlayRightInset
            )
        }

        // Modal layer (spec: map-canvas-screen — No surface opens between the map and its
        // chrome): every surface that opens over the map. Composed above the whole chrome
        // band, so no compass, speed widget, zoom control, street-name pill, turn card or
        // routing status card can paint over one of them. The order of the surfaces inside
        // this band is unchanged, which keeps every back handler in its registration
        // position (spec: System back dismisses topmost overlay).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MapLayer.MODAL.z)
                .testTag(MapLayer.MODAL.tag)
        ) {
            MapCanvasModalBandContent(
                state = state,
                viewModel = viewModel,
                routePanelViewModel = routePanelViewModel,
                routeState = routeState,
                navState = navState,
                navigationViewModel = navigationViewModel,
                context = context,
                isLandscape = isLandscape,
                distanceProgressPercent = distanceProgressPercent,
                timeProgressPercent = timeProgressPercent,
                showAboutDialogState = showAboutDialogState,
                favoritePickerFieldState = favoritePickerFieldState,
                showFavoritePickerState = showFavoritePickerState,
                onBottomInset = { overlayBottomInset = it },
                showPermissionRationaleState = showPermissionRationaleState,
                onPreciseLocationAction = onPreciseLocationAction,
                contactsPermissionLauncher = contactsPermissionLauncher,
                showAddressBookRationaleState = showAddressBookRationaleState,
                addressBookRationaleStore = addressBookRationaleStore,
                showNavDetailsState = showNavDetailsState,
                stopNavigation = stopNavigation,
                onNavigateToMapManager = onNavigateToMapManager
            )
        }

        // Reset the expanded view when navigation ends
        LaunchedEffect(navState.isNavigating) {
            if (!navState.isNavigating) showNavDetails = false
        }

        // Snackbar layer (spec: map-canvas-screen — Snackbar messages are composed above
        // the map and its in-window surfaces): the frontmost band of this screen. A dialog
        // or bottom sheet the platform presents in its own window is still composed over it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MapLayer.SNACKBAR.z)
                .testTag(MapLayer.SNACKBAR.tag)
        ) {
            MapCanvasSnackbarBandContent(hostState = snackbarHostState)
        }
    }
}







