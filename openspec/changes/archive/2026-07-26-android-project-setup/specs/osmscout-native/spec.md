## ADDED Requirements

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
The system SHALL cross-compile all transitive C++ dependencies of `libosmscout-client` (e.g., protobuf, libmarisa, libxml2) for Android as part of the CMake build.

#### Scenario: Dependencies compile for arm64-v8a
- **WHEN** NDK CMake build runs for arm64-v8a
- **THEN** all required C++ dependencies compile successfully for that ABI

### Requirement: CMakeLists.txt integration
The system SHALL provide a top-level `CMakeLists.txt` in the native source directory that includes `libosmscout-client` and exposes the JNI entry point.

#### Scenario: CMake configuration succeeds
- **WHEN** Gradle triggers `externalNativeBuild`
- **THEN** CMake configuration completes without errors
