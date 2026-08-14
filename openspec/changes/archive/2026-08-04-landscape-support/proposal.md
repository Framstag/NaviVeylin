## Why

NaviVeylin currently only supports portrait orientation. When the device is rotated to landscape — common in car mounts, foldable tabletop mode, or tablets — the map UI becomes unusable: the vertical button column overlaps the map, buttons sit in the camera notch area, and the zoom pill wastes horizontal space. Landscape support is essential for car use and tablet form factors.

## What Changes

- **Orientation-aware layout**: App detects landscape vs portrait and switches between two distinct overlay arrangements
- **Safe-zone placement**: In landscape, all controls move away from the top edge (camera notch / status bar area) to the bottom or side edges
- **Horizontal zoom pill**: `ZoomControls` renders as a horizontal row instead of vertical column when in landscape
- **Zoom + Favorites side-by-side**: Zoom controls and favorites button placed adjacent in landscape, forming a compact control cluster
- **Compass + Search + Menu repositioned**: These move to the left side or bottom edge in landscape, avoiding the top bezel
- **`BoxWithConstraints`-based orientation detection**: Use Compose `BoxWithConstraints` to detect landscape (width > height) rather than deprecated `Configuration.orientation` — follows Android best practices for foldables and multi-window

## Capabilities

### New Capabilities
- `landscape-layout`: Orientation-aware map screen overlay layout that rearranges controls for landscape mode following Android best practices

### Modified Capabilities
- `map-canvas-screen`: Top-right overlay column requirement changes — in landscape, controls SHALL NOT be placed in the top edge zone; arrangement depends on orientation
- `zoom-controls`: Zoom pill SHALL support horizontal layout variant when in landscape mode

## Impact

- **`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`**: Major layout restructure — add orientation-aware `BoxWithConstraints` branching, reposition all overlay buttons
- **`app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt`**: Add horizontal layout variant (Row instead of Column) with orientation parameter
- **`app/src/main/java/com/naviveylin/ui/map/CompassButton.kt`**: May need repositioning logic (no structural change)
- **`openspec/specs/map-canvas-screen/spec.md`**: Update requirements to describe both portrait and landscape layouts
- **`openspec/specs/zoom-controls/spec.md`**: Add landscape variant requirement
- **`openspec/specs/landscape-layout/spec.md`**: New spec for the orientation-aware layout capability
- **No new dependencies** — pure Compose layout change, no native code or library additions
