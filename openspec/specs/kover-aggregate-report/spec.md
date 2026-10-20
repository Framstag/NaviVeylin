# kover-aggregate-report Specification

## Purpose
Ensures the root Kover merge resolves `app`, `auto`, and `core` as project dependencies so `:koverHtmlReport`/`:koverXmlReport` produce the aggregated coverage report instead of failing on an unresolvable external dependency.

## Requirements

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

The Kover Gradle plugin pinned in the root `build.gradle.kts` SHALL be at least 0.9.9 — the first
release whose plugin-apply path does not pass a `Project` object to `DependencyHandler.add` (upstream
issue #818) — so that configuring the build on Gradle 9.6.1 emits no `Using a Project object as a
dependency notation` deprecation, the call path that fails on Gradle 10. `guidelines/Build.md` §7
SHALL name the pinned version and SHALL NOT record that deprecation as an open upstream issue.

#### Scenario: Build stays warning-neutral

- **WHEN** the Kover plugin pinned in the root `build.gradle.kts` is applied on Gradle 9.6.1
- **THEN** no `Using a Project object as a dependency notation` deprecation is emitted, which the
  pinned release (0.9.9 or newer) is the measured precondition of — exercised by
  `KoverDeprecationBaselineTest.the pinned kover version clears the project-object deprecation`

#### Scenario: The coverage guideline names the pinned version

- **WHEN** `guidelines/Build.md` §7 is read
- **THEN** its engine table states the pinned version and it keeps no `Known upstream issue` record of
  the Kover deprecation — exercised by
  `KoverDeprecationBaselineTest.the coverage guideline names the pinned version and records no open upstream issue`
