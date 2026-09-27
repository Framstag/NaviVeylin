# browse-drive-modes

## Why

The phone start screen is a full-screen map that conflates two different intents: free driving (follow position, auto-zoom, heading-up) and browsing/searching (free-form pan/zoom, north-up). Both share one sticky map configuration — `followMode`, `autoZoomEnabled`, orientation, viewport — so switching between them requires manually toggling settings. There is no explicit mode concept: the current "mode" is derived from scattered booleans (`followMode` in `MapCanvasUiState`, `isNavigating` in `NavigationState`), with no single source of truth.

## What Changes

- **Explicit map modes**: introduce a derived `MapMode` enum (`BROWSE`, `FREE_DRIVE`, `NAVIGATION`) as the single source of truth for the map's driving state, replacing the implicit boolean derivation.
- **Always start in Browse mode**: the app boots into browse (follow off, north-up, last persisted viewport). `followMode` is demoted from a persisted setting to runtime state — drive mode is per-session intent, never restored on start.
- **Right-column location button becomes the mode toggle**: in browse, one tap enters free drive (follow on, auto-zoom on, heading-up, centered on position); in free drive, one tap exits to browse (follow off, north-up, staying at the current position).
- **Drift-triggered re-center button**: the bottom-left re-center button appears only when the user manually pans/zooms away from the auto state. In browse it centers on the GPS position (staying in browse); in free drive it resets to the standard drive values (follow + auto-zoom + heading-up + driving zoom). Pressing it hides the button. `autoZoomPaused` generalizes into a `driveSuspended` runtime flag covering pan/zoom/rotate.
- **Config sheet restructured (no mode switch)**: the location-options sheet shows a header with the current state (Browse / Free drive / Navigation) and that state's options only. Mode switching stays in the UI (right-column button), never in config. The "Map follows position" toggle is removed — replaced by the mode model.
  - Browse section: orientation (north-up / free rotation) — `freeFormNorthUp`
  - Driving section (shared by free drive AND navigation): auto-zoom toggle + orientation (follow direction / north-up) — `autoZoomEnabled` + `navNorthUp`
  - General section unchanged: keep screen on, lane hints, dark mode, ambient light, rendering, map style
- **Config sheet reachable during navigation**: the navigation right column gains the location-options gear (currently absent), so driving config is adjustable mid-route.
- **Additive, non-breaking**: no persisted-format change; `followMode`/`autoZoomPaused` semantics shift but the settings file fields stay. Rollback: revert the change; behavior returns to today's single-config map.

## Capabilities

### New Capabilities

- `map-modes` (`openspec/specs/map-modes/spec.md`): the map SHALL have an explicit mode model — Browse (default on start), Free drive (one-tap preset), Navigation (route active) — with a derived `MapMode` enum, per-mode config presets, drift-triggered re-center with mode-dependent action, and drive suspension semantics.

### Modified Capabilities

- `location-options-ui` (`openspec/specs/location-options-ui/spec.md`): the "Map follows position" toggle is removed; the sheet SHALL show a mode header + active-state section (Browse or Driving) + general options, with no mode switch; the sheet SHALL be reachable during navigation.
- `map-canvas-screen` (`openspec/specs/map-canvas-screen/spec.md`): the right-column location button SHALL toggle between browse and free drive; the re-center button SHALL be drift-triggered with mode-dependent action.

## Impact

| Area | Files |
|------|-------|
| State model | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `MapMode` enum, `driveSuspended` flag, mode presets (`enterFreeDrive`/`exitFreeDrive`/`resetDrivePreset`), re-center logic, `followMode` no longer restored from settings |
| Map screen | `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — right-column button as mode toggle, re-center button wiring, nav right column gains location-options gear |
| Config sheet | `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — mode header, Browse/Driving sections, no mode switch, no "Map follows position" |
| Persistence | `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — `followMode` no longer restored (field may stay for backward compat or be dropped) |
| Specs | `openspec/specs/map-modes/spec.md` (new), `openspec/specs/location-options-ui/spec.md`, `openspec/specs/map-canvas-screen/spec.md` (deltas in this change) |
| Guidelines | `guidelines/UI.md` — document the mode model, mode-switch placement, re-center semantics |

**Scope**: general feature — phone/mobile variant. Android Auto already separates browse/free-driving/navigation via distinct screens (`RootScreen` → `MapScreen`/`FreeDrivingScreen`/`NavigationScreen`); the phone gains an explicit mode model instead. No AA behavior change.

**Rollback**: revert the change; the map returns to today's single-config behavior. No data impact (settings fields unchanged).

**Verification**: build both flavors; unit tests for the mode derivation, preset application, and re-center visibility; on-device — start in browse, one-tap drive entry, drift-triggered re-center in both modes, sheet sections per state, sheet reachable mid-route.
