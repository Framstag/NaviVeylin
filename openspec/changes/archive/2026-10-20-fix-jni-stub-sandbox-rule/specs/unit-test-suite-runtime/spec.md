# Spec Delta — unit-test-suite-runtime

## ADDED Requirements

### Requirement: A test class that loads the JNI stub declares no sandbox pin

A test source file in a module that ships the host JNI stub (`src/test/jniLibs/libosmscout_client_java.so`) that
loads that stub SHALL NOT pin a Robolectric sandbox: no line-anchored `@Config(… sdk …)` and no
`@GraphicsMode(…)` on such a file. A run that adds such a pin SHALL fail, naming the file and the line.

#### Scenario: A stub-loading class that pins a sandbox fails the case

- **WHEN** the test sources of the stub-shipping modules are read and one file names the `com.framstag.libosmscout.client`
  package (whose `OSMScoutClient` static initializer calls `System.loadLibrary`) while declaring a line-anchored
  `@Config(… sdk …)` or `@GraphicsMode(…)`
- **THEN** the case fails and its message names the file, the line and the annotation it found
- **Case**: `JniStubSandboxRuleTest.noTestClassThatLoadsTheStubPinsTheSandbox`

#### Scenario: Both stub modules are read

- **WHEN** the case scans the `:app` and `:auto` test sources
- **THEN** each module root contributes at least one file, so neither root can go unread and leave the first
  scenario passing on an empty list
- **Case**: `JniStubSandboxRuleTest.everyStubModuleIsScanned`

#### Scenario: The detector reports a pinned stub-loading class

- **WHEN** the detector is given a fixture that imports the client package and declares a line-anchored
  `@Config(sdk = …)`
- **THEN** it reports exactly one finding and that finding names the fixture's line
- **Case**: `JniStubSandboxRuleTest.aPinnedStubLoadingClassIsReported`

#### Scenario: A pinned class that loads no stub is allowed

- **WHEN** the detector is given a fixture that declares a line-anchored `@Config(sdk = …)` and names no client
  package
- **THEN** it reports no finding, so the rule does not forbid a sandbox pin as such
- **Case**: `JniStubSandboxRuleTest.aPinnedClassThatLoadsNoStubIsAllowed`

#### Scenario: A qualifier on a stub-loading class is allowed

- **WHEN** the detector is given a fixture that imports the client package and declares
  `@Config(qualifiers = "de")` with no sdk argument
- **THEN** it reports no finding, because a qualifier is applied per test and starts no second sandbox, so the
  locale cases stay legal
- **Case**: `JniStubSandboxRuleTest.aQualifierOnAStubLoadingClassIsAllowed`

#### Scenario: The unpinned engine class keeps its cases in the default sandbox

- **WHEN** the engine class that used to pin SDK 34 runs with no sandbox pin, in the suite and in the gate
- **THEN** its cases still pass on the default sandbox, so removing the pin moved the sandbox and not the
  behaviour
- **Case**: `NavigationEngineAcquisitionTest.acquire_withRoute_startsNavigation`
