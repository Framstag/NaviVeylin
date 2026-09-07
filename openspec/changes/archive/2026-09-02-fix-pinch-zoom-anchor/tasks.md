# Fix Pinch Zoom Anchor — Tasks

## 1. Fix the live gesture transform

- [x] 1.1 In `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`, change `gestureTransformTranslation` (spec: map-pan-zoom, "Pinch-to-zoom") to the local-space compensation `T = (1/s − 1)·(C − P) + (1/s)·R(−θ)·D` (design D1) and update its KDoc to document the RenderNode local-space translation semantics; verify the function compiles and the existing pure-rotation/pan unit tests still pass

## 2. Update the transform unit tests

- [x] 2.1 In `app/src/test/java/com/naviveylin/ui/map/MapCanvasGestureTransformTest.kt`, rewrite `purePinchKeepsCentroidFixed` to assert the actual RenderNode model `xp = center.x + s·(centroid.x − center.x + t.x)` (design D2) and verify it passes with the fixed formula and fails with the old one
- [x] 2.2 Add a unit test that a screen-space pan `D` at zoom `s = 2` moves the content exactly `D` (no pan amplification) and verify it passes

## 3. Build and test verification

- [x] 3.1 Run the unit test suite (run-tests skill / `./gradlew :app:testDebugUnitTest`) and verify all gesture/transform tests pass and no existing tests regress
- [x] 3.2 Build the app (`./gradlew :app:assembleMobileDebug`) and verify it compiles without errors

## 4. On-device verification

- [x] 4.1 On a real device, pinch on a building that is not at the screen center and verify the building stays under the fingers for the whole gesture and at commit (spec: map-pan-zoom, "Pinch zoom anchored at an off-center building"); also verify two-finger pan still follows the fingers and rotation still rotates around the screen center
