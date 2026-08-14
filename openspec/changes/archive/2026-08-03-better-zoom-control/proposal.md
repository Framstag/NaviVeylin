## Why

Current zoom control has three separate elements (zoom in, zoom out, zoom level label) stacked vertically with inconsistent styling. It looks disjointed and takes up unnecessary space. A unified pill-shaped control improves visual cohesion and usability.

## What Changes

- Replace three separate zoom elements with single pill-shaped control
- Pill contains "+" button, centered zoom level text, and "-" button in one row
- "+" button greys out when zoom-in not possible (canZoomIn = false)
- "-" button greys out when zoom-out not possible (canZoomOut = false)
- Shadow retained on the pill container
- Use Material 3 surface colors for consistency with rest of UI

## Capabilities

### New Capabilities
- `pill-zoom-control`: Unified pill-shaped zoom control replacing the old three-element layout

### Modified Capabilities

None — this is a pure UI refactor of an existing control. No spec-level behavior changes.

## Impact

- **File modified**: `app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt` — complete rewrite of composable
- **File modified**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — update call site if API changes
- **No API change**: `ZoomControls` composable signature stays the same (canZoomIn, canZoomOut, currentMag, onZoomIn, onZoomOut, modifier)
- **No native code changes**
- **No dependency changes**
