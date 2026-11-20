# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A fact the run records is printed from its record, once

Every fact SHALL be written to the run's own record before it is printed, by one call that does both, so a line and its record cannot disagree, and the human line SHALL be rendered from the recorded values, not composed beside them. This requirement covers the call sites this change lands — the run's header, the phase lines and the record's own rotation; converting the print sites of `run.sh` that still compose a line without recording it is `the-print-sites-are-converted`.

#### Scenario: A header fact is one event and the line shows it

- **WHEN** a run starts and prints its header
- **THEN** each fact of the header is one event in the run's record, and the printed line carries the values that event holds
- **Case**: `run#aFactPrintedOnceIsRecordedOnce`

### Requirement: The event vocabulary is declared, and an undeclared kind is refused

Each event kind SHALL be declared in one vocabulary file with the fields it must carry and the human line it renders to. An emit of an undeclared kind, or of a declared kind missing a required field, SHALL end the run with a refusal naming the kind and the field, before the fact is printed and before an item is recorded.

#### Scenario: An undeclared kind ends the run

- **WHEN** an emit names a kind the vocabulary does not declare
- **THEN** the run exits 3 with a refusal naming that kind, prints no line for it, and records no item result
- **Case**: `run#anUndeclaredKindStopsTheRun`

#### Scenario: A missing required field ends the run

- **WHEN** an emit of a declared kind omits a field the vocabulary requires
- **THEN** the run exits 3 naming the kind and the absent field, and the record gains no line for it
- **Case**: `run#aDeclaredKindMissingAFieldStopsTheRun`

### Requirement: The run's record is bounded, and a rotation is named

The record SHALL be bounded by a recorded value, and a rotation SHALL be recorded in the record it starts. A reader SHALL be able to read a rotated record as the lower bound it is, never as a complete account.

#### Scenario: A record past its bound rotates

- **WHEN** the next event would carry the record past the configured byte bound
- **THEN** the record is rotated and the new file's first event says the record was rotated
- **Case**: `run#aFullRecordRotatesAndSaysSo`

#### Scenario: The rotation keeps what it replaced

- **WHEN** a record is rotated
- **THEN** the replaced file is kept beside it and the rotation event names it
- **Case**: `run#theRotationKeepsTheOlderFile`

#### Scenario: The bound is a recorded value with a default

- **WHEN** the profile records no byte bound for the record
- **THEN** the run uses the default bound and names the value it used in its header
- **Case**: `run#theBoundIsARecordedValue`

### Requirement: No event carries a coordinate

An event SHALL carry an identity — an issue, a phase, a path inside the project, a count — and SHALL NOT carry a coordinate or a secret. The rule SHALL be enforced over the vocabulary and over every call site, not over a reviewer's reading.

#### Scenario: No kind's fields admit a position

- **WHEN** the suite reads the vocabulary's field declarations
- **THEN** no kind declares a latitude, longitude or a coordinate-shaped field, and a row that does fails the case
- **Case**: `driver#noEventCarriesACoordinate`

#### Scenario: An emitted fact names an identity

- **WHEN** a run emits the event for an item and for a phase
- **THEN** the event names the issue number and the phase name and no position, and a call site passing a coordinate-shaped value is refused by the emit
- **Case**: `run#anEventNamesAnIssueAndAPhaseNeverAPosition`
