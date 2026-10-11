# Tasks

## 1. Prove the defect on HEAD (tests only, before any production change)

- [x] 1.1 Add `offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent` to
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelBrowseReCenterTest.kt`: off-center BROWSE
  viewport, exactly one fix, quality settled (`advanceTimeBy(2500)` for the debounce), then advance the
  injected `fakeNowMs` past `RECENTER_DWELL_MS` and pump the scheduler **without** a further fix or
  viewport write. Verify: the case fails on HEAD (`./gradlew :app:testMobileDebugUnitTest --tests
  "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"` → this method `FAILED`), and quote the
  XML tally of the class from `app/build/test-results/testMobileDebugUnitTest/`.
- [x] 1.2 Record the red baseline in the change dir (one line: test name, expected, actual, command,
  timestamp) so the apply report can quote it. Verify: the note names the failing method and the
  command that reproduced it.

## 2. Complete the dwell on the clock

- [x] 2.1 In `MapCanvasViewModel.kt`, publish an appear-dwell re-evaluation from the existing
  `Dispatchers.Default` tick loop (`:1243-1251`) while a dwell is armed and the button is not yet
  visible (`timeSource` elapsed >= `RECENTER_DWELL_MS`), and feed it into the re-center derivation
  alongside `uiState` (`:1334-1336`). Keep the arm in a `@Volatile` field so the ticker never reads
  `_uiState` (design D1/D2). Verify: task 1.1's case passes; `./gradlew
  :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"` is
  green.
- [x] 2.2 Add the `internal val recenterDwellTickCount` seam (declared before `init`), mirroring
  `fixQualityTickCount` (design D3). No second period seam: the dwell rides the shared loop's existing
  `fixQualityTickMs`, so a case that needs a fast dwell tick shortens that one period. Verify: the
  class's cases observe the counter and the heartbeat (`fixQualityTickCount`), and `./gradlew
  :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest"` stays
  green (the shared quality gate and the tick loop are unchanged).
- [x] 2.3 Update `guidelines/UI.md` §7a ("Phone map re-center button") so the BROWSE bullet states that
  the dwell completes on elapsed time — the button appears once the dwell has elapsed with no further
  fix, pan or zoom. Verify: read the §7a bullet and confirm it matches the shipped rule and the spec's
  new scenarios; `openspec validate fix-browse-recenter-dwell` still passes.

## 3. Cover the remaining spec scenarios

- [x] 3.1 `aSingleFlickPanRevealsTheButtonWithoutAFollowUpEvent`: one pan gesture beyond the appear
  threshold, gesture ends, dwell elapses with no further event → visible. Verify: the case passes.
- [x] 3.2 `theDwellBoundaryIsDecidedByElapsedTime`: hidden one millisecond short of the dwell, visible
  at the boundary, both reached through the tick path (no extra fix). Verify: the case passes and fails
  if the tick condition is inverted.
- [x] 3.3 `noDwellEvaluationWhileTheViewportIsCentered`: with the vehicle inside the hide threshold,
  `recenterDwellTickCount` does not advance over a tick window (the "unchanged work costs no
  main-dispatcher traffic" rule). Verify: the case passes.
- [x] 3.4 `pressingReCenterDiscardsThePendingDwell`: reveal via the tick, press re-center, no later tick
  and no re-reveal while the viewport stays centered. Verify: the case passes.
- [x] 3.5 `leavingBrowseDiscardsTheDwellAndReturningReArmsIt`: arm → FREE_DRIVE before the dwell →
  dwell elapses → no visibility; back to BROWSE off center → a full new dwell is required. Verify: the
  case passes.
- [x] 3.6 `agedOutFixSuppressesThePendingDwell`: arm → fix no longer usable (`NONE` quality) → dwell
  elapses → hidden; a usable fix again on an off-center viewport re-arms the full dwell. Verify: the
  case passes.
- [x] 3.7 Repair the class harness (`awaitDwellAndRefresh`, `:150`) so no case implies that a second fix
  is what reveals the button: keep the injected-clock advance, drop the assumption from the comment and
  from `offCenterViewportShowsTheButtonWithoutAnyMapInteraction` (`:200`). Verify: the whole class is
  green and no remaining call site needs the extra fix to pass.

## 4. Integration verification

- [x] 4.1 Run the focused suites, then the full both-flavor gate once
  (`./gradlew test -PforceTests --no-build-cache`, `guidelines/Build.md` §2/§3), quoting per-module
  `tests/failures/errors` from the XMLs; confirm the build has no warnings. Verify: `BUILD SUCCESSFUL`
  plus the quoted tallies, and name any module/test excluded.
- [x] 4.2 `revert-check` the new invariant: mutate the tick condition to `false` (or remove the ticker
  duty), confirm task 1.1's case goes red against the mutated tree, restore the fix, forced green
  (TODO.md §113). Verify: the red-on-mutation and green-on-restore runs are quoted with their commands.
- [x] 4.3 Generate the coverage report for `:app` and confirm the re-center derivation and the dwell
  path are covered (`./gradlew :app:jacocoTestReport` path from `guidelines/Build.md` §7). Verify: the
  report path and the relevant class's line/branch numbers are quoted.
- [x] 4.4 On-device check (phone, stationary): start the app in phone mode with a persisted viewport away
  from the current position and do not touch the screen; the re-center button must appear within ~2 s,
  with `adb logcat -s NaviVeylin` showing a `browse re-center off=…px vis=true` line. Verify: the
  observed button plus the quoted log line; if the device cannot be provisioned, say so explicitly
  instead of implying device proof.

  **Device evidence** (Pixel_8 AVD `emulator-5554`, `:app` mobile debug built from this diff and
  installed with `adb install -r -t`, region `europe-germany-bremen`, persisted viewport
  53.0793/8.8017 mag 15, German locale — "app starts in phone mode, after a GPS fix" as reported):
  - one `adb emu geo fix 8.8050 53.0820` (the vehicle 568 px off the persisted centre), **no touch
    afterwards**; app log `GPS fix acc=5,0 … follow=false` at 15:24:45.536, `GPS fix quality: GOOD` at
    15:24:47.611, and `browse re-center off=568,4px vis=true` at **15:24:48.897** — 3.4 s after the only
    fix;
  - UI dump 6 s after the fix: node `content-desc="Auf Standort zentrieren"`, bounds
    `[53,2211][116,2274]` (bottom-left, per `guidelines/UI.md` §7a); still present 7 s later;
  - tap at the node's centre (84,2242): the node is gone, the mode toggle still offers
    `content-desc="Freie Fahrt starten"` (BROWSE kept), and the last `RECENTER` line is still the
    `vis=true` one — so the hide was the press, not the 60 s fix age-out;
  - the age-out path is visible in the same session: `RECENTER off=-px vis=false` exactly ~59 s after a
    fix (15:25:47.925 for the 15:24:45.5 fix), which is why a dump taken a minute later shows no
    button;
  - native log: `adb logcat -d -s NaviVeylin | grep -ciE "fatal|exception|crash|assert"` = **0**;
    screenshot `/tmp/recenter-button.png` (2.0 MB); FAIL=0.
  - **Final revision** (both review fixes, 2026-10-11 16:22-16:23 CEST): fix at 16:22:41.418, quality
    GOOD at 16:22:44.332, `browse re-center off=848,6px vis=true` at **16:22:48.410**; visible dump
    (`content-desc="Auf Standort zentrieren"`, bounds `[53,2211][116,2274]`) then hidden after the tap
    with `content-desc="Freie Fahrt starten"` still present. Retained in `device-logcat.txt` together
    with the native-fault count (0) and the `RECENTER` diagnostics lines (task 5.6).
  - **How to reproduce, and one trap**: the persisted viewport centre follows a re-center tap, so a
    re-run must put the vehicle off *that* centre — after the first session's re-center tap the persisted
    centre was the injected fix position, and a fix at the same coordinates measures `off=0,0px` and
    correctly shows no button (seen in the measurement at 16:21:06).
  - **Honest limit**: the emulator re-delivers the same position about once per second (`dumpsys
    location` `last location` keeps the identical 53.082000/8.804998 with an advancing
    `elapsedRealtime`), so this run cannot single out the clock-completed dwell — a fix-driven emission
    is available on this device too. What the run proves is the user-visible contract (button appears
    promptly and stays, press hides it, BROWSE kept, no native fault). The *discriminating* proof that
    the dwell is what completes it is host-side: the red case on HEAD and the mutation kill (6/22).
- [x] 4.5 Add a `TODO.md` entry for the latent gap found while tracing: `setScreenSize` changes the
  canvas metrics without a UI-state emission, so a canvas-size-only change cannot re-derive the offset
  (`MapCanvasViewModel.kt:2397`, `proposal.md` — Impact). Verify: the entry carries `**id:**`,
  `**category:**`, `**class:**` and `**status:**` on the line under its heading, and `openspec
  doctor` is clean.
- [x] 4.6 Record in `ki_processing_failures.log` any failed approach hit during this change (per the
  `apply` guidance). Verify: either an entry with a timestamp and a short problem description exists, or
  the task notes that no approach failed.

## 5. Independent-review round (2026-10-11, findings from run `739bc0cf`)

The read-only reviewer returned `VERDICT: PASS` (18/18 scenarios) with no blocker and seven P2 findings.
These tasks close the in-scope ones.

- [x] 5.1 Make the dwell guard read the tier the tick derives itself (`MapCanvasViewModel.kt:1289`) instead
  of relying on the debounced publication, so a fix that becomes unusable right after arming cannot
  reveal the button inside the quality pipeline's ~2 s window (finding P2-1; the arm-clear-on-visible
  branch `:3668-3673` is the other half of that contract). Verify: the new case
  `anAgingFixDoesNotLetTheClockRevealTheButton` fails when the tier check is removed from the guard
  (1/23 red) and passes with it; it is red on the pre-fix guard.
- [x] 5.2 Count the shared-tick heartbeat (`fixQualityTickCount`) **after** the dwell evaluation of the
  same iteration, so awaiting N heartbeats implies N dwell evaluations (finding P2-3), and keep the new
  helpers (`pumpUntil`, `pumpCurrentUntil`, `awaitReveal`, `awaitTickHeartbeats`, the spin budget)
  documented on the class. Verify: the negative cases (`noDwellEvaluationWhileTheViewportIsCentered`,
  `anAgingFixDoesNotLetTheClockRevealTheButton`) and the fix-quality suite stay green.
- [x] 5.3 Remove the stale `recenterDwellTickMs` references from `design.md` (the seam deliberately does
  not exist) and state what the cases do instead — production 1000 ms period, real-time wait, bounded
  spin (finding P2-2). Verify: `grep -c recenterDwellTickMs design.md` is 0.
- [x] 5.4 Add the fix-unusable discard condition to `guidelines/UI.md` §7a, so the guideline matches the
  synced requirement (finding P2-4). Verify: the §7a bullet names the aged-out/source-off case.
- [x] 5.5 Record both `revert-check` mutations verbatim in `red-baseline.md` (the tick-condition
  neutralisation of task 4.2 and the guard's tier removal of task 5.1), since a mutant log alone cannot
  be re-checked (finding 7). Verify: both mutations are described with the resulting red/green tallies.
- [x] 5.6 Re-run the device check on a build of *this* revision and retain the logcat excerpt in the
  change dir, and describe the visible/hidden dump pair with its timestamps in task 4.4 (finding P2-6).
  Verify: `openspec/changes/fix-browse-recenter-dwell/device-logcat.txt` carries the `RECENTER` lines, the
  `browse re-center … vis=true` line and the native-fault count, all from one run.

- [x] 5.7 Re-verify after the review fixes: focused suites (`MapCanvasViewModelBrowseReCenterTest` 23/23
  with the new age-route case, `MapCanvasViewModelFixQualityTest` 9/9), then the forced both-flavor gate
  — green on the second attempt (`BUILD SUCCESSFUL in 16m 42s`, 2026-10-11T14:35Z suites: app mobile
  255 classes / 1935 tests / 0 failures, app automotive 255/1935/0, auto 78/821/0, core 54/559/0,
  osmscout-client-java 5/34/0; no code warnings). The **first** attempt failed one unrelated case,
  `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp` (`expected:<1> but was:<5>`),
  which passes alone (2/2) and in a pair with this change's class (23/0 + 2/0): the known aggregate
  `DiagnosticsLog` shared-sink flake, recorded as a second instance in `TODO.md` §181 with this evidence,
  together with the fact that this change's 23 `RECENTER`-logging cases raise its exposure (finding P2-6/
  verification note). Verify: the tallies above plus §181's new bullet.

## Workflow follow-up

- Have a fresh read-only child confirm each new `map-modes` scenario against the diff and the gate XMLs
  before archiving (the writer is not the reviewer).
- Archive the change, then verify the archived `map-modes` spec text.
