package com.naviveylin.ui.map

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naviveylin.R
import com.naviveylin.core.CarSessionPresence
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.ResolvedAnchor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.sin


/**
 * The phone map screen's file-foot helpers and on-map controls that live beside it rather than inside a
 * band: the gesture and rotation geometry helpers the screen and its tests call, [KeepScreenOnEffect],
 * [MapMenu] with the overlay button style, the action/widget/compass columns, the re-center and
 * drive-mode buttons, [LocalOverlayWidthProbe] and the car-session presence helper. Moved out of
 * `MapCanvasScreen.kt` verbatim by `2026-10-25-fix-map-canvas-screen-file-size`, with `private`
 * top-level declarations widened to `internal` — the screen and the band files call them, and a
 * `private` top-level declaration is visible to its own file only.
 */

internal fun shouldShowRequestPermissionRationale(
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
    // The press point travels with the call: the file-backed diagnostics entry records
    // the pixel and the magnification instead of a coordinate (spec: auto-diagnostics —
    // Diagnostics carry no coordinates).
    viewModel.onLongPress(lat, lon, pos.x.toInt(), pos.y.toInt())
}

/**
 * The magnification one double tap commits: one level above the level the viewport records,
 * never past [maxMag] (spec: smooth-zoom — A double tap steps one level at its own point).
 * Level-based like the zoom buttons, so a double tap after a pinch lands on a whole level
 * rather than on a fractional remainder.
 */
internal fun doubleTapZoomMagnification(current: Double, maxMag: Double): Double =
    (kotlin.math.round(current) + 1.0).coerceAtMost(maxMag)

/**
 * One double-tap zoom step at the tap point (spec: smooth-zoom — A double tap steps one level
 * at its own point). Commits the next level immediately — the viewport records the target — and
 * moves the center so the geographic point under the tap stays under the tap, exactly as a
 * scroll-wheel tick keeps the point under the cursor. [animateZoom] starts the eased transition
 * anchored on the tap point: the display never snaps, and the frame in hand is scaled around
 * that pixel while the native render at the new level runs. At the maximum magnification there
 * is no step to take, and the viewport, the center and the display stay untouched.
 */
internal fun fireDoubleTap(
    viewModel: MapCanvasViewModel,
    context: android.content.Context,
    pos: androidx.compose.ui.geometry.Offset,
    size: androidx.compose.ui.unit.IntSize,
    animateZoom: (androidx.compose.ui.geometry.Offset) -> Unit
) {
    val s = viewModel.uiState.value
    val mag = s.viewport.magnification
    val target = doubleTapZoomMagnification(mag, MapCanvasViewModel.MAX_MAG)
    if (target == mag) return
    // A double tap is a manual zoom: it leaves follow mode and suspends the drive preset
    // (spec: map-modes — drive suspension), like every other manual zoom input.
    viewModel.disengageFollowMode()
    val dpi = context.resources.displayMetrics.densityDpi.toDouble()
    val (lat, lon) = ProjectionUtils.zoomAtCursor(
        pos.x.toDouble(), pos.y.toDouble(),
        mag, target,
        size.width.toDouble(), size.height.toDouble(),
        s.viewport.centerLat, s.viewport.centerLon, dpi
    )
    viewModel.updateCenter(lat, lon)
    viewModel.updateMagnification(target)
    // The committed level is only a request: a magnification that must appear on the map goes
    // through the renderer, which is where the walk's first step is asked for and where the
    // frame that carries the new level comes from. Every other discrete zoom commit does the
    // same in the same order (zoom buttons `MapCanvasScreenChromeBand.kt`, keyboard
    // `MapCanvasMapBand.kt`, pinch end), and without it the display would ease toward a level no
    // frame carries (spec: smooth-zoom — the displayed map eases from the currently rendered scale
    // to the new level around that pixel; review round 1, finding 1).
    viewModel.renderMap()
    animateZoom(pos)
}

/** Minimum/maximum visual zoom factor during a multi-touch gesture (±2 mag levels). */
internal const val MIN_GESTURE_ZOOM = 1f / 16f
internal const val MAX_GESTURE_ZOOM = 16.0f

/**
 * Minimum interval between render requests issued because the pan display window
 * is saturated at the overrun margin (spec: canvas-overrun — Large pan triggers
 * full re-render). Mirrors the renderer's pan debounce: a fast drag re-centers at
 * the render cadence instead of queueing one render per touch event
 * (spec: render-performance — Pan hot path stays off the frame budget).
 */
internal const val PAN_RENDER_REQUEST_THROTTLE_MS = 50L

/**
 * Gesture-level pan diagnostics (spec: `map-pan-zoom` — the pan path is diagnosable
 * without per-event output; spec: `render-performance` — the bounded-diagnostics
 * carve-out). At most one line per gesture (window served / clamped / unavailable plus
 * the committed versus displayed center at lift) and one line per unservable-window
 * rejection, so the two stuck states of a dead pan are distinguishable from `adb
 * logcat` alone. The bound — not a flag — is what keeps the pan hot path clean: no
 * line is emitted per touch event. Set to false to silence the pan path entirely.
 */
internal const val PAN_DIAGNOSTICS_ENABLED = true

/**
 * Display-animation duration for auto-zoom commits (spec: smooth-zoom —
 * auto-zoom commits animate over ~650 ms, slower than discrete-input
 * 250 ms so the driving zoom glides instead of snapping — the distance-
 * proportional convergence step is additionally eased by this longer
 * animation, which together suppress the zoom "pumping" that a faster
 * animation (and a fixed-step follower) produced).
 */
internal const val AUTO_ZOOM_ANIMATION_MS = 650L

/**
 * The screen point a zoom animation keeps visually fixed (spec: smooth-zoom -
 * Geographic anchor stays fixed during zoom animation): while a follow mode is active
 * the vehicle sits at its resolved follow anchor, so the animation pivots on that pixel
 * (the marker overlay is given the same pivot) and the vehicle cannot drift off its
 * slot while the animation plays. Without a follow mode the screen center is the pivot
 * - the zoom buttons and the keyboard keep anchoring the center, the scroll wheel the
 * cursor, and a double tap the point it landed on (spec: smooth-zoom - Double tap zooms
 * one level at the tap point).
 * Extracted pure for unit testing.
 */
internal fun zoomAnimationAnchor(
    followMode: Boolean,
    anchor: ResolvedAnchor,
    canvasWidth: Int,
    canvasHeight: Int
): Offset =
    if (followMode && canvasWidth > 0 && canvasHeight > 0) {
        Offset(
            (anchor.fx * canvasWidth).toFloat(),
            (anchor.fy * canvasHeight).toFloat()
        )
    } else {
        Offset(canvasWidth / 2f, canvasHeight / 2f)
    }

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
internal fun DrawScope.drawFrontFrame(
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
 * The offset the frame on screen is drawn with: the follow drift while follow mode is active,
 * the pan overrun window otherwise — and **neither** while a route-planning session holds the
 * camera, because the session's fits place their content by the viewport alone. Leaving the follow
 * drift in place shifted the drawn frame (and the overlays, which share this offset) by up to the
 * overrun margin away from what the fit computed: the analysed leg was centred in the model and
 * off-centre or short of the bottom/a side on the device (owner finding, 2026-10-03).
 */
internal fun followDisplayOffset(
    followActive: Boolean,
    sessionOwnsViewport: Boolean,
    followOffset: Float,
    panOffset: Float
): Float = if (followActive && !sessionOwnsViewport) followOffset else panOffset

/**
 * Screen-space translation for the map-anchored overlays in the current display
 * mode (spec: gps-location-marker — Marker projects against displayed bitmap
 * viewport).
 *
 * The map content is drawn at `center − displayOffset`, so an overlay projected
 * against the frame's own viewport must be translated by the same negated offset to
 * stay on the content it rides. In follow mode the projection already uses the
 * anchor center of the displayed (predicted) position, which carries the drift —
 * translating there as well would apply it twice.
 *
 * The [panOffsetX]/[panOffsetY] passed in are the CLAMPED window offsets, so a
 * saturated pan window keeps the overlays on the content that is actually on
 * screen instead of letting them run ahead of it.
 */
internal fun markerDisplayShiftPx(
    followActive: Boolean,
    panOffsetX: Float,
    panOffsetY: Float
): Pair<Float, Float> =
    if (followActive) 0f to 0f else -panOffsetX to -panOffsetY

/**
 * Copy of an [ImageBitmap] for the render-completion crossfade: the original
 * front-buffer instance becomes the renderer's back buffer and its pixels
 * are overwritten by the next render, so the crossfade needs its own pixel
 * storage.
 */
internal fun copyImageBitmap(src: ImageBitmap): ImageBitmap =
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
internal fun normalizeDegrees(deg: Double): Double {
    var d = deg % 360.0
    if (d > 180.0) d -= 360.0
    if (d < -180.0) d += 360.0
    return d
}

/** Normalize an angle in radians to [-π, π]. */
internal fun normalizeRadians(rad: Double): Double {
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
internal val ToasterMenuOffsetY = 48.dp
internal const val MenuFadeInMs = 140
internal const val MenuScaleInMs = 180
internal const val MenuFadeOutMs = 110

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
internal fun MapOverlayIconButton(
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

/**
 * Reports the measured width (px) of an overlay cluster to the map screen, so the
 * follow anchor can stay clear of it (spec: smooth-follow — visible-area
 * scenarios). Defaults to a no-op so the cluster stays usable in isolation
 * (tests, previews).
 */
internal val LocalOverlayWidthProbe = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Compass block, shared by both orientations (spec: compass-button). */
@Composable
internal fun MapCompassBlock(
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    isDarkPresentation: Boolean,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    CompassButton(
        mapAngleRadians = mapAngleRadians,
        gpsFixQuality = gpsFixQuality,
        isDarkPresentation = isDarkPresentation,
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
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    isDarkPresentation: Boolean,
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
    /**
     * Height of the session overlay currently composed, so the column sits **above** it
     * (spec: `phone-align-controls-in-all-modes` — the right-side controls keep their stack
     * and position in every variant, and a bottom overlay never covers them; owner finding,
     * 2026-10-03: the min strip covered the zoom-out button).
     */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    // Read the probe outside the size-change lambda: a CompositionLocal can only be
    // read from composable scope.
    val measuredWidthProbe = LocalOverlayWidthProbe.current
    Column(
        modifier = modifier
            .onSizeChanged { measuredWidthProbe(it.width) }
            .padding(bottom = bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MapCompassBlock(
            mapAngleRadians = mapAngleRadians,
            gpsFixQuality = gpsFixQuality,
            isDarkPresentation = isDarkPresentation,
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

/**
 * Hilt entry point for the process-scoped car-session-presence signal (spec:
 * `car-session-presence`). The phone map UI has no view-model parameter for it
 * (the signal is not map state), so it is resolved from the application graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarSessionPresenceEntryPoint {
    fun carSessionPresence(): CarSessionPresence
}

/**
 * Resolve the car-session-presence singleton from the application graph. A
 * composable argument (not a direct call) so tests can pass their own instance.
 */
@Composable
internal fun rememberCarSessionPresence(): CarSessionPresence {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CarSessionPresenceEntryPoint::class.java
        ).carSessionPresence()
    }
}
