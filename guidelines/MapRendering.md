# Map Rendering — Knowledge Base & Rules

This document preserves the rules, constraints, and pitfalls of the map render pipeline
(`MapRenderer`, `MapCanvasViewModel`, `LocationService`) that we worked out over several
debugging rounds. Read before making changes to rendering/follow-mode/GPS processing!

Target behavior: the map stays stable in follow mode (no jumps), the GPS marker sits exactly on
the vehicle position, and after corners the marker arrow immediately points in the direction of
travel while the map itself rotates at its own pace.

---

## 1. Render Pipeline Architecture

- Two paths in `MapRenderer.executeRender`:
  - **Tile path** (`job.angle == 0.0`): assemble tiles from `TileCache` onto a 1080×2400 canvas.
  - **Rotated path** (`job.angle != 0.0`): full JNI render onto an overrun canvas
    (1296×2880 = 1.2× screen), then `extractCenterRegion` → 1080×2400.
- The overrun buffer allows sub-region blits for small pans before a full render is needed.
- Both paths MUST ALWAYS deliver a 1080×2400 frame to `_frontBufferFlow`. A 1296×2880 frame in the
  UI = bug (wrongly scaled, marker offset).

## 2. Bitmap Lifecycle (CRITICAL — caused "jumps" multiple times)

- `Bitmap.createBitmap(src, x, y, w, h)` and `frontBuffer` SHARE pixel memory with `src`.
- The next render writes into the same memory via `backBuffer.setPixels()` → the image displayed
  by Compose is overwritten in place with new content at the old crop offset → horizontal jumps
  without rotation.
- **Rule: Every frame that goes to Compose (`_frontBufferFlow.value`) must be an independent
  copy** (`bitmap.copy(...)` or `extractCenterRegion(...).copy(...)`).
- `extractCenterRegion` MUST always return `copy()` — even when `fbW == sw` (otherwise Compose
  draws the front buffer directly, which gets recycled on the next render).
- Sub-region blits: copy the region first, then `recycle()`. `Bitmap.createBitmap` + `recycle` on
  a shared region causes a "trying to use a recycled bitmap" crash.
- Tile path: `frontBuffer` and `_frontBufferFlow` each get a copy, then recycle the original
  bitmap.

## 3. GPS Deduplication

- Fused + LocationManager deliver the same fix with the same `location.time` but possibly with
  different `bearing`. Therefore:
  - **`LocationService`**: dedupe by `location.time` (identical timestamp).
  - **`MapCanvasViewModel`**: dedupe by timestamp difference (< `GPS_DEDUPE_MS`) + coarse
    coordinate (`1e-6°`, ~11 cm). **Do NOT dedupe by bearing** — different providers attach
    different bearings to the same fix.
- Still forward duplicates to the navigation engine; only skip render work.

## 4. Render Coalescing / Throttling

- Limit follow-mode renders to one per `GPS_FOLLOW_RENDER_INTERVAL_MS` (200 ms).
- Every fix still goes to `prepareViewport` (keep target viewport current), but only one render
  per time window is triggered. This keeps the native pipeline from overflowing.
- `positionChanged` threshold: render only from > 5 m movement (or first fix). Otherwise
  jitter renders.

## 5. Epoch Rules

- `epoch.incrementAndGet()` ONLY on zoom, overlay, style, and marker visibility changes.
- **Pure GPS position updates must NOT increment the epoch** — otherwise the running native render
  is discarded as stale and frame gaps occur ("map freezes / shows old image").
- The conflated render queue guarantees that the next job picks up the latest position.

## 6. Angle Handling

- **Store all angles in radians**; normalize to `[-π, π]` at all storage points:
  `_uiState.viewport.angle`, `MapRenderer.currentAngle`, `MapRenderer.frontBufferAngle`.
- Unnormalized angles grow beyond 2π across renders and produce apparent rotation jumps (the log
  then shows e.g. `angle=14.6` — note: the `prepareViewport` log converts to degrees for display;
  values in the log are degrees, not radians).
- Native convention (C++): `screenBearing = gpsMarkerBearing + angle`. For follow-direction,
  `angle = -bearing` (radians) so the marker arrow points up. **Do not flip the sign.**

## 7. Course over Ground (COG)

Purpose: stable direction of travel from track geometry instead of noisy `Location.bearing`.

- Ring buffer with 10 positions; course = bearing between the newest point and the oldest point
  that is ≥ `MIN_COURSE_DISTANCE_M` (40 m) away.
- **Two-state logic:**
  - `courseStable == false` (after start/teleport/turn reset): short base
    (`MIN_COURSE_DISTANCE_FAST_M` = 10 m) + fast low-pass (`COURSE_LOW_PASS_ALPHA_FAST` = 0.7)
    → new direction established quickly.
  - `courseStable == true` (≥ 40 m track): 40-m base + slow low-pass (0.3) → stable straight ahead.
- **Turn reset:** if the last segment bearing (> `MIN_SEGMENT_FOR_TURN_M` = 8 m) deviates by more
  than `COURSE_TURN_RESET_DEG` (45°) from the smoothed course → clear history, set
  `courseStable=false`, `lastSmoothedBearing=NaN`. The previous 90° threshold was too high: a 90°
  corner never triggered a reset, and the 40-m window mixed old/new direction over a long stretch.
- **No `-1.0` sentinel:** if no course is available, return `NaN`. `-1.0` is treated by
  `!isNaN()` checks as a valid ~1° bearing → map drifts north after reset.
- Fallback when course missing: keep last used bearing (`lastUsedBearing`) / last angle
  (`lastUsedAngle`) — NEVER jump back to north-up (0°), except at the very beginning.
- Logs: `course computed newest=… oldest=… dist=… bearing=…° stable=…` and
  `course history reset: turn …° / teleport …m` are the diagnostic anchors.

## 8. Marker Rules

- **Position:** in follow mode ALWAYS the raw (or navigation-filtered) GPS position
  (`followMarkerLat/Lon`), never the smoothed camera center. A smoothed marker drifts off the road
  (at 20 m/s already ~9 m offset visible).
- **Arrow orientation (important):** the marker arrow uses the FRESHEST direction signal,
  independent of map smoothing. Priority chain:
  1. Window course (`courseBearing`) — reacts immediately after turn reset.
  2. Last segment bearing (`lastSegmentBearing`, newest 2 points ≥ 2 m) — also applies when the
     window course is still NaN (after reset < 10 m).
  3. Last used bearing (`lastUsedBearing`).
- Map rotation may lag after corners (low-pass + rate limit) — the arrow must still point along
  the new road immediately. Geometry: `screenBearing = markerBearing + angle`; with fresh
  `markerBearing` + old `angle` the arrow points on screen exactly in the road direction of the
  new stretch.
- **Do not** fall back to raw `Location.bearing` (noisy, provider-dependent).

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

- **No EMA smoothing of the center** (`centerSmoothAlpha = 1.0`): viewport center = raw GPS
  position, so the marker stays on the road.
- Only teleports > `centerSmoothMaxJumpM` (500 m) reset the center directly.

## 11. Auto-Zoom

- Hysteresis on the RAW target value (`abs(finalTarget - currentMag)`), not on rounded integer
  values. Rounded comparisons reported `diff=0.0` at target 15.666 vs. current 16 →
  sub-level zoom pumping.
- Commit only after cooldown + `ZOOM_COMMIT_SAMPLES` stable samples + ≥ 1 full zoom level.

## 12. Front-Buffer Emission

- Emit the finished frame as long as epoch AND magnification match the job.
- **No angle epsilon check against `currentAngle`**: during a slow render `prepareViewport`
  changes the target angle; the finished frame would otherwise be discarded (old cause of "map
  shows old image / jumps"). The next job picks up the new angle.
- Render job snapshots center/marker at enqueue (`PendingRender`), not at the later
  `submitDebounced` — otherwise old center + new marker in one frame (marker offset jumps).

## 13. Sub-Region Blits

- The blit delta MUST be rotated by the viewport angle (`dx*cos − dy*sin`, `dx*sin + dy*cos`).
  Without rotation, a blit at -40° map angle shifts the content horizontally by up to
  `sin(40°) × move` wrongly.
- Blit only at the same magnification; on zoom change keep the old correct frame, don't show a
  scaled placeholder.

---

## Known Pitfalls (Regression Checklist)

When "map jumps" / "marker wrong" appears, check first:

1. Does `executeRender` emit a 1296×2880 frame instead of 1080×2400? → `extractCenterRegion`
   missing or if/else path broken.
2. Does the emitted frame share memory with `frontBuffer`? → next `setPixels` corrupts the
   displayed image (horizontal jumps without rotation).
3. Angle unnormalized (> π)? → grows across frames, apparent rotation jumps.
4. Is `epoch` incremented on pure GPS move? → running renders get discarded.
5. Does `smoothCourseBearing` return `-1.0` instead of NaN? → drift north after reset.
6. Turn-reset threshold too high (> 45°)? → course mixes old/new direction after corners.
7. Marker uses smoothed instead of fresh course? → arrow points in old direction after corners.
8. Blit delta without rotation matrix? → lateral offset on rotated map.
9. Center smoothed? → marker drifts off the road.
10. Rate limit < 90°/frame? → map rotates too slowly after corners.

---

## Parameter Overview

| Parameter | Value | Purpose |
|---|---|---|
| `GPS_DEDUPE_MS` | 100 ms | Duplicate time window (coordinate + timestamp) |
| `GPS_FOLLOW_RENDER_INTERVAL_MS` | 200 ms | Follow-render throttle |
| `centerSmoothMaxJumpM` | 500 m | Teleport reset center |
| `COURSE_HISTORY_SIZE` | 10 | Course ring buffer |
| `MIN_COURSE_DISTANCE_M` | 40 m | Stable course base |
| `MIN_COURSE_DISTANCE_FAST_M` | 10 m | Fast base after reset/start |
| `MIN_SEGMENT_FOR_TURN_M` | 8 m | Min. segment for turn detection |
| `COURSE_TURN_RESET_DEG` | 45° | Turn-reset threshold |
| `COURSE_LOW_PASS_ALPHA` | 0.3 | Stable course (≥ 40 m) |
| `COURSE_LOW_PASS_ALPHA_FAST` | 0.7 | Fast course (< 40 m) |
| `MIN_BEARING_DELTA_DEG` | 2° | Rotation deadband |
| `MAX_ANGLE_RATE_DEG_PER_RENDER` | 90° | Rotation rate limit per render |
| `ANGLE_EPSILON_RAD` | (removed) | Former angle tolerance — do not reintroduce |
