# Tasks: Speed-Zoom Table Update

## 1. Extract table and update values

- [x] 1.1 Create `app/src/main/java/com/naviveylin/ui/map/SpeedZoomTable.kt` with `internal object SpeedZoomTable` containing the `SpeedZoomLevel` data class, the table with new values (`0.0→18.0`, `6.0→17.5`, `15.0→16.0`, `30.0→16.0`, `60.0→16.0`, `90.0→13.0`, `130.0→12.0`), and `compute(speedKmH: Double): Double` with linear interpolation and clamping (spec: auto-speed-zoom / SPEED_ZOOM_TABLE with narrowed range).
- [x] 1.2 In `MapCanvasViewModel.kt`, remove the private `SpeedZoomLevel`/`SPEED_ZOOM_TABLE`/`computeSpeedZoom` and delegate to `SpeedZoomTable.compute(...)`; keep `filterSpeed`, throttling, hysteresis, and commit logic unchanged (spec: auto-speed-zoom / SPEED_ZOOM_TABLE with narrowed range).

## 2. Tests

- [x] 2.1 Add `app/src/test/java/com/naviveylin/ui/map/SpeedZoomTableTest.kt` (plain JUnit, no Robolectric): exact breakpoints (`0→18.0`, `6→17.5`, `30→16.0`, `60→16.0`, `90→13.0`, `130→12.0`), interpolation (`5 km/h → ≈17.6`, `75 km/h → ≈14.5`, `100 km/h → ≈12.75`), and clamping below/above the table range (spec: auto-speed-zoom / SPEED_ZOOM_TABLE with narrowed range, Auto-zoom uses linear interpolation between speed breakpoints).
- [x] 2.2 Run `./gradlew :app:testDebugUnitTest --tests "com.naviveylin.ui.map.SpeedZoomTableTest"` — all tests pass.
- [x] 2.3 Run `./gradlew :app:testDebugUnitTest` — full unit suite passes, no regressions (spec: auto-speed-zoom).

## 3. Verification

- [x] 3.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles without errors.
- [x] 3.2 Manual check on real device: walking speed shows mag ≈18, city/suburban speeds (up to 60 km/h) show building names and numbers, highway speed zooms out, no zoom pumping between 30–60 km/h.
