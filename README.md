 # NaviVeylin

Android navigation app built on the [libosmscout](https://github.com/Framstag/libosmscout) ecosystem.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17+ | Required by AGP 8.7+ |
| Android SDK | platform 36, build-tools 36.0.0 | At `$ANDROID_SDK_ROOT` |
| NDK | 27.0.12077973 | Side-by-side NDK |
| Gradle | 8.9 | Wrapper included |
| CMake | 3.22+ | Bundled with SDK |
| vcpkg | latest | For native C++ deps (optional) |

> **`local.properties`** points to your local SDK/NDK/Java paths.
> It is **not** checked into git — copy `local.properties.template` or let Android Studio generate it.

## Quick Start

```bash
# 1. Add libosmscout submodule + write CMakeLists.txt
./initialize.sh

# 2. Build debug APK (Kotlin/Java only, native stubs)
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Full Native Build

To include libosmscout with map rendering and routing:

```bash
# 1. Add libosmscout submodule + write CMakeLists.txt
./initialize.sh

# 2. Cross-compile native dependencies for Android
#    (reads SDK/NDK/Java paths from local.properties automatically)
./setup-vcpkg.sh

# 3. Build full APK with native .so
#    (VCPKG_ROOT is auto-set by setup-vcpkg.sh; override with env var)
./gradlew :app:assembleDebug
```

## Project Structure

```
├── app/                       # Main Android application
│   ├── src/main/
│   │   ├── cpp/               # Native C++ (libosmscout + JNI)
│   │   │   ├── CMakeLists.txt
│   │   │   ├── libosmscout/   # Git submodule
│   │   │   └── placeholder.cpp
│   │   ├── java/com/naviveylin/
│   │   │   ├── di/            # Hilt modules
│   │   │   ├── ui/            # Compose screens (map, favorites, ...)
│   │   │   ├── navigation/    # Nav graph
│   │   │   └── data/          # Room DB + repos (incl. FavoriteRepository)
│   │   ├── res/               # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── osmscout-jni/              # JNI bridge AAR (placeholder — real JNI in submodule)
├── auto/                      # Android Auto (placeholder)
├── initialize.sh              # Add submodule + write CMakeLists.txt
├── setup-vcpkg.sh             # Cross-compile native deps via vcpkg
├── AGENTS.md                  # AI agent context
├── .gitignore
├── local.properties           # Machine-specific (not checked in)
├── local.properties.template  # Template for new devs
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradle/
```

## Scripts

| Script | What it does |
|--------|-------------|
| `initialize.sh` | Add `libosmscout` submodule, write `CMakeLists.txt` |
| `setup-vcpkg.sh` | Clone vcpkg, bootstrap, install Android deps (reads env from `local.properties`), write toolchain wrapper. Uses `$VCPKG_ROOT` or `./vcpkg` |

## Build Commands

```bash
# Debug APK (Kotlin/Java only)
./gradlew :app:assembleDebug

# Release APK (requires signing config)
./gradlew :app:assembleRelease

# Play-ready release AAB (generates date-based version, bumps versionCode)
./gradlew release

# JNI bridge AAR (placeholder — real JNI in submodule)
./gradlew :osmscout-jni:assembleRelease

# Android Auto module
./gradlew :auto:assembleDebug

# Full clean
./gradlew clean
```

## Release Build

`./gradlew release` produces a signed, Play-ready Android App Bundle:

- **versionName** is generated as `<yyyy>-<MM>-<dd>-<N>` — 4-digit year, zero-padded month/day, and a running number `N` (no leading zeros) that increments per release build on the same day and resets to 1 on a new day
- **versionCode** increments by one on every release build
- Version state persists in `app/release-version.properties` (gitignored, managed by the build — first run starts at `versionCode` 20)
- Only `release` bumps the state; debug/other builds keep the fixed `1.0.0`/19 and never touch it
- Signed with `app/release.keystore` when present (otherwise the build warns and produces an unsigned AAB)
- Output: `app/build/outputs/bundle/release/app-release.aab`

The generated version is available to the app via `BuildConfig.VERSION_NAME` and shown in the About dialog (About → Version).

## Architecture

- **UI**: Jetpack Compose + Material 3
- **Navigation**: Jetpack Navigation Compose
- **DI**: Hilt
- **Persistence**: Room (app data), JSON via JNI (favorites)
- **Native**: libosmscout via NDK/CMake + JNI (Cairo rendering backend)
- **JNI bridge**: `libosmscout-client-java` (submodule) — produces `libosmscout_client_java.so`
- **Form factors**: Single `app` module, adaptive layouts (phone, foldable, tablet)
- **Android Auto**: Placeholder module, deferred

## Android Auto Diagnostics

When Android Auto fails to start (crash hint, blank screen, or silent return to launcher), gather evidence from two sources:

**1. On-device log** — `filesDir/diagnostics/app.log`, viewable in the phone app (About → Diagnostics) and on the car screen (Root → Diagnostics). Contains session lifecycle events, warmup step markers, and crash traces. Warmup markers localize native crashes: if the log ends at `Building native client` without `Native client ready`, the failure is inside native init.

**2. Phone logcat** — the app runs on the phone for phone-based Android Auto, so the phone is adb-able even though the head unit is not:

```bash
# Native crash backtraces (SIGSEGV etc.) — the Java crash handler cannot capture these
adb logcat -b crash

# Host (gearhead) decision logs + app logs
adb logcat | grep -iE "gearhead|carapp|naviveylin|DEBUG|libosmscout"
```

Correlate the two by wall-clock timestamps. A native crash shows in `-b crash` with no corresponding on-device `CRASH` entry; a host timeout shows the host's force-stop in the gearhead logs.

## Distribution

Install via APK sideloading or alternative stores (F-Droid, GitHub Releases). For Google Play upload, use `./gradlew release` to produce a signed AAB.

## License

[License information TBD]
