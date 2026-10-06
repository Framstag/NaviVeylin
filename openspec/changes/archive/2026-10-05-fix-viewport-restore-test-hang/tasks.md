# Tasks

## 1. Make the gate safe after release

- [x] 1.1 Give `FirstDispatchGatedDispatcher` a `released` flag: hold a dispatch only while not released and the first slot is free, run every later dispatch inline, and set the flag before draining in `release()` (design D1; `TODO.md` §117). Verify: the two re-entry cases cannot be left with a queued block and no drainer — the failure they used to produce was a stall, so the check is that `MapCanvasViewModelViewportRestoreTest` completes: `./gradlew :app:testAutomotiveDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelViewportRestoreTest"` → 6 tests, 0 failures, ~30 s (before: 240 s timeout, no XML).
- [x] 1.2 Add a `heldCount` to `GatedDispatcher` (the hold-everything gate) so a case can assert it is actually holding the load. Verify: `screen size during viewport restore does not clobber restored center` compiles and its `awaitHeldBlock { gated.heldCount > 0 }` assertion holds.

## 2. Wait for state instead of assuming it

- [x] 2.1 Add the shared bounded waiter `private fun TestScope.awaitHeldBlock(reason, timeoutMs = 5_000, condition)` — advance the scheduler, sleep 10 ms, re-check, and assert with the reason when the deadline passes (design D2). Verify: it is used by the four hardened cases and the suite reports a named failure instead of stalling when a state never arrives (checked by the revert-checks in group 4).
- [x] 2.2 `re-entry while init suspended keeps the new map viewport` and `saveViewport during re-entry window keeps persisted viewport` wait for `gated.heldCount > 0` after `initMap(mapA)`, and the first also waits for mapB's restored center, replacing the ad-hoc 5 s loop (design D2). Verify: both cases pass in both flavors, and removing the supersession guard still fails the first (group 4).
- [x] 2.3 `saveViewport after restore persists current viewport` waits for the restore to be applied before it deletes the seed file and exercises the save, and then waits for the file to exist before loading it — the shape that failed with `NullPointerException` because the save had no-oped. Verify: the case passes in both flavors; its mutation check is in group 4.
- [x] 2.4 `screen size during viewport restore does not clobber restored center` asserts the gate holds the load before reporting the screen size, and waits for the first render with a measured deadline of 15 s instead of the 5 s that the first render reaches under load (design D3). Verify: the case passes in both flavors; the mutation check is in group 4.

## 3. Prove the suites produce a verdict again

- [x] 3.1 Run the class in both flavors: `:app:testMobileDebugUnitTest` / `:app:testAutomotiveDebugUnitTest` with `--tests "com.naviveylin.ui.map.MapCanvasViewModelViewportRestoreTest"`. Verify: 6 tests / 0 failures each — **automotive 27 s, mobile 27 s** (2026-10-02), where the automotive run previously timed out at 240 s with zero result XML.
- [x] 3.2 Run the whole gate with real execution: `./gradlew --no-build-cache --continue :core:testDebugUnitTest :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest`, then read the counts from `*/build/test-results/*/`. Verify: **`BUILD SUCCESSFUL in 5m 31s`** — `:core` 437 tests / 38 classes · `:app` mobile 1483 / 198 · `:app` automotive 1483 / 198 · `:auto` 754 / 74, 0 failures and 0 errors in all four. (`--no-build-cache` is required: the first attempt at this gate came back as a cache restore, which `TODO.md` §17 rejects as evidence, and then hit the stall.)

## 4. Revert-checks: the cases still falsify

- [x] 4.1 Remove the supersession guard (`initJob?.cancel()` in `MapCanvasViewModel.initMap`) and run the class (automotive). Verify: `re-entry while init suspended keeps the new map viewport` fails — 6 tests, 1 failure, 51 s (bounded, not a stall); guard restored and the class re-run green.
- [x] 4.2 Remove the save guard (`if (!viewportRestored)` in `MapCanvasViewModel.saveViewport`) and run the class (automotive). Verify: `saveViewport during re-entry window keeps persisted viewport`, `saveViewport during restore does not clobber persisted viewport` and `initMap renders at restored viewport not renderer default` fail — 6 tests, 3 failures, 36 s; guard restored and the class re-run green.

## 5. Bookkeeping

- [x] 5.1 Record in `TODO.md` §117 that the hang is fixed by this change (and how: the post-release dispatch is no longer swallowed, plus the bounded state waits), keeping the §101-family follow-up open. Verify: §117 names the change and its removal condition (removed when this change is archived).
- [x] 5.2 `openspec validate fix-viewport-restore-test-hang --strict`. Verify: the change validates clean (it declares `skip_specs: true` — a test-only change has no spec delta).
