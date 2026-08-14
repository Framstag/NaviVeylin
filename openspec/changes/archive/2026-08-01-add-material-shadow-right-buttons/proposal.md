## Why

Right-side map overlay buttons (menu, search, favorites, location options, zoom controls) lack Material 3 elevation, making them appear flat against the map canvas. This reduces visual hierarchy — buttons should feel raised above the map surface per Material Design 3 guidelines.

## What Changes

- Add Material 3 elevation to all right-side column buttons in `MapCanvasScreen.kt`
- Add elevation to `ZoomControls.kt` buttons
- Add elevation to `LocationOptionsOverlay.kt` button
- Keep existing `surfaceContainerHigh` container colors; elevation is additive

## Capabilities

### New Capabilities
- `button-elevation`: Standard Material 3 elevation (shadow) on map overlay icon buttons for visual hierarchy

### Modified Capabilities
- None

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add `elevation` param to `IconButton` (menu), `FilledTonalIconButton` (search, favorites)
- `app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt` — add `elevation` param to `FilledIconButton` (zoom in/out)
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — add `elevation` param to `FilledTonalIconButton` (location options)
- No new dependencies, no API changes, no native code changes
