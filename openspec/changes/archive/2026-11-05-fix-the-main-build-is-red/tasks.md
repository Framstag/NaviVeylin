# Tasks

- [x] 1. Add `navNorthUpSetByUser` and `freeFormNorthUpSetByUser` to `MapCanvasViewModel`, beside the three
      dark-mode flags, with the doc the pattern carries (`MapCanvasViewModel.kt`). Verify:
      `grep -n "SetByUser" app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` lists five flags.
- [x] 2. Mark the choice in `onSetNavOrientation` and `onSetFreeFormOrientation`
      (`MapCanvasViewModelFollow.kt`). Verify: `grep -n "SetByUser = true" app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelFollow.kt`
      lists both setters.
- [x] 3. Guard the two restores in `wirePersistedSettings` (`MapCanvasViewModelSettings.kt`): a chosen field keeps
      the in-state value, an unchosen one takes the persisted value. Verify: `grep -n "navNorthUp = \|freeFormNorthUp = " app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt`
      shows both bound to the local `if`-values, not to `settings`.
- [x] 4. Write the red cases in `NorthUpLateRestoreTest` (held read on its own scheduler): the choice that must
      survive the read, and the field that must still be restored. Verify on the pre-fix tree
      (`./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests "com.naviveylin.ui.map.NorthUpLateRestoreTest"`):
      `tests=2 failures=1`, the survivor case red — recorded in the evidence as
      `red-TEST-NorthUpLateRestoreTest.xml` (`timestamp=2026-11-05T09:12:23.151Z`).
- [x] 5. Point `MapCanvasViewModelFollowModeTest`'s storage at the test scheduler and drain it in
      `enableFollowDirectionMode` before applying the mode (`guidelines/BuildTests.md` §6e). Verify: the class is
      green with the fix — `:app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelFollowModeTest"`
      gives `tests=24 failures=0` (`2026-11-05T09:12:59.979Z`).
- [x] 6. Re-run both classes after the fix in both flavours. Verify: `tests=2 failures=0` and `tests=24
      failures=0` on `mobileDebug` (`2026-11-05T09:13:00.192Z`/`09:12:59.979Z`), and the same two classes green
      on `automotiveDebug` (`tests=2 failures=0` `2026-11-05T09:14:44.508Z`, `tests=24 failures=0`
      `2026-11-05T09:14:44.337Z`), copied as `green-automotive-TEST-NorthUpLateRestoreTest.xml` /
      `...MapCanvasViewModelFollowModeTest.xml`.
- [x] 7. Run the cases that name the orientation state beyond the two changed classes — the rest of
      `com.naviveylin.ui.map` and `AppSettingsTest` — on `mobileDebug`: `BUILD SUCCESSFUL` (`--tests
      "com.naviveylin.ui.map.*" --tests "com.naviveylin.data.AppSettingsTest"`, 2026-11-05T09:15Z). That step
      carries no tally of its own; the classes it covers are measured by the gate run (`tests=458 failures=0`,
      09:25–09:27, `evidence/gate-summary.txt`).
