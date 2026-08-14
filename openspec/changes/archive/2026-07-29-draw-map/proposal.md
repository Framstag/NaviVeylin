## What Changes

Add map rendering to NaviVeylin using libosmscout's Cairo backend. After a map is downloaded, display it on screen with interactive pan and zoom. Add zoom controls (in/out buttons) overlaid on the map. Persist the map viewport center and zoom level to a file so the map restores to the same location on app restart.

## Capabilities

### New Capabilities

- `map-render`: Render a libosmscout map using the Cairo backend onto a Compose canvas. Handle projection, tile sizing, and draw loop.
- `map-pan-zoom`: Touch-based pan (drag) and pinch-to-zoom. Update viewport state in response to gestures.
- `zoom-controls`: Floating zoom in/out buttons overlaid on the map display. Each button adjusts zoom level by one step.
- `viewport-persist`: Save current map center (lat/lon) and zoom level to a local file on pause/stop. Load and restore on startup.

### Modified Capabilities

*(None — first rendering change.)*

## Impact

- **New files:** Map rendering composable, gesture handler, zoom controls composable, viewport persistence manager
- **Modified files:** Main activity / nav graph to show map screen after download completes
- **Dependencies:** libosmscout Cairo renderer already linked via NDK; JNI bridge methods for draw, pan, zoom
- **Data:** Viewport state file stored in app internal storage
