package com.naviveylin.ui.map

import android.util.Log
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.naviveylin.ui.about.AboutDialog
import com.naviveylin.ui.favorites.FavoritesSheet
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.ui.navigation.NavigationStateOverlay
import com.naviveylin.ui.navigation.NextTurnOverlay
import com.naviveylin.ui.route.ActiveField
import com.naviveylin.ui.route.FavoritePickerDialog
import com.naviveylin.ui.route.RoutePanel
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSummaryDialog
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.ProjectionUtils
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.round
import kotlin.math.sin

private const val TAG = "MapCanvasScreen"

@Composable
fun MapCanvasScreen(
    mapPath: String,
    onNavigateToMapManager: () -> Unit = {},
    viewModel: MapCanvasViewModel = hiltViewModel(),
    routePanelViewModel: RoutePanelViewModel = hiltViewModel(),
    navigationViewModel: NavigationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val routeState by routePanelViewModel.uiState.collectAsState()
    val navState by navigationViewModel.state.collectAsState()
    // Wire RoutePanelViewModel into MapCanvasViewModel for route result collection
    LaunchedEffect(routePanelViewModel) {
        viewModel.setRoutePanelViewModel(routePanelViewModel)
    }
    // Wire NavigationViewModel follow mode callback
    LaunchedEffect(Unit) {
        navigationViewModel.setFollowModeCallback { enabled ->
            viewModel.onToggleFollowMode(enabled)
        }
        navigationViewModel.setRoutePanelViewModel(routePanelViewModel)
        viewModel.setNavigationViewModel(navigationViewModel)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showSearchPanel by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var favoritePickerField by remember { mutableStateOf<ActiveField?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Location permission state
    var showPermissionRationale by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
    var gestureRotation by remember { mutableStateOf(0f) }
    var gestureZoom by remember { mutableStateOf(1f) }
    var gesturePan by remember { mutableStateOf(Offset.Zero) }
    var gestureCentroid by remember { mutableStateOf(Offset.Zero) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startLocationUpdates()
        } else {
            // Check if permanently denied
            if (!shouldShowRequestPermissionRationale(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showPermissionRationale = true
            }
        }
    }

    // Request location permission on first composition if not granted
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startLocationUpdates()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startLocationUpdates()
                    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Snackbar at bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        val surfaceColor = MaterialTheme.colorScheme.surface

        when {
            state.isLoading && state.renderedBitmap == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            state.error != null && state.renderedBitmap == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.retryRender() }) {
                        Text("Retry")
                    }
                }
            }

            else -> {
                // Map canvas with gesture handling
                // Single pointerInput block handles all gestures to avoid conflicts
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusTarget()
                        .onSizeChanged { size ->
                            viewModel.setScreenSize(size.width, size.height)
                            canvasSize = size
                        }
                        // Live gesture transform on the current bitmap: rotation,
                        // zoom, and pan are applied visually with no render calls
                        // until the gesture ends (onRenderRequested commits).
                        // The rotation is applied around the SCREEN CENTER, not the
                        // gesture centroid: the rendered bitmap exactly fills the
                        // canvas, so rotating around an off-center pivot swings the
                        // map off-screen (empty regions) — worst at 180°. Center
                        // rotation also matches the committed native render (which
                        // rotates around the viewport center), so there is no jump
                        // on gesture end. The zoom keeps its pivot at the gesture
                        // centroid (matches zoomAtCursor on commit) via the
                        // translation compensation in gestureTransformTranslation.
                        .graphicsLayer {
                            val theta = gestureRotation
                            val s = gestureZoom
                            val t = gestureTransformTranslation(
                                theta, s, gestureCentroid, size, gesturePan
                            )
                            translationX = t.x
                            translationY = t.y
                            rotationZ = normalizeDegrees(Math.toDegrees(theta.toDouble())).toFloat()
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .mapGestureHandler(
                            object : MapGestureCallbacks {
                                override fun onPan(dx: Float, dy: Float) {
                                    viewModel.disengageFollowMode()
                                    val s = viewModel.uiState.value
                                    val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                    val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                                        dx.toDouble(), dy.toDouble(),
                                        s.viewport.angle,
                                        s.viewport.magnification,
                                        canvasSize.width.toDouble(), canvasSize.height.toDouble(),
                                        s.viewport.centerLat, s.viewport.centerLon, dpi
                                    )
                                    viewModel.updateCenter(newLat, newLon)
                                    viewModel.renderMap()
                                }

                                override fun onCentroidPan(dx: Float, dy: Float) {
                                    // Update the center state (no render); the visual
                                    // translation is applied to the current bitmap and
                                    // committed on gesture end.
                                    viewModel.disengageFollowMode()
                                    val s = viewModel.uiState.value
                                    val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                    val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                                        dx.toDouble(), dy.toDouble(),
                                        s.viewport.angle,
                                        s.viewport.magnification,
                                        canvasSize.width.toDouble(), canvasSize.height.toDouble(),
                                        s.viewport.centerLat, s.viewport.centerLon, dpi
                                    )
                                    Log.d(TAG, "gesture centroidPan dx=" + dx + " dy=" + dy +
                                        " canvas=" + canvasSize.width + "x" + canvasSize.height +
                                        " angle=" + s.viewport.angle + " mag=" + s.viewport.magnification +
                                        " center=" + s.viewport.centerLat + "," + s.viewport.centerLon +
                                        " -> " + newLat + "," + newLon)
                                    viewModel.updateCenter(newLat, newLon)
                                    gesturePan += Offset(dx, dy)
                                }

                                override fun onRotate(angleDeltaRadians: Double) {
                                    // Disengage follow mode + clear north-up immediately;
                                    // the angle is applied visually to the current bitmap
                                    // and committed on gesture end.
                                    viewModel.onManualRotationStart()
                                    gestureRotation += angleDeltaRadians.toFloat()
                                }

                                override fun onGestureCentroid(centroid: Offset) {
                                    // Clamp the pivot to the canvas: corrupted pointer
                                    // positions from multi-touch emulation would otherwise
                                    // produce a garbage zoom/rotation pivot (map swings away).
                                    val cw = canvasSize.width.toFloat()
                                    val ch = canvasSize.height.toFloat()
                                    gestureCentroid = if (cw > 0f && ch > 0f &&
                                        centroid.x.isFinite() && centroid.y.isFinite()) {
                                        Offset(centroid.x.coerceIn(0f, cw), centroid.y.coerceIn(0f, ch))
                                    } else {
                                        Offset(cw / 2f, ch / 2f)
                                    }
                                }

                                override fun onZoom(centroid: Offset, zoomFactor: Float) {
                                    // Continuous zoom factor vs gesture start; applied
                                    // visually and committed on gesture end. Clamped to
                                    // the range the commit can actually deliver: the
                                    // committed magnification is clamped to
                                    // [GESTURE_MIN_MAG, MAX_MAG], so at the limits the
                                    // visual preview must not exceed the headroom —
                                    // otherwise the map zooms in visually and then
                                    // snaps back on gesture end.
                                    gestureZoom = clampGestureVisualZoom(
                                        zoomFactor, viewModel.uiState.value.viewport.magnification
                                    )
                                }

                                override fun onLongPress(position: Offset) {
                                    fireLongPress(viewModel, context, position, canvasSize)
                                }

                                override fun onRenderRequested() {
                                    // Gesture end: commit the accumulated multi-touch
                                    // changes to the viewport and render once with the
                                    // final angle/mag/center (correct label direction).
                                    val hasMultiTouchChanges = gestureRotation != 0f ||
                                        gestureZoom != 1f || gesturePan != Offset.Zero
                                    if (hasMultiTouchChanges) {
                                        val s = viewModel.uiState.value
                                        Log.d(TAG, "gesture end rot=" + gestureRotation + " zoom=" + gestureZoom +
                                            " pan=" + gesturePan + " centroid=" + gestureCentroid +
                                            " center=" + s.viewport.centerLat + "," + s.viewport.centerLon +
                                            " mag=" + s.viewport.magnification + " angle=" + s.viewport.angle)
                                        val newAngle = normalizeRadians(s.viewport.angle + gestureRotation.toDouble())
                                        viewModel.updateAngle(newAngle)
                                        val zoomSteps = round(log2(gestureZoom.toDouble())).toInt()
                                        if (zoomSteps != 0) {
                                            val mag = s.viewport.magnification
                                            val newMag = MapCanvasViewModel.clampGestureMagnification(mag + zoomSteps)
                                            if (newMag != mag) {
                                                val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                                val (clat, clon) = ProjectionUtils.zoomAtCursor(
                                                    gestureCentroid.x.toDouble(), gestureCentroid.y.toDouble(),
                                                    mag, newMag,
                                                    canvasSize.width.toDouble(), canvasSize.height.toDouble(),
                                                    s.viewport.centerLat, s.viewport.centerLon, dpi
                                                )
                                                Log.d(TAG, "gesture commit angle=" + newAngle + " zoomSteps=" + zoomSteps +
                                                    " mag=" + mag + "->" + newMag +
                                                    " zoomAtCursor centroid=" + gestureCentroid +
                                                    " canvas=" + canvasSize.width + "x" + canvasSize.height +
                                                    " -> " + clat + "," + clon)
                                                viewModel.updateCenter(clat, clon)
                                                viewModel.updateMagnification(newMag)
                                            }
                                        }
                                        // Full native render only when the angle or mag
                                        // changed (correct label direction); a pure pan
                                        // uses the fast tile path.
                                        val needsFullRender = gestureRotation != 0f || gestureZoom != 1f
                                        gestureRotation = 0f
                                        gestureZoom = 1f
                                        gesturePan = Offset.Zero
                                        gestureCentroid = Offset.Zero
                                        viewModel.renderMap(forceFullRender = needsFullRender)
                                    }
                                }
                            }
                        )
                        // Scroll-wheel zoom (emulator/testing)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                                if (change.type == PointerType.Mouse &&
                                    event.type == PointerEventType.Scroll) {
                                    val scrollDelta = change.scrollDelta
                                    val deltaY = scrollDelta.y
                                    if (deltaY != 0f) {
                                        val s = viewModel.uiState.value
                                        val mag = s.viewport.magnification
                                        val dir = if (deltaY < 0) 1 else -1
                                        val newMag = (mag + dir).coerceIn(
                                            MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG
                                        )
                                        if (newMag != mag) {
                                            viewModel.disengageFollowMode()
                                            val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                            val (clat, clon) = ProjectionUtils.zoomAtCursor(
                                                change.position.x.toDouble(), change.position.y.toDouble(),
                                                mag, newMag,
                                                size.width.toDouble(), size.height.toDouble(),
                                                s.viewport.centerLat, s.viewport.centerLon, dpi
                                            )
                                            viewModel.updateCenter(clat, clon)
                                            viewModel.updateMagnification(newMag)
                                        }
                                    }
                                }
                            }
                        }
                        .onKeyEvent { event ->
                            when {
                                // / key: open search
                                event.type == KeyEventType.KeyUp &&
                                event.key == Key.Slash -> {
                                    showSearchPanel = true
                                    true
                                }
                                event.type == KeyEventType.KeyUp &&
                                (event.key == Key.Plus || event.key == Key.Equals) -> {
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomIn()
                                    viewModel.renderMap()
                                    true
                                }
                                event.type == KeyEventType.KeyUp &&
                                event.key == Key.Minus -> {
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomOut()
                                    viewModel.renderMap()
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    drawRect(color = surfaceColor)

                    state.renderedBitmap?.let { bitmap ->
                        // Scale bitmap to fill canvas for correct visual size
                        val scale = (canvasWidth / bitmap.width.toFloat())
                            .coerceAtLeast(canvasHeight / bitmap.height.toFloat())
                        val w = (bitmap.width * scale).toInt()
                        val h = (bitmap.height * scale).toInt()
                        val dx = ((canvasWidth - w) / 2f).toInt()
                        val dy = ((canvasHeight - h) / 2f).toInt()

                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(dx, dy),
                            dstSize = IntSize(w, h)
                        )
                    }
                }

                // GPS marker is rendered as a Compose overlay on top of the rendered map,
                // never baked into cached tiles or reusable buffers (spec: gps-location-marker).
                // Projection uses the front-buffer viewport so the marker stays anchored
                // to the bitmap actually on screen. No bitmap yet → no marker: there is no
                // displayed frame to project against.
                if (state.renderedBitmap != null) {
                    LocationMarkerOverlay(
                        lat = state.gpsMarkerLat,
                        lon = state.gpsMarkerLon,
                        bearing = state.gpsMarkerBearing,
                        accuracy = state.gpsMarkerAccuracy,
                        viewport = state.renderViewport,
                        dpi = context.resources.displayMetrics.densityDpi.toDouble()
                    )
                }
            }
        }

        // Orientation-aware overlay layout
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                // Landscape: all controls on right side (left side reserved for nav hints)
                // Top-right: menu, compass, search, favorites
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 8.dp)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    // Menu button
                    Box {
                        FilledTonalIconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Download Maps") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToMapManager()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.toggleFavoritesSheet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuExpanded = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(2.dp))

                    // Compass button
                    val compassNorthUp = if (navState.isNavigating) state.navNorthUp else state.freeFormNorthUp
                    CompassButton(
                        isNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        gpsFixQuality = state.gpsFixQuality,
                        onCenterClick = {
                            val loc = viewModel.getCurrentLocation()
                            if (loc != null) {
                                viewModel.onToggleFollowMode(true)
                                viewModel.updateCenter(loc.latitude, loc.longitude)
                            } else {
                                viewModel.showSnackbar("No GPS location available")
                            }
                        },
                        onToggleOrientation = {
                            val isNavigating = navState.isNavigating
                            if (isNavigating) {
                                viewModel.onSetNavOrientation(!state.navNorthUp)
                            } else {
                                viewModel.onSetFreeFormOrientation(!state.freeFormNorthUp)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.size(2.dp))

                    // Search + Favorites side-by-side
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorites button
                        FilledTonalIconButton(
                            onClick = { viewModel.toggleFavoritesSheet() },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorites"
                            )
                        }

                        Spacer(modifier = Modifier.size(4.dp))

                        // Search button
                        FilledTonalIconButton(
                            onClick = {
                                showSearchPanel = true
                                viewModel.onSearchPanelOpened()
                            },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search location"
                            )
                        }
                    }
                }

                // Bottom-right: location options, zoom, mylocation
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 12.dp)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    // Location options
                    LocationOptionsOverlay(
                        followMode = state.followMode,
                        onToggleFollowMode = { enabled ->
                            viewModel.onToggleFollowMode(enabled)
                        },
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
                        laneHintsEnabled = state.laneHintsEnabled,
                        onToggleLaneHints = { enabled ->
                            viewModel.onToggleLaneHints(enabled)
                        },
                        renderMode = state.renderMode,
                        onSetRenderMode = { mode ->
                            viewModel.onSetRenderMode(mode)
                        },
                        isNavigating = navState.isNavigating
                    )

                    Spacer(modifier = Modifier.size(4.dp))

                    // Zoom controls (horizontal)
                    ZoomControls(
                        canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                        canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                        currentMag = state.viewport.magnification,
                        isLandscape = true,
                        onZoomIn = {
                            android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                            viewModel.disengageFollowMode()
                            viewModel.zoomIn()
                            viewModel.renderMap()
                        },
                        onZoomOut = {
                            android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                            viewModel.disengageFollowMode()
                            viewModel.zoomOut()
                            viewModel.renderMap()
                        }
                    )

                    // Re-center button (visible when follow mode is off and GPS available)
                    if (!state.followMode && state.gpsFixQuality != GpsFixQuality.NONE) {
                        Spacer(modifier = Modifier.size(2.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val loc = viewModel.getCurrentLocation()
                                if (loc != null) {
                                    viewModel.onToggleFollowMode(true)
                                    viewModel.updateCenter(loc.latitude, loc.longitude)
                                    viewModel.renderMap()
                                } else {
                                    viewModel.showSnackbar("No GPS location available")
                                }
                            },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Re-center on location"
                            )
                        }
                    }
                }
            } else {
                // Portrait: top-right column (unchanged)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 8.dp, top = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    // Menu button
                    Box {
                        FilledTonalIconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Download Maps") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToMapManager()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.toggleFavoritesSheet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuExpanded = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }

                    // Compass button
                    val compassNorthUp = if (navState.isNavigating) state.navNorthUp else state.freeFormNorthUp
                    CompassButton(
                        isNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        gpsFixQuality = state.gpsFixQuality,
                        onCenterClick = {
                            val loc = viewModel.getCurrentLocation()
                            if (loc != null) {
                                viewModel.onToggleFollowMode(true)
                                viewModel.updateCenter(loc.latitude, loc.longitude)
                            } else {
                                viewModel.showSnackbar("No GPS location available")
                            }
                        },
                        onToggleOrientation = {
                            val isNavigating = navState.isNavigating
                            if (isNavigating) {
                                viewModel.onSetNavOrientation(!state.navNorthUp)
                            } else {
                                viewModel.onSetFreeFormOrientation(!state.freeFormNorthUp)
                            }
                        }
                    )

                    // Search button
                    FilledTonalIconButton(
                        onClick = {
                            showSearchPanel = true
                            viewModel.onSearchPanelOpened()
                        },
                        modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search location"
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))

                    // Favorites button
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleFavoritesSheet() },
                        modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorites"
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    // Location options (follow mode + orientation + auto-zoom)
                    LocationOptionsOverlay(
                        followMode = state.followMode,
                        onToggleFollowMode = { enabled ->
                            viewModel.onToggleFollowMode(enabled)
                        },
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
                        laneHintsEnabled = state.laneHintsEnabled,
                        onToggleLaneHints = { enabled ->
                            viewModel.onToggleLaneHints(enabled)
                        },
                        renderMode = state.renderMode,
                        onSetRenderMode = { mode ->
                            viewModel.onSetRenderMode(mode)
                        },
                        isNavigating = navState.isNavigating
                    )

                    Spacer(modifier = Modifier.size(4.dp))

                    // Zoom controls
                    ZoomControls(
                        canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                        canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                        currentMag = state.viewport.magnification,
                        onZoomIn = {
                            android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                            viewModel.disengageFollowMode()
                            viewModel.zoomIn()
                            viewModel.renderMap()
                        },
                        onZoomOut = {
                            android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                            viewModel.disengageFollowMode()
                            viewModel.zoomOut()
                            viewModel.renderMap()
                        }
                    )

                    // Re-center button (visible when follow mode is off and GPS available)
                    if (!state.followMode && state.gpsFixQuality != GpsFixQuality.NONE) {
                        Spacer(modifier = Modifier.size(4.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val loc = viewModel.getCurrentLocation()
                                if (loc != null) {
                                    viewModel.onToggleFollowMode(true)
                                    viewModel.updateCenter(loc.latitude, loc.longitude)
                                    viewModel.renderMap()
                                } else {
                                    viewModel.showSnackbar("No GPS location available")
                                }
                            },
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Re-center on location"
                            )
                        }
                    }
                }
            }
        }

        // Search panel overlay
        if (showSearchPanel) {
            SearchPanel(
                query = state.searchQuery,
                results = state.searchResults,
                isSearching = state.isSearching,
                gpsAvailable = state.gpsFixQuality != GpsFixQuality.NONE,
                adminRegionName = state.searchAdminRegionName,
                centerLat = state.viewport.centerLat,
                centerLon = state.viewport.centerLon,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onResultSelected = { entry ->
                    viewModel.onSearchResultSelected(entry)
                    showSearchPanel = false
                },
                onSelectCurrentLocation = {
                    viewModel.selectCurrentLocation()
                    showSearchPanel = false
                },
                onSelectFavorite = {
                    viewModel.toggleFavoritesSheet()
                    showSearchPanel = false
                },
                onSelectFromHistory = {
                    showSearchHistory = true
                },
                onDismiss = {
                    viewModel.clearSearch()
                    showSearchPanel = false
                }
            )
        }

        // Search history sheet (opened from "Select from history")
        if (showSearchHistory) {
            val history by viewModel.searchHistory.collectAsState()
            SearchHistorySheet(
                entries = history,
                onEntrySelected = { text ->
                    viewModel.onHistoryEntrySelected(text)
                    showSearchHistory = false
                },
                onDismiss = { showSearchHistory = false }
            )
        }

        // Location details sheet
        if (state.showDetailsSheet && state.selectedLocation != null) {
            LocationDetailsSheet(
                entry = state.selectedLocation!!,
                objectDescription = state.objectDescription,
                isFavorite = viewModel.isSelectedLocationFavorite(),
                groupNames = viewModel.getFavoriteGroupNames(),
                onAddToFavorites = { groupName, favName, isNewGroup ->
                    viewModel.addSelectedToFavorites(groupName, favName, isNewGroup)
                },
                onRemoveFromFavorites = { viewModel.removeSelectedFromFavorites() },
                onRouteToLocation = { viewModel.openRoutePanelWithStart(state.selectedLocation) },
                onDismiss = { viewModel.dismissDetailsSheet() }
            )
        }

        // About dialog
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
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

        // Route panel — hidden when summary dialog is shown
        if (state.showRoutePanel && !routeState.showSummaryDialog) {
            RoutePanel(
                viewModel = routePanelViewModel,
                onOpenFavoritePicker = { field ->
                    favoritePickerField = field
                    showFavoritePicker = true
                },
                onDismiss = { viewModel.dismissRoutePanel() },
                onStartNavigation = {
                    val entry = routeState.routeEntry
                    if (entry != null) {
                        navigationViewModel.startNavigation(entry, routeState.vehicle)
                        routePanelViewModel.setNavigating(true)
                    }
                },
                onStopNavigation = { navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false) },
                isNavigating = navState.isNavigating,
                centerLat = state.viewport.centerLat,
                centerLon = state.viewport.centerLon
            )
        }

        // Favorite picker dialog (for route field selection)
        if (showFavoritePicker) {
            FavoritePickerDialog(
                favoriteRepository = routePanelViewModel.favoriteRepository,
                onFavoriteSelected = { entry ->
                    when (favoritePickerField) {
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
                    favoritePickerField = null
                    showFavoritePicker = false
                },
                onDismiss = {
                    favoritePickerField = null
                    showFavoritePicker = false
                }
            )
        }

        // Permission rationale dialog
        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text("Location Permission Needed") },
                text = {
                    Text(
                        "NaviVeylin needs location access to show your position " +
                                "on the map. Please enable it in Settings."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPermissionRationale = false
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        } catch (_: Exception) {}
                    }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationale = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        // Route summary overlay — slides up from bottom, covers full screen

        // Dismiss route panel when summary dialog appears (ModalBottomSheet is separate window, always on top)
        LaunchedEffect(routeState.showSummaryDialog) {
            if (routeState.showSummaryDialog) {
                viewModel.dismissRoutePanel()
            }
        }
    
        if (routeState.showSummaryDialog && routeState.routeEntry != null) {
            RouteSummaryDialog(
                routeEntry = routeState.routeEntry!!,
                steps = routeState.routeSteps,
                activeStepIndex = if (navState.isNavigating) navState.currentStepIndex else null,
                onStartNavigation = {
                    navigationViewModel.startNavigation(routeState.routeEntry!!, routeState.vehicle)
                    routePanelViewModel.dismissSummaryDialog()
                    routePanelViewModel.setNavigating(true)
                },
                onStopNavigation = { navigationViewModel.stopNavigation() },
                isNavigating = navState.isNavigating,
                onDismiss = {
                    routePanelViewModel.dismissSummaryDialog()
                    viewModel.openRoutePanelWithStart(null)
                }
            )
        }
    
        // Navigation overlays
        if (navState.isNavigating) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
            ) {
                val buttonColumnEstimate = 64.dp
                NextTurnOverlay(
                    instruction = navState.nextInstruction,
                    laneOneway = navState.laneOneway,
                    laneCount = navState.laneCount,
                    laneSuggested = navState.laneSuggested,
                    laneSuggestedFrom = navState.laneSuggestedFrom,
                    laneSuggestedTo = navState.laneSuggestedTo,
                    laneTurns = navState.laneTurns,
                    laneHintsEnabled = state.laneHintsEnabled,
                    modifier = Modifier.widthIn(max = maxWidth - buttonColumnEstimate)
                )
            }
            NavigationStateOverlay(
                remainingDistance = navState.remainingDistance,
                etaMillis = navState.etaMillis,
                currentSpeedKmH = navState.currentSpeedKmH,
                maxSpeedKmH = navState.maxSpeedKmH,
                currentRoadInfo = navState.currentRoadInfo,
                isRerouting = navState.isRerouting,
                isOffRoute = navState.isOffRoute,
                onStopNavigation = { navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
    
private fun shouldShowRequestPermissionRationale(
    context: android.content.Context,
    permission: String
): Boolean {
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        return false
    }
    return try {
        val activity = context as androidx.activity.ComponentActivity
        activity.shouldShowRequestPermissionRationale(permission)
    } catch (_: ClassCastException) {
        false
    }
}

internal fun fireLongPress(
    viewModel: MapCanvasViewModel,
    context: android.content.Context,
    pos: androidx.compose.ui.geometry.Offset,
    size: androidx.compose.ui.unit.IntSize
) {
    val s = viewModel.uiState.value
    val dpi = context.resources.displayMetrics.densityDpi.toDouble()
    // Angle-aware conversion: the north-up screenToGeo ignores the viewport
    // rotation, so on a rotated map the resolved geo point would not be under
    // the press point. screenToGeoRotated reduces to screenToGeo at angle 0.
    val vp = ProjectionUtils.viewport(
        s.viewport.centerLat, s.viewport.centerLon,
        s.viewport.magnification,
        size.width, size.height,
        dpi,
        s.viewport.angle
    )
    val (lat, lon) = vp.screenToGeoRotated(pos.x.toDouble(), pos.y.toDouble())
    viewModel.onLongPress(lat, lon)
}

/** Minimum/maximum visual zoom factor during a multi-touch gesture (±2 mag levels). */
private const val MIN_GESTURE_ZOOM = 0.25f
private const val MAX_GESTURE_ZOOM = 4.0f

/**
 * Clamp the live visual zoom factor to the range the gesture-end commit can
 * actually deliver. The commit applies `round(log2(zoom))` magnification levels
 * clamped to [GESTURE_MIN_MAG, MAX_MAG]; at the limits the visual preview must
 * not exceed the headroom at the current magnification, or the map zooms in
 * visually and then snaps back on gesture end (e.g. at mag 20 the preview would
 * show up to 4× while the commit cannot zoom in at all).
 */
internal fun clampGestureVisualZoom(zoomFactor: Float, mag: Int): Float {
    val maxVisual = Math.pow(2.0, (MapCanvasViewModel.MAX_MAG - mag).toDouble()).toFloat()
    val minVisual = Math.pow(2.0, (MapCanvasViewModel.GESTURE_MIN_MAG - mag).toDouble()).toFloat()
    return zoomFactor.coerceIn(
        minVisual.coerceAtLeast(MIN_GESTURE_ZOOM),
        maxVisual.coerceAtMost(MAX_GESTURE_ZOOM)
    )
}

/**
 * Translation for the live multi-touch visual transform. The rotation is applied
 * around the screen center (see the graphicsLayer comment in [MapCanvasScreen])
 * while the zoom keeps its pivot at the gesture centroid; this returns the
 * translation that reconciles the two pivots: T = (1 - s)·R(θ)·(C - O) + P, where
 * O is the canvas center, C the gesture centroid, s the zoom factor, θ the
 * accumulated rotation, and P the accumulated centroid pan.
 *
 * At zoom 1 the translation reduces to the pan (pure rotation around the center);
 * at rotation 0 it reduces to the centroid-anchored zoom (the map content at the
 * centroid stays fixed, matching the committed zoomAtCursor).
 */
internal fun gestureTransformTranslation(
    rotationRadians: Float,
    zoom: Float,
    centroid: Offset,
    canvasSize: Size,
    pan: Offset
): Offset {
    val ox = canvasSize.width / 2f
    val oy = canvasSize.height / 2f
    val dx = centroid.x - ox
    val dy = centroid.y - oy
    val cosT = cos(rotationRadians)
    val sinT = sin(rotationRadians)
    return Offset(
        (1f - zoom) * (cosT * dx - sinT * dy) + pan.x,
        (1f - zoom) * (sinT * dx + cosT * dy) + pan.y
    )
}

/** Normalize an angle in degrees to [-180, 180]. */
private fun normalizeDegrees(deg: Double): Double {
    var d = deg % 360.0
    if (d > 180.0) d -= 360.0
    if (d < -180.0) d += 360.0
    return d
}

/** Normalize an angle in radians to [-π, π]. */
private fun normalizeRadians(rad: Double): Double {
    var r = rad % (2 * Math.PI)
    if (r > Math.PI) r -= 2 * Math.PI
    if (r < -Math.PI) r += 2 * Math.PI
    return r
}

/**
 * Keeps the device screen on while the app is in the foreground and [keepScreenOn]
 * is true. A lifecycle observer re-applies [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
 * on every resume (window flags can be cleared by the system while the app is
 * backgrounded) and releases it on pause; a keyed effect responds immediately to
 * setting toggles. See openspec/specs/always-on-display.
 */
@Composable
internal fun KeepScreenOnEffect(keepScreenOn: Boolean) {
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentKeepScreenOn by rememberUpdatedState(keepScreenOn)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (currentKeepScreenOn) {
                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(currentKeepScreenOn) {
        if (currentKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
