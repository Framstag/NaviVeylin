# AGENTS.md — AI Agent Context for NaviVeylin

This file helps AI coding agents understand the project structure, conventions, and constraints.

## Project Overview

NaviVeylin is an Android navigation app using libosmscout for map rendering and routing. It targets phone, foldable, and tablet form factors with a single `app` module. Android Auto support is planned but deferred.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin, Java, C++20 (NDK) |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Persistence | Room |
| Navigation | Jetpack Navigation Compose |
| Native | libosmscout via NDK/CMake + JNI (Cairo rendering backend) |
| Build | Gradle (Kotlin DSL), AGP 8.7+ |
| Native build | CMake 3.22+, NDK 27 |
| ABI targets | arm64-v8a, armeabi-v7a, x86_64 |
| Min SDK | 26 |
| Target SDK | 36 |

## Module Structure

```
:app              → Main app (phone, foldable, tablet)
:osmscout-jni     → JNI bridge AAR (placeholder — real JNI in libosmscout-client-java submodule)
:auto             → Android Auto placeholder (deferred)
```

## Key Conventions

### Code Style
- Kotlin: official style (per `gradle.properties`)
- C++: follow libosmscout conventions
- Package: `com.naviveylin.*`

### Architecture
- Single Activity (`MainActivity`), Compose-based UI
- Hilt for DI, ViewModel + StateFlow for state
- Room for local persistence
- `FavoriteRepository` wraps JNI CRUD for favorites, exposes `StateFlow`
- Native calls go through `libosmscout-client-java` JNI bridge (submodule)

### Native Integration
- C++ source: `app/src/main/cpp/`
- JNI bridge: `libosmscout/libosmscout-client-java/` (submodule)
  - Produces `libosmscout_client_java.so` + `libosmscoutclientjava.jar`
  - Target: `osmscout_client_java`
  - Depends on: `OSMScout::OSMScout`, `OSMScout::Map`, `OSMScout::MapCairo`, `OSMScout::Client`
- CMake builds all native code
- vcpkg for cross-compiling C++ dependencies (cairo, pango, harfbuzz, fribidi, protobuf, ...)
  - Location: `$VCPKG_ROOT` env var or `./vcpkg`
  - SDK/NDK/Java paths read from `local.properties` automatically
- Cairo rendering backend (not OpenGL — better quality for map rendering)

### Stylesheets

- The libosmscout submodule `stylesheets/` dir (`app/src/main/cpp/libosmscout/stylesheets/`) is the **single source of truth** for map style sheets
- `app/build.gradle.kts` copies it into `build/generated/assets/stylesheets` at build time (`syncSubmoduleStylesheets` task, wired into `preBuild` and all `merge*Assets` tasks) — there is **no committed snapshot** in `app/src/main/assets/`
- A submodule bump automatically changes the stylesheet content of the next APK; no manual sync step
- `checkSubmoduleStylesheets` (preBuild) fails the build with an actionable message if the submodule is not initialized (fresh clone: `git submodule update --init --recursive`)
- `AssetCopier` refreshes the on-device copy from the APK on every app start (per-file size+SHA-256 compare, deletes stale files), so existing installs get new styles after an update without clearing data

### Android Auto
- `:auto` module exists as placeholder
- `NaviVeylinCarAppService` stub throws `UnsupportedOperationException`
- Real implementation deferred to later change

## OpenSpec Workflow

This project uses OpenSpec with the `spec-driven` schema:

```
proposal → specs → design → tasks → apply
```

Change artifacts live in `openspec/changes/<change-name>/`.
Config: `openspec/config.yaml`

## Build & Test

```bash
# Build debug APK (all 3 ABIs: arm64-v8a, armeabi-v7a, x86_64)
./gradlew :app:assembleDebug

# Build for specific ABI only (faster iteration)
./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a

# Build Play-ready release AAB (bumps version state, all 3 ABIs)
./gradlew release

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest
```

### Release versioning
- `./gradlew release` generates `versionName` as `<yyyy>-<MM>-<dd>-<N>` (4-digit year, zero-padded month/day, running number `N` without leading zeros), increments `versionCode` by one, then runs `:app:bundleRelease`
- Version state lives in `app/release-version.properties` (**gitignored**): `lastDate`, `runningNumber`, `versionCode`. Same day → `N+1`; new day → `N` resets to 1; `versionCode` starts at 20 (migrated from the old hardcoded 19)
- The bump happens at configuration time, gated on the `release` task being requested — every other build (`assembleDebug`, etc.) uses the fixed fallback `1.0.0`/`19` and never touches the state file
- Direct `bundleRelease` without `release` reuses the last persisted values; only `release` bumps (single release machine assumed)
- `buildConfig = true`; app code reads the version via `BuildConfig.VERSION_NAME` (used by `AboutDialog`)
- Signing unchanged: `app/release.keystore` present → signed AAB; absent → warning logged, unsigned AAB still produced
- Output: `app/build/outputs/bundle/release/app-release.aab`
- Unit tests cover the dialog display (see `AboutDialogComposeTest.kt`); the date-format logic is inline in the Gradle DSL and verified behaviorally (run `release` twice on the same day)

### JNI stub for unit tests
`app/src/test/jniLibs/` contains a tiny host-compiled stub (ELF, no symbols,
named both `libosmscout_client_java.so` and `libosmscout_client_javad.so`)
so `OSMScoutClient`'s static `System.loadLibrary` succeeds in JVM/Robolectric
unit tests — the Android .so cannot load on the host JVM. The `_javad` variant
is the fallback name the loader tries second; without it full-suite runs can
fail flakily. Tests override native methods via fakes (see
`app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt`).
Keep the stub only in the test source set; never use it in the app.

**Classloader rule**: any test class that instantiates `FakeOSMScoutClient`
(or otherwise triggers `OSMScoutClient`'s static `System.loadLibrary`) MUST run
under `@RunWith(RobolectricTestRunner::class)` with the DEFAULT sandbox config —
do NOT set `@Config(sdk=...)` or `@GraphicsMode(...)` on such classes, since a
different sandbox gets its own classloader and the stub .so can only load in
one. A plain-JUnit class loading the stub first binds it to the system
classloader and breaks every Robolectric sandbox class in the same JVM with
"already loaded in another classloader" — full-suite runs then fail
systematically. Compose UI tests for the route panel live in
`app/src/test/java/com/naviveylin/ui/route/RoutePanelComposeTest.kt`.

## Native Build Details

### Architecture
- Core libosmscout libs (OSMScout, OSMScoutMap, OSMScoutClient) built from source via `add_subdirectory(libosmscout)`
- Cairo backend (OSMScoutMapCairo) links against vcpkg-installed cairo + deps
- CMakeLists.txt uses ABI-aware vcpkg triplet selection:
  - `arm64-v8a` → `arm64-android`
  - `armeabi-v7a` → `arm-neon-android`
  - `x86_64` → `x64-android`
- vcpkg overlay triplet at `vcpkg-overlays/triplets/arm64-android.cmake` uses API 26
- iconv stub for Android API < 28

### vcpkg Dependencies
- Cairo, pixman, fontconfig, freetype, libpng, expat, brotli, bzip2
- Pango, harfbuzz, fribidi, glib, libffi, pcre2, gettext, libiconv, libuuid, pthreads
- zlib, libxml2, protobuf, abseil
- All installed for 3 Android triplets: arm64-android, arm-neon-android, x64-android

### Rebuilding vcpkg packages
```bash
# Force rebuild of specific package
rm -rf vcpkg/buildtrees/<package>
./setup-vcpkg.sh
```

### vcpkg usage pattern (CI)

- **Classic mode, no manifest**: the dependency list is hardcoded in `setup-vcpkg.sh` (`DEPS=(...)`), not in a `vcpkg.json` manifest. Overlay ports (`vcpkg-overlays/`, e.g. marisa-trie) and overlay triplets are passed via `--overlay-ports`/`--overlay-triplets`.
- **CI pins vcpkg**: `.github/workflows/build.yml` sets `VCPKG_COMMIT` to a specific commit and fetches it shallowly. Pinning keeps the binary-cache ABI hash stable — an unpinned daily clone would miss the cache every run.
- **CI uses the binary cache**: `VCPKG_BINARY_SOURCES=files,<dir>,readwrite` stores compiled packages (~0.5–1 GB) instead of caching the full vcpkg tree (4–7 GB with buildtrees/installed). The cache key is `VCPKG_COMMIT` + hash of `setup-vcpkg.sh` + `vcpkg-overlays/**`.
- **Adding/removing a dependency**: edit the `DEPS` list in `setup-vcpkg.sh` — the CI cache key changes automatically (script is hashed). For an unregistered port, add an overlay port under `vcpkg-overlays/`.
- **Refreshing ports in CI**: bump `VCPKG_COMMIT` in the workflow — the cache key changes and packages rebuild.
- **`setup-vcpkg.sh` tolerates install failures** (`|| echo`), so CI runs an explicit `.pc` verification gate after it (see `Verify vcpkg packages` step).

## Common Patterns

### Adding a new dependency
1. Add to `app/build.gradle.kts` (or module's build file)
2. If native, add to `CMakeLists.txt` and vcpkg

### Adding a new screen
1. Define route in `NavGraph.kt`
2. Create composable in `ui/` package
3. Add ViewModel in `ui/` or `data/` package
4. Register Hilt module if needed

### Adding a full-screen sheet (e.g., FavoritesSheet)
1. Create composable in `ui/<feature>/` package
2. Create `@HiltViewModel` in same package
3. Wire into `MapCanvasScreen` via boolean state flag + conditional composition
4. No nav graph changes needed — sheet lives on top of map

### Adding a details sheet after search
1. Create composable using `ModalBottomSheet`
2. Add `showDetailsSheet` flag to `MapCanvasUiState`
3. Wire `onResultSelected` to set flag + update center
4. Sheet provides fav action via `FavoriteRepository`

### Adding a native function
1. Add Java `native` method in `OSMScoutClient.java` (in libosmscout-client-java submodule)
2. Implement JNI wrapper in C++ (`libosmscout-client-java/src/`)
3. Add CMake target link if new library

## Constraints

- No Google Play Services
- No Google Maps
- No Google account required
- App distributed outside Play Store; `./gradlew release` also produces an AAB suitable for Google Play upload
- All map rendering from libosmscout native code
