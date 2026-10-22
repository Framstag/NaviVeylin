## ADDED Requirements

### Requirement: A declared mutation record is checked against the file it names

The mutation harness SHALL offer a check that applies every selected record's expressions to a copy of the file the
record names, and reports each record where one of them changes nothing — running no case, mutating nothing. It
SHALL name a stale record by its label and its file, exit 1 when one is stale, and exit 0 only when every selected
record applies. The project's gate SHALL run it for a delta that touches a path under the engine's directory or the
set itself.

#### Scenario: A record that names no current line is reported

- **WHEN** one declared record's expressions are pointed at a file in which one of them changes nothing, and the
  check is run for that record
- **THEN** it exits 1 and prints `MUTATION-PATTERN-STALE`, the record's label and the file the record names, having
  run no case
- **Case**: `mutation#aStaleRecordIsNamed`

#### Scenario: A set in which every record applies passes

- **WHEN** the check is run over the whole declared set against the engine the set is declared in
- **THEN** it exits 0, prints no `MUTATION-PATTERN-STALE` line, and its summary counts the records the harness
  declares
- **Case**: `mutation#everyDeclaredRecordApplies`

#### Scenario: The gate owes the check for an engine path

- **WHEN** a delta touches a path under the engine's directory and the gate is asked what that delta owes without
  running anything
- **THEN** the record check is one of the owed checks, named with the command that runs it
- **Case**: `gate#anEnginePathOwesTheRecordCheck`
