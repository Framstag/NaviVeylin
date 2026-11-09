# Spec Delta — tooling-test-layout

## Purpose

Where the repository tooling's test code lives: the `tests/` directory that owns a test driver, its mutation
sets and its fixtures, the naming rule that keeps a move a path edit, the depth-independent root resolution a
moved driver uses, the location-based exclusion the portability check applies, and the rule that live
references follow the tests while archived evidence does not.

## ADDED Requirements

### Requirement: Tooling test code sits under the owning group's tests directory

Every tooling test driver, the mutation sets it reads and the fixtures it uses SHALL live under one `tests/`
directory: `tools/<group>/tests/` when the tool it exercises sits in a group directory (`loop`, `issues`,
`sensors`), and `tools/tests/` when the tool sits at the `tools/` root. A production tool SHALL NOT move,
including one whose name ends in `-cases.sh`.

#### Scenario: A grouped tool's test moves beside its tool

- **WHEN** a test of a tool inside `tools/loop/`, `tools/issues/` or `tools/sensors/` is moved
- **THEN** it lives in that group's `tests/` directory and the tool it exercises does not move
- **Case**: none — the move is structural; `git status` records the rename and the moved driver's own run is the check

#### Scenario: A root-level tool's test lands in tools/tests/

- **WHEN** a test of a tool that sits at the `tools/` root is moved
- **THEN** it lives in `tools/tests/`
- **Case**: none — the same structural check: `git status` records the rename

#### Scenario: A production tool is not moved for its name

- **WHEN** a production tool sits at the `tools/` root and its name ends in `-cases.sh`
- **THEN** it stays where it is, because the rule moves test code and not files that merely look like it
- **Case**: none — `git status` names no rename of it and `bash tools/declared-cases.sh --help` still runs

### Requirement: A moved test resolves the tree without assuming its depth

A moved test driver SHALL resolve the repository root from the tree it sits in (`git rev-parse
--show-toplevel`, or a form that does not assume the driver's own depth), so a driver under
`tools/<group>/tests/` or `tools/tests/` finds the tree; and every reference a test makes to another test, a
fixture or a mutation set SHALL name that file's new path.

#### Scenario: A driver run from the new depth finds the tree

- **WHEN** a moved driver is invoked from any working directory
- **THEN** it resolves the repository root and reaches the tool it exercises
- **Case**: none — each moved selftest's `--list` run from a directory that is not the repository root is the check

#### Scenario: A cross-reference names the new path

- **WHEN** a test names another test, a fixture or a mutation set
- **THEN** the named file's path is its post-move path
- **Case**: none — a `grep` over `tools/` for a pre-move test path is the check

### Requirement: The portability check excludes tests by location

`tools/check-portability.sh` SHALL derive its engine file set from production tool directories and SHALL skip
every `tests/` directory by location, not by a file-name suffix, so a new test file is never scanned and a
moved one cannot be missed.

#### Scenario: A test directory is not scanned

- **WHEN** the check derives the files it reads a project value from
- **THEN** no file under a `tests/` directory is among them, whatever its name
- **Case**: `portability#testsDirectoryIsNotScanned`

#### Scenario: A production engine is still scanned

- **WHEN** the check derives the files it reads a project value from
- **THEN** every production tool directory is still read
- **Case**: `portability#productionEnginesAreStillScanned`

### Requirement: Live references follow the tests, archived evidence does not

The change SHALL update every live reference to a moved test — the CI workflow, the guidelines, the skills,
`PORTING.md`, `AGENTS.md`, the affected specs and each mutation set's `case-cmd` — and SHALL leave the
archived evidence under `openspec/changes/archive/**` byte-identical.

#### Scenario: A live reference names the new path

- **WHEN** a live file names a moved test
- **THEN** the reference is its post-move path
- **Case**: none — the `grep` in task 5.5 over live files is the check

#### Scenario: Archived evidence is not rewritten

- **WHEN** an archived evidence file names a moved test
- **THEN** its bytes are unchanged
- **Case**: none — `git diff --stat` names no file under `openspec/changes/archive/`

### Requirement: A moved test file keeps its name

A moved test SHALL keep its file name, so a reference changes only the directory and never the file.

#### Scenario: A reference changes only the directory

- **WHEN** a moved test is referenced
- **THEN** the file's base name is the same before and after the move
- **Case**: none — the base name is compared before and after the move
