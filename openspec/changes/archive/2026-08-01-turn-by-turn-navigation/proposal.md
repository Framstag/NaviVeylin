## Why

The route summary dialog has a "Start Navigation" button that does nothing. The JNI bridge already provides `NavigationController`, `NavigationListener`, and `startNavigationWithVehicle()` — but no Kotlin/Compose UI wires them together. Turn-by-turn navigation turns the app from a route planner into a real navigation tool.

## What Changes

- Wire "Start Navigation" button in `RouteSummaryDialog` and `RoutePanel` to `OSMScoutClient.startNavigationWithVehicle()`
- Add "Stop Navigation" button to stop active navigation
- Implement GPS follow mode (map auto-centers on location during navigation)
- Show next turn overlay (turn icon, distance, street name) during active navigation
- Show navigation state (ETA, remaining distance, current speed)
- Auto-zoom by speed during navigation
- Handle reroute requests when off-route

## Capabilities

### New Capabilities
- `navigation-controller`: Manages navigation lifecycle — start, stop, GPS follow mode, reroute handling. Wraps JNI `NavigationController` + `NavigationListener`.
- `next-turn-overlay`: Shows next manoeuvre during active navigation — turn type icon, distance, street name, "next next" hint for close-following turns.
- `navigation-state-display`: Shows ETA, remaining distance, current speed, max allowed speed during active navigation.

### Modified Capabilities
- `route-summary-dialog`: "Start Navigation" button becomes functional. Add "Stop Navigation" button when navigation is active. Dialog reuses step list with current-step highlighting during navigation.
- `route-panel-ui`: Add "Start Navigation" button when route is calculated and navigation is not active.

## Impact

- **New file**: `app/src/main/java/com/naviveylin/navigation/NavigationController.kt` — Kotlin wrapper around JNI `NavigationController` + `NavigationListener`
- **New file**: `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — next turn display composable
- **New file**: `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — ETA/speed display composable
- **Modified**: `RouteSummaryDialog.kt` — wire Start Navigation, add Stop Navigation
- **Modified**: `RoutePanel.kt` — add Start Navigation button
- **Modified**: `RoutePanelViewModel.kt` — add navigation state, active step tracking
- **Modified**: `MapCanvasViewModel.kt` — add follow mode, location processing during navigation
- **Modified**: `MapCanvasScreen.kt` — compose navigation overlays, wire follow mode
- **No native/JNI changes** — all infrastructure exists in `OSMScoutClient.cpp`
