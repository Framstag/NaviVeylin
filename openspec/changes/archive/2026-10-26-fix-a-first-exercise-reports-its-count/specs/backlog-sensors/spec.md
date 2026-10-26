## ADDED Requirements

### Requirement: A row's first exercise is reported, not spent

A row that carries no measurement on record SHALL NOT write to the tracker on an unattended tick: the run SHALL report the count of findings it would have filed and write no condition, record or summary item. A run a person started SHALL record that first measurement, so the row has one from then on.

#### Scenario: The unattended tick reports a row's first exercise

- **WHEN** a scheduled tick runs a row whose issues carry no measurement marker, so the row has never measured
- **THEN** the run prints the count of findings it would have filed and writes nothing at all — no condition item, no record issue and no summary — so a new bound's blast radius is a number in the log before it is items on the tracker
- **Case**: `run#aFirstExerciseOnATickReportsInsteadOfFiling`

#### Scenario: A dispatched run records the first exercise

- **WHEN** a run a person started — a dispatch that asked to write, and equally a command run by hand, which names no trigger — runs a row whose issues carry no measurement marker
- **THEN** it files the way any settled row does, so the measurement it writes is the one every later run is compared against and the row is never left reporting its first exercise for the life of its bound
- **Case**: `run#aDispatchedFirstExerciseRecordsItsMeasurement`
