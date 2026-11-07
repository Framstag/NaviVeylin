# Logging recipes — the commands and the configuration the logging rules are read against

Procedure for `guidelines/Logging.md`. Each anchor names a section of that document; the rules stay there.

## §1 Native code (libosmscout submodule)

- The `osmscout::log` API is platform-independent: `#include <osmscout/log/Logger.h>`, then
  `osmscout::log.Debug() << "...";` (also `.Info()`, `.Warn()`, `.Error()`).
- Native debug is off by default; enable it with `osmscout::log.Debug(true)`.
- The native coordinate rule is enforced by `checkNoCoordinatesInLogs` (buildSrc
  `NativeCoordinateLogScanner`). It scans `app/src/main/cpp/*.cpp|h` and the JNI translation unit
  `OSMScoutClient.cpp`, and fails on an `osmscout::log` statement that streams a position accessor.
  Provenance: the gate and this scan were added by the change `fix-native-coordinate-log-gate`
  (issue #125).

## §2 Native log integration (NaviVeylin side)

- The app-owned NDK bridge surfaces `osmscout::log` lines to Logcat:
  - C++: `app/src/main/cpp/native_log_bridge.cpp/.h` — the `AndroidLogLogger` sink forwards lines to
    `__android_log_print`
  - Kotlin: `com.naviveylin.NativeLogBridge` (guarded `System.loadLibrary("naviveylin_log_bridge")`; safe
    no-op in host unit tests)
  - Installed once in `NaviVeylinApp.onCreate` before any DB open/render/routing
- Native lines appear under tag `NaviVeylin`, the levels mapped `DEBUG/INFO/WARN/ERROR` → `D/I/W/E`.

Read them on a device with:

```bash
adb logcat -s NaviVeylin
```

- A stylesheet load reports the type names the installed database cannot resolve as one line per parsed
  style file per load. The complete per-name list needs `osmscout::log.Debug(true)`. One such line per
  style file per load is the expected output on an install whose map data predates the stylesheet — a
  startup loads the set more than once, so compare per file; a per-occurrence warning wall means the
  condensation regressed.
- The search request line is written once per request at `Info`, so
  `adb logcat -s NaviVeylin` reads it without enabling native debug.
- The bridge links `osmscout_client_java`.

## §3 Kotlin (app) logging

- The build gate `checkNoCoordinatesInLogs` (buildSrc `CoordinateLogScanner`, in `preBuild`) rejects a
  position copied into a local whose name the coordinate identifier list does not know
  (`val frameLat = viewportLat`, `val a = fix.lat`, or an alias of one).
- The on-device diagnostics file is pruned to `DiagnosticsLog.RETENTION_MS` (7 days) by the logging
  worker; the exported text and both viewers lead with the `diagnostics_disclosure` statement.
- Readers use `readEntriesAsync`/`exportTextAsync` rather than reading the file in a host callback or
  during composition.
