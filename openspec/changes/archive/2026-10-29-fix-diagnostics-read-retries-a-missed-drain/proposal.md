# A read that misses its drain is retried and recorded, not published as an empty log

## Why

Item **#701** ("A missed log drain is indistinguishable from an empty log"). Verified on HEAD (`bbfd2a69`),
the claim holds on both of its halves:

- `DiagnosticsLog.readEntries()` called `awaitDrained()` and threw its boolean away, then read the file
  regardless (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:265` on HEAD `bbfd2a69`; the guard this
  change adds is at `:271`): the wait's expiry is not observable anywhere, so a read that missed the worker
  returned whatever the file held at that instant.
- The read happens once. `DiagnosticsScreen.loadEntries` takes that snapshot, sets `entries` and
  `invalidate()`s, and never reads again (`auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:175-182`),
  so a car session that missed the drain renders `no_log_entries` until the screen is replaced —
  `StartupScreensTest.diagnosticsScreenShowsNewestFirst` read `expected:<3> but was:<0>` in CI run
  `40510522192` for exactly this class of miss.

**Root cause, one mechanism:** the read treats the end of its bounded wait as "the log is complete", because
the boolean that says otherwise (`awaitDrained`, `DiagnosticsLog.kt:384-394`) was discarded at the call site
(`:265` on HEAD, `:271` after this change) and no retry followed it. One mechanism, two symptoms: an entry the
worker still holds is absent from the snapshot, and nothing records that the snapshot was taken after a miss.

**Red on HEAD** — the case that exercises the defect, in the class the item names as the existing seam:
`DiagnosticsLogWritePathTest#aMissedDrainIsRetriedAndRecorded`
(`core/src/test/java/com/naviveylin/core/DiagnosticsLogWritePathTest.kt`). It parks the worker on a 60 s
deadline (the state `DiagnosticsLogWritePathTest.kt:83` already forces with
`assertFalse("the entry is still buffered", DiagnosticsLog.awaitDrained(0))`), logs one entry, and reads:

```
./gradlew --offline :core:testDebugUnitTest -PforceTests --no-build-cache \
  --tests "com.naviveylin.core.DiagnosticsLogWritePathTest"
DiagnosticsLogWritePathTest > aMissedDrainIsRecordedAndRetriedOnce FAILED
    java.lang.AssertionError: the missed drain is recorded: [[2026-10-29 08:43:28.886] TEST warm-up]
11 tests completed, 1 failed
```

`XML tests=11 failures=1 (2026-10-29T07:43:26.770Z)` (`evidence/red-on-head.xml`, `evidence/red-on-head.log`).
The read returned the one line that had already been flushed and dropped the entry the worker held — the
item's mechanism, reproduced.

**Owning requirement**: `auto-diagnostics` — this change adds one next to "Buffered entries reach the file
within a bounded delay" (the drain bound the read waits on) and "Diagnostics viewable on the car screen" (the
window that showed the miss as an empty log): *A read that misses its drain is retried and recorded*.

**Owning guideline section**: `guidelines/Logging.md` §3 "Kotlin (app) logging" — readers use
`readEntriesAsync`/`exportTextAsync`, and a diagnostics line carries identity and never a coordinate
(`Logging.md` §3, `checkNoCoordinatesInLogs`).

**Surface, before the plan was fixed** — 1 case added, 0 cases rewritten, 0 fixtures realigned, 1 production
function, 2 production callers:

```
surface: 1 case added, 0 rewritten, 0 fixtures — grep -rn 'readEntries\|readEntriesAsync' --include=*.kt
*/src/main -> 7 lines, 2 production callers (auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:178,
app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt:210); grep -rl 'readEntries' --include=*.kt
*/src/test | wc -l -> 29 files, 51 call sites (50 before this case); grep -c '^#### Scenario:'
openspec/specs/auto-diagnostics/spec.md -> 42 existing scenarios
```

The two production callers are why the fix belongs in the read and not in one screen: the phone viewer
(`AboutDialog.kt:210`) and the share path (`exportText`, `DiagnosticsLog.kt:299-301`) take the same snapshot, and
a retry added to `DiagnosticsScreen` alone would leave both of them silent.

## What Changes

- **Added** an `auto-diagnostics` requirement: a read whose bounded drain wait expires records the expiry on
  the log's own diagnosis tag (identity only, no coordinate) and retries the drain once before reading the
  file — so the entries the worker held are published, and the window carries the marker that says it was
  taken across a miss.
- `DiagnosticsLog.readEntries()` honours `awaitDrained()`'s result instead of discarding it
  (`DiagnosticsLog.kt:270-289`). Both callers (car screen, phone dialog), `readEntriesAsync` (`:296`) and
  `exportText` (`:299-301`) inherit the retry without an API change: the signature and the return type stay
  `List<String>`.
- No production file outside `core/src/main` changes; no fixture, no build script, no new dependency.

## Impact

| file | change |
|---|---|
| `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` | `readEntries()` records an expired drain on `TAG` and retries the drain once before it reads the file |
| `core/src/test/java/com/naviveylin/core/DiagnosticsLogWritePathTest.kt` | one added case, `aMissedDrainIsRetriedAndRecorded`: parked worker, one buffered entry, the read must record the miss and return the entry |
| `openspec/specs/auto-diagnostics/spec.md` | one ADDED requirement, one scenario, one case |

Additive: no published signature, resource, string or preference changes. Rollback: revert the hunk in
`readEntries` and drop the added requirement and the case — the tree returns to the base commit's behaviour,
including the silent miss.
