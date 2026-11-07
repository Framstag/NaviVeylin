# Evidence — fix-late-dark-mode-settings-restore

Item: issue #312 (`the main build is red`). Base commit of the run: `5c94487b`.

## 1. The defect and its red case

`wirePersistedSettings()` restored the dark-mode preference and the ambient-light sensitivity
unconditionally after its suspension point (`MapCanvasViewModelSettings.kt:370-371` before the change); the
neighbouring `preferSymbolPoiIcons` restore was already guarded by `preferSymbolSetByUser`.

Red on the base (`red-on-head.xml`, `tests="2" skipped="0" failures="1" errors="0"`,
`timestamp="2026-10-26T06:47:28.403Z"`, re-measured after the archive so the cited XML is the one beside this
file):

```
$ ./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.DarkModeLateRestoreTest" \
    -PforceTests -PjvmTestsOnly
java.lang.AssertionError: the persisted Automatic must not replace the preference the user just set
    expected:<ON> but was:<AUTOMATIC>
tests="2" skipped="0" failures="1" errors="0"
```

## 2. The tracker's symptom, and why the green run of the same code was timing

The CI run that filed the item (`gh run view 40219449471 --log-failed`, sha `d846c741`):

```
MapCanvasViewModelDarkModeTest > sensorWinsWhenSensitivityActive FAILED
    kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
```

No file of the dark-mode path changed between `d846c741` (red) and `6e8b2889` (green):
`git diff --name-only d846c741 6e8b2889 -- app/src/main/java/com/naviveylin/data/DarkModeController.kt
app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt
app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelDarkModeTest.kt` printed nothing, so the class was
green by timing rather than by a fix. The class' start-up read ran on the production `Dispatchers.IO`
(`SettingsStorage.ioDispatcher`, un-wired there — the precedent that wires it is `SymbolIconPreferenceTest:67`,
and the rule is `guidelines/Build.md` §4e), it built two storages for one file, and it seeded no settings file, so
the restore landed at an arbitrary point and carried values persisted by earlier cases in the same JVM.

**The timeout itself is not proven to have that cause, and is not claimed to.**
`attribution-probe.log` measures both combinations: with HEAD's production code *and* HEAD's test class the class
is green 3/3 here (`probe-head-code-head-testsetUp-class.xml`) and the new red class is red 3/3
(`red-on-head-run3.xml`); with the fixed code and HEAD's test class it is green
(`probe-fixed-code-head-testsetUp-class.xml`). The timeout's trigger is load, which a sandbox does not reproduce;
what the change removes is the real-thread window the class' start-up read used.

## 3. Green after the change

`green-cases.log` (taken 2026-10-26T06:24:30Z):

| command | tally |
|---|---|
| `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.DarkModeLateRestoreTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelDarkModeTest" -PforceTests -PjvmTestsOnly` | `tests="2" failures="0"`, `tests="9" failures="0"` |
| `./gradlew --offline :app:testAutomotiveDebugUnitTest --tests "com.naviveylin.ui.map.*DarkModeTest" -PforceTests -PjvmTestsOnly` | `tests="9" failures="0"` |

The mobile class also ran green three times in a row before that run (`tests="9" skipped="0" failures="0"
errors="0"` each), which is the rate the flake rule asks for (`guidelines/Build.md` §4e).

The XMLs beside this file: `TEST-com.naviveylin.ui.map.DarkModeLateRestoreTest.xml`,
`TEST-com.naviveylin.ui.map.MapCanvasViewModelDarkModeTest.xml`,
`automotive-TEST-com.naviveylin.ui.map.MapCanvasViewModelDarkModeTest.xml`.

## 4. Revert-check (the falsification the new invariant owes)

`revert-check.log`: the four guards mutated to `if (true)` / `if (false)` →
`aChangeThatLandsBeforeTheReadKeepsBothFields` fails (`tests="2" failures="1"`); restored, forced run →
`tests="2" failures="0"` with `timestamp="2026-10-26T06:42:03.781Z"`. XMLs: `red-mutated.xml`, `green-restored.xml`.

## 5. The gate, once

`gate.log` — `./tools/loop/gate.sh`, run once on this change:

```
LOOP-GATE: tests=595 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 275s,
tools/loop-selftest.sh --from-git 6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 371s, … )
```

`gate-summary.xml` is that line's aggregate. **What the 595 counts:** the gate adds each suite's own summary line
(`<name>: all N cases passed`), and the compiled step prints no such line — it prints `BUILD SUCCESSFUL`, so it
contributes `0` and the 595 is the sum over the shell suites only (`tools/loop/gate.sh:219-236`). The Kotlin cases
are the compiled step's own tallies, which live beside this file as the per-class XMLs in
`app/build/test-results/` and in `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`
(`BUILD SUCCESSFUL in 4m 35s`): 2111 (mobile) + 2111 (automotive) + 646 (core) + 880 (auto) case tallies, no
failure and no error, the case that filed the item included. Neither number contradicts the other; they count
different things, and neither is a hand sum.

## 7. Independent review (text-only, environment limit named)

Two attempts to run a fresh read-only child with tools failed in this environment — every tool call of the child
session was refused (`This '<tool>' call for agent '…' requires approval, but no interactive UI is available`), so
neither attempt gathered evidence. A third child judged the diff, the delta, the case bodies and the tallies from
their text (no tools): `VERDICT: pass`, no CRITICAL or MAJOR finding, five MINOR ones — attribution not isolated
(measured above, and stated in the proposal), the gate's 595 without a stated unit (§5 now states it), the flags
never resetting (one call site, recorded in `design.md`), a cross-session restore the tree does not have (one
writer, one restorer, recorded in `design.md`), and the case name's "before the read" (read as "before it
completes", recorded in `design.md`; the name is kept so the red XML names the case that ran).

## 8. What changed

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `darkModePreferenceSetByUser`,
  `ambientSensitivitySetByUser` (documented like `preferSymbolSetByUser`).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt` — the two setters set their flag;
  `wirePersistedSettings()` restores the controller and `uiState` field only while the flag is unset.
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelDarkModeTest.kt` — `setUp` only: one
  `SettingsStorage` on the test's scheduler, a seeded `maps/settings.json` per case.
- `app/src/test/java/com/naviveylin/ui/map/DarkModeLateRestoreTest.kt` — the two cases of the added requirement.
- `openspec/specs/dark-mode/spec.md` — the added requirement, merged by the archive.
