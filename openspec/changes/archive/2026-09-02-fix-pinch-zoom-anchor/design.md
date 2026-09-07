# Fix Pinch Zoom Anchor — Design

## Context

See proposal.md — Why. The live pinch visual transform
(`gestureTransformTranslation` in `MapCanvasScreen.kt`) computes a translation
compensation that assumes the Compose `graphicsLayer` translation is applied in
screen space (after scale/rotation). Android's RenderNode — which Compose
`graphicsLayer` drives via `RenderNodeLayer.android.kt` — builds its matrix as
`M = T(pivot)·S·R·T(−pivot)·T(translation)` (verified in AOSP
`RenderProperties.cpp::updateMatrix()`: `setTranslate` → `preRotate` →
`preScale`), i.e. the translation is applied in the layer's **local** space and
gets scaled by `s` and rotated by `θ`:

```
p' = P + S·R·(p − P + T)        P = canvas center (transformOrigin 0.5,0.5)
```

The current compensation `T = (1−s)·R(θ)·(C−P) + D` makes the actual visual
anchor `P + s·(C−P)` — the centroid reflected through the center, scaled by s.
For a 2× pinch the content under the fingers lands exactly at the screen
center (the reported symptom).

The commit path is already correct: `ProjectionUtils.zoomAtCursor()` (pure geo
math) and the draw-layer `scale(s, pivot = zoomAnchor)` (DrawScope transform =
screen-space pivot) both anchor at the centroid. Only the live gesture
`graphicsLayer` transform is wrong.

## Goals / Non-Goals

**Goals:**
- The geographic point under the pinch centroid stays at the same screen pixel
  during the whole gesture (spec: map-pan-zoom, "Pinch zoom anchored at an
  off-center building").
- Keep the existing design decisions: rotation around the screen center, zoom
  around the gesture centroid, pan follows the fingers.
- Fix the pan amplification (pan currently scaled by `s` when zoomed).

**Non-Goals:**
- No change to the commit path, the discrete zoom animation, or the `:auto`
  module (Car App Library native gestures).
- No change to the rotation-around-center design (the θ≠0 combined
  rotate+zoom case stays approximate by design — see Risks).

## Decisions

### D1: Fix the closed-form compensation in `gestureTransformTranslation`

Solve `p' = P + S·R·(p − P + T)` for the desired visual
`p' = D + P + R·(s·(p − C) + (C − P))` (scale around centroid C, then rotate
around center P, then pan by D):

```
T = (1/s − 1)·(C − P) + (1/s)·R(−θ)·D
```

- θ=0, D=0: `T = (1/s − 1)(C−P)` — the centroid stays fixed (the fix).
- s=1: `T = 0` — pure rotation around the center, no translation (unchanged).
- Pan: `(1/s)·R(−θ)·D` — the content moves exactly `D` in screen space; the
  current code adds `D` unscaled, so the pan is amplified by `s` when zoomed.

**Alternatives considered:**
- **A: Switch to the official Compose pattern** (`transformOrigin = (0f, 0f)`
  top-left pivot + incremental `offset = (offset + centroid/oldScale) −
  (centroid/newScale + pan/oldScale)` accumulation). This is the documented
  Compose approach, but it changes the rotation pivot to the top-left corner,
  breaking the rotation-around-center design (the map would swing off-screen at
  180° — the exact bug the archived `2026-08-15-bugfix-rotation` change fixed),
  and requires restructuring the per-gesture state from closed-form to
  incremental accumulation. Rejected.
- **B: Apply a custom `Matrix` via `graphicsLayer`** — more invasive, no
  benefit over fixing the closed form. Rejected.
- **C: Fix the closed-form compensation (chosen)** — minimal one-function
  change, keeps the center rotation pivot, matches the commit's `zoomAtCursor`
  anchoring, and the existing pure-rotation/pan tests keep passing.

### D2: Rewrite `purePinchKeepsCentroidFixed` with the correct transform model

The test currently asserts `xp = t.x + center.x + s·(centroid.x − center.x)`
(screen-space translation model) — it passes while the device is broken. It
must assert the actual RenderNode model:

```
xp = center.x + s·(centroid.x − center.x + t.x)
```

Add a companion test for the pan fix: at s=2 with a pan `D`, the content moves
exactly `D` in screen space (currently it moves `s·D`).

## Risks / Trade-offs

- **Combined rotate+zoom is approximate** → the zoom compensation is not
  rotated (`(1/s − 1)(C−P)`), so during simultaneous rotation+zoom the centroid
  swings with the rotation (it is fixed only in the pre-rotation frame). This
  matches the design intent (rotation around center) and the commit behavior
  (`zoomAtCursor` is north-up math). The user-visible pure-zoom case is exact.
  Mitigation: document in the function KDoc; no spec change (the spec scenario
  covers the pure-zoom case).
- **Gesture-over-running-animation fold** → if a pinch starts while a discrete
  zoom animation is parked, the folded base scale re-anchors from the center to
  the centroid (small jump). Pre-existing edge case, out of scope.
- **Test-only coverage of the transform order** → the fix relies on the
  RenderNode matrix semantics. Mitigation: the rewritten unit test asserts the
  actual model, and the on-device check (pinch on an off-center building) is the
  acceptance gate.

## Migration Plan

- Change `gestureTransformTranslation` in `MapCanvasScreen.kt` (one formula).
- Rewrite `purePinchKeepsCentroidFixed` + add the pan test in
  `MapCanvasGestureTransformTest.kt`.
- Run `./gradlew :app:testDebugUnitTest` (or the run-tests skill) — all
  gesture/transform tests must pass.
- On-device: pinch on an off-center building — the building must stay under
  the fingers through the gesture and at commit.
- Rollback: revert the formula and the test change (single commit, no data or
  API impact).

## Open Questions

None.
