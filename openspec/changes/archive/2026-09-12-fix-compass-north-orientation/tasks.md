## 1. Core convention (spec: compass-button, auto-map-layout, auto/free-driving; design D1, D4)

- [x] 1.1 Add `ProjectionUtils.compassRotationDegrees(mapAngleRadians: Double): Double` to `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — returns the screen direction of north in degrees, clockwise-positive from screen-up (`normalizedDegrees(deg(angle))`), with a doc comment stating the convention (native angle is math-CCW but north renders at `+angle` on screen; heading-up stores `−bearing`). Verify: unit tests in `ProjectionUtilsTest` — θ=0→0, θ=+90→90, θ=−90→270, and a westbound-follow case (bearing 270 → θ=+90 → 90, i.e. driver's right).
- [x] 1.2 Correct the misleading "Map rotated 30 degrees CCW" comment in `ProjectionUtilsTest.screenBearing` tests to the actual clockwise-positive screen convention (assertions like 120/315 stay unchanged). Verify: comment updated and those tests still pass.

## 2. Phone compass button (spec: compass-button; design D2, D4)

- [x] 2.1 Extract and implement the needle target as a pure internal function `compassNeedleTarget(isNorthUp: Boolean, bearingDegrees: Double, mapAngleRadians: Double): Double` in `CompassButton.kt`: north-up branch → `compassRotationDegrees(mapAngleRadians)`; follow branch → `screenBearing(bearingDegrees, mapAngleRadians)`, falling back to `compassRotationDegrees(mapAngleRadians)` when the bearing is unknown (negative/NaN). Replace the local `-deg(angle)` negation. Verify: plain-JUnit tests pin north-up=θ, follow-up-with-bearing=0 (westbound 270°/eastbound 90°), follow after manual rotation (bearing 270, θ=−270+30 → 30), NaN/null/negative-bearing fallback to map north.
- [x] 2.2 Add nullable `bearingDegrees: Double?` parameter to `CompassButton` and thread it through `MapCompassBlock`/`MapRightWidgetColumn`; `MapCanvasScreen` passes `state.markerBearing` at all three call sites (L1128/L1252/L1694). Verify: `:app:compileMobileDebugKotlin` compiles; existing `CompassButtonComposeTest` (clicks/size) still passes.
- [x] 2.3 Add a Compose test asserting the needle's rendered rotation (via the pure `compassNeedleTarget`) for follow-direction westbound and north-up, so the direction convention is pinned at the UI layer too. Verify: new test passes.

## 3. Android Auto compass rose (spec: auto-map-layout, auto/free-driving; design D3)

- [x] 3.1 In `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt`, replace `canvas.rotate(-Math.toDegrees(angleRadians)…)` with `canvas.rotate(ProjectionUtils.compassRotationDegrees(angleRadians).toFloat(), …)`; keep the `angleRadians` input (screens already feed heading-up θ). Verify: `SurfaceIndicatorsTest` geometry tests still pass; `:auto:compileDebugKotlin` compiles.
- [x] 3.2 Add a unit test pinning the rose rotation direction: `compassRotationDegrees` for westbound follow (θ=+90 → +90°/right) and eastbound (θ=−90 → 270°/left) — plain-JUnit on the core function plus a `SurfaceIndicatorsTest` assertion that `drawRose`'s rotation derives from that function. Verify: new tests pass.

## 4. Verification (spec: compass-button, auto-map-layout, auto/free-driving)

- [x] 4.1 Build all touched modules via the `build-app` skill (`./gradlew :app:assembleMobileDebug :auto:assembleDebug`); verify no compile errors or new warnings.
- [x] 4.2 Run the full unit-test suite via the `run-tests` skill (`./gradlew test`); verify all tests pass, including the new core/compass/rose tests and the pre-existing `ProjectionUtilsTest`, `CompassButtonComposeTest`, `SurfaceIndicatorsTest`.
- [x] 4.3 On-device phone verification: follow-direction mode heading west — compass north pointer at the driver's right (north), heading east — at the driver's left; north-up mode needle up; follow triangle straight up in pure heading-up; manual-rotation sub-check covered by unit test (`CompassNeedleTargetTest` "follow needle follows travel direction after manual rotation") — not gestured on-device. Verify: visual observation on device (GPX replay or drive), map and marker arrow still correct.
- [x] 4.4 On-device Android Auto verification: free-driving and navigation views on emulator/head unit with heading-up rotation heading west — rose north pointer at the driver's right; east — left; surface lifecycle (lock/unlock, reconnect) unaffected. Verify: visual observation on the map surface.
