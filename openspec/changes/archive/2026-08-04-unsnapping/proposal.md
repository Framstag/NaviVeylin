## Why

When follow-location mode is active, any manual pan or zoom snaps the map back to the GPS position on the next location update, making exploration frustrating. Users need to open the settings sheet to disable follow mode before they can freely browse the map. This friction discourages casual map browsing while navigating.

## What Changes

- **Disengage follow mode on any user-initiated view change** — pan, zoom (pinch/scroll/buttons), rotation, or keyboard zoom. Already implemented in gesture handlers; ensure `disengageFollowMode()` is called in all paths (including zoom buttons and keyboard).
- **Show a "Re-center" button** on the right-side button column when follow mode is off but the user has a GPS fix. Button uses a crosshair/my-location icon.
- **Tapping "Re-center" re-enables follow mode** and centers the map on the current GPS position.
- **Compass button center-click** already re-enables follow mode — keep as-is (alternative entry point).

## Capabilities

### New Capabilities
- `map-recenter-button`: A persistent re-center button on the map overlay that re-enables follow-location mode with one tap. Appears when follow mode is inactive and GPS is available.

### Modified Capabilities
- `javascout-map-follow`: Update requirements to specify that any user-initiated pan/zoom disengages follow mode, and a re-center button appears to re-enable it.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add re-center button to right-side button column, conditionally visible when `!followMode && gpsFixQuality != NONE`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `disengageFollowMode()` already exists; verify all gesture/keyboard paths call it. No new VM logic needed.
- `app/src/main/cpp/libosmscout/openspec/specs/javascout-map-follow/spec.md` — update spec to reflect new behavior
