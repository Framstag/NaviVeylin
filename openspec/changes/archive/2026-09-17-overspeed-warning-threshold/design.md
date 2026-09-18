## Context

Two independent speed-badge render paths each hardcode a different over-limit rule (see proposal.md — Why). The `speed-limit-warning-colors` change already unified the warning *colors* (red background, white text, 0.92/0xCC alpha) and explicitly deferred the threshold drift. This design unifies the *rule* behind one configurable global value, reusing the existing shared-settings pipeline: phone JSON `AppSettings` ↔ `:core` `AutoSettings` via `AutoSettingsMapping`, served to the AA process by `AutoSettingsProvider` (`SettingsStorage` backed). Persistence pattern everywhere is `load → copy → save` (`SettingsStorage`, JSON, `ignoreUnknownKeys = true`).

Key constraint from the specs: the rule is `current >= max + delta` (inclusive), delta a whole km/h in 0..30, default 5, identical on phone and AA.

## Goals / Non-Goals

**Goals:**
- One shared, persisted delta (0–30, default 5) driving both surfaces' warning state.
- One predicate implementation per surface with identical semantics; AA's rounded `>`-drift removed.
- Adequate controls for both settings UIs: Compose slider (phone), a car-appropriate picker (AA — ListTemplate rows cannot host a slider).
- Backward compatibility: old installs load with delta 5; old JSON untouched (`ignoreUnknownKeys`).

**Non-Goals:**
- No native/submodule changes (`SpeedAgent` only reports max speed; warning stays UI-side).
- No audible/haptic overspeed alert, no sign-geometry changes, no warning-state color changes (handled by `speed-limit-warning-colors`).
- No live re-read of the delta mid-drive on car screens (settings are edited while parked; existing screens load settings at init — same as `autoZoomEnabled` today).

## Decisions

### 1. Unified predicate: `current >= max + delta`, raw doubles

**Chosen**: both surfaces compare raw doubles, `warn ⇔ !max.isNaN() && max > 0 && current >= max + delta`. Phone: parameterize the existing pure `isSpeedOverLimit(current, max, delta)`. AA: `drawSpeedBadge` gains the delta and drops its rounded `>`-comparison.

Rationale: one rule everywhere (spec requirement), and `>=` is what the user specified — at exactly `max + delta` the badge must warn. 0 delta means "warn at the limit". Raw doubles make the phone and AA predicates literally identical; the AA rounding was a float-noise guard for its previous +0 rule, and with delta ≥ 0 the noise window only exists at the exact boundary (whole km/h values are exact in IEEE-754 for the common sums, e.g. `50.0 + 5.0 == 55.0`), so flicker risk is negligible.

**Alternative — keep AA's rounded comparison**: `round(current) >= round(max) + delta`. Rejected: rounding twice per surface re-introduces the drift the change exists to kill, and for delta > 0 integer arithmetic on `round(max) + delta` diverges from the phone's raw rule at the boundary.

### 2. Setting plumbing: one `Int` field through the existing layer cake

`AppSettings.overspeedWarningDeltaKmh: Int = 5` (serializable, no validation needed at load — pickers clamp), mirrored as `AutoSettings.overspeedWarningDeltaKmh: Int = 5` in `:core`, mapped both directions in `AutoSettingsMapping` (same mechanical pattern as `laneHintsEnabled`). Naming: delta is the spec's language; `Kmh` suffix matches the domain and avoids ambiguity with a speed-limit value.

**Alternative — compute threshold as an absolute speed (`max + delta`)**: rejected; the delta (how much over) is what the user wants to configure and is what the UI shows.

### 3. Phone control: slider, 0..30, 1 km/h steps

`LocationOptionsOverlay` driving-row: `Slider(0f..30f, steps = 29)`, value rounded to `Int` on change, live label `+N km/h` (or "warn at limit" when 0? — label stays `+N km/h`; 0 reads `+0 km/h`). Slider over 31 discrete values with `steps` is the adequate control: continuous feel, 1 km/h precision guaranteed (no step-5 trap), immediate feedback, no keyboard. Persists via `MapCanvasViewModel.onSetOverspeedDelta` (`load → copy → save`), identical to `onToggleLaneHints`.

**Alternatives**: exposed dropdown (31 rows, slow), stepper `+`/`−` buttons (fine for 0–30 but no overview), numeric text field (input validation overkill). Slider wins on precision + speed + one-handed map-sheet use.

### 4. AA control: dedicated value-picker screen

`PreferencesScreen` gains an "Overspeed warning" row (title + current value text, existing `PreferenceRow` shape) whose click pushes a new `OverspeedDeltaPickerScreen` — a `ListTemplate` with one row per whole km/h 0..30, the current value marked, tap selects → `AutoSettingsProvider.save(copy(overspeedWarningDeltaKmh = value))` → `finish()`. Pattern mirrors the existing `PreferencesScreen` (Hilt entry-point injection, secondary constructor for tests).

Rationale: ListTemplate rows cannot host a slider (car-app constraint); tap-to-cycle on the main screen would need up to 31 taps to wrap — unusable for a 31-value range. A scrollable 31-row picker is the standard car pattern and keeps row rendering on the template host.

**Alternative — cycle-on-tap**: rejected for the 31-tap worst case. **Alternative — `GridTemplate` picker**: viable (compact), but 31 rows in a list matches the existing preferences UX and avoids a second layout mode; noted if QA wants a denser view.

### 5. AA application thread: delta into `SurfaceIndicators.draw`

`SurfaceIndicators.draw(...)` and `drawSpeedBadge(...)` gain `overLimitDeltaKmh: Int = 5`; `NavigationScreen` and `FreeDrivingScreen` already load shared settings at init (mirror of `autoZoomEnabled` today) and pass it through. No new observer loop: the delta changes only from the preferences screen (parked), so next-screen-entry application is acceptable — consistent with existing AA settings behavior.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Archive-order collision with `speed-limit-warning-colors` on `map-speed-widget`'s "Overspeed warning color" requirement (both MODIFY it) | Both deltas keep every base scenario name; color change is already 9/11 applied and archives first, this change second — its delta then replaces the requirement with the threshold rule + identical color wording. Apply/archive in that order. |
| `>=` boundary float noise (rare exact-match flicker at `max + delta`) | Whole-km/h arithmetic is exact in IEEE-754 for the common range; if device QA ever sees flicker, switch both sides to rounded-int comparison `round(current) >= round(max) + delta` — spec-neutral (boundary scenarios use whole km/h). |
| Phone default changes AA behavior (stops warning at +0, warns from +5) | Intentional per user: "no difference anymore"; default 5 documented in specs. |
| 31-row picker scroll cost on head units | ListTemplate virtualization is host-rendered; short list, negligible. |

## Migration Plan

No data migration: settings JSON gains the new key with `ignoreUnknownKeys` → old installs read delta 5 automatically; writes from either surface persist the new key. Rollback = revert the Kotlin changes and archive-revert the spec deltas; existing APKs unaffected (additive setting + default).

Verification:
- Unit: `SpeedWidgetTest` — delta-boundary cases (`>=` at exactly `max + delta`; `delta = 0` warns at the limit; unknown limit never warns; configured-delta cases), pure `isSpeedOverLimit` parametrized.
- Unit: `AutoSettingsMapping` round-trip includes the new field; AA `PreferencesScreenMapper` row + picker-selection tests.
- On-device: phone sheet slider (0..30, label updates, persists across restart); AA emulator — picker opens, selection persists, nav + free-driving badge warn at the configured delta; default-5 install (no settings file) warns at `max + 5`.

## Open Questions

None blocking.
