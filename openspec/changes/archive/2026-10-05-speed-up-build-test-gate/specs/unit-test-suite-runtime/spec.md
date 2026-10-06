# Spec Delta

## ADDED Requirements

### Requirement: Unit-test parallelism is declared and result-preserving

A module SHALL declare, together with its fork budget, how many test JVMs may execute concurrently, and
that declaration SHALL apply to a fresh checkout without local configuration. A suite executed with the
declared number of concurrent JVMs SHALL write one result XML per executed class and SHALL report the
same failing classes as the same suite executed in a single JVM.

#### Scenario: Declared concurrency applies to a fresh checkout

- **WHEN** a module's unit tests run
- **THEN** at most the declared number of test JVMs run concurrently and each is launched with the
  declared heap ceiling

#### Scenario: The failure set is unchanged by concurrency

- **WHEN** a module's suite runs with the declared number of concurrent JVMs
- **THEN** its failing classes are exactly the failing classes of the same suite run in a single JVM

#### Scenario: Per-class results survive concurrency

- **WHEN** test classes execute in concurrent JVMs
- **THEN** every executed class has its own result XML and the run ends with a verdict rather than a
  memory failure or a lost result file

#### Scenario: Declared concurrency is justified by a measurement

- **WHEN** a module's declared concurrency is introduced or changed
- **THEN** the module's budget note and `guidelines/Build.md` record the concurrency, the wall time and
  peak memory measured at it, and the failure set observed at that concurrency
