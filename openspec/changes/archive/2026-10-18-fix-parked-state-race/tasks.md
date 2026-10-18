# Tasks — fix-parked-state-race

Parent requirement: `unit-test-suite-runtime`, "A published work state is true when it is read" (one ADDED
requirement, three scenarios). `[#93]` marks the tracker item this closes (case 5 of §148).

## 1. The red case first

- [x] 1.1 `DiagnosticsLogWritePathTest.workPendingIsNotAParkedWorker`: park the worker, queue one entry below
      the high-water mark (nothing signals it, so it stays blocked on the deadline), assert the parked state
      is now reported false, then drain it again. Verifies *handed work is not an idle worker* and
      *a reader and the transition share one lock*.
- [x] 1.2 Red on HEAD recorded: `./gradlew :core:testDebugUnitTest --tests "*DiagnosticsLogWritePathTest*"`
      → `10 tests completed, 1 failed`; XML `tests="10" failures="1"`, message
      `java.lang.AssertionError: the entry the worker has been handed is not a parked worker`.

## 2. The fix

- [x] 2.1 `DiagnosticsLog.enqueue` takes the worker out of the waiting state under `bufferLock`; the field's
      contract is stated where it is declared (the state is true of the moment it is read).
- [x] 2.2 `DiagnosticsLog` publishes `flushedBatches`, incremented under `bufferLock` after a batch reaches
      the file, with the seams `flushedBatchCount()` and `awaitFlushedBatchAbove(batches, timeoutMs)`. A count
      is true of the moment it is read (design D2).
- [x] 2.3 The formerly flaky case awaits the crossing's flush instead of the worker's idleness, and drains the
      tail before it ends; its comment carries the historical measurement and the cause. Verifies *a flush is
      awaited as a flush*.
- [x] 2.4 Focused green: the class `10 tests, 0 failures`, repeated 5× forced; `:core:testDebugUnitTest`
      forced 3× green, module tally `642 tests, 0 failures, 0 errors, 0 skipped`.

## 3. Falsification and the loaded run

- [x] 3.1 Mutation (a): `if (wrote) flushedBatches++` → `if (false) …` — the formerly flaky case fails
      (`highWaterMarkFlushesWithoutWaitingForTheDeadline`), restored green.
- [x] 3.2 Mutation (b): the clearing line removed — `workPendingIsNotAParkedWorker` fails, restored green.
- [x] 3.3 Forced aggregate gate, the setting the defect needs:
      `./gradlew test -PforceTests --no-build-cache` → **BUILD SUCCESSFUL**, `:core` at
      `642 tests, 0 failures, 0 errors` (the first aggregate attempt of the session failed 1 `:app` case with
      a local JDK artifact, `NoClassDefFoundError: sun.security.ssl.NamedGroup$SupportedGroups`, which did not
      recur; the JDK situation and the daemon-JVM workaround are in `report.md`). The second confirmation is
      the CI run of the push.
- [x] 3.4 `[#93]` After the push, the CI run's `Run unit tests` step must be green; recorded in `report.md`
      and commented on the item. Done: run `39134747662`, `success`, `BUILD SUCCESSFUL in 6m 10s`, `:core` at
      642 tests / 0 failures, 5670 tests across the gate with no failures.

## 4. Closure

- [x] 4.1 `openspec validate fix-parked-state-race --strict`; structural checks:
      `tools/declared-cases-selftest.sh`, `tools/check-portability.sh`, `tools/check-doc-routes.sh`.
- [x] 4.2 `report.md` carries the traceability (scenario → case), the red-on-HEAD tally, both mutations, the
      aggregate tally and the CI result; the change is archived and the spec synced.
