# A published work state must be true when it is read

## Why

Issue **#93** (`§148 Timing-sensitive cases fail in the loaded aggregate test run`), case 5:
`DiagnosticsLogWritePathTest > highWaterMarkFlushesWithoutWaitingForTheDeadline` failed in **two consecutive**
`main` runs (`39072831441` on `5c994a0`, `39130502425` on `134fde9`), both `641 tests completed, 1 failed`:

```
java.lang.AssertionError: the line at the high-water crossing must be on disk:
    [2026-10-18 13:33:38.137] TEST warm-up        (DiagnosticsLogWritePathTest.kt:126)
```

**Root cause — a state a reader can read after it stopped being true.** `DiagnosticsLog` publishes
`workerWaiting` for tests: the worker sets it `true` before it blocks on the buffer condition and clears it
only when it *resumes* (`DiagnosticsLog.kt:432-438`). `awaitParked` takes `bufferLock` and returns as soon as
that flag reads `true` — and the flag stays `true` through the whole window between `enqueue` signalling the
parked worker at the high-water mark (`DiagnosticsLog.kt:397-399`) and the worker being scheduled to resume.
A case that enqueues work and then asks "is the worker parked?" can be told *yes* by a worker that already
holds that work:

1. the case parks the worker — flag true, worker blocked;
2. the burst crosses the high-water mark, which signals the worker and clears nothing;
3. the case asks again, takes the lock **before** the worker resumes, and reads the stale `true` — its own
   signal mistaken for "parked";
4. it reads the file, which still holds only the warm-up line, and fails.

The window is a scheduling delay, which is why the case was green on an idle host and red in 3 of 24 loaded
aggregate runs. The *write* was never the defect: the worker writes a batch before it parks again.

**What correcting the flag exposed.** With the state true when read, the case's own first assertion
(`a burst returns the worker to its wait without the deadline`) failed for a *second, real* reason: a burst
split by the crossing's drain leaves a tail below the high-water mark, and that tail is flushed by the
deadline, not by a signal — by design ("buffered entries reach the file within a bounded delay"), which a
case with a 60 s deadline cannot pretend away. The case was passing that assertion on the stale flag. So the
state it needs is neither "parked" nor "an empty ring": it is **the flush the crossing caused**.

Both halves contradict `unit-test-suite-runtime`: "A module's failure set is independent of host load and
concurrency" and "A case awaits the state it needs, not a proxy of it".

## What changes

1. **`DiagnosticsLog.enqueue` takes the worker out of the waiting state under `bufferLock`**, where the flag
   lives: pending work means "not parked", from the enqueue that queued it, not from the moment the worker
   happens to be scheduled. Two lines plus the field's contract.
2. **`DiagnosticsLog` publishes how many batches the worker has written** (`flushedBatches`, incremented
   under `bufferLock` after a batch reaches the file, with the `internal` seam
   `awaitFlushedBatchAbove(batches, timeoutMs)` and `flushedBatchCount()`). A count is true of the moment it
   is read and cannot describe a moment that has passed.
3. **The formerly flaky case awaits the flush, not the worker's idleness**: it takes the batch count, bursts,
   awaits one more batch within the 2 s bound against the 60 s deadline (so the flush that arrives is the
   crossing's work, not the deadline's), then asserts `TEST burst-5` is on disk, and finally drains the tail
   so no case leaves the worker on the deadline. Its comment keeps the historical measurement and the cause.
4. **A deterministic case for the flag's contract**: park the worker, queue one entry *below* the high-water
   mark (nothing signals it, so it stays blocked on the deadline) and assert the parked state is now reported
   false. Red on HEAD, green after, no load and no repetition.

## Impact

| file | change |
|---|---|
| `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` | the waiting state's transition; the written-batch count and its two test seams |
| `core/src/test/java/com/naviveylin/core/DiagnosticsLogWritePathTest.kt` | one new case; the flaky case awaits the flush it needs |
| `openspec/specs/unit-test-suite-runtime/spec.md` | two scenarios added to a new requirement ("A published work state is true when it is read") |

Additive and internal: both published states have exactly one reader each, the `internal` test seams; no
production path reads them and no logging behaviour changes. **Rollback**: drop the clearing line and the
count increment (the two seams stay unused).

This change touches a compiled file, so the gate applies: the focused `:core` cases, the module's forced
suite repeated, and the forced aggregate gate (`./gradlew test -PforceTests --no-build-cache`) — the setting
the defect needed.
