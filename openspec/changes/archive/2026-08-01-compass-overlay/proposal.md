## Why

The app currently has no way to control map orientation. In free-form mode the map always shows north-up, and during navigation the map rotates to driving direction but the user cannot override this. Users need per-mode orientation control (north-up vs. direction-follow) persisted across restarts, with a proper Material 3 bottom-sheet settings UI instead of the current dropdown.

## What Changes

- Replace the current `LocationOptionsOverlay` dropdown with a full-width Material 3 bottom sheet
- Add two orientation options per mode: "North always up" and "Follow direction"
- Two modes: free-form (non-navigation) and navigation
- Persist all settings to `AppSettings` JSON so they survive app restart
- Remove the old dropdown-based options UI

## Capabilities

### New Capabilities
- `compass-settings`: Per-mode map orientation settings (north-up vs. direction-follow) with persistence

### Modified Capabilities
- `location-options-ui`: Replace dropdown dialog with Material 3 bottom sheet; add orientation controls alongside existing follow-mode and auto-zoom toggles

## Impact

- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — `AppSettings` gains new fields for per-mode orientation
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — Rewrite from dropdown to `ModalBottomSheet`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — New state fields for orientation modes; wire persistence
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — Update `LocationOptionsOverlay` call site; pass new state/callbacks
- `openspec/specs/location-options-ui/spec.md` — Updated requirements
- New `openspec/specs/compass-settings/spec.md` — Orientation settings spec
