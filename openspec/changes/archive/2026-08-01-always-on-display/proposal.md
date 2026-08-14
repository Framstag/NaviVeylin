## Why

During turn-by-turn navigation, the device screen may time out and turn off, forcing the driver to tap the screen to resume guidance. This is a safety hazard and degrades the navigation experience. The app should keep the screen on while routing is active, with a user-controllable toggle to opt out.

## What Changes

- Add `keepScreenOn` boolean to `AppSettings` (default `true`) persisted via `SettingsStorage`
- Add "Keep screen on during navigation" toggle to the location options bottom sheet, visible only during active navigation
- Apply `FLAG_KEEP_SCREEN_ON` on the `MainActivity` window when navigation is active AND the setting is enabled
- Remove the flag when navigation stops or the setting is disabled
- Load the persisted setting on app start and apply it reactively

## Capabilities

### New Capabilities
- `always-on-display`: Keep the device screen on during active turn-by-turn navigation, with a persistent user toggle to enable/disable the behavior

### Modified Capabilities
<!-- No existing specs change — this is a new capability -->

## Impact

- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — add `keepScreenOn: Boolean = true` field to `AppSettings`
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — add "Keep screen on" toggle row in bottom sheet (visible during navigation)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — wire toggle to settings save/load, expose `keepScreenOn` in `MapCanvasUiState`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — observe navigation state + setting, apply/remove `FLAG_KEEP_SCREEN_ON` on activity window
- `app/src/main/java/com/naviveylin/MainActivity.kt` — no changes needed (flag applied via window from screen composable)
