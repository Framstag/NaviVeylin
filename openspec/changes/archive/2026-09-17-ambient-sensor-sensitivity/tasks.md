# Tasks — Ambient sensor sensitivity + AA dark mode apply

Parent specs: `dark-mode` (MODIFIED "Ambient light sensor sensitivity"), `auto/preferences` (ADDED "Dark mode preference applies to car rendering"). Design: design.md.

## 1. Settings model + migration

- [x] 1.1 Add `@Serializable enum class AmbientLightSensitivity(val darkLux: Float?, val lightLux: Float?)` (`OFF` null/null, `HIGH` 10/50, `MEDIUM` 5/100, `LOW` 2/200) in `AmbientLightClassifier.kt` and replace `AppSettings.ambientLightDarkMode: Boolean` with `ambientLightSensitivity: AmbientLightSensitivity = AmbientLightSensitivity.OFF`. Verify: `AppSettingsTest` compiles and default-off still holds (spec dark-mode "Setting defaults to off").
- [x] 1.2 Migrate old JSON in `SettingsStorage.load()`: parse raw text as `JsonObject`; if old `ambientLightDarkMode` boolean key present, map `true → "HIGH"`, `false → "OFF"`, remove old key, decode normalized text. Verify: new `AppSettingsTest` migration cases — boolean `true` decodes to HIGH, boolean `false` to OFF, missing key to OFF, and a round-trip save/load writes only the new field (spec dark-mode "Old settings files remain valid").
- [x] 1.3 Verify existing `AppSettingsTest` backward-compat tests pass: `./gradlew :app:testDebugUnitTest --tests "*AppSettings*"`.

## 2. Sensor classifier / pipeline / monitor (phone)

- [x] 2.1 Make `classifyLux(previousDark, lux, sensitivity)` level-aware; keep default-HIGH behavior identical to current constants (spec dark-mode "Higher sensitivity enters dark earlier"). Verify: extend `AmbientLightClassifierTest` — same HIGH entry/exit values as today, MEDIUM/LOW enter dark at lower lux, wider leave-dark band, band-hold per level.
- [x] 2.2 Thread the level through `AmbientLightPipeline` and add `AmbientLightMonitor.setSensitivity(level)` that swaps in a fresh pipeline (debounce/hysteresis reset on level change). Verify: `AmbientLightPipelineTest`/`AmbientLightMonitorTest` cover level-aware classification and re-pipeline flip (spec dark-mode "Change applies immediately", "No flapping near the threshold").
- [x] 2.3 Verify sensor tests pass: `./gradlew :app:testDebugUnitTest --tests "*AmbientLight*"`.

## 3. Controller + activity gating

- [x] 3.1 `DarkModeController`: replace `_sensorEnabled`/`sensorEnabled` with `_sensorSensitivity`; `setSensorOption(Boolean)` → `setSensorSensitivity(level)` persisting enum name to `AppSettings.ambientLightSensitivity`; `restoreSensorOption` → `restoreSensorSensitivity`; `resolveDarkPresentation` gate becomes `sensitivity != OFF`. Verify: `MapCanvasViewModelDarkModeTest` (renamed cases) — sensor wins when level != OFF, ignored when OFF, falls back to system when sensor inactive (spec dark-mode "Off disables the sensor").
- [x] 3.2 `MainActivity`: gating combine emits `(active, level)` — stop when `preference != AUTOMATIC || sensitivity == OFF`, else `start()` + `monitor.setSensitivity(level)` on change. Verify: compile; logic covered by controller tests (spec dark-mode "Setting persists across restarts").
- [x] 3.3 `MapCanvasViewModel`: uiState `ambientLightDarkMode` → `ambientLightSensitivity`; `onSetAmbientLightOption` → `onSetAmbientLightSensitivity`; restore from settings. Verify: `MapCanvasViewModelDarkModeTest` and `AppSettingsTest`-backed restore cases pass (spec dark-mode "Change applies immediately").

## 4. Phone UI

- [x] 4.1 `LocationOptionsSheetContent`: replace the ambient `Switch` with a `selectableGroup` of `OrientationOption` rows Off/High/Medium/Low calling `onSetAmbientLightSensitivity`; move/rename `testTag`; add string resources. Verify: `LocationOptionsOverlayComposeTest` updated — each level renders and reports (spec dark-mode "Change applies immediately").
- [x] 4.2 Manual phone verification: set each level, confirm dark flips at noticeably different light levels (darker room for LOW), Off falls back to system night mode, value persists across restart (spec dark-mode scenarios). — verified on device by user (2026-09-17)

## 5. Android Auto — dark mode applies to rendering

- [x] 5.1 `NavigationSession`: add `_darkModePref: MutableStateFlow<String>` seeded from `entryPoint.autoSettingsProvider().load().darkMode` at start (default `"AUTOMATIC"` on failure) and expose `resolvedDark = combine(_hostDark, _darkModePref)` resolving ON → dark, OFF → light, else host; add narrow `updateDarkModePreference(String)` pushed by the preferences callback. Verify: new unit test covers ON/OFF/AUTOMATIC × host resolution (spec auto/preferences "On forces dark", "Off forces light", "Automatic follows host").
- [x] 5.2 `PreferencesScreen`: take `onDarkModeChanged: (String) -> Unit` (constructor, default no-op) and invoke it after each dark-mode row save; `NavigationSession` wires it when creating the screen. Verify: update `PreferencesScreenMapperTest`/screen test that the callback fires with the new value on dark-mode toggle (spec auto/preferences "Preference change applies live").
- [x] 5.3 `MapScreen` and `NavigationScreen`: consume `resolvedDark` instead of `hostDark` (constructor param swap; collect + `pushDark` unchanged mechanics via `CarDaylightApplier` dedup). Verify: existing auto screen tests pass; new test — resolvedDark flip re-pushes stylesheet through the applier, forced ON/OFF yields no re-render on host change (dedup) (spec auto/preferences "Host night change applies live").

## 6. Integration verification

- [x] 6.1 Build both flavors cleanly: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` (all-ABI full build `:app:assembleDebug`).
- [x] 6.2 Full unit suites: `./gradlew test` (app + auto + core + osmscout-client-java).
- [x] 6.3 On-device/emulator dark-mode check (phone flavor): toggle sensitivity levels in the location-options sheet with a controllable light environment; confirm live flip, persistence, Off fallback; confirm no regression in manual dark mode On/Off/Automatic. — verified on device by user (2026-09-17)
- [x] 6.4 On-device AA check (projection emulator or head unit, driving simulation optional): set dark mode On/Off/Automatic from the AA Preferences screen, confirm the map re-renders daylight/dark variant on return and on host night change while map visible; confirm no sensitivity row appears in the list. — verified on device by user (2026-09-17)
