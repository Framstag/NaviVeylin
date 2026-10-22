# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A mutation run is scoped by the files the change touches

The mutation harness SHALL select its records by the file each record mutates as well as by its label: a
named file set SHALL run exactly the records whose file is one of the named paths, and a base commit SHALL
name that set by the paths the diff against it touches, together with the working tree. A filtered run SHALL
state what it selected against the declared set (`selected N of M records`), and a selection that names no
record SHALL exit 2 without mutating anything. A run that names neither a file nor a label SHALL run the whole
set, exactly as before.

#### Scenario: A file set lists exactly the records those files own

- **WHEN** the harness lists the selection of a file set whose path one or more records mutate
- **THEN** it prints those records and no other, and the list is the set the run would execute
- **Case**: `tools/loop-mutations-selftest.sh` (`a file set lists exactly the records its file owns`)

#### Scenario: A scoped run runs exactly those records and says how much of the set it ran

- **WHEN** the harness runs a file set whose path one or more records mutate, against an engine copy
- **THEN** exactly those records are mutated and go red, each restore goes green, the harness prints
  `selected N of M records`, and the copy is byte-identical to the pristine engine afterwards
- **Case**: `tools/loop-mutations-selftest.sh` (`a file set runs the records its file owns`)

#### Scenario: A selection no record owns is refused

- **WHEN** the named file set contains no file any record mutates
- **THEN** the harness exits 2, says the selection names no record of the declared set, and runs no record — a
  green exit for having done nothing is the silent lie the harness exists to prevent
- **Case**: `tools/loop-mutations-selftest.sh` (`a file set no record owns exits 2`)

#### Scenario: A base commit names the set by the diff it touches

- **WHEN** the harness is given a base commit in a tree where one record's file is changed since that commit
- **THEN** the selection is that record, derived from the diff and the working tree, and no other
- **Case**: `tools/loop-mutations-selftest.sh` (`a git diff names the records its file owns`)
