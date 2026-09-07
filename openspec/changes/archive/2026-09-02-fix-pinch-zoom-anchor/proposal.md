# Fix Pinch Zoom Anchor

## Why

On a real device, pinch zoom is anchored at/near the screen center instead of
the finger centroid: a building under the fingers drifts away while zooming
instead of staying under the fingers. Root cause: `gestureTransformTranslation`
in `MapCanvasScreen.kt` computes the live gesture visual transform assuming the
Compose `graphicsLayer` translation is applied in screen space, but Android's
RenderNode (which Compose drives) applies the translation in the layer's local
(pre-scale) space — the compensation is wrong by a factor of `-s`, so for a 2×
pinch the content under the fingers lands exactly at the screen center.

## What Changes

- Fix the live pinch visual transform in `gestureTransformTranslation`
  (`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`): the
  translation compensation becomes `T = (1/s − 1)·(C − P) + (1/s)·R(−θ)·D`
  (C = gesture centroid, P = canvas center, s = zoom factor, θ = accumulated
  rotation, D = accumulated centroid pan) instead of `(1 − s)·R(θ)·(C − P) + D`.
  - At rotation 0 the centroid stays fixed under the fingers (the user-visible
    fix); at zoom 1 the transform reduces to the pure center rotation (unchanged
    behavior); the pan term is no longer amplified by the zoom factor.
- Rewrite the `purePinchKeepsCentroidFixed` unit test in
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasGestureTransformTest.kt`:
  it currently encodes the same wrong screen-space transform model, so it passes
  while the device is broken. The other three transform tests (pure rotation,
  pan preservation, 180° canvas coverage) are unaffected and must keep passing.
- No change to the commit path: `ProjectionUtils.zoomAtCursor()` (geo math) and
  the draw-layer `scale(s, pivot = zoomAnchor)` (DrawScope = screen-space pivot)
  are already correctly anchored.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-pan-zoom`: the pinch-to-zoom requirement "the geographic point under the
  pinch center stays fixed" currently only holds at gesture end (commit via
  `zoomAtCursor`) and for the placeholder draw origin. Add the requirement that
  the **live gesture visual transform** keeps the focal point stationary during
  the whole gesture, with a scenario for an off-center pinch on a building.

## Impact

- **Code**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`
  (`gestureTransformTranslation`, ~L1782-1799) — one formula change.
- **Tests**: `app/src/test/java/com/naviveylin/ui/map/MapCanvasGestureTransformTest.kt`
  (`purePinchKeepsCentroidFixed` rewritten with the correct transform model).
- **Scope**: phone/tablet only. The `:auto` module uses the Car App Library's
  native gesture handling (MapController) — unaffected.
- **Guidelines**: `guidelines/Design.md` gesture section — the design intent
  (rotate around center, zoom around centroid) was correct; only the
  implementation math was wrong. No guideline change needed.
- **Type**: additive behavior fix (corrects wrong behavior, no API change).
  Rollback: revert the formula in `gestureTransformTranslation` and the test.
- **Verification**: unit tests + on-device check (pinch on an off-center
  building, building must stay under the fingers through the gesture and at
  commit).
