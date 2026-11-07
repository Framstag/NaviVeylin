## ADDED Requirements

### Requirement: The split's vendored half reaches the packaged app

The two provenances `:osmscout-client-java` compiles SHALL both be part of what an Android consumer
receives, and the packaged dex of a variant SHALL define one class for every `*.java` file under the
module's two source roots. A class the sources declare and the dex does not define SHALL fail that
variant's `assemble`/`bundle` naming the class, because it is a startup `ClassNotFoundException` and not
a compile error.

#### Scenario: Every client class is defined in the packaged dex

- **WHEN** a variant's packaging tasks run after the module's sources were split into two source sets
- **THEN** the gate reads that variant's merged dex files and finds a `class_defs` entry for every class
  the two source roots declare, and absent one it fails the build and names it
- **Case**: `:app:checkClientApiInDexMobileDebug` — red with the classes directory wiring removed (35 of the
  module's 48 classes absent, `com.framstag.libosmscout.client.MapProvider` among them), green with it
  (issue #691)

#### Scenario: A referenced type is not a defined one

- **WHEN** a dex file mentions a class only as a referenced type — the state the packaged APK was in
- **THEN** that class is absent from the descriptors the gate reads as defined
- **Case**: `buildSrc: ClientApiDexGateTest` "a type that is only referenced is not defined"

#### Scenario: The two provenance rows stay two rows

- **WHEN** the module's own and vendored JaCoCo reports are written after the classes directory wiring
- **THEN** the own row measures the override classes alone and the vendored row the vendored classes alone
- **Case**: `inputs#splitsTheJavaModulesCoverageByProvenance`
