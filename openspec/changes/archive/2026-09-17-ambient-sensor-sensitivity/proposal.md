# Proposal — Ambient sensor sensitivity + AA dark mode apply

## Why

The ambient light sensor feature triggers dark presentation too eagerly: with the current fixed thresholds (enter dark below 10 lux, leave above 50 lux), a shadowed or angled phone sensor (e.g. dash-mount facing the cabin on an overcast day) flips Autodark mode while the surroundings are still visually light. Users want control over how readily the sensor triggers dark mode. Separately, on Android Auto the "Dark mode" settings row (On/Off/Automatic) is decorative — the car map always follows the host's day/night signal, so the preference has no effect on the car screen.

## What Changes

- **Ambient sensor sensitivity levels**: replace the boolean ambient-light option with a four-state sensitivity setting — **Off**, **High**, **Medium**, **Low** (Off = sensor disabled, same as today's toggle off). High preserves the current behavior; Medium and Low use progressively lower dark-entry lux thresholds and wider hysteresis bands.
- **Settings model change**: `ambientLightDarkMode: Boolean` becomes `ambientLightSensitivity` (enum). Migration on decode: `true -> High`, missing/false -> `Off`. Default stays `Off` (today's default). **BREAKING** for the persisted settings field name/type; existing values migrate, no data loss.
- **Phone surface**: the ambient dark-mode section in the location-options sheet shows the four-state selector; changing level re-creates the sensor pipeline with the new thresholds and applies immediately.
- **Android Auto — sensitivity intentionally not exposed**: the car screen stays host-driven (head unit / headlights). No AA sensitivity row — a dead setting row would repeat the "setting ignored" sin. Note: AAOS-only installs have no phone UI to reach the setting, so sensor behavior there stays host-driven by design.
- **Android Auto — Dark mode row fixed**: the AA "Dark mode" preference now actually applies: **On** forces dark, **Off** forces light, **Automatic** follows the host day/night. Map and navigation screens resolve dark from the preference × host signal and re-render the stylesheet variant when either changes.

## Capabilities

### New Capabilities
- *none*

### Modified Capabilities
- `dark-mode`: ambient light sensor option requirement changes from a boolean enable to a four-state sensitivity setting (Off/High/Medium/Low) with level-specific thresholds and persistence migration.
- `auto/preferences`: new requirement — the dark mode preference SHALL apply to the car map and navigation rendering (On/Off/Automatic), reacting to preference changes and host day/night changes without restart.

## Impact

- **Persistence**: `AppSettings` JSON schema (`SettingsStorage.kt`) — field rename + enum decode with migration; `AppSettingsTest` backward-compat cases.
- **Phone pipeline**: `AmbientLightClassifier.kt` (level-aware thresholds), `AmbientLightMonitor.kt` / `AmbientLightPipeline` (level-driven pipeline creation), `DarkModeController.kt` (sensitivity state + persistence, rename of `setSensorOption`), `MainActivity.kt` (monitor re-creation on level change), `MapCanvasViewModel.kt` (`ambientLightDarkMode` -> sensitivity), `LocationOptionsOverlay.kt` (sheet control).
- **Auto module**: `NavigationSession.kt` (resolved-dark flow combining host day/night + shared dark mode preference), `MapScreen.kt` / `NavigationScreen.kt` (consume resolved dark, re-push stylesheet on preference change), `PreferencesScreenMapper.kt` (dark mode row unchanged functionally, now has an effect), `AutoSettingsMapping.kt` (field copy if needed).
- **Tests**: classifier threshold tests per level, settings migration tests, monitor/pipeline level tests, ViewModel tests, AA screen/rendering tests for preference application, Compose test updates.
- **Specs**: `specs/dark-mode` (modified), `specs/auto/preferences` (modified).
