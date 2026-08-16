## 1. Fix implementation

- [x] 1.1 In `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`, change `onPan` to call `ProjectionUtils.dragDeltaToNewCenterRotated(dx, dy, s.viewport.angle, ...)` instead of `dragDeltaToNewCenter(...)` (spec: map-pan-zoom / Touch-based pan — delta conversion SHALL use viewport rotation angle)
- [x] 1.2 Keep the existing `dragDeltaToNewCenter` helper in `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` untouched — it stays for JavaScout `MapInteractionHandler` and angle-0 equivalence tests (design D1/D2)
- [x] 1.3 Confirm `onPan` and `onCentroidPan` now share the same rotation-aware math (both delegate to `dragDeltaToNewCenterRotated`) (spec: map-pan-zoom / Touch-based pan)

## 2. Tests

- [x] 2.1 Extend `app/src/test/java/com/naviveylin/ui/map/ProjectionUtilsTest.kt` with rotated-pan cases at non-axis angles (e.g., 30°/45°/135°) asserting `dragDeltaToNewCenterRotated` matches the finger-follow direction, and a case asserting angle-0 equivalence with `dragDeltaToNewCenter` (spec: map-pan-zoom / Touch-based pan — conversion reduces to north-up at angle 0)
- [x] 2.2 If feasible without a Compose test harness change, add a call-site test verifying the pan callback selects the rotated variant at non-zero angle; otherwise rely on 2.1 + manual QA (design D3)

## 3. Verification

- [x] 3.1 Build: `./gradlew :app:compileDebugKotlin` compiles without errors
- [x] 3.2 Unit tests: `./gradlew :app:testDebugUnitTest` passes, including new rotated-pan cases and existing `dragDeltaToNewCenterRotated` 90° cases
- [x] 3.3 Manual QA: rotate the map to a non-zero angle (two-finger rotation), then single-finger drag — map SHALL follow the finger exactly; repeat at angle 0 to confirm unchanged north-up behavior
