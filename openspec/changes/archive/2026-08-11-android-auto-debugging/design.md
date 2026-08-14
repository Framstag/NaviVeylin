# Design: Android Auto startup hardening + diagnostics

## Context

See proposal.md for motivation. Current state that shapes the approach:

- `NavigationSession.onCreateScreen()` resolves the Hilt `AutoEntryPoint` lazily on the main thread; first resolution builds the entire singleton graph — `OSMScoutClient` (native init, stylesheet copy from assets, map DB open) and `LocationService` (starts GPS) — while the Android Auto host blocks waiting for the first template.
- No exception handling around session/screen creation: any failure propagates and the host sees a dead app.
- No crash or session logging exists; logcat is unreachable on a real head unit while driving.
- App is sideloaded, no Play Services, offline-first — cloud crash reporting is off the table.
- `:app` hosts `NaviVeylinCarAppService`; screens live in `:auto`; shared interfaces in `:core`.

## Goals / Non-Goals

**Goals:**
- Process survives Android Auto session startup under all failure modes.
- First screen returns fast even on cold start (heavy init off main thread).
- On-device crash + session log, viewable in AA and on the phone, exportable.
- Logging infrastructure reusable by both `:app` and `:auto`.

**Non-Goals:**
- Fixing navigation/render bugs unrelated to startup (map rendering races stay as-is).
- Native (JNI/C++) crash capture via breakpad — out of scope, see Risks.
- Android Automotive OS (AAOS) specifics — head-unit Android Auto only.

## Decisions

### D1: Pre-warm the Hilt graph in the background at session start
`NavigationSession` init kicks off a background warmup (Dispatchers.Default) that resolves `AutoEntryPoint` and touches the singleton `OSMScoutClient`, while `onCreateScreen` returns a lightweight loading screen synchronously. When warmup finishes, the real root/navigation screen is pushed via `ScreenManager`.

- Why: moves the slow one-time work off the host's response path; the singleton graph builds once per process, so later accesses are fast.
- Alternatives considered: (a) blocking init in `onCreateScreen` — current behavior, causes host timeouts; (b) init in `CarAppService.onCreate` — same main-thread problem, just earlier; (c) `android:process` split — overkill.

### D2: Guard every host-callback entry point with a fallback screen
Wrap `onCreateSession`/`onCreateScreen`/`onNewIntent` bodies in `runCatching`. On failure, return `ErrorScreen` (PaneTemplate with the message + "Retry" action) instead of crashing. Wrap `onGetTemplate` centrally via a `SafeScreen` wrapper so template-building exceptions degrade to an error pane rather than killing the process.

- Why: Android Auto hosts give no second chance; any throw in a host callback looks like "crashes immediately".
- Alternative: relying on the crash handler (D4) alone — recovers nothing for the user, session just dies.

### D3: Diagnostics log lives in `:core` as a file-backed singleton
New `DiagnosticsLog` (plain object, Context initialized from `NaviVeylinApp.onCreate`) appends timestamped lines to `filesDir/diagnostics/app.log` with tags (`CRASH`, `SESSION`, `TEMPLATE`, `WARMUP`). Bounded: rotate to `app.log.1` when > 256 KB, keep newest.

- Why: `:core` is shared by `:app` and `:auto` without circular deps (same pattern as `MapRenderUtil`); file-backed because head units can't be adb'd; rotation keeps storage bounded (spec).
- Alternative: Room table — overkill for append-only text logs; logcat-only — unreachable on car.

### D4: Crash capture via `Thread.setDefaultUncaughtExceptionHandler`
In `NaviVeylinApp.onCreate`, install a handler that logs the stack trace to `DiagnosticsLog` then delegates to the previous handler (preserves default behavior). Only the main thread's default handler is installed; `Thread.setDefaultUncaughtExceptionHandler` covers all threads that lack their own handler.

- Why: no external dependency, works offline, works on sideloaded builds.
- Alternatives: Crashlytics/Sentry — require network + Play services integration, rejected by project constraints; `androidx.crashreporting` equivalents — still network-backed.

### D5: Diagnostics UI — AA screen + phone dialog
- `:auto`: new `DiagnosticsScreen` (PaneTemplate, rows = last ~20 log lines newest-first) reachable from `RootScreen`; refresh via `invalidate()` when shown.
- `:app`: extend the existing About/Diagnostics dialog to read the log file, display it, and share via `Intent.EXTRA_TEXT` (last ~50 KB) — no FileProvider/manifest changes needed.

- Why: car screen is where the failure happens; phone view lets the user send the evidence after parking.
- Alternative: FileProvider share of the raw file — nicer fidelity, but adds manifest + XML config for marginal benefit over text sharing.

### D6: Timed instrumentation around warmup and first template
`DiagnosticsLog` gains a `time(label) { }` helper; log durations of warmup, Hilt resolution, and first `onGetTemplate`. This turns "crashes immediately" into a concrete timeline in the log.

- Why: the proposal's suspected cause (slow main-thread init) needs evidence; timings confirm or refute it from a real head-unit session.

## Risks / Trade-offs

- [Native (C++) crashes are not captured by the Java crash handler] → Mitigation: session logging still records the last events before the native call; wrap JNI calls in try/catch where feasible. Breakpad-style capture listed as follow-up if evidence shows native crashes.
- [Warmup racing with session teardown (host disconnects quickly)] → Mitigation: warmup coroutine checks session-destroyed flag before pushing screens; scope cancelled in `onDestroy`.
- [Error/loading screen shown while warmup still running] → Mitigation: push the real screen when warmup completes; until then the loading pane is fully interactive-safe (no observers started).
- [Log file grows during long sessions] → Mitigation: size cap + rotation (D3); share caps exported text (D5).
- [Retry loop after permanent failure (e.g. no map data)] → Mitigation: error screen shows the message once per session; retry re-attempts warmup, no auto-loop.

## Migration Plan

New feature + hardening — no schema/data migration. Deploy as normal APK sideload. Rollback = install previous APK; logs and rotation are self-contained in `filesDir` and harmless if the feature is removed. `DiagnosticsLog` must tolerate being uninitialized (null-safe no-op) so `:auto` unit tests and host-JVM stubs keep working.

## Open Questions

- Exact root cause of the observed crash — will be resolved from real head-unit diagnostics logs after this change ships (does not change specs, approach, or task breakdown).
