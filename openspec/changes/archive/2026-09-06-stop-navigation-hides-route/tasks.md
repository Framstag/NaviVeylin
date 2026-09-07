# Tasks: Stop Navigation Hides Route

## 1. `RoutePanelViewModel` (spec: route-panel-ui)

- [x] 1.1 Add `_routeVisible = MutableStateFlow(true)` + `routeVisible: StateFlow<Boolean>`; add `clearRouteFromMap()` (sets `routeVisible = false`, bumps `_clearRouteSignal`) and `showRouteOnMap()` (sets `routeVisible = true`); verify `:app:compileMobileDebugKotlin` compiles
- [x] 1.2 Set `_routeVisible.value = true` in `onSuccess` (before emitting the new `RouteResult`) and in `clearRoute()`; verify `:app:compileMobileDebugKotlin` compiles
- [x] 1.3 Add unit tests in `RoutePanelViewModelRerouteTest.kt` (or a new `RoutePanelViewModelRouteVisibilityTest.kt`): `clearRouteFromMap` sets `routeVisible=false` + bumps `clearRouteSignal` while preserving `uiState` (start/dest/vehicle/routeEntry/routeSteps) and `routeResultFlow`; `showRouteOnMap` sets `routeVisible=true`; `onSuccess` resets `routeVisible=true`; verify the new tests pass

## 2. `MapCanvasViewModel` draw path (spec: route-panel-ui)

- [x] 2.1 Replace the `routeResultFlow` draw collector in `setRoutePanelViewModel` with `combine(vm.routeResultFlow, vm.routeVisible)` gated on `result != null && visible`; keep the `clearRouteSignal` collector unchanged; verify `:app:compileMobileDebugKotlin` compiles
- [x] 2.2 Verify the existing `MapRendererBlitTest` and any map-draw tests still pass (`./gradlew :app:testMobileDebugUnitTest --tests "*MapRenderer*"`)

## 3. `NavigationViewModel` restart hook (spec: route-panel-ui)

- [x] 3.1 Add `routePanelViewModel?.showRouteOnMap()` at the top of `startNavigation()`; verify `:app:compileMobileDebugKotlin` compiles and existing `NavigationViewModel` tests pass

## 4. `MapCanvasScreen` stop call sites (spec: route-panel-ui, route-summary-dialog)

- [x] 4.1 Add `routePanelViewModel.clearRouteFromMap()` to the route-panel stop handler (L1543), the nav status bar stop handler (L1758), and the nav details overlay stop handler (L1774); verify `:app:compileMobileDebugKotlin` compiles
- [x] 4.2 Add `routePanelViewModel.clearRouteFromMap()` AND the missing `routePanelViewModel.setNavigating(false)` to the summary-dialog stop handler (L1642); verify `:app:compileMobileDebugKotlin` compiles

## 5. Verify

- [x] 5.1 Run the app unit test suite (`./gradlew :app:testMobileDebugUnitTest` or the run-tests skill) and verify all tests pass, including the new route-visibility tests and the existing reroute tests
- [x] 5.2 Build the debug APK (`./gradlew :app:assembleMobileDebug` or the build-app skill) and verify it compiles without errors
- [x] 5.3 On-device check (emulator or phone): calculate a route, start navigation, stop it — route polyline + markers disappear from the map; reopen the route planning dialog — start/dest/vehicle/summary still present and "Start Navigation" available; start again — route redraws; navigate away to Map Manager and back after a stop — route does NOT reappear; inspect `adb logcat -s NaviVeylin` for unexpected errors

## 6. Documentation

- [x] 6.1 Remove the "Bug: route polyline persists after navigation stop" row from `TODO.md` §1 (Route Calculation & Visualization) — the fix supersedes it; note the entry's suggested `routePanelViewModel.clearRoute()` is superseded by `clearRouteFromMap()` (Option B keeps the panel state); verify `TODO.md` no longer lists the bug
