# Proposal: Fix Android Auto startup crash and add debugging tooling

## What Changes

NaviVeylin now passes Android Auto host validation and appears in the app list on a real head unit, but **crashes immediately on launch**. The exact failure is not yet confirmed — no crash logs are captured, and logcat is not reachable from a car session.

This change has two parts:

1. **Fix the startup crash.** Harden the Android Auto session startup path so the app cannot die instantly:
   - Move heavy one-time initialization off the main thread. The first `AutoEntryPoint` resolution inside `NavigationSession.onCreateScreen()` constructs the full singleton graph on the main thread — `OSMScoutClient` (native init, stylesheet copy from assets via `AssetCopier.ensureStylesheets()`, map DB open) plus `LocationService.startLocationUpdates()`. While the host blocks waiting for the first template, this can exceed host timeouts (ANR → host force-stop → "crash").
   - Wrap session/screen creation in exception guards with a safe fallback screen, so a failure surfaces as a readable error instead of a process death.
   - Pre-warm the Hilt entry point and native client before the host requests the first screen, or make session creation non-blocking.

2. **Add diagnostics to analyze and fix the situation.** Real head units cannot be adb'd from the dev machine while the user is driving, so the app must capture its own failure evidence:
   - Crash capture: `Thread.setDefaultUncaughtExceptionHandler` writes the fatal stack trace to a log file in the app's files dir.
   - Session event logging: structured log of `CarAppService`/`Session` lifecycle (create/destroy, `onCreateScreen` intent, screen pushes, template errors, Hilt resolution timing).
   - Viewable logs: a debug screen in the Android Auto UI ("Diagnostics") plus access from the phone app (About/Diagnostics dialog), with the ability to share/export the log file.

After diagnostics are in place, the confirmed root cause(s) of the crash get fixed (follow-up within this change's tasks once evidence is available from a real head-unit session).

## Capabilities

### New Capabilities
- `auto-startup-hardening`: Resilient Android Auto session startup — no process death on start, off-main-thread initialization, safe fallback screen when setup fails
- `auto-diagnostics`: Self-captured crash and session logs for Android Auto, viewable on the car screen and on the phone, exportable for analysis

### Modified Capabilities
<!-- No existing spec-level behavior changes. -->

## Impact

- `:auto` module — `NavigationSession`, `MapScreen`, new diagnostics screen, session startup logic
- `:app` module — `NaviVeylinCarAppService` (guard/init hooks), Hilt `AutoEntryPoint` wiring if pre-warming is added
- `:core` module — new crash-log store / session logger shared by `:app` and `:auto`
- AndroidManifest — no new permissions expected (log files live in internal storage)
- Tests — Robolectric tests for crash handler, session logger, and startup guard paths
