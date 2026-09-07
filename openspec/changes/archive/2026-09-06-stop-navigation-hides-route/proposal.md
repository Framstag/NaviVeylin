## Why

Stopping navigation (`stopNavigation()` + `setNavigating(false)`) clears `NavigationState` but leaves the route polyline and `_route_start`/`_route_end` markers on the map. The clear plumbing exists (`clearRouteSignal` → `mapRenderer.clearRoute()`) but is never triggered on the stop path — all four stop call sites in `MapCanvasScreen.kt` (route panel, summary dialog, nav status bar, nav details overlay) miss it. The route should disappear from the map when navigation stops, while the calculated route stays available in the route planning dialog so the user can start again without recalculating.

## What Changes

- Add `clearRouteFromMap()` to `RoutePanelViewModel`: hides the route from the map (bumps `clearRouteSignal`) **without** resetting panel state — start/destination, vehicle, route summary, steps, and `routeResultFlow` are preserved.
- Add `showRouteOnMap()` to `RoutePanelViewModel`: makes the route visible again (used when navigation restarts).
- Add a `routeVisible` state flag to `RoutePanelViewModel`; the map draw path (`MapCanvasViewModel.setRoutePanelViewModel`) draws the route only when `routeVisible` is true. This prevents the route from being redrawn by StateFlow re-collection (composition re-entry after navigating away/back) and lets restart redraw it.
- Wire `clearRouteFromMap()` into all four stop call sites in `MapCanvasScreen.kt`; fix the summary-dialog stop path (line 1642) which also omits `setNavigating(false)`.
- Wire `showRouteOnMap()` into `NavigationViewModel.startNavigation()` so every start path (route panel, summary dialog, reroute) redraws the route.
- Android Auto is unaffected: `AANavigationController.stopNavigation()` resets `NavigationState`, and `NavigationScreen` already clears its route when `routeLats`/`routeLons` become null.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `route-panel-ui`: new requirement — stopping navigation hides the route from the map while preserving the route panel state (start, destination, vehicle, summary); restarting navigation redraws the route.
- `route-summary-dialog`: extend the "Stop Navigation button" requirement — stopping from the summary dialog hides the route from the map and returns the dialog to summary mode with the route still available.

## Impact

- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — new `routeVisible` StateFlow, `clearRouteFromMap()`, `showRouteOnMap()`; `onSuccess` sets `routeVisible = true`; `clearRoute()` resets it.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `setRoutePanelViewModel` draw collector becomes `combine(routeResultFlow, routeVisible)`; clear collector unchanged.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — four stop call sites add `clearRouteFromMap()`; line 1642 also adds `setNavigating(false)`.
- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — `startNavigation()` calls `routePanelViewModel?.showRouteOnMap()`.
- Tests: `RoutePanelViewModelTest` (or new test class) for `clearRouteFromMap`/`showRouteOnMap`/`routeVisible` transitions; existing reroute tests must stay green (`routeResultFlow` preserved).
- No native/JNI changes, no manifest changes, no guideline changes. Additive behavior fix; rollback = revert the change (route stays visible on stop, as today).
