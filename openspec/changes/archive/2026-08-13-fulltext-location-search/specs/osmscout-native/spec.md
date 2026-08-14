## MODIFIED Requirements

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
