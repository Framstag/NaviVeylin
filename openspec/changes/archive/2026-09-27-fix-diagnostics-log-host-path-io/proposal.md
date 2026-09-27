# Proposal

## Why

`DiagnosticsLog.appendLine` writes the log line to disk **on the calling thread** — one global lock,
`file.length()`, `file.appendText(…)` (stat + open/write/close), and a file rename when the 256 KB cap
trips. Several callers are car **host-facing** paths that the car-app library dispatches on the app's
main thread: `MapScreen.onGetTemplate` (once per template build), the session `SurfaceCallback`
(surface adopt/release), the host navigation/trip calls in `NavigationManagerController`, and every
notification post.

That collides with the rule `fix-aaos-host-crash` settled (design D2/D3): a host callback that does
not answer promptly is a host problem, and per `TODO.md` §51 a blocked app process is a host-crash
input (ANR → kill → the host's queued template call runs against an invalidated `CarHost`). The
diagnostics log is also the only triage channel left when a real head unit crashes with no adb access
(`TODO.md` §47), so it must be made non-blocking rather than removed.

Recorded as `TODO.md` §61.

## What Changes

- **Logging never performs file I/O on the caller's thread.** Lines are appended to a bounded
  in-memory ring and flushed by one dedicated IO worker; the caller's only work is the in-memory
  append (and the existing logcat mirror).
- **The crash path stays synchronous.** The uncaught-exception handler writes its trace directly (not
  through the queue), so a process that is about to die still lands its stack trace on disk.
- **No log line per template build.** `MapScreen.onGetTemplate` no longer appends a line per
  `invalidate()`; the template-build evidence stays available through the failure path
  (`logThrowable` on a build exception) and the throttled `MAP` diagnostics.
- **Rotation and reads move off the caller's thread.** The size cap and the rename happen on the IO
  worker; the diagnostics screen / share export read the file on a background dispatcher instead of
  the main thread.
- **Unchanged on the outside**: same file path (`filesDir/diagnostics/app.log` + `app.log.1`), same
  line format, same 256 KB bound, same tags — the diagnostics screen, the share sheet and the
  `guidelines/Build.md` §10 recipe keep working.
- **Not BREAKING**: no public API, resource, manifest, flavour or Gradle change; no native change. It
  is a behaviour change only in that a diagnostic line may reach the file a fraction of a second after
  it was logged (the crash path being the deliberate exception).
- **Rollback**: revert the change's commits — the previous synchronous append behaviour returns. No
  persisted state, no migration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-diagnostics`: a requirement is added — capturing a diagnostic entry never blocks the caller
  (no filesystem I/O or blocking lock on the caller's thread), while the existing bounded storage and
  post-mortem-readability requirements stay in force (the crash path keeps a synchronous write so
  "log file survives process death" still holds).

## Impact

**`:core`**

- `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` — the whole change's centre: a bounded
  in-memory ring plus one IO worker (single-threaded executor/dispatcher) owning `appendLine`,
  `rotate`, and the file handles; `log`/`logThrowable` become non-blocking; `logThrowable` keeps a
  synchronous path for `installCrashHandler`; `readEntries`/`exportText` gain a suspend or
  background-dispatch variant for the readers.

**`:app`**

- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — `readEntries()` is called inside
  `remember { … }` (`:181`) and on a click (`:219`), i.e. a full log read on the **main thread** during
  composition; the share path (`:230`) builds the text from it as well. These move to a background
  read with a loading state.
- `app/src/main/java/com/naviveylin/NaviVeylinApp.kt` — `init`/`installCrashHandler` wiring unchanged
  in contract; the worker's lifecycle is documented (process lifetime).

**`:auto`**

- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — the per-`onGetTemplate` log line is removed;
  the remaining `MAP` lines (surface available, throttled pan) stay.
- `auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt` — `onGetTemplate` calls
  `DiagnosticsLog.readEntries()` (`:25`), i.e. a **host callback reads the whole log file**; it moves
  to a background read with the entries published into the screen's state.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` and
  `auto/src/main/java/com/naviveylin/auto/SessionCarSurfaceHost.kt` — no call-site change beyond the
  removed line; they are the host paths the requirement protects.
- `auto/src/main/java/com/naviveylin/auto/SessionLog.kt` — unchanged (it is the session-event facade
  over the same seam).

**In-flight coordination (no delta here).** `car-host-fault-isolation` (change `fix-aaos-host-crash`)
is the capability that states "no host callback does blocking work"; it is not yet in
`openspec/specs/`, so this change states the logging-specific requirement in `auto-diagnostics` and
adds a task to reconcile the wording if that change archives first. It does not modify
`fix-aaos-host-crash`'s artifacts.

**Guidelines**

- `guidelines/Design.md` §4 (threading: which dispatcher owns what, and the rule that a diagnostic
  handler never blocks its caller).
- `guidelines/Build.md` §10 (the host-crash triage recipe still reads `Diag/…` logcat lines and the
  diagnostics file; note that a line may be flushed slightly after the event).
- `guidelines/UI.md` — only if the diagnostics screen's load path becomes visibly asynchronous
  (loading/empty state); no template change is intended.

**Specs changed:** `openspec/specs/auto-diagnostics/spec.md` — five requirements **added** (logging
never blocks the caller; the in-memory buffer is bounded; buffered entries reach the file within a
bounded delay; crash capture does not depend on the logging worker; reading diagnostics does not block
the UI). The existing requirements (crash capture, session logging, both viewers, bounded storage) keep
their text and scenarios — the new ones only add the constraints the write path has to meet, so no
requirement is replaced.

**Native / JNI:** none — no libosmscout submodule patch and no `:osmscout-client-java` override; the
change is Kotlin-only.

**Scope: general** (`:core` + both consumers). The motivating path is the car (Android Auto projection
and AAOS both run the same host callbacks), but the phone app logs through the same seam and must not
block its main thread either.

**Verification:** unit tests with an injectable clock/executor — a caller-thread assertion (no file IO
from the thread that calls `log`), a crash-path test (trace present on disk without a flush tick), a
ring-overflow test (bounded memory, oldest dropped, cap respected), a rotation test on the worker, and
a re-run of the existing diagnostics tests; plus the car/phone suites and both flavour builds. On
device: the §10 recipe on the AAOS AVD, asserting the `HOST`/`MAP` logcat stream is unchanged and the
diagnostics file still contains the session lines.
