# Design: Fix Rotation Gesture on Real Devices

## Context

Two-finger rotation does nothing on a real device. `MapGestures.kt` reports rotation only when a single event's finger-line angle delta exceeds `0.05 rad` (2.9°). Real touch hardware samples at 60–120 Hz, so a normal rotation produces per-event deltas of ~0.01–0.03 rad — below the threshold, so `onRotate` never fires and the map never rotates. The emulator check passed because emulator multi-touch sends coarse events and the Compose test rotates 9°/step (0.157 rad/event).

The threshold was originally meant to filter finger jitter, but it is an order of magnitude too large for high-refresh-rate input.

## Goals / Non-Goals

**Goals:**
- Rotation responds to slow rotations with small per-event deltas.
- Jitter (stationary fingers) does not produce visible rotation drift.
- Total reported rotation matches the fingers' total rotation.

**Non-Goals:**
- Changing the compass button behavior or layout.
- Changing the native renderer / JNI (angle support already exists end-to-end).
- Rotation animation easing beyond the existing render pipeline.
- Changing the gesture handler's pan/zoom *detection* semantics (single-finger pan, pinch ratio, centroid pan).

## Decisions

### D1: Accumulate raw angle deltas; report on a small accumulated threshold

In `MapGestures.kt`, replace the per-event check `abs(angleDelta) > 0.05f` with a per-gesture accumulator:

```
rotationAccumulator += angleDelta
if (abs(rotationAccumulator) > ROTATION_REPORT_THRESHOLD_RAD) {
    onRotate(rotationAccumulator)
    rotationAccumulator = 0f
}
```

with `ROTATION_REPORT_THRESHOLD_RAD = 0.01f` (0.57°).

- **Slow rotation works**: deltas sum past 0.01 rad within 1–2 events at 120 Hz and are reported; the total reported rotation equals the fingers' total rotation (loss bounded by one threshold).
- **Jitter is filtered**: stationary-finger angle noise oscillates around zero, so the accumulated sum rarely crosses the threshold; any residual is flushed on gesture end (bounded by 0.01 rad ≈ 0.57°).
- The accumulator is per-gesture state (declared in the `awaitEachGesture` block alongside `gestureStartDist`), so it resets automatically between gestures.

### D2: Flush residual rotation on gesture end

When the tracked pointer lifts (`!change.pressed`), report any non-zero residual accumulator before `onRenderRequested`, so the committed angle matches the fingers' total rotation instead of silently dropping up to 0.01 rad.

### D3: Rotate around the screen center, zoom around the gesture centroid

At ~180° the map swung off-screen ("rotates away"). Two defects in the live visual transform:

1. **Off-center pivot reveals empty canvas.** The emitted bitmap exactly fills the canvas (`extractCenterRegion` crops the 1.2× render to screen size; the draw scales it to fill), so rotating around any off-center pivot swings the map off-screen — worst at 180° where the shift is `2·(pivot − center)`.
2. **Moving pivot re-anchors the accumulated rotation.** `transformOrigin` tracked the gesture centroid; when the fingers' midpoint drifted, the total rotation was re-applied around the new origin, jumping the map by `(I − R(θ))·ΔC` — 2× the drift at 180°.

Fix: apply the rotation around the **screen center** (fixed pivot — no re-anchor jumps, map always covers the canvas, and it matches the committed native render which rotates around the viewport center, so there is no gesture-end jump). The zoom keeps its pivot at the gesture centroid (matching the commit's `zoomAtCursor`) via a translation compensation:

```
T = (1 − s)·R(θ)·(C − O) + P
```

where O is the canvas center, C the gesture centroid, s the zoom factor, θ the accumulated rotation, and P the accumulated centroid pan. At zoom 1 the translation reduces to the pan (pure rotation around the center); at rotation 0 it reduces to the centroid-anchored zoom (the map content at the centroid stays fixed). Implemented as `gestureTransformTranslation` in `MapCanvasScreen.kt`.

### D4: Clamp the visual zoom to the commit's headroom at the limits

Zooming in at the maximum magnification (or out at the minimum) made the map jump: the live preview clamps to `[0.25, 4.0]` (±2 mag levels), but the gesture-end commit clamps the magnification to `[4, 20]`. At mag 20 the preview shows up to 4× while the commit cannot zoom in at all (`round(log2(4)) = 2` levels, `clamp(20+2) = 20` — no change), so the map snaps back from 4× to 1× on release. At mag 19 the preview shows 4× (2 levels) but the commit delivers 1 level (19→20) — a 4×→2× snap.

Fix: clamp the live visual zoom to the headroom at the current magnification — `[2^(GESTURE_MIN_MAG − mag), 2^(MAX_MAG − mag)]` intersected with `[0.25, 4.0]` — via `clampGestureVisualZoom` in `MapCanvasScreen.kt`. At mag 20 the preview clamps to 1.0 (no zoom-in preview, no snap-back); at mag 19 it clamps to 2.0 (exactly the committed zoom). Mid-range magnifications keep the full ±2-level preview.

## Risks

- **Jitter drift on very noisy input**: if a device reports large stationary-finger angle noise, the accumulator can report small spurious rotations. Bounded by the threshold per report; acceptable for the first fix. The emulator's noisy multi-touch may show slight wobble, but the emulator is not the target.
- **Coarser granularity**: rotation is reported in ~0.01 rad steps instead of per-event. Invisible in practice (0.57° steps at 120 Hz).

## Testing

- New `MapGestureComposeTest.slowRotationWithSmallPerEventDeltasIsReported`: rotates 90° in 100 steps of 0.9° (0.0157 rad/event — below the old 0.05 threshold). Asserts rotation is reported and totals ≈ 90°. Fails on the old per-event threshold, passes with the accumulator.
- Existing gesture tests (clockwise/counter-clockwise, no-zoom-on-rotation, jitter, pinch, centroid, single render on end, single-finger pan) must still pass.
- New `MapCanvasGestureTransformTest` (D3): pure pinch keeps the centroid fixed; pure rotation has no translation (map stays covering the canvas at 180°); pan is preserved; a canvas corner maps inside the bitmap at 180°.
- New `MapCanvasGestureTransformTest` (D4): at mag 20 the preview clamps to 1.0 (no zoom-in snap-back), at mag 19 to 2.0, at mag 4 to 1.0 (no zoom-out snap-back), at mag 5 to 0.5, mid-range keeps the full ±2-level range.
- Manual: two-finger rotation on a real device — map rotates live, no pan/zoom drift, stays visible through 180°, no zoom snap-back at the magnification limits, commits on gesture end.
