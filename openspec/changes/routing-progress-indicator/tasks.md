## 1. State plumbing

- [x] 1.1 Add `navigationStartTimeMillis: Long = 0L` to `NavigationState` in `core/src/main/java/com/naviveylin/core/NavigationState.kt` and verify `./gradlew :core:compileDebugKotlin` compiles
- [x] 1.2 Set `navigationStartTimeMillis = System.currentTimeMillis()` and `remainingDistance = totalDistance` in `NavigationViewModel.startNavigation()`; reset in `stopNavigation()`; verify `./gradlew :app:compileMobileDebugKotlin` compiles

## 2. Progress derivation

- [x] 2.1 Add pure helper (e.g. `routeProgressPercent(total: Double, remaining: Double): Int` and `elapsedTimePercent(start: Long, eta: Long, now: Long): Int`, both clamped 0–100, returning 0 when inputs invalid/zero) in a testable location (e.g. `ui/route/RouteProgress.kt`) and verify unit tests cover clamping and zero-division guards (spec: navigation-status-details — "Progress values clamped to valid range")

## 3. Routing status card UI

- [x] 3.1 Add `distanceProgressPercent: Int?` and `timeProgressPercent: Int?` parameters to `NavigationStateOverlay` and render two small progress lines (thin track + filled portion, no labels/percent values, small `Place`/`Schedule` icons for differentiation) between the road name and the stats row, only when both are non-null (spec: navigation-status-details — "Route progress lines in routing status card", "Lines differentiated by icon", "No labels or percent values shown", "Progress lines not shown outside navigation")
- [x] 3.2 Wire progress values in `MapCanvasScreen` from `navState` (distance % from `totalDistance`/`remainingDistance`, time % from `navigationStartTimeMillis`/`etaMillis`/`System.currentTimeMillis()`, null when not navigating) into `NavigationStateOverlay`; remove the progress section and parameters from `RouteSummaryDialog`; verify `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` builds

## 4. Tests

- [x] 4.1 Add Compose test for `NavigationStateOverlay`: progress lines visible with values passed, hidden when null; remove the progress assertions from `RouteSummaryDialogComposeTest`; run `./gradlew :app:testMobileDebugUnitTest --tests "*NavigationStateOverlay*"` (Robolectric, default sandbox — no `@Config(sdk=...)` on classes touching the JNI stub)
- [x] 4.2 Add unit tests for the progress helpers (clamping, zero-division, boundary values) and verify `./gradlew :app:testMobileDebugUnitTest` passes
- [x] 4.3 Run full unit suite `./gradlew test` and verify no regressions

## 5. On-device verification

- [x] 5.1 Install `:app:assembleMobileDebug` on emulator/device, calculate a route, start navigation and verify both progress lines render in the routing status card with icons and update as the position advances; verify lines absent outside navigation
