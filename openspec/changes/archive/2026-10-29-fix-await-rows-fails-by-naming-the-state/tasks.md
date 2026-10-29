# Tasks — fix-await-rows-fails-by-naming-the-state

## 1. Red case

- [x] 1.1 In `StartupScreensTest.awaitRows` add the awaited state's name and a `windowMs: Long = 5_000`
      parameter, keeping the body exactly at HEAD (`withTimeoutOrNull<Boolean>(windowMs) { … }`, then
      `return titles`), and add `awaitRowsNamesTheStateThatNeverArrived`: call the helper with an
      unsatisfiable predicate and `windowMs = 0L`, require it to fail, and require the message to name the
      awaited state. Run it and require the failure the item describes:
      `./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache --tests
      "com.naviveylin.auto.StartupScreensTest"` → `StartupScreensTest >
      awaitRowsNamesTheStateThatNeverArrived FAILED`, `java.lang.AssertionError: awaitRows returned rows for a
      state that never arrived, so the case fails later on a count` at `StartupScreensTest.kt:160`,
      `7 tests completed, 1 failed`, `BUILD FAILED in 12s`; `tests="7" failures="1" errors="0"` at
      `2026-10-29T08:09:13.343Z` (`evidence/red-on-head.xml`, `evidence/red-on-head.log`).
      *(spec: `unit-test-suite-runtime` — A helper that awaits a state fails by naming it)*

## 2. The fix

- [x] 2.1 Assert the awaited state in the helper: capture the `Boolean?` `withTimeoutOrNull` returns, re-read
      the rows after a final `Looper.idle()`, and `assertNotNull("the startup screen never published $what:
      $titles", arrived)`; read the rows through one `rowTitles` expression used by the loop and the message.
      Update the one existing call site (`:117`) to name its state (`"3 log rows"`). Verify: the same focused
      command → `BUILD SUCCESSFUL in 11s`, `tests="7" failures="0" errors="0"` at
      `2026-10-29T08:09:38.126Z` (`evidence/green-after-fix.xml`); after the last edit (the helper's KDoc no
      longer claims "no wall-clock window" while taking one) `--rerun-tasks` → `60 actionable tasks: 60
      executed`, `tests="7" failures="0"` at `2026-10-29T08:14:42.907Z` (`evidence/green-final.xml`,
      `evidence/green-final.log`).
      *(spec: `unit-test-suite-runtime` — A helper that awaits a state fails by naming it)*
- [x] 2.2 The class's other cases are unchanged in behaviour and the module is green in one invocation:
      `./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 37s`,
      92 classes, 886 tests, 0 failures, 0 errors, XML timestamps `2026-10-29T08:09:56.422Z` …
      `2026-10-29T08:10:30.206Z` (`evidence/auto-suite.log`) — 886 against the 885 the module carried before
      this change, i.e. exactly this one case added.
      *(spec: `unit-test-suite-runtime` — Module unit-test suite completes in a single invocation)*
- [x] 2.3 One `revert-check` mutation: the `assertNotNull` commented out with the swallowed window restored
      (marked `REVERT-CHECK MUTATION`). `awaitRowsNamesTheStateThatNeverArrived` fails at its "returned rows
      for a state that never arrived" assertion, `7 tests completed, 1 failed`, `tests="7" failures="1"`
      at `2026-10-29T08:10:46.519Z` (`evidence/revert-check-mutated.xml`, `evidence/revert-check.log`).
      Restored: `grep -c 'REVERT-CHECK MUTATION'` → `0`; `--rerun-tasks` → `60 actionable tasks: 60 executed`,
      `BUILD SUCCESSFUL in 33s`, `tests="7" failures="0"` at `2026-10-29T08:11:29.442Z`
      (`evidence/revert-check-restored.xml`).
      *(spec: `unit-test-suite-runtime` — A helper that awaits a state fails by naming it)*

## 3. Gate, scoped by what the change touched

- [x] 3.1 The change touches test sources and openspec documents only — no compiled file — so the whole-build
      gate is not owed (`tools/loop/gate.sh` not run) and the structural checks stand in:
      `openspec validate fix-await-rows-fails-by-naming-the-state --strict` → `Change … is valid`, exit 0;
      `tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent`, exit 0;
      `tools/check-todo-references.sh` → `62 citation(s) resolve`, exit 0; `tools/check-doc-routes.sh` →
      `all 155 section references resolve`, exit 0; `bash tools/gen-feature-list.sh --check-classification` →
      `specs read: 179, classified: 182, unclassified: 0, delta spec ids: 1`, exit 0
      (`evidence/checks.log`).
      *(spec: `unit-test-suite-runtime`, `build-test-gate` — verification follows the changed files)*

## 4. Evidence and archive

- [x] 4.1 Copied every cited XML into `evidence/` and into `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/700`;
      `bash tools/loop/evidence-check.sh openspec/changes/fix-await-rows-fails-by-naming-the-state --root .`
      before the archive → `PASS` (`ok C1 … ok C5 file:line: 445 resolved and in range, 0 unresolved or
      ambiguous`), and again after it (`bash tools/loop/evidence-check.sh
      openspec/changes/archive/2026-10-29-fix-await-rows-fails-by-naming-the-state --root .`) → `PASS`
      (`ok C5 file:line: 447 resolved and in range`) (`evidence/evidence-check.log`,
      `evidence/evidence-check-archived.log`); `evidence/evidence.md` carries the measure block, the red run,
      the green run, the mutation and the surface count.
- [x] 4.2 Archived; the directory is `openspec/changes/archive/2026-10-29-fix-await-rows-fails-by-naming-the-state`
      and the merged requirement is `openspec/specs/unit-test-suite-runtime/spec.md:542` (the spec now carries
      61 scenarios, 60 before this delta). Verify: `openspec validate
      fix-await-rows-fails-by-naming-the-state --strict` → `Change '…' is valid`, exit 0
      (`evidence/openspec-validate.log`); `openspec archive … --yes` reported `unit-test-suite-runtime: update`,
      `+ 1 added`, `Totals: + 1, ~ 0, - 0, → 0`, `archived as '2026-10-29-fix-await-rows-fails-by-naming-the-state'`
      (`evidence/openspec-archive.log`). Post-archive: `openspec validate --specs` → `179 passed, 0 failed`,
      `tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent: 0 open change(s)`
      (`evidence/checks.log`, the `--- post-archive` block).
- [x] 4.3 Left the tree as the change leaves it: no commit, no push, no version control, no tracker write.
