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
import androidx.compose.ui.unit.dp
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
import com.naviveylin.ui.route.RouteSummaryDialog
import com.naviveylin.ui.route.elapsedTimePercent
import com.naviveylin.ui.route.routeProgressPercent
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

    // Surface route-calc failures while navigating (spec: reroute-route-visibility):
    // the route panel is hidden during navigation, so the in-panel error text is
    // not visible — show a snackbar instead. Non-navigation failures keep the
    // in-panel error display and do not snackbar.
    LaunchedEffect(routePanelViewModel) {
        routePanelViewModel.routeErrorEvent.collect { message ->
            if (navigationViewModel.state.value.isNavigating) {
                viewModel.showSnackbar(message)
            }
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
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
    var gesturePivot by remember { mutableStateOf(Offset.Zero) }
    // True while a multi-touch gesture is in progress (first onGestureCentroid
    // → onRenderRequested): gates the one-time pivot freeze at gesture start.
    var gestureActive by remember { mutableStateOf(false) }
    // Gesture-end midpoint (captured in onRenderRequested before the gesture
    // state resets): the rotation hold and the render-land crossfade pivot
    // around this point — the same focal point the commit (D2) uses, so the
    // held/crossfaded old frame aligns with the committed render.
    var crossfadePivot by remember { mutableStateOf(Offset.Zero) }
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
    var rotationHoldActive by remember { mutableStateOf(false) }
    // Rotation render-land crossfade: the old rotated frame fades into the new
    // native render (masking any tile-vs-native rasterization difference at the
    // swap) — mirror of the zoom crossfade below.
    var crossfadeAngle by remember { mutableStateOf(0f) }
    var lastFrontAngle by remember { mutableStateOf(Double.NaN) }

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
     * fixed: screen center for buttons/keyboard/auto-zoom, cursor for the
     * wheel. Auto-zoom commits pass a longer duration (spec: smooth-zoom —
     * auto-zoom commits animate at ~500 ms).
     */
    fun animateDiscreteZoom(anchor: Offset, durationMs: Long = 250L) {
        val s = viewModel.uiState.value
        val frontMag = s.renderViewport?.mag ?: return
        val target = 2.0.pow((s.viewport.magnification - frontMag).toDouble()).toFloat()
        val now = System.currentTimeMillis()
        if (zoomAnim.active) {
            zoomAnim.retrack(target, anchor.x, anchor.y, now, durationMs)
        } else {
            zoomAnim.start(zoomAnimScale, target, anchor.x, anchor.y, now, durationMs)
        }
        zoomAnchor = anchor
    }

    /** Screen center anchor for button/keyboard zoom and auto-zoom commits. */
    fun animateDiscreteZoomToCenter(durationMs: Long = 250L) {
        animateDiscreteZoom(Offset(canvasSize.width / 2f, canvasSize.height / 2f), durationMs)
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
                        crossfadeAngle = 0f
                        crossfadePivot = Offset.Zero
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
                // Rotation render-land (design D1): when the front buffer swaps to
                // the committed angle while the rotation hold is armed, disarm the
                // hold and crossfade the old rotated frame into the new native
                // render — masks any tile-vs-native rasterization difference at the
                // swap (mirror of the zoom crossfade above). The old frame is drawn
                // rotated by (committed − old front-buffer angle) so it aligns with
                // the new frame while fading out.
                val frontAngle = ui.renderViewport?.angle
                if (rotationHoldActive && frontAngle != null &&
                    frontAngle == ui.viewport.angle && lastFrontAngle != frontAngle) {
                    prevRenderedBitmap?.let { oldBitmap ->
                        if (crossfadeBitmap == null) {
                            crossfadeBitmap = copyImageBitmap(oldBitmap)
                            crossfadeScale = 1f
                            crossfadeAnchor = Offset(
                                canvasSize.width / 2f, canvasSize.height / 2f
                            )
                            crossfadeStartMs = nowMs
                            crossfadeAlpha = 1f
                        }
                        crossfadeAngle = (ui.viewport.angle - lastFrontAngle).toFloat()
                    }
                    rotationHoldActive = false
                    Log.d(TAG, "rotation: render landed angle=${Math.toDegrees(frontAngle)} crossfade=${crossfadeBitmap != null}")
                }
                lastFrontMag = frontMag
                lastFrontAngle = frontAngle ?: lastFrontAngle
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
                if (ui.followMode && fix != null && !fix.speedKmH.isNaN() && fix.speedKmH > 1.8) {
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Orientation for the whole screen (spec: landscape-layout); the
        // overlay layout below re-derives it from its own constraints.
        val isLandscape = maxWidth > maxHeight

        // Snackbar at bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
        val compassNorthUp = when (viewModel.mode) {
            MapMode.NAVIGATION, MapMode.FREE_DRIVE -> state.navNorthUp
            MapMode.BROWSE -> state.freeFormNorthUp
        }
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
                        text = state.error ?: stringResource(R.string.unknown_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.retryRender() }) {
                        Text(stringResource(R.string.retry))
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
                        // The rotation AND zoom pivot at the finger midpoint
                        // (gesturePivot, FROZEN at the gesture-start midpoint): the
                        // geographic point under the midpoint at gesture start stays
                        // under the midpoint for the whole gesture, and the
                        // gesture-end commit adjusts the viewport center so the
                        // anchor holds in the rendered frame (design D1/D2, spec:
                        // Rotation anchored at the finger midpoint). Rotating around
                        // an off-center pivot can expose empty corners at large
                        // angles (accepted — the overrun buffer margin covers
                        // moderate angles; standard map-app behavior).
                        .graphicsLayer {
                            // Rotation display-layer hold (design D1): between gesture
                            // end and render land the display keeps the final angle by
                            // rotating the current front buffer by the angle gap
                            // (committed − front-buffer) — derived from the SAME
                            // collected state the draw reads, so the rotation zeroes
                            // atomically with the committed bitmap swap (no one-frame
                            // double-rotation twist). rotationDisplayTheta is pure.
                            val theta = rotationDisplayTheta(
                                gestureRotation, rotationHoldActive,
                                state.renderViewport?.angle, state.viewport.angle
                            )
                            val s = gestureZoom
                            // Pivot: the frozen gesture-start midpoint during the
                            // gesture; the gesture-end midpoint (crossfadePivot) while
                            // the rotation hold is armed, so the held rotation and the
                            // render-land crossfade pivot around the commit's focal
                            // point (D2) — the same point the committed render rotates
                            // around.
                            val pivot = if (rotationHoldActive && crossfadePivot != Offset.Zero) {
                                crossfadePivot
                            } else {
                                gesturePivot
                            }
                            val t = gestureTransformTranslation(
                                theta, s, pivot, size, gesturePan
                            )
                            translationX = t.x
                            translationY = t.y
                            rotationZ = normalizeDegrees(Math.toDegrees(theta.toDouble())).toFloat()
                            scaleX = s
                            scaleY = s
                            // Pivot at the finger midpoint (fraction of the layer
                            // size); fall back to the screen center when unset.
                            val pw = size.width
                            val ph = size.height
                            transformOrigin = if (pw > 0f && ph > 0f &&
                                pivot.x.isFinite() && pivot.y.isFinite() &&
                                (pivot != Offset.Zero || theta != 0f)
                            ) {
                                TransformOrigin(
                                    (pivot.x / pw).coerceIn(0f, 1f),
                                    (pivot.y / ph).coerceIn(0f, 1f)
                                )
                            } else {
                                TransformOrigin(0.5f, 0.5f)
                            }
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
                                    // A new gesture supersedes any held rotation from
                                    // the previous one (design D1 fallback).
                                    rotationHoldActive = false
                                    // Disengage follow mode + clear north-up immediately;
                                    // the angle is applied visually to the current bitmap
                                    // and committed on gesture end.
                                    viewModel.onManualRotationStart()
                                    gestureRotation += angleDeltaRadians.toFloat()
                                }

                                override fun onGestureCentroid(centroid: Offset) {
                                    // A new gesture supersedes any held rotation from the
                                    // previous one (design D1 fallback).
                                    rotationHoldActive = false
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
                                    // Rotation/zoom pivot = the finger midpoint
                                    // (design D1), FROZEN at the FIRST midpoint of
                                    // the gesture: a live pivot re-anchors the
                                    // accumulated rotation/zoom each frame and jumps
                                    // the map by (I − s·R(θ))·ΔC (archived
                                    // 2026-08-15-bugfix-rotation defect). The
                                    // centroid drift is carried by the pan
                                    // compensation (gestureTransformTranslation).
                                    if (!gestureActive) {
                                        gesturePivot = gestureCentroid
                                        gestureActive = true
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
                                        val rotationChanged = gestureRotation != 0f
                                        val zoomChanged = abs(newMag - s.viewport.magnification) > 1e-6
                                        if (rotationChanged || zoomChanged) {
                                                val dpi = context.resources.displayMetrics.densityDpi.toDouble()
                                                // Generalized focal-point commit (design D2, spec:
                                                // Rotation anchored at the finger midpoint): the
                                                // viewport center is adjusted so the geo point under
                                                // the finger midpoint stays under it after the
                                                // combined rotate+zoom. Reduces to zoomAtCursor for
                                                // a pure zoom (Δ=0); a pure rotation now also moves
                                                // the center.
                                                val (clat, clon) = ProjectionUtils.rotateZoomAtFocalPoint(
                                                    gestureCentroid.x.toDouble(), gestureCentroid.y.toDouble(),
                                                    s.viewport.magnification, newMag,
                                                    gestureRotation.toDouble(),
                                                    canvasSize.width.toDouble(), canvasSize.height.toDouble(),
                                                    s.viewport.centerLat, s.viewport.centerLon,
                                                    s.viewport.angle, dpi
                                                )
                                                Log.d(TAG, "gesture commit angle=" + newAngle +
                                                    " zoomFactor=" + detectorFactor +
                                                    " mag=" + s.viewport.magnification + "->" + newMag +
                                                    " focal=" + gestureCentroid +
                                                    " canvas=" + canvasSize.width + "x" + canvasSize.height +
                                                    " -> " + clat + "," + clon)
                                                viewModel.updateCenter(clat, clon)
                                                if (zoomChanged) viewModel.updateMagnification(newMag)
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
                                        // Rotation display-layer hold (design D1): arm the
                                        // derived rotation gap (committed − front-buffer
                                        // angle) until the re-render at the committed angle
                                        // lands — the frame loop disarms it when the front
                                        // buffer matches (mirror of the zoomAnimScale
                                        // handoff).
                                        rotationHoldActive = gestureRotation != 0f
                                        // Gesture-end midpoint: the hold and the
                                        // render-land crossfade pivot around this
                                        // point (the commit's focal point, D2).
                                        crossfadePivot = gestureCentroid
                                        gestureRotation = 0f
                                        gestureZoom = 1f
                                        gestureBaseScale = 1f
                                        gestureSmoothFactor = 1f
                                        gesturePan = Offset.Zero
                                        gestureCentroid = Offset.Zero
                                        gestureActive = false
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
                            dispatchMapCanvasKey(
                                event = event,
                                onOpenSearch = { viewModel.openSearch() },
                                onZoomIn = {
                                    attributionInteractionTick++
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomIn()
                                    viewModel.renderMap()
                                    animateDiscreteZoomToCenter()
                                },
                                onZoomOut = {
                                    attributionInteractionTick++
                                    viewModel.disengageFollowMode()
                                    viewModel.zoomOut()
                                    viewModel.renderMap()
                                    animateDiscreteZoomToCenter()
                                }
                            )
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
                                crossfadeScale, crossfadeAnchor, crossfadeAlpha,
                                crossfadeAngle, crossfadePivot
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
                            onToggleMenu = { menuExpanded = true },
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
                        compassNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        bearingDegrees = state.gpsMarkerBearing,
                        gpsFixQuality = state.gpsFixQuality,
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
                                renderMode = state.renderMode,
                                onSetRenderMode = { mode ->
                                    viewModel.onSetRenderMode(mode)
                                },
                                availableStyles = state.availableStyleSheets,
                                styleSheet = state.styleSheet,
                                onSetStyleSheet = { style ->
                                    viewModel.onStyleSheetSelected(style)
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
                        viewModel.mode, state.driveSuspended, state.browseDrifted
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
                            onToggleMenu = { menuExpanded = true },
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
                        compassNorthUp = compassNorthUp,
                        mapAngleRadians = state.viewport.angle,
                        bearingDegrees = state.gpsMarkerBearing,
                        gpsFixQuality = state.gpsFixQuality,
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
                                renderMode = state.renderMode,
                                onSetRenderMode = { mode ->
                                    viewModel.onSetRenderMode(mode)
                                },
                                availableStyles = state.availableStyleSheets,
                                styleSheet = state.styleSheet,
                                onSetStyleSheet = { style ->
                                    viewModel.onStyleSheetSelected(style)
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
                        viewModel.mode, state.driveSuspended, state.browseDrifted
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

            // Animated map menu overlay (scrim + panel), anchored below the toaster button
            MapMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onDownloadMaps = { onNavigateToMapManager() },
                onOpenFavorites = { viewModel.toggleFavoritesSheet() },
                onOpenSearch = { viewModel.openSearch() },
                onOpenAbout = { showAboutDialog = true },
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
                centerLat = state.viewport.centerLat,
                centerLon = state.viewport.centerLon,
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
                        // Close the routing window when navigation starts.
                        viewModel.dismissRoutePanel()
                    }
                },
                onStopNavigation = { navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false)
                    routePanelViewModel.clearRouteFromMap() },
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
                title = { Text(stringResource(R.string.location_permission_needed)) },
                text = {
                    Text(
                        stringResource(R.string.location_permission_rationale)
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
                        Text(stringResource(R.string.open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationale = false }) {
                        Text(stringResource(R.string.cancel))
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
                onStopNavigation = { navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false)
                    routePanelViewModel.clearRouteFromMap() },
                isNavigating = navState.isNavigating,
                onDismiss = {
                    routePanelViewModel.dismissSummaryDialog()
                    viewModel.openRoutePanelWithStart(null)
                }
            )
        }
    
        // Free-driving street label (spec: current-road-info): bottom-center
        // pill with the road's ref + name, shown only when no route is active
        // (the navigation road-info row covers the navigating case).
        if (!navState.isNavigating) {
            val roadText = state.currentRoadInfo?.let {
                listOfNotNull(
                    it.ref.takeIf { r -> r.isNotEmpty() },
                    it.name.takeIf { n -> n.isNotEmpty() }
                ).joinToString(" ")
            }
            if (!roadText.isNullOrEmpty()) {
                StreetNamePill(
                    text = roadText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }

        // Navigation overlays: full-width turn hints at the top, the right-side
        // widget column (compass directly above the speed widget, zoom at the
        // bottom below all other controls) spanning from above the routing status
        // up to the top, and the routing status covering the bottom of the window.
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
                    modifier = Modifier.fillMaxWidth()
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
                            compassNorthUp = compassNorthUp,
                            mapAngleRadians = state.viewport.angle,
                            bearingDegrees = state.gpsMarkerBearing,
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
                                    renderMode = state.renderMode,
                                    onSetRenderMode = { mode ->
                                        viewModel.onSetRenderMode(mode)
                                    },
                                    availableStyles = state.availableStyleSheets,
                                    styleSheet = state.styleSheet,
                                    onSetStyleSheet = { style ->
                                        viewModel.onStyleSheetSelected(style)
                                    }
                                )
                            },
                            canZoomIn = state.viewport.magnification < MapCanvasViewModel.MAX_MAG,
                            canZoomOut = state.viewport.magnification > MapCanvasViewModel.MIN_MAG,
                            currentMag = state.viewport.magnification,
                            onZoomIn = {
                                android.util.Log.d("MapCanvasScreen", "zoom+ pressed")
                                attributionInteractionTick++
                                viewModel.zoomIn()
                                viewModel.renderMap()
                                animateDiscreteZoomToCenter()
                            },
                            onZoomOut = {
                                android.util.Log.d("MapCanvasScreen", "zoom- pressed")
                                attributionInteractionTick++
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
                            viewModel.mode, state.driveSuspended, state.browseDrifted
                        ) && state.gpsFixQuality != GpsFixQuality.NONE) {
                        MapReCenterButton(
                            onReCenter = reCenterAction,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                }
                // Bottom: routing status, full width
                NavigationStateOverlay(
                    remainingDistance = navState.remainingDistance,
                    etaMillis = navState.etaMillis,
                    currentRoadInfo = navState.currentRoadInfo,
                    distanceProgressPercent = if (navState.isNavigating) {
                        routeProgressPercent(navState.totalDistance, navState.remainingDistance)
                    } else null,
                    timeProgressPercent = if (navState.isNavigating) {
                        elapsedTimePercent(
                            start = navState.navigationStartTimeMillis,
                            eta = navState.etaMillis,
                            now = System.currentTimeMillis()
                        )
                    } else null,
                    isRerouting = navState.isRerouting,
                    isOffRoute = navState.isOffRoute,
                    onStopNavigation = { navigationViewModel.stopNavigation()
                        routePanelViewModel.setNavigating(false)
                        routePanelViewModel.clearRouteFromMap() },
                    onClick = { showNavDetails = true }
                )
            }
        }

        // Expanded routing status details — full-screen route description
        if (showNavDetails && navState.isNavigating) {
            NavigationDetailsOverlay(
                instructions = navState.instructions,
                currentStepIndex = navState.currentStepIndex,
                currentRoadInfo = navState.currentRoadInfo,
                remainingDistance = navState.remainingDistance,
                etaMillis = navState.etaMillis,
                onStopNavigation = {
                    navigationViewModel.stopNavigation()
                    routePanelViewModel.setNavigating(false)
                    routePanelViewModel.clearRouteFromMap()
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
 * Display-animation duration for auto-zoom commits (spec: smooth-zoom —
 * auto-zoom commits animate over ~650 ms, slower than discrete-input
 * 250 ms so the driving zoom glides instead of snapping — the distance-
 * proportional convergence step is additionally eased by this longer
 * animation, which together suppress the zoom "pumping" that a faster
 * animation (and a fixed-step follower) produced).
 */
private const val AUTO_ZOOM_ANIMATION_MS = 650L

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
    alpha: Float,
    rotationDegrees: Float = 0f,
    rotationPivot: Offset = Offset(canvasWidth / 2f, canvasHeight / 2f)
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

    if (zoomScale == 1f && rotationDegrees == 0f) {
        drawImage(image = bitmap, dstOffset = IntOffset(dx, dy), dstSize = IntSize(w, h), alpha = alpha)
    } else {
        // Rotation about the gesture midpoint (design D1 — the render-land
        // crossfade must align the old rotated frame with the committed render,
        // which pivots around the same midpoint).
        withTransform({
            if (zoomScale != 1f) scale(zoomScale, zoomScale, pivot = zoomAnchor)
            if (rotationDegrees != 0f) rotate(rotationDegrees, pivot = rotationPivot)
        }) {
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
 * Rotation display-layer hold angle (design D1, spec map-rotation-gesture —
 * No temporary angle jump on gesture end): between gesture end and the
 * re-render landing, the display keeps the final angle by rotating the current
 * front buffer by the angle gap (committed − front-buffer angle). The gap is
 * derived from the same state the draw reads so it zeroes atomically with the
 * committed bitmap swap — no one-frame double-rotation twist when the render
 * lands, and the held value always matches what the new render will show
 * (wrapping-safe: rotating by the gap ≡ rotating by the accumulated gesture
 * rotation). Extracted pure for unit testing.
 */
internal fun rotationDisplayTheta(
    gestureRotation: Float,
    holdActive: Boolean,
    frontAngle: Double?,
    committedAngle: Double
): Float {
    if (!holdActive) return gestureRotation
    val gap = if (frontAngle == null) 0.0 else committedAngle - frontAngle
    return gestureRotation + gap.toFloat()
}

/**
 * Translation for the live multi-touch visual transform. The rotation AND zoom
 * pivot at the finger midpoint ([pivot], the graphicsLayer transformOrigin —
 * design D1, spec map-rotation-gesture: Rotation anchored at the finger
 * midpoint), so the pivot-reconciliation term of the old screen-center model
 * vanishes and the translation reduces to the pan compensation only.
 *
 * The Compose `graphicsLayer` drives an Android RenderNode whose matrix is
 * `M = T(pivot)·S·R·T(−pivot)·T(translation)` — the translation is applied in the
 * layer's LOCAL (pre-scale/rotate) space, so it is scaled by `s` and rotated by
 * `θ`. Solving `p' = P + S·R·(p − P + T)` for the desired visual
 * `p' = D + P + S·R·(p − P)` (rotate and scale around the pivot P, pan by the
 * screen-space centroid movement D) gives:
 *
 *     T = (1/s)·R(−θ)·D
 *
 * The model requires a FIXED pivot: the caller freezes [pivot] at the
 * gesture-start midpoint (a live pivot re-anchors the accumulated rotation
 * around the new origin each frame and jumps the map by `(I − s·R(θ))·ΔC`).
 * [pivot] and [canvasSize] are kept for call-site clarity (the pivot is the
 * transformOrigin; the formula does not depend on it).
 */
internal fun gestureTransformTranslation(
    rotationRadians: Float,
    zoom: Float,
    pivot: Offset,
    canvasSize: Size,
    pan: Offset
): Offset {
    val cosT = cos(rotationRadians)
    val sinT = sin(rotationRadians)
    val invZoom = 1f / zoom
    // R(−θ)·D: the screen-space pan rotated into the local (pre-scale) frame.
    val pdx = cosT * pan.x + sinT * pan.y
    val pdy = -sinT * pan.x + cosT * pan.y
    return Offset(
        invZoom * pdx,
        invZoom * pdy
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
    onOpenSearch: () -> Unit,
    onOpenAbout: () -> Unit,
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
                        text = { Text(stringResource(R.string.download_maps)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onDownloadMaps()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorites)) },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenFavorites()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_menu_title)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenSearch()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.about)) },
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
internal fun MapActionColumn(
    isLandscape: Boolean,
    onToggleMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFavorites: () -> Unit
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Toaster (menu) button
        MapOverlayIconButton(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(R.string.menu),
            onClick = onToggleMenu
        )

        Spacer(modifier = Modifier.size(2.dp))

        if (isLandscape) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MapOverlayIconButton(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.favorites),
                    onClick = onToggleFavorites
                )
                Spacer(modifier = Modifier.size(4.dp))
                MapOverlayIconButton(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_location),
                    onClick = onOpenSearch
                )
            }
        } else {
            MapOverlayIconButton(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_location),
                onClick = onOpenSearch
            )
            Spacer(modifier = Modifier.size(4.dp))
            MapOverlayIconButton(
                imageVector = Icons.Default.Favorite,
                contentDescription = stringResource(R.string.favorites),
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
    bearingDegrees: Double?,
    gpsFixQuality: GpsFixQuality,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    CompassButton(
        isNorthUp = isNorthUp,
        mapAngleRadians = mapAngleRadians,
        bearingDegrees = bearingDegrees,
        gpsFixQuality = gpsFixQuality,
        onCenterClick = onCenterClick,
        onToggleOrientation = onToggleOrientation
    )
}

/**
 * Right-side widget column shared by the standard (free-form) view and the
 * routing view (spec: phone-align-controls-in-all-modes): compass directly
 * above the speed widget, optional location-options slot, then zoom controls
 * at the bottom below all other controls. Positioning (anchoring, insets,
 * scrolling) is applied by the caller via [modifier].
 */
@Composable
internal fun MapRightWidgetColumn(
    isLandscape: Boolean,
    compassNorthUp: Boolean,
    mapAngleRadians: Double,
    bearingDegrees: Double? = null,
    gpsFixQuality: GpsFixQuality,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit,
    speedInput: SpeedWidgetInput?,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    currentMag: Double,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    locationOptions: (@Composable () -> Unit)? = null,
    driveToggle: (@Composable () -> Unit)? = null,
    reserveSpeedSlot: Boolean = false,
    overspeedWarningDeltaKmh: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MapCompassBlock(
            isNorthUp = compassNorthUp,
            mapAngleRadians = mapAngleRadians,
            bearingDegrees = bearingDegrees,
            gpsFixQuality = gpsFixQuality,
            onCenterClick = onCenterClick,
            onToggleOrientation = onToggleOrientation
        )
        if (driveToggle != null) {
            Spacer(modifier = Modifier.size(8.dp))
            driveToggle()
        }
        if (speedInput != null || reserveSpeedSlot) {
            Spacer(modifier = Modifier.size(8.dp))
            SpeedWidget(
                currentSpeedKmH = speedInput?.currentSpeedKmH ?: Double.NaN,
                maxSpeedKmH = speedInput?.maxSpeedKmH ?: Double.NaN,
                reserveLimitSpace = reserveSpeedSlot,
                reserveSlotWhenHidden = reserveSpeedSlot,
                overspeedWarningDeltaKmh = overspeedWarningDeltaKmh
            )
        }
        if (locationOptions != null) {
            Spacer(modifier = Modifier.size(4.dp))
            locationOptions()
        }
        Spacer(modifier = Modifier.size(8.dp))
        ZoomControls(
            canZoomIn = canZoomIn,
            canZoomOut = canZoomOut,
            currentMag = currentMag,
            isLandscape = isLandscape,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )
    }
}

/** Re-center (MyLocation) button, shown at bottom-left when follow mode is off. */
@Composable
internal fun MapReCenterButton(
    onReCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapOverlayIconButton(
        imageVector = Icons.Default.MyLocation,
        contentDescription = stringResource(R.string.recenter_on_location),
        onClick = onReCenter,
        modifier = modifier
    )
}

/**
 * Drive mode toggle (spec: map-modes — mode toggle button): a dedicated
 * control in the right-side widget column, car icon in BROWSE (tap →
 * FREE_DRIVE), exit icon in FREE_DRIVE (tap → BROWSE). Hidden during
 * NAVIGATION.
 */
@Composable
internal fun DriveModeButton(
    mode: MapMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mode == MapMode.NAVIGATION) return
    val isDriving = mode == MapMode.FREE_DRIVE
    MapOverlayIconButton(
        imageVector = if (isDriving) Icons.Default.Stop else Icons.Default.DirectionsCar,
        contentDescription = stringResource(
            if (isDriving) R.string.exit_free_drive else R.string.start_free_drive
        ),
        onClick = onToggle,
        modifier = modifier
    )
}
