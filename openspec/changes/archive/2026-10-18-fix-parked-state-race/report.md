# fix-parked-state-race — report and evidence

Closes issue **#93** (case 5 of §148: `DiagnosticsLogWritePathTest.highWaterMarkFlushesWithoutWaitingForTheDeadline`).

Files: `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt`,
`core/src/test/java/com/naviveylin/core/DiagnosticsLogWritePathTest.kt`,
`openspec/specs/unit-test-suite-runtime/spec.md` (one requirement added). Compiled file → the Gradle gate
applies; no device step (the property is a lock/flag ordering in a JVM sandbox).

## Root cause

`DiagnosticsLog` publishes `workerWaiting` for tests; the worker set it `true` before blocking on the buffer
condition and cleared it only when it *resumed*. `enqueue` signals the parked worker at the high-water mark
and cleared nothing, so a case that bursted and then asked `awaitParked` could take `bufferLock` before the
worker was scheduled to resume and read the stale `true` — mistaking its own signal for "parked" and reading
the file before the flush. A scheduling delay, hence: green on an idle host, red in 3 of 24 loaded aggregate
runs and in two consecutive `main` runs.

## Traceability — scenario to case

| scenario (delta) | case |
|---|---|
| *handed work is not an idle worker* | `DiagnosticsLogWritePathTest.workPendingIsNotAParkedWorker` |
| *a reader and the transition share one lock* | the same case (the transition and `awaitParked` both take `bufferLock`; the assertion fails if either moves) |
| *a flush is awaited as a flush* | `DiagnosticsLogWritePathTest.highWaterMarkFlushesWithoutWaitingForTheDeadline` (awaits `awaitFlushedBatchAbove`, not `awaitParked`) |

## Red on HEAD

```
./gradlew :core:testDebugUnitTest --tests "*DiagnosticsLogWritePathTest*"
  DiagnosticsLogWritePathTest > workPendingIsNotAParkedWorker FAILED
  10 tests completed, 1 failed

XML: <testsuite name="…DiagnosticsLogWritePathTest" tests="10" skipped="0" failures="1" errors="0">
     java.lang.AssertionError: the entry the worker has been handed is not a parked worker
```

The CI symptom of the same defect (two `main` runs, issue #93): run `39072831441` (2026-10-17, `5c994a0`) and
run `39130502425` (2026-10-18, `134fde9`), both `641 tests completed, 1 failed`,
`the line at the high-water crossing must be on disk: … TEST warm-up`.

## Green after the fix

```
:core:testDebugUnitTest --tests "*DiagnosticsLogWritePathTest*"   green, class 10 tests / 0 failures
  repeated forced: 5 runs, 5 green
:core:testDebugUnitTest -PforceTests --no-build-cache            3 runs, 3 green
  module tally: 642 tests, 0 failures, 0 errors, 0 skipped
test -PforceTests --no-build-cache (the forced aggregate gate)   BUILD SUCCESSFUL in 5m 48s
  core tally in the aggregate run: 642 tests, 0 failures, 0 errors
```

## Mutations (revert-check)

| # | mutation | case that must fail | observed |
|---|---|---|---|
| a | `if (wrote) flushedBatches++` → `if (false) …` | `highWaterMarkFlushesWithoutWaitingForTheDeadline` | FAILED, restored green |
| b | the clearing line removed from `enqueue` | `workPendingIsNotAParkedWorker` | FAILED, restored green |

## What the fix exposed, and why the case changed

With the flag honest, the formerly flaky case's *first* assertion failed for a second, real reason: a burst
split by the crossing's drain leaves a tail below the high-water mark, and that tail is flushed by the
deadline (by design: "buffered entries reach the file within a bounded delay"). The case had been passing
that assertion **on the stale flag**. It now awaits the flush itself (`awaitFlushedBatchAbove`, bounded 2 s
against the 60 s deadline) and drains the tail before it ends — the state it needs, not the worker's
idleness.

## Local Gradle note (not part of this change)

The daemon criteria resolve a JDK 21 from this machine, and the first `./gradlew` attempt took the VS Code
JRE (`…/redhat.java…/jre/21.0.12.1…`), which has no `jlink`:
`Execution failed for JdkImageTransform: … jlink executable … does not exist`. Issue **#91** owns the
machine-local JDK path in `gradle.properties`. The runs above used

```
./gradlew -Dorg.gradle.java.installations.paths= -Dorg.gradle.java.installations.auto-detect=true \
          -Dorg.gradle.java.installations.auto-download=false <tasks…>
```

One earlier aggregate attempt failed one `:app` case with a local JDK artifact
(`NoClassDefFoundError: sun.security.ssl.NamedGroup$SupportedGroups`, `MapManagerSourceSwitchPlanTest`); it
did not recur in the recorded aggregate run and is not reproducible on demand, so it is named here rather
than filed (CI's `Run unit tests` is the authoritative environment, and it never showed it).

## Structural checks

```
openspec validate fix-parked-state-race --strict   valid
tools/declared-cases-selftest.sh                   12 passed, 0 failed
tools/check-portability.sh                         OK — 42 engines, 133 values
tools/check-doc-routes.sh                          all 89 references resolve
tools/check-compose-sandbox-rule.sh                69 Compose test classes carry the rule
```

## CI (end-to-end)

Run `39134747662` (`d394b0f`, `Run unit tests` → **success**, `BUILD SUCCESSFUL in 6m 10s`) — the loaded
environment where the case failed twice:

```
app/build/test-results/testMobileDebugUnitTest:        275 classes, 2059 tests, 0 failures, 0 errors
app/build/test-results/testAutomotiveDebugUnitTest:    275 classes, 2059 tests, 0 failures, 0 errors
auto/build/test-results/testDebugUnitTest:              85 classes,  864 tests, 0 failures, 0 errors
core/build/test-results/testDebugUnitTest:              60 classes,  642 tests, 0 failures, 0 errors
osmscout-client-java/build/test-results/test:            7 classes,   46 tests, 0 failures, 0 errors
```

5670 tests, 0 failures, 0 errors. The two `main` runs this closes: `39072831441` (`5c994a0`) and
`39130502425` (`134fde9`), both `641 tests completed, 1 failed`. The run's `Publish the sensor inputs` step is
also green, so the commit under test produced a complete record — the precondition the sensors' rows needed
(the owed verification of #161).
