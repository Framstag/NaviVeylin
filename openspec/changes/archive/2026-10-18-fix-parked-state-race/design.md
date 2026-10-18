# Design — fix-parked-state-race

Scope: `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt`,
`core/src/test/java/com/naviveylin/core/DiagnosticsLogWritePathTest.kt`, one spec requirement. Compiled, so
the Gradle gate applies.

Threading: the caller thread enqueues under `bufferLock`; the worker drains and writes on
`DiagnosticsLog-worker`. Everything this change publishes is written and read under `bufferLock`.

## D1 — Where the transition out of the waiting state is published

**Chosen**: in `enqueue`, under `bufferLock`, beside the entry that makes it true. Pending work means "not
parked" from the moment the work exists, whatever the scheduler does with the worker.

| alternative | why not |
|---|---|
| clear the flag in the worker's `finally` only (as before) | the worker cannot clear a flag before it is scheduled to resume; the window *is* the scheduling delay, so no worker-side change closes it |
| `awaitParked` additionally requires `pending.isEmpty()` | answers a different question than "the worker is waiting": the burst is deliberately split, and the needed state is neither parked nor empty-ringed |
| relax the flaky case (accept any burst line) | hides the stale read; the case would pass even if the high-water signal did nothing, which is the property it proves |

**Risk**: a case that parks the worker and then queues one entry below the mark now waits for the worker to
drain instead of being told "parked" — that is the intent, and the new case asserts exactly it. No production
path reads the flag.

## D2 — The state a flush is awaited by

Correcting D1 exposed the case's *first* assertion for what it was: `awaitParked` after the burst returned
`true` only because of the stale flag. With the flag honest, a split burst's tail sits below the high-water
mark and is flushed by the deadline — by design, and a case with a 60 s deadline cannot wait for it. So the
case needs a state that means "a flush has happened".

**Chosen**: `flushedBatches`, incremented under `bufferLock` after a batch has reached the file, with the
seams `flushedBatchCount()` and `awaitFlushedBatchAbove(batches, timeoutMs)` mirroring the existing
`awaitDrained`/`awaitParked` shape. A monotonically growing count is true of the moment it is read.

| alternative | why not |
|---|---|
| await `awaitDrained` after the burst | the tail waits for the 60 s deadline, so the bounded wait fails — and draining the tail with `flushNow` first would make the assertion prove the deadline's work, not the crossing's |
| enlarge the burst so every tail re-crosses the mark | the final remainder is always below the mark, so the case still cannot end "parked"; and the expectation of which line is on disk would depend on the burst's arithmetic |
| count batches in the test by polling the file | a wall-clock poll in a test source is refused by the build check that owns this area |

**Risk**: the counter is a second published state; it is monotonic and only incremented after a successful
write (`wrote`), so it cannot report a batch that was dropped for a repointed target. The mutation run
removes the increment and the case goes red, which is the evidence that the case leans on it.

## D3 — The case that carries the flag's contract

**Chosen**: a deterministic case (`workPendingIsNotAParkedWorker`): park the worker, queue one entry with
`maxPendingChars` at its default so nothing signals the worker, assert `awaitParked(0)` is false, then
`flushNow(2_000)` so the sandbox is left clean. On HEAD the stale flag answers `true` and the case fails; on
the fixed code it answers `false`. No load, no repetition, no real-time wait.

**Alternative**: rely on the formerly flaky case in repeated aggregate runs — rejected: a change that fixes a
load-dependent failure owes a case decided by its subject, and the symptom case cannot be *proved* stable by
one host.

## Verification (all measured; tallies in the change's `report.md`)

- Red on HEAD: `./gradlew :core:testDebugUnitTest --tests "*DiagnosticsLogWritePathTest*"` →
  `10 tests completed, 1 failed`, `workPendingIsNotAParkedWorker`.
- Green after: the same selection green; the class repeated 5× forced green; `:core:testDebugUnitTest`
  forced 3× green; the forced aggregate gate (`./gradlew test -PforceTests --no-build-cache`) green with
  `:core` at **642 tests, 0 failures, 0 errors**.
- Mutations (revert-check), each restoring to green: (a) drop the clearing line → the deterministic case
  fails; (b) make the batch count never increment → the formerly flaky case fails.
- Local Gradle needs the daemon-JVM workaround documented in the report (the machine-local JDK path in
  `gradle.properties` resolves to a JRE without `jlink`; issue #91 owns that), so the *aggregate* confirmation
  is the CI run of the push, which is where the flake lived.
- No device step: the property is a lock/flag ordering, observed in a JVM sandbox.
