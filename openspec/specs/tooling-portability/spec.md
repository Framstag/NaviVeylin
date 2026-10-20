# tooling-portability Specification

## Purpose

The backlog tooling can be used by another project: every project-specific fact lives in one committed
profile, every command determines the project it is running on from its own working tree, no engine names
a project value, and a synthetic project with a different repository, allowlist and vocabulary runs the
whole toolchain — so a second project is a copy and a profile, not a code review.

## Requirements

### Requirement: An engine names no project value

The tooling's engines SHALL contain no project value — not the repository, not an allowlisted author, not
a value of the project's vocabulary, not a project path, not the project's name — and a check SHALL fail
when one appears.

#### Scenario: A project value in an engine fails the check

- **WHEN** the repository name, an allowlisted author, a vocabulary value or the project's name appears in
  an engine
- **THEN** the check fails and names the value, the file and the line

#### Scenario: The check passes on the project it guards

- **WHEN** the check runs over the real tree
- **THEN** it passes and prints which engines it examined and which files it excluded

#### Scenario: The exclusions are deliberate and visible

- **WHEN** the check prints its exclusions
- **THEN** they are the source adapter that reads this project's own record format, the test fixtures and
  the project's documents, and no engine

#### Scenario: A change that leaks portability is caught where it is introduced

- **WHEN** a change introduces a project value into an engine
- **THEN** the check fails on that change, so the leak is found by the pipeline rather than by the second
  project

### Requirement: The check's value set is the project's own

The check SHALL read the values it forbids from the project's profile rather than carrying a list of its
own, so that a value added to the profile is immediately forbidden in an engine and the check cannot
become a stale list that fails open. Its value set SHALL be the whole of the project's vocabulary — every
namespace's values and their label forms, and the values the profile's `roles` section declares for an
engine's decisions — not one namespace of it. The labels the profile reserves to authorise work SHALL be
the one asserted exception: the check SHALL accept exactly one read-only declaration of such a label and
print it, and SHALL report any other occurrence as a leak.

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

### Requirement: A project with a different vocabulary runs the toolchain

A project whose repository, allowlisted authors, vocabulary and bands all differ from this project's SHALL
be able to run the whole toolchain — filing, selecting, updating, closing, reopening, a declared check, a
prioritisation pass and a loop run — without an edit to an engine.

#### Scenario: The whole toolchain runs in the foreign project

- **WHEN** the toolchain is run against a project with different repository, allowlist and vocabulary
- **THEN** every command succeeds and the issues it produced carry that project's vocabulary

#### Scenario: Identity is refused in the foreign project too

- **WHEN** the tooling runs in the foreign project as an identity that project does not allowlist
- **THEN** it refuses, because the gate is a behaviour and not a local configuration

#### Scenario: A project with no gate command still runs

- **WHEN** the foreign project declares no gate command
- **THEN** the loop's phases complete and its report says the gate was skipped

#### Scenario: A duplicate is refused in the foreign project

- **WHEN** the foreign project's fixture contains two findings with the same fingerprint
- **THEN** one issue is created and the second is reported as a duplicate, as in this project

### Requirement: The porting recipe is executable documentation

The document describing how to use the tooling in another project SHALL be verified: every command it
contains SHALL be executed by the check against the synthetic project, so the recipe cannot drift away from
the tooling.

#### Scenario: Every command in the recipe runs

- **WHEN** the recipe's commands are executed against the synthetic project
- **THEN** each succeeds, and the first failure fails the check

#### Scenario: The recipe assumes no build of this project

- **WHEN** the recipe is inspected
- **THEN** it names no build command of this project and no path outside the copied tree, so a project
  without that build can follow it unchanged

#### Scenario: The adaptation point is named

- **WHEN** a reader follows the recipe
- **THEN** it names the profile and the per-tool configuration as the files to edit and the source adapter
  as the file to replace or delete

#### Scenario: The cost of a copy is stated

- **WHEN** a reader considers adopting the tooling
- **THEN** the recipe states that an engine fix upstream means re-copying the engines, that a local engine
  edit means owning the merge, and that sharing the engines from one place remains an option the profile
  boundary keeps cheap

### Requirement: The synthetic project is a fixture, not a second project

The project used to prove portability SHALL live in the repository's fixtures, SHALL be built from a
throwaway working tree at test time, and SHALL be documented as modelling neither a second tracker, nor
another shell, nor another forge client.

#### Scenario: The fixture is versioned, not local

- **WHEN** the fixture is inspected
- **THEN** it is part of the repository's tracked content, so a fresh clone can run the proof

#### Scenario: The fixture's copy of the engines cannot drift

- **WHEN** the fixture runs the toolchain
- **THEN** it uses the engines in the repository rather than a copy of its own

#### Scenario: What the fixture does not model is written down

- **WHEN** a reader of the fixture asks whether their project is covered
- **THEN** the fixture states the assumptions it does not exercise

### Requirement: A value flag refuses a missing value before it consumes it

Every engine that parses a flag with a value SHALL check that the value is there before the arm reads and
consumes it, and SHALL refuse with a usage error naming the tool and the flag. The check SHALL also refuse a
next word that is itself a flag, so the refusal names the flag whose value is missing rather than the flag
that follows it. A parser SHALL never re-enter an iteration it has not consumed.

The self-test SHALL scan every engine for an arm that reads a value without that check, the self-test and
mutation harnesses included. Only the `*-cases.sh` files MAY be excluded, because they are harness.

No flag of this tooling accepts `--flag=value`: a value is the next argv word, and a free-text value that
begins with `--` is refused rather than passed.

#### Scenario: A bare value flag at the end of the arguments is a usage error

- **WHEN** an engine is given a value flag as its last argument and nothing after it
- **THEN** it exits 2 with `<tool>: <flag> needs a value` on stderr
- **Case**: `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `create#bareValueFlagNeedsAValue`

#### Scenario: A value flag does not take the next flag as its value

- **WHEN** a value flag is followed by another flag
- **THEN** the first flag is reported as the one missing its value, rather than the second flag being read as
  that value and the error surfacing later as the second flag's own validation
- **Case**: `usage#bareValueFlagDoesNotSwallowTheNextFlag`, `create#bareValueFlagNeedsAValue`

#### Scenario: A single-shot harness refuses a bare flag instead of running its whole set

- **WHEN** a self-test or mutation harness that parses one flag is given `--case` or `--only` with nothing
  after it
- **THEN** it exits 2 naming the flag, and no case runs, no mutation is applied
- **Case**: `usage#bareValueFlagInASingleShotHarnessIsAUsageError`

#### Scenario: A value flag written without the check is a red case

- **WHEN** an engine's flag arm reads a value without checking it first
- **THEN** the tooling's own self-test reports it, so a new flag cannot miss the rule
- **Case**: `usage#noUnguardedValueFlagRemains`
