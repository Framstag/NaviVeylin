# Spec Delta

## MODIFIED Requirements

### Requirement: All test-bearing modules produce coverage metrics

Every module with JVM unit tests SHALL produce coverage metrics for the unit test execution of an
invocation that requests coverage (including Robolectric tests running on the host JVM): `app`, `auto`,
and `core` measured by the Kover Gradle plugin; `osmscout-client-java` (pure Java module without the
Kotlin plugin, which Kover cannot measure) measured by the standard Gradle JaCoCo plugin. An invocation
that does not request coverage SHALL execute the same test classes with the coverage instrumentation
agent detached, and the tests' results SHALL be unaffected by the choice.

#### Scenario: App module records coverage

- **WHEN** `koverHtmlReport` runs for the `:app` module after an invocation that requested coverage
- **THEN** an HTML report SHALL exist under the app module's Kover report output directory with non-empty coverage for app sources

#### Scenario: Kotlin library modules record coverage

- **WHEN** Kover report tasks run for `:auto` and `:core`
- **THEN** each module SHALL produce a report covering its own production sources

#### Scenario: Java module records coverage via JaCoCo

- **WHEN** `:osmscout-client-java:jacocoTestReport` runs
- **THEN** an HTML and an XML report SHALL exist with non-empty coverage counters for the module's classes

#### Scenario: Instrumentation is attached only when coverage is requested

- **WHEN** a unit-test invocation runs without requesting coverage
- **THEN** the test JVM is launched with no coverage instrumentation agent attached

#### Scenario: Test results are the same with and without the agent

- **WHEN** the same suite runs once with and once without the coverage instrumentation agent
- **THEN** the sets of failing classes are identical, and the suite still completes in a single
  invocation in both cases

#### Scenario: A coverage-bearing invocation feeds the reports without re-running tests

- **WHEN** a coverage report task runs after an invocation that requested coverage
- **THEN** the report is produced from that invocation's execution data without executing the tests
  again
