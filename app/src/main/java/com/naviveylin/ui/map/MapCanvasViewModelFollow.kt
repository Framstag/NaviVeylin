package com.naviveylin.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naviveylin.R
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.FreeDrivingStatusProvider
import com.naviveylin.core.SpeedZoomTable
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.core.anchorCenter
import com.naviveylin.core.resolveAnchorFraction
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import com.naviveylin.core.FixFreshness
import com.naviveylin.core.SpeedStaleness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Follow render center: the displayed position once the display loop runs, else the marker fix. */
internal fun MapCanvasViewModel.followRenderCenterLat(): Double =
    if (!followDisplayLat.isNaN()) followDisplayLat else smoothedCenterLat

internal fun MapCanvasViewModel.followRenderCenterLon(): Double =
    if (!followDisplayLon.isNaN()) followDisplayLon else smoothedCenterLon

/**
 * Resolve the active preset into the visible map area and publish it as
 * [MapCanvasUiState.resolvedAnchor] (single source used by the render target and
 * the marker projection). Called whenever the preset, the canvas size or the
 * overlay insets change.
 */
internal fun MapCanvasViewModel.publishResolvedAnchor(
    anchor: VehicleAnchorPosition = _uiState.value.activeFollowAnchor
) {
    val resolved = resolveAnchorFraction(
        anchor,
        leftPx = overlayInsetLeftPx,
        topPx = overlayInsetTopPx,
        rightPx = overlayInsetRightPx,
        bottomPx = overlayInsetBottomPx,
        screenW = screenWidth,
        screenH = screenHeight
    )
    if (resolved != _uiState.value.resolvedAnchor) {
        _uiState.value = _uiState.value.copy(resolvedAnchor = resolved)
    }
}

/**
 * Anchor-centered render target for [lat]/[lon] under the active follow
 * anchor (spec: smooth-follow — Anchor-centered follow framing): the
 * viewport center that projects the position to the *resolved* anchor screen
 * fraction (visible-area mapped, see [publishResolvedAnchor]) under [angle].
 * The anchor is applied HERE and nowhere else — the follow blit then carries
 * the prediction drift only. Falls back to the raw position while the canvas
 * size is unknown.
 */
internal fun MapCanvasViewModel.followRenderTarget(
    lat: Double, lon: Double, mag: Double, angle: Double
): Pair<Double, Double> {
    if (screenWidth <= 0 || screenHeight <= 0) return lat to lon
    val anchor = _uiState.value.resolvedAnchor
    return anchorCenter(
        lat, lon, anchor.fx, anchor.fy, mag,
        screenWidth, screenHeight, projectionDpi, normalizeAngle(angle)
    )
}

/** Keep the internal suspension flag and its uiState mirror in sync. */
internal fun MapCanvasViewModel.setAutoZoomSuspended(suspended: Boolean) {
    autoZoomSuspended = suspended
    _uiState.value = _uiState.value.copy(driveSuspended = suspended)
}

/** Get the current navigation position for marker rendering. */
fun MapCanvasViewModel.getNavigationPosition(): com.framstag.libosmscout.client.NavigationPosition? = _navPosition

/**
 * Filter speed spikes: reject speed above the plausibility ceiling. An unknown speed stays unknown
 * (NaN) rather than falling back to a remembered value (spec: `auto-speed-zoom` — Auto-zoom speed
 * source per surface): the seed this used to hold pinned the phone's free-driving zoom target to
 * the 20 km/h level, so a free drive at 90+ km/h never zoomed out.
 */
internal fun MapCanvasViewModel.filterSpeed(rawSpeedKmH: Double): Double {
    if (rawSpeedKmH >= 0 && rawSpeedKmH <= MapCanvasViewModel.MAX_PLAUSIBLE_SPEED_KMH) {
        lastValidSpeedKmH = rawSpeedKmH
    }
    return lastValidSpeedKmH
}

/**
 * Push the marker state to the renderer (for the next render job) AND to the
 * overlay state directly. The overlay must show the latest fix immediately —
 * waiting for the next rendered frame would lag the marker behind the vehicle
 * (throttled renders, no render on < 5 m movement). The overlay projects the
 * marker against the displayed frame's viewport, so a fresh fix on a stale map
 * is still anchored correctly.
 */
internal fun MapCanvasViewModel.updateMarkerState(lat: Double, lon: Double, bearing: Double, accuracy: Double) {
    mapRenderer?.setGpsMarkerState(lat, lon, bearing, accuracy)
    _uiState.value = _uiState.value.copy(
        gpsMarkerLat = lat,
        gpsMarkerLon = lon,
        gpsMarkerBearing = bearing,
        gpsMarkerAccuracy = accuracy
    )
}

/** Compute turn zoom boost floor: 16.0 if ≤ 2000m, 15.0 if ≤ 5000m, 0.0 otherwise. */
internal fun MapCanvasViewModel.computeTurnBoost(turnDistanceMeters: Double): Double {
    if (turnDistanceMeters < 0 || turnDistanceMeters.isNaN()) return 0.0
    return when {
        turnDistanceMeters <= 2000.0 -> 16.0
        turnDistanceMeters <= 5000.0 -> 15.0
        else -> 0.0
    }
}

/**
 * Find nearest strong curve in route geometry ahead of current position.
 * Returns distance in meters to the curve, or NaN if none found within lookAhead.
 */
internal fun MapCanvasViewModel.findNearestCurve(lat: Double, lon: Double, lookAheadMeters: Double = 500.0): Double {
    val rLats = routeLats ?: return Double.NaN
    val rLons = routeLons ?: return Double.NaN
    if (rLats.size < 3) return Double.NaN

    // Find closest point on route to current position
    var closestIdx = 0
    var closestDist = Double.MAX_VALUE
    for (i in rLats.indices) {
        val dlat = lat - rLats[i]
        val dlon = lon - rLons[i]
        val dist = dlat * dlat + dlon * dlon
        if (dist < closestDist) {
            closestDist = dist
            closestIdx = i
        }
    }

    // Look ahead for bearing changes > 30 degrees
    var accumulatedDist = 0.0
    for (i in closestIdx until rLats.size - 2) {
        val lat1 = rLats[i]
        val lon1 = rLons[i]
        val lat2 = rLats[i + 1]
        val lon2 = rLons[i + 1]
        val lat3 = rLats[i + 2]
        val lon3 = rLons[i + 2]

        val segDist = haversine(lat1, lon1, lat2, lon2)
        accumulatedDist += segDist
        if (accumulatedDist > lookAheadMeters) break

        val bearing1 = Math.toDegrees(Math.atan2(
            Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2)),
            Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
            Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1))
        ))

        val bearing2 = Math.toDegrees(Math.atan2(
            Math.sin(Math.toRadians(lon3 - lon2)) * Math.cos(Math.toRadians(lat3)),
            Math.cos(Math.toRadians(lat2)) * Math.sin(Math.toRadians(lat3)) -
            Math.sin(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lat3)) * Math.cos(Math.toRadians(lon3 - lon2))
        ))

        var delta = Math.abs(bearing1 - bearing2)
        if (delta > 180) delta = 360 - delta

        if (delta > 30.0) {
            return accumulatedDist
        }
    }

    return Double.NaN
}

/** Haversine distance between two coordinates in meters. */
internal fun MapCanvasViewModel.haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dlat = Math.toRadians(lat2 - lat1)
    val dlon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dlon / 2) * Math.sin(dlon / 2)
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/**
 * Smooth the GPS center. With alpha 1.0 the center follows the raw fix
 * directly so the marker never drifts away from the road/track. For jumps
 * larger than CENTER_SMOOTH_MAX_JUMP_M the new fix is used directly so the
 * map does not lag after a teleport / first fix.
 */
internal fun MapCanvasViewModel.smoothCenter(newLat: Double, newLon: Double): Pair<Double, Double> {
    if (smoothedCenterLat.isNaN()) {
        smoothedCenterLat = newLat
        smoothedCenterLon = newLon
        return Pair(newLat, newLon)
    }
    val dist = distanceMeters(newLat, newLon, smoothedCenterLat, smoothedCenterLon)
    if (dist > centerSmoothMaxJumpM) {
        smoothedCenterLat = newLat
        smoothedCenterLon = newLon
        return Pair(newLat, newLon)
    }
    smoothedCenterLat = smoothedCenterLat * (1.0 - centerSmoothAlpha) + newLat * centerSmoothAlpha
    smoothedCenterLon = smoothedCenterLon * (1.0 - centerSmoothAlpha) + newLon * centerSmoothAlpha
    return Pair(smoothedCenterLat, smoothedCenterLon)
}

/** Compare two angles in radians, tolerating wrap-around and floating-point noise. */
internal fun MapCanvasViewModel.isAngleSame(a: Double, b: Double): Boolean {
    return kotlin.math.abs(normalizeAngle(a - b)) < 1e-4
}

/**
 * Whether turn-by-turn route guidance is currently active — selects the
 * routing vs free-driving vehicle anchor.
 */
internal fun MapCanvasViewModel.isNavigatingNow(): Boolean =
    _navigationViewModel?.state?.value?.isNavigating == true

/**
 * Set the routing vehicle anchor **for the phone** (spec: location-options-ui —
 * vehicle position controls). Persists through the shared settings storage in the
 * PHONE's own field, so a picker change here never alters Android Auto's anchor
 * (that one is configured on the car).
 */
fun MapCanvasViewModel.setRoutingAnchor(anchor: VehicleAnchorPosition) {
    routingAnchor = anchor
    _uiState.value = _uiState.value.copy(
        routingAnchor = anchor,
        activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
    )
    publishResolvedAnchor()
    viewModelScope.launch {
        settingsStorage.update { it.copy(routingAnchorId = anchor.id) }
    }
}

/**
 * Set the free-driving vehicle anchor **for the phone** (spec:
 * location-options-ui — vehicle position controls). Persists in the phone's own
 * field; Android Auto keeps its own value.
 */
fun MapCanvasViewModel.setFreeDrivingAnchor(anchor: VehicleAnchorPosition) {
    freeDrivingAnchor = anchor
    _uiState.value = _uiState.value.copy(
        freeDrivingAnchor = anchor,
        activeFollowAnchor = if (isNavigatingNow()) routingAnchor else freeDrivingAnchor
    )
    publishResolvedAnchor()
    viewModelScope.launch {
        settingsStorage.update { it.copy(freeDrivingAnchorId = anchor.id) }
    }
}

internal fun MapCanvasViewModel.distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = (sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2))
        .coerceIn(0.0, 1.0)
    return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Resolve the max speed of the road at [lat]/[lon] for the follow-mode
 * speed widget (spec: map-speed-widget). Throttled: re-resolves only after
 * significant movement or a cooldown, off the main thread. getMaxSpeedAt
 * returns a negative value when the road has no limit → NaN (unknown).
 */
internal fun MapCanvasViewModel.resolveMaxSpeed(lat: Double, lon: Double) {
    val now = System.currentTimeMillis()
    val moved = lastMaxSpeedLat.isNaN() ||
        distanceMeters(lastMaxSpeedLat, lastMaxSpeedLon, lat, lon) > MapCanvasViewModel.MAX_SPEED_RESOLVE_MOVE_M
    val cooldownElapsed = now - lastMaxSpeedResolveMs >= MapCanvasViewModel.MAX_SPEED_RESOLVE_INTERVAL_MS
    if (!moved && !cooldownElapsed) return
    lastMaxSpeedLat = lat
    lastMaxSpeedLon = lon
    lastMaxSpeedResolveMs = now
    viewModelScope.launch(defaultDispatcher) {
        val maxSpeed = try {
            client.getMaxSpeedAt(lat, lon)
        } catch (e: Exception) {
            Log.w(MapCanvasViewModel.TAG, "getMaxSpeedAt failed", e)
            Double.NaN
        }
        _uiState.value = _uiState.value.copy(
            maxSpeedKmH = maxSpeed.takeIf { it > 0.0 } ?: Double.NaN
        )
    }
}

/**
 * Resolve the road at [lat]/[lon] for the free-driving street label
 * (spec: current-road-info — current road shown when no route is active).
 * Bearing-aware native lookup, throttled like [resolveMaxSpeed]: re-resolves
 * only after significant movement or a cooldown, off the main thread.
 */
internal fun MapCanvasViewModel.resolveCurrentRoad(lat: Double, lon: Double, bearing: Double) {
    val now = System.currentTimeMillis()
    val moved = lastRoadLat.isNaN() ||
        distanceMeters(lastRoadLat, lastRoadLon, lat, lon) > MapCanvasViewModel.ROAD_RESOLVE_MOVE_M
    val cooldownElapsed = now - lastRoadResolveMs >= MapCanvasViewModel.ROAD_RESOLVE_INTERVAL_MS
    if (!moved && !cooldownElapsed) return
    lastRoadLat = lat
    lastRoadLon = lon
    lastRoadResolveMs = now
    viewModelScope.launch(defaultDispatcher) {
        val road = try {
            client.getRoadAt(lat, lon, bearing)
        } catch (e: Exception) {
            Log.w(MapCanvasViewModel.TAG, "getRoadAt failed", e)
            null
        }
        // Publish the resolved road (or its absence) into the shared
        // free-driving status instead of keeping a private copy: the label and
        // the notification read the same value (spec: `current-road-info` —
        // One free-driving road/speed status feeds every surface and the
        // notification).
        freeDrivingRoad = road
        publishFreeDrivingStatus(_uiState.value.currentSpeedKmH)
    }
}

/**
 * Publish the phone surface's road/speed into the shared status (spec:
 * `current-road-info`). The provider itself drops an unchanged value, so this
 * is safe to call at the fix rate.
 */
internal fun MapCanvasViewModel.publishFreeDrivingStatus(speedKmH: Double) {
    freeDrivingStatusProvider.publish(
        FreeDrivingStatusProvider.SURFACE_PHONE,
        freeDrivingRoad,
        speedKmH
    )
}

fun MapCanvasViewModel.onToggleFollowMode(enabled: Boolean) {
    // The session owns the camera while it is open (spec: route-planning-session —
    // Session holds the camera while active): a follow re-engage is refused there,
    // and the re-center affordance re-engages after the session has ended.
    if (enabled && sessionHoldsCamera()) return
    _uiState.value = _uiState.value.copy(followMode = enabled)
    if (enabled) {
        // Reset auto-zoom state for fresh navigation start
        setAutoZoomSuspended(false)
        lastSpeedBandIndex = -1
        currentTargetMag = 15.0
        lastAutoZoomCommitMs = 0L
        turnPassedDistance = Double.NaN
        lastValidSpeedKmH = Double.NaN
        // Set initial magnification to routing-sensible default
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(magnification = MapCanvasViewModel.DRIVE_PRESET_MAG)
        )

        // Immediately center on current GPS position
        val loc = locationService.location.value
        if (loc != null) {
            // Apply orientation: if north-up, reset angle; if follow-direction, use bearing
            val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
            val isNorthUp = when (mode) {
                MapMode.NAVIGATION, MapMode.FREE_DRIVE -> _uiState.value.navNorthUp
                MapMode.BROWSE -> _uiState.value.freeFormNorthUp
            }
            val angle = if (!isNorthUp && !loc.markerBearing.isNaN()) {
                computeMapAngle(isNorthUp, loc.markerBearing)
            } else 0.0

            // Anchor-centered from the first frame (spec: smooth-follow —
            // Anchor restored after manual pan or re-center).
            val (targetLat, targetLon) = followRenderTarget(
                loc.lat, loc.lon, _uiState.value.viewport.magnification, angle
            )
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(
                    centerLat = targetLat,
                    centerLon = targetLon,
                    angle = angle
                )
            )
            renderMap()
        }
    }
}

/**
 * Disengage follow mode (called on manual pan/zoom/rotate). In FREE_DRIVE
 * this suspends the drive preset (spec: map-modes — drive suspension). In
 * BROWSE it does nothing to the re-center button: that visibility is derived
 * from the viewport and the fix, not from a remembered interaction (spec:
 * map-modes — Browse re-center).
 */
fun MapCanvasViewModel.disengageFollowMode() {
    val s = _uiState.value
    if (s.followMode) {
        // Leave follow mode on the framing the user actually sees: the viewport
        // center becomes the displayed frame's center (the anchor center), so the
        // first pan/zoom continues from the shown frame instead of jumping by the
        // anchor offset (spec: smooth-follow — Anchor restored after manual pan or
        // re-center).
        val shown = s.renderViewport
        _uiState.value = s.copy(
            followMode = false,
            driveSuspended = true,
            viewport = if (shown != null) {
                s.viewport.copy(centerLat = shown.lat, centerLon = shown.lon)
            } else {
                s.viewport
            }
        )
    }
}

/**
 * Enter FREE_DRIVE mode (spec: map-modes — mode toggle): applies the drive
 * preset — follow on, auto-zoom on, heading-up, centered on the current GPS
 * position at the driving zoom. Runtime state only; per-mode sub-options
 * (auto-zoom, orientation) stay user-adjustable in the config sheet.
 */
fun MapCanvasViewModel.enterFreeDrive() {
    if (mode == MapMode.NAVIGATION) return
    val loc = locationService.location.value
    setAutoZoomSuspended(false)
    val angle = if (loc != null && !loc.markerBearing.isNaN()) {
        computeMapAngle(false, loc.markerBearing)
    } else 0.0
    // Anchor-centered framing for the drive preset (spec: smooth-follow —
    // Anchor-centered follow framing).
    val (targetLat, targetLon) = if (loc != null) {
        followRenderTarget(loc.lat, loc.lon, MapCanvasViewModel.DRIVE_PRESET_MAG, angle)
    } else {
        _uiState.value.viewport.centerLat to _uiState.value.viewport.centerLon
    }
    _uiState.value = _uiState.value.copy(
        followMode = true,
        autoZoomEnabled = true,
        navNorthUp = false,
        driveSuspended = false,
        browseReCenterVisible = false,
        viewport = _uiState.value.viewport.copy(
            magnification = MapCanvasViewModel.DRIVE_PRESET_MAG,
            centerLat = targetLat,
            centerLon = targetLon,
            angle = angle
        )
    )
    // Reset auto-zoom state for a fresh drive start
    lastSpeedBandIndex = -1
    currentTargetMag = 15.0
    lastAutoZoomCommitMs = 0L
    turnPassedDistance = Double.NaN
    lastValidSpeedKmH = Double.NaN
    renderMap()
}

/**
 * Apply the BROWSE representation preset (spec: map-modes — mode presets):
 * follow off, north-up orientation, drive suspension and browse-drift flags
 * cleared. The viewport center and zoom are intentionally kept where they
 * are — representation settings only, not a viewport restore.
 */
internal fun MapCanvasViewModel.applyBrowseRepresentation() {
    _uiState.value = _uiState.value.copy(
        followMode = false,
        freeFormNorthUp = true,
        driveSuspended = false,
        browseReCenterVisible = false,
        viewport = _uiState.value.viewport.copy(angle = 0.0)
    )
}

/**
 * Exit FREE_DRIVE to BROWSE (spec: map-modes — mode toggle): follow off,
 * north-up, staying at the current position and zoom.
 */
fun MapCanvasViewModel.exitFreeDrive() {
    if (mode != MapMode.FREE_DRIVE) return
    applyBrowseRepresentation()
    renderMap()
}

/**
 * Reset a suspended FREE_DRIVE to the standard drive values (spec:
 * map-modes — drive suspension and reset): follow on, auto-zoom on,
 * heading-up, speed-based driving zoom.
 */
fun MapCanvasViewModel.resetDrivePreset() {
    if (mode != MapMode.FREE_DRIVE) return
    val loc = locationService.location.value
    setAutoZoomSuspended(false)
    val angle = if (loc != null && !loc.markerBearing.isNaN()) {
        computeMapAngle(false, loc.markerBearing)
    } else 0.0
    // Re-engage on the anchor-centered frame (spec: smooth-follow — Anchor
    // restored after manual pan or re-center).
    val (targetLat, targetLon) = if (loc != null) {
        followRenderTarget(loc.lat, loc.lon, _uiState.value.viewport.magnification, angle)
    } else {
        _uiState.value.viewport.centerLat to _uiState.value.viewport.centerLon
    }
    _uiState.value = _uiState.value.copy(
        followMode = true,
        autoZoomEnabled = true,
        navNorthUp = false,
        driveSuspended = false,
        viewport = _uiState.value.viewport.copy(
            centerLat = targetLat,
            centerLon = targetLon,
            angle = angle
        )
    )
    lastSpeedBandIndex = -1
    currentTargetMag = 15.0
    lastAutoZoomCommitMs = 0L
    turnPassedDistance = Double.NaN
    lastValidSpeedKmH = Double.NaN
    renderMap()
}

/**
 * Derive the quality of [loc] (spec: `gps-fix-quality` — Fix availability and quality tiers):
 * NONE without a fix, for a fix past [fixAgeLimitMs] and while the platform reports the location
 * source disabled; POOR/GOOD by the reported accuracy otherwise. One definition for both callers —
 * the fix-driven pipeline and the re-evaluation tick that decides whether a tick is needed.
 *
 * The source read is a system-server binder call, so it runs on [defaultDispatcher]: the tick
 * caller is already off the main thread (no hop), the pipeline caller is the main-confined
 * collector.
 */
internal suspend fun MapCanvasViewModel.deriveFixQuality(loc: GpsFix?): GpsFixQuality = when {
    loc == null -> GpsFixQuality.NONE
    FixFreshness.isAgedOut(loc.time, timeSource.nowMillis(), fixAgeLimitMs) -> GpsFixQuality.NONE
    !withContext(defaultDispatcher) { locationService.isLocationSourceEnabled() } ->
        GpsFixQuality.NONE
    loc.accuracy > MapCanvasViewModel.GPS_FIX_MAX_ACCURACY_M -> GpsFixQuality.POOR
    else -> GpsFixQuality.GOOD
}

/**
 * Re-derive the BROWSE re-center visibility from the current viewport, canvas
 * and fix (spec: map-modes — Browse re-center). The offset is measured on
 * every UI-state change, so panning, zooming and vehicle movement all count
 * and nothing has to be remembered. Appear edge: beyond
 * [RECENTER_SHOW_OFFSET_PX] continuously for [RECENTER_DWELL_MS]; hide edge:
 * below [RECENTER_HIDE_OFFSET_PX], immediately; between the two the current
 * visibility is held, so a shown button cannot be toggled off by hovering at
 * the appear threshold. There is no suspension point between the read and the
 * write, so the copy cannot clobber a concurrent update.
 */
internal fun MapCanvasViewModel.refreshBrowseReCenter(state: MapCanvasUiState) {
    var offsetPx = Double.NaN
    val visible = if (mode != MapMode.BROWSE || state.gpsFixQuality == GpsFixQuality.NONE) {
        browseReCenterBeyondSinceMs = 0L
        false
    } else {
        offsetPx = MapCanvasViewModel.browseCenterOffsetPx(
            state.viewport.centerLat, state.viewport.centerLon,
            state.viewport.magnification, state.viewport.angle,
            state.canvasWidthPx, state.canvasHeightPx, projectionDpi,
            state.gpsMarkerLat, state.gpsMarkerLon
        )
        when {
            offsetPx.isNaN() -> {
                browseReCenterBeyondSinceMs = 0L
                false
            }
            offsetPx < MapCanvasViewModel.RECENTER_HIDE_OFFSET_PX -> {
                browseReCenterBeyondSinceMs = 0L
                false
            }
            state.browseReCenterVisible -> {
                // Already visible: nothing is being waited for, so clear the arm and let the
                // re-evaluation ticker go quiet (design D1/D2).
                browseReCenterBeyondSinceMs = 0L
                true
            }
            offsetPx >= MapCanvasViewModel.RECENTER_SHOW_OFFSET_PX -> {
                val now = timeSource.nowMillis()
                if (browseReCenterBeyondSinceMs == 0L) browseReCenterBeyondSinceMs = now
                now - browseReCenterBeyondSinceMs >= MapCanvasViewModel.RECENTER_DWELL_MS
            }
            else -> {
                // Inside the hysteresis band and not shown yet: no dwell
                // accrues, so position noise that keeps dipping below the
                // appear threshold never reveals the button.
                browseReCenterBeyondSinceMs = 0L
                false
            }
        }
    }
    if (state.browseReCenterVisible != visible) {
        _uiState.value = state.copy(browseReCenterVisible = visible)
        logBrowseReCenter(offsetPx, visible)
    }
}

/**
 * Evidence for the derived re-center rule — no screenshot shows why the
 * button appeared. [com.naviveylin.core.DiagnosticsLog.log] only buffers, so
 * this never touches the file on the caller's thread.
 */
internal fun MapCanvasViewModel.logBrowseReCenter(offsetPx: Double, visible: Boolean) {
    val offset = if (offsetPx.isNaN()) "-" else "%.1f".format(offsetPx)
    Log.d(MapCanvasViewModel.TAG, "browse re-center off=" + offset + "px vis=" + visible)
    com.naviveylin.core.DiagnosticsLog.log(
        "RECENTER",
        "off=" + offset + "px vis=" + visible +
            " mag=%.2f".format(_uiState.value.viewport.magnification)
    )
}

/**
 * Re-center in BROWSE (spec: map-modes — Browse re-center): put the current
 * GPS position at the center of the map, stay in BROWSE, hide the button.
 * Deliberately not anchor-framed: the vehicle anchor presets configure the
 * driving modes, and an off-center target would immediately re-show the
 * button the user just dismissed.
 */
fun MapCanvasViewModel.recenterInBrowse() {
    if (mode != MapMode.BROWSE) return
    val loc = locationService.location.value
    if (loc != null) {
        val vp = _uiState.value.viewport
        _uiState.value = _uiState.value.copy(
            browseReCenterVisible = false,
            viewport = vp.copy(centerLat = loc.lat, centerLon = loc.lon)
        )
        renderMap()
    }
}

/**
 * Re-anchor the follow framing on a displayed (predicted) position and render
 * (spec: smooth-follow — Anchor-centered follow framing, Prediction state
 * update). The follow display loop calls this when the blit offset has reached
 * the overrun margin, so the frame is re-rendered with the display position
 * back inside the margin — still anchor-centered, so the vehicle does not jump
 * to the screen center.
 *
 * Single follow center (delta fix-follow-vehicle-jumps): this and the per-fix
 * zoom/rotation renders are the ONLY follow re-renders, and both anchor on
 * [MapCanvasViewModel.followDisplayLat/Lon] — a fix never re-commits the
 * follow center on its own.
 */
fun MapCanvasViewModel.renderFollowFrameAt(lat: Double, lon: Double) {
    if (!_uiState.value.followMode || sessionHoldsCamera()) return
    if (lat.isNaN() || lon.isNaN()) return
    val vp = _uiState.value.viewport
    val (targetLat, targetLon) = followRenderTarget(lat, lon, vp.magnification, vp.angle)
    _uiState.value = _uiState.value.copy(
        viewport = vp.copy(centerLat = targetLat, centerLon = targetLon)
    )
    renderMap()
}

/** Set free-form orientation: true = north-up, false = follow direction. */
fun MapCanvasViewModel.onSetFreeFormOrientation(northUp: Boolean) {
    _uiState.value = _uiState.value.copy(freeFormNorthUp = northUp)
    // Apply immediately: if north-up, reset angle; if follow-direction, use current bearing
    if (northUp) {
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(angle = 0.0)
        )
        renderMap()
    } else {
        val loc = locationService.location.value
        if (loc != null && !loc.markerBearing.isNaN()) {
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(
                    angle = -Math.toRadians(loc.markerBearing)
                )
            )
            renderMap()
        }
    }
    viewModelScope.launch {
        settingsStorage.update { it.copy(freeFormNorthUp = northUp) }
    }
}

/** Set navigation orientation: true = north-up, false = follow direction. */
fun MapCanvasViewModel.onSetNavOrientation(northUp: Boolean) {
    _uiState.value = _uiState.value.copy(navNorthUp = northUp)
    // Apply immediately
    if (northUp) {
        _uiState.value = _uiState.value.copy(
            viewport = _uiState.value.viewport.copy(angle = 0.0)
        )
        renderMap()
    } else {
        val navPos = _navPosition
        if (navPos != null && !navPos.bearing.isNaN()) {
            _uiState.value = _uiState.value.copy(
                viewport = _uiState.value.viewport.copy(
                    angle = -Math.toRadians(navPos.bearing)
                )
            )
            renderMap()
        }
    }
    viewModelScope.launch {
        settingsStorage.update { it.copy(navNorthUp = northUp) }
    }
}

/** Get current location for overlay rendering. */
fun MapCanvasViewModel.getCurrentLocation(): GpsFix? = locationService.location.value

/**
 * Lease GPS updates for the visible phone map (spec: `location-updates-lease`).
 * Idempotent, so the lifecycle callers (screen init, resume) can call it
 * freely; the matching [stopLocationUpdates] releases this surface's lease
 * without touching a lease held by navigation or by the car session.
 */
fun MapCanvasViewModel.startLocationUpdates() {
    if (mapLease == null) {
        mapLease = locationService.acquire(LocationConsumers.PHONE_MAP)
    }
}

/** Release the visible-phone-map GPS lease. */
fun MapCanvasViewModel.stopLocationUpdates() {
    mapLease?.release()
    mapLease = null
}

/** Check if location permission is granted. */
fun MapCanvasViewModel.hasLocationPermission(): Boolean = locationService.hasPermission

/**
 * The observers of this ViewModel's own driving-mode vote: the FREE_DRIVE vote published to the shared provider, and the surface's status cleared when free driving ends.
 */
internal fun MapCanvasViewModel.wireDrivingModeVotes() {
    // Publish FREE_DRIVE mode to the shared driving-state provider: the
    // phone surface's vote is `followMode || driveSuspended` (identical to
    // this ViewModel's mode derivation, spec: map-modes). One observer on
    // the source of truth — no per-transition publish sites to miss. The
    // provider is nullable only for Robolectric tests that omit it.
    val drivingModeProvider = drivingModeProvider
    if (drivingModeProvider != null) {
        viewModelScope.launch {
            _uiState.map { it.followMode || it.driveSuspended }
                .distinctUntilChanged()
                .collect { freeDriving ->
                    drivingModeProvider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, freeDriving)
                }
        }
    }

    // Leaving free driving clears the surface's published status, so no road
    // from an ended mode survives into the notification (spec: `current-road-info`
    // — Status is empty before the first fix). One observer on the mode's own
    // source, like the driving-mode vote above.
    viewModelScope.launch {
        _uiState.map { it.followMode || it.driveSuspended }
            .distinctUntilChanged()
            .collect { freeDriving ->
                if (!freeDriving) {
                    freeDrivingStatusProvider.clear(FreeDrivingStatusProvider.SURFACE_PHONE)
                }
            }
    }

    // Basemap data changes (download/update/delete while the app runs):
}

/**
 * The GPS fix-quality re-evaluation: the tick that derives it without a new fix and the debounced pipeline that publishes it.
 */
internal fun MapCanvasViewModel.wireFixQuality() {
    // Fix-quality re-evaluation tick (spec: gps-fix-quality — Quality is re-evaluated without a
    // new fix). A counter in a StateFlow, not a cold flow: the combined value must appear as soon
    // as the location flow has one, without waiting for a tick.
    //
    // The loop runs on Dispatchers.Default with the REAL clock — the rule the stale-speed ticker
    // below documents (TODO.md §40.C.16): an endless delay loop on the virtual clock of a test
    // scheduler would keep every `advanceUntilIdle` non-idle. It reports a tick **only** when its
    // own derivation differs from the published quality, because the collector below lives on the
    // main dispatcher: a tick per period would read the main dispatcher from a real thread pool
    // every second, and for a ViewModel that outlives its test that is a read against another
    // test's `Dispatchers.setMain`, failing that later test with `Dispatchers.Main is used
    // concurrently with setting it` (spec: gps-fix-quality — Fix-quality re-evaluation stays off
    // the main thread; TODO.md §121). An unchanged quality therefore costs no main-dispatcher
    // traffic at all, while a real change is detected within one tick and published through the
    // one debounced pipeline below. The comparison is against the published value, so ticks that
    // arrive while a change is still inside the debounce repeat it; the pipeline's own
    // `distinctUntilChanged()` drops them.
    viewModelScope.launch(Dispatchers.Default) {
        while (true) {
            delay(fixQualityTickMs)
            val quality = deriveFixQuality(locationService.location.value)
            // The same tick completes a pending BROWSE appear dwell (spec: map-modes — Browse
            // re-center). The dwell is armed by a state emission, but the failing case has no later
            // emission — a stationary vehicle with the location provider silent — so the deadline
            // has to be delivered on the clock instead of waiting for one. The arm is cleared as
            // soon as the button is visible, and no arm means no write at all.
            //
            // The guard reads the tier this tick just derived, not the published one: the published
            // NONE arrives through a 2 s debounce (see the pipeline below), and the arm is only
            // cleared by a state emission, so a fix that becomes unusable right after arming would
            // otherwise let the clock path reveal the button — the case the requirement's
            // "age out" scenario forbids (independent review 2026-10-11, finding P2-1).
            val armedAtMs = browseReCenterBeyondSinceMs
            if (armedAtMs != 0L && quality != GpsFixQuality.NONE &&
                timeSource.nowMillis() - armedAtMs >= MapCanvasViewModel.RECENTER_DWELL_MS
            ) {
                recenterDwellTickCount.value = recenterDwellTickCount.value + 1L
                recenterDwellTicks.value = recenterDwellTicks.value + 1L
            }
            // Counted after the dwell evaluation of the same iteration, so a case that awaits N
            // heartbeats knows those N dwell evaluations have also happened.
            fixQualityTickCount.value = fixQualityTickCount.value + 1L
            if (quality != _gpsFixQuality.value) {
                fixQualityTicks.value = fixQualityTicks.value + 1L
            }
        }
    }

    // GPS fix quality: re-derived on a slow tick as well as on every fix. The location source
    // goes silent when there is nothing new to report — location services switched off, or the
    // minimum-distance throttling at standstill — so a value computed only on emission would
    // stay latched at its last tier forever (spec: gps-fix-quality — Quality is re-evaluated
    // without a new fix). Debounced to prevent flicker; a tick that derives the same quality
    // publishes nothing.
    @OptIn(FlowPreview::class)
    viewModelScope.launch {
        combine(locationService.location, fixQualityTicks) { loc, _ -> loc }
            .map { loc -> deriveFixQuality(loc) }
            .distinctUntilChanged()
            .debounce(2000L)
            .collect { quality ->
                if (quality != _gpsFixQuality.value) {
                    Log.d(MapCanvasViewModel.TAG, "GPS fix quality: $quality")
                }
                _gpsFixQuality.value = quality
                _uiState.value = _uiState.value.copy(gpsFixQuality = quality)

                // Eagerly resolve the admin region once a GOOD fix is
                // available while the search panel is open, so the region
                // name appears without requiring a typed query.
                if (quality == GpsFixQuality.GOOD && searchPanelOpen && searchAdminRegionHandle == 0L) {
                    viewModelScope.launch {
                        withContext(defaultDispatcher) { currentSearchAdminRegionHandle() }
                    }
                }
            }
    }
}

/**
 * The stale-speed decay: a silent provider reads zero instead of its last speed.
 */
internal fun MapCanvasViewModel.wireStaleSpeed() {
    // Stale-speed decay (spec: gps-speed-priority — stationary reads zero).
    // The provider goes silent at standstill (min-distance throttling), so
    // the follow-mode speed must decay to 0 once no fresh fix arrives — the
    // location flow still delivers the LAST fix, which the collect below
    // would otherwise keep re-rendering with its pre-stop speed.
    // Runs on Dispatchers.Default with the REAL clock: the state holders
    // must not feed the (virtual) test scheduler with an endless delay
    // loop, and the follow-mode gate keeps tests that never enable follow
    // mode free of stray zero-writes.
    viewModelScope.launch(Dispatchers.Default) {
        while (true) {
            delay(MapCanvasViewModel.SPEED_STALE_TICK_MS)
            if (_uiState.value.followMode &&
                SpeedStaleness.isStale(lastSpeedFixTime, System.currentTimeMillis())
            ) {
                _uiState.value = _uiState.value.copy(currentSpeedKmH = 0.0)
            }
        }
    }
}

/**
 * The BROWSE re-center visibility, re-derived from state instead of hooked per mutation site.
 */
internal fun MapCanvasViewModel.wireBrowseReCenter() {
    // Browse re-center visibility (spec: map-modes — Browse re-center): one
    // collector re-derives it from the viewport, the canvas and the fix, so
    // every pan, zoom and position update is covered without hooking each
    // mutation site. refreshBrowseReCenter emits only on a visibility change,
    // so it does not re-trigger itself forever.
    viewModelScope.launch {
        combine(uiState, recenterDwellTicks) { state, _ -> state }
            .collect { refreshBrowseReCenter(it) }
    }
}

/**
 * Follow mode: keeps the map center and the marker in sync with the GPS fixes.
 */
internal fun MapCanvasViewModel.wireFollowMode() {
    // Follow mode: keep map center and marker in sync.
    viewModelScope.launch {
        var lastRenderedLat = Double.NaN
        var lastRenderedLon = Double.NaN

        locationService.location.collect { fix ->
            if (fix == null) {
                _uiState.value = _uiState.value.copy(
                    gpsLocation = null,
                    gpsMarkerLat = Double.NaN,
                    gpsMarkerLon = Double.NaN,
                    gpsMarkerBearing = Double.NaN,
                    gpsMarkerAccuracy = 0.0
                )
                mapRenderer?.clearGpsMarkerState()
                lastMarkerLat = Double.NaN
                lastMarkerLon = Double.NaN
                return@collect
            }

            _uiState.value = _uiState.value.copy(
                gpsLocation = fix,
                currentSpeedKmH = fix.speedKmH
            )
            lastSpeedFixTime = fix.time

            if (logCount++ % 30 == 0) {
                Log.d(MapCanvasViewModel.TAG, "GPS fix acc=${"%.1f".format(fix.accuracy)} " +
                        "bearing=${if (!fix.markerBearing.isNaN()) "%.1f".format(fix.markerBearing) else "-"} " +
                        "speed=${if (!fix.speedKmH.isNaN()) "%.1f".format(fix.speedKmH) else "-"} " +
                        "follow=${_uiState.value.followMode}")
            }

            // Use navigation position if available (filtered by engine), else raw GPS
            val navPos = _navPosition
            val isNavigating = _navigationViewModel?.state?.value?.isNavigating == true
            // Vehicle anchor: routing while route guidance is active, else the
            // free-driving anchor (spec: smooth-follow — Vehicle position
            // anchor in follow mode).
            val activeAnchor = if (isNavigating) routingAnchor else freeDrivingAnchor
            if (activeAnchor != _uiState.value.activeFollowAnchor) {
                _uiState.value = _uiState.value.copy(activeFollowAnchor = activeAnchor)
                // Re-resolve into the visible map area for the new mode's preset.
                publishResolvedAnchor(activeAnchor)
            }
            val markerLat = if (isNavigating && navPos != null && !navPos.lat.isNaN()) navPos.lat else fix.lat
            val markerLon = if (isNavigating && navPos != null && !navPos.lon.isNaN()) navPos.lon else fix.lon

            // Deduplicate duplicate fixes delivered by multiple providers in the same
            // millisecond. Compare by timestamp and coarse coordinates only; bearing
            // may differ between Fused and LocationManager even for the same fix.
            val freshestBearing = if (!fix.markerBearing.isNaN()) fix.markerBearing else -1.0
            val accuracy = fix.accuracy
            val sameFix = (fix.time - lastGpsTime) < MapCanvasViewModel.GPS_DEDUPE_MS &&
                    kotlin.math.abs(fix.lat - lastGpsLat) < 1e-6 &&
                    kotlin.math.abs(fix.lon - lastGpsLon) < 1e-6
            if (sameFix) {
                // Duplicate fix — skip render work. Navigation is fed by the
                // engine's own location lease (spec: `navigation-engine` — one
                // engine per process feeds the native controller), not by this
                // map pipeline.
                return@collect
            }
            lastGpsLat = fix.lat
            lastGpsLon = fix.lon
            lastGpsTime = fix.time

            // Resolve the road's max speed for the follow-mode speed widget
            // (throttled: only on significant movement or after a cooldown).
            resolveMaxSpeed(fix.lat, fix.lon)
            // Resolve the current road for the free-driving street label
            // (bearing-aware, throttled; spec: current-road-info).
            resolveCurrentRoad(fix.lat, fix.lon, fix.markerBearing)
            // Publish the fresh speed into the shared free-driving status (the
            // road half follows from the lookup above; spec: `current-road-info`
            // — One free-driving road/speed status feeds every surface and the
            // notification).
            publishFreeDrivingStatus(fix.speedKmH)

            if (!_uiState.value.followMode ||
                (mode == MapMode.FREE_DRIVE && _uiState.value.driveSuspended)
            ) {
                // The marker is a Compose overlay: update it on every fix so it
                // tracks the vehicle immediately, independent of the render cadence.
                updateMarkerState(markerLat, markerLon, freshestBearing, accuracy)
                val nowMs = System.currentTimeMillis()
                val dist = distanceMeters(lastMarkerLat, lastMarkerLon, markerLat, markerLon)
                if ((dist > 5.0 || lastMarkerLat.isNaN()) &&
                    nowMs - lastNonFollowMarkerRenderMs >= MapCanvasViewModel.NON_FOLLOW_MARKER_RENDER_INTERVAL_MS
                ) {
                    lastMarkerLat = markerLat
                    lastMarkerLon = markerLon
                    lastNonFollowMarkerRenderMs = nowMs
                    renderMap()
                }
                return@collect
            }

            // Update smoothed center on every distinct GPS fix so the EMA
            // never has to catch up after a coalesced tick.
            val smoothedLat = smoothCenter(markerLat, markerLon).first
            val smoothedLon = smoothCenter(markerLat, markerLon).second

            // In follow mode the GPS marker represents the current vehicle position
            // and must be drawn at the raw (or navigation-filtered) GPS fix, not at
            // the smoothed camera center. Otherwise the marker drifts away from the
            // road/track when the camera lags behind.
            val followMarkerLat = markerLat
            val followMarkerLon = markerLon

            // Map rotation uses the location layer's smoothed bearing; the
            // marker arrow uses the freshest bearing (no added lag). Provider
            // knowledge (Fused vs LocationManager) stays inside LocationService.
            val isNorthUp = when (mode) {
                MapMode.NAVIGATION, MapMode.FREE_DRIVE -> _uiState.value.navNorthUp
                MapMode.BROWSE -> _uiState.value.freeFormNorthUp
            }
            val effectiveBearing = if (!fix.smoothedBearing.isNaN()) fix.smoothedBearing else lastUsedBearing
            // The marker arrow tracks the freshest direction signal so it points
            // along the new driving direction immediately after a turn, while the
            // map rotation uses the smoothed value and rotates at its own pace.
            val markerBearingRaw = if (!fix.markerBearing.isNaN()) fix.markerBearing else lastUsedBearing
            // Orientation mode must NOT influence the arrow: north-up is a map-rotation
            // choice, not a bearing-availability state (spec: gps-location-marker).
            val markerBearing = computeMarkerBearing(isNorthUp, markerBearingRaw)
            // Feed the marker state to the renderer (snapshotted into the next render
            // job) AND to the overlay state directly — the overlay must show the latest
            // fix immediately, not wait for the next rendered frame.
            updateMarkerState(followMarkerLat, followMarkerLon, markerBearing, accuracy)
            val smoothedAngle = if (!isNorthUp && !effectiveBearing.isNaN()) computeMapAngle(isNorthUp, effectiveBearing) else Double.NaN
            val renderedAngle = normalizeAngle(mapRenderer?.renderedAngle ?: _uiState.value.viewport.angle)
            // "Always north" commits 0 on EVERY update: the last-angle fallback is
            // a follow-direction rule only, so a stale heading-up angle can never
            // rotate the map while north-up is selected (spec: compass-settings —
            // "Switching to north-up while driving stops further rotation";
            // gps-render-coalescing — "Course unavailable").
            val angle = when {
                isNorthUp -> 0.0
                !smoothedAngle.isNaN() -> {
                    val deltaDeg = Math.toDegrees(normalizeAngle(smoothedAngle - renderedAngle))
                    if (kotlin.math.abs(deltaDeg) < MIN_BEARING_DELTA_DEG) {
                        renderedAngle
                    } else {
                        val maxDeltaRad = Math.toRadians(MAX_ANGLE_RATE_DEG_PER_RENDER)
                        val deltaRad = normalizeAngle(smoothedAngle - renderedAngle)
                        val clampedDelta = deltaRad.coerceIn(-maxDeltaRad, maxDeltaRad)
                        normalizeAngle(renderedAngle + clampedDelta)
                    }
                }
                // Follow direction without a valid course yet: stay at the last
                // used follow-direction angle (or North-Up if none) so the map
                // neither spins nor snaps to north (spec: gps-render-coalescing —
                // "Keep last valid course bearing").
                !lastUsedAngle.isNaN() -> lastUsedAngle
                else -> 0.0
            }
            if (!effectiveBearing.isNaN()) lastUsedBearing = effectiveBearing
            // Only follow-direction angles are remembered: a north-up period must
            // not erase the last driving direction, so switching back to follow
            // direction without a usable bearing resumes it instead of snapping
            // to north (design D2).
            if (!isNorthUp && !angle.isNaN()) lastUsedAngle = angle

            // Distance since last actual render; used to decide whether a new
            // native render is worth the cost.
            val distanceMeters = if (!lastRenderedLat.isNaN()) {
                distanceMeters(smoothedLat, smoothedLon, lastRenderedLat, lastRenderedLon)
            } else Double.POSITIVE_INFINITY

            val positionChanged = distanceMeters > 5.0 || lastRenderedLat.isNaN()
            val angleChanged = !isAngleSame(angle, _uiState.value.viewport.angle)
            // Single-follow-center (delta fix-follow-vehicle-jumps): position
            // scroll is owned by the display loop (blit within the overrun margin),
            // so a fix no longer triggers a render on its own. Renders fire for
            // rotation changes, zoom commits, and the initial frame before the
            // display loop has a position.
            val displayActive = !followDisplayLat.isNaN() && !followDisplayLon.isNaN()
            val shouldRender = (positionChanged && !displayActive) || angleChanged

            // Anchor-centered follow framing (spec: smooth-follow — Anchor-centered
            // follow framing): one place applies the anchor — the render target.
            // The follow blit then carries the prediction drift only, and the
            // marker overlay needs no anchor of its own. viewport.center stays the
            // geo position at the screen center (the anchor center in follow mode),
            // so gesture math and the mini-map keep working unchanged.
            //
            // Single follow center (delta fix-follow-vehicle-jumps): the frame is
            // anchored on the DISPLAYED (eased predicted) position, not on the raw
            // fix — the renderer target stays on the position the display loop
            // scrolls, and the marker (same source) stays on the road.
            fun followTarget(mag: Double) = followRenderTarget(
                followRenderCenterLat(), followRenderCenterLon(), mag, angle
            )

            val (targetLat, targetLon) = followTarget(zoomWalk.renderMag(_uiState.value.viewport.magnification))

            // Always keep renderer's target viewport current so the next
            // render (coalesced or not) is centered on the anchor center.
            mapRenderer?.prepareViewport(targetLat, targetLon, zoomWalk.renderMag(_uiState.value.viewport.magnification), angle)

            // Coalesce follow-mode renders so GPS ticks cannot overrun the render pipeline.
            val now = timeSource.nowMillis()
            val throttleElapsed = now - lastFollowRenderMs >= MapCanvasViewModel.GPS_FOLLOW_RENDER_INTERVAL_MS
            if (!throttleElapsed && shouldRender) {
                return@collect
            }

            // Compute the new magnification first so the viewport state is updated
            // once and only one render is requested. Two consecutive renders with
            // different zoom levels produced scaled "wrong zoom" frames.
            var newMag = _uiState.value.viewport.magnification
            var zoomCommitted = false
            if (positionChanged && _uiState.value.autoZoomEnabled) {
                val navVm = _navigationViewModel
                val navState = navVm?.state?.value
                val rawSpeed = autoZoomSpeedKmH(
                    isNavigating = isNavigatingNow(),
                    engineSpeedKmH = navState?.currentSpeedKmH ?: Double.NaN,
                    ownSpeedKmH = _uiState.value.currentSpeedKmH
                )
                val filteredSpeed = filterSpeed(rawSpeed)
                val speedTarget = SpeedZoomTable.compute(filteredSpeed)

                val turnDist = navState?.nextInstruction?.distanceTo ?: Double.NaN
                val turnFloor = computeTurnBoost(turnDist)

                val curveDist = findNearestCurve(markerLat, markerLon)
                val curveFloor = computeTurnBoost(curveDist)

                // Track turn waypoint passage for post-turn hold
                if (!turnDist.isNaN() && turnDist < 0 && turnPassedDistance.isNaN()) {
                    turnPassedDistance = 0.0
                } else if (!turnPassedDistance.isNaN()) {
                    turnPassedDistance += 5.0
                    if (turnPassedDistance > 600.0) {
                        turnPassedDistance = Double.NaN
                    }
                } else if (!turnDist.isNaN() && turnDist > 0) {
                    turnPassedDistance = Double.NaN
                }

                val postTurnFloor = if (!turnPassedDistance.isNaN() && turnPassedDistance <= 600.0) 15.0 else 0.0
                val finalTarget = maxOf(speedTarget, turnFloor, curveFloor, postTurnFloor)
                val currentBand = SpeedZoomTable.bandIndex(filteredSpeed)

                if (autoZoomSuspended && currentBand != lastSpeedBandIndex) {
                    setAutoZoomSuspended(false)
                }

                if (!autoZoomSuspended) {
                    // Design D1 (spec: auto-speed-zoom — Smooth zoom
                    // transitions): commit the fractional target (turn/
                    // curve/post-turn floors included) without integer
                    // rounding. The first commit jumps straight to the
                    // target (keeps the spec's "Speed unknown" scenario);
                    // later commits converge via a distance-proportional
                    // step (ZOOM_CONVERGENCE_GAIN × remaining gap, capped
                    // at ≤ 0.5 levels per update) — fast when far away,
                    // gentle when near, so speed-noise target jitter is
                    // damped instead of chased (no zoom "pumping").
                    // stepToward returns current unchanged below epsilon →
                    // no commit, and since the mag did not change, no
                    // re-render either.
                    val target = finalTarget.coerceIn(MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG)
                    val hasCommitted = lastAutoZoomCommitMs > 0L
                    val stepped = if (hasCommitted) SpeedZoomTable.stepToward(newMag, target) else target
                    if (stepped != newMag) {
                        newMag = stepped
                        currentTargetMag = target
                        lastAutoZoomCommitMs = now
                        zoomCommitted = true
                        // The speed target is recorded immediately, but the display
                        // must not land the whole difference in one frame: start (or
                        // extend) the bounded walk from the displayed frame.
                        beginZoomWalk(stepped)
                        Log.d(MapCanvasViewModel.TAG, "autoZoom commit speed=" + "%.1f".format(filteredSpeed) +
                                "target=$target mag=$stepped")
                    } else if (logCount++ % 30 == 0) {
                        Log.d(MapCanvasViewModel.TAG, "autoZoom hold speed=" + "%.1f".format(filteredSpeed) +
                                "target=$target current=$newMag")
                    }
                    lastSpeedBandIndex = currentBand
                }
            }

            // Center renders only for rotation, zoom, or the initial frame — the
            // display loop scrolls position within the overrun margin (spec:
            // smooth-follow — Prediction state update). An exact double comparison
            // would re-render on every GPS tick (sub-meter jitter) — that caused
            // an endless render loop.
            val viewportMoved = shouldRender ||
                    newMag != _uiState.value.viewport.magnification

            if (viewportMoved) {
                // Track the position the frame is anchored on (displayed center,
                // or the marker fix before the display loop is active) so the next
                // distance check is against the actual rendered center.
                lastRenderedLat = followRenderCenterLat()
                lastRenderedLon = followRenderCenterLon()
                // Committed center = anchor center for the magnification the frame will
                // actually be rendered at: the walk's current step while one is pending
                // (a walk renders the step, not the recorded target).
                val renderedMag = zoomWalk.renderMag(newMag)
                val (commitLat, commitLon) = followTarget(renderedMag)
                _uiState.value = _uiState.value.copy(
                    viewport = _uiState.value.viewport.copy(
                        centerLat = commitLat,
                        centerLon = commitLon,
                        angle = normalizeAngle(angle),
                        magnification = newMag
                    ),
                    // Auto-zoom display-animation tick (spec: smooth-zoom):
                    // bumped only on auto-zoom commits — the screen starts
                    // the ~500 ms center-anchored easing on this.
                    autoZoomCommitTick = if (zoomCommitted) {
                        _uiState.value.autoZoomCommitTick + 1
                    } else {
                        _uiState.value.autoZoomCommitTick
                    }
                )
                mapRenderer?.prepareViewport(commitLat, commitLon, newMag, angle)
                // In follow mode the marker represents the current vehicle position and
                // is drawn by the Compose overlay at the raw/navigation GPS fix, projected
                // against the emitted frame viewport (which is anchor-centered). The
                // viewport moved — render the anchor-centered frame.
                renderMap()
                lastFollowRenderMs = timeSource.nowMillis()
            } else {
                // No viewport motion; marker stays at the raw GPS fix via the overlay.
            }
        }
    }
}
