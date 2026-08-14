# Tasks: Android Auto startup hardening + diagnostics

## 1. Diagnostics logging core (`auto-diagnostics`)

- [x] 1.1 Add `DiagnosticsLog` to `:core` — file-backed append-only log (timestamped entries with tags), null-safe no-op when Context not initialized (host-JVM unit tests / stubs)
- [x] 1.2 Implement size cap + rotation: rotate to `app.log.1` when file exceeds 256 KB, keep newest entries
- [x] 1.3 Add `time(label) { }` timing helper that logs a duration line
- [x] 1.4 Install uncaught-exception handler in `NaviVeylinApp.onCreate` writing stack traces to `DiagnosticsLog` and chaining to the previous handler
- [x] 1.5 Log Android Auto lifecycle events: `CarAppService` create/destroy session, `NavigationSession` `onCreateScreen`/`onNewIntent`/destroy, screen pushes (intent action/data, screen name)
- [x] 1.6 Log template-build exceptions and warmup/first-template timings (see 2.x)

## 2. Startup hardening (`auto-startup-hardening`)

- [x] 2.1 Kick off background warmup in `NavigationSession` init — resolve `AutoEntryPoint` and touch the singleton `OSMScoutClient` on `Dispatchers.Default`; cancel and guard when session destroyed
- [x] 2.2 Make `onCreateScreen` return a lightweight loading screen synchronously; push real root/navigation screen via `ScreenManager` when warmup completes
- [x] 2.3 Wrap host callbacks (`onCreateSession`/`onCreateScreen`/`onNewIntent`) in `runCatching`; on failure return an error screen (message + Retry) instead of crashing
- [x] 2.4 Add `SafeScreen` wrapper that catches `onGetTemplate` exceptions and returns an error `PaneTemplate`
- [x] 2.5 Error screen Retry re-attempts warmup and re-pushes the real screen on success

## 3. Diagnostics UI (`auto-diagnostics`)

- [x] 3.1 Add `DiagnosticsScreen` to `:auto` — PaneTemplate listing last ~20 log lines newest-first, refreshed on show; entry row from `RootScreen`
- [x] 3.2 Add phone-side diagnostics view (extend About/Diagnostics dialog): read log file, display entries, share last ~50 KB via `Intent.EXTRA_TEXT` share sheet

## 4. Tests

- [x] 4.1 Unit tests for `DiagnosticsLog`: append, rotation at cap, timing helper output (Robolectric)
- [x] 4.2 Test crash handler: throwing on a background thread produces a `CRASH` log entry (Robolectric, default sandbox config per AGENTS.md)
- [x] 4.3 Test `NavigationSession` startup guard: `onCreateScreen` failure yields error screen, process not crashed (Robolectric, default sandbox)
- [x] 4.4 Test `SafeScreen`: template exception yields error pane
- [x] 4.5 Test `DiagnosticsScreen` row count/ordering and phone diagnostics dialog share intent (Robolectric)

## 5. Verification

- [x] 5.1 `./gradlew :core:test :auto:test :app:test` passes (existing tests stay green)
- [x] 5.2 `./gradlew :app:assembleDebug` builds successfully
