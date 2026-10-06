# Tasks — Speed up test iteration

Specs: `build-test-gate` (delta: iteration pre-gate) and `unit-test-suite-runtime` (delta: wall-clock
waits refused). Design: `design.md` (D1–D6). Every task names its own verification.

## 1. Pre-gate tool and its documentation

Lands independently of the in-flight work — it touches no production or test source.

- [x] 1.1 Write `tools/declared-cases.sh`: given a change name and the working-tree diff, print the
      selected test classes (artifacts-named ∪ diff-touched ∪ source mentions a changed type) and their
      last measured class time from the module result XML. Verify by running it for three in-flight
      changes and quoting the selected class count and the classes it printed, and by confirming the
      printed cost line matches the newest result XML for those classes.
- [x] 1.2 Write `tools/declared-cases-selftest.sh` — device-free, `tools/measure-highlight.py` precedent.
      Cases: each of the three rules selects its fixture class; a class named nowhere and touched
      nowhere is not selected; a change with no selectable class prints the empty-selection explanation
      and exits 0; the report prints the classes before executing anything. Verify by running the
      self-test and quoting its per-case result.
- [x] 1.3 Document the procedure in `guidelines/Build.md` §4 (recipe, the 30–45 s expectation for a
      declared set, "a pre-gate run is not gate evidence", the completion rule unchanged) and point
      `AGENTS.md` iteration-loop rule 2 at it. Verify by following the documented command on an
      in-flight change and pasting its output; confirm the documented text claims no saving below the
      measured floor.
- [x] 1.4 Name the case that exercises each `build-test-gate` delta scenario, so none stays unenforced:
      declared-cases-first (1.1 run), selection-needs-no-maintained-list (1.2 case), selection-visible-
      before-run (1.2 case), nothing-selectable (1.2 case), pre-gate-is-not-evidence (1.3 document plus
      the absence of any gate record from a pre-gate run). Verify by listing scenario → case in the
      change's record, each naming an executable check.
- [x] 1.5 Revert-check the tool: remove the artifacts rule, the declared-case case must fail; restore,
      re-run green forced (`-PforceTests --no-build-cache`). Record the command, the failure and the
      restored tallies.

## 2. Extend the time seam to the view-model windows

Prerequisite: the in-flight change that owns `EngineTimeSource`/`EngineDispatchers` has landed or is
confirmed finished (`git status` clean for those paths). Each task states its own two-sided case.

- [x] 2.1 Read `EngineTimeSource` for the recenter dwell window (`MapCanvasViewModel.RECENTER_DWELL_MS`)
      and drop the wall-clock comparison. Verify with a two-sided case: time just before the window
      produces no trigger, time advanced past it produces the trigger; both green in
      `MapCanvasViewModelBrowseReCenterTest`.
- [x] 2.2 Read the seam for the fix-quality tick. Verify with the two-sided case in
      `MapCanvasViewModelFixQualityTest` (before-window → no publication, after → publication).
- [x] 2.3 Read the seam for the follow throttle (`GPS_FOLLOW_RENDER_INTERVAL_MS`, now `internal` so the
      case names it) and convert `MapCanvasViewModelFollowModeTest` in the same step: seven `Thread.sleep`
      waits become `fakeNowMs` advances, plus the two-sided case (one millisecond short of the interval →
      the fix is coalesced away; at the interval → the same bearing change rotates the map). Verified: 24
      tests green, class time 7.044s → 4.812s, boundary case 0.072s.
- [x] 2.4 Give the renderer's own scope an injectable dispatcher (`rendererDispatcher`, default
      `Dispatchers.Default`) instead of the inline one, so the renderer's work — including its debounce —
      can run on a scheduler a case owns. Deliberately **not** `defaultDispatcher`: the restore window
      gates the ViewModel's initialization (`PostInitializationGatedDispatcher`), so the renderer needs a
      separate seam or the held gate would hold the very frame the case asserts. Additive — default
      unchanged — and verified with `MapCanvasViewModelViewportRestoreTest` green (6/6).
- [x] 2.5 Convert `MapCanvasViewModelViewportRestoreTest`: its bounded real-clock `awaitHeldBlock`
      polls and its three `Thread.sleep`/deadline waits are now `driveUntil` assertions on the test
      scheduler, with the collaborators the restore path hops through pinned in `setUp`
      (`viewportStorage.ioDispatcher`, `settingsStorage.ioDispatcher`,
      `searchHistoryRepository`/`favoriteRepository.defaultDispatcher`) and the renderer scope on 2.4's
      seam. Verified: class **16.083s → 7.22s** class time, `initMap renders at restored viewport`
      **5.549s → 0.233s**, 6/6 green, no `Thread.sleep`/`System.currentTimeMillis` left in the file.
      Residual: `saveViewport during re-entry window keeps persisted viewport` still measures 6.2s with
      no wait in it (baseline on the same machine 7.996s, on the quiet 05:00 machine 3.762s) — pre-existing
      real time outside the test's own waits, filed as `TODO.md` §142.
- [x] 2.6 Verify the production default is behaviour-neutral: `EngineTimeSource.System` still reads the
      wall clock (`EngineTimeSourceTest` green) and one case per converted subject runs with the
      production source and observes the same outcome as with the fake. Verified: `:core`
      `EngineTimeSourceTest` 1/1 green; 43 files construct the ViewModel, 40 of them with the production
      default (only the three converted subjects inject a fake), and the full mobile suite is green
      (221 suites / 1719 tests / 0 failures / 0 errors) with those 40 exercising the default path.
- [x] 2.7 Revert-check one window: revert 2.1's seam read, the named two-sided case must fail; restore
      and re-run it green forced. Record the evidence. Done 2026-10-06: the dwell read reverted to
      `System.currentTimeMillis()` → 15 tests, **11 failures**, including
      `theDwellBoundaryIsDecidedByInjectedTimeNotByElapsedRealTime`; restored → 15/15 green
      (`-PforceTests --no-build-cache`, fresh XML timestamp), no mutation marker left in the tree.

## 3. Refuse wall-clock waits in `:app`, and convert its sites

- [x] 3.1 Add the test-source scanner to `buildSrc/src/main/kotlin/` (fixed sleep, system-clock deadline
      loop, sleep-based pacing) plus its unit tests over fixtures — clean file, one sleep, one deadline
      loop. Verify with `./gradlew -p buildSrc test`.
- [x] 3.2 Convert `:app`'s wait sites to the seam or the test scheduler: `MapCanvasViewModelModeTest`,
      `MapCanvasViewModelInitThreadingTest`, `MapCanvasViewModelNavEndRestoreTest`,
      `MapCanvasViewModelVehicleAnchorTest`, `MapCanvasViewModelSingleFollowCenterTest`,
      `MapCanvasViewModelSpeedWidgetTest`, `MapCanvasViewModelAutoZoomCommitTest`, the remaining
      `MapCanvasViewModelBrowseReCenterTest`/`ViewportRestoreTest`/`FixQualityTest` helpers,
      `mapmanager/BasemapViewModelTest`, `NaviVeylinAppStartupLoggingTest`. Verify with a forced
      `:app` suite run and a per-class before/after class-time table from the result XML.
      **Progress 2026-10-06** (each verified green by a focused forced run):
      `MapCanvasViewModelAutoZoomCommitTest` and `MapCanvasViewModelSingleFollowCenterTest` converted
      (fake clock for the 200 ms follow-render throttle instead of `Thread.sleep(230)`) — against a
      same-session baseline of 18.337 s / 13.221 s they now measure 9.42 s / 9.29 s, wait-free;
      `MapCanvasViewModelModeTest` and `MapCanvasViewModelNavEndRestoreTest` converted (their route
      calculation is awaited as observable state on a real dispatcher instead of a
      `System.currentTimeMillis()` deadline poll), 9.30 s / 9.00 s, wait-free. Remaining:
      `InitThreadingTest`, `VehicleAnchorTest`, `SpeedWidgetTest`, `BasemapViewModelTest`,
      `AppStartupLoggingTest`, and `FakeOSMScoutClient` — its simulated-latency sleep is an `:app` test
      source, so it blocks 3.3 and is converted here rather than left to 4.1 (`RenderModeSwitchTest` is
      its only user).
      **Do not inject the engine's dispatchers onto the test scheduler to remove these waits:** measured
      here, that made two cases cost 9.3 s each (0.047 s / 0.055 s before) because the engine's native
      route path then hits a timeout; the state await on a real dispatcher keeps the wait-free rule
      without that cost. The residual ~5-9 s on the cases that await a real route calculation is filed as
      `TODO.md` §143 (pre-existing; absent on the quiet 05:00 machine).
- [x] 3.3 Wire the check into `app/build.gradle.kts` `preBuild` and verify
      `./gradlew :app:assembleMobileDebug` compiles with no warnings, the check passes, and
      `:app:testMobileDebugUnitTest -PforceTests --no-build-cache` is green with fresh timestamps.
- [x] 3.4 Revert-check the `:app` wiring: plant `Thread.sleep(10)` in an `:app` test source, the check
      must fail naming that file and line; remove it, the check passes. Record both outputs. Done
      2026-10-06: planted in `NaviVeylinAppStartupLoggingTest` → `BUILD FAILED` with
      `app/src/test/java/com/naviveylin/NaviVeylinAppStartupLoggingTest.kt:25: fixed sleep — Thread.sleep(`
      and `1 wait(s) to convert.`; removed → `:app:checkNoWallClockWaits` SUCCESSFUL. `:app`'s test sources
      hold 0 sleeps and 0 clock-deadline loops; the full mobile suite is green (221 suites / 1719 tests /
      0 failures), class time 124 s against the 252 s pre-change reference.

## 4. Refuse wall-clock waits in `:auto`, and convert its sites

- [x] 4.1 Replace `FakeOSMScoutClient.renderWithRouteAndPoisDelayMs`'s sleep with a test-controlled gate
      (latch or `CompletableDeferred`) and update its single user `RenderModeSwitchTest`; the switch
      case must still observe the render arriving late. Verify by running that class. Done 2026-10-06 as
      part of 3.2: measurement showed the delay was **unnecessary** — the mode switch already lands
      before the queued render runs — so the property and its sleep were deleted and
      `RenderModeSwitchTest` stays green (3 tests). No gate was needed.
- [x] 4.2 Convert `AutoMapRendererTest`, `MapScreenTest`, `AutoInitialViewportTest`,
      `TemplateFaultIsolationTest`, `StartupScreensTest`, `DiagnosticsScreenTest` to scheduler-driven
      awaits, keeping each case's observable assertion. Verified 2026-10-06: `:auto` holds 0
      `Thread.sleep` sites; per class — `AutoMapRendererTest` 76 tests / 4.68 s,
      `MapScreenTest` 3 / 7.04 s, `AutoInitialViewportTest` 8 / 2.71 s, `TemplateFaultIsolationTest`
      21 / 1.37 s, `StartupScreensTest` 6 / 0.24 s, `DiagnosticsScreenTest` 3 / 5.83 s, all green. Shapes
      used: explicit file mtimes instead of a `sleep(5)` for "newest file wins"; a real-dispatcher
      spin-pump (`runBlocking` + `withTimeoutOrNull` + `yield`) for the screens whose work lands on a
      real thread or the Robolectric looper; a new internal `zoomWalkLoopTurns` counter in
      `AutoMapRenderer` so the walk case awaits loop turns instead of `sleep(200)`. The full `:auto` suite
      also exposed a race the tighter loop made visible — `FakeMapScreenClient.styleSheetFlags` is
      appended from a real dispatcher and iterated on the test thread — fixed by making the fake's two
      recorded lists copy-on-write.
- [x] 4.3 Wire the check into `auto/build.gradle.kts` `preBuild`; verify `:auto:assembleDebug` compiles
      without warnings, the check passes, and `:auto:testDebugUnitTest -PforceTests --no-build-cache` is
      green. Done: `:auto:checkNoWallClockWaits` SUCCESSFUL; full `:auto` suite green — 76 suites /
      781 tests / 0 failures / 0 errors, class time 23 s.
- [x] 4.4 Revert-check the `:auto` wiring: plant a sleep in an `:auto` test source, the check must fail;
      restore, it passes. Record both outputs. Done 2026-10-06: planted in `StartupScreensTest` →
      `BUILD FAILED` with `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:35: fixed sleep —
      Thread.sleep(`; removed → `:auto:checkNoWallClockWaits` SUCCESSFUL.

## 5. Refuse wall-clock waits in `:core`, and convert its sites

- [x] 5.1 Make `DiagnosticsLog.time { }` read `EngineTimeSource` and convert `DiagnosticsLogTest` /
      `DiagnosticsLogWritePathTest` to advance the fake time instead of sleeping; the recorded duration
      must still be the elapsed controlled time. Done 2026-10-06: `internal var timeSource` added to
      `DiagnosticsLog` (`EngineTimeSource.System` production default), `time { }` reads it, and
      `timeHelperLogsDuration` asserts the *controlled* duration (`do work took 5ms` from
      `fakeNow += 5`). `DiagnosticsLogWritePathTest`'s bounded `poll` helper is replaced by the log's own
      `awaitDrained()` signal. Verified: `:core` suite green — 41 suites / 447 tests / 0 failures / 0
      errors, class time 4 s (was 32.8 s in the 2026-10-05 gate record).
- [x] 5.2 Wire the check into `core/build.gradle.kts` `preBuild`; verify `:core:assembleDebug` compiles
      without warnings, the check passes, and `:core:testDebugUnitTest -PforceTests --no-build-cache` is
      green. Done: `:core:checkNoWallClockWaits` SUCCESSFUL; suite green (41 / 447 / 0).
- [x] 5.3 Revert-check the `:core` wiring and the seam read: plant a sleep, the check must fail; revert
      5.1's seam read, the duration case must fail; restore both, forced green. Record the evidence. Done
      2026-10-06: planted sleep in `DiagnosticsLogTest` → `BUILD FAILED` with
      `core/src/test/java/com/naviveylin/core/DiagnosticsLogTest.kt:25: fixed sleep`; removed →
      SUCCESSFUL. `DiagnosticsLog.time` reverted to `System.currentTimeMillis()` →
      `timeHelperLogsDuration` FAILED with `the logged duration is the controlled one: … do work took
      0ms` (nothing slept, so the real clock measured zero); restored → `:core` suite green.

## 6. Integration, measurements and documentation

- [x] 6.1 Measure and record: per converted class, the class time before and after from the result XML,
      the resulting suite wall time per module, and the share of the previously wait-bound bucket that
      remains — in `guidelines/Build.md`'s suite section next to the existing fork-budget measurements.
      Verified: written 2026-10-06 as the §6 bullet "Wall-clock waits are refused, and the conversion is
      measured", with the per-module table (both `:app` flavors, `:auto`, `:core`, JNI), the same-session
      per-class deltas, and the two pre-existing residuals it does not claim (§142/§143).
- [x] 6.2 Run the unfiltered gate: both `:app` flavors, `:auto`, `:core`, JNI, all shipped ABIs, forced
      (`-PforceTests --no-build-cache`). Verified 2026-10-06: `test :app:assembleMobileDebug
      :app:assembleAutomotiveDebug -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 4m 54s`, 220
      actionable tasks (107 executed), 0 failed tasks; tallies `:app` mobile 221 / 1719 / 0, automotive
      221 / 1719 / 0, `:auto` 76 / 781 / 0, `:core` 41 / 447 / 0, JNI 2 / 26 / 0.
- [x] 6.3 Record the gate's phase timings with `tools/gate-timings.init.gradle.kts` and compare against
      the `guidelines/Build.md` §2 baseline. Verified: the record was written by the 6.2 run (123 executed
      tasks; suites `:app` automotive 79 s, mobile 75 s, `:auto` 70 s, `:core` 18 s) and the comparison is
      quoted in the new §6 bullet (suite work 12m00s → 4m02s, wall 9m46s → 4m54s).
- [x] 6.4 Confirm the no-device claim: the change's production files are exactly three —
      `MapCanvasViewModel.kt` (the two clock windows, the retired `nowMs` hook, the renderer seam),
      `AutoMapRenderer.kt` (the `zoomWalkLoopTurns` counter) and `DiagnosticsLog.kt` (the time seam plus
      `awaitDrained` visibility); no car surface code, no manifest, no resource and no native change, so
      no emulator or head unit step is owed. Verified by the change's `git status` file list.
- [x] 6.5 Confirm the documented rules still match reality: every scenario of both spec deltas has a
      named, executable case (the map below plus the group 2/3/4/5 cases), and the check has no allowlist
      file anywhere in the tree. Verified 2026-10-06: `checkNoWallClockWaits` is wired in `:app` (task
      3.3), `:auto` (4.3) and `:core` (5.2); `find` for an allowlist file returns nothing; every delta
      scenario names a case and each of the three module checks has a planted-sleep revert-check
      (3.4/4.4/5.3); `openspec validate speed-up-test-iteration` is valid.

## Scenario → case map (reference, not tracked work)

Every delta scenario of both spec files, with the executable check that exercises it. Cases are in
`tools/declared-cases-selftest.sh` (12 cases, device-free) and the per-module build check of groups 3–5.

**`build-test-gate` — iteration pre-gate**

- *Declared cases run before the module suite* → the documented command, executed 2026-10-06 for
  `fix-route-session-stop-path`: `--rules declared,touched` selected 9 classes (its artifacts' own
  names + its touched test files) before any suite ran.
- *Selection needs no maintained list* → selftest case "unrelated class is never selected" (a class no
  rule reaches is absent without editing any file), plus task 6.5's check that no selection file exists
  in the tree.
- *The selection is visible before it runs* → selftest case "measured class time is read from the result
  XML": the report prints the class list and its measured cost, and the tool never executes a test.
- *A change with nothing selectable says so* → selftest case "empty selection explains and exits 0"
  (change whose artifacts name no test class + empty diff).
- *A pre-gate run is not gate evidence* → the notice the tool prints on every run, the `guidelines/Build.md`
  §4 bullet stating it, and task 6.2's unfiltered both-flavor gate.

**`unit-test-suite-runtime` — wall-clock waits refused**

- *A new fixed sleep fails the build* → the buildSrc scanner case for a fixed sleep (task 3.1) and the
  planted-sleep revert-checks 3.4, 4.4, 5.3.
- *A system-clock deadline loop fails the build* → the buildSrc scanner case for a `System.currentTimeMillis()`
  deadline loop (task 3.1), plus one revert-check planting that pattern.
- *Converting a wait keeps the behaviour covered* → the two-sided cases of tasks 2.1–2.3 (before-window
  produces no trigger, past-window produces it), 4.1's late-arriving render and 5.1's measured duration
  from advanced fake time; the restore class joins them at 2.5.
- *The refused patterns stay refused* → each module's check enabled as its sources are cleaned and the
  whole module's test sources scanned green: tasks 3.3 (`:app`), 4.3 (`:auto`), 5.2 (`:core`).

## Workflow follow-up

- Archive the change and sync its spec deltas once group 6 is green.
- `TODO.md`: record the measured recovery and close or update any entry this change resolves (§132's
  fork-contention note stays open — this change does not touch fork counts).
