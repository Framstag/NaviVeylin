# Map Follow — location, follow and camera

This document owns the follow path: GPS deduplication, angle handling, bearing smoothing, the marker
rules, follow-mode rotation and camera center, and auto-zoom. The pipeline it rides is
`guidelines/MapRendering.md`; the rationale and the measurements behind these rules are in
`guidelines/MapFollow.why.md`.

---

## 3. GPS Deduplication

- Fused + LocationManager deliver the same fix with the same `location.time` but possibly with
  different `bearing`. Therefore:
  - **Rule: `LocationService`**: dedupe by `location.time` (identical timestamp).
  - **Rule: `MapCanvasViewModel`**: dedupe by timestamp difference (< `GPS_DEDUPE_MS`) + coarse
    coordinate (`1e-6°`, ~11 cm). **Do NOT dedupe by bearing** — different providers attach
    different bearings to the same fix.
- Still forward duplicates to the navigation engine; only skip render work.
## 6. Angle Handling

- **Rule: Store all angles in radians**; normalize to `[-π, π]` at all storage points:
  `_uiState.viewport.angle`, `MapRenderer.currentAngle`, `MapRenderer.frontBufferAngle`.
- Unnormalized angles grow beyond 2π across renders and produce apparent rotation jumps (the log
  then shows e.g. `angle=14.6` — note: the `prepareViewport` log converts to degrees for display;
  values in the log are degrees, not radians).
- Former native convention (C++): `screenBearing = gpsMarkerBearing + angle`. For follow-direction,
  `angle = -bearing` (radians) so the marker arrow points up. **Do not flip the sign.**
- Kotlin overlay convention (same sign): `ProjectionUtils.screenBearing(bearingDeg, angleRad)` =
  `bearingDeg + toDegrees(angle)`. The marker is drawn by the Compose overlay, so the C++ side no
  longer computes a screen bearing — keep both sides on the same formula.
- **Rule: The orientation mode decides the fallback, never a stale angle.** In the follow collector the
  angle is committed as: `isNorthUp -> 0.0`; else the deadband/rate-limited `smoothedAngle`; else
  `lastUsedAngle` (or 0). "Always north" therefore commits 0 on EVERY update and the last-angle
  fallback is a follow-direction rule only — a heading-up angle from before the toggle (or from a
  previous drive) must never rotate the map once north-up is selected. `lastUsedAngle` is written
  only from follow-direction angles, so a north-up period does not erase the last driving
  direction. Regression tests: `MapCanvasViewModelFollowModeTest.northUpStaysAtZeroOverConsecutiveHeadingChanges`,
  `togglingToNorthUpWhileDrivingStopsFurtherRotation` (both fail on the pre-fix branch).
- At a standstill or after a course-history reset the "map does not spin / does not snap to north"
  guarantee comes from the deadband path: the location layer keeps the previous bearing, so
  `smoothedAngle` equals the rendered angle and the commit keeps it. The explicit
  `lastUsedAngle` fallback is defensive (it is only reachable if no bearing was ever seen, in which
  case it is NaN and yields 0).
## 7. Bearing Smoothing (location layer)

Bearing smoothing lives in `LocationService` (`BearingFilter`), NOT in the ViewModel. The location
layer emits a uniform `GpsFix` with two bearings, provider-agnostic to consumers:

- **Rule: `smoothedBearing`** — for map rotation. Stable, no render churn.
- **Rule: `markerBearing`** — for the marker arrow. Freshest signal, no added lag.

Provider-aware inside `LocationService` only:

- **Rule: Fused path**: Fused already smooths its bearing (sensor fusion + Kalman). `markerBearing` =
  `loc.bearing` as delivered (zero added lag); `smoothedBearing` = light EMA (alpha 0.5) on
  `loc.bearing` — do NOT re-derive course from positions on the Fused path.
- **Rule: LocationManager path** (GMS-less devices): fakes Fused quality — course-over-ground derived
  from the recent position track:
  - Ring buffer with 10 positions; course = bearing between the newest point and the oldest point
    that is ≥ `MIN_COURSE_DISTANCE_M` (40 m) away.
  - **Rule: Two-state logic**
    - `courseStable == false` (after start/teleport/turn reset): short base
      (`MIN_COURSE_DISTANCE_FAST_M` = 10 m) + fast low-pass (`COURSE_LOW_PASS_ALPHA_FAST` = 0.7)
      → new direction established quickly.
    - `courseStable == true` (≥ 40 m track): 40-m base + slow low-pass (0.3) → stable straight ahead.
  - **Rule: Turn reset** if the last segment bearing (> `MIN_SEGMENT_FOR_TURN_M` = 8 m) deviates by more
    than `COURSE_TURN_RESET_DEG` (45°) from the smoothed course → clear history, set
    `courseStable=false`, `lastSmoothedBearing=NaN`. The previous 90° threshold was too high: a 90°
    corner never triggered a reset, and the 40-m window mixed old/new direction over a long stretch.
  - `markerBearing` = latest segment bearing (freshest stable signal), falling back to the window
    course, then the last smoothed value.
  - **Rule: Turn reset keeps the fresh segment** the segment that triggers the reset IS the new
    direction — it is preserved as `lastSegmentBearing` so the marker points the new way at the
    turn fix itself instead of falling back to the old direction for a fix.
- **Rule: No `-1.0` sentinel** if no bearing is available, return `NaN`. `-1.0` is treated by
  `!isNaN()` checks as a valid ~1° bearing → map drifts north after reset.
- Course is derived from **raw provider positions** (not navigation-filtered positions) — the nav
  engine filters marker *position*, the location layer owns *bearing*.

`MapCanvasViewModel` keeps only render-side concerns:

- Deadband vs the rendered angle (`MIN_BEARING_DELTA_DEG` = 2°): map rotation only moves when the
  smoothed bearing exceeds this — prevents re-rendering on every small change.
- Per-render rate clamp (`MAX_ANGLE_RATE_DEG_PER_RENDER` = 90°): a sharp turn completes in 1-2
  frames instead of slowly crawling.
- Fallback when no bearing: keep last used bearing (`lastUsedBearing`) / last angle
  (`lastUsedAngle`) — NEVER jump back to north-up (0°), except at the very beginning.
- Marker/map decoupling: marker arrow = `fix.markerBearing` (freshest), map rotation =
  `fix.smoothedBearing` (smoothed).
## 8. Marker Rules

- **Rule: Render target** the marker is a Compose overlay (`LocationMarkerOverlay`) drawn on top of the
  rendered map bitmap in `MapCanvasScreen`. It is NEVER written into cached tiles, the back buffer,
  or the front buffer — those hold only static map content. A marker-only move/hide triggers no
  native render, no epoch bump, and no tile invalidation.
- **Rule: Position updates on every fix** the VM calls `updateMarkerState` (renderer snapshot + direct
  `uiState.gpsMarker*` update) on every distinct fix — the overlay shows the latest fix
  immediately, independent of the render cadence. The frame collector does NOT overwrite the
  marker fields (it only sets bitmap/viewport), so a throttled or skipped render (no > 5 m
  movement) never freezes the marker.
- **Rule: Projection** the overlay projects against `uiState.renderViewport` (from the emitted
  `frameFlow`) — the viewport of the bitmap actually on screen. NEVER the live
  `currentViewport`, which leads the rendered frame during gestures. A fresh fix projected on a
  stale map is still anchored correctly (the marker sits at the vehicle's position on the
  displayed bitmap).
- **Rule: Position** in follow mode ALWAYS the raw (or navigation-filtered) GPS position
  (`followMarkerLat/Lon` → `uiState.gpsMarkerLat/Lon`), never the smoothed camera center. A smoothed
  marker drifts off the road (at 20 m/s already ~9 m offset visible). Non-follow mode uses the raw fix.
- **Rule: Arrow orientation (important)** the marker arrow uses the FRESHEST direction signal,
  independent of map smoothing. Priority chain:
  1. Window course (`courseBearing`) — reacts immediately after turn reset.
  2. Last segment bearing (`lastSegmentBearing`, newest 2 points ≥ 2 m) — also applies when the
     window course is still NaN (after reset < 10 m).
  3. Last used bearing (`lastUsedBearing`).
  The VM publishes this as `uiState.gpsMarkerBearing`; the overlay draws the arrow at
  `screenBearing(bearing, frontBufferAngle)`. In north-up orientation the bearing is `-1` and the
  arrow points north on the map.
- Map rotation may lag after corners (low-pass + rate limit) — the arrow must still point along
  the new road immediately.
- **Rule: Do not** fall back to raw `Location.bearing` (noisy, provider-dependent) — except in
  non-follow mode, which mirrors the old behavior.
## 9. Map Rotation (Follow-Mode)

- Target angle from smoothed course: `smoothedAngle = -radians(effectiveBearing)`.
- Deadband: ignore angle changes < `MIN_BEARING_DELTA_DEG` (2°) relative to the rendered angle
  → no jitter rendering.
- Rate limit: max. `MAX_ANGLE_RATE_DEG_PER_RENDER` (90°) per render toward the target angle.
  (30°/frame was too slow: a 92° corner took ~13 s at ~1 render/s.)
- Reference for deadband/rate limit is `mapRenderer.renderedAngle` (front-buffer angle), not the
  mutable `currentAngle`.
- `angleChanged` comparison with tolerance (`isAngleSame`, 1e-4 rad) — no exact double comparison.
## 10. Camera Center (Follow-Mode)

- **Rule: No EMA smoothing of the center** (`centerSmoothAlpha = 1.0`): viewport center = raw GPS
  position, so the marker stays on the road.
- Only teleports > `centerSmoothMaxJumpM` (500 m) reset the center directly.
## 11. Auto-Zoom

- **Rule: The speed each surface feeds the table is its own** (`autoZoomSpeedKmH` in
  `ui/map/AutoZoomSpeedSource.kt`, spec `auto-speed-zoom` — Auto-zoom speed source per surface): the
  phone uses its GPS fix speed while free driving and the navigation engine's speed while route
  guidance is active; the car uses the engine's speed (it has no other). Measured 2026-10-12: the
  phone read the engine speed unconditionally, which is not fed outside navigation, and a
  `lastValidSpeedKmH` seed of 20.0 stood in for it — a free drive at 90+ km/h kept the 20 km/h
  target (16.0) instead of the highway one (13.0). The seed is gone; an unknown speed now reaches
  the table as NaN and the table returns its stationary level (a NaN input otherwise fell through
  every comparison to the LAST entry, the 130 km/h magnification).
- Convergence is distance-proportional, not gated. `SpeedZoomTable.stepToward(current, target)`
  (`core/src/main/java/com/naviveylin/core/SpeedZoomTable.kt`) moves a constant fraction of the
  remaining gap (`ZOOM_CONVERGENCE_GAIN = 0.3`), capped at `MAX_ZOOM_STEP_PER_UPDATE = 0.5`
  magnification levels per position update. The zoom moves quickly when far from the target and
  slows down near it, so a large speed change converges over several seconds instead of snapping.
- The old commit gate is REMOVED (`ZOOM_COOLDOWN_MS` + `ZOOM_COMMIT_SAMPLES` stable samples +
  ≥ 1 full zoom level). Its ≥ 1-level rule was itself visible as full-level snap jumps; the
  sub-level pumping it guarded against (`diff=0.0` from comparing rounded values at target 15.666
  vs. current 16) is now prevented structurally by the deadband below.
- Deadband replaces target hysteresis: when `abs(target - current) < ZOOM_EPSILON` (0.05 levels)
  the step returns the current magnification unchanged, so the caller commits nothing and triggers
  no re-render. Compare the RAW fractional values — never rounded integer magnifications.
- Commit the fractional target: never round to a whole level at commit time (the `2^z` scale
  conversion stays at the JNI boundary, sec 12). The displayed zoom then eases to the target over
  ~650 ms through the front-buffer animation (spec `smooth-zoom`), not a single-frame jump.
- **Rule: A change larger than the window the frame in hand can serve is WALKED (`core/ZoomWalk`).**
  The overrun frame (`DEFAULT_CANVAS_OVERRUN = 1.2`) covers `log2(1.2) = 0.263` levels, kept at
  `ZOOM_BLIT_WINDOW = 0.25` with margin; a commit farther than that from the frame in hand does not
  land in one frame: the committed magnification advances one step per LANDED render
  (`MapCanvasViewModel.advanceZoomWalk` on the frame collector), each step is requested through
  `MapRenderer.requestRenderImmediate` (a walk step is paced by the landing of the previous step's
  frame, so the pan/zoom debounce would only add `zoomDebounceMs` per step), and the last step lands
  exactly on the recorded target. Without this, an entry from a browse viewport at mag 13 to a speed
  target of 17 showed the old raster scaled across a 16x area change (the phone's reported
  "wrong-scale picture", parity with `AutoMapRenderer.advanceZoomWalk` on the car).
- **Rule: The walk's pin on the render magnification lives exactly as long as the walk.** `ZoomWalk.renderMag`
  honours its `step` only while a walk is pending and falls back to the committed magnification
  otherwise. A landed walk keeps its `step` deliberately — the display target and the landing check
  read it — but it must not pin the pipeline: otherwise every magnification committed outside the walk
  (`fitViewportToRoute`, the follow-engage preset, the mode presets, a re-center) is silently discarded
  (the dated measurement is in `MapFollow.why.md` §11: the route overview fit committed mag 10.0 and the frame was
  rendered at the landed auto-zoom walk's 17.756, so the fitted route lay outside the frame until an
  unrelated render put the camera back on the vehicle). The reader bounds the pin, not a `cancel()`
  obligation at every magnification writer.
- **Rule: The viewport keeps the FINAL magnification while the display walks.** `viewport.magnification`
  is the recorded (and persisted) target; `MapCanvasUiState.zoomWalkStepMag` carries the step the
  render pipeline and the display animation are on, and `zoomAnimationTargetMag` derives the value
  the screen eases toward. Persistence routes through `ZoomWalk.persistMag`, so a walked step is
  never written (spec `smooth-zoom` - Viewport records final magnification). A programmatic camera
  fit (POI fit) passes `walk = false` and lands directly - it does not run the display animation.
- **Rule: The display animation pivots on the resolved follow anchor while a follow mode is active**
  (`zoomAnimationAnchor`: the anchor fraction times the canvas, for every preset), and on the screen
  center otherwise for the inputs that keep the center (zoom buttons, keyboard), on the cursor for the
  scroll wheel, and on the point it landed on for a double tap (spec `smooth-zoom` - A double tap steps one
  level at its own point). A retracked step keeps the last anchor the display was given by an input - which is
  the screen center for the inputs that keep the center (zoom buttons, keyboard) - rather than recomputing one
  from the center. The marker overlay receives
  the same pivot,
  so the vehicle cannot drift off its anchor slot while the animation plays. The applied display
  scale goes through `ZoomWalk.displayScale`, which clamps to the window - a guard so a change that
  ever lands un-walked degrades to a bounded scale instead of an uncovered strip of surface color.
- Phone and Android Auto share the convergence primitive (`SpeedZoomTable.stepToward`); Android
  Auto commits the fractional magnification through the fractional viewport-render parameter.
