## Purpose

Ensures the root Kover merge resolves `app`, `auto`, and `core` as project dependencies so `:koverHtmlReport`/`:koverXmlReport` produce the aggregated coverage report instead of failing on an unresolvable external dependency.

## ADDED Requirements

### Requirement: Root Kover merge resolves project modules

The root `kover` configuration SHALL declare `app`, `auto`, and `core` as project dependencies so the Kover report tasks aggregate their unit-test coverage.

#### Scenario: Coverage tasks resolve all modules

- **WHEN** `:koverHtmlReport` or `:koverXmlReport` runs
- **THEN** the `:koverExternalArtifacts` configuration resolves without `Could not find :app:`-style errors and the aggregated report is produced

### Requirement: Aggregated report produced

`:koverHtmlReport` SHALL write `build/reports/kover/html/index.html` covering the Kotlin modules' unit tests, and `:koverXmlReport` SHALL write the XML report.

#### Scenario: HTML report exists after run

- **WHEN** `./gradlew :koverHtmlReport` completes successfully
- **THEN** `build/reports/kover/html/index.html` exists

### Requirement: No deprecation regression from the declaration

The declaration SHALL not introduce new Gradle deprecation warnings on the current toolchain (Gradle 9.6.1, Kover 0.9.8).

#### Scenario: Build stays warning-neutral

- **WHEN** a build runs with the project-notated `kover` dependencies
- **THEN** no new `Deprecation` warnings related to the `kover` configuration appear beyond the pre-existing baseline
