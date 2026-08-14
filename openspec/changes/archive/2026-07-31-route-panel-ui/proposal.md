## Why

NaviVeylin has route calculation in the JNI bridge (`calculateRouteAsync()`, `RoutingProfile`, `RouteInstruction`, `renderWithRoute()`) but zero UI to use it. Users can search locations and see details, but cannot plan a route from A→B. This is the biggest feature gap vs JavaScout (17 missing route/nav features). A route panel bridges search → routing, enabling turn-by-turn navigation later.

## What Changes

- **Route button** on `LocationDetailsSheet` — opens route panel prefilled with that location as start
- **Route panel** — modal bottom sheet with start/dest fields, vehicle selector, swap, calculate/clear
- **Start field** — prefilled from search result; supports search, favorite picker, current location
- **Dest field** — search-based target selection; supports search, favorite picker, current location
- **Vehicle selector** — car / bicycle / pedestrian toggle
- **Swap button** — swaps start and destination
- **Calculate button** — calls `calculateRouteAsync()` with progress indicator
- **Route polyline** — rendered on map via `renderWithRoute()`
- **Route markers** — `_route_start` / `_route_end` POI types on map
- **Route instructions** — turn-by-turn list in panel after calculation
- **Cancel button** — calls `cancelRoute()` during calculation
- **Clear button** — clears route from map and panel

## Capabilities

### New Capabilities
- `route-panel-ui`: Route planning panel — start/dest selection, vehicle choice, calculate/clear/cancel, route polyline + markers on map, turn-by-turn instruction list

### Modified Capabilities
- `enhanced-details-sheet`: Add "Route" button to `LocationDetailsSheet` that opens route panel with location prefilled as start
- `location-search`: Search results and search panel reused for route start/dest picking (no new search UI needed)

## Impact

- **New file**: `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — route panel composable
- **New file**: `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — route state management
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` — add "Route" button
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — wire route panel state, pass route data to renderer
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — route state fields, calculate/cancel/clear methods
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — wire `renderWithRoute()` for route polyline + markers
- **JNI dependency**: `OSMScoutClient.calculateRouteAsync()`, `cancelRoute()`, `RouteCallback`, `RouteEntry`, `RouteInstruction`, `RoutingProfile`, `Vehicle` — all exist in bridge
