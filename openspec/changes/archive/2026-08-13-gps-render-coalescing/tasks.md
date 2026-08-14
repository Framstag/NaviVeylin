# GPS Render Coalescing — Tasks

## 1. GPS Deduplication

- [x] 1.1 Deduplicate fixes in `LocationService` by `location.time` so Fused + LocationManager do not emit the same fix twice
- [x] 1.2 Keep emitting genuinely new fixes with different timestamps

## 2. Follow-Mode Render Throttling

- [x] 2.1 Add `GPS_FOLLOW_RENDER_INTERVAL_MS = 200` throttle in `MapCanvasViewModel`
- [x] 2.2 Skip GPS ticks inside the throttle window instead of queuing a render for each tick
- [x] 2.3 Continue forwarding every GPS fix to the navigation engine regardless of throttle

## 3. Bearing / Course-Over-Ground

- [x] 3.1 Replace raw-GPS bearing smoothing with course-over-ground from the last 10 positions
- [x] 3.2 Compute course bearing between newest point and oldest point at least 40 m away
- [x] 3.3 Add low-pass filter (`COURSE_LOW_PASS_ALPHA = 0.3`) on the course bearing
- [x] 3.4 Keep previous smoothed course bearing when not enough movement is available
- [x] 3.5 Keep `MIN_BEARING_DELTA_DEG = 2.0` deadband relative to rendered angle
- [x] 3.6 Increase `MAX_ANGLE_RATE_DEG_PER_RENDER` to 30.0 so the map follows turns quickly
- [x] 3.7 Normalize follow-mode angle to `[-π, π]` in ViewModel state and MapRenderer
- [x] 3.8 Reset course history when latest segment (> 8 m) turns > 45° from smoothed course; clear smoothed bearing
- [x] 3.9 Use COG bearing for marker even when viewport move does not trigger a render
- [x] 3.10 Keep last used map angle and last used marker bearing when course temporarily unavailable
- [x] 3.11 Fast reconvergence after reset: 10 m distance + alpha 0.7 until 40 m history is stable
- [x] 3.12 `smoothCourseBearing` returns NaN (not -1.0) when no bearing, preventing drift toward North-Up
- [x] 3.13 Marker arrow uses freshest direction (window course -> segment bearing -> last used); map rotation may lag
- [x] 3.14 `MAX_ANGLE_RATE_DEG_PER_RENDER` raised to 90.0 so sharp turns complete in 1-2 frames

## 4. Center Position in Follow Mode

- [x] 4.1 Remove center EMA smoothing (`centerSmoothAlpha = 1.0`)
- [x] 4.2 Keep 500 m teleport reset
- [x] 4.3 Draw GPS marker at raw/navigation GPS fix, not at a smoothed center
- [x] 4.4 Use smoothed (raw) center for distance checks and render centering

## 5. Auto-Zoom Stability

- [x] 5.1 Increase `ZOOM_COMMIT_SAMPLES` to 3
- [x] 5.2 Add `ZOOM_HYSTERESIS_MAG = 1.0`: commit only when target differs by at least one full level
- [x] 5.3 Log zoom diff and hysteresis decision

## 6. Render Pipeline Stability

- [x] 6.1 Remove `epoch.incrementAndGet()` from `MapRenderer.setGpsMarker` for pure position updates
- [x] 6.2 Keep epoch bump for zoom, overlay, style, and marker visibility changes
- [x] 6.3 Emit completed rotated frames based on epoch/magnification match; do not discard for angle drift
- [x] 6.4 Log suppressed frames with the drift in degrees
- [x] 6.5 Fix shared backing-buffer recycle crash in `trySubRegionBlit` / `blitSubRegion`: copy region before recycle
- [x] 6.6 `extractCenterRegion` returns independent copy; emitted frames never share storage with `frontBuffer`
- [x] 6.7 Sub-region blit rotates the delta by the viewport angle

## 8. Diagnostics

- [x] 8.1 Log `isNorthUp`, `freeFormNorthUp`, `navNorthUp` with every 30th bearing log
- [x] 8.2 Log time difference for discarded duplicate GPS fixes
- [x] 8.3 Log course history reset reason (teleport / turn)
- [x] 8.4 Log oldest/newest course points and cumulative distance used for bearing

## 9. Tests + Build

- [x] 9.1 Update `MapRendererGpsMarkerTest` to assert `setGpsMarker` return value instead of epoch
- [x] 9.2 Stabilize `MapCanvasViewModelFavAddTest` by cancelling `viewModelScope` after each test
- [x] 9.3 Stabilize `MapCanvasViewModelAdminRegionTest`, `MapCanvasViewModelFollowModeTest`, `MapCanvasViewModelDarkModeTest` with `cancelScopeForTest()`
- [x] 9.4 Run `./gradlew :app:testDebugUnitTest --tests 'com.naviveylin.ui.map.*'` successfully
- [x] 9.5 Run `./gradlew :app:compileDebugKotlin` successfully
- [x] 9.6 Run `./gradlew :app:testDebugUnitTest` successfully
- [x] 9.7 Run `openspec validate "gps-render-coalescing"` successfully

