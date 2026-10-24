package com.naviveylin.ui.map

import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.data.ViewportStorage
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Report the regions the map's own overlays cover (px), so the anchor preset
 * resolves against the **visible map area** rather than the whole canvas
 * (spec: smooth-follow — "Bottom anchor stays visible above the routing status
 * card" and the sibling scenarios). The screen calls this from
 * `Modifier.onSizeChanged`, i.e. only on a real size change; insets that did not
 * change cause no state emission.
 */
fun MapCanvasViewModel.setMapOverlayInsets(top: Int, bottom: Int, right: Int, left: Int = 0) {
    val t = top.coerceAtLeast(0)
    val b = bottom.coerceAtLeast(0)
    val r = right.coerceAtLeast(0)
    val l = left.coerceAtLeast(0)
    if (t == overlayInsetTopPx && b == overlayInsetBottomPx &&
        r == overlayInsetRightPx && l == overlayInsetLeftPx
    ) {
        return
    }
    overlayInsetTopPx = t
    overlayInsetBottomPx = b
    overlayInsetRightPx = r
    overlayInsetLeftPx = l
    publishResolvedAnchor()
}

/** Set screen dimensions on the renderer (called from composable). */
fun MapCanvasViewModel.setScreenSize(width: Int, height: Int) {
    val wasZero = screenWidth <= 0 || screenHeight <= 0
    screenWidth = width
    screenHeight = height
    // The visible-area resolution depends on the canvas size (rotation, fold).
    publishResolvedAnchor()
    mapRenderer?.let {
        it.screenWidth = width
        it.screenHeight = height
        // If first render was skipped due to zero size, trigger it now
        if (wasZero && width > 0 && height > 0) {
            val vp = _uiState.value.viewport
            renderMap()
        }
    }
}

/** Update center latitude (called from gesture handler). */
fun MapCanvasViewModel.updateCenter(lat: Double, lon: Double) {
    // Any centre move ends the "camera is on a manoeuvre" state: a user pan and the overview
    // fit both land here (the manoeuvre focus sets the flag *after* its own call).
    manoeuvreFocusStanding = false
    // Guard against corrupted gesture math (NaN/infinity or out-of-range
    // coordinates would make every subsequent render fail). Clamp to the
    // Mercator-valid latitude range — ±90 (the poles) breaks rendering.
    if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return
    val clat = lat.coerceIn(-85.0, 85.0)
    val clon = lon.coerceIn(-180.0, 180.0)
    _uiState.value = _uiState.value.copy(
        viewport = _uiState.value.viewport.copy(centerLat = clat, centerLon = clon)
    )
}

/** Update map rotation angle (called from two-finger rotation gesture). */
fun MapCanvasViewModel.updateAngle(angleRadians: Double) {
    _uiState.value = _uiState.value.copy(
        viewport = _uiState.value.viewport.copy(angle = angleRadians)
    )
}

/**
 * Manual two-finger rotation: disengage follow mode, clear the active
 * "always north" flag, and apply the rotation delta to the viewport angle.
 * The map stays at the manually set angle afterwards.
 */
fun MapCanvasViewModel.onManualRotation(angleDeltaRadians: Double) {
    onManualRotationStart()
    updateAngle(_uiState.value.viewport.angle + angleDeltaRadians)
}

/**
 * Disengage follow mode and clear the active "always north" flag when a
 * manual rotation gesture starts. The angle itself is applied visually
 * during the gesture and committed on gesture end.
 */
fun MapCanvasViewModel.onManualRotationStart() {
    disengageFollowMode()
    when (mode) {
        MapMode.NAVIGATION, MapMode.FREE_DRIVE -> {
            if (_uiState.value.navNorthUp) {
                _uiState.value = _uiState.value.copy(navNorthUp = false)
            }
        }
        MapMode.BROWSE -> {
            if (_uiState.value.freeFormNorthUp) {
                _uiState.value = _uiState.value.copy(freeFormNorthUp = false)
            }
        }
    }
}

/** Update magnification (called from zoom controls or pinch; fractional values
 *  allowed — the pinch gesture commits continuous magnification).
 *
 *  [walk] false is for programmatic camera fits (POI fit), which land directly and
 *  do not run the display zoom animation (spec: smooth-zoom - the bounded window
 *  applies to the animated zoom input paths). */
fun MapCanvasViewModel.updateMagnification(mag: Double, walk: Boolean = true) {
    val clamped = mag.coerceIn(MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG)
    commitMagnification(clamped, walk)
    // Detect user-initiated zoom: in the driving modes suspend the drive
    // preset (auto-zoom included; spec: map-modes — drive suspension and
    // reset). In BROWSE nothing has to be recorded — the re-center button
    // follows the measured offset between the map center and the vehicle
    // (spec: map-modes — Browse re-center).
    if (mode == MapMode.FREE_DRIVE || mode == MapMode.NAVIGATION) {
        if (_uiState.value.autoZoomEnabled && !autoZoomSuspended) {
            setAutoZoomSuspended(true)
            val navVm = _navigationViewModel
            val rawSpeed = navVm?.state?.value?.currentSpeedKmH ?: Double.NaN
            lastSpeedBandIndex = if (!rawSpeed.isNaN() && rawSpeed >= 0) SpeedZoomTable.bandIndex(filterSpeed(rawSpeed)) else -1
        }
    }
}

/** Increment magnification by 1 level (snaps to the next whole level above
 *  the current fractional magnification — discrete controls stay level-based). */
fun MapCanvasViewModel.zoomIn() {
    val snapped = kotlin.math.round(_uiState.value.viewport.magnification)
    updateMagnification(snapped + 1.0)
}

/** Decrement magnification by 1 level (snaps to the whole level below). */
fun MapCanvasViewModel.zoomOut() {
    val snapped = kotlin.math.round(_uiState.value.viewport.magnification)
    updateMagnification(snapped - 1.0)
}

/**
 * Record [target] as the viewport magnification (spec: smooth-zoom - Eased zoom
 * animation on discrete zoom input). The viewport records the FINAL magnification
 * immediately; when [walk] is set and the change is farther than the window the
 * displayed frame can serve, the render pipeline advances to it in bounded steps,
 * one step per landed render ([advanceZoomWalk]).
 */
internal fun MapCanvasViewModel.commitMagnification(target: Double, walk: Boolean = true) {
    _uiState.value = _uiState.value.copy(
        viewport = _uiState.value.viewport.copy(magnification = target)
    )
    if (walk) beginZoomWalk(target) else cancelZoomWalk()
}

/**
 * Start (or re-target) a bounded magnification walk toward [target] from the frame
 * in hand. Only the walk's step is published - [MapCanvasUiState.viewport] already
 * carries the final magnification.
 */
internal fun MapCanvasViewModel.beginZoomWalk(target: Double) {
    zoomWalk.cancel()
    val frontMag = _uiState.value.renderViewport?.mag ?: 0.0
    val step = zoomWalk.request(target, frontMag)
    _uiState.value = _uiState.value.copy(
        zoomWalkStepMag = if (zoomWalk.active) step else Double.NaN
    )
    Log.d(MapCanvasViewModel.TAG, "zoomWalk request target=" + target + " step=" + step + " front=" + frontMag)
}

/** Abandon a pending walk (a commit that lands directly, e.g. a camera fit). */
internal fun MapCanvasViewModel.cancelZoomWalk() {
    if (zoomWalk.active) Log.d(MapCanvasViewModel.TAG, "zoomWalk cancelled")
    zoomWalk.cancel()
    if (!_uiState.value.zoomWalkStepMag.isNaN()) {
        _uiState.value = _uiState.value.copy(zoomWalkStepMag = Double.NaN)
    }
}

/**
 * A frame rendered at [frontMag] landed: commit the next bounded step when a walk is
 * pending (spec: smooth-zoom - the displayed frame never shows a magnification
 * farther than the frame in hand can serve). No-op without a pending walk.
 */
internal fun MapCanvasViewModel.advanceZoomWalk(frontMag: Double) {
    val next = zoomWalk.onFrameLanded(frontMag) ?: return
    // The landing step is published as the walk's last step only while the walk is
    // still pending; once it is finished the display target falls back to the
    // recorded viewport magnification (which is the same value).
    _uiState.value = _uiState.value.copy(
        zoomWalkStepMag = if (zoomWalk.active) next else Double.NaN
    )
    Log.d(MapCanvasViewModel.TAG, "zoomWalk step=" + next + " front=" + frontMag + " target=" + zoomWalk.target)
    renderMap()
}

/**
 * Applies one rendered frame to the UI state. A frame that arrives while the phone map is
 * suspended is discarded rather than published (spec: map-canvas-screen — A car session
 * suspends the phone map): a render that was in flight at the suspension edge must not put
 * a frame back into a surface that has given its storage up.
 */
@VisibleForTesting
internal suspend fun MapCanvasViewModel.applyRenderedFrame(frame: MapRenderer.FrameState) {
    val bitmap = frame.bitmap ?: return
    if (phoneMapRenderRefused) {
        Log.d(MapCanvasViewModel.TAG, "frameFlow: frame discarded, phone map suspended")
        return
    }
    // First frame: the DB may have opened after the initMap flag push (SetStyleFlag is a
    // no-op until a DB is open) — re-apply once.
    if (!stylePushedToNative) {
        stylePushedToNative = true
        ensureMapStyle(null, resolvedDaylight(), MapStyleApplyReason.FIRST_RENDERED_FRAME)
    }
    _uiState.value = _uiState.value.copy(
        renderedBitmap = bitmap.asImageBitmap(),
        isLoading = false,
        error = null,
        renderViewport = frame.viewport
        // gpsMarker* is intentionally NOT set here: the location collector updates it on
        // every fix (updateMarkerState) so the marker tracks the vehicle without waiting
        // for a render.
    )
    Log.d(MapCanvasViewModel.TAG, "frontBufferFlow: new bitmap " + bitmap.width + "x" + bitmap.height)
    // A landed frame advances a pending magnification walk (spec: smooth-zoom - one step
    // per landed render).
    advanceZoomWalk(frame.viewport.mag)
}

/**
 * Applies a car-session presence edge: a rising edge suspends the phone map, a falling
 * edge resumes it. An unchanged value is not an edge, so the release and the resume run
 * once per session rather than once per emission.
 */
internal fun MapCanvasViewModel.onCarSessionPresenceChanged(active: Boolean) {
    if (active == carSessionActive) return
    carSessionActive = active
    if (active) suspendPhoneMap() else resumePhoneMap("car session ended")
}

internal fun MapCanvasViewModel.suspendPhoneMap() {
    if (_uiState.value.phoneMapSuspended) return
    // The released frame must not stay referenced by the UI: the renderer drops its own
    // copy and the state drops the emitted one in the same transition, so nothing composes
    // a bitmap the renderer no longer owns.
    _uiState.value = _uiState.value.copy(
        phoneMapSuspended = true,
        phoneMapOverridden = false,
        renderedBitmap = null
    )
    val released = mapRenderer?.releaseRenderStorage()
    DiagnosticsLog.log(
        DiagnosticsLog.SESSION_TAG,
        "phone map suspended: car session active (presence=true, released tiles=" +
            "${released?.tilesCleared ?: 0} frameBuffers=${released?.frameBuffersReleased ?: 0} " +
            "idleTargets=${released?.idleTargetsReleased ?: 0}; shared native tile-data cache untouched)"
    )
}

/**
 * Ends the suspension and renders a fresh frame (design D5): the released frame buffers
 * leave nothing to reuse, and the frame is rendered at the CURRENT viewport and navigation
 * state — never at the state the suspension froze.
 */
internal fun MapCanvasViewModel.resumePhoneMap(reason: String) {
    val state = _uiState.value
    val wasOverridden = state.phoneMapOverridden
    if (!state.phoneMapSuspended && !wasOverridden) return
    _uiState.value = state.copy(
        phoneMapSuspended = false,
        phoneMapOverridden = false,
        renderedBitmap = null
    )
    DiagnosticsLog.log(
        DiagnosticsLog.SESSION_TAG,
        "phone map resumed: $reason (presence=$carSessionActive, override=$wasOverridden)"
    )
    if (screenWidth > 0 && screenHeight > 0) renderMap()
}

/**
 * The user asked for the phone map for the rest of the car session (spec:
 * map-canvas-screen — The user brings the map back). No-op without a suspension: the
 * override exists only to lift one, and it is cleared at session end.
 */
fun MapCanvasViewModel.showMapDuringCarSession() {
    if (!_uiState.value.phoneMapSuspended) return
    _uiState.value = _uiState.value.copy(
        phoneMapSuspended = false,
        phoneMapOverridden = true,
        renderedBitmap = null
    )
    DiagnosticsLog.log(
        DiagnosticsLog.SESSION_TAG,
        "phone map override: map requested during a car session (presence=true)"
    )
    if (screenWidth > 0 && screenHeight > 0) renderMap()
}

/** Re-render the map with current viewport via MapRenderer. */
fun MapCanvasViewModel.renderMap(forceFullRender: Boolean = false) {
    if (phoneMapRenderRefused) {
        Log.d(MapCanvasViewModel.TAG, "renderMap: refused — the phone map is suspended by a car session")
        return
    }
    val renderer = mapRenderer ?: run {
        Log.w(MapCanvasViewModel.TAG, "renderMap: mapRenderer is null — no render")
        return
    }
    val vp = _uiState.value.viewport
    // A pending walk owns the rendered magnification: the viewport already carries
    // the FINAL target, so rendering it directly would land the whole difference in
    // one frame (spec: smooth-zoom - Eased zoom animation on discrete zoom input).
    val mag = zoomWalk.renderMag(vp.magnification)
    Log.d(MapCanvasViewModel.TAG, "renderMap mag=" + mag + " (viewport " + vp.magnification + ")")
    if (zoomWalk.active && !forceFullRender) {
        // A walked step is paced by the landing of the previous step's frame, so the
        // pan/zoom debounce would only add latency (design D3). A forced overlay
        // render keeps its debounced path and its force flag.
        renderer.requestRenderImmediate(vp.centerLat, vp.centerLon, mag, vp.angle)
    } else {
        renderer.requestRender(vp.centerLat, vp.centerLon, mag, vp.angle, forceFullRender)
    }
}

/** Persist current viewport to disk. */
fun MapCanvasViewModel.saveViewport() {
    // No-op until initMap has applied the persisted viewport restore:
    // saving during the restore window would overwrite the previously
    // persisted viewport with the uninitialized default (the isValid()
    // guard in ViewportStorage passes — the default center is
    // valid-looking).
    if (!viewportRestored) {
        Log.d(MapCanvasViewModel.TAG, "saveViewport: skipped, viewport restore not yet applied")
        return
    }
    viewModelScope.launch {
        viewportStorage.save(currentMapKey ?: "default", _uiState.value.viewport)
    }
}

/** Retry after error. */
fun MapCanvasViewModel.retryRender() = renderMap()

/** Test hook: cancel all viewModel coroutines so runTest does not hang. */
@VisibleForTesting
internal fun MapCanvasViewModel.cancelScopeForTest() {
    rendererScope?.cancel()
    mapRenderer?.shutdown()
    viewModelScope.cancel()
}

/**
 * Test hook: inject a renderer without opening a map database, so the
 * route-visibility collectors (setRoutePanelViewModel) can be exercised
 * deterministically on the test scheduler.
 */
@VisibleForTesting
internal fun MapCanvasViewModel.setMapRendererForTest(renderer: MapRenderer?) {
    mapRenderer = renderer
}

/**
 * Test hook: publish a landed frame at [mag] exactly as the frame collector does
 * (render viewport + walk advance), so the bounded magnification walk can be
 * exercised deterministically without the native render pipeline.
 */
@VisibleForTesting
internal fun MapCanvasViewModel.publishRenderedFrameForTest(mag: Double) {
    val vp = _uiState.value.viewport
    val previous = _uiState.value.renderViewport
    _uiState.value = _uiState.value.copy(
        renderViewport = if (previous != null) {
            previous.copy(mag = mag)
        } else {
            MapRenderer.RenderViewport(vp.centerLat, vp.centerLon, mag, vp.angle)
        }
    )
    advanceZoomWalk(mag)
}

/**
 * Invalidates the cached tiles when the basemap data changes while the app runs.
 */
internal fun MapCanvasViewModel.wireBasemapReload() {
    // invalidate cached tiles and force a re-render so the change shows
    // without an app restart (spec: basemap-loading — current view
    // re-renders with/without the basemap overlay active).
    viewModelScope.launch {
        basemapReloadNotifier.revision.collect { revision ->
            if (revision > 0L) {
                mapRenderer?.invalidateData()
            }
        }
    }
}

/**
 * The car-session edge: one collector owns the render gate and the storage release.
 */
internal fun MapCanvasViewModel.wireCarSession() {
    // Car-session suspension (spec: map-canvas-screen — The phone map canvas is
    // suspended while a car session is active). ONE collector owns both the render gate
    // and the storage release, so "no phone renders" and "storage released" can never
    // disagree. Runs on the main dispatcher (viewModelScope): the edge writes UI state.
    viewModelScope.launch {
        carSessionPresence.active.collect { active -> onCarSessionPresenceChanged(active) }
    }
}
