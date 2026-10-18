# Spec Delta

## MODIFIED Requirements

### Requirement: The check's value set is the project's own

The check SHALL read the values it forbids from the project's profile rather than carrying a list of its own,
so that a value added to the profile is immediately forbidden in an engine and the check cannot become a stale
list that fails open. Its value set SHALL be the whole of the project's vocabulary — every namespace's values
and their label forms, and the values the profile's `roles` section declares for an engine's decisions — not
one namespace of it. The labels the profile reserves to authorise work SHALL be the one asserted exception:
the check SHALL accept exactly one read-only declaration of such a label and print it, and SHALL report any
other occurrence as a leak.

#### Scenario: A newly declared value is forbidden in engines

- **WHEN** a value is added to the profile and then used literally in an engine
- **THEN** the check reports it

#### Scenario: A value removed from the profile is no longer forbidden

- **WHEN** a value is removed from the profile
- **THEN** the literal is no longer reported, which is what distinguishes a read value set from a stale
  one

#### Scenario: portability#plantedValueInEveryNamespaceFails

- **WHEN** a value of any namespace the profile declares — not the category namespace alone — is named in an
  engine
- **THEN** the check fails, names the value and names the namespace it was read from

#### Scenario: portability#plantedSubmodulePathFails

- **WHEN** the submodule path the loop's configuration declares is named in an engine
- **THEN** the check fails, names the path and names the loop configuration as its source

#### Scenario: portability#authorisationLabelIsTheOneException

- **WHEN** the reserved label that authorises work is declared read-only once, and read nowhere else
- **THEN** the check passes and prints that declaration as the documented exception, and **WHEN** a second
  declaration or any other occurrence of the label appears in an engine, the check fails and names it

### Requirement: Discovery is exercised where it breaks

The tools SHALL determine the project from their working tree in every situation a project actually occurs
in, and the situations that break a naive resolution SHALL be covered by the check's own cases. The check's
suite SHALL state the two preconditions its own root must meet, and SHALL refuse with a named reason and the
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

- **WHEN** the check's suite is run from a copy whose root path is machine-local state (under `.pi/`)
- **THEN** it names that reason, exits with the environment's code, and reports no case as failed
