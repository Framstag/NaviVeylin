## 1. Add re-center button to MapCanvasScreen

- [x] 1.1 Add `Icons.Filled.MyLocation` import to MapCanvasScreen.kt
- [x] 1.2 Add re-center `FilledTonalIconButton` in right-side button column, below zoom controls, visible when `!state.followMode && state.gpsFixQuality != GpsFixQuality.NONE`
- [x] 1.3 Wire button `onClick` to re-center on GPS: `viewModel.onToggleFollowMode(true)` + `viewModel.updateCenter(loc.latitude, loc.longitude)` + `viewModel.renderMap()`
- [x] 1.4 Verify all gesture/keyboard paths call `disengageFollowMode()` — pan, pinch zoom, pinch rotate, scroll zoom, keyboard +/- (already done, confirm)

## 2. Verify build and tests

- [x] 2.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` and confirm no compile errors
- [x] 2.2 Run `./gradlew test` and confirm existing tests pass
