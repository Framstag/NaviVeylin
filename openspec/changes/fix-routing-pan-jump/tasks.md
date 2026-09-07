## 1. Shared commit gate (spec: auto/map-pan — follow suspended while panned)

- [x] 1.1 Add top-level `shouldCommitViewport(panning: Boolean, angle: Double?, newZoom: Int?): Boolean` to `MapPanHandler.kt` (`!panning && (angle != null || newZoom != null)`, design D1) and verify `MapPanHandlerTest` covers: panning=true suppresses commit even with angle+zoom set (the band-crossing case), panning=false commits on angle or zoom change, panning=false with both null does not commit
- [x] 1.2 `NavigationScreen` GPS handler: replace the inline `if (angle != null || zoom != null)` commit condition with `if (shouldCommitViewport(panHandler.panning, angle, zoom))` and verify `:auto:compileDebugKotlin` compiles
- [x] 1.3 `FreeDrivingScreen`: remove the companion `shouldCommitViewport`, call the shared helper, and verify `:auto:compileDebugKotlin` compiles

## 2. Gate the auto-zoom feed (spec: auto/map-pan — auto-zoom suspended while panned)

- [x] 2.1 Add companion `autoZoomTarget(panning: Boolean, autoZoomEnabled: Boolean, speedKmH: Double, controller: AutoZoomController): Int?` to `NavigationScreen` (design D2: `if (!panning && autoZoomEnabled && speedKmH >= 0.0) controller.onSpeed(speedKmH) else null`) and verify `NavigationScreenTest` covers: panning=true returns null even when the controller would re-engage on a band crossing (suspended city-band controller fed a highway speed), panning=false delegates to `onSpeed`, disabled auto-zoom returns null
- [x] 2.2 `NavigationScreen` GPS handler: `val zoom = autoZoomTarget(panHandler.panning, autoZoomEnabled, pos.speedKmH, autoZoomController)` and verify `:auto:compileDebugKotlin` compiles
- [x] 2.3 Mirror `autoZoomTarget` in `FreeDrivingScreen` (same gate on its `onGpsFix` zoom computation) and verify `FreeDrivingScreenTest` covers the panning gate for symmetry

## 3. Test consolidation and regression verification

- [x] 3.1 Move the `shouldCommitViewport` cases from `FreeDrivingScreenTest` to `MapPanHandlerTest` (no duplicate coverage of the same decision) and verify the moved tests pass
- [x] 3.2 Run the full `:auto` unit test suite (`./gradlew :auto:testDebugUnitTest`) and verify all tests pass, including the existing `AutoZoomControllerTest.manualZoomSuspendsUntilBandChange` (documents the unchanged band-crossing re-engage semantics)
- [x] 3.3 Build verification: `./gradlew :app:assembleMobileDebug` (arm64-v8a) compiles and `:app:compileAutomotiveDebugKotlin` compiles
