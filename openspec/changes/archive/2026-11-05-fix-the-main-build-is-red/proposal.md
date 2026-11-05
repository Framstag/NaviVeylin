# Proposal

## Why

`main` is red. The failing case is the compass requirement "Switching to north-up while driving stops further
rotation" — the build pipeline's run of 2026-11-05 reported `2234 tests completed, 1 failed`, and the one failure
was

```
MapCanvasViewModelFollowModeTest > togglingToNorthUpWhileDrivingStopsFurtherRotation FAILED
    java.lang.AssertionError: north-up must hold with no bearing too expected:<0.0> but was:<1.5707963267948966>
        at com.naviveylin.ui.map.MapCanvasViewModelFollowModeTest$togglingToNorthUpWhileDrivingStopsFurtherRotation$1.invokeSuspend(MapCanvasViewModelFollowModeTest.kt:445)
```

(`Build Debug APK` run 41967608675, commit `84b85a79`, 2026-11-05T04:56:36Z, log line 5814.) `1.5707963267948966`
is +90° — the heading-up angle the map had before the user chose north-up — so the map rotated *back* to a
rotation the requirement forbids.

**Root cause, one sentence:** the start-up read of the persisted settings — `MapCanvasViewModel.wirePersistedSettings`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt:434-494`) — writes `navNorthUp` and
`freeFormNorthUp` from disk unconditionally when it completes, so a read still in flight when the user chooses an
orientation restores the persisted follow-direction value over the user's newer choice, and the next fix
(`MapCanvasViewModelFollow.kt:1030-1072`) then takes the "follow direction, no usable course" branch and commits
`lastUsedAngle` — the +90° the user had just left.

The read is a suspension point on the storage's own dispatcher (`SettingsStorage.load()` is
`withContext(ioDispatcher)`, `app/src/main/java/com/naviveylin/data/SettingsStorage.kt:264`), so it lands after the
choice whenever the IO thread loses the race — which is what a loaded CI runner makes likely. The same class
already documents the clobber as a workaround in its own helper ("The init settings load overwrites the state with
persisted values when the test scheduler advances — flush it, then re-apply so the test state sticks",
`MapCanvasViewModelFollowModeTest.kt:257-269`): the defect was visible in the test's own text and worked around
rather than fixed.

The capability already forbids exactly this for the dark-mode fields — `dark-mode`'s "A user change outranks an
in-flight settings read", implemented with `darkModePreferenceSetByUser` / `ambientSensitivitySetByUser` /
`preferSymbolSetByUser` — and the two orientation fields are the ones the pattern was never applied to.

**Red case, on the fix's base** (`ec58cb91`), taken with
`./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests "com.naviveylin.ui.map.NorthUpLateRestoreTest"`:

```
NorthUpLateRestoreTest > northUpChosenWhileTheReadIsOpenSurvivesTheRead FAILED
    java.lang.AssertionError: the persisted follow-direction value must not replace the orientation the user just chose
2 tests completed, 1 failed
```

XML `tests="2" skipped="0" failures="1" errors="0" timestamp="2026-11-05T09:12:23.151Z"` — the case is red on
HEAD and green once the fix is applied (`tests="2" failures="0"`, `2026-11-05T09:13:00.192Z`), and the existing
class is green with it (`tests="24" failures="0"`, `2026-11-05T09:12:59.979Z`).

Owning requirement: `compass-settings` — "Per-mode orientation setting" ("North always up SHALL hold the map at 0°
on **every** map commit … no previously used rotation angle and no missing/noisy bearing signal SHALL rotate the
map while this mode is active", scenario "Switching to north-up while driving stops further rotation").
Owning guideline section: `guidelines/MapFollow.md` §9 "Map Rotation (Follow-Mode)" (the rotation the lost
orientation re-enables) and §6 "Angle Handling".

surface: 2 cases added (`NorthUpLateRestoreTest`, both in a new class), 1 case rewritten (`MapCanvasViewModelFollowModeTest.enableFollowDirectionMode`
no longer re-applies the choice after the drain), 1 fixture realigned (that class's `SettingsStorage` is given the
test scheduler as its IO seam), 2 production fields guarded — 2 flags added, 2 setters marked, 1 restore site
guarded. Counted with
`grep -rln "onSetNavOrientation\|onSetFreeFormOrientation\|navNorthUp\|freeFormNorthUp" app/src/test/java` (6 test
files name the orientation state; the two changed/added) and `grep -rn "navNorthUp\|freeFormNorthUp" app/src/main/java`
(2 setters, 1 restore site, the 2 state fields).

## What Changes

- `MapCanvasViewModel` gains `navNorthUpSetByUser` and `freeFormNorthUpSetByUser`, beside the three flags the
  dark-mode requirement already uses, with the same contract.
- `onSetNavOrientation` and `onSetFreeFormOrientation` set their flag: the user has chosen.
- `wirePersistedSettings` keeps the in-state orientation for a field the user chose and restores the persisted one
  for a field they did not.
- `NorthUpLateRestoreTest` (new) holds the start-up read on its own scheduler, so "the read completes after the
  choice" is an ordering the case decides instead of a race: one case for the choice that must survive the read,
  one for the field that must still be restored.
- `MapCanvasViewModelFollowModeTest` points the storage's IO seam at the test scheduler (the rule
  `guidelines/BuildTests.md` §6e states for a production `withContext` in a suspend accessor) and drops the
  flush-then-re-apply workaround its helper carried.

**Not changing:** the rotation pipeline, the north-up branch of the follow collector, and every other field the
start-up read restores. The other unguarded fields are a wider question than this item — a change that guarded all
of them would be a sweep no test in this item measures — and none of them can rotate the map.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `compass-settings`: adds one requirement — a user orientation choice outranks an in-flight settings read — with
  two scenarios. Every shipped requirement keeps its behaviour: "North always up" still holds the map at 0° on
  every commit; the difference is that the mode can no longer be reverted under the user while the start-up read
  is open.

## Impact

**Files**

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the two flags, with the doc the pattern carries.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelFollow.kt` — the two setters mark the choice.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt` — the restore keeps a chosen orientation.
- `app/src/test/java/com/naviveylin/ui/map/NorthUpLateRestoreTest.kt` — new, 2 cases.
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFollowModeTest.kt` — IO seam wired, helper drained first.

No surface, no dependency, no persisted schema change: the settings file keeps its shape, and a device that
already carries `navNorthUp: false` restores it exactly as before when the user has not touched the orientation.
