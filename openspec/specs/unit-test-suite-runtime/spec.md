# unit-test-suite-runtime Specification

## Purpose

The JVM unit-test suites of the project's modules run to completion in a single invocation inside a
declared, bounded fork budget, and nothing a test creates — background work, retained bitmaps, native
surfaces — outlives that test, so a suite's result is attributable per class and reproducible from a
fresh checkout.

## Requirements

### Requirement: Test-created components release their background work

Every unit test that constructs a component owning long-lived background work — its own coroutine
scope, timers, render loops, a native surface or a large retained bitmap — SHALL release that
component before the test method returns. No background work, retained bitmap or native surface
created by a finished test SHALL remain alive for the remainder of the JVM run.

#### Scenario: Component released when the test returns

- **WHEN** a unit test constructs such a component and the test method returns
- **THEN** the component has been shut down and none of its background jobs is still active

#### Scenario: Every instance of a test class is released

- **WHEN** a test class constructs such a component in its per-test setup for many test methods
- **THEN** every instance that class created is released, including instances whose test method never
  exercised a shutdown path itself

#### Scenario: Shutdown is idempotent

- **WHEN** a test method shuts such a component down itself and the test teardown shuts the same
  instance down again
- **THEN** no exception is raised and the test result is unaffected

#### Scenario: A leak fails the test instead of staying silent

- **WHEN** a component constructed by a test still has active background work after the teardown
  attempt
- **THEN** the test fails, so the leak is attributable to that test class rather than surfacing later
  as an out-of-memory failure of unrelated classes

#### Scenario: Retained render buffers become reclaimable

- **WHEN** a component that retained a rendered bitmap is shut down
- **THEN** the retained bitmap is released and no longer reported by the component's own buffer state

### Requirement: Module unit-test suite completes in a single invocation

The unit-test suite of a module SHALL complete in a single Gradle test invocation within the module's
declared fork budget, writing one result XML per executed test class. A run SHALL NOT require
splitting test classes across multiple invocations, and the suite SHALL also complete in a single
invocation when the coverage instrumentation agent is attached.

#### Scenario: Auto module suite in one invocation

- **WHEN** `:auto:testDebugUnitTest` is executed as one invocation at the module's declared fork budget
- **THEN** all of the module's test classes execute, one result XML exists per executed class, and the
  run completes without `OutOfMemoryError`

#### Scenario: App module suites in one invocation per flavor

- **WHEN** `:app:testMobileDebugUnitTest` or `:app:testAutomotiveDebugUnitTest` is executed as one
  invocation at the module's declared fork budget
- **THEN** all of that flavor's test classes execute, one result XML exists per executed class, and no
  test fails because the JVM ran out of headroom mid-suite

#### Scenario: Coverage report generated in one invocation

- **WHEN** the aggregated coverage report tasks are executed for the Kotlin modules
- **THEN** the same test suite completes in that single invocation and the module's coverage report is
  produced

#### Scenario: Documented procedure needs no batching

- **WHEN** the documented test procedure in `guidelines/Build.md` is followed for a module
- **THEN** it prescribes a single invocation and does not require an operator to split test classes
  into batches

#### Scenario: Failures stay attributable

- **WHEN** a test class in the suite fails
- **THEN** the failure is reported for that class with its own result XML, and the run does not end in
  a memory failure that leaves the suite without per-class results

### Requirement: Unit-test fork budget is declared and bounded

A module whose unit-test suite cannot complete within the build plugin's default fork heap SHALL
declare its fork budget in that module's build script: a heap ceiling and, when a single fork cannot
hold the suite, a fork cadence that bounds the peak. The declared values SHALL apply to a fresh
checkout without local configuration and SHALL be accompanied by the measurement that justifies them.

#### Scenario: Declared budget is applied to the fork

- **WHEN** a module's unit tests run
- **THEN** the test JVM is launched with the declared heap ceiling and a fresh JVM is started at the
  declared fork cadence

#### Scenario: Peak memory stays inside the declared bound

- **WHEN** the declared fork cadence is reached during a suite run
- **THEN** the remaining test classes continue in a fresh JVM and the heap of a single fork stays
  within the declared ceiling

#### Scenario: Budget is reproducible from a fresh checkout

- **WHEN** a fresh checkout runs the module's unit tests without any local Gradle configuration
- **THEN** the same fork budget applies

#### Scenario: Budget values are justified by a measurement

- **WHEN** a module's declared fork budget is introduced or changed
- **THEN** `guidelines/Build.md` records the suite, the class and test counts, and the measured heap
  headroom that justify the declared values
