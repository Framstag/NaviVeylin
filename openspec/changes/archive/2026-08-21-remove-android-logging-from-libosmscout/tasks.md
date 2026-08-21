## 1. Remove Android dependency from libosmscout client-java

- [x] 1.1 Remove the `if(ANDROID)` → `target_link_libraries(log)` block from `libosmscout-client-java/CMakeLists.txt` in the libosmscout submodule (`naviveylin-local` branch). Verify: `git diff` shows only that block removed; `grep -n "ANDROID" libosmscout-client-java/CMakeLists.txt` returns no matches.
- [x] 1.2 Commit the submodule change and bump the submodule pointer in the main repo (`git submodule update`; new commit in NaviVeylin). Verify: `git submodule status` shows the new submodule commit; `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` still links `libosmscout_client_java.so` successfully.

  Discovery during implementation: Android NDK build was broken by pre-existing `reinterpret_cast<void**>` on `AttachCurrentThread` (host JDK jni.h uses `void**`, NDK uses `JNIEnv**`). Fixed with portable member-function-pointer helper in `OSMScoutClient.cpp` (submodule commit `6a7f7d852`); arm64 build now passes.

## 2. Audit libosmscout for remaining Android references

- [x] 2.1 Grep the whole submodule tree for `android/log.h`, `__android_log_*`, `ANDROID_LOG_*`, and Android-only CMake conditionals, excluding `libosmscout/Android/`. Verify: zero logging matches outside `Android/`; the only `android/log.h` users live under `libosmscout/Android/` (frozen, untouched). Note: pre-existing upstream `#ifdef __ANDROID__` in `OSMScout2/src/Theme.cpp` (Qt desktop tool, font-size branch, not built, not logging) stays untouched.
- [x] 2.2 Add a CI gate to `.github/workflows/build.yml` that fails the build if any of those patterns appear in libosmscout files outside `libosmscout/Android/`. Verify: gate passes on current tree (run the same grep command from 2.1 locally); a deliberately injected `android/log.h` include in `libosmscout-client-java` fails the gate.

## 3. Build NaviVeylin-side log bridge

- [x] 3.1 Add `app/src/main/cpp/native_log_bridge.cpp` (+ `.h`): an `osmscout::Logger` subclass whose `Destination` buffers a line and flushes to `__android_log_print` (tag `NaviVeylin`, levels mapped DEBUG/INFO/WARN/ERROR), plus a JNI entry `Java_com_naviveylin_NativeLogBridge_install` calling `osmscout::log.SetLogger(...)`. Verify: file compiles in the app NDK build (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`).
- [x] 3.2 Wire the bridge into `app/src/main/cpp/CMakeLists.txt`: `add_library` + `target_link_libraries(... osmscout_client_java)` + `target_include_directories` for `osmscout/log/Logger.h`. Verify: CMake configure logs the new target; no link errors on all three ABIs (`assembleDebug` full run).

## 4. Install sink at app startup

- [x] 4.1 Add `NativeLogBridge` Kotlin wrapper class (package `com.naviveylin`) declaring the `install()` native method; call `System.loadLibrary` for the bridge lib after client-java loads. Verify: JNI signature matches the C++ entry; a debug build boots without `UnsatisfiedLinkError`.
- [x] 4.2 Call `NativeLogBridge.install()` from Application/`MainActivity.onCreate`, before DB open / first render. Verify: with `adb logcat -s NaviVeylin`, native `osmscout::log` lines (e.g. JNI `[JNI]` debug/warn messages) appear after app start; previously they were absent.

  Note: install is wired into `NaviVeylinApp.onCreate` (Hilt `@HiltAndroidApp`); `install()` is a guarded no-op when the bridge .so is unavailable (host unit tests).

## 5. Regression check

- [x] 5.1 Run `./gradlew test` (host unit tests, JNI stub) — verify all pass unchanged; the stub in `app/src/test/jniLibs/` needs no modification.
- [x] 5.2 Full ABI build `./gradlew :app:assembleDebug` — verify APK builds for arm64-v8a, armeabi-v7a, x86_64 and installs.
