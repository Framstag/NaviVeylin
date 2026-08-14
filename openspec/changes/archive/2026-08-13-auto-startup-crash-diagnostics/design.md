# Design: Android Auto startup crash diagnostics

## Context

See proposal.md — Why. Current state that shapes the approach:

- `NavigationSession.startWarmup()` runs Hilt entry-point resolution + native `OSMScoutClient` build on `Dispatchers.Default`; the session's `init` block starts it immediately, so any session construction would produce log lines. The observed log shows only `CarAppService created`/`destroyed` — the failure sits between service creation and session creation, or inside the warmup's native init (invisible to the Java crash handler).
- `DiagnosticsLog` appends synchronously (file append under a lock) — a marker logged *before* a native call is durable on disk even if the native call kills the process. This is the property the warmup markers rely on.
- `SessionLog` centralizes `NavigationSession` event logging so exact lines are unit-testable (existing pattern, `SessionLogTest`).
- `DiagnosticsLog.time(label) { }` already exists for duration logging.
- `:app` hosts `NaviVeylinCarAppService`; `:auto` hosts the session; `:core` hosts `DiagnosticsLog`.

## Goals / Non-Goals

**Goals:**
- Localize the startup failure to a specific step (host never requested a session / entry-point resolution / native client build / process start too slow) from the on-device log alone.
- Capture the host's view via logcat on the phone (adb-able) as a parallel evidence source.
- Keep every new log line unit-testable.

**Non-Goals:**
- Fixing the root cause — this change gathers evidence; the fix is a follow-up once the failing step is known.
- Native crash capture via breakpad — deferred; only warranted if logcat confirms a SIGSEGV in libosmscout.
- Changing the warmup architecture or host-visible behavior.

## Decisions

### D1: Warmup step markers via `SessionLog`
Extend `SessionLog` with a generic `warmupStep(step: String)` helper (logs under the existing `WARMUP` tag) and call it at four points in `startWarmup()`: before/after entry-point resolution, before/after native client build. The "before" markers are logged synchronously on the warmup thread before the native call, so a crash leaves the log ending at the last "before" marker.

- Why: same testable pattern as existing session logging; synchronous append (already how `DiagnosticsLog` works) makes the markers crash-durable.
- Alternatives: (a) logcat-only markers — unreachable on a head unit; (b) breakpad — heavy, premature until a native crash is confirmed; (c) wrapping the native call in try/catch — does not catch C++ crashes.

### D2: `onCreateSession` + host identity logging in `NaviVeylinCarAppService`
Log `onCreateSession` (with `sessionInfo.displayType`) and the connected host's identity (`getHostInfo()` package/uid) directly to `DiagnosticsLog` under the existing `CARAPP` tag, matching the current `created`/`destroyed` lines.

- Why: answers the primary question — did the host ever request a session, and which host (gearhead / DHU / AAOS) connected? Direct `DiagnosticsLog` calls keep service-level logging in one place (the service already logs `created`/`destroyed` that way).
- Alternative: `onBind`/`onUnbind` overrides — **not possible**: both are `final` in `CarAppService`; `onStartCommand` never fires for bound services (the host binds, it does not start). The bind signal is already covered by `CarAppService created`, since service creation happens on first bind.
- Alternative: route through `SessionLog` — mixes service-level and session-level concerns; `SessionLog` lives in `:auto` and is session-scoped.

### D3: `Application.onCreate` timing
Wrap `NaviVeylinApp.onCreate` body in `DiagnosticsLog.time("Application.onCreate") { … }` and add a process-start marker line before it.

- Why: rules out slow process start (host bind timeout) as the cause; `time()` already exists.
- Alternative: manual `System.currentTimeMillis()` diff — duplicates existing helper.

### D4: Logcat procedure documented, not coded
Add a task that documents the `adb logcat -b crash` + filtered `adb logcat` procedure (phone is adb-able; head unit is not) in the README or a `docs/` note, so the evidence-gathering step is repeatable.

- Why: the procedure is a dev workflow, not app behavior — no spec or code change; the phone-side logcat is the only source for native backtraces and the host's (gearhead) decision logs.
- Alternative: bake logcat capture into the app — impossible; logcat is host-side.

## Risks / Trade-offs

- [Warmup markers add log noise] → bounded by existing 256 KB rotation; markers are one line each, four per session.
- [Marker logged but process dies before flush] → `DiagnosticsLog` appends synchronously; the "before" marker is on disk before the native call executes.
- [Logcat on the phone may not capture the host's internal decision] → gearhead logs are verbose but present; if absent, the app-side markers still localize the failure.
- [Root cause turns out to be a native crash needing breakpad] → scoped as follow-up; logcat `-b crash` provides the backtrace in the meantime.

## Migration Plan

Additive log lines only — no format change, no schema/data migration. Old log entries remain readable. Rollback = install previous APK; new log lines are harmless if reverted.

## Open Questions

- The actual root cause of the crash — resolved by the evidence this change collects (logcat + markers) after it ships; does not change specs, approach, or task breakdown.
