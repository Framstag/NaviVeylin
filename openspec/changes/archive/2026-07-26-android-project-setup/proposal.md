## What Changes

Create a new Android Navigation App project named **NaviVeylin** in the current directory. The project will be a modern Android application using Jetpack components, with native C++ integration via JNI for map rendering and routing capabilities from the libosmscout ecosystem.

The initial setup includes:
- Standard Android application module with Gradle build system
- Integration of `libosmscout-client` C++ library via Android NDK
- Java/JNI bridge library `libosmscout-client-java` wrapping the C++ layer
- Foundation for future Android Auto support (planned for later versions)
- Modern Android architecture using Jetpack libraries

## Capabilities

### New Capabilities

- `app`: Main Android application module with Gradle build, Jetpack components (Navigation, Lifecycle, ViewModel, Room, Hilt), manifest, resources, and NDK/CMake integration entry point
- `osmscout-native`: C++ `libosmscout-client` library integrated via Git submodule and Android NDK CMake build, producing native `.so` libraries for target ABIs
- `osmscout-jni`: Java/JNI wrapper library `libosmscout-client-java` providing Java-accessible APIs over the native C++ layer
- `auto`: Android Auto support — manifest declarations, car app module, and templates (implementation deferred to later change)

### Modified Capabilities

None — this is a new project with no existing capabilities.

## Impact

- **New project root**: `NaviVeylin/` directory created in the current working directory
- **Build system**: Gradle (Kotlin DSL) with AGP (Android Gradle Plugin), CMake for native code
- **Dependencies**:
  - External: `libosmscout-client` (C++) from https://github.com/Framstag/libosmscout
  - External: `libosmscout-client-java` (Java/JNI) from same repository
  - Android Jetpack libraries (androidx.*)
- **NDK/ABI targets**: arm64-v8a, armeabi-v7a, x86_64 (for emulator)
- **No Google services** initially — no Google Maps, no Play Services dependency; app will be distributed outside Play Store
