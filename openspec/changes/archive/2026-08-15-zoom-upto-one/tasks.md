## 1. Constants in MapCanvasViewModel

- [x] 1.1 Change `MIN_MAG` from `4` to `1` in the `MapCanvasViewModel` companion object (~line 1827) (spec: zoom-controls — "disabled when magnification is at the minimum (1)")
- [x] 1.2 Add `const val GESTURE_MIN_MAG = 4` next to `MIN_MAG`/`MAX_MAG`, with a comment that it bounds the pinch/rotation gesture only (spec: map-rotation-gesture — "clamped to the gesture-specific limits (4–20)")
- [x] 1.3 Verify `updateMagnification()` clamp stays `MIN_MAG..MAX_MAG` (now 1–20) and `computeAreaZoom()` still uses `MIN_AREA_ZOOM` unchanged (spec: zoom-controls — keyboard/scroll-wheel scenario)

## 2. Screen wiring in MapCanvasScreen

- [x] 2.1 Switch the gesture-commit clamp (`newMag = (mag + zoomSteps).coerceIn(...)`, ~line 357) from `MapCanvasViewModel.MIN_MAG` to `MapCanvasViewModel.GESTURE_MIN_MAG` (spec: map-rotation-gesture — "Gesture cannot zoom out below level 4")
- [x] 2.2 Leave the scroll-wheel clamp (~line 403) on `MIN_MAG` so the wheel reaches magnification 1 like the buttons (spec: zoom-controls — keyboard/scroll-wheel scenario)
- [x] 2.3 Verify both `canZoomOut` expressions (portrait ~line 653, landscape ~line 833) compare against `MIN_MAG`, enabling the button until magnification 1 (spec: zoom-controls — "Zoom out button disabled at min")

## 3. Tests

- [x] 3.1 Add unit test(s) for `updateMagnification()`/`zoomOut()`: clamp at 1–20, `zoomOut()` at magnification 1 is a no-op, `zoomIn()` at 20 is a no-op — follow existing `MapCanvasViewModel*Test` Robolectric pattern (spec: zoom-controls)
- [x] 3.2 Add/extend gesture test in `MapGestureComposeTest.kt`: pinch-out commit at magnification 4 keeps 4; gesture never reaches magnification 1 (spec: map-rotation-gesture — "Gesture cannot zoom out below level 4")
- [x] 3.3 Run unit tests: `./gradlew :app:testDebugUnitTest` (or `./gradlew test`) — all existing tests still pass (rule: verify existing tests)

## 4. Build verification

- [x] 4.1 Compile check: `./gradlew :app:compileDebugKotlin` (or `assembleDebug`) succeeds with no errors (rule: verify build compiles)
- [x] 4.2 Run `openspec validate zoom-upto-one` — passes with the two spec deltas
- [x] 4.3 Manual check on device/emulator: zoom out via button/keyboard/wheel reaches magnification 1; pinch-out stops at 4; zoom level label shows 1 (spec: zoom-controls, map-rotation-gesture)
