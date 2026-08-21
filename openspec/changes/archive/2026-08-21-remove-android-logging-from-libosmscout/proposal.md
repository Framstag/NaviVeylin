# Proposal: remove-android-logging-from-libosmscout

## Why

The `naviveylin-local` branch of the libosmscout submodule must stay platform-independent outside its `Android/` directory. One conditional Android dependency (a dead `liblog` link in `libosmscout-client-java/CMakeLists.txt`) remains there, and native `osmscout::log` output is currently invisible on Android because the console logger writes to `std::cerr`, which never reaches Logcat. Native logging must be surfaced by NaviVeylin itself.

## What Changes

- **Remove** the `if(ANDROID)` → `target_link_libraries(log)` block from `libosmscout-client-java/CMakeLists.txt`. Dead code: nothing in the branch calls `__android_log_print` anymore, so the `liblog` link is an unconditional leftover that only exists on Android.
- **Audit** the whole libosmscout submodule and guarantee zero Android-specific references (`#include <android/log.h>`, `__android_log_*`, `ANDROID_LOG_*`, Android-only CMake conditionals) in any directory except `Android/`.
- **Freeze `Android/`**: no local changes accepted in the libosmscout `Android/` directory. Its existing `android/log.h` usage is upstream-owned, not compiled by the app build (root `CMakeLists.txt` never `add_subdirectory(Android)`), and stays untouched.
- **Surface native logs in NaviVeylin**: add a small app-owned NDK bridge (in `app/src/main/cpp/`, outside the libosmscout submodule) that installs a custom `osmscout::log` `Destination` forwarding native log lines to Android Logcat. NaviVeylin's own code may use Android APIs; libosmscout may not.
- JNI native logging stays on the platform-independent `osmscout::log` API (already done in commit `4a6dfde7c`).

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**: none.

No spec-level behavior changes: log output still exists, only its sink moves from "nowhere on Android" to "Logcat via NaviVeylin's own bridge". No existing spec in `openspec/specs/` requires logging behavior or Android platform-independence. The change sets `skip_specs: true` in `.openspec.yaml` — do not invent a requirement to satisfy validation.

## Impact

- `libosmscout` submodule (`naviveylin-local` branch): `libosmscout-client-java/CMakeLists.txt` — remove `if(ANDROID)` log link (1 file).
- App module: small native source + CMake wiring in `app/src/main/cpp/` for the log-forwarding bridge.
- No ABI changes, no vcpkg dependency changes, no Java/Kotlin API changes.
- Host CI builds unaffected (the `log` link was never applied to non-Android builds).
