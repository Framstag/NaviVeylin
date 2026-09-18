## Why

The overspeed warning threshold is hardcoded and differs per surface: the phone warns when the current speed exceeds the limit by **5 km/h or more** (`SpeedWidget` `isSpeedOverLimit`), while Android Auto warns as soon as the displayed (rounded) speed **exceeds the limit** (`SurfaceIndicators` `drawSpeedBadge`). The `speed-limit-warning-colors` change deliberately deferred closing this drift. Drivers have no way to tune how much over the limit triggers the warning.

This change makes the threshold a single shared, user-configurable setting so both surfaces warn under the **same rule** with **the same value**.

## What Changes

- New shared persisted setting: **overspeed warning threshold** ("delta"), an integer in **0–30 km/h**, default **5**, precise to **1 km/h** (no coarser steps).
  - Warning rule becomes **`current speed >= max speed + delta`** (inclusive — at exactly `max + delta` the badge warns; 0 = warn the instant the limit is reached).
  - Applies identically to the phone speed widget and the Android Auto speed badge (navigation AND free driving), closing the phone/AA drift.
  - Unknown limit (NaN / ≤ 0) never warns, unchanged.
- Phone settings sheet (`LocationOptionsOverlay`) gains a control for the delta: a 0–30 slider snapped to whole km/h with the current value shown.
- Android Auto preferences (`PreferencesScreen`) gains an "Overspeed warning" row; selecting it opens a dedicated value picker screen (0–30) under car-app constraints (ListTemplate rows cannot host a slider; tap-to-cycle would need up to 31 taps).
- Settings plumbing: `AppSettings` (phone JSON) and `AutoSettings` (`:core`, shared with the AA process) both carry the same property; `AutoSettingsMapping` maps it both directions. The value is global — editing on either surface changes both.
- Persist via the existing `load → copy → save` pattern; JSON `ignoreUnknownKeys` keeps old installs safe (new field defaults to 5).

## Capabilities

### New Capabilities

- None — behavior lives in the existing speed-widget and settings-surface capabilities.

### Modified Capabilities

- `map-speed-widget`: the overspeed warning requirement changes from a fixed "exceeds max by 5 km/h or more" rule to the configurable threshold rule (`current >= max + delta`, delta 0–30, default 5, one shared global value). Boundary scenario flips: at exactly `max + delta` the badge now warns; delta 0 warns at the limit.
- `auto-map-layout`: the navigation speed-limit warning changes from "current speed exceeds the limit" to the same shared configurable rule; the Android Auto settings dialog gains the overspeed-warning setting (row + value picker) mirroring the phone sheet.
- `location-options-ui`: the phone options bottom sheet gains the overspeed-warning threshold control (slider with 1 km/h precision).

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — `AppSettings` + field
  - `app/src/main/java/com/naviveylin/data/AutoSettingsMapping.kt` — both mappers
  - `core/src/main/java/com/naviveylin/core/AutoSettings.kt` — field for the AA process
  - `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` — `isSpeedOverLimit` takes the delta; hardcoded 5 removed
  - `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` + `ui/map/MapCanvasViewModel.kt` — slider row + persist
  - `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` — `draw`/`drawSpeedBadge` take the delta; unified `>=` predicate
  - `auto/.../FreeDrivingScreen.kt`, `auto/.../NavigationScreen.kt` — read the delta from shared settings
  - `auto/.../PreferencesScreen.kt`, `PreferencesScreenMapper.kt` — new row + new value-picker screen
- **Tests**: `SpeedWidgetTest` (threshold cases, incl. `>=` boundary), AA `PreferencesScreenMapper`/picker tests, mapping tests.
- **Spec deltas**: `map-speed-widget`, `auto-map-layout`, `location-options-ui` (files under `openspec/specs/`).
- **Behavior note**: with the default 5, AA stops warning at +0 and warns from limit+5 — an intentional alignment to the phone's default. Phone boundary shifts by 1 km/h (`>` becomes `>=`). No data migration; old installs get the default silently.
