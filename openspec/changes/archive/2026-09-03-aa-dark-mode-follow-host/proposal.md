## Why

On Android Auto, the host (head unit) decides day/night from its own ambient light sensor or time and renders all templates (panels, buttons, headers) in that theme. NaviVeylin's map surface, however, is app-drawn via `SurfaceCallback` and always loads the stylesheet with the `daylight` flag set — so at night the car shows dark UI chrome around a light map. The app must follow the host's day/night decision instead of hardcoding daylight.

## What Changes

- The AA map path (both `MapScreen` and `NavigationScreen`) reads the host's day/night state from `CarContext.isDarkMode()` and pushes it to the native stylesheet via `client.setStyleSheetFlag("daylight", !dark)`, then re-renders.
- `NavigationSession` overrides `onCarConfigurationChanged(Configuration)` and exposes the host's dark state as a flow; screens re-push the flag on change (live switch, e.g. tunnel entry).
- A new deduped applier (mirroring `CarStyleApplier`) applies the daylight flag only on value change; a failed native apply resets the marker so the next push retries.
- No change to the stylesheet selection, the render pipeline, or the phone-side dark mode controller. Templates already follow the host — only the app-drawn map surface changes.
- Additive behavior change; rollback = revert commit.

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `auto-map-renderer`: "Map rendered on car display" gains the requirement that the map surface follows the host's day/night state (dark variant at night, daylight variant by day, live switch on host configuration change).

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — override `onCarConfigurationChanged`, expose host dark state flow.
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — collect dark state, push daylight flag + `requestRender()`.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — same.
- New `auto/src/main/java/com/naviveylin/auto/CarDaylightApplier.kt` (or extend `CarStyleApplier`) — deduped flag push.
- Tests: `CarDaylightApplierTest.kt` (dedupe + retry), screen tests for initial state and live switch.
- `guidelines/MapRendering.md` — document the AA daylight-flag contract (host-driven, mirrors phone `pushDarkPresentation`).
- Covers both Android Auto projection and AAOS (same `:auto` module, `CarAppActivity`).
- No impact on other active changes (rotation, reroute, overlays — none touch the AA style path).
