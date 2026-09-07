# Design — Phone ambient light dark mode

## Context

See proposal.md — Why. Android has no OS-level ambient-light→dark-mode API: system night mode is time/user/battery driven. The app already has the extension point: `DarkModeController.setEnvironmentDark(Boolean)` fed from `isSystemInDarkTheme()` in `MainActivity`, resolving through the 3-state preference into `NaviVeylinTheme` + `pushDarkPresentation` (`setStyleSheetFlag("daylight", !dark)`). This change adds the light sensor as an opt-in environment source behind that same resolution path.

## Goals / Non-Goals

**Goals**
- Opt-in ambient-light-based dark mode on phone, defaulting to the current follow-system behavior.
- Hysteresis + debounce so the presentation does not flap.
- Battery-conscious: sensor only active when preference = **Automatic** + option enabled + app foreground.

**Non-Goals**
- No change to the AA path (host-driven, separate change `aa-dark-mode-follow-host`).
- No change to the 3-state preference semantics (On/Off still win over the environment).
- No OS-level mechanism exists to adopt — the sensor is app-side by necessity.

## Decisions

### D1: Sensor source as nullable signal in the controller

`DarkModeController` gains `_sensorDark: MutableStateFlow<Boolean?>` (null = sensor inactive/unavailable). Resolution:

```
environmentDark = when {
    sensorEnabled && sensorDark != null -> sensorDark
    else -> systemDark
}
```

`setEnvironmentDark` stays as the system source; new `setSensorDark(Boolean?)` feeds the sensor. Pure function `resolveDarkPresentation(preference, systemDark, sensorDark, sensorEnabled)` — unit-testable without Android.

- **Alternative A (sensor overrides system unconditionally)**: ignores the option gate, changes default behavior. Rejected — option must gate.
- **Alternative B (new preference state, e.g. AUTOMATIC_LIGHT)**: expands the 3-state contract, breaks existing persisted settings semantics. Rejected — option is orthogonal to the preference.

### D2: Hysteresis + debounce in a pure classifier

New pure function `classifyLux(previousDark: Boolean, lux: Float): Boolean`:

- dark entry: `lux < 10` (DARK_LUX)
- light exit: `lux > 50` (LIGHT_LUX)
- between thresholds: keep previous state

Debounce: 10 s (DEBOUNCE_MS) — a sustained classification must hold for the debounce window before the signal flips. Hysteresis alone prevents threshold flapping; debounce filters sensor noise and transient shadows.

- **Alternative**: 30 s debounce. Rejected — tunnel entry should switch within ~10 s; hysteresis already prevents dusk flapping.
- **Alternative**: asymmetric debounce (fast dark, slow light). Rejected for v1 — single constant, tunable later without spec change (thresholds are constants, not contract).

### D3: Monitor lifecycle owned by MainActivity, gated by controller state

New `AmbientLightMonitor` (SensorManager, `TYPE_LIGHT`, `SENSOR_DELAY_NORMAL`): `start()`/`stop()` register/unregister the listener. MainActivity starts it when `preference == AUTOMATIC && option enabled` (observed via controller flows) and stops it otherwise; `onStop` of the activity stops it unconditionally (foreground-only). No permission needed (`TYPE_LIGHT`). `getDefaultSensor(TYPE_LIGHT) == null` → monitor reports inactive (null signal → system fallback).

- **Alternative (always-on listener)**: battery drain, background reading. Rejected.
- **Alternative (register in Application)**: no foreground gating. Rejected.

### D4: Config option in AppSettings

New `ambientLightDarkMode: Boolean = false` in `AppSettings` (JSON, `ignoreUnknownKeys` — old files stay valid). Toggle in `LocationOptionsOverlay` dark-mode section (below On/Off/Automatic), wired through `MapCanvasViewModel` → `DarkModeController` + `SettingsStorage` (same pattern as `onSetDarkModePreference`).

## Risks / Trade-offs

- [Sensor noise / transient shadows flip presentation] → Mitigation: hysteresis band (10–50 lux) + 10 s debounce.
- [No light sensor on device] → Mitigation: null signal → system fallback; option toggle still visible but inert (documented in UI).
- [Battery from continuous sensor] → Mitigation: gated listening (AUTO + option + foreground), `SENSOR_DELAY_NORMAL`.
- [Tunnel entry slower than ideal (10 s debounce)] → Mitigation: acceptable; hysteresis prevents the worse failure (flapping). Constants tunable.
- [Sensor classification disagrees with system night mode] → Mitigation: by design — option explicitly switches the environment source; user can disable.

## Migration Plan

Additive. Old settings files lack the field → default `false` (existing `ignoreUnknownKeys` + default pattern). Rollback = revert commit.

## Verification

- Unit: `classifyLux` hysteresis tests (entry/exit/band-hold), `resolveDarkPresentation` with sensor null/active, settings migration test.
- On-device: toggle option, cover sensor with hand/light, verify map + UI switch within debounce; disable → follow-system restored; device without sensor → no crash, system fallback.
- Logcat: `NaviVeylin` tag shows sensor classification transitions.

## Open Questions

None — thresholds (10/50 lux, 10 s) are constants, tunable without spec change.
