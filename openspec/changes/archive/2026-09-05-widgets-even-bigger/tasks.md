## 1. Phone: max-speed sign (spec `map-speed-widget`)

- [x] 1.1 In `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt`, grow the visible sign and its placeholder together: `.size(56.dp)` → `.size(64.dp)` in both the `showLimit` branch and the placeholder branch, border `5.dp` → `6.dp`; verify `./gradlew :app:testDebugUnitTest --tests "*SpeedWidgetTest*"` passes with updated assertions
- [x] 1.2 In `SpeedWidget.kt`, bump `speedLimitDigitsStyle()` from `titleLarge` (22sp) to `headlineMedium` (28sp); verify `SpeedWidgetTest.limitSignAtLeast56dp`-style digit assertion updated to ≥28sp and passing
- [x] 1.3 Update `app/src/test/java/com/naviveylin/ui/map/SpeedWidgetTest.kt`: sign-size test 56dp → 64dp, add digit-style ≥28sp assertion, keep placeholder-footprint-matches-sign check; verify the full `SpeedWidgetTest` class passes

## 2. Phone: compass (spec `compass-button`)

- [x] 2.1 In `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt`, grow the button `.size(48.dp)` → `.size(56.dp)` and the inner `Canvas` `40.dp` → `48.dp`; verify `CompassButtonComposeTest` still passes (press/long-press behavior unchanged)
- [x] 2.2 In `CompassButton.kt`, bump the "N" label `9.sp` → `11.sp`; verify no test asserts the old size and the compass renders (existing Compose test green)
- [x] 2.3 Update `app/src/test/java/com/naviveylin/ui/map/CompassButtonComposeTest.kt` with a size assertion (56dp layout / 48dp visual) and confirm the needle-ratio (70%) behavior is unchanged; verify the class passes

## 3. Android Auto: rose + limit sign (spec `auto-map-layout`)

- [x] 3.1 In `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt`, bump `ROSE_DIAMETER_DP` 48f → 56f; verify `SurfaceIndicatorsTest` geometry assertions (rose radius, centering over badge) still pass
- [x] 3.2 In `SurfaceIndicators.kt`, bump `LIMIT_DIAMETER_DP` 48f → 56f, `LIMIT_RING_DP` 6f → 7f, `LIMIT_TEXT_DP` 20f → 24f; verify `SurfaceIndicatorsTest` limit-sign geometry (below badge, centered) still passes
- [x] 3.3 Update `auto/src/test/java/com/naviveylin/auto/SurfaceIndicatorsTest.kt` with size assertions for the new rose (56dp) and sign (56dp/7dp ring/24sp digits); verify the class passes
- [x] 3.4 Run `auto/src/test/java/com/naviveylin/auto/SurfaceLayoutTest.kt` and verify no overlap regressions with the larger indicators

## 4. Regression + docs

- [x] 4.1 Run full `./gradlew test` and verify all suites pass (phone + auto)
- [x] 4.2 Update `guidelines/UI.md` phone overlay size convention (compass larger than overlay buttons, sign minimums) and AA indicator sizes; verify the doc reflects the new spec minimums
