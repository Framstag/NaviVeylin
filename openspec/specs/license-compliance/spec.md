# license-compliance Specification

## Purpose
Establishes a reviewable, enforced record of the licenses of every third-party
component distributed with NaviVeylin, so that license obligations can be verified
from build output rather than assumed.

## Requirements

### Requirement: Every distributed component has a resolvable license

Every component recorded in a distributed build's dependency inventory SHALL
resolve to a license identifier: either an SPDX license identifier, or — where a
license has no SPDX identifier — a `LicenseRef-` identifier declared in the
project's policy file together with the canonical source of that license. A
component whose license resolves to neither SHALL fail the build rather than
appear in the inventory with an unresolved or assertion-less license value.

#### Scenario: All components resolve

- **WHEN** the dependency inventory is generated for a distribution flavor
- **THEN** every component SHALL carry either an SPDX license identifier or a
  policy-declared `LicenseRef-` identifier
- **AND** no component SHALL carry a placeholder or assertion-less license value

#### Scenario: Non-SPDX license declared in the policy

- **GIVEN** a component whose license is not on the SPDX list and whose declared
  metadata provides only a license name
- **AND** the policy file declares a `LicenseRef-` identifier for that license
  with its canonical source
- **WHEN** the dependency inventory is generated
- **THEN** the component SHALL report that `LicenseRef-` identifier
- **AND** the canonical source SHALL be available to the license list

#### Scenario: Non-SPDX license without a declaration fails

- **GIVEN** a component whose license is not on the SPDX list
- **AND** no `LicenseRef-` identifier declared for it in the policy file
- **WHEN** the dependency inventory is generated
- **THEN** generation SHALL fail and SHALL name the component and its license name

#### Scenario: Unresolvable license fails the build

- **GIVEN** a component whose license cannot be determined from its declared
  metadata or the project's license mapping
- **WHEN** the dependency inventory is generated
- **THEN** generation SHALL fail
- **AND** the failure SHALL name the component whose license is unresolved

### Requirement: Dual-licensed components record an explicit election

When a component is offered under more than one license, the project SHALL record
which license it relies on in a repository-controlled declaration, and the elected
license SHALL be the one reported in the inventory and shown to users.

#### Scenario: Election is reported

- **GIVEN** a component distributed under two alternative licenses, one of which
  imposes weaker obligations
- **WHEN** the dependency inventory is generated
- **THEN** the reported license SHALL be the elected one
- **AND** the election SHALL be visible as a reviewable change to a repository file

#### Scenario: Missing election fails the build

- **GIVEN** a component whose license expression offers a choice
- **AND** no election recorded for that component
- **WHEN** the dependency inventory is generated
- **THEN** generation SHALL fail and SHALL name the component requiring an election

### Requirement: Components are classified as shipped or build-time only

Each component SHALL be classified by whether its code is present in the
distributed application artifact or is used only during the build. The
classification SHALL be derived from the built artifact, not maintained by hand.

#### Scenario: Linked native libraries are shipped

- **GIVEN** a native library whose code is linked into a shared object packaged in
  the application
- **WHEN** the dependency inventory is generated
- **THEN** that component SHALL be classified as shipped

#### Scenario: Installed but unlinked packages are build-time only

- **GIVEN** a build dependency that is installed on the build machine but whose
  code is not present in the distributed application
- **WHEN** the dependency inventory is generated
- **THEN** that component SHALL be classified as build-time only
- **AND** it SHALL NOT be reported as shipped

### Requirement: License policy gate fails the build

The build SHALL enforce a license policy: a component whose license is missing,
unresolved, or not permitted by the declared policy SHALL fail the gate. The gate
SHALL run as part of continuous integration.

#### Scenario: Disallowed license fails

- **GIVEN** a component whose resolved license is not listed as permitted
- **WHEN** the license gate runs
- **THEN** the gate SHALL fail
- **AND** the failure SHALL identify the offending component and its license

#### Scenario: Permitted license passes

- **GIVEN** every component's resolved license is permitted by the policy
- **WHEN** the license gate runs
- **THEN** the gate SHALL pass

#### Scenario: Gate runs in CI

- **WHEN** the continuous integration workflow builds the application
- **THEN** it SHALL run the license gate and SHALL fail the run when the gate fails

### Requirement: Permitted license policy is declared in the repository

The set of permitted licenses SHALL be declared in a repository-controlled file
that reviewers can read and change, and it SHALL cover every license in use
including the elected licenses of dual-licensed components. A license expression
offering alternatives SHALL be satisfied only by an alternative that the policy
permits.

#### Scenario: Policy is reviewable

- **WHEN** a contributor inspects the repository
- **THEN** the permitted license set SHALL be present as a committed file

#### Scenario: Expression satisfied by a permitted alternative

- **GIVEN** a component offered under a permissive license and a copyleft license
  where only the permissive one is permitted
- **AND** that election is recorded
- **WHEN** the license gate runs
- **THEN** the gate SHALL pass

### Requirement: Notices of bundled components are distributed

Where a bundled component requires its notice text to accompany redistribution,
that notice SHALL be included in the license information distributed with the
application and SHALL be reachable by users without network access. A bundled
component that requires a notice it does not provide SHALL fail the build.

#### Scenario: Notice is distributed and reachable

- **GIVEN** a bundled component that requires a notice text
- **WHEN** the license information is produced for a distribution flavor
- **THEN** that component's notice text SHALL be part of the distributed license
  information
- **AND** it SHALL be reachable from the bundled dependency license list without
  network access

#### Scenario: Missing notice fails the build

- **GIVEN** a bundled component whose license requires its notice to accompany
  redistribution
- **AND** no notice text available for that component
- **WHEN** the license information is produced
- **THEN** the build SHALL fail and SHALL name the component whose notice is missing

### Requirement: License inventory covers both distribution flavors

Complete license data SHALL be produced for both shipped flavors — the phone /
Android Auto flavor and the Android Automotive OS flavor — and SHALL be tied to
the version of the build it describes.

#### Scenario: Both flavors carry complete license data

- **WHEN** release artifacts are produced for both flavors
- **THEN** each flavor SHALL have complete license data for its components
- **AND** the license data SHALL identify the version of the build it describes

#### Scenario: Flavor differences are represented

- **GIVEN** a component distributed in one flavor but not the other
- **WHEN** license data is produced for both flavors
- **THEN** the component SHALL appear only in the flavor that distributes it
