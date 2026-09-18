## ADDED Requirements

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

## MODIFIED Requirements

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
