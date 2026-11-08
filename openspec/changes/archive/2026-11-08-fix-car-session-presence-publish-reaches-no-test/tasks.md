# Tasks

## 1. Confirm the claim

- [x] 1.1 The two publishes were inline and host-only. `bash tools/look.sh
  'carSessionPresence().setActive'` named only `NavigationSession.kt`, and no `:auto` test constructs
  `NavigationSession`; `NavigationSessionDarkModeTest` and `NavigationSessionRestoreTest` state the
  `CarContext` constraint in their KDoc.
- [x] 1.2 The red case. `SessionPresencePublishTest#sessionCallbacksSelectMatchingPublishDirection`
  failed on the pre-correction tree with `the onStart callback selects the live publish`; XML
  `tests=1 failures=1 errors=0`, timestamp `2026-11-08T15:37:28.251Z`
  (`evidence/review-round-2-red.xml`, `evidence/review-round-2-red.log`).
- [x] 1.3 Route with the map. The reading set derived for the touched paths is
  `guidelines/BacklogTooling.md` §9, `guidelines/DesignCrossVariant.md` §8a and §8b, and
  `guidelines/UICar.md` §2 and §3. The shipped capability is `car-session-presence`. §8a owns the
  guarded-seam rule; no guideline edit is owed.

## 2. Bind lifecycle direction to named seams

- [x] 2.1 Add `publishCarSessionPresenceOnStart(presence)` and
  `publishCarSessionPresenceOnDestroy(presence)`. They delegate to one private helper, preserving the
  lazy provider resolution, `guardedHostCall` confinement and the publish/clear rejection names.
- [x] 2.2 Route the production `onStart` callback through the start seam and `onDestroy` through the
  destroy seam. The source-level case reads both callback bodies and requires each to select only
  its matching seam.

## 3. The host-free cases

- [x] 3.1 `SessionPresencePublishTest` has five cases: `sessionStartPublishesLive`,
  `sessionEndClearsTheSignal`, `aFaultingPublishIsConfinedAndNamed`,
  `clearingAFaultingPublishIsNamedForTheClear`, and
  `sessionCallbacksSelectMatchingPublishDirection`. Verify:
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SessionPresencePublishTest"
  -PforceTests --no-build-cache` → XML `tests=5 failures=0 errors=0`
  (`evidence/revert-check-green.xml`).
- [x] 3.2 The corrected delta compiles and the affected class passes. The focused run ends
  `BUILD SUCCESSFUL` (`evidence/revert-check-green.log`).
- [x] 3.3 Falsify the lifecycle-direction invariant with one production mutation: change the
  `onStart` call to the destroy seam. `sessionCallbacksSelectMatchingPublishDirection` fails with
  `the onStart callback selects the live publish`; XML `tests=5 failures=1 errors=0`, timestamp
  `2026-11-08T15:38:45.353Z` (`evidence/revert-check-red.xml`). Restoring the call and forcing the
  class gives XML `tests=5 failures=0 errors=0`, timestamp `2026-11-08T15:40:27.155Z`
  (`evidence/revert-check-green.xml`). The post-restore production diff is
  `evidence/production-diff.txt`; the mutation-marker search reports zero matches in
  `evidence/surface.txt`.

## 4. Hand over

- [x] 4.1 This corrected delta touches compiled code, so the fresh gate process runs
  `./tools/loop/gate.sh` once against the final tree; its `LOOP-GATE` line is carried whole by
  `evidence/gate.log`, and the gate's own tally for this class is `tests=5 failures=0 errors=0`,
  timestamp `2026-11-08T15:41:50.604Z` (`evidence/gate-SessionPresencePublishTest.xml`). The earlier
  gate record measured a different tree and is not reused.
- [x] 4.2 The red, mutation, restored-green, surface, production-diff and gate records are copied into
  the change's `evidence/` directory as they are produced.
