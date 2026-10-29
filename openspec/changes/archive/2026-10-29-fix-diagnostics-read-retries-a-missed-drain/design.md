# Design — a read that misses its drain is retried and recorded

## The root cause

One mechanism. `DiagnosticsLog.readEntries()` waits for the logging worker with a bound and then discards the
answer to that wait:

```kotlin
fun readEntries(): List<String> {
    awaitDrained()                                   // HEAD :265 — the boolean is dropped
    val file = currentFile() ?: return emptyList()
    return fileLock.withLock { ... file.readLines() ... }   // HEAD :267-274 — read regardless
}
```

The two states the wait distinguishes — "the worker moved everything" and "the bound expired with entries
still buffered" (`awaitDrained`, `:384-394` after this change, `:370-380` on HEAD, returns `false` for the
second) — collapse into one on HEAD at `:265`. A snapshot taken after a miss is therefore published as if it were the log: the reader cannot tell an
empty file from an entry that arrives milliseconds later.

`DiagnosticsScreen.loadEntries` (`auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:175-182`) reads
that snapshot once per screen and `invalidate()`s; nothing reads again, so the miss is what the car session
shows for the life of the screen (`no_log_entries`, `DiagnosticsScreen.kt:114-118`). The phone viewer
(`app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt:210`) and the share path
(`exportText`, `DiagnosticsLog.kt:299-301`) take the same snapshot and lose the same way.

## The single fix

Honour the wait's result, in the read, once:

```kotlin
fun readEntries(): List<String> {
    if (!awaitDrained()) {
        log(TAG, "read drain expired after ${READ_DRAIN_TIMEOUT_MS}ms — retrying once")
        if (!flushNow()) {
            log(TAG, "read drain expired again after ${READ_DRAIN_TIMEOUT_MS}ms — reading the file as it stands")
        }
    }
    ...unchanged file read...
}
```

- `log(TAG, …)` is the log's own diagnosis tag and carries identity only — the bound, never a file name, never
  a position (`guidelines/Logging.md` §3, `checkNoCoordinatesInLogs`).
- `flushNow()` (`DiagnosticsLog.kt:341-344`) is `awaitDrained()` plus the signal that wakes a worker parked on
  its flush deadline, which is the state the red case reproduces: the worker is not wedged, it is asleep, and
  the retry both wakes it and waits for it. The retry is bounded by the same `READ_DRAIN_TIMEOUT_MS`, so a read
  costs at most two bounds and never blocks on an unbounded wait.
- The marker line is *an entry of the log*, so the window the car screen publishes carries it: a read taken
  across a miss marks itself, and the newest-first window the screen shows
  (`DiagnosticsScreen.kt:178`, `MAX_ROWS = 20`) leads with it instead of showing an empty log unmarked.

The public signature and the return type stay `List<String>`: both production callers, `readEntriesAsync` and
`exportText` inherit the fix without a call-site change.

## Blast radius

- One production function in one file: `DiagnosticsLog.readEntries()` (`core/src/main/.../DiagnosticsLog.kt`).
- One added test case in one test class: `DiagnosticsLogWritePathTest#aMissedDrainIsRecordedAndRetriedOnce`
  (the class already owns the parked-worker seam, `DiagnosticsLogWritePathTest.kt:83`). It pays the read's own
  1 000 ms bound once, because that expiry *is* the behaviour under test — no sleep, no poll loop, so
  `checkNoWallClockWaits` has nothing to refuse.
- 51 `readEntries` call sites over 29 test files keep the contract they assert (a `List<String>` of lines); the
  change can add at most two marker lines, and only on a read whose wait already expired — no case of the 42
  existing `auto-diagnostics` scenarios forces that state through `readEntries`.
- One capability spec delta (`auto-diagnostics`, ADDED requirement + one scenario). No fixture, no build file,
  no resource, no dependency, no API.

## Rollback

Revert the `readEntries` hunk and delete the added case and the added requirement. Nothing else depends on
either: the read returns to `awaitDrained()`-and-ignore, and the tree is the base commit's behaviour, silent
miss included.

## Alternatives the evidence eliminated

- **Poll until the list is non-empty in the screen.** The read returns the same `emptyList()` for a healthy
  empty log and for a miss (`DiagnosticsLog.kt:266-274`), so an emptiness-keyed loop spins forever on a
  device that has logged nothing — and the car screen builds its template in a host callback
  (`DiagnosticsScreen.kt:92-95`), which is the thread the poll would occupy.
- **Return a status or throw on expiry.** That changes the contract of a function with 2 production callers
  and 51 test call sites, and a throw would turn a slow worker into a crash path inside the diagnostics
  viewer. Neither caller can do better than the one retry the log owns.
- **Raise `READ_DRAIN_TIMEOUT_MS` (1 000 ms, `DiagnosticsLog.kt:63`).** The item's evidence
  (`expected:<3> but was:<0>`) cannot tell a slow worker from an empty file, so a longer bound would buy the
  same silence with a slower screen; the miss stays unobservable.
- **Retry in `DiagnosticsScreen` only.** Two of the three read paths would stay silent — the phone dialog
  (`AboutDialog.kt:210`) and `exportText` — and the marker would be written by a surface about another
  module's worker.
