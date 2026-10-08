# Logging Guidelines — Native, Bridge and App Diagnostics

What a log line may say, where it is written, and how it reaches Logcat and the on-device diagnostics file.
This document owns the logging rules and the facts around them; the measurement recipes that use them are
`Build.md` §10 (on-device evidence), the coordinate gate is enforced in the build, and the disclosure duty
is `Regulatory.md` §9.

**Maintenance rule** — when a change supersedes a logging convention here, update this document in the same
change. `AGENTS.md` keeps the two facts a session needs before it reads anything else (the one tag and the
one bridge) plus a route here.

## 1. Native code (libosmscout submodule)

- Log through the platform-independent `osmscout::log` API (`#include <osmscout/log/Logger.h>`):
  ```cpp
  osmscout::log.Debug() << "...";   // also .Info(), .Warn(), .Error()
  ```
- **Never** use Android logging (`android/log.h`, `__android_log_print`, `ANDROID_LOG_*`) in libosmscout
  outside its frozen `Android/` directory — no conditional or unconditional Android dependencies allowed
  there. The CI gate `Check libosmscout Android-free outside Android/` (`.github/workflows/build.yml`) fails
  the build if those patterns reappear.
- No local changes accepted in the libosmscout `Android/` dir; its `android/log.h` usage is upstream-owned
  and never compiled by the app build.
- Debug output is gated — enable with `osmscout::log.Debug(true)` if needed.

## 2. Native log integration (NaviVeylin side)

- `osmscout::log` lines are surfaced to Logcat by the **app-owned** NDK bridge:
  - C++: `app/src/main/cpp/native_log_bridge.cpp/.h` — `AndroidLogLogger` sink forwarding lines to
    `__android_log_print`
  - Kotlin: `com.naviveylin.NativeLogBridge` (guarded `System.loadLibrary("naviveylin_log_bridge")`; safe
    no-op in host unit tests)
  - Installed once in `NaviVeylinApp.onCreate` before any DB open/render/routing
- Native lines appear in Logcat under tag **`NaviVeylin`**, levels mapped `DEBUG/INFO/WARN/ERROR` →
  `D/I/W/E`
- Inspect with: `adb logcat -s NaviVeylin`
- A stylesheet load reports the type names the installed database cannot resolve as **one line per parsed
  style file** (`Unknown types in '<file>': N (<sample>, N more)`) — never one line per rule occurrence; the
  complete per-name list needs `osmscout::log.Debug(true)` (native debug is off by default). One such line
  per style file **per load** is the expected output on an install whose map data predates the stylesheet
  (`TODO.md` §91/§99) — a startup loads the set more than once (measured 8 files × 13 loads = 104 lines,
  `TODO.md` §120), so compare per file; a per-occurrence warning wall means the condensation regressed
  (`guidelines/Build.md` §10)
- The bridge links `osmscout_client_java` and is the **only** place in the native build allowed to use
  Android logging APIs — libosmscout must stay platform-independent

## 3. Kotlin (app) logging

- Use `android.util.Log` (`Log.d/i/w/e`) with per-class `TAG` constants; app diagnostics helpers live in
  `com.naviveylin.core.DiagnosticsLog` (buffered in memory and written by its own worker thread — logging
  never touches the file on the caller's thread, and readers use `readEntriesAsync`/`exportTextAsync`
  instead of reading it in a host callback or during composition)
- Kotlin logs and forwarded native logs are separate streams; native lines always come from the bridge under
  the `NaviVeylin` tag
- **Never log coordinates** (spec `auto-diagnostics` — Diagnostics carry no coordinates): a log or
  diagnostics line carries identity instead — object label/id, map database or map file name, magnification,
  screen pixel, accuracy, bearing. The build gate `checkNoCoordinatesInLogs` (buildSrc
  `CoordinateLogScanner`, in `preBuild`) fails on a new one — including a position copied into a local whose
  name the coordinate identifier list does not know (`val frameLat = viewportLat`, `val a = fix.lat`, or an
  alias of one), so renaming the value does not slip past it; the on-device file is pruned to
  `DiagnosticsLog.RETENTION_MS` (7 days) by the logging worker, and the exported text and both viewers lead
  with the `diagnostics_disclosure` statement. See `guidelines/Regulatory.md` §9 and `guidelines/UI.md`
  (gates).
