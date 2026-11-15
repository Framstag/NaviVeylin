# agent-issue-loop delta

## ADDED Requirements

### Requirement: The archive's classes are derived from the record, not from a field's absence

A run's report SHALL total an archive's items by the class each record decides for itself — the `stopReason` it names,
or the bound its own note quotes — and SHALL NOT read a field's absence as a class. The population SHALL be the
records a class can belong to, `blocked` and `cut`. The records the derivation cannot decide, and the rounds a bound
ended, SHALL each be printed as a figure of their own and summed into neither side. The analysis pass's rates SHALL
read the same derivation.

#### Scenario: A record whose own note names the bound that ended it is decided

- **WHEN** an archived record is `blocked` and names no `stopReason`, and its note quotes the `LOOP-TIMEOUT` sentence
  that ended it
- **THEN** the report's class line totals it as `bound`, beside the record that recorded its own mechanism as
  `refused`
- **Case**: `report#theArchivesClassesAreDerivedNotInvented`

#### Scenario: What the derivation cannot decide is printed apart, never as a class

- **WHEN** an archived `blocked` or `cut` record names no mechanism and no bound its note quotes
- **THEN** it is counted as undecidable beside the decided classes — as is the record a review round's own bound ended,
  which carries no class of the item's — and no class of its own is invented for it
- **Case**: `report#theArchivesClassesAreDerivedNotInvented`

#### Scenario: A record a class cannot belong to is counted on neither side

- **WHEN** an archived record closed, was skipped or was falsified
- **THEN** it is in neither the decided nor the undecidable figure, and the denominator is the archived `blocked` and
  `cut` records only
- **Case**: `report#theArchivesClassesAreDerivedNotInvented`

#### Scenario: An archive with no record a class can belong to prints an absence, not a zero

- **WHEN** the run's report reads an archive that holds no record `blocked` or `cut`, or a project with no archive at
  all
- **THEN** the class line prints that there is no such record, rather than a class or a rate nobody measured
- **Case**: `report#theArchivesClassesAreDerivedNotInvented`