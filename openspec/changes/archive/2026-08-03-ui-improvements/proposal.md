## Why

The map screen and navigation UI have several rough edges that reduce usability and polish. Keyboard shortcuts speed up power users, the about dialog is missing author attribution, zoom range is capped below libosmscout's capability, map rotation requires a menu trip instead of a natural gesture, the favorites dialog loses navigation state, and the compass lacks clear visual distinction between orientation modes.

## What Changes

- Add keyboard shortcuts: `+`/`-` for zoom in/out, `Ctrl+F` for search
- Add "Tim Teulings" and "Copyright 2026" to the about dialog
- Ensure about menu item is present in all app menus (map screen, main screen)
- Extend maximum zoom level from 18 to 20
- Add two-finger rotation gesture on the map canvas
- Reset favorites selection dialog to main screen on every open
- Visually differentiate compass "always north" vs "follow direction" modes
- Increase compass ring thickness for better visibility

## Capabilities

### New Capabilities
- `keyboard-shortcuts`: Keyboard shortcuts for zoom (`+`/`-`) and search (`Ctrl+F`) on the map screen
- `map-rotation-gesture`: Two-finger rotation gesture on the map canvas for direct map rotation

### Modified Capabilities
- `about-dialog`: Add author name ("Tim Teulings") and copyright year ("Copyright 2026") to the about dialog content. Ensure about menu item is present in all app menus, not only the map screen overflow menu.
- `zoom-controls`: Extend maximum magnification from 18 to 20. Update zoom button disabled state and all related bounds checks.
- `fav-management-ui`: Reset favorites sheet navigation state to the main group grid on every open, rather than remembering the last sub-screen.
- `compass-button`: Visually differentiate the compass widget between "always north" (north-up) and "follow direction" modes. Increase the GPS fix status ring thickness for better visibility.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — keyboard shortcut handling, two-finger rotation gesture
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — zoom level bounds, rotation state
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — rotation gesture integration
- `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — mode-specific appearance, thicker ring
- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — author/copyright content
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` — reset navigation state on open
- `app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt` — max zoom level 20
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` or `RouteSummaryDialog.kt` — about menu item if missing
- `app/src/main/java/com/naviveylin/ui/map/MainScreen.kt` — about menu item if missing
