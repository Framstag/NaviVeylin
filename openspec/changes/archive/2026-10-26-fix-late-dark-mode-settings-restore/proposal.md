# Proposal — fix-late-dark-mode-settings-restore

## Why

One defect, verified on the run's base `5c94487b`, issue #312: **the start-up read of the persisted settings
restores the dark-mode preference and the ambient-light sensitivity unconditionally, so a change the user made
while that read was still in flight is silently replaced by the older persisted value.**

**Root cause (one).** `wirePersistedSettings()` restores both fields as soon as `settingsStorage.load()` returns
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt:370-371` and the same two fields in the
`uiState` copy at `:387-388`). The read is a suspension point, and the very next statement of that function
guards the *third* restored field against exactly this — `preferSymbolPoiIcons` is taken from `uiState` when
`preferSymbolSetByUser` is set (`:372-378`, `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:424`).
The two dark-mode fields have no such flag, so a `onSetDarkModePreference` / `onSetAmbientLightSensitivity` call
that lands inside the read's window is undone when the read resumes.

**How the tracker's symptom follows from it, and where that stops being a claim.** `MapCanvasViewModelDarkModeTest`
built its two `SettingsStorage` instances on the production `Dispatchers.IO` (`SettingsStorage.ioDispatcher`,
un-wired in that class) and seeded no settings file, so the start-up read landed at an arbitrary point relative
to the case's own writes and carried whatever values earlier cases (the file is shared inside the JVM) had
persisted. A case whose expected presentation depended on the restored value then waits out `runTest`'s 60 s
dispatch timeout. CI main failed twice on one case with exactly that signature
(`gh run view 40219449471 --log-failed`):

```
MapCanvasViewModelDarkModeTest > sensorWinsWhenSensitivityActive FAILED
    kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
```

The same two commits' later run (`6e8b2889`, green) changed no file of the dark-mode path —
`git diff --name-only d846c741 6e8b2889 -- app/src/main/java/com/naviveylin/data/DarkModeController.kt
app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt
app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelDarkModeTest.kt`
printed nothing — so the green run was timing, not a fix. **What is not claimed:** that the *unconditional
restore* is what produced the timeout. The timeout does not reproduce in this sandbox even with the un-fixed code
and the un-wired class (`evidence/attribution-probe.log`, 3 runs, `tests="9" failures="0"` each); its trigger is
load. What this change does about the symptom is remove the real-thread window that class' start-up read used —
the `SettingsStorage.ioDispatcher` seam plus a seeded settings file — and the guard's own defect is proven
separately by the red case.

**Red test — the smallest case that exercises the defect, failing on `5c94487b`.** `DarkModeLateRestoreTest`
holds the start-up read on a scheduler of its own, changes both fields while it is open, and only then lets the
read complete (`evidence/red-on-head.xml`, taken 2026-10-26T06:47:28.403Z):

```
$ ./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.DarkModeLateRestoreTest" \
    -PforceTests -PjvmTestsOnly
tests="2" skipped="0" failures="1" errors="0"
java.lang.AssertionError: the persisted Automatic must not replace the preference the user just set
    expected:<ON> but was:<AUTOMATIC>
```

Both cases are green after the change (`evidence/green-cases.log`).

**Owning requirement.** `dark-mode` — "Manual dark mode control" ("Changing the preference SHALL apply
immediately") and "Ambient light sensor option" ("SHALL apply immediately when changed"); the delta adds the
requirement that states the precedence, whose scenarios are the two new cases. Guideline section:
`guidelines/UI.md` §9 "Dark mode (phone + Android Auto)"; the test-side rule the old class violated is
`guidelines/Build.md` §4e — an un-wired dispatcher seam leaves a real thread that no `advanceUntilIdle()`
reaches — with `SymbolIconPreferenceTest` (`:67`) as the precedent that wires `SettingsStorage.ioDispatcher`.

surface: 1 capability's delta, 2 production files (`MapCanvasViewModel.kt` two flags,
`MapCanvasViewModelSettings.kt` the guarded restore), 1 test file rewritten (`MapCanvasViewModelDarkModeTest`
setUp) and 1 test file added (2 cases) — from
`git grep -n "restorePreference\|restoreSensorSensitivity\|darkModePreferenceSetByUser\|ambientSensitivitySetByUser"`
(4 sites in production, 0 elsewhere) and `git grep -l "SettingsStorage(context)" -- app/src/test` (the other
classes construct the storage but none asserts a restored dark-mode field; `SymbolIconPreferenceTest` already
wires the seam).

## What changes

- `MapCanvasViewModelSettings.kt` — the restore of `darkMode` and `ambientLightSensitivity` (controller and
  `uiState`) is guarded by the same "the user changed it in this session" flag the neighbouring
  `preferSymbolPoiIcons` restore uses.
- `MapCanvasViewModel.kt` — the two flags (`darkModePreferenceSetByUser`, `ambientSensitivitySetByUser`), set by
  the two setters.
- `MapCanvasViewModelDarkModeTest.kt` — one `SettingsStorage` instance (production wires a singleton, the test
  built two, each with its own mutex on the same file), its file IO on the test's scheduler, and a seeded settings
  file per case, so the start-up read is drained before the case body and no predecessor's value decides the
  case.
- `DarkModeLateRestoreTest.kt` — the two cases of the new requirement.
- `openspec/specs/dark-mode/spec.md` — the added requirement (delta only, merged at archive).

## What does not change

The resolution rule itself (`resolveDarkPresentation`), the sensor pipeline, the stylesheet flag push, and the
behaviour of `preferSymbolPoiIcons` — the guard is the existing one, applied to two more fields.
