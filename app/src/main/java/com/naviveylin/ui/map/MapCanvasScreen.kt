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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.naviveylin.ui.route.RouteSummaryDialog
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.PoiEntry
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.ZoomAnimation
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
    var showNavDetails by remember { mutableStateOf(false) }
    var attributionInteractionTick by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Location permission state
    var showPermissionRationale by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Address-book (contacts) permission state: one-time rationale dialog
    // before the first request (spec: address-book-permission).
    var showAddressBookRationale by remember { mutableStateOf(false) }
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
    var gestureRotation by remember { mutableStateOf(0f) }
    var gestureZoom by remember { mutableStateOf(1f) }
    // smooth-zoom fold (design D3, one displayed-scale model): when a gesture
    // starts over a parked/frozen zoom-animation display scale, that scale is
    // folded into the gesture base here — the graphicsLayer gestureZoom then
    // carries the full visual scale and the draw-layer zoomAnimScale resets to
    // 1f, so the two transforms can never double-apply (caused jumpy pinch +
    // ghost frames after a button zoom).
    var gestureBaseScale by remember { mutableStateOf(1f) }
    // Continuous-pinch input smoothing: the detector's cumulative factor is noisy
    // (emulated pinch pointers teleport → the ratio jumps between zoom levels).
    // A short exponential smoothing on the applied factor damps the spikes; commit
    // uses the DISPLAYED factor so the rendered frame always matches the preview.
    var gestureSmoothFactor by remember { mutableStateOf(1f) }
    var gesturePan by remember { mutableStateOf(Offset.Zero) }
    var gestureCentroid by remember { mutableStateOf(Offset.Zero) }

    // Follow-mode smooth scroll (spec: smooth-follow): extrapolate the displayed
    // position between 1 Hz GPS fixes and ease corrections on fix arrival. The
    // display loop runs only in follow mode while moving; the draw block applies
    // the offset within the overrun margin. Predicted positions are display-only
    // — the navigation engine keeps receiving real fixes.
    val followPrediction = remember { FollowPrediction() }
    var followActive by remember { mutableStateOf(false) }
    var followDisplayLat by remember { mutableStateOf(Double.NaN) }
    var followDisplayLon by remember { mutableStateOf(Double.NaN) }
    var followOffsetX by remember { mutableStateOf(0f) }
    var followOffsetY by remember { mutableStateOf(0f) }
    var followLogCount by remember { mutableStateOf(0) }

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
    var zoomAnimScale by remember { mutableStateOf(1f) }
    var zoomAnchor by remember { mutableStateOf(Offset.Zero) }
    var prevRenderedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastFrontMag by remember { mutableStateOf(-1.0) }
    // Crossfade at render completion (zoom-transition-scaling delta): the old
    // front-buffer copy is drawn over the swapped-in frame, fading out.
    var crossfadeBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var crossfadeScale by remember { mutableStateOf(1f) }
    var crossfadeAnchor by remember { mutableStateOf(Offset.Zero) }
    var crossfadeStartMs by remember { mutableStateOf(0L) }
    var crossfadeAlpha by remember { mutableStateOf(0f) }

    /**
     * Starts/retracks the zoom animation toward the committed magnification
     * (design D2/D3/D6). [anchor] is the screen point that must stay visually
     * fixed: screen center for buttons/keyboard, cursor for the wheel.
     */
    fun animateDiscreteZoom(anchor: Offset) {
        val s = viewModel.uiState.value
        val frontMag = s.renderViewport?.mag ?: return
        val target = 2.0.pow((s.viewport.magnification - frontMag).toDouble()).toFloat()
        val now = System.currentTimeMillis()
        if (zoomAnim.active) {
            zoomAnim.retrack(target, anchor.x, anchor.y, now)
        } else {
            zoomAnim.start(zoomAnimScale, target, anchor.x, anchor.y, now)
        }
        zoomAnchor = anchor
    }

    /** Screen center anchor for button/keyboard zoom. */
    fun animateDiscreteZoomToCenter() {
        animateDiscreteZoom(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
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

    LaunchedEffect(Unit) {
        var lastFixTime = -1L
        var lastFrameMs = 0L
        var lastRenderRequestMs = 0L
        while (isActive) {
            withFrameNanos { _ ->
                val ui = viewModel.uiState.value
                val fix = ui.gpsLocation
                val nowMs = System.currentTimeMillis()

                // smooth-zoom (spec: smooth-zoom): drive the eased animation
                // and advance the render-completion crossfade each frame.
                if (zoomAnim.active) {
                    zoomAnimScale = zoomAnim.tick(nowMs)
                }
                if (crossfadeBitmap != null) {
                    val t = (nowMs - crossfadeStartMs).toFloat() / CROSSFADE_MS
                    if (t >= 1f) {
                        crossfadeBitmap = null
                        crossfadeAlpha = 0f
                    } else {
                        crossfadeAlpha = 1f - t
                    }
                }

                // Continuous display scale: when a gesture is active and the front
                // buffer swaps mid-gesture (previous render completion), the frozen
                // base scale must be transformed to the new buffer level so the
                // composed visual (buffer × base × factor) stays continuous —
                // otherwise the committed frame replaces the buffer at a different
                // level and shows a zoom jump until render-land.
                val committedMag = ui.viewport.magnification
                val frontMag = ui.renderViewport?.mag ?: -1.0
                if (frontMag > 0 && lastFrontMag > 0 && frontMag != lastFrontMag &&
                    (gestureBaseScale != 1f || gestureZoom != 1f)) {
                    // Invert: the base represented 2^(commit − bufferOld); after the
                    // swap the new buffer carries 2^(bufferNew − bufferOld) of the
                    // visual itself, so base must be DIVIDED by the level delta
                    // (base × 2^(old − new)). When the swap is the committed render,
                    // base collapses to 1.0 (the factor alone stays in gestureZoom).
                    gestureBaseScale = (gestureBaseScale * Math.pow(
                        2.0, (lastFrontMag - frontMag)
                    )).toFloat()
                    Log.d(TAG, "smooth-zoom: gesture base rescaled to $gestureBaseScale (buffer $lastFrontMag -> $frontMag)")
                }

                // Render-completion handoff (design D4): when the front buffer
                // swaps to the committed magnification, the rendered frame
                // supplies the target zoom exactly. Animation-case (uniform
                // hold at the aligned scale) swaps immediately; any residual
                // mismatch (fractional pinch end followed within the freeze)
                // crossfades from the scaled old frame so no single-frame
                // content jump is visible (spec: zoom-transition-scaling).
                if (lastFrontMag > 0 && frontMag == committedMag &&
                    lastFrontMag != frontMag && zoomAnimScale != 1f) {
                    val hold = 2.0.pow((committedMag - lastFrontMag).toDouble()).toFloat()
                    zoomAnim.finish(nowMs)
                    if (abs(zoomAnimScale - hold) < 0.02f) {
                        // Animation already holds the frame-aligned scale —
                        // the swapped-in frame matches exactly.
                        zoomAnimScale = 1f
                    } else {
                        prevRenderedBitmap?.let { oldBitmap ->
                            crossfadeBitmap = copyImageBitmap(oldBitmap)
                            crossfadeScale = zoomAnimScale
                            crossfadeAnchor = zoomAnchor
                            crossfadeStartMs = nowMs
                            crossfadeAlpha = 1f
                        }
                        zoomAnimScale = 1f
                    }
                    Log.d(TAG, "smooth-zoom: render landed mag=$frontMag hold=$hold displayed=$zoomAnimScale crossfade=${crossfadeBitmap != null}")
                }
                lastFrontMag = frontMag
                prevRenderedBitmap = ui.renderedBitmap
                if (fix != null && fix.time != lastFixTime) {
                    // Use the receipt time (not fix.time) as the prediction base:
                    // GPS fix timestamps can be ahead of the system clock (GPS
                    // time vs UTC), which would make the extrapolation window
                    // negative and hold the position instead of scrolling.
                    followPrediction.update(
                        fix.lat, fix.lon,
                        if (fix.speedKmH.isNaN()) Double.NaN else fix.speedKmH / 3.6,
                        if (fix.smoothedBearing.isNaN()) Double.NaN else fix.smoothedBearing,
                        nowMs
                    )
                    lastFixTime = fix.time
                }
                val dtSec = if (lastFrameMs > 0) (nowMs - lastFrameMs) / 1000.0 else 0.016
                lastFrameMs = nowMs
                val moving = fix != null && !fix.speedKmH.isNaN() && fix.speedKmH > 1.8
                if (ui.followMode && moving && fix != null) {
                    val predicted = followPrediction.predictedPosition(nowMs)
                    val alpha = FollowPrediction.easeAlpha(dtSec)
                    if (followDisplayLat.isNaN()) {
                        followDisplayLat = predicted.first
                        followDisplayLon = predicted.second
                    } else {
                        followDisplayLat += (predicted.first - followDisplayLat) * alpha
                        followDisplayLon += (predicted.second - followDisplayLon) * alpha
                    }
                    followActive = true
                    // Compute the display offset against the current frame and clamp
                    // to the overrun margin; request a render when clamped so the map
                    // keeps scrolling instead of sticking at the edge.
                    val bitmap = ui.renderedBitmap
                    val vp = ui.renderViewport
                    if (bitmap != null && vp != null && canvasSize.width > 0) {
                        val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                        val offset = FollowPrediction.displayOffsetPx(
                            followDisplayLat, followDisplayLon,
                            vp.lat, vp.lon, vp.mag, vp.angle,
                            bitmap.width, bitmap.height,
                            canvasSize.width, canvasSize.height, dpi
                        )
                        followOffsetX = offset.clampedX.toFloat()
                        followOffsetY = offset.clampedY.toFloat()
                        if (offset.clamped && nowMs - lastRenderRequestMs > 500) {
                            lastRenderRequestMs = nowMs
                            viewModel.updateCenter(followDisplayLat, followDisplayLon)
                            viewModel.renderMap()
                        }
                        // Diagnostic: log the prediction state on fix arrival so a
                        // device logcat shows exactly where the overshoot comes from
                        // (fix vs predicted vs displayed vs offset).
                        if (fix.time == lastFixTime && followLogCount++ % 30 == 0) {
                            val dbg = followPrediction.debugState(nowMs)
                            Log.d(TAG, "follow t=" + fix.time +
                                " fix=" + "%.6f".format(fix.lat) + "," + "%.6f".format(fix.lon) +
                                " spd=" + (if (fix.speedKmH.isNaN()) "-" else "%.1f".format(fix.speedKmH)) +
                                " brg=" + (if (fix.smoothedBearing.isNaN()) "-" else "%.0f".format(fix.smoothedBearing)) +
                                " avg=" + "%.1f".format(dbg.avgSpeedMs * 3.6) +
                                " gps=" + (if (dbg.gpsSpeedMs.isNaN()) "-" else "%.1f".format(dbg.gpsSpeedMs * 3.6)) +
                                " eff=" + "%.1f".format(dbg.effectiveSpeedMs * 3.6) +
                                " savg=" + "%.1f".format(dbg.smoothAvgMs * 3.6) +
                                " dec=" + dbg.decelerating +
                                " stp=" + dbg.stopped +
                                " pred=" + "%.6f".format(predicted.first) + "," + "%.6f".format(predicted.second) +
                                " disp=" + "%.6f".format(followDisplayLat) + "," + "%.6f".format(followDisplayLon) +
                                " off=" + "%.1f".format(offset.clampedX) + "," + "%.1f".format(offset.clampedY) +
                                " clamped=" + offset.clamped)
                        }
                    }
                } else {
                    followActive = false
                    followDisplayLat = Double.NaN
                    followDisplayLon = Double.NaN
                    followOffsetX = 0f
                    followOffsetY = 0f
                }
            }
        }
    }

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

    // Address-book permission launcher: result updates visibility (grant ->
    // available, deny -> hidden); the rationale dialog runs before any launch.
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshAddressBookAvailability()
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
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
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
                                    attributionInteractionTick++
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
                                    attributionInteractionTick++
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
                                    attributionInteractionTick++
                                    // Disengage follow mode + clear north-up immediately;
                                    // the angle is applied visually to the current bitmap
                                    // and committed on gesture end.
                                    viewModel.onManualRotationStart()
                                    gestureRotation += angleDeltaRadians.toFloat()
                                }

                                override fun onGestureCentroid(centroid: Offset) {
                                    // smooth-zoom: a pinch gesture takes over the
                                    // zoom display (design D3) — park any running
                                    // discrete-zoom animation at its current scale.
                                    freezeZoomAnimationForGesture()
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
                                    attributionInteractionTick++
                                    // smooth-zoom: gesture takes over the zoom display.
                                    freezeZoomAnimationForGesture()
                                    // Continuous zoom factor vs gesture start; applied
                                    // visually and committed on gesture end. Clamped to
                                    // the range the commit can actually deliver: the
                                    // committed magnification is clamped to
                                    // [GESTURE_MIN_MAG, MAX_MAG], so at the limits the
                                    // visual preview must not exceed the headroom —
                                    // otherwise the map zooms in visually and then
                                    // snaps back on gesture end.
                                    // Damped visual factor: track the raw cumulative
                                    // factor with a short exponential average so noisy
                                    // input cannot teleport the zoom level mid-gesture.
                                    // The commit keeps visual == commit parity.
                                    val rawFactor = clampGestureVisualZoom(
                                        zoomFactor, viewModel.uiState.value.viewport.magnification
                                    )
                                    gestureSmoothFactor = if (gestureSmoothFactor == 1f) {
                                        rawFactor
                                    } else {
                                        gestureSmoothFactor +
                                            (rawFactor - gestureSmoothFactor) * 0.4f
                                    }
                                    gestureZoom = gestureBaseScale * gestureSmoothFactor
                                }

                                override fun onLongPress(position: Offset) {
                                    fireLongPress(viewModel, context, position, canvasSize)
                                }

                                override fun onRenderRequested() {
                                    attributionInteractionTick++
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
                                        // continuous-pinch-zoom: commit the unrounded
                                        // fractional magnification — the visual preview
                                        // factor already equals the committed factor within
                                        // the headroom clamp, so no snap at gesture end.
                                        // Commit uses the detector's cumulative factor only —
                                        // the folded base scale is already committed in
                                        // viewport.magnification (visual continuity: display
                                        // = front buffer × gestureBaseScale × factor).
                                        val detectorFactor = gestureZoom / gestureBaseScale
                                        val newMag = gestureEndMagnification(s.viewport.magnification, detectorFactor)
                                        if (abs(newMag - s.viewport.magnification) > 1e-6) {
                                                val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                                val (clat, clon) = ProjectionUtils.zoomAtCursor(
                                                    gestureCentroid.x.toDouble(), gestureCentroid.y.toDouble(),
                                                    s.viewport.magnification, newMag,
                                                    canvasSize.width.toDouble(), canvasSize.height.toDouble(),
                                                    s.viewport.centerLat, s.viewport.centerLon, dpi
                                                )
                                                Log.d(TAG, "gesture commit angle=" + newAngle +
                                                    " zoomFactor=" + detectorFactor +
                                                    " mag=" + s.viewport.magnification + "->" + newMag +
                                                    " zoomAtCursor centroid=" + gestureCentroid +
                                                    " canvas=" + canvasSize.width + "x" + canvasSize.height +
                                                    " -> " + clat + "," + clon)
                                                viewModel.updateCenter(clat, clon)
                                                viewModel.updateMagnification(newMag)
                                        }
                                        // Full native render only when the angle or mag
                                        // changed (correct label direction); a pure pan
                                        // uses the fast tile path.
                                        // Fold the composed gesture scale into the
                                        // display layer: the screen keeps showing
                                        // frontBuffer × gestureZoom (the exact gesture
                                        // preview) while the debounced render at the
                                        // fractional commit runs — the render-land handoff
                                        // resets the display scale when the buffer matches
                                        // (smooth-zoom D3/D4, no zoom-level snap between
                                        // gesture end and render completion).
                                        zoomAnchor = gestureCentroid
                                        zoomAnimScale = gestureZoom
                                        val needsFullRender = gestureRotation != 0f || gestureZoom != 1f
                                        gestureRotation = 0f
                                        gestureZoom = 1f
                                        gestureBaseScale = 1f
                                        gestureSmoothFactor = 1f
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
                                        attributionInteractionTick++
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
                                            // smooth-zoom: animate the discrete zoom
                                            // anchored at the cursor position.
                                            animateDiscreteZoom(change.position)
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
                                    attributionInteractionTick++
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomIn()
                                    viewModel.renderMap()
                                    animateDiscreteZoomToCenter()
                                    true
                                }
                                event.type == KeyEventType.KeyUp &&
                                event.key == Key.Minus -> {
                                    attributionInteractionTick++
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomOut()
                                    viewModel.renderMap()
                                    animateDiscreteZoomToCenter()
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
                        // Overrun-sized frames (follow mode) are drawn at natural
                        // size with the follow offset applied within the margin;
                        // screen-sized frames keep the scale-to-fill behavior.
                        drawFrontFrame(
                            bitmap, canvasWidth.toFloat(), canvasHeight.toFloat(),
                            followOffsetX, followOffsetY,
                            zoomAnimScale, zoomAnchor, 1f
                        )
                    }

                    // smooth-zoom render-completion crossfade (zoom-transition-
                    // scaling delta): the scaled old frame fades out over the
                    // swapped-in rendered frame — no single-frame content jump.
                    crossfadeBitmap?.let { old ->
                        if (crossfadeAlpha > 0f) {
                            drawFrontFrame(
                                old, canvasWidth.toFloat(), canvasHeight.toFloat(),
                                followOffsetX, followOffsetY,
                                crossfadeScale, crossfadeAnchor, crossfadeAlpha
                            )
                        }
                    }
                }

                // GPS marker is rendered as a Compose overlay on top of the rendered map,
                // never baked into cached tiles or reusable buffers (spec: gps-location-marker).
                // Projection uses the front-buffer viewport so the marker stays anchored
                // to the bitmap actually on screen. No bitmap yet → no marker: there is no
                // displayed frame to project against.
                if (state.renderedBitmap != null) {
                    // In follow mode the marker rides the displayed (eased predicted)
                    // position so it glides with the blitted map; the viewport is
                    // centered on the same position so the marker lands on the road.
                    val markerLat = if (followActive) followDisplayLat else state.gpsMarkerLat
                    val markerLon = if (followActive) followDisplayLon else state.gpsMarkerLon
                    val markerViewport = if (followActive) {
                        MapRenderer.RenderViewport(
                            followDisplayLat, followDisplayLon,
                            state.renderViewport?.mag ?: 0.0, state.renderViewport?.angle ?: 0.0
                        )
                    } else {
                        state.renderViewport
                    }
                    LocationMarkerOverlay(
                        lat = markerLat,
                        lon = markerLon,
                        bearing = state.gpsMarkerBearing,
                        accuracy = state.gpsMarkerAccuracy,
                        viewport = markerViewport,
                        dpi = context.resources.displayMetrics.densityDpi.toDouble(),
                        zoomScale = zoomAnimScale,
                        zoomAnchor = zoomAnchor
                    )
                }
            }
        }

        // Orientation-aware overlay layout
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isLandscape = maxWidth > maxHeight
            val compassNorthUp = if (navState.isNavigating) state.navNorthUp else state.freeFormNorthUp
            val reCenterAction = {
                val loc = viewModel.getCurrentLocation()
                if (loc != null) {
                    viewModel.onToggleFollowMode(true)
                    viewModel.updateCenter(loc.lat, loc.lon)
                    viewModel.renderMap()
                } else {
                    viewModel.showSnackbar("No GPS location available")
                }
            }

            if (isLandscape) {
                // Landscape: action buttons top-left, state controls on right
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
                        onToggleMenu = { menuExpanded = true },
                        onOpenSearch = {
                            showSearchPanel = true
                            viewModel.onSearchPanelOpened()
                        },
                        onToggleFavorites = { viewModel.toggleFavoritesSheet() }
                    )
                }

                // Top-right: compass (view indicator)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 8.dp)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    MapCompassBlock(
                        isNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        gpsFixQuality = state.gpsFixQuality,
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
                            if (navState.isNavigating) {
                                viewModel.onSetNavOrientation(!state.navNorthUp)
                            } else {
                                viewModel.onSetFreeFormOrientation(!state.freeFormNorthUp)
                            }
                        }
                    )
                }

                // Bottom-right: location options + zoom (view controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 44.dp)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    MapLocationZoomBlock(
                        isLandscape = true,
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
                        availableStyles = state.availableStyleSheets,
                        styleSheet = state.styleSheet,
                        onSetStyleSheet = { style ->
                            viewModel.onStyleSheetSelected(style)
                        },
                        isNavigating = navState.isNavigating,
                        canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                        canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                        currentMag = state.viewport.magnification,
                        onZoomIn = {
                            android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                            attributionInteractionTick++
                            viewModel.disengageFollowMode()
                            viewModel.zoomIn()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        },
                        onZoomOut = {
                            android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                            attributionInteractionTick++
                            viewModel.disengageFollowMode()
                            viewModel.zoomOut()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        }
                    )
                }

                // Bottom-left: re-center (action) when follow off + GPS available
                if (!state.followMode && state.gpsFixQuality != GpsFixQuality.NONE) {
                    MapReCenterButton(
                        onReCenter = reCenterAction,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 12.dp)
                            .navigationBarsPadding()
                    )
                }
            } else {
                // Portrait: action column top-left, view column top-right
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
                        onToggleMenu = { menuExpanded = true },
                        onOpenSearch = {
                            showSearchPanel = true
                            viewModel.onSearchPanelOpened()
                        },
                        onToggleFavorites = { viewModel.toggleFavoritesSheet() }
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 8.dp, top = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    MapCompassBlock(
                        isNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        gpsFixQuality = state.gpsFixQuality,
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
                            if (navState.isNavigating) {
                                viewModel.onSetNavOrientation(!state.navNorthUp)
                            } else {
                                viewModel.onSetFreeFormOrientation(!state.freeFormNorthUp)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.size(4.dp))

                    MapLocationZoomBlock(
                        isLandscape = false,
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
                        availableStyles = state.availableStyleSheets,
                        styleSheet = state.styleSheet,
                        onSetStyleSheet = { style ->
                            viewModel.onStyleSheetSelected(style)
                        },
                        isNavigating = navState.isNavigating,
                        canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                        canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                        currentMag = state.viewport.magnification,
                        onZoomIn = {
                            android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                            attributionInteractionTick++
                            viewModel.disengageFollowMode()
                            viewModel.zoomIn()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        },
                        onZoomOut = {
                            android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                            attributionInteractionTick++
                            viewModel.disengageFollowMode()
                            viewModel.zoomOut()
                            viewModel.renderMap()
                            animateDiscreteZoomToCenter()
                        }
                    )
                }

                // Bottom-left: re-center (action) when follow off + GPS available
                if (!state.followMode && state.gpsFixQuality != GpsFixQuality.NONE) {
                    MapReCenterButton(
                        onReCenter = reCenterAction,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 12.dp)
                            .navigationBarsPadding()
                    )
                }
            }

            // Animated map menu overlay (scrim + panel), anchored below the toaster button
            MapMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onDownloadMaps = { onNavigateToMapManager() },
                onOpenFavorites = { viewModel.toggleFavoritesSheet() },
                onOpenPoiSearch = { viewModel.openPoiSearch() },
                onOpenAddressBook = { viewModel.openAddressBookSheet() },
                onOpenAbout = { showAboutDialog = true },
                addressBookAvailable = state.addressBookAvailable,
                toasterTopPadding = if (isLandscape) 8.dp else 4.dp
            )
        }

        // OSM attribution notice (bottom-right corner, per OSMF Attribution
        // Guidelines). Auto-hides after 5s of no interaction; any map
        // interaction re-shows it and restarts the timer.
        OsmAttributionOverlay(
            interactionTick = attributionInteractionTick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp)
                .navigationBarsPadding()
        )

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

        // POI search sheet
        if (state.poiSearchOpen) {
            PoiSearchPanel(
                category = state.poiCategory,
                radiusMeters = state.poiRadiusMeters,
                results = state.poiResults,
                isSearching = state.isPoiSearching,
                error = state.poiSearchError,
                client = viewModel.osmscoutClient,
                centerLat = state.poiSearchCenterLat,
                centerLon = state.poiSearchCenterLon,
                currentPosition = if (state.gpsMarkerLat.isNaN()) null else state.gpsMarkerLat to state.gpsMarkerLon,
                selectedPoi = if (state.poiSelectedLat.isNaN()) null else state.poiSelectedLat to state.poiSelectedLon,
                onCategorySelected = { viewModel.onPoiCategorySelected(it) },
                onRadiusChanged = { viewModel.onPoiRadiusChanged(it) },
                onSearch = { viewModel.performPoiSearch() },
                onEntryClick = { viewModel.onPoiEntryClick(it) },
                onDismiss = { viewModel.closePoiSearch() }
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

        // Address-book person search sheet (full-screen)
        if (state.showAddressBookSheet) {
            com.naviveylin.ui.addressbook.AddressBookSheet(
                onDismiss = { viewModel.closeAddressBookSheet() },
                onResultSelected = { entry -> viewModel.onAddressBookResultSelected(entry) }
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
        // Address-book (contacts) rationale dialog — shown once before the
        // first READ_CONTACTS request (spec: address-book-permission).
        if (showAddressBookRationale) {
            com.naviveylin.ui.addressbook.AddressBookRationaleDialog(
                onDismiss = {
                    showAddressBookRationale = false
                    addressBookRationaleStore.markShown()
                },
                onContinue = {
                    showAddressBookRationale = false
                    addressBookRationaleStore.markShown()
                    try {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    } catch (_: Exception) {}
                },
                onNotNow = {
                    showAddressBookRationale = false
                    addressBookRationaleStore.markShown()
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
                    .padding(start = ActionColumnInset)
            ) {
                NextTurnOverlay(
                    instruction = navState.nextInstruction,
                    laneOneway = navState.laneOneway,
                    laneCount = navState.laneCount,
                    laneSuggested = navState.laneSuggested,
                    laneSuggestedFrom = navState.laneSuggestedFrom,
                    laneSuggestedTo = navState.laneSuggestedTo,
                    laneTurns = navState.laneTurns,
                    laneHintsEnabled = state.laneHintsEnabled,
                    modifier = Modifier.widthIn(max = maxWidth - ViewColumnReserve)
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
                onClick = { showNavDetails = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Expanded routing status details — full-screen route description
        if (showNavDetails && navState.isNavigating) {
            NavigationDetailsOverlay(
                instructions = navState.instructions,
                currentStepIndex = navState.currentStepIndex,
                currentRoadInfo = navState.currentRoadInfo,
                remainingDistance = navState.remainingDistance,
                etaMillis = navState.etaMillis,
                currentSpeedKmH = navState.currentSpeedKmH,
                maxSpeedKmH = navState.maxSpeedKmH,
                onStopNavigation = {
                    navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false)
                },
                onDismiss = { showNavDetails = false }
            )
        }

        // Reset the expanded view when navigation ends
        LaunchedEffect(navState.isNavigating) {
            if (!navState.isNavigating) showNavDetails = false
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
private const val MIN_GESTURE_ZOOM = 1f / 16f
private const val MAX_GESTURE_ZOOM = 16.0f

/** Duration of the render-completion crossfade in ms (smooth-zoom / zoom-transition-scaling delta). */
private const val CROSSFADE_MS = 150.0f

/**
 * Draw one front-buffer frame (main display or crossfade copy) with the
 * smooth-zoom display scale applied around [zoomAnchor] (design D5: scale
 * around anchor, then follow offset — the anchor point stays visually fixed
 * while the animation plays).
 *
 * Overrun-sized frames (follow mode) are drawn at natural size with the
 * follow offset applied within the margin; screen-sized frames keep the
 * scale-to-fill behavior (unchanged from the pre-smooth-zoom draw path).
 */
private fun DrawScope.drawFrontFrame(
    bitmap: ImageBitmap,
    canvasWidth: Float,
    canvasHeight: Float,
    followOffsetX: Float,
    followOffsetY: Float,
    zoomScale: Float,
    zoomAnchor: Offset,
    alpha: Float
) {
    val overrun = bitmap.width > canvasWidth || bitmap.height > canvasHeight
    val baseScale = if (overrun) 1f else
        (canvasWidth / bitmap.width.toFloat())
            .coerceAtLeast(canvasHeight / bitmap.height.toFloat())
    val w = (bitmap.width * baseScale).toInt()
    val h = (bitmap.height * baseScale).toInt()
    val offsetX = if (overrun) followOffsetX else 0f
    val offsetY = if (overrun) followOffsetY else 0f
    val dx = ((canvasWidth - w) / 2f).toInt() - offsetX.toInt()
    val dy = ((canvasHeight - h) / 2f).toInt() - offsetY.toInt()

    if (zoomScale == 1f) {
        drawImage(image = bitmap, dstOffset = IntOffset(dx, dy), dstSize = IntSize(w, h), alpha = alpha)
    } else {
        withTransform({ scale(zoomScale, zoomScale, pivot = zoomAnchor) }) {
            drawImage(image = bitmap, dstOffset = IntOffset(dx, dy), dstSize = IntSize(w, h), alpha = alpha)
        }
    }
}

/**
 * Copy of an [ImageBitmap] for the render-completion crossfade: the original
 * front-buffer instance becomes the renderer's back buffer and its pixels
 * are overwritten by the next render, so the crossfade needs its own pixel
 * storage.
 */
private fun copyImageBitmap(src: ImageBitmap): ImageBitmap =
    src.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true).asImageBitmap()

/**
 * Clamp the live visual zoom factor to the range the gesture-end commit can
 * actually deliver. The commit applies `round(log2(zoom))` magnification levels
 * clamped to [GESTURE_MIN_MAG, MAX_MAG]; at the limits the visual preview must
 * not exceed the headroom at the current magnification, or the map zooms in
 * visually and then snaps back on gesture end (e.g. at mag 20 the preview would
 * show up to 4× while the commit cannot zoom in at all).
 */
internal fun clampGestureVisualZoom(zoomFactor: Float, mag: Double): Float {
    val maxVisual = Math.pow(2.0, (MapCanvasViewModel.MAX_MAG - mag).toDouble()).toFloat()
    val minVisual = Math.pow(2.0, (MapCanvasViewModel.GESTURE_MIN_MAG - mag).toDouble()).toFloat()
    return zoomFactor.coerceIn(
        minVisual.coerceAtLeast(MIN_GESTURE_ZOOM),
        maxVisual.coerceAtMost(MAX_GESTURE_ZOOM)
    )
}

/**
 * Magnification committed at pinch gesture end (continuous-pinch-zoom, spec
 * map-pan-zoom): the unrounded fractional target `start + log2(factor)`, the
 * same clamp the visual preview uses so commit == preview even at limits.
 * Extracted pure for unit testing.
 */
internal fun gestureEndMagnification(mag: Double, gestureZoom: Float): Double =
    MapCanvasViewModel.clampGestureMagnification(
        mag + log2(gestureZoom.toDouble())
    )

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

/** Vertical offset (dp) between the toaster button and the menu popup. */
private val ToasterMenuOffsetY = 48.dp
private const val MenuFadeInMs = 140
private const val MenuScaleInMs = 180
private const val MenuFadeOutMs = 110

/** Left inset reserved for the action button column; nav hints start after it. */
internal val ActionColumnInset = 64.dp

/** Right-side width reserved for the view button column. */
internal val ViewColumnReserve = 64.dp

/**
 * Map screen menu (toaster). Shared by the landscape and portrait layouts so
 * the POI search entry stays in sync and the menu is testable in isolation.
 * Rendered as an overlay in the main window below the toaster button — a
 * full-screen tap scrim dismisses it, and it animates in/out with a Material 3
 * fade + scale. Drawn in the main window (not a Popup) so the exit animation
 * reliably completes and the menu always closes on dismissal (spec: map-menu).
 */
@Composable
internal fun MapMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownloadMaps: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPoiSearch: () -> Unit,
    onOpenAddressBook: () -> Unit,
    onOpenAbout: () -> Unit,
    addressBookAvailable: Boolean,
    toasterTopPadding: Dp
) {
    BackHandler(enabled = expanded) { onDismiss() }
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(MenuFadeInMs)) +
            scaleIn(initialScale = 0.9f, animationSpec = tween(MenuScaleInMs)),
        exit = fadeOut(tween(MenuFadeOutMs)) +
            scaleOut(targetScale = 0.9f, animationSpec = tween(MenuFadeOutMs))
    ) {
        Box(Modifier.fillMaxSize()) {
            // Full-screen scrim: tapping anywhere outside the menu dismisses it
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Surface(
                shape = MenuDefaults.shape,
                color = MenuDefaults.containerColor,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = toasterTopPadding + ToasterMenuOffsetY)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DropdownMenuItem(
                        text = { Text("Download Maps") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onDownloadMaps()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Favorites") },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenFavorites()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.poi_search_title)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenPoiSearch()
                        }
                    )
                    // Address book entry — only while READ_CONTACTS is granted
                    // (spec: address-book-permission — visibility).
                    if (addressBookAvailable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.address_book_menu_title)) },
                            leadingIcon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                            onClick = {
                                onDismiss()
                                onOpenAddressBook()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("About") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenAbout()
                        }
                    )
                }
            }
        }
    }
}

/** Shared overlay icon button style (shadow + shape) for all map controls. */
@Composable
private fun MapOverlayIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.shadow(3.dp, RoundedCornerShape(16.dp))
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription)
    }
}

/**
 * Left action column: toaster (menu) button, search, and favorites. Search +
 * favorites sit side by side in landscape (favorites left), stacked in portrait
 * (spec: map-canvas-screen, landscape-layout).
 */
@Composable
private fun MapActionColumn(
    isLandscape: Boolean,
    onToggleMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFavorites: () -> Unit
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Toaster (menu) button
        MapOverlayIconButton(
            imageVector = Icons.Default.Menu,
            contentDescription = "Menu",
            onClick = onToggleMenu
        )

        Spacer(modifier = Modifier.size(2.dp))

        if (isLandscape) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MapOverlayIconButton(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorites",
                    onClick = onToggleFavorites
                )
                Spacer(modifier = Modifier.size(4.dp))
                MapOverlayIconButton(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search location",
                    onClick = onOpenSearch
                )
            }
        } else {
            MapOverlayIconButton(
                imageVector = Icons.Default.Search,
                contentDescription = "Search location",
                onClick = onOpenSearch
            )
            Spacer(modifier = Modifier.size(4.dp))
            MapOverlayIconButton(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorites",
                onClick = onToggleFavorites
            )
        }
    }
}

/** Compass block, shared by both orientations (spec: compass-button). */
@Composable
private fun MapCompassBlock(
    isNorthUp: Boolean,
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    CompassButton(
        isNorthUp = isNorthUp,
        mapAngleRadians = mapAngleRadians,
        gpsFixQuality = gpsFixQuality,
        onCenterClick = onCenterClick,
        onToggleOrientation = onToggleOrientation
    )
}

/** Location options + zoom controls block, shared by both orientations. */
@Composable
private fun MapLocationZoomBlock(
    isLandscape: Boolean,
    followMode: Boolean,
    onToggleFollowMode: (Boolean) -> Unit,
    freeFormNorthUp: Boolean,
    onSetFreeFormOrientation: (Boolean) -> Unit,
    navNorthUp: Boolean,
    onSetNavOrientation: (Boolean) -> Unit,
    autoZoomEnabled: Boolean,
    onToggleAutoZoom: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    darkModePreference: DarkModePreference,
    onSetDarkModePreference: (DarkModePreference) -> Unit,
    laneHintsEnabled: Boolean,
    onToggleLaneHints: (Boolean) -> Unit,
    renderMode: RenderMode,
    onSetRenderMode: (RenderMode) -> Unit,
    availableStyles: List<String>,
    styleSheet: String,
    onSetStyleSheet: (String) -> Unit,
    isNavigating: Boolean,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    currentMag: Double,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    LocationOptionsOverlay(
        followMode = followMode,
        onToggleFollowMode = onToggleFollowMode,
        freeFormNorthUp = freeFormNorthUp,
        onSetFreeFormOrientation = onSetFreeFormOrientation,
        navNorthUp = navNorthUp,
        onSetNavOrientation = onSetNavOrientation,
        autoZoomEnabled = autoZoomEnabled,
        onToggleAutoZoom = onToggleAutoZoom,
        keepScreenOn = keepScreenOn,
        onToggleKeepScreenOn = onToggleKeepScreenOn,
        darkModePreference = darkModePreference,
        onSetDarkModePreference = onSetDarkModePreference,
        laneHintsEnabled = laneHintsEnabled,
        onToggleLaneHints = onToggleLaneHints,
        renderMode = renderMode,
        onSetRenderMode = onSetRenderMode,
        availableStyles = availableStyles,
        styleSheet = styleSheet,
        onSetStyleSheet = onSetStyleSheet,
        isNavigating = isNavigating
    )

    Spacer(modifier = Modifier.size(4.dp))

    ZoomControls(
        canZoomIn = canZoomIn,
        canZoomOut = canZoomOut,
        currentMag = currentMag,
        isLandscape = isLandscape,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut
    )
}

/** Re-center (MyLocation) button, shown at bottom-left when follow mode is off. */
@Composable
private fun MapReCenterButton(
    onReCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapOverlayIconButton(
        imageVector = Icons.Default.MyLocation,
        contentDescription = "Re-center on location",
        onClick = onReCenter,
        modifier = modifier
    )
}
