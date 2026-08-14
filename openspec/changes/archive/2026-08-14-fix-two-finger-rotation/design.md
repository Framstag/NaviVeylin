# Design: Fix Two-Finger Rotation

## Context

The map canvas gesture handler in `MapCanvasScreen.kt` already contains rotation math, but the gesture is unusable: rotating two fingers produces a zoom-like effect. Investigation shows four defects:

1. **Pan fires during rotation.** The pan branch tracks the first finger (`pointerId`) and pans whenever it moves > 0.5 px. During a rotation the first finger moves in an arc, so the map center drifts.
2. **Zoom fires on distance jitter.** The zoom threshold is `abs(zoomFactor - 1f) > 0.03` (3%), and the delta logic is `if (zoomFactor > 1.05f) 1 else -1` — so a 3–5% distance *increase* zooms *out*. Rotation gestures naturally change finger distance by a few percent.
3. **Rotation renders are slow.** `MapRenderer.executeRender` uses the fast tile-cache path only when `job.angle == 0.0`; any non-zero angle falls to a full native render at 1.2× overrun with a 200 ms debounce. Zoom stays on the fast path, so the user sees zoom but not rotation.
4. **"Always north" survives the gesture.** The gesture calls `disengageFollowMode()` but leaves `freeFormNorthUp`/`navNorthUp` set, so re-engaging follow mode snaps back to north-up and the compass still reports north-up.

## Goals / Non-Goals

**Goals:**
- Two-finger rotation rotates the map in real time, without spurious pan/zoom.
- Rotation disengages follow mode and the active north-up flag.
- Rotation renders use the fast tile-cache path so they are as responsive as zoom.
- Centroid-based panning when two fingers move together (rotation-aware).

**Non-Goals:**
- Changing the compass button behavior or layout.
- Changing the native renderer / JNI (angle support already exists end-to-end).
- Rotation animation easing beyond the existing render pipeline.

## Decisions

### D1: Rewrite the multi-touch branch of the gesture handler

Extract the gesture handler from the inline `pointerInput` block in `MapCanvasScreen.kt` into a reusable `Modifier.mapGestureHandler(MapGestureCallbacks)` in a new file `MapGestures.kt` (using `composed` + `rememberUpdatedState` so the handler always calls the latest callbacks). The handler reports raw gesture deltas; `MapCanvasScreen` wires the callbacks to the ViewModel and projection math. This makes the gesture logic directly testable with Compose UI tests.

The handler logic:

- **Single finger** (1 pressed change): pan exactly as today (first-finger delta).
- **Two fingers** (≥ 2 pressed changes): use `event.changes[0]`/`event.changes[1]` (Compose sorts changes by pointer id, so the pair is stable):
  - **Pan** via the *centroid* movement: `centroid = (c1.position + c2.position) / 2`, delta vs. previous centroid. A pure rotation around the midpoint keeps the centroid fixed → no pan.
  - **Rotate** via the finger-line angle delta (existing math, kept): `atan2` of `c2 - c1` for current vs. previous positions, normalized to `[-π, π]`. On `abs(delta) > 0.05 rad`, call `onRotate`.
  - **Centroid pivot**: the handler reports the two-finger centroid on every multi-touch event (`onGestureCentroid`); the screen clamps it to the canvas bounds. This guards against corrupted pointer positions from multi-touch emulation (which can be millions of px) — an unclamped pivot would make `zoomAtCursor` and the visual transform pivot produce a garbage center / swing the map away.
  - **Zoom** via the continuous finger-distance ratio vs the distance at the moment the second finger went down (ignored when the fingers started closer than 20 px). The caller applies it as a visual scale clamped to `[0.25, 4.0]` (±2 mag levels) and commits the magnification change on gesture end (`round(log2(totalZoom))` mag steps, clamped to `MIN_MAG`/`MAX_MAG`).

The centroid pan must be rotation-aware (D4); the single-finger pan keeps its current behavior (north-up math) to avoid scope creep — it is pre-existing and not part of this change.

### D2: ViewModel method to disengage follow mode and north-up

Add `MapCanvasViewModel.onManualRotation(angleDeltaRadians: Double)`:

1. `disengageFollowMode()` (existing).
2. Clear the active north-up flag: if `_navigationViewModel?.state?.value?.isNavigating == true` then `navNorthUp = false`, else `freeFormNorthUp = false` (mirrors how the compass picks the active flag in `MapCanvasScreen`).
3. `updateAngle(viewport.angle + angleDeltaRadians)` (existing).

The gesture handler calls this instead of `disengageFollowMode()` + `updateAngle()`. Pan and zoom keep calling `disengageFollowMode()` only — they do not conflict with north-up.

### D3: No renders during the gesture — visual transform on the current bitmap

Rendering during a combined zoom+rotate gesture is too slow (each zoom step re-renders tiles at the new magnification). Instead, the multi-touch gesture is applied **entirely as a visual transform of the current bitmap**, with no render calls until the gesture ends:

- `MapCanvasScreen` tracks the accumulated gesture state (`gestureRotation`, `gestureZoom`, `gesturePan`, `gestureCentroid`) in Compose state.
- The Canvas applies a `graphicsLayer` transform: `rotationZ` (accumulated angle), `scaleX/Y` (accumulated zoom factor), `translationX/Y` (accumulated centroid pan), with `transformOrigin` at the gesture centroid (so the scale matches the commit's `zoomAtCursor`).
- `onCentroidPan` updates the center state (no render) and accumulates the pan; `onRotate` disengages follow mode + clears north-up and accumulates the angle; `onZoom` records the continuous zoom factor.
- On gesture end (`onRenderRequested`), the accumulated changes are committed to the viewport (`updateAngle` normalized to `[-π, π]`, `updateMagnification` with `round(log2(totalZoom))` steps + `zoomAtCursor`, center already updated) and a single render is requested — a forced full native render when the angle or magnification changed (labels drawn correctly), or the fast tile path for a pure multi-touch pan.

`MapRenderer.executeRender` uses the tile path when `job.angle == 0.0 || !job.forceFullRender`; a forced render always uses the full native path. `forceFullRender` is plumbed through `requestRender` → `submitDebounced` → `PendingRender` → `enqueueRenderJob` → `RenderJob`.

### D4: Rotation-aware drag delta in ProjectionUtils

Add `ProjectionUtils.dragDeltaToNewCenterRotated(dx, dy, angle, mag, viewWidth, viewHeight, centerLat, centerLon, dpi)`:

```
geoEast  = -dx·cos(angle) - dy·sin(angle)
geoNorth = -dx·sin(angle) + dy·cos(angle)
newLon = centerLon + geoEast / scaleGradtorad
newLat = toDegrees(asin(tanh(geoNorth / scale + latOffset)))
```

Derived from the native `MercatorProjection.PixelToGeo` rotation (`geo = R(angle)·screen` in the east-north frame). At `angle = 0` it reduces exactly to the existing `dragDeltaToNewCenter`. The centroid pan in D1 uses this.

### D5: Shorter debounce for angle-only changes

In `MapRenderer.startDebounceLoop`, split the zoom/rotation debounce so the fast tile-path preview stays smooth:

```
isZoom   = req.mag != frontBufferMag
isRotate = req.angle != frontBufferAngle
timeout  = when { isZoom -> zoomDebounceMs; isRotate -> rotateDebounceMs; else -> panDebounceMs }
```

with `rotateDebounceMs = 50L`.

## Risks

- **Commit jump for off-center rotation.** The visual transform rotates around the gesture centroid; the committed native render rotates around the viewport center. For gestures with the centroid near the screen center (typical) the jump is negligible.
- **Compass lags during the gesture.** The compass reads `viewport.angle`, which only updates on commit; it snaps to the final angle on gesture end.
- **Slow gesture-end render.** The forced full render at non-zero angle is a full native render (no tile cache), so the final frame takes a few hundred ms. Pre-existing behavior.
- **Gesture regression for single-finger pan.** The single-finger branch is unchanged; the multi-touch branch only activates with ≥ 2 pressed pointers.
- **Compass state after rotation.** The compass reads the same `freeFormNorthUp`/`navNorthUp` flags that D2 clears, so it updates automatically.

## Limits

- Visual zoom during a multi-touch gesture is clamped to `[0.25, 4.0]` (±2 magnification levels); the committed magnification is additionally clamped to `MIN_MAG`/`MAX_MAG` (4–20).
- The rotation angle is normalized to `[-π, π]` on commit and the visual `rotationZ` to `[-180, 180]`, so repeated rotations cannot grow the angle unbounded (a `>360°` angle would otherwise accumulate).
- The zoom ratio is ignored when the fingers started closer than 20 px (unreliable ratio).

## Testing

- `ProjectionUtilsTest`: rotated drag delta reduces to north-up at angle 0; pan right at 90° moves center south; round-trip consistency.
- `MapCanvasViewModelFollowModeTest`: `onManualRotation` clears follow mode and the active north-up flag; re-engaging follow mode does not snap back to north-up.
- `MapGestureComposeTest`: two-finger rotation reports clockwise/counter-clockwise angle deltas, constant-distance rotation does not zoom or pan, rotation with small distance jitter reports zoom factors near 1.0, sustained pinch reports a growing zoom factor, pinch reports zoom, render is requested exactly once on gesture end, single-finger drag reports pan.
- `MapRendererRotatedRenderTest`: north-up render emits the front buffer; forced full render carries a non-zero angle through to the front buffer. (The non-forced rotated tile composition is not exercised — Robolectric's shadow Canvas hangs on rotated drawBitmap of large tiles; the app path is verified manually.)
- `TileCacheRenderTest`: rotated tile range covers all four viewport corners; rotated tile edge matches the projection rotation.
- Manual: two-finger rotation on device — map rotates/zooms/pans live via the bitmap transform with no render calls, then redraws once with upright labels on gesture end; pinch still zooms; compass flips to follow-direction after rotation; re-engaging follow mode keeps the manual angle.
