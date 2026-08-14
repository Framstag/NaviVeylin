## Why

Navigation apps are used at night and in tunnels, where a bright map and bright UI controls glare and impair visibility. The app should dim both its controls and its map rendering when dark presentation is requested. Today the app follows the system theme for Compose controls but the map stylesheet always renders the light `daylight` variant, and there is no user control over dark mode.

## What Changes

- Add a three-state dark mode setting: **On**, **Off**, **Automatic** (default), persisted in `AppSettings` JSON via `SettingsStorage`.
- **Automatic** resolves to the system night mode (`isSystemInDarkTheme()`); the car environment (`CarContext.isDarkMode()`) is designed as a future signal source behind the same abstraction (car integration deferred).
- Drive the Compose Material theme (`NaviVeylinTheme`) from the resolved dark-mode state instead of raw `isSystemInDarkTheme()`.
- Switch the map to its dark rendering by setting the `daylight` style sheet flag to `false` and reloading the style sheet — mirroring the OSMScout2 demo mechanism (`map.toggleDaylight()` → `DBThread` `toggleDaylight`/`setStyleFlag` slots → `stylesheetFlags["daylight"]` → `LoadStyleInternal`). `standard.oss` already ships `IF daylight` dark variants.
- Expose a JNI API on `OSMScoutClient` to set the `daylight` style flag (e.g. `setStyleSheetFlag("daylight", boolean)`), implemented in `OSMScoutClient.cpp` against the existing `DBThread::setStyleFlag` slot, triggering a full map re-render on change.
- Dark mode is controlled solely via the settings control (On/Off/Automatic). An on-map toggle button (as in the OSMScout2 demo) was considered and dropped — settings control suffices.

## Capabilities

### New Capabilities
- `dark-mode`: resolves the three-state dark-mode preference (on/off/automatic), applies it to Compose control theming, and switches the map style sheet between daylight and dark variants via the native `daylight` flag.

### Modified Capabilities
<!-- No existing spec-level behavior changes: dark-mode introduces a new capability and does not alter existing requirement contracts. -->

## Impact

- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — add `darkMode` field (enum) to `AppSettings`; keep JSON backward compatibility (old files decode with defaults)
- `app/src/main/java/com/naviveylin/ui/theme/Theme.kt` — `NaviVeylinTheme` takes resolved `darkTheme` from preference state
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — load/prefer dark mode, expose toggle + environment resolution; `MapCanvasUiState` gains dark-mode state
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — three-state control (On/Off/Automatic)
- `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java` — new native method for style flag / daylight
- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — JNI implementation calling `DBThread::setStyleFlag` / `toggleDaylight`
- `app/src/main/assets/stylesheets/standard.oss` — already contains `IF daylight` dark variants; verify coverage
- Map re-render invalidates style-dependent tile cache (upstream PR #1701: daylight flag changes area fill patterns — invalidate cached images)
- No new Gradle/vcpkg dependencies
