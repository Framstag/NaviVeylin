# Design: remove-android-logging-from-libosmscout

## Context

The libosmscout submodule's `naviveylin-local` branch already logs through the platform-independent `osmscout::log` API (commit `4a6dfde7c`). The only remaining Android dependency outside the frozen `Android/` directory is a dead conditional link:

- `libosmscout-client-java/CMakeLists.txt` links `liblog` inside `if(ANDROID)` — nothing calls `__android_log_print` anymore, so the block exists solely because it used to be needed.

`osmscout::log` (from `libosmscout/include/osmscout/log/Logger.h`) supports exchanging the global logger via `Log::SetLogger(std::shared_ptr<Logger>)`; a custom `Logger::Destination` receives `Print(...)` + `PrintLn()` calls and can forward to any sink. The default console logger writes to `std::cerr`, which is invisible in Logcat on Android.

The app module owns `app/src/main/cpp/` (built only for Android, never for host CI) and currently contains only `placeholder.cpp`.

## Goals / Non-Goals

**Goals:**
- Zero Android-specific references in libosmscout outside `Android/` (conditional or unconditional).
- Native `osmscout::log` output visible in Android Logcat via a NaviVeylin-owned bridge.
- `Android/` directory stays untouched (frozen, upstream-owned).
- Keep JNI logging on `osmscout::log` — no new logging API in libosmscout.

**Non-Goals:**
- Changing libosmscout `Android/` directory (no local changes accepted).
- Changing upstream logging internals of libosmscout.
- Porting Logcat output to host CI builds (Linux `std::cerr` is fine there).

## Decisions

### D1: Remove the dead `if(ANDROID)` log link in client-java

Delete the `if(ANDROID) ... target_link_libraries(log)` block from `libosmscout-client-java/CMakeLists.txt`. Rationale: nothing references `__android_log_print` in the branch; the link is a vestigial conditional Android dependency and violates the platform-independence rule. Host CI builds never used it; removing it changes nothing there. Alternative (guarding the link with a comment) rejected — it keeps a dependency that must not exist.

### D2: App-owned native bridge installs an Android log sink

Add `app/src/main/cpp/native_log_bridge.cpp` (+ header) to the app module:
- Subclasses `osmscout::Logger` with an `AndroidLogDestination : osmscout::Logger::Destination` whose `Print*`/`PrintLn()` implementations buffer a line and flush to `__android_log_print` (tag `NaviVeylin`, level mapped from the `osmscout::Logger::Level` of the owning logger).
- Exposes one JNI entry point, e.g. `Java_com_naviveylin_NativeLogBridge_install(JNIEnv*, jobject)`, calling `osmscout::log.SetLogger(...)`.
- The app's native lib links `osmscout_client_java` (same CMake tree via the existing `add_subdirectory(libosmscout)`), so it can call the exported `osmscout::log` symbol.

Rationale vs. alternatives:
- Alternative A — per-line JNI callback to Kotlin `Log.d`: adds a JNI marshaling layer for every log line, plus a new libosmscout API. Rejected: more churn, more overhead.
- Alternative B — do the `SetLogger` from inside client-java JNI: would reintroduce `android/log.h` into libosmscout (outside `Android/`). Rejected outright: violates the rule.
- Alternative C — rely on `JNI_OnLoad` in the bridge .so: implicit, hard to test. Explicit `install()` call from Kotlin chosen.

### D3: Install at app startup, before any native work

Call `NativeLogBridge.install()` from the app's Application/`MainActivity` onCreate, after the client-java .so is loaded and before any DB open/render. Rationale: the logger is process-global; installing early guarantees no native log lines are lost. Thread-safety of `osmscout::log` is unchanged from today (single install at startup, then read-only).

### D4: CI regression gate

Add a step to `.github/workflows/build.yml` that fails if `android/log.h`, `__android_log_*`, or `ANDROID_LOG_*` appear in any libosmscout file outside `libosmscout/Android/`. Rationale: enforces the freeze + platform-independence permanently, not just in this change. Cheap grep, no build cost.

## Risks / Trade-offs

- **Race with early native logging**: lines logged before `install()` stay invisible. Mitigated by startup install; acceptable — pre-change they were invisible forever.
- **Load-order dependency**: app bridge .so links `libosmscout_client_java.so`; Android resolves at runtime, so client .so must load first (`System.loadLibrary` order in the app). Enforced in Kotlin; the existing single-load pattern in `OSMScoutClient` already guarantees this before any bridge call.
- **Log interleaving**: concurrent native threads writing through one destination interleave lines (upstream console logger had the same property). No regression.
- **Submodule bump risk**: a future upstream merge could reintroduce Android refs outside `Android/`; D4's CI gate catches it at merge time.
