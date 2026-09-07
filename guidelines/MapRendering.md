# Map Rendering — Knowledge Base & Rules

This document preserves the rules, constraints, and pitfalls of the map render pipeline
(`MapRenderer`, `MapCanvasViewModel`, `LocationService`) that we worked out over several
debugging rounds. Read before making changes to rendering/follow-mode/GPS processing!

Target behavior: the map stays stable in follow mode (no jumps), the GPS marker sits exactly on
the vehicle position, and after corners the marker arrow immediately points in the direction of
travel while the map itself rotates at its own pace.

---

## 1. Render Pipeline Architecture

- Two user-selectable modes in `MapRenderer` (`renderMode`: `TILES` default, `DIRECT`):
  - **TILES mode — tile path** (`renderMode == TILES && (angle == 0.0 || !forceFullRender)`):
    visible geo bounds are covered from geographic tiles in `TileCache`; missing tiles are
    rendered natively one per tile (`renderTilePixels`, 256px @ 96dpi scaled by device dpi) and
    cached; the composed frame is screen-sized (e.g. 1080×2400). Rotated live previews compose
    tiles north-up and rotate the whole canvas about the viewport center — labels stay north-up.
  - **DIRECT mode — full native path**: every render goes through `MapRenderUtil.renderToBitmap`
    onto an overrun canvas (1296×2880 = 1.2× screen), then `extractCenterRegion` → 1080×2400.
    Labels render natively in the viewport direction. No tile cache is read or written.
  - Forced full renders (rotation gesture end, `forceFullRender = true`) use the full native path
    in BOTH modes when the angle is non-zero, so labels are drawn in the correct direction.
- Path selection in `executeRender`:
  `val tilePath = renderMode == RenderMode.TILES && (job.angle == 0.0 || !job.forceFullRender)`
  - Tile path bails (→ full native render) on antimeridian views, tile render failure, or a tile
    grid wider than the 4×4 sanity guard.
- The overrun buffer allows sub-region blits for small pans before a full render is needed.
- Both paths MUST ALWAYS deliver a screen-sized frame to `_frontBufferFlow`. An overrun-sized
  frame in the UI = bug (wrongly scaled, marker offset).
- **Overlay stage (final):** the GPS marker is NOT part of the native render. After a frame is
  emitted, `MapCanvasScreen` composes `LocationMarkerOverlay` on top of the displayed bitmap.
  Tiles, back buffer, and front buffer contain only static map content — never the marker.
- **Mode switch:** `onSetRenderMode` in the ViewModel persists the choice and applies it via
  `mapRenderer.invalidateStyle()` (epoch bump + tile cache clear + forced full re-render), so no
  tiles/buffers from the other mode survive. `renderMode` is a `@Volatile` field read at job
  execution — an in-flight job may finish in the old mode; its result is discarded by the epoch
  check.
- **DIRECT mode low-zoom risk:** at mag < 4 a full native render covers huge world viewports
  (z=2 ~5s, z=1 hangs). The zoom floors (`MIN_MAG`/`GESTURE_MIN_MAG` = 4) prevent gesture-driven
  low zooms in both modes; DIRECT at mag 4 is still a full-world first draw — expect slowness
  before the frame lands.
- **Removed:** the old screen-space tile-split helpers (`TileCache.storeTiles`/`compose`,
  `computeTileGrid`, `MapRenderer.blitSubRegion`) are gone; the tile cache is geographic-only.

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
- Former native convention (C++): `screenBearing = gpsMarkerBearing + angle`. For follow-direction,
  `angle = -bearing` (radians) so the marker arrow points up. **Do not flip the sign.**
- Kotlin overlay convention (same sign): `ProjectionUtils.screenBearing(bearingDeg, angleRad)` =
  `bearingDeg + toDegrees(angle)`. The marker is drawn by the Compose overlay, so the C++ side no
  longer computes a screen bearing — keep both sides on the same formula.

## 7. Bearing Smoothing (location layer)

Bearing smoothing lives in `LocationService` (`BearingFilter`), NOT in the ViewModel. The location
layer emits a uniform `GpsFix` with two bearings, provider-agnostic to consumers:

- **`smoothedBearing`** — for map rotation. Stable, no render churn.
- **`markerBearing`** — for the marker arrow. Freshest signal, no added lag.

Provider-aware inside `LocationService` only:

- **Fused path**: Fused already smooths its bearing (sensor fusion + Kalman). `markerBearing` =
  `loc.bearing` as delivered (zero added lag); `smoothedBearing` = light EMA (alpha 0.5) on
  `loc.bearing` — do NOT re-derive course from positions on the Fused path.
- **LocationManager path** (GMS-less devices): fakes Fused quality — course-over-ground derived
  from the recent position track:
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
  - `markerBearing` = latest segment bearing (freshest stable signal), falling back to the window
    course, then the last smoothed value.
  - **Turn reset keeps the fresh segment:** the segment that triggers the reset IS the new
    direction — it is preserved as `lastSegmentBearing` so the marker points the new way at the
    turn fix itself instead of falling back to the old direction for a fix.
- **No `-1.0` sentinel:** if no bearing is available, return `NaN`. `-1.0` is treated by
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

- **Render target:** the marker is a Compose overlay (`LocationMarkerOverlay`) drawn on top of the
  rendered map bitmap in `MapCanvasScreen`. It is NEVER written into cached tiles, the back buffer,
  or the front buffer — those hold only static map content. A marker-only move/hide triggers no
  native render, no epoch bump, and no tile invalidation.
- **Position updates on every fix:** the VM calls `updateMarkerState` (renderer snapshot + direct
  `uiState.gpsMarker*` update) on every distinct fix — the overlay shows the latest fix
  immediately, independent of the render cadence. The frame collector does NOT overwrite the
  marker fields (it only sets bitmap/viewport), so a throttled or skipped render (no > 5 m
  movement) never freezes the marker.
- **Projection:** the overlay projects against `uiState.renderViewport` (from the emitted
  `frameFlow`) — the viewport of the bitmap actually on screen. NEVER the live
  `currentViewport`, which leads the rendered frame during gestures. A fresh fix projected on a
  stale map is still anchored correctly (the marker sits at the vehicle's position on the
  displayed bitmap).
- **Position:** in follow mode ALWAYS the raw (or navigation-filtered) GPS position
  (`followMarkerLat/Lon` → `uiState.gpsMarkerLat/Lon`), never the smoothed camera center. A smoothed
  marker drifts off the road (at 20 m/s already ~9 m offset visible). Non-follow mode uses the raw fix.
- **Arrow orientation (important):** the marker arrow uses the FRESHEST direction signal,
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
- **Do not** fall back to raw `Location.bearing` (noisy, provider-dependent) — except in
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
- **Fractional magnification (continuous pinch)**: the committed viewport magnification is a
  `Double` zoom level (z, fractional from continuous pinch commits). Kotlin-internal magnification
  is always a *level-style* value; the JNI render/projection boundary converts to the libosmscout
  *scale factor* `2^z` once (JNI `SetMagnification(double)`). Tile lookups snap to
  `floor(log2(mag))` internally — never round fractional mags at commit time (only discrete
  controls snap via `zoomIn/zoomOut`).
- **No angle epsilon check against `currentAngle`**: during a slow render `prepareViewport`
  changes the target angle; the finished frame would otherwise be discarded (old cause of "map
  shows old image / jumps"). The next job picks up the new angle.
- Render job snapshots the viewport AND the marker state at enqueue (`PendingRender`); the marker
  snapshot is emitted with the frame (`frameFlow`) — the overlay reads that frame's snapshot per
  frame, so there is no marker/center skew to worry about.

## 13. Sub-Region Blits

- The blit delta MUST be rotated by the viewport angle (`dx*cos − dy*sin`, `dx*sin + dy*cos`).
  Without rotation, a blit at -40° map angle shifts the content horizontally by up to
  `sin(40°) × move` wrongly.
- Blit only at the same magnification; on zoom change keep the old correct frame, don't show a
  scaled placeholder.
- Blits copy pure map content — the marker overlay is drawn by Compose on top afterwards, so a
  blit can never carry stale marker pixels.
- A blit is a TILE-PREVIEW optimization only: the blit-covered branch in `submitDebounced` MUST NOT
  discard a pending FORCED render (`forceFullRender=true` — route set/clear, favorites, search
  selection, stylesheet switch, epoch bump). Discarding it would leave the new overlay undrawn
  until a gesture triggers a full render (observed symptom: stale route on the map after a reroute
  until the user pans). Keep the pending render (`pendingRender?.forceFullRender != true` guard)
  so overlay changes render even when the camera never moved.
- Tile-path rotated composition MUST rotate about the viewport center (tiles placed north-up,
  `canvas.rotate(deg, W/2, H/2)`), NEVER about each tile's own corner — corner pivots shift
  content by up to `d·θ` (d = tile distance from center, θ = rotation) and break marker-overlay
  alignment.

---

## 14. Rotation Gesture Display-Layer Handoff

- The live rotation angle during a two-finger gesture lives in the graphicsLayer
  (`rotationZ = gestureRotation`), NOT in the rendered bitmap (which stays at the committed
  angle until gesture end).
- The rotation AND zoom pivot at the FINGER MIDPOINT (the graphicsLayer `transformOrigin` =
  `gesturePivot`): the geographic point under the midpoint at gesture start stays under the
  midpoint for the whole gesture, and the gesture-end commit adjusts the viewport center so the
  anchor holds in the rendered frame (`ProjectionUtils.rotateZoomAtFocalPoint` — the
  generalized focal-point commit; Δ=0 reduces to `zoomAtCursor`).
- Rotating around an off-center pivot can expose empty corners at large angles (accepted — the
  overrun buffer margin covers moderate angles; standard map-app behavior).
- At gesture end the committed angle is set and a full render is requested, but the render is
  async (debounce + background job). The display MUST keep the final angle applied until the
  front buffer swaps to the committed angle — otherwise the old bitmap (old angle) shows
  unrotated for the render duration and the map temporarily jumps back to the pre-gesture
  angle (observed symptom: rotate → map snaps back → restores).
- The hold is a FLAG (`rotationHoldActive` in `MapCanvasScreen`); the held ANGLE is derived per draw as `committedAngle − frontBufferAngle` (`rotationDisplayTheta`) from the SAME collected state the draw reads — so it zeroes atomically with the committed bitmap swap and there is no one-frame window that draws the new bitmap with a stale rotation. A STORED hold value cleared by the frame loop is wrong: the clear (direct `uiState` read) and the draw (collected state) can straddle the render-land emission and show a double-rotation twist for one frame.
- At render land the old rotated frame is crossfaded into the new native render (mirror of the zoom crossfade, `crossfadeAngle`), masking tile-vs-native rasterization differences; the old frame is drawn rotated about the gesture midpoint (`drawFrontFrame` `rotationDegrees`/`rotationPivot`), which equals the committed render's pivot.
- The hold disarms when the front buffer reaches the committed angle or on the next gesture start. The derived gap composes independently with the zoom handoff: `rotationZ` and `scaleX/Y` are applied together in the graphicsLayer and each clears on its own condition.

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
11. Marker baked into cached tiles / reused front buffer (ghost marker artifacts after it moves)?
    → marker must be a Compose overlay; tiles/buffers must contain only map content.
12. Overlay projecting against `currentViewport` instead of `frontBufferViewport`? → marker
    misplaced during pan/zoom/rotate gestures; always use `uiState.renderViewport`.
13. `setGpsMarker`/`clearGpsMarker` or native `gpsMarker` state re-introduced? → forbidden: the
    marker renders exclusively via `LocationMarkerOverlay`.
14. Tile-path rotation pivots on each tile's own corner? → marker overlay (projected about the
    viewport center) diverges from the map by up to `d·θ` — rotate about the viewport center.
15. Overlay fed the LIVE GPS fix instead of the frame marker snapshot? → marker jumps ahead of the
    road by up to one fix of travel while frames lag; always draw the snapshot that rode with the
    displayed frame (`frameFlow`).

---

## 14. Android Auto renderer — smooth follow (overrun + blit + extrapolation)

`AutoMapRenderer` (spec `auto-smooth-follow`) mirrors the phone's overrun/blit machinery on the
car Surface:

- **Overrun buffer**: every full native render is at `OVERRUN_FACTOR` (1.2×) the surface size and
  is KEPT as the overrun buffer (not recycled) for sub-region blits. The visible region is drawn
  centered: `dx = (surfaceW - bitmapW) / 2`.
- **Sub-region blit**: a viewport change within the overrun region is served by
  `lockCanvas → drawBitmap(overrun, dx, dy) → unlockCanvasAndPost` — no native render. The blit
  offset comes from `FollowPrediction.displayOffsetPx` (rotated-frame clamp, same rotation rule as
  the phone blit). Beyond the margin → full render at the new center.
- **Extrapolation loop**: a gated ~30 fps coroutine eases the displayed position toward
  `FollowPrediction.predictedPosition` and blits the delta; the GPS/destination markers are drawn
  at the displayed position so they glide with the map. Gate: resumed + follow mode + speed
  > ~1 m/s + fresh fix + valid surface (battery/thermal on head units).
- **Display-only prediction**: predicted positions never reach the navigation engine — the
  prediction lives entirely inside the renderer.
- **CRITICAL — overrun bitmap lifecycle**: the overrun bitmap is recycled when a new full render
  swaps it in. The extrapolation loop and the render loop run on different coroutines, so the
  read of the overrun bitmap AND the blit draw MUST happen under the shared `surfaceLock` —
  otherwise a concurrent full render recycles the bitmap mid-draw ("trying to use a recycled
  bitmap" crash). The full native render itself runs OUTSIDE the lock so the loop can keep
  blitting the old frame while the render is in flight.
- **CRITICAL — display ownership in follow mode**: in follow mode the extrapolation loop owns
  `displayLat/Lon` (the eased predicted position). `fullRender` and the `renderFrame` blit path
  MUST NOT reset the display to the render target when `followMode` is engaged — a render
  triggered by a transient follow-off (heading-up `setViewport`) would otherwise yank the eased
  display back every fix (visible "pumping"). Only set `display = viewport` when `!followMode`.
- **reengageFollow must anchor the viewport**: screens that do transient
  `setViewport(...) → reengageFollow()` per fix (heading-up rotation) must make
  `reengageFollow` set `viewport = fix` + emit — otherwise the next `setViewport` reads a stale
  `viewportState` (initial center), the pending render targets it, and the display pumps between
  the stale center and the fix. `reCenter` keeps the snap (user re-center button);
  `reengageFollow` re-engages without snapping the display.
- **Stale fix must ease back, never freeze**: a GPS gap does NOT gate the extrapolation loop
  off — `FollowPrediction.predictedPosition` holds past its extrapolation window, so the display
  eases back to the last fix. Freezing the display at the last predicted position reads as a
  massive overshoot during gaps.
- **Blit eligibility**: only pure viewport/marker changes may blit. Favorites, route, DPI, and
  surface changes force a full render (`blitEligible = false`) — a blit would show stale
  native-rendered content.

---

## 15. Day/Night (daylight flag) contract

- The stylesheet `daylight` flag selects the map variant: set = daylight, unset = dark.
  Native side: `setStyleSheetFlag("daylight", !dark)` → `DBThread.SetStyleFlag` →
  `LoadStyleInternal(stylesheet, flags)` reloads the variant on the DB thread; the next
  render uses it. No tile cache survives the reload (variant switch must not show
  tiles from the other variant).
- **Phone**: the environment source is `DarkModeController` (system night mode, or the
  ambient light sensor when enabled — see `dark-mode` spec). `pushDarkPresentation`
  dedupes and calls `invalidateStyle()` (epoch bump + full re-render).
- **Android Auto**: the source is the HOST's day/night state, never the phone's system
  mode — the car process runs on the phone but the head unit decides (its own light
  sensor / time). `NavigationSession.hostDark` (init from `CarContext.isDarkMode()`,
  updated in `onCarConfigurationChanged`) feeds both map screens; `CarDaylightApplier`
  dedupes the push; `invalidateStyle()` (blit bypass + full render) re-renders so the
  stale-variant overrun buffer is never blitted. Templates are host-rendered and
  follow the host automatically — only the app-drawn surface needs this.
- **Startup race (both variants)**: `SetStyleFlag` is a silent no-op until a DB is
  open, so the initial flag push can be dropped while the DB is still initializing
  (warmup). The flag MUST be re-pushed once the DB is ready: phone re-pushes on the
  first rendered frame (`stylePushedToNative` in `MapCanvasViewModel`); AA re-pushes
  after a successful style load and at surface creation (`pushHostDark` in both map
  screens, with `CarDaylightApplier.reset()` so the re-push is not deduped away).
- Initial `isDarkMode()` may be false until the host sends configuration
  (`UI_MODE_UNKNOWN`); the first `onCarConfigurationChanged` corrects it (one extra
  render at startup).

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
