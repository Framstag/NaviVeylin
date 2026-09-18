# build-sbom Specification

## Purpose

Generates a CycloneDX JSON software bill of materials covering all dependencies of NaviVeylin builds — JVM/Gradle, native vcpkg, and the libosmscout submodule — for dependency review, wired into release builds and CI.

## ADDED Requirements

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
