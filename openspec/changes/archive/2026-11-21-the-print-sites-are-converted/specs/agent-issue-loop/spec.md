# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: No site prints a run fact without recording it

A fact the run makes visible SHALL be written to the run's own record by the same call that prints it, and the human line SHALL be rendered from the recorded values. A site in the engine that prints a run fact without recording it SHALL fail the suite, so the record is complete rather than aspirational.

#### Scenario: A converted site renders from its record

- **WHEN** a converted site is emitted
- **THEN** the record gains one event carrying the values and the printed line shows those same values, with no second composition beside it
- **Case**: `run#aConvertedSiteRendersFromItsRecord`

#### Scenario: No print-only site survives

- **WHEN** the suite reads the engine's owner files for lines that print and are no emit
- **THEN** every such line is either an emit call for a declared kind or a rendering `emit` owns, and a run fact printed beside the record fails the case
- **Case**: `driver#everyPrintOnlySiteIsConverted`

#### Scenario: A free-text reason survives the round trip

- **WHEN** an emitted kind carries a stop reason containing a quote and a newline
- **THEN** the line parses as one JSON object, the reason reads back whole, and the printed line shows it unescaped
- **Case**: `run#aFreeTextReasonSurvivesTheRoundTrip`

### Requirement: The record and the report's figures agree, or the difference is named

The report SHALL be able to recompute the run-level counts from the run's record and print them beside the figures it derives from the state. A difference SHALL be named on its own line with both numbers and the first item the record lacks, and a run whose record is absent SHALL be said to be one — never counted as agreement.

#### Scenario: The two readings agree on a completed run

- **WHEN** the report is asked for the record's reading of a run that ended with its record intact
- **THEN** the recomputed counts equal the state's figures and no difference line is printed
- **Case**: `report#theRecordAndTheStateAgree`

#### Scenario: A difference is named beside the figures

- **WHEN** the record holds fewer completed items than the state does
- **THEN** the report prints a difference line naming both numbers and the item the record lacks, and exits 0
- **Case**: `report#aDifferenceIsNamedBesideTheFigures`

#### Scenario: A run with no record says so

- **WHEN** the report is asked for the record's reading of a run whose record file is absent
- **THEN** it says the record is absent and makes no claim of agreement
- **Case**: `report#aRunWhoseRecordIsAbsentSaysSo`
