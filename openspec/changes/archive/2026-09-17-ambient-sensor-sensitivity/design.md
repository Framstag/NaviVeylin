# Design — Ambient sensor sensitivity + AA dark mode apply

## Context

See `proposal.md` — Why. Current state that shapes this design:

- Phone: `AmbientLightMonitor` (fixed `DARK_LUX=10`, `LIGHT_LUX=50`, `DEBOUNCE_MS=10 s`) → `DarkModeController` → `resolveDarkPresentation(preference, environmentDark, sensorDark, sensorEnabled)`. Monitor gated in `MainActivity` by `combine(preference == AUTOMATIC, sensorEnabled)` over `Lifecycle.State.STARTED`.
- Persistence: `AppSettings` (kotlinx.serialization, `ignoreUnknownKeys = true`), field `ambientLightDarkMode: Boolean = false`.
- Phone UI: `LocationOptionsSheetContent` — dark mode section already uses a `selectableGroup` of `OrientationOption` rows; the ambient option is a `Switch` (`ambientLightToggle`).
- AA: `NavigationSession.hostDark` ← `carContext.isDarkMode()`; `MapScreen`/`NavigationScreen` each push it to `CarDaylightApplier` → native stylesheet `daylight` flag. The shared "Dark mode" row (`PreferencesScreenMapper`, cycles ON/OFF/AUTOMATIC in `AutoSettings.darkMode`) is currently decorative — nothing reads it for rendering. Settings flow through `AutoSettingsProvider` (`toAutoSettings`/`toAppSettings`) and the session has `entryPoint` access.
- AAOS: no phone UI surface (`MainActivity` forwards to `CarAppActivity`); sensitivity stays **Off** there by intentional scope (host-driven is correct automotive behavior).

## Goals / Non-Goals

**Goals:**
- Four-state sensor sensitivity (Off/High/Medium/Low) on the phone, replacing the boolean option, with level-specific hysteresis thresholds and live re-application.
- Migration of existing persisted settings without data loss (old `true` → High, `false`/missing → Off).
- AA "Dark mode" preference actually drives car rendering (On/Off/Automatic × host), reacting live to preference changes and host changes.

**Non-Goals:**
- No sensor-driven dark mode on Android Auto (host-driven by design — headlight logic, no AA sensitivity row).
- No change to the host day/night signal (`carContext.isDarkMode()` remains the Automatic-mode environment on AA).
- No debounce scaling per level (thresholds are the sensitivity lever; 10 s debounce is unchanged).

## Decisions

### 1. Sensitivity enum with thresholds on the enum

`@Serializable enum class AmbientLightSensitivity(val darkLux: Float?, val lightLux: Float?)` in `AmbientLightClassifier.kt` (same package as the classifier/threshold constants):

```
OFF(   null,  null)   -- sensor disabled
HIGH(  10f,   50f)    -- current behavior (DARK_LUX / LIGHT_LUX)
MEDIUM( 5f,  100f)
LOW(    2f,  200f)
```

Wider band + lower entry per level = progressively more reluctant to flip either way; the shadowed dash-mount sensor case (phone position) is the real "triggers too soon" driver, fixed primarily by the lower dark-entry lux.

*Alternatives:* keep 3 booleans or an Int threshold setting — rejected (opaque, no hysteresis semantics); scale only the dark-entry threshold — rejected (asymmetric band would flip back-and-forth more, worse near dusk).

### 2. `classifyLux` becomes level-aware; pipeline constructed per level

`classifyLux(previousDark, lux, sensitivity: AmbientLightSensitivity)` uses `sensitivity.darkLux/lightLux` (null → sensor off, function unused). `AmbientLightPipeline` takes the level; `AmbientLightMonitor` exposes `setSensitivity(level)` that swaps in a fresh `AmbientLightPipeline` (hysteresis + debounce reset is correct — thresholds changed). `MainActivity`'s existing gating `combine` (preference, sensitivity) now emits `(active, level)`:
- `sensitivity == OFF || preference != AUTOMATIC` → `stop()`
- else → `start()` and `monitor.setSensitivity(level)`

*Trade-off:* re-pipelining on a level change emits an immediate classification (fresh `lastDark = null`), so the presentation may flip right after changing the level. Acceptable — immediate feedback at the moment the user changed the setting.

### 3. Settings migration at load time, one-time normalization

`AppSettings.ambientLightDarkMode: Boolean` → `ambientLightSensitivity: AmbientLightSensitivity = OFF`. `SettingsStorage.load()` changes: read raw JSON into a `JsonObject`; if the old `ambientLightDarkMode` boolean key is present, map `true → "HIGH"`, `false → "OFF"`, remove the old key, and decode the normalized text. `ignoreUnknownKeys` remains so any residual old key is dropped on the next save.

*Alternatives:* keep both fields (leftover nullable field forever — rejected); custom deserializer accepting bool-or-string (fragile kotlinx mechanics — rejected). Load-time normalization is one-time, explicit, and testable.

### 4. AA: session owns the resolved-dark flow

`NavigationSession` adds a preference holder and exposes `resolvedDark: StateFlow<Boolean>`:

```
_hostDark (existing, host signal)
_darkModePref: MutableStateFlow<String> = "AUTOMATIC"
resolvedDark = combine(_hostDark, _darkModePref) { host, pref ->
    when (pref) { "ON" -> true; "OFF" -> false; else -> host }
}
```

Seeded at session start by `entryPoint.autoSettingsProvider().load().darkMode` (non-blocking); **OFF/AUTOMATIC default keeps today's behavior if that load fails**. `PreferencesScreen` gets a narrow `onDarkModeChanged: (String) -> Unit` callback invoked after each dark-mode save; the session updates `_darkModePref`. `MapScreen`/`NavigationScreen` switch from `hostDark: StateFlow<Boolean>` to `resolvedDark` constructor param; their existing `CarDaylightApplier` dedup makes redundant pushes (forced On/Off while host changes) free.

*Alternatives:* resolve preference inside each screen (divergence risk, duplicated logic — rejected); session poll/observe the settings file (race-prone, no push — rejected). Callback keeps PreferencesScreen decoupled from rendering.

Note: sensitivity is intentionally absent from `AutoSettings` mapping and the AA preferences screen — no dead row (see proposal). `AutoSettingsMapping` is unchanged.

### 5. Phone UI/VM plumbing

- `MapCanvasViewModel`: uiState `ambientLightDarkMode: Boolean` → `ambientLightSensitivity`; `onSetAmbientLightOption(Boolean)` → `onSetAmbientLightSensitivity(level)`; restore from settings.
- `LocationOptionsSheetContent`: the `Switch` becomes a `selectableGroup` of `OrientationOption` rows (Off/High/Medium/Low) reusing the existing dark-mode pattern; label/`onSetAmbientLightSensitivity`. `testTag` moves to the group/row.
- `DarkModeController`: `_sensorEnabled` → `_sensorSensitivity`; `setSensorOption(enabled)` → `setSensorSensitivity(level)` persisting the enum name; `restoreSensorOption` → `restoreSensorSensitivity`; `resolveDarkPresentation` gate becomes `sensitivity != OFF`.

## Risks / Trade-offs

- [Old JSON files with the boolean key] → load-time normalization in `SettingsStorage.load()`; covered by migration tests (`true→HIGH`, `false→OFF`, missing→`OFF`, round-trip).
- [Presentation flip right after a sensitivity change (pipeline reset)] → accepted; immediate feedback is expected at the moment of change; not a flap (single flip).
- [AA: preference load fails at session start] → default AUTOMATIC = follow host = today's behavior; no regression.
- [AA: On/Off forced presentation disagrees with the rest of the car UI] → driver explicitly chose it (same contract as the phone); Automatic remains the sensible default and is unchanged.
- [Regression: sensors on AAOS] → none: sensitivity stays OFF on AAOS (no phone surface), monitor gating unchanged elsewhere.

## Migration Plan

1. Ship code that decodes both JSON shapes (old boolean, new enum); normalize on first `load()`; subsequent saves write the new field only.
2. Rollback: revert to boolean decode; enum JSON files have no old key → default `false` (sensor off) — a strict superset of the pre-change default, no crash. Users who chose a level would need to re-pick; acceptable for a toggled-option feature.
3. AA dark-row fix is additive (new consumed state); rollback = screens reverted to `hostDark`, preference row returns to decorative (pre-change state).

## Open Questions

None blocking. (Medium/Low debounce kept constant at 10 s per decision 1; if field reports linger-flapping at a level later, scaling debounce is an isolated follow-up.)
