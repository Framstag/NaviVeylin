# issue-backlog Specification

## Purpose

The project's issue tracker is its backlog: each finding is one issue whose labels carry what kind it is,
where it lives and what state it is in, so a reader selects a working set by query instead of reading the
backlog, and so an automated actor can only ever act on an item its owner has explicitly authorised.

## Requirements

### Requirement: One issue per distinct finding

The tooling SHALL create exactly one issue per distinct finding, and SHALL refuse to create a second
issue for a finding whose computed fingerprint already matches one, naming the issue that already exists.

#### Scenario: A new finding is filed

- **WHEN** a finding is submitted with its kind, its area and its claim
- **THEN** one issue exists carrying that claim, and its labels record the kind and the area

#### Scenario: A repeated finding is refused, not duplicated

- **WHEN** a finding whose fingerprint matches an existing issue is submitted
- **THEN** no issue is created, the call reports the outcome as a duplicate rather than as a failure,
  and it names the existing issue

#### Scenario: A deliberate duplicate is possible and recorded

- **WHEN** a caller asserts that a matching fingerprint describes a genuinely different finding
- **THEN** the issue is created and the override is recorded in the output

### Requirement: Labels carry the item's kind, area, state and provenance

The tooling SHALL express an item's kind, area, lifecycle state and provenance as labels in separate
namespaces, with exactly one value per namespace where the item has that fact, and SHALL draw every value
from a closed vocabulary held by the project.

#### Scenario: Each fact has its own namespace

- **WHEN** an issue is created
- **THEN** its kind, its area, its state and its provenance are each a label in a distinct namespace,
  so selecting by one fact cannot select by another

#### Scenario: A value outside the vocabulary is refused

- **WHEN** a caller supplies a value that the project's vocabulary does not contain
- **THEN** the operation is refused with a reason naming the value, and no label is created, so a typo
  cannot hide an issue from every selector

#### Scenario: A priority namespace is reserved

- **WHEN** the taxonomy is created
- **THEN** it contains a priority namespace whose values are the project's bands in a fixed range, and the
  tooling that computes a band is not the tooling that defines what a band is

#### Scenario: Provenance is distinguishable

- **WHEN** an issue was imported, filed by an automated check, or filed by a person
- **THEN** its provenance label says which, so a reader can tell an imported item from a filed one

### Requirement: Only an authorised actor's item may be acted on

The tooling SHALL act on an issue only when its author is on a committed allowlist and the issue carries
the project's explicit opt-in label, and SHALL refuse to run at all when the identity it is running as is
not on that allowlist.

#### Scenario: An unauthorised author is not acted on

- **WHEN** an issue whose author is not on the allowlist is considered
- **THEN** it is excluded and the exclusion names the author as the reason

#### Scenario: The opt-in is required and is not the author's decision alone

- **WHEN** an issue by an allowlisted author does not carry the opt-in label
- **THEN** it is not eligible, so filing an item and authorising work on it remain two acts

#### Scenario: The tooling cannot grant the opt-in to itself

- **WHEN** any operation of the tooling is given the opt-in label as an input
- **THEN** it is refused with a reason, so no script, check or automated pass can authorise its own work

#### Scenario: A foreign identity stops the tooling rather than narrowing it

- **WHEN** the identity the tooling runs as is not on the allowlist
- **THEN** it refuses before acting and says so, rather than reporting an empty working set

### Requirement: Identity survives its source

An issue imported from an existing record SHALL carry a machine-readable marker naming that record's
identifier, and the tooling SHALL be able to find an issue by that identifier.

#### Scenario: A historical reference resolves

- **WHEN** a reference to an imported item's source identifier is looked up
- **THEN** exactly the one issue carrying that marker is returned

#### Scenario: A partial identifier does not match

- **WHEN** a lookup is given an identifier that is a substring of another item's identifier
- **THEN** the other item is not returned, because the match is on the marker and not on its text

#### Scenario: Re-importing is a no-op

- **WHEN** the same source record is imported again
- **THEN** no issue is created or edited, and the outcome is reported as already present

### Requirement: Closure is the only terminal state, and deletion is not offered

The tooling SHALL close an issue only together with a stated reason and the evidence for it, SHALL record
whether the closure was a completed fix or a decision not to act, and SHALL expose no operation that
deletes an issue.

#### Scenario: A closure without evidence is refused

- **WHEN** a closure is requested without a reason or without evidence
- **THEN** nothing is written and the call reports what is missing

#### Scenario: The reason is recorded before the closure

- **WHEN** a valid closure is requested
- **THEN** the reason is attached to the issue before it is closed, so the closure can be audited and
  reversed

#### Scenario: No operation deletes an issue

- **WHEN** the whole tooling surface is inspected
- **THEN** it offers no deletion, so the only record of a finding cannot be destroyed by a call

### Requirement: The tooling runs against the current project without being told

The tooling SHALL determine the project it is operating on from the working tree it is invoked in, SHALL
read every project-specific fact from a single committed profile, and SHALL NOT require a project name, a
repository or a path as an argument.

#### Scenario: Invoked from a subdirectory

- **WHEN** the tooling is invoked from anywhere inside the project's tree
- **THEN** it resolves that project's root, that project's repository and that project's profile

#### Scenario: Invoked inside a separate working tree of the project

- **WHEN** the tooling is invoked inside a linked working tree of the project
- **THEN** it uses that working tree's own profile and never the main checkout's

#### Scenario: The environment does not decide the project

- **WHEN** an environment variable names a different repository than the working tree's
- **THEN** the working tree wins, and only an explicit argument can override it

#### Scenario: Invoked outside a project

- **WHEN** the tooling is invoked where there is no project
- **THEN** it reports that and stops, rather than inventing a default

#### Scenario: A project fact lives in one place

- **WHEN** a project-specific value is needed — the repository, an allowlisted author, a vocabulary value,
  a bound
- **THEN** it is read from the project's profile, so adapting the tooling to another project is an edit to
  that file alone
