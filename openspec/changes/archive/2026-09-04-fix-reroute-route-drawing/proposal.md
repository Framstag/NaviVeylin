# Proposal: Fix phone reroute route not drawn on map

## Why

On the phone variant the new route is not drawn on the map after a reroute happens. Full pipeline trace (reroute → route drawing):

1. `onRerouteRequest` gate confirms → `NavigationViewModel.confirmReroute` (NavigationViewModel.kt:469) → `setStartLocation` → `clearRouteIfNeeded` (RoutePanelViewModel.kt:200) → **`_routeResultFlow.value = null` + `_clearRouteSignal++` → the old route is removed from the map**.
2. `calculateRoute()` runs the native route calculation asynchronously.
3. **Calc succeeds** → `onSuccess` → `_routeResultFlow.value = RouteResult(...)` → `MapCanvasViewModel` collector → `mapRenderer.setRoute(...)` → new route drawn. Works.
4. **Calc fails** → `onError` (RoutePanelViewModel.kt:267) sets `RouteState.Error(message)` but **never emits a new `RouteResult`** → the map shows no route at all. The message is also effectively invisible: `onError` has no `Log.e`, the route panel is hidden during navigation, and the phone UI never renders `NavigationState.errorMessage`.

The failure is silent and leaves the user with a route-less map, matching the reported symptom. Root fragility: the phone's route drawing depends *entirely* on `routeResultFlow`, which the reroute clears before the new route is ready. The car/AAOS variant is immune — `AANavigationController.startNavigation` stores `routeLats`/`routeLons` in `NavigationState` (AANavigationController.kt:209-210) and its map draws from that state, so a failed recalc leaves the last route on screen.

## What Changes

- **Keep the last drawn route during reroute calculation**: reroute updates start/destination without triggering `clearRouteIfNeeded`, so the old route stays visible until the new one is ready. On success the new `RouteResult` replaces it (`mapRenderer.setRoute` overwrites the polyline); on failure the last route remains on screen.
- **Surface route-calc failures during navigation**: `Log.e` in `RoutePanelViewModel.onError` plus a phone snackbar while navigating (the established notification pattern on `MapCanvasScreen`, same channel as existing snackbars).
- **Car parity for shared state**: `NavigationViewModel.startNavigation` stores `routeLats`/`routeLons` in `NavigationState` exactly like `AANavigationController` does, so the shared state carries route geometry consistently on both surfaces.

## Capabilities

- **New Capabilities**:
  - `reroute-route-visibility` — the route stays drawn during reroute calculation, and failed reroute calculations are surfaced (log + snackbar) instead of failing silently.
- **Modified Capabilities**: none. `reroute-trigger` (in-progress change `fast-reroute-trigger`, uncommitted) is unaffected — trigger timing logic is untouched. `rerouting-visual-feedback` and `navigation-controller` are untouched.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — `confirmReroute` switches to the new no-clear reroute location update; `startNavigation` stores `routeLats`/`routeLons` in `_state` (parity with car).
  - `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — new `updateLocationsForReroute(start, dest)` (updates start/dest without clearing the drawn route); `onError` logs and emits a reroute-error event.
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — collects the reroute-error event and shows a snackbar while navigating.
- **Tests**:
  - `app/src/test/java/com/naviveylin/ui/route/` — new unit tests: reroute location update must NOT clear `routeResultFlow`/emit a clear signal; `onError` must emit the error event.
  - `app/src/test/java/com/naviveylin/navigation/` — test that `startNavigation` populates `state.routeLats/routeLons`.
  - Existing `RoutePanelComposeTest.kt`, `RoutePanelViewModelSearchHistoryTest.kt`, and navigation tests stay green.
- **No native change**: no submodule patch, no JNI, no vcpkg/CMake impact.
- **Scope**: phone. Android Auto/AAOS is already correct (car draws from `NavigationState.routeLats`, errors already visible via `NavigationState.errorMessage` on the car screen) — this change only aligns the phone.
- **Additive**, not breaking. Rollback: revert the three Kotlin files; behavior returns to the current state (route cleared on reroute, silent calc failures).
- **Guidelines**: `guidelines/MapRendering.md` — document the keep-route-on-reroute behavior of the render pipeline. `guidelines/UI.md` — snackbar is already the established phone notification pattern; no change needed.
