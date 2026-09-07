# Tasks: Fix phone reroute route not drawn on map

Parent spec: `reroute-route-visibility` (specs/reroute-route-visibility/spec.md). Design: design.md.

## 1. RoutePanelViewModel: no-clear reroute update + error surfacing (spec: reroute-route-visibility — Route stays drawn during reroute calculation, Failed reroute calculation is surfaced during navigation)

- [x] 1.1 Add `updateLocationsForReroute(start: LocationEntry, dest: LocationEntry)` to `RoutePanelViewModel.kt` — copies both into `_uiState` without calling `clearRouteIfNeeded`; add KDoc explaining it must NOT clear the drawn route; verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 Add `routeErrorEvent: SharedFlow<String>` to `RoutePanelViewModel.kt` (`extraBufferCapacity = 1`, `tryEmit` from `onError`); add `Log.e(TAG, "Route calculation failed: $message")` in `onError`; verify compile
- [x] 1.3 Add unit tests `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelRerouteTest.kt` (Robolectric, `MainDispatcherRule`, `FakeOSMScoutClient`): `updateLocationsForReroute` sets start/dest, keeps `routeResultFlow.value` unchanged, does NOT bump `clearRouteSignal`, keeps `routeEntry`; `onError` emits on `routeErrorEvent`; verify `./gradlew :app:testMobileDebugUnitTest --tests "*RoutePanelViewModelRerouteTest*"` passes

## 2. NavigationViewModel: reroute uses no-clear update + route geometry parity (spec: reroute-route-visibility — Route stays drawn during reroute calculation, Phone and Android Auto parity for route geometry state, Panel-driven route clearing unchanged)

- [x] 2.1 Change `confirmReroute` (NavigationViewModel.kt:469) to call `vm.updateLocationsForReroute(currentLoc, destLoc)` instead of `setStartLocation` + `setDestLocation`; verify compile
- [x] 2.2 Extend `startNavigation` (NavigationViewModel.kt:144) `_state.value.copy(...)` with `routeLats = routeEntry.latitudes, routeLons = routeEntry.longitudes` (mirror `AANavigationController.kt:209-210`); verify `stopNavigation` already clears via `NavigationState()` default; verify compile
- [x] 2.3 Add/extend unit tests in `app/src/test/java/com/naviveylin/navigation/` (Robolectric per AGENTS.md classloader rule — do NOT set `@Config(sdk=...)`/`@GraphicsMode` on classes touching `FakeOSMScoutClient`): `startNavigation` populates `state.routeLats`/`routeLons` from the `RouteEntry`; `stopNavigation` nulls them; verify `./gradlew :app:testMobileDebugUnitTest --tests "*NavigationViewModel*"` passes

## 3. MapCanvasScreen: snackbar on reroute-calc failure while navigating (spec: reroute-route-visibility — Failed reroute calculation is surfaced during navigation)

- [x] 3.1 In `MapCanvasScreen.kt`, add `LaunchedEffect(routePanelViewModel)` collecting `routePanelViewModel.routeErrorEvent`; when `navigationViewModel.state.value.isNavigating`, call `viewModel.showSnackbar(message)` (existing channel at MapCanvasViewModel.kt:2101 → snackbar UI at MapCanvasScreen.kt:483); do not show the snackbar when the panel is open/not navigating (in-panel error text already covers that); verify compile
- [x] 3.2 Verify/extend Compose test coverage for the snackbar path if feasible (follow `RoutePanelComposeTest.kt` fixture); otherwise document in the test file why (snackbar channel requires MapCanvasScreen VM wiring) and rely on on-device verification

## 4. Build and regression verification

- [x] 4.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 4.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `RoutePanelComposeTest.kt`, `RoutePanelViewModelSearchHistoryTest.kt`, `RerouteConfirmationGateTest.kt`, `RouteGeometryTest.kt`, and all navigation tests
- [x] 4.3 Confirm the unrelated uncommitted changes in the working tree (north-up marker bearing, regional search, fast-reroute-trigger) remain untouched by this change (`git status` review before commit)

## 5. On-device verification (spec: reroute-route-visibility — scenarios)

- [x] 5.1 Scripted deviation (GPX replay or mock-GPS app) while navigating: confirm the previously drawn route stays visible during the reroute calculation and the new route replaces it on success (logcat `adb logcat -s NaviVeylin`; watch `onRerouteRequest: rerouting` → route draw)
- [x] 5.2 Induce a reroute calc failure (e.g., destination in unmapped area): confirm logcat shows `route calculation failed: <msg>` and the phone shows a snackbar; confirm the last route remains on the map
- [x] 5.3 Confirm 5.1/5.2 behave identically on Android Auto / AAOS if a head unit or AAOS emulator is available (car path already draws from `NavigationState.routeLats`; expect parity with no changes)
