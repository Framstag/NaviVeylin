# Tasks — the displayed viewport is one projection

Spec: `smooth-follow`, the requirement "Single resolved anchor across render, blit and marker" (delta
`specs/smooth-follow/spec.md`). Scope: compiled Kotlin changes, so the gate runs the changed cases and then the
module's suite (`guidelines/Build.md` §2, the scope block).

## 1. The derivation

- [x] 1.1 `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt`: add the derived displayed viewport —
  the frame viewport shifted by the clamped overrun offset through `ProjectionUtils.screenToGeoRotated` — beside
  `markerDisplayShiftPx`. *Verified*: `./gradlew :app:compileDebugKotlin`.
- [x] 1.2 `MapCanvasScreenParts.kt`: retire `markerDisplayShiftPx` and its doc comment.
  *Verified*: `tools/look.sh 'markerDisplayShiftPx'` names no main-source site.

## 2. The two call sites

- [x] 2.1 `app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt`: `RouteSegmentHighlightOverlay`
  (`:610`) takes the derived viewport and drops its `graphicsLayer` shift (`:618`).
  *Verified*: `app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift` asserts the pan-side position.
- [x] 2.2 `MapCanvasMapBand.kt`: `LocationMarkerOverlay` (`:667`) takes the derived viewport and drops its
  `graphicsLayer` shift (`:682`); the overlay's own anchor-centre projection (`:641`) is retired, so the
  frame's own anchor-centred viewport is the derivation's follow input.
  *Verified*: the same case, plus `FollowAnchorFramingTest` still green.

## 3. The cases

- [x] 3.1 `app/src/test/java/com/naviveylin/ui/map/`: the cases `app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift`
  and `app: MarkerDisplayShiftTest.theDerivationDoesNotBranchOnTheMode` — a pan offset with follow active lands the
  overlay on the frame's own shift, and the derivation does not branch on the mode. *Verified*: red on the base
  (`evidence/red-marker-display-shift.xml`, tests=3 failures=3), green after tasks 1–2
  (`evidence/green-marker-display-shift.xml`).
- [x] 3.2 `MarkerDisplayShiftTest`: rewritten to assert the derived viewport rather than the helper's two branches
  (`theModeTestHelperIsRetired` is its third case). *Verified*: the case's own run is green.

## 4. Verification

- [x] 4.1 `bash tools/check-change-readiness.sh openspec/changes/the-displayed-viewport-is-one-projection` prints
  `LOOP-CHANGE-READY: ok`.
- [x] 4.2 `openspec validate the-displayed-viewport-is-one-projection --strict` passes.
- [x] 4.3 The gate runs the changed cases and then the module's suite; a red run is attributed before it is
  re-run (`guidelines/BuildEvidence.md` §4d).
