## Purpose

Keeps every action a workflow pins on a Node 24 runtime, so the runner stops printing its Node 20 deprecation
notice on each of the workflow's steps, and keeps the pin check itself running with no network, no build and no
device.

## ADDED Requirements

### Requirement: A workflow pin names a major that declares Node 24

Every action a workflow step pins SHALL be at or above the first major whose committed `action.yml` declares
`runs.using: node24`, and a pin below that action's floor SHALL be reported with the file and the line it sits
on rather than left to the runner's deprecation warning.

#### Scenario: A Node 20 pin fails the check

- **WHEN** a workflow step pins an action at a major below that action's recorded Node 24 floor
- **THEN** the check exits non-zero and names the file, the line and the first major that declares Node 24
- **Case**: `a Node 20 upload-artifact pin fails`

#### Scenario: A Node 20 checkout pin fails the check

- **WHEN** a workflow step pins an action whose metadata at the pinned major declares Node 20
- **THEN** the check reports that line as a defect and names the first Node 24 major in the message
- **Case**: `a Node 20 checkout pin fails`

#### Scenario: A pin at or above the floor passes

- **WHEN** every pinned action in the tree is at or above its recorded Node 24 floor
- **THEN** the check exits zero and prints the number of pins it examined
- **Case**: `pins at or above the Node 24 floor pass`

#### Scenario: This repository's own workflows are checked

- **WHEN** the check runs against this repository's own `.github/workflows/`
- **THEN** every `uses:` pin there is at or above its action's floor, and the case fails on a pin below it
- **Case**: `this repository's workflows pin a Node 24 major`

#### Scenario: A workflow with no action pin passes

- **WHEN** a workflow file pins no action at all
- **THEN** the check exits zero and reports the counts it examined
- **Case**: `a workflow with no action pin passes`

### Requirement: The check decides offline and names what it cannot resolve

The check SHALL record each action's floor with the `action.yml` URL the declaration was read from, so it
decides from files it carries rather than from the network. A ref it cannot resolve offline — a branch, a
commit SHA, an expression — SHALL be reported. A local action (`./path`) and a container action
(`docker://…`) SHALL be skipped, because neither carries a Node runtime of its own.

#### Scenario: A ref that cannot be resolved offline is reported

- **WHEN** a workflow pins a branch or a commit SHA instead of a released major
- **THEN** the check reports that line, because it cannot decide the ref's runtime without the network
- **Case**: `an unverifiable ref fails`

#### Scenario: A ref that carries no Node runtime is skipped

- **WHEN** a workflow uses a local action or a container action
- **THEN** the check skips that line and does not fail on it
- **Case**: `a container action and a local action are skipped`

#### Scenario: A tree with no workflow directory is a layout error

- **WHEN** the check is pointed at a tree that carries no `.github/workflows` directory
- **THEN** it exits 2 and says so, instead of reporting a green tree it never examined
- **Case**: `a tree with no workflow directory is a layout error`

### Requirement: An action the check has no recorded runtime for is a defect

The check's floor table SHALL be the only list of action runtimes, and an action pinned without a row SHALL be
reported so the recording is a deliberate act rather than an omission. Because an action may publish a subpath
(`actions/cache/restore`), a subpath SHALL resolve to its action's floor.

#### Scenario: An unrecorded action fails

- **WHEN** a workflow pins an action the floor table does not record
- **THEN** the check exits non-zero and says which table entry to add and what to read it from
- **Case**: `an action with no recorded runtime fails`

#### Scenario: A subpath resolves to its action's floor

- **WHEN** a workflow pins a subpath of a recorded action
- **THEN** the check applies that action's floor and passes when the major is at or above it
- **Case**: `a subpath of a recorded action resolves to the action's floor`
