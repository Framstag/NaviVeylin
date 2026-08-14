## 1. Project Scaffold

- [x] 1.1 Create `NaviVeylin/` project directory with Gradle wrapper (8.x+)
- [x] 1.2 Create `settings.gradle.kts` with `:app`, `:osmscout-jni`, `:auto` module includes
- [x] 1.3 Create root `build.gradle.kts` with AGP, Kotlin, Hilt, and Room plugin declarations
- [x] 1.4 Create `gradle.properties` with AndroidX, non-transitive R classes, JVM target config
- [x] 1.5 Create `local.properties` with `sdk.dir` and `ndk.dir` placeholders

## 2. App Module (`:app`)

- [x] 2.1 Create `app/build.gradle.kts` with AGP, Compose, Navigation, Hilt, Room, CMake dependencies
- [x] 2.2 Create `app/src/main/AndroidManifest.xml` with internet permission, app entry Activity
- [x] 2.3 Create `app/src/main/java/com/naviveylin/` package structure (di, ui, data, native)
- [x] 2.4 Create `NaviVeylinApp.kt` — Application class with `@HiltAndroidApp`
- [x] 2.5 Create `MainActivity.kt` — single Activity with `setContent` + Compose NavHost
- [x] 2.6 Create `MainScreen.kt` — top-level Compose screen with Navigation scaffold
- [x] 2.7 Create `app/src/main/res/values/` with themes, strings, colors (Material 3)
- [x] 2.8 Configure `app/build.gradle.kts` with `ndk { abiFilters "arm64-v8a", "armeabi-v7a", "x86_64" }`
- [x] 2.9 Verify `./gradlew :app:assembleDebug` produces a valid APK

## 3. Jetpack Architecture

- [x] 3.1 Create Hilt DI modules (`AppModule.kt`, `NativeModule.kt`, `DatabaseModule.kt`)
- [ ] 3.2 Create Room database class with entities for map metadata, favorites, search history
- [ ] 3.3 Create DAO interfaces for each Room entity
- [ ] 3.4 Create `AppViewModel.kt` base ViewModel with coroutine scope and Hilt injection
- [ ] 3.5 Create Navigation graph (`NavGraph.kt`) with route definitions
- [ ] 3.6 Add Compose adaptive support — `WindowSizeClass` detection in `MainActivity`
- [ ] 3.7 Create tablet layout composables for `sw600dp` and `sw840dp` width buckets

## 4. Native C++ Integration (`osmscout-native`)

- [x] 4.1 Add `libosmscout-client` as Git submodule from https://github.com/Framstag/libosmscout
- [x] 4.2 Create `app/src/main/cpp/CMakeLists.txt` with `add_subdirectory(libosmscout-client)`
- [x] 4.3 Configure `app/build.gradle.kts` `externalNativeBuild` block pointing to CMakeLists.txt
- [x] 4.4 Add `cmake_minimum_required(VERSION 3.22.1)` and Android toolchain config
- [x] 4.5 Cross-compile transitive C++ deps (protobuf, libmarisa, libxml2) in CMake
- [x] 4.6 Verify `./gradlew assembleDebug` produces `.so` files for all 3 ABIs

### Notes
- Core libs (OSMScout, OSMScoutMap, OSMScoutClient) built from source via `add_subdirectory(libosmscout)`
- Cairo backend (OSMScoutMapCairo) links against vcpkg-installed cairo + deps
- CMakeLists.txt uses ABI-aware vcpkg triplet selection: `arm64-v8a`→`arm64-android`, `armeabi-v7a`→`arm-neon-android`, `x86_64`→`x64-android`
- vcpkg overlay triplet at `vcpkg-overlays/triplets/arm64-android.cmake` uses API 26 (matching Gradle minSdk)
- iconv stub for Android API < 28

## 5. JNI Bridge (`:osmscout-jni`)

- [x] 5.1 Create `osmscout-jni/build.gradle.kts` as Android Library module
- [x] 5.2 Create JNI entry point class `OsmscoutClient.kt` with `System.loadLibrary()`
- [ ] 5.3 Create C++ JNI wrapper functions in `app/src/main/cpp/jni/` matching Java native declarations
- [ ] 5.4 Implement JNI bridge for map loading (init, open, close)
- [ ] 5.5 Implement JNI bridge for coordinate queries (lat/lon lookups)
- [ ] 5.6 Implement JNI bridge for routing (waypoint input, route calculation)
- [ ] 5.7 Add C++ exception → Java exception conversion in JNI layer
- [x] 5.8 Wire `:app` to depend on `:osmscout-jni` project
- [ ] 5.9 Verify `./gradlew :osmscout-jni:assembleRelease` produces AAR with native libs

### Notes
- Upstream `libosmscout-client-java` submodule provides the complete JNI bridge
- `:osmscout-jni` module was removed — upstream Java classes used directly
- `OSMScoutClient.java` provides: openDatabase, render, searchLocations, calculateRouteAsync, startNavigation, etc.
- `MapDownloadManager.java` removed (uses `java.net.http` not available on Android)
- `NativeModule.kt` updated to use `OSMScoutClientBuilder`

## 6. Android Auto Placeholder (`:auto`)

- [x] 6.1 Create `auto/build.gradle.kts` with `android.car` dependency
- [x] 6.2 Create `auto/src/main/AndroidManifest.xml` with CarAppService intent filter
- [x] 6.3 Create `NaviVeylinCarAppService.kt` — empty `CarAppService` stub
- [x] 6.4 Verify `./gradlew :auto:assembleDebug` compiles successfully

## 7. Build & Distribution

- [x] 7.1 Create `app/build.gradle.kts` signing config for debug keystore
- [x] 7.2 Add `buildTypes { release { ... } }` with minification (R8/ProGuard) rules
- [x] 7.3 Verify `./gradlew assembleRelease` produces a signed release APK
- [x] 7.4 Document build and install instructions in `README.md`
