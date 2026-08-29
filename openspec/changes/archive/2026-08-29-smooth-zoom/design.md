# Design: smooth-zoom

## Context

The visual pipeline is Compose-side: `MapCanvasScreen` draws `state.renderedBitmap` (the renderer's front buffer) each frame, and already applies per-frame transforms at that layer — `gestureZoom`/`gestureRotation`/`gesturePan` via `graphicsLayer` during pinch, and the follow-mode pan offset (`followOffsetX/Y`, driven by a `withFrameNanos` display loop, spec `smooth-follow`). `MapRenderer` deliberately does not scale its placeholder on zoom changes (`guidelines/MapRendering.md`: "on zoom change keep the old correct frame"); the render pipeline (tile-cache, 200 ms debounce, overrun-sized jobs) is unchanged by this change. Discrete zoom paths (`zoomIn()`/`zoomOut()`, scroll wheel, keyboard +/-) commit the target magnification immediately and queue a debounced render — display snaps only because nothing animates between the old and new front-buffer magnifications.

See proposals: motivation in `proposal.md`, behavior contract in `specs/smooth-zoom/spec.md` and deltas for `zoom-transition-scaling`, `zoom-controls`.

## Goals / Non-Goals

**Goals**
- Eased front-buffer scale animation between start and end magnification, display-only.
- Geographic anchoring (screen center for buttons/keyboard, cursor for wheel).
- Smooth handoff from gesture placeholder → rendered frame (no single-frame content jump).
- Composition with the follow-mode display loop.
- Unit-testable animation state, mirroring the `FollowPrediction` role.

**Non-Goals**
- No changes to the native render pipeline (debounce values, tile cache, render job contents).
- No continuous-zoom render pumping (rendering stays integer-magnification; see MapRendering "sub-level zoom pumping" avoidance).
- No index/zoom-speed-driven auto zoom behavior changes (`auto-speed-zoom`, `auto-turn-zoom`, `fav-auto-zoom`, `adaptive-zoom` untouched).
- No :auto/AAOS changes.

## Decisions

### D1: Animate in the Compose display layer, not in MapRenderer

The zoom animation is a frame-loop in `MapCanvasScreen` that multiplies into the same `graphicsLayer` transform already used for gestures, anchored at the zoom focal point.

- Why: the display layer already owns visual-only transforms (gesture, follow offset) and runs one continuous frame loop; MapRenderer's front buffer keeps showing the last *correct* frame during zoom changes (existing guideline/behavior). Since discrete zoom is N·±1 steps, start→target scale is exactly `2^(targetMag − frontBufferMag)` — known immediately, no need for the renderer to scale anything.
- Alternative (rejected): renderer-side scaled placeholder (render a crossfade in `executeRender`/buffer swap). Touches buffer-swap logic that is fight-tested on pan/zoom deferred paths, splits "displayed" state between two layers, and gives no visual benefit since the Compose blit already scales at zero marginal cost.

### D2: Pure `ZoomAnimation` state helper in `com.naviveylin.core`

New class `ZoomAnimation` (sibling of `FollowPrediction`), no Android dependencies:

- State: `active`, `progress`, `startScale`, `targetScale`, `anchorPx`, `startTimeMs`.
- API: `start(from: Float, to: Float, anchor: OffsetLike, nowMs: Long, durationMs = 250L)`, `retrack(to: Float, nowMs: Long)` (rebuilds animation with `start = currentScale()`), `tick(nowMs)` → current eased scale (`t = elapsed/duration`, ease-out cubic `1 − (1−t)^3`), plus `finished`.
- Duration per animation 250 ms regardless of remaining steps; retracking accumulates scale from the current value so rapid taps compound smoothly.
- Rationale for pure class: unit-testable in JVM tests without Robolectric; matches the established `smooth-follow` pattern (composable-local driver + pure math class).
- Alternative (rejected): Compose `Animatable<Float>` per animation — harder to compose gesture/follow transforms and to test logic in isolation; the frame loop already exists.

### D3: One displayed-scale model for all input paths

`displayedScale` = scale applied to the *currently rendered front buffer* (1.0 = no zoom applied, matches frontBufferMag):

- Pinch gesture sets it directly (fractional, from `gestureZoom`).
- Discrete input animates it toward `2^(committedMag − frontBufferMag)`.
- Gesture and animation never run simultaneously: gesture start marks the animation idle; animation start (button tap) while gesture is active is impossible since controls are occluded by the gesture.
- Render completion: front buffer swaps to `committedMag` → `displayedScale` resets to 1.0. Because the previous displayed scale equaled `2^(targetMag − oldFrontMag)`, the rendered tile geometry lines up at the same scale — the only visible difference is tile detail, which is why:

### D4: 150 ms crossfade at render completion (gesture case), immediate swap (animation case)

On render completion at the target magnification, `MapCanvasScreen` runs a short (~150 ms) alpha crossfade drawing the old front buffer (at its end-of-animation/gesture scale, alpha 1→0) over the new rendered frame. Spec mapping: `zoom-transition-scaling` requires "no single-frame jump" for the gesture handoff; `smooth-zoom` explicitly permits immediate replacement when the render lands during an animation (geometry already matches, crossfade unnecessary blur).

- Alternative (rejected): always crossfade — adds visible ghosting on a case where content already aligns (buttons), and the specs differentiate the two cases.

### D5: Anchor applied in the same `graphicsLayer` block

Anchor = `transformOrigin` complement + translation, computed by extending the existing `gestureTransformTranslation` helper (already handles centroid pivot + compensated translation). New anchor inputs: screen center for buttons/keyboard, cursor `Offset` for scroll wheel. Follow offset is applied after (outer) the scale so the zoom anchor point stays fixed while the map pans beneath it — "scale around anchor, then translate" composition.

### D6: Input wiring

`MapCanvasViewModel.updateMagnification()` stays the single entry point for committing magnification (already used by buttons, wheel, keyboard). The *screen* decides whether to start the `ZoomAnimation` (discrete input) based on which input path triggered the commit — implement as a screen-level `fun animateZoomTo(anchor: Offset)` called next to the existing `viewModel.zoomIn()/zoomOut()` invocations rather than inside the ViewModel (ViewModel has no notion of screen pixels/anchors).

Edge cases:
- Zoom while a render is already pending at a newer mag → recompute target from the *pending* committed mag, retrack.
- Front buffer mag not yet matching committed mag on animation start (previous animation still running) → start scale = current displayed scale, target scales recompute from frontBufferMag.
- Animation granularity: one animation to the final target, not per-step — 5 quick taps still converge in ~250 ms from wherever the scale was when the last tap landed (retrack).

## Risks / Trade-offs

- [Frame-loop grows (follow + zoom + gesture in one loop)] → keep the zoom branch isolated behind `ZoomAnimation` state; guard with early-out when `!zoomAnim.active && gestureZoom == 1f`; keep diagnostics logging count-gated like follow mode.
- [Overrun-size front buffer (follow mode) scaled wrong] → zoom scale composes with the overrun draw path at the anchor; verify the "scale then follow-offset" order keeps the marker overlay aligned (marker already uses the display viewport; use the animated scale's effective viewport for marker projection only if misalignment is observed).
- [Crossfade doubles bitmap draw cost for one frame-ish period] → 150 ms, only during render completion; front buffers are screen-sized blits; acceptable. Cap: skip crossfade when buffers recycle concurrently.
- [Zoom tapped during active render causes retrack-into-swap mid-flight] → D3/D4 rules make swap-at-matched-scale the fallback; worst case is a visible tile-detail change, same as today's behavior, never a scale jump.
- [Persisting intermediate scales] → viewport persistence keeps using the committed magnification only (`MapCanvasViewModel` state, never the Compose-side display scale); add a unit test.

## Migration Plan

Additive. Ship behind no flag (visual-only, easily reverted); existing tests for placeholder behavior updated for the crossfade. Rollback = revert commit; no data, API, or native changes.

## Open Questions

None — anchor points, duration, easing, and crossfade policy follow the specs and the established `smooth-follow` patterns.