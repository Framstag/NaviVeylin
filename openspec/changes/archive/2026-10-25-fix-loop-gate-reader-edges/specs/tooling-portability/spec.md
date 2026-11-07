# Delta — tooling-portability

## MODIFIED Requirements

### Requirement: Discovery is exercised where it breaks

The tools SHALL determine the project from their working tree in every situation a project actually occurs
in, and the situations that break a naive resolution SHALL be covered by the check's own cases. The check's
suite SHALL state the preconditions its own root must meet, and SHALL refuse with a named reason and the
environment's exit code when they are not met, rather than reporting a case as failed.

#### Scenario: Invoked from a nested directory

- **WHEN** a tool is invoked from a directory below the project's root
- **THEN** the project's root, repository and profile are resolved from the tree

#### Scenario: Invoked in a linked working tree

- **WHEN** a tool is invoked inside a linked working tree of the project
- **THEN** that working tree's own profile and repository are used and no path of the main checkout is read

#### Scenario: The environment contradicts the tree

- **WHEN** an environment variable names a different repository
- **THEN** the working tree's repository is used and only an explicit argument overrides it

#### Scenario: Invoked outside a project

- **WHEN** a tool is invoked where there is no project
- **THEN** it reports that and stops, and no default project is invented

#### Scenario: preconditions#refusesOutsideAGitTree

- **WHEN** the check's suite is run from a copy whose root is not inside a git working tree
- **THEN** it names that reason, exits with the environment's code, and reports no case as failed — and its
  header states the precondition before the run

#### Scenario: preconditions#refusesUnderMachineLocalState

- **WHEN** the check's suite is run from a copy whose root is a `.pi/` path that is not the top of a git
  working tree — machine-local state, not a checkout
- **THEN** it names that reason, exits with the environment's code, and reports no case as failed — and a
  linked working tree under `.pi/`, which *is* a checkout, runs the suite and lists its cases

#### Scenario: preconditions#runsInALinkedWorkingTree

- **WHEN** the check's suite is run from a linked working tree under `.pi/`, the tree the loop's gate runs in
- **THEN** it accepts the root as a checkout and lists its cases rather than refusing it
- **Case**: `preconditions#runsInALinkedWorkingTree`
