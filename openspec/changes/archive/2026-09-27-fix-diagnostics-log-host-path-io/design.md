# Design

## Context

See `proposal.md` — Why, and the delta spec `specs/auto-diagnostics/spec.md` for the requirements.

Constraints that shape the approach, from the current code and the in-flight work:

- `DiagnosticsLog` (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt`) is a Kotlin `object`
  used from `:app` and `:auto`, initialised once in `NaviVeylinApp.onCreate` and safe to call before
  `init` (every call is a no-op in host-JVM tests). `log`/`logThrowable` are **non-suspend** and are
  called from many contexts, including non-coroutine ones (native bridge wiring, exception handlers).
- `appendLine` (`:149`) holds one `synchronized(lock)` across `file.length()` + `file.appendText(…)`
  and, at the 256 KB cap, `rotate()` (`:163`) which renames `app.log` → `app.log.1`.
- Callers on car **host** paths (car-app library dispatches host callbacks on the app main thread,
  `RemoteUtils.dispatchCallFromHost`): `MapScreen.onGetTemplate` (`auto/…/MapScreen.kt:430`),
  `SessionCarSurfaceHost` adopt/release (`:103`, `:187`), `NavigationManagerController` (`:71`, `:82`,
  `:116`), `recordNotificationPost` (`app/…/NavigationNotificationService.kt:268`, from `:81`/`:149`).
- Readers: `DiagnosticsScreen.onGetTemplate` (`auto/…/DiagnosticsScreen.kt:25`) — a host callback
  reading the whole file; `AboutDialog` (`app/…/ui/about/AboutDialog.kt:181` inside `remember { … }`,
  `:219`, `:230` share).
- In-flight sibling: `fix-aaos-host-crash` states the general rule ("a host callback only retains
  state", "no fault escapes") in capability `car-host-fault-isolation`, which is **not yet** in
  `openspec/specs/`. This design keeps the requirement logging-specific so it holds regardless of when
  that change archives.
- Tests: `:core` runs on the host JVM/Robolectric; `DiagnosticsLog` already has an `initForTest`,
  `reset`, an overridable `maxBytes`, and `TODO.md` §47 documents the automotive case where the file is
  never created at all.

## Goals / Non-Goals

**Goals**

- No filesystem access, no blocking lock and no unbounded allocation on the thread that logs an entry —
  in particular not inside a car host callback.
- The file channel keeps its current externally visible shape: same path, name, rotation pair, line
  format, 256 KB bound, and the same evidence value for the `guidelines/Build.md` §10 recipe.
- A crash trace still lands on disk even if the process dies immediately.
- Testable without a device: the worker and the flush deadline are injectable.

**Non-Goals**

- Not `TODO.md` §47 (the file is not created on the automotive build). It is a separate defect with its
  own change; this change must not assume the file exists — the no-op behaviour stays.
- Not a redesign of what is logged, the tag set, the diagnostics screens' content, or the log format.
- Not the logcat mirror: `Log.d`/`Log.e` stay on the caller's thread (they are cheap, non-blocking and
  are what the §10 recipe greps).
- Not a general logging framework, no new dependency.

## Decisions

### D1 — Buffer in the seam, not at the call sites

Chosen: `DiagnosticsLog` itself owns a bounded in-memory ring plus one worker; `log`/`logThrowable`
become "append to ring (cheap, non-blocking) + logcat", and the worker owns the file.

- Alternatives: (a) each caller dispatches the write to `Dispatchers.IO` — the call sites are
  non-suspend and include non-coroutine contexts, so it would need a scope per caller and every caller
  could forget it; the seam is the only place that cannot be missed. (b) Drop the file write on host
  paths and keep logcat only — rejected: on a real head unit the file is the only triage channel
  (`TODO.md` §47 shows logcat may not even be reachable), so this would trade a latency risk for an
  evidence loss. (c) Make logging suspend — rejected: it changes the signature of a seam used from
  exception handlers and native wiring.
- Consequence: `log`/`logThrowable` must stay non-blocking and allocation-bounded (see D4).

### D2 — The crash path writes synchronously, bypassing the worker

Chosen: `installCrashHandler`'s handler writes its (single) entry directly under the same lock, then
delegates to the previous handler. The worker pauses while the handler holds the lock.

- Alternatives: (a) enqueue and wait for a flush with a timeout — rejected: while the process is dying
  the worker may never be scheduled, and a timeout adds delay to the platform's own handling. (b) Flush
  the queue from the handler before delegating — rejected: the entry may not be in the ring at all
  (logcat-only path) and the queue could be arbitrarily long. (c) Keep the whole log synchronous (status
  quo) — rejected: that is the defect.
- Consequence: two writers, one lock. The handler's write is one short line under `synchronized(lock)`;
  the worker's append takes the same lock (bounded by one append), so the handler cannot block behind a
  rotation for more than one flush.

### D3 — Worker: one daemon thread, not a coroutine dispatcher

Chosen: a single dedicated daemon `Thread` (not an executor, and no coroutine scope), injectable for
tests through the seam's knobs — `flushIntervalMs`, `maxPendingEntries`/`maxPendingChars`, and
`reset()` (which retires and joins the worker so a test starts from the uninitialised state) — plus
`flushNow()`/`awaitDrained()`/`workerThreadOrNull()` where the caller is `:core` itself. The worker is
created lazily on the first buffered entry and lives for the process lifetime.

- Alternatives: (a) `Dispatchers.IO` + an internal scope — works, but `:core` classes are exercised in
  host-JVM tests where the dispatcher is only available through `kotlinx-coroutines-test`, and the flush
  deadline logic is easier to drive deterministically with an explicit executor + injectable clock.
  (b) `HandlerThread` — Android-only, and `:core`'s seam must stay callable in host tests.
- Consequence: the process holds one extra thread; documented in the KDoc and asserted by a test that
  the thread is a daemon (a JVM must not be kept alive by it — relevant for `:core` unit tests).

### D4 — Bounded ring with a drop-oldest policy and a high-water flush

Chosen: a bounded ring (capacity in **entries** and in characters; default derived from the existing
256 KB file cap) that drops the oldest entries on overflow, plus: flush when the ring reaches a
high-water mark, and a timed flush with a configurable bound for the low-rate case.

- Alternatives: (a) Unbounded queue — rejected: the spec requires a bounded memory footprint and the
  failure mode we are fixing is exactly "the app's own diagnostics hurt the app". (b) Drop the newest
  on overflow — rejected: the tail is the triage-relevant part. (c) Flush every entry immediately —
  rejected: that is the current behaviour in a different thread (still a file write per entry, just not
  on the host thread; it would keep the rename storm at the cap and cost a thread hand-off per line).
- Consequence to state in the spec/design: a burst that overflows the ring loses oldest entries, but the
  file itself is still the same bounded artefact.

### D5 — Flush deadline and rotation stay on the worker

Chosen: the worker polls the ring with a bounded wait (flush deadline, default ~250 ms) and rotates via
the existing `renameTo` pair, now from the worker; `readEntries`/`exportText` do not race the writer:
they wait (bounded) for the worker to drain and then read. **A drained batch is written only to the
target it was drained for** — the drain records the target file and the write is skipped when `init`/
`reset` repointed the log meanwhile (found during apply: without it, a batch drained for the app log
could land in a newly configured file, e.g. a test's temp log).

- Alternatives: (a) Append-only file with truncate-oldest rewriting — rejected: a full-file rewrite is
  more expensive than a rename and changes the on-disk artefact the readers and the §10 recipe rely on.
  (b) Two alternating files with sizes tracked in memory — equivalent on disk but needs a persisted
  size/last-file note to survive process restarts (the reader would have to reconstruct it). Keep the
  rename.
- Consequence: the flush deadline is the maximum evidence loss on a hard kill; 250 ms is well below the
  seconds-to-minutes window the host-crash report describes, and the crash path (D2) is exempt.

### D6 — No log line per template build

Chosen: the `"MapTemplate delivered"` line in `MapScreen.onGetTemplate` is removed. Template builds are
unbounded (host-paced, once per `invalidate()`), and a failure already logs through `logThrowable`
(`MapScreen.kt:433`) while the surface/render `MAP` lines carry the lifecycle evidence.

- Alternatives: (a) Throttle it to one per second — rejected: it still puts a per-build code path on the
  host answering path and duplicates the render diagnostics. (b) Move it to the render loop's existing
  throttle — rejected: it answers a different question (template delivery vs rendered frames) and the
  render diagnostics already cover the render side.
- Consequence: the §10 recipe loses a per-template line; the recipe's baseline counts `lock OK` and
  `surface adopt/release`, which are unaffected.

### D7 — Reads move off the calling thread, the file stays the reader's source

Chosen: `readEntries`/`exportText` gain a background variant used by both readers; the car diagnostics
screen publishes the entries into its state and calls `invalidate()` when they arrive, and `AboutDialog`
loads them in a `LaunchedEffect`/background dispatch with a loading state.

- Alternatives: (a) Cache the last N entries in memory and serve readers from the cache — rejected: the
  file is the artefact of record (it must show entries logged before the current process, e.g. a crash
  trace) and a cache would make the viewer disagree with the file. (b) Leave the readers as they are —
  rejected: the car diagnostics screen reads the file **inside a host callback** and the phone dialog
  reads it during composition.
- Consequence: the diagnostics screen shows a loading state on first composition; the phone dialog gets
  one recomposition after the load.

### Threading and lifecycle model (new/changed components)

| Component | Thread | Lifecycle |
|---|---|---|
| `DiagnosticsLog.log` / `logThrowable` | caller (may be the app main thread / a car host callback) | only an in-memory append + logcat; never blocks on IO |
| In-memory ring | guarded by the existing `lock`; append is short and allocation-bounded | process lifetime |
| Logging worker (single daemon thread) | its own thread | created lazily on first entry, process lifetime; flushes on the deadline / high-water mark, then idles |
| Crash handler write | the thread that hit the uncaught exception | one direct append under `lock`, then the previous handler runs (platform behaviour preserved) |
| `readEntries` / `exportText` background variant | caller-selected dispatcher (`Dispatchers.IO`) | one-shot per read |

## Risks / Trade-offs

- **[The crash write contends with the worker's lock]** → both take the same lock; the worker's critical
  section is one append (or one rename), so the handler waits at most one flush, never for a queue.
- **[A hard kill loses the flush deadline's worth of tail]** → the deadline is configurable and small
  (~250 ms); the crash path is exempt (D2); the §10 recipe reads logcat for the last moments anyway.
- **[Dropping oldest entries in the ring hides mid-session evidence]** → the file (the artefact of
  record) is unaffected, the bound is derived from the existing 256 KB cap, and the drop is counted and
  reported once so a reader can tell the log is incomplete.
- **[An extra process-lifetime thread in `:core`]** → daemon, created lazily (nothing to leak in tests
  that never log) and injectable; a test asserts the daemon flag so a JVM is never held open.
- **[`TODO.md` §47: on the automotive build the file may never be created]** → unchanged behaviour: no
  worker is started and no write attempted when there is no file configured; the ring stays unused.
  This change must not make §47 harder to diagnose (it must not silently swallow a missing file: the
  failure stays visible under the existing `append failed` logcat line).
- **[Two readers can now observe a rotation]** → reads take the same lock to resolve the file pair;
  worst case a read misses the just-rotated tail, as today.
- **[The change is invisible on a healthy run]** → that is the point; the verification therefore asserts
  behaviour (no caller-thread IO, flush deadline, crash durability), not a user-visible difference.

## Migration Plan

No data, settings, resource or manifest migration; no native change; both flavours ship as one change.
Land in this order so each step is independently verifiable and revertable:

1. The ring + worker inside `DiagnosticsLog`, with `log`/`logThrowable` switched to the ring and the
   synchronous crash path preserved (D1-D5) — the seam's external contract is unchanged.
2. Remove the per-template-build line (D6).
3. Move both readers to the background read (D7).
4. Guideline touch-ups (`guidelines/Design.md` §4, `guidelines/Build.md` §10 note).
5. Verification: unit tests, both flavour builds, both suites, and the §10 recipe on the AAOS AVD for the
   logcat/file parity.

Rollback is `git revert` per step: the previous synchronous write path returns, and nothing persisted
or external depends on the new behaviour (the on-disk format is unchanged, so a reverted build reads a
log written by the new one).

## Open Questions

None that would change the specs or the approach. The flush deadline (default ~250 ms) and the ring
capacity are tunables, not decisions: they are configurable, recorded in the KDoc, and their values are
validated by the tests plus the on-device run rather than being frozen in the spec.
