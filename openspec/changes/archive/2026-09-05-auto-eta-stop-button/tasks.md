# Tasks: Auto ETA Stop Button

## 1. NavigationManager wiring (spec: auto/navigation-view — "Leave navigation at any time")

- [x] 1.1 In `NavigationSession.startObserving`, register a `NavigationManagerCallback` and call `navigationManager.navigationStarted()` when `isNavigating` flips true; call `navigationManager.navigationEnded()` then `clearNavigationManagerCallback()` when it flips false; verify with a new unit test that the callback is registered on navigation start and cleared on stop
- [x] 1.2 Wire the callback's `onStopNavigation()` to `navigationViewModel.stopNavigation()`; verify with a unit test that invoking the callback stops navigation
- [x] 1.3 In `NavigationSession.onDestroy`, clean up while navigating: `navigationEnded()` then `clearNavigationManagerCallback()`, wrapped in `runCatching`; verify with a unit test that destroy while navigating does not throw

## 2. Remove the app-owned stop action (spec: auto/navigation-view — "Leave navigation at any time")

- [x] 2.1 Remove the `stopAction` from the navigation map action strip in `NavigationScreen.buildTemplate` (route-description action stays); verify with a screen test that the map action strip contains only the route-description action and no stop/back action
- [x] 2.2 Extract the navigation map action strip (route-description action only) into a testable factory `NavigationScreenActions.navigationMapActionStrip`; `stopAction` stays in `NavigationScreenActions` (FreeDrivingScreen still uses it — free driving has no ETA card); verify the `:auto` module compiles without warnings
- [x] 2.3 Update `NavigationTemplateFactoryTest` (map action strip fixture 2 → 1 action) and add a `navigationMapActionStrip` test to `NavigationScreenActionsTest` (the `stopAction` cases stay — the factory is still used by FreeDrivingScreen); verify the updated tests pass

## 3. Verification

- [x] 3.1 Verify the `:auto` module compiles: `./gradlew :auto:compileDebugKotlin` (or the build-app skill) succeeds without errors
- [x] 3.2 Run the `:auto` unit test suite (`./gradlew :auto:testDebugUnitTest` or the run-tests skill) and verify all tests pass, including the new session tests
- [x] 3.3 On-device check (AA emulator or head unit): navigate with a travel estimate, tap the host ETA card stop button, and verify navigation stops and the screen returns to the root menu; verify the navigation map action strip shows no "x" button; inspect `adb logcat -s NaviVeylin` for the stop path
