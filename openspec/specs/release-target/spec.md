# release-target Specification

## Purpose

Provides a Gradle release target that generates a date-based version name and a monotonic version code, producing a signed Android App Bundle ready for Google Play upload.

## Requirements

### Requirement: Release target generates date-based version name
The build SHALL generate the release `versionName` in the format `<yyyy>-<MM>-<dd>-<N>` where `yyyy` is a four-digit year, `MM` and `dd` are zero-padded two-digit month and day-of-month, and `N` is the running number for that day with no leading zeros. The running number SHALL increment by one on each release build performed on the same day and SHALL reset to 1 on the first release build of a new day.

#### Scenario: First release build of a day
- **WHEN** a release build runs on a day with no prior release build
- **THEN** the generated `versionName` uses that day's date with running number `1` (e.g., `2026-08-19-1`)

#### Scenario: Subsequent release builds same day
- **WHEN** another release build runs on the same day as a previous release build
- **THEN** the generated `versionName` uses the same date with the running number incremented by one (e.g., `2026-08-19-2`)

#### Scenario: Running number has no leading zeros
- **WHEN** a release build generates a running number below 10
- **THEN** the running number SHALL be rendered without leading zeros (e.g., `2026-08-19-9`, never `2026-08-19-09`)

#### Scenario: New day resets running number
- **WHEN** a release build runs on a date different from the previous release build
- **THEN** the running number SHALL reset to 1

### Requirement: Release target increments version code
Each release build SHALL increment the `versionCode` by exactly one relative to the previous release build, and the resulting `versionCode` SHALL be persisted so later builds continue from it.

#### Scenario: Version code increases by one
- **WHEN** a release build completes
- **THEN** the produced artifact SHALL have a `versionCode` exactly one greater than the previous release build's `versionCode`

#### Scenario: Version code persists across builds
- **WHEN** a subsequent release build runs after a prior release build
- **THEN** the new `versionCode` SHALL continue from the persisted value rather than restarting

### Requirement: Release target produces Play-ready app bundle
The release target SHALL build the release Android App Bundle via `:app:bundleRelease` and SHALL sign it when a release keystore is configured, producing an `*.aab` suitable for upload to Google Play.

#### Scenario: Release build produces signed AAB
- **GIVEN** a release keystore is configured
- **WHEN** the release target runs
- **THEN** the build SHALL produce a signed `*.aab` artifact in the app module's output directory

#### Scenario: Release build without keystore warns
- **GIVEN** no release keystore is configured
- **WHEN** the release target runs
- **THEN** the build SHALL still produce an `*.aab` artifact and SHALL log a clear warning that it is unsigned

### Requirement: Generated version name available to application
The generated release `versionName` SHALL be available to application code at runtime so UI can display it.

#### Scenario: App can read generated version name
- **WHEN** the application runs with a release build
- **THEN** the app SHALL be able to read the generated `versionName` (e.g., `2026-08-19-1`) from its version metadata

### Requirement: Non-release builds keep deterministic version
Builds other than the release target SHALL NOT mutate the persisted version state, and SHALL use a fixed, deterministic `versionName` and `versionCode`.

#### Scenario: Debug build does not change version state
- **WHEN** a developer runs a non-release build (e.g., `assembleDebug`)
- **THEN** the persisted running number and version code SHALL remain unchanged

#### Scenario: Debug build uses fixed version
- **WHEN** a debug build runs
- **THEN** it SHALL use the fixed deterministic `versionName` and `versionCode` configured for non-release builds
