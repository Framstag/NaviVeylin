## 1. Warmup step markers (`:auto`)

- [x] 1.1 Add `SessionLog.warmupStep(step: String)` helper logging under the `WARMUP` tag (spec: auto-diagnostics — Warmup steps recorded)
- [x] 1.2 Add four markers in `NavigationSession.startWarmup()`: before/after Hilt entry-point resolution, before/after native client build — "before" markers logged synchronously before the native call (spec: auto-diagnostics — Warmup steps recorded)
- [x] 1.3 Extend `SessionLogTest` with unit tests for `warmupStep` output format and tag (spec: auto-diagnostics — Warmup steps recorded)

## 2. Service-level logging (`:app`)

- [x] 2.1 Log `onCreateSession` with `sessionInfo.displayType` and host identity (`getHostInfo()` package/uid) to `DiagnosticsLog` under the `CARAPP` tag in `NaviVeylinCarAppService` (spec: auto-diagnostics — Session creation request recorded)
- [x] 2.2 Log host identity via `getHostInfo()` in `onCreateSession` — `onBind`/`onUnbind` are `final` in `CarAppService` and `onStartCommand` never fires for bound services, so the bind signal is covered by the existing `CarAppService created` line (service creation happens on first bind) (spec: auto-diagnostics — Session lifecycle events recorded)
- [ ] 2.3 Verify service logging manually via logcat on a device — the session-creation log line is unit-tested via the `sessionCreatedMessage` seam; manual verification covers the real host flow (`getHostInfo()` with a live host, real session construction) (spec: auto-diagnostics — Session lifecycle events recorded)

## 3. App startup timing (`:app`)

- [x] 3.1 Add a process-start marker line and wrap `NaviVeylinApp.onCreate` body in `DiagnosticsLog.time("Application.onCreate") { … }` (spec: auto-diagnostics — App startup timing recorded)
- [x] 3.2 Add a Robolectric test that instantiates the application and asserts the startup marker and timing line appear in `DiagnosticsLog.readEntries()` (spec: auto-diagnostics — App startup timing recorded)

## 4. Logcat procedure documentation

- [x] 4.1 Document the evidence-gathering procedure in README (or `docs/`): `adb logcat -b crash` for native backtraces, filtered `adb logcat` for host (gearhead) logs, and how to correlate with the on-device log timestamps (proposal: logcat-gathering procedure)

## 5. Verification

- [x] 5.1 Build compiles: `./gradlew :app:assembleDebug` without errors
- [x] 5.2 All existing tests pass: `./gradlew test`
- [x] 5.3 Verify no regressions in unrelated areas (phone app startup, map rendering, existing diagnostics UI)
