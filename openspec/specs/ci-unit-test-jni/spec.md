# ci-unit-test-jni Specification

## Purpose
Ensures JVM unit tests in CI can load the libosmscout JNI stub — the stub is committed to the repository, present on the unit-test `java.library.path` of every module whose tests trigger `OSMScoutClient`'s static native-library load, and available under both loader-name variants.

## Requirements

### Requirement: JNI stub committed for CI unit tests

The host-compiled JNI stub shared objects used by JVM unit tests SHALL be versioned in the repository so fresh checkouts and CI runners have them without a dev machine.

#### Scenario: Fresh checkout runs native-touching unit tests

- **WHEN** a unit test triggers `System.loadLibrary("osmscout_client_java")` on a fresh checkout (no locally built stub)
- **THEN** the library loads from the versioned test stub and the test does not fail with `UnsatisfiedLinkError`

#### Scenario: Fallback library name available

- **WHEN** the loader falls back from `osmscout_client_java` to `osmscout_client_javad`
- **THEN** the `_javad` stub exists in the same module test jniLibs and the load succeeds

### Requirement: Stub stays test-only

The JNI stubs SHALL live exclusively in test source sets (`src/test/jniLibs`), never in main sources, and SHALL NOT end up in the APK.

#### Scenario: App build excludes stubs

- **WHEN** the app APK is assembled
- **THEN** the stub `.so` files are absent from the APK native libraries

### Requirement: Stub reachable per module

Every module whose unit tests construct fake libosmscout clients (and thus initialize `OSMScoutClient`) SHALL have the stub in its own `src/test/jniLibs`, which the Android Gradle plugin adds to the unit-test `java.library.path`.

#### Scenario: App module tests

- **WHEN** `:app:testMobileDebugUnitTest` runs tests that instantiate a fake client
- **THEN** the stub resolves from `app/src/test/jniLibs` and tests pass

#### Scenario: Auto module tests

- **WHEN** `:auto:testDebugUnitTest` runs `AutoMapRendererTest` (constructs `FakeAutoRenderClient`, a subclass of `OSMScoutClient`)
- **THEN** the stub resolves from `auto/src/test/jniLibs` and the tests pass
