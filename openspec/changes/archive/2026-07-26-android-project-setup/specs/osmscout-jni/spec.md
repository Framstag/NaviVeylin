## ADDED Requirements

### Requirement: Android Library module
The system SHALL provide `libosmscout-client-java` as an Android Library module (`:osmscout-jni`) producing an AAR.

#### Scenario: AAR build succeeds
- **WHEN** developer runs `./gradlew :osmscout-jni:assembleRelease`
- **THEN** an AAR file is produced containing compiled Java classes and native `.so` files

### Requirement: JNI bridge to native library
The system SHALL expose a Java API that wraps `libosmscout-client` C++ functions via JNI, covering map loading, coordinate queries, and routing.

#### Scenario: Native function called from Java
- **WHEN** Java code calls a JNI bridge method
- **THEN** the corresponding C++ function in `libosmscout-client` executes and returns results to Java

### Requirement: Native library loading
The system SHALL load the native `.so` library in a `JNI_OnLoad` or static initializer before any JNI calls are made.

#### Scenario: Library loads on app start
- **WHEN** app starts
- **THEN** `System.loadLibrary("osmscout-client")` succeeds and JNI functions are available

### Requirement: Error handling across JNI boundary
The system SHALL handle C++ exceptions in the JNI layer and convert them to Java exceptions.

#### Scenario: C++ exception becomes Java exception
- **WHEN** a C++ function throws an exception
- **THEN** the JNI bridge catches it and throws a corresponding Java exception
