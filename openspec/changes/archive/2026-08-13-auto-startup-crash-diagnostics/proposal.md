# Proposal: Android Auto startup crash diagnostics

## Why

NaviVeylin crashes on launch under Android Auto on a real head unit: the launcher shows a crash hint and returns to the previous app. The on-device diagnostics log records only `CarAppService created`/`destroyed` — no session events, no crash trace. The failure happens between service creation and session creation, a zone the app currently logs nothing about. Without evidence from a real head-unit session, the root cause cannot be identified.

## What Changes

- **Log `onCreateSession` to the diagnostics file** — currently only `Log.d`. Answers whether the host ever requested a session. Closes a gap against the existing `auto-diagnostics` spec, which already requires session-creation events to be logged.
- **Add step-by-step warmup markers** — log before/after Hilt entry-point resolution and before/after native `OSMScoutClient` build. If the log shows "building native client…" but never "native client ready", the crash is a native (C++) failure during init — invisible to the Java crash handler.
- **Log `Application.onCreate` timing** — rules out slow process start (host bind timeout) as the cause.
- **Log service bind/unbind lifecycle** — `onBind`/`onUnbind`/`onStartCommand` distinguish "host bound then gave up" from "host never bound".
- **Document the logcat-gathering procedure** — `adb logcat -b crash` on the phone captures native crash backtraces and the host's (gearhead) view; the phone is adb-able even though the head unit is not.

No behavior change to the user-visible app beyond richer diagnostics output.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `auto-diagnostics`: The "session lifecycle is logged" requirement is extended so the log captures the startup sequence in enough detail to localize a native crash — warmup step markers (entry-point resolution, native client build) and app-startup timing, in addition to the already-required session events.

## Impact

- `:auto` module — `NavigationSession` (warmup step logging), `SessionLog` (new log helpers)
- `:app` module — `NaviVeylinCarAppService` (`onCreateSession` + bind/unbind logging), `NaviVeylinApp` (startup timing)
- `:core` module — `DiagnosticsLog` (reuse existing `time()` helper; no new storage)
- Tests — `SessionLogTest`, `StartupScreensTest` (Robolectric) extended for new log lines
- No manifest, dependency, or schema changes
