# build-sbom Specification

## Purpose

Generates a CycloneDX JSON software bill of materials covering all dependencies of NaviVeylin builds — JVM/Gradle, native vcpkg, and the libosmscout submodule — for dependency review, wired into release builds and CI.

## Requirements

### Requirement: Release target produces SBOM per distribution flavor
The `release` target SHALL produce one CycloneDX JSON SBOM per distribution
flavor — `mobileRelease` and `automotiveRelease` — alongside the corresponding
AAB, and each SBOM SHALL carry the release `versionName` as its component
version.

#### Scenario: Mobile release emits SBOM
- **WHEN** the release target completes the mobile bundle
- **THEN** a CycloneDX JSON SBOM for the mobile flavor SHALL exist in the app module's output directory next to the mobile AAB

#### Scenario: Automotive release emits SBOM
- **WHEN** the release target completes the automotive bundle
- **THEN** a CycloneDX JSON SBOM for the automotive flavor SHALL exist in the app module's output directory next to the automotive AAB

#### Scenario: SBOM version matches release version
- **WHEN** the release target generates an SBOM
- **THEN** the SBOM's top-level component version SHALL equal the release `versionName` generated for that build (e.g. `2026-09-12-1`)

### Requirement: Any build variant can generate SBOM
The build SHALL expose a standalone SBOM generation task per Android variant so
any variant — including debug variants such as `mobileDebug` — can produce its
own CycloneDX JSON SBOM without running the release target.

#### Scenario: Debug variant generates SBOM
- **WHEN** a developer runs the SBOM task for the `mobileDebug` variant
- **THEN** a CycloneDX JSON SBOM SHALL be produced describing that variant's dependencies

#### Scenario: Non-release SBOM uses deterministic version
- **WHEN** the SBOM task runs for a non-release variant (e.g. `mobileDebug`)
- **THEN** the SBOM's component version SHALL be the fixed, deterministic version used by non-release builds

### Requirement: SBOM covers all JVM dependencies
Each SBOM SHALL include all JVM/Gradle dependencies of the variant — direct and
transitive, across `:app`, `:auto`, `:core`, and `:osmscout-client-java` — as
components with name and version. Gradle-managed test-only configurations
SHALL be excluded.

#### Scenario: JVM dependencies present
- **WHEN** an SBOM is generated for any variant
- **THEN** the SBOM SHALL contain components for the variant's resolved third-party JVM dependencies (e.g. androidx, Compose, Hilt artifacts) with their resolved versions

#### Scenario: Test-only dependencies excluded
- **WHEN** an SBOM is generated for an app variant
- **THEN** the SBOM SHALL NOT contain components that exist only in unit-test or instrumented-test configurations

### Requirement: SBOM covers native vcpkg dependencies
Each SBOM SHALL include a component for every installed vcpkg package (e.g.
cairo, pango, harfbuzz, freetype, protobuf) across all three build triplets.
Packages installed for multiple triplets with identical name and version SHALL
appear as a single component.

#### Scenario: Native packages present
- **WHEN** an SBOM is generated on a machine with the vcpkg dependency tree installed
- **THEN** the SBOM SHALL contain components for the installed native packages with their versions

#### Scenario: Cross-triplet duplicates deduplicated
- **WHEN** the same vcpkg package and version is installed for more than one triplet (e.g. arm64-android and x64-android)
- **THEN** the SBOM SHALL list that package exactly once as a single component

### Requirement: SBOM records libosmscout submodule
Each SBOM SHALL record the libosmscout submodule as a component whose version
is the git SHA currently checked out in the working tree.

#### Scenario: Submodule component present
- **WHEN** an SBOM is generated
- **THEN** the SBOM SHALL contain a component identifying libosmscout with a version equal to the submodule's checked-out commit SHA

### Requirement: SBOM output is valid CycloneDX JSON
The generated SBOM SHALL be valid CycloneDX JSON, verified by schema
validation as part of generation.

#### Scenario: Output validates
- **WHEN** an SBOM is generated
- **THEN** the output SHALL pass CycloneDX JSON schema validation

### Requirement: Missing native SBOM data fails generation

If a required native dependency's source data is missing — its vcpkg SPDX file,
its license declaration, or the license text distributed for a shipped
component — generation SHALL fail with an actionable error message rather than
produce an incomplete SBOM.

#### Scenario: Missing vcpkg SPDX file fails build

- **GIVEN** an installed vcpkg package whose `vcpkg.spdx.json` file is absent
- **WHEN** SBOM generation runs
- **THEN** generation SHALL fail and SHALL report which package/SPDX file is missing

#### Scenario: Missing license source data fails build

- **GIVEN** an installed vcpkg package whose license text source data is absent
- **AND** that package's code is distributed in the application
- **WHEN** SBOM generation runs
- **THEN** generation SHALL fail and SHALL report which package's license data is
  missing

#### Scenario: Packaged native library without SBOM component fails build

- **GIVEN** the application's packaged native library directory contains a library
  with no corresponding SBOM component
- **WHEN** SBOM generation runs
- **THEN** generation SHALL fail and SHALL name the unrecorded library

### Requirement: SBOM generation does not mutate release version state
Running SBOM generation — standalone, in CI, or as part of `release` — SHALL
NOT mutate the persisted release version state beyond what the release target
itself already does; a `release` run SHALL bump the version exactly once
regardless of SBOM generation.

#### Scenario: Standalone SBOM leaves version state unchanged
- **WHEN** the SBOM task runs for a non-release or standalone invocation
- **THEN** the persisted release version state SHALL remain unchanged

#### Scenario: Release bumps version once despite SBOM
- **WHEN** the release target runs with SBOM generation enabled
- **THEN** the release version SHALL be bumped exactly once

### Requirement: CI uploads SBOM build artifact
The CI workflow SHALL generate the SBOM for the debug build and upload it as a
build artifact so the CI run exposes the dependency inventory.

#### Scenario: CI run uploads SBOM
- **WHEN** the CI workflow builds the debug APK
- **THEN** the workflow SHALL also produce the corresponding SBOM and upload it as a build artifact alongside the APK

### Requirement: SBOM components declare license and distribution scope

Each SBOM component SHALL carry at least one SPDX license identifier and SHALL
declare whether the component is shipped in the distributed application or used
only during the build. A component that cannot be resolved to a license and a
distribution scope SHALL fail generation.

#### Scenario: Component carries license and scope

- **WHEN** an SBOM is generated
- **THEN** every component SHALL declare at least one SPDX license identifier
- **AND** every component SHALL declare its distribution scope (shipped or
  build-time only)

#### Scenario: Native components are no longer assertion-less

- **GIVEN** a natively built dependency included in the SBOM
- **WHEN** the SBOM is generated
- **THEN** the component SHALL report an SPDX license identifier
- **AND** it SHALL NOT report a placeholder or assertion-less license value

### Requirement: SBOM records shipped native runtime libraries

The SBOM SHALL record every native library that is packaged in the application
artifact, including native libraries that are not provided by the project's
vcpkg dependency tree.

#### Scenario: Shipped runtime library present

- **GIVEN** a native library packaged in the application's native library
  directory and referenced by a shipped shared object
- **WHEN** the SBOM is generated
- **THEN** the SBOM SHALL contain a component for that library with its license
  identifier and a shipped distribution scope

#### Scenario: Runtime library is not silently omitted

- **GIVEN** a native library present in the packaged application artifact
- **WHEN** the SBOM is generated
- **THEN** generation SHALL fail if that library has no corresponding SBOM
  component

### Requirement: SBOM root component carries the application license

The SBOM's root (application) component SHALL carry the application's own SPDX
license identifier, recorded per distribution flavor. The root SHALL NOT be
distributed with an absent or unresolved license field.

#### Scenario: Root component has the application license

- **WHEN** an SBOM is generated for a distribution flavor
- **THEN** the SBOM's root component SHALL declare the application's SPDX license
  identifier (`GPL-3.0-or-later`)
- **AND** the identifier SHALL be a valid SPDX id (no `LicenseRef-` and no
  `NOASSERTION`)

#### Scenario: Root license matches both flavors

- **WHEN** SBOMs are generated for `mobileRelease` and `automotiveRelease`
- **THEN** both root components SHALL carry the same application license
  identifier

### Requirement: First-party components resolve to the application license

Components for the application's own modules (`:app`, `:auto`, `:core`,
`:osmscout-client-java`) SHALL resolve to the application's SPDX license
identifier rather than to a declared `LicenseRef-` placeholder for the project's
own license.

#### Scenario: First-party components carry the SPDX identifier

- **WHEN** an SBOM is generated
- **THEN** each first-party component SHALL declare `GPL-3.0-or-later` as its
  license
- **AND** no first-party component SHALL carry `LicenseRef-NaviVeylin`

#### Scenario: First-party license requires distributed text

- **WHEN** the license inventory is produced for a distribution flavor
- **THEN** the application's license text SHALL be part of the inventory's
  embedded texts (a full copy of the GNU GPL version 3 text)
- **AND** the text SHALL be reachable offline from the bundled license list
