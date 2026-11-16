# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A guard names the issue that patronises it

A guard the engine enforces SHALL cite in its own comment the issue whose failure put it there, in the form the engine already uses. A guard citing none SHALL be counted as one with no patron written down rather than read as an error, so the census reports it instead of refusing it.

#### Scenario: A new guard cites its patron

- **WHEN** a case adds a guard to an engine file
- **THEN** the guard's comment cites the issue number the case was filed for, and the census reads that number
- **Case**: `census#everyGuardNamesItsPatron`

#### Scenario: A guard without a patron is counted, not refused

- **WHEN** a guard's comment cites no issue number
- **THEN** the census lists it under "no patron written down" and the tool exits 0
- **Case**: `census#aGuardWithoutAPatronIsCounted`

#### Scenario: The citation form is the engine's own

- **WHEN** the census reads the engine for cited numbers
- **THEN** it reads the parenthesised and the bare form and no free text, so a number mentioned in prose is not mistaken for a patron
- **Case**: `census#theCitationFormIsRead`

### Requirement: The census counts the guards no open issue and no case holds

The census SHALL print three counts — guards whose cited item is closed, guards whose refusal appears in no case body, and guards citing no issue — and a total. A count it could not take SHALL be named as unread, never printed as zero.

#### Scenario: The three counts are printed

- **WHEN** the census runs against the tracker it was given
- **THEN** it prints the three counts and a total, each on one line, and `--json` carries the lists behind them
- **Case**: `census#theCensusIsANumber`

#### Scenario: A closed patron with no case is one guard in two counts

- **WHEN** a fixture engine holds a guard whose cited item is closed and whose phrase no fixture case carries
- **THEN** the guard appears in both counts and in the total once
- **Case**: `census#aClosedPatronWithNoCaseIsCounted`

#### Scenario: An unread count is named, never zero

- **WHEN** the tracker query fails
- **THEN** the patron count is printed as unread with the reason, and the two offline counts are still printed
- **Case**: `census#anUnreadCountIsNamed`

### Requirement: The census refuses nothing and stops no run

The census SHALL NOT write state, change a label or end a run, and a run SHALL NOT consult it. Its threshold SHALL be the change's own text, never a value the profile can set, so a healthy engine cannot be argued into a cleanup.

#### Scenario: The census writes nothing

- **WHEN** the census runs on any tree
- **THEN** it leaves the state directory, the tracker and the labels as it found them
- **Case**: `census#theCensusRefusesNothing`

#### Scenario: A run does not consult the census

- **WHEN** a run is driven from the stub-`pi` fixtures
- **THEN** nothing in the run's path invokes the census, and a tracker that refuses the census does not change the run's verdict
- **Case**: `census#aRunDoesNotConsultTheCensus`
