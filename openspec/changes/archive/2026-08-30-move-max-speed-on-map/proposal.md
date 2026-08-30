## Why

On the phone, current speed and max speed are only shown inside the routing status card during active navigation — hidden in follow mode and buried in the status overlay. Android Auto already draws them as on-map widgets (speed badge + round speed-limit sign in the right visualisation region, `SurfaceIndicators`). The phone should match: show the same speed widgets directly on the map in both follow mode and routing view, and drop the speed readout from the routing status overlay.

## What Changes

- Add an on-map speed widget to the phone map (Compose overlay, mirroring the AA `SurfaceIndicators` visualisation): a speed badge showing the current speed, with the max speed as a round sign below it.
- Show the widget in the initial map view while in follow mode (speed from the GPS fix; max speed from `getMaxSpeedAt` at the current position) and during active navigation (speed/max from the navigation engine).
- Keep the overspeed behavior: badge turns red when current speed exceeds max by 5+ km/h (phone convention) — aligned with the AA warning color.
- Remove the current-speed and max-speed columns from the routing status card (`NavigationStatsRow`) and from the expanded navigation details view. ETA, remaining time, remaining distance, road name, and stop button stay.
- Widget hides when no speed data is available and when navigation stops / follow mode is left.

## Capabilities

### New Capabilities
- `map-speed-widget`: phone on-map speed widget — current speed badge with max-speed sign below, shown in follow mode and during navigation, overspeed warning color, hidden when data is unavailable.

### Modified Capabilities
- `navigation-status-details`: the expanded navigation view no longer shows the speed stats — status content is reduced to current road name and ETA / remaining time / remaining distance.
- `navigation-state-display`: speed and max-speed display moves from the routing status overlay to the on-map widget; the "displayed during active navigation" behavior itself is unchanged, but the display location and the follow-mode extension are covered by `map-speed-widget`.

## Impact

- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — remove speed column from `NavigationStatsRow` (shared by status card and details view).
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt` — drop `currentSpeedKmH`/`maxSpeedKmH` params and speed stats.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — place the speed widget overlay (top-right, near the compass block, mirroring AA placement); wire navigation and follow-mode speed sources.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — expose follow-mode current speed (GPS fix `speedKmH`) and max speed (`getMaxSpeedAt` at current position) in `MapCanvasUiState`.
- New composable widget (e.g. `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt`) mirroring AA `SurfaceIndicators` badge + limit sign.
- Tests: Compose widget test (badge/sign rendering, overspeed color, hidden states); update any tests asserting speed in the status card.
- No native, JNI, or dependency changes. AA behavior unchanged.
