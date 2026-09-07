## Why

Phone "Automatic" dark mode currently follows the system night mode, which Android derives from time (sunset/sunrise) or the user setting — not from ambient light. Driving through a tunnel at noon or an overcast day keeps the app light. The user wants light-based switching. Android has no OS-level ambient-light→dark-mode API, so the app must read `Sensor.TYPE_LIGHT` itself — but only as an opt-in enhancement, keeping the OS-level system-night-mode path as the default.

## What Changes

- New `AmbientLightMonitor`: reads `SensorManager.TYPE_LIGHT` with hysteresis (dark below ~10 lux, light above ~50 lux) and debounce (~30 s), lifecycle-aware (register on START, unregister on STOP), no permission needed, falls back to system night mode when no sensor exists.
- New persisted config option `ambientLightDarkMode: Boolean = false` in `AppSettings`, exposed as a toggle in the dark-mode section of the phone settings overlay (next to On/Off/Automatic).
- `DarkModeController` resolution: when the config option is enabled AND the preference is **Automatic**, the environment signal comes from the light sensor; otherwise from system night mode. The existing `setEnvironmentDark` extension point is reused.
- Sensor listening only active when preference = **Automatic** + config enabled + app foreground (battery).
- Additive, non-breaking; rollback = revert commit. Default behavior (follow system) unchanged.

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `dark-mode`: "Automatic mode follows environment dimming" gains the ambient-light sensor as an additional environment source behind the existing resolution path, gated by a persisted config option; "Manual dark mode control" gains the config toggle.

## Impact

- New `app/src/main/java/com/naviveylin/data/AmbientLightMonitor.kt` — sensor reading, hysteresis, debounce, lifecycle.
- `app/src/main/java/com/naviveylin/data/DarkModeController.kt` — environment source resolution (sensor vs system).
- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — new `ambientLightDarkMode` field (old settings files stay valid via default).
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — config toggle UI.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — wire toggle + sensor state into controller.
- `app/src/main/java/com/naviveylin/MainActivity.kt` — start/stop monitor, feed controller.
- Tests: hysteresis resolution unit tests (pure function), controller resolution tests, settings migration test.
- `guidelines/UI.md` — document the config option and its placement.
- No impact on other active changes; AA path untouched (AA follows host, see `aa-dark-mode-follow-host`).
