# Design — fix-late-dark-mode-settings-restore

## Root cause

`wirePersistedSettings()` restores the dark-mode preference and the ambient-light sensitivity unconditionally
once `settingsStorage.load()` returns (`MapCanvasViewModelSettings.kt:370-371`), while the neighbouring
`preferSymbolPoiIcons` restore is guarded by `preferSymbolSetByUser` (`:372-378`). The load is a suspension
point, so a change the user made inside its window is replaced by the value the read carried.

The tracker's symptom — one Robolectric case of the dark-mode ViewModel class failing CI twice with
`UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion` — is the same window
seen from the test: that class left `SettingsStorage.ioDispatcher` on the production `Dispatchers.IO`, built two
storages for one file, and seeded no settings file, so the restore landed at an arbitrary point and carried
values persisted by earlier cases in the same JVM.

## The single fix

Apply the guard the neighbouring field already has:

- `MapCanvasViewModel.darkModePreferenceSetByUser` / `ambientSensitivitySetByUser`, set by
  `onSetDarkModePreference` / `onSetAmbientLightSensitivity` (the two setters are the only user entry points).
- `wirePersistedSettings()` restores the controller's field and the `uiState` field only when the corresponding
  flag is unset; otherwise it keeps the value in `uiState`, which is the value the setter published.

And give the affected test class the seam its siblings already use:

- one `SettingsStorage` for the class (production wires it as a `@Singleton`, so two instances meant two mutexes
  on one file),
- `settingsStorage.ioDispatcher = mainDispatcherRule.dispatcher`, which makes the start-up read drain inside the
  class' `advanceUntilIdle()` instead of racing the real pool,
- a seeded `maps/settings.json` per case, so the defaults — not a predecessor's write — are what a read would
  restore.

## Why the alternative was eliminated, not chosen

- **Skip the restore when a value differs** — eliminated by the evidence: the read's value and the user's value
  are both legitimate, and "differs" cannot tell which is newer; only the flag records that the user spoke.
- **Give `DarkModeController` its own dispatcher seam** — not needed once the restore is guarded and the read is
  drained before the case body: the controller's `combine` result is a `StateFlow` value, so its final state is
  order-independent, and every case's expectation now follows from the value the case itself sets or from the
  seeded default. Adding a seam there would be a second, unmeasured change.
- **Only fix the test (wire the seam, seed the file)** — would leave the product race in place and has no
  falsifiable red case: the guard is what makes the defect go away, and `DarkModeLateRestoreTest` fails without
  it (`evidence/red-on-head.xml`).

## Blast radius

- `wirePersistedSettings()` — two `if`s and two `val`s; the restore of every other field is untouched, and the
  guard only fires when the user changed the field in this session (nothing sets the flags at construction).
- The two setters — one assignment each.
- `MapCanvasViewModelDarkModeTest` — its `setUp` only; no case's assertion changed.
- Other test classes construct `SettingsStorage` themselves and assert other fields; `SymbolIconPreferenceTest`
  already wires the same seam, and none of them asserts a restored dark-mode field
  (`git grep -l "SettingsStorage(context)" -- app/src/test`).

## What this change does not restructure, and why that is safe

- **The read runs once.** `wirePersistedSettings()` is called from one place, `MapCanvasViewModel.kt:528` in
  `init` (`git grep -n "wirePersistedSettings()" -- app/src/main` names exactly that call and the definition), so
  a flag that never resets is read by exactly one restore. A re-run of that read would need the flags cleared
  before it starts.
- **One writer, one restorer.** The preference and the sensitivity are set only by `onSetDarkModePreference` /
  `onSetAmbientLightSensitivity` and restored only by `wirePersistedSettings()`, all in `MapCanvasViewModel`
  (`git grep -n "setPreference\|setSensorSensitivity\|restore" -- app/src/main auto/src/main`). The car path
  reads the settings through `AutoServiceModule.kt:277`'s `toAutoSettings()` and never touches these controller
  fields, so a second surface's restore cannot race the phone's change today. Moving the guard into
  `DarkModeController` would be the change that keeps that true if a second restorer appears; it is not needed
  for this delta, and adding it would be an unmeasured change.

## Rollback

Revert the four hunks (two flags, two guards, two setter assignments) and the test-side wiring; the previous
behaviour — and the flake — return together, which is what `revert-check` records.

## Verification

- Red: `DarkModeLateRestoreTest` on the base (`tests="2" failures="1"`, `evidence/red-on-head.xml`).
- Green: the same class, `MapCanvasViewModelDarkModeTest` (3 consecutive mobile runs) and the automotive flavour
  of the dark-mode class (`evidence/green-cases.log`).
- The gate once: `./tools/loop/gate.sh`, whose XMLs are copied into `evidence/`.

## Measured, and one thing this change does not prove

`evidence/attribution-probe.log` records both combinations: HEAD code + HEAD test class is green 3/3 for
`MapCanvasViewModelDarkModeTest` (the CI timeout does not reproduce in this sandbox — its trigger is load) and red
2/1 for `DarkModeLateRestoreTest`; fixed code + HEAD test class is green 9/9. So the guard's defect is proven by
the red case and the revert-check, while the timeout's cause is only *consistent* with the window the test class
left open — the class fix removes that window, and the report says so rather than claiming a causal chain.

The case name `aChangeThatLandsBeforeTheReadKeepsBothFields` reads "before the read" as "before the read
completes", which is the ordering the body produces (the change lands after the read started and before it
returns). It is left as measured so the red XML beside the change names the case it ran.
