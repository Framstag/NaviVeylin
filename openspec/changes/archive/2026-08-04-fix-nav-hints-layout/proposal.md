## Why

During active navigation, the NextTurnOverlay (turn instructions + lane hints) spans full screen width and overlaps the on-map buttons (menu, compass, search, favorites, zoom controls) in the top-right corner. This makes buttons hard to tap and looks cluttered.

## What Changes

- Constrain NextTurnOverlay width so it does not extend under the top-right button column
- Remove left-side gap on NextTurnOverlay — align it flush to the left display edge
- Keep NavigationStateOverlay (routing status panel) at full width
- Remove horizontal padding from NavigationStateOverlay for true edge-to-edge appearance

## Capabilities

### New Capabilities
- `nav-hints-layout`: Layout constraints for the navigation hints overlay — width limited to avoid covering on-map buttons, left-aligned without margin

### Modified Capabilities
- *(none — no existing spec-level behavior changes)*

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — NextTurnOverlay and NavigationStateOverlay modifier changes
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — width constraint and padding changes
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — full-width padding changes
