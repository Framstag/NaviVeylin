# test-coverage Specification

## Purpose

The build system measures JVM unit test code coverage with Kover across all modules, producing per-module and aggregated HTML/XML reports that CI publishes as artifacts — giving the project visibility into test coverage without gating builds.

## Requirements

### Requirement: All test-bearing modules produce coverage metrics
Every module with JVM unit tests SHALL produce coverage metrics for its unit test execution (including Robolectric tests running on the host JVM): `app`, `auto`, and `core` measured by the Kover Gradle plugin; `osmscout-client-java` (pure Java module without the Kotlin plugin, which Kover cannot measure) measured by the standard Gradle JaCoCo plugin.

#### Scenario: App module records coverage
- **WHEN** `koverHtmlReport` runs for the `:app` module after its unit tests executed
- **THEN** an HTML report SHALL exist under the app module's Kover report output directory with non-empty coverage for app sources

#### Scenario: Kotlin library modules record coverage
- **WHEN** Kover report tasks run for `:auto` and `:core`
- **THEN** each module SHALL produce a report covering its own production sources

#### Scenario: Java module records coverage via JaCoCo
- **WHEN** `:osmscout-client-java:jacocoTestReport` runs
- **THEN** an HTML and an XML report SHALL exist with non-empty coverage counters for the module's classes

### Requirement: Aggregated multi-module report
A single aggregated coverage report SHALL combine the unit test coverage of the Kotlin modules `app`, `auto`, and `core` so the whole Kotlin surface can be judged from one report (the pure-Java module is reported separately by JaCoCo).

#### Scenario: Aggregated report covers all Kotlin modules
- **WHEN** the aggregated Kover report task completes
- **THEN** the report SHALL list classes from `app`, `auto`, and `core` and SHALL show a combined line/branch coverage percentage without double-counting shared classes

### Requirement: HTML and XML report formats
Kover SHALL emit reports in both HTML (human-readable) and XML (machine-readable, e.g. for CI/artifact consumption) formats.

#### Scenario: HTML report generated
- **WHEN** a Kover report task completes
- **THEN** an HTML report file SHALL exist in the configured report output directory

#### Scenario: XML report generated
- **WHEN** a Kover report task completes
- **THEN** an XML report file SHALL exist in the configured report output directory

### Requirement: Generated code excluded from metrics
Coverage metrics SHALL exclude generated code that is not hand-written production logic: `BuildConfig`, `R` classes, and Dagger/Hilt/KSP-generated classes.

#### Scenario: Generated classes absent from report
- **WHEN** coverage reports are generated
- **THEN** entries for `BuildConfig`, `R` (and R-style resource classes), and Dagger/Hilt/KSP-generated classes SHALL NOT appear in the report's coverage metrics

### Requirement: CI publishes coverage artifacts
The CI workflow SHALL generate the coverage reports after its unit test step and upload the HTML and XML outputs as a downloadable workflow artifact.

#### Scenario: CI workflow produces coverage artifact
- **WHEN** CI runs its unit test step successfully
- **THEN** a coverage report step SHALL run after it and a workflow artifact containing HTML and XML coverage reports SHALL be uploaded

#### Scenario: CI stays green without thresholds
- **WHEN** CI runs with coverage enabled
- **THEN** the workflow SHALL NOT fail the build due to coverage percentages — measurement is report-only
