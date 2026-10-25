# Native C++ Integration (osmscout-native)

## Purpose

C++ `libosmscout-client` library integrated via Git submodule and Android NDK CMake build, producing native `.so` libraries for target ABIs.

## Requirements

### Requirement: Git submodule for libosmscout-client
The system SHALL include `libosmscout-client` as a Git submodule from https://github.com/Framstag/libosmscout.

#### Scenario: Submodule initialized
- **WHEN** developer runs `git submodule update --init --recursive`
- **THEN** `libosmscout-client` source is available at the expected path under `app/src/main/cpp/`

### Requirement: CMake build via Android NDK
The system SHALL build `libosmscout-client` using CMake with the Android NDK, targeting arm64-v8a, armeabi-v7a, and x86_64 ABIs.

#### Scenario: NDK CMake produces .so files
- **WHEN** developer runs `./gradlew assembleDebug`
- **THEN** CMake compiles `libosmscout-client` and produces `.so` files for all three target ABIs

### Requirement: External dependency cross-compilation
The system SHALL cross-compile all transitive C++ dependencies of `libosmscout-client` (e.g., protobuf, libxml2, marisa-trie) for Android as part of the CMake build. The marisa-trie dependency SHALL be installed for all three Android triplets so that the core library compiles with full-text search support.

#### Scenario: Dependencies compile for arm64-v8a
- **WHEN** NDK CMake build runs for arm64-v8a
- **THEN** all required C++ dependencies, including marisa-trie, compile successfully for that ABI

#### Scenario: Marisa available in CMake build
- **WHEN** CMake configures the native build
- **THEN** the marisa library and headers are found
- **AND** the core library is built with `OSMSCOUT_HAVE_LIB_MARISA` defined

#### Scenario: Full-text search compiled into JNI library
- **WHEN** the native build produces `libosmscout_client_java.so`
- **THEN** the shared library contains the full-text search code path
- **AND** it links against the marisa library

#### Scenario: All three ABIs build
- **WHEN** `./gradlew :app:assembleDebug` runs
- **THEN** the build succeeds for arm64-v8a, armeabi-v7a, and x86_64
- **AND** each ABI's `libosmscout_client_java.so` links marisa successfully

### Requirement: CMakeLists.txt integration
The system SHALL provide a top-level `CMakeLists.txt` in the native source directory that includes `libosmscout-client` and exposes the JNI entry point.

#### Scenario: CMake configuration succeeds
- **WHEN** Gradle triggers `externalNativeBuild`
- **THEN** CMake configuration completes without errors

### Requirement: Configured feature headers live in the build tree
The native build SHALL render libosmscout's four `*Features.h` into the app's CMake build tree and prepend each
module's include root to the include path, so those libraries compile with this configuration's values, and no
generated feature header exists below `app/src/main/cpp/libosmscout/`.

#### Scenario: The configure leaves no generated feature header in the submodule source tree
- **WHEN** `./gradlew :app:assembleMobileDebug` configures the native build
- **THEN** the four headers exist under `osmscout-features/` in the app's CMake build tree (`OSMSCOUT_FEATURES_DIR`)
- **AND** no generated `*Features.h` exists below `app/src/main/cpp/libosmscout/` (the two tracked copies under `Android/OsmScoutLib/jni/include/config/` are not generated — no template sits beside them)

#### Scenario: A generated feature header in the source tree is removed by the build gate
- **WHEN** a generated `*Features.h` is present in the submodule source tree as `preBuild` runs
- **THEN** `:app:checkSubmoduleFeatureHeaders` removes it and fails, naming the path and §202
- **AND** the following build finds none there and succeeds

#### Scenario: A host build in the same working tree measures its own feature set
- **WHEN** a working tree that also ran an Android configure is built for the host with `-DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON` and the case "Cairo measures a configured font file as the face of that file" runs
- **THEN** the case passes — 7 cases, 167 assertions — because `libosmscout-map` was compiled with that build's `MapFeatures.h`
