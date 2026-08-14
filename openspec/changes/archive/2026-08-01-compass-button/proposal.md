## Why

Map users lack a visual reference for north direction and GPS fix quality on the map screen. A compass button provides orientation awareness, quick re-centering, and at-a-glance GPS status — all common in navigation apps.

## What Changes

- **New compass button overlay** on the map screen showing animated north direction
- **Two-mode toggle**: "Always north" (map stays north-up) and "Follow direction" (map rotates to bearing), switched via long press
- **Short press** re-centers map on current location
- **GPS fix indicator ring** around compass: light red (no fix), light yellow (bad fix), light green (good fix)
- **Position**: below menu button, above search button in the top-right overlay column
- **Integration** with existing orientation settings (`freeFormNorthUp`, `navNorthUp`) and follow mode
- **Integration** with existing GPS location state for fix quality

## Capabilities

### New Capabilities
- `compass-button`: Animated compass widget showing north direction, GPS fix status ring, mode toggle (long press), and location re-center (short press)

### Modified Capabilities
- `compass-settings`: Compass button mode toggles SHALL update the same orientation settings (`freeFormNorthUp`/`navNorthUp`) that the location options bottom sheet controls — the two UIs SHALL stay in sync
- `map-canvas-screen`: Map screen overlay column SHALL include the compass button between menu and search buttons

## Impact

- **New file**: `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — composable for the animated compass widget
- **Modified file**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add compass button to overlay column, wire state
- **Modified file**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — expose GPS fix quality state, compass mode state
- **Modified file**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasUiState.kt` (or `MapCanvasViewModel.kt` data class) — add compass mode and GPS fix fields
- **Dependencies**: Compose Animation APIs for compass rotation animation
