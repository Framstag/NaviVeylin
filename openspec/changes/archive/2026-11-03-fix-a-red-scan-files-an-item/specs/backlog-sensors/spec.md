# Spec Delta

## ADDED Requirements

### Requirement: A pipeline outside the build publishes its own record, and its row waits for it

A workflow the build does not run SHALL be monitored by one row and SHALL publish the record that row reads:
every run states its result and, when red, the step that broke and the command it ran, in one artifact both
the workflow and the checks' run name. The publisher SHALL pair that path with a producer and SHALL NOT write
or require it. The checks' run SHALL fetch the record for the commit under test and select the row only once a
completed run carries it.

#### Scenario: A red scan is one condition, and its identity is the step that broke

- **WHEN** the record of the commit under test carries a failed result and names the step that broke
- **THEN** the row files one condition whose identity is that job and step, so a second, unrelated failing step
  is a second item rather than a reopening of the first one's record, and the item's text names the command
  that failed
- **Case**: `sensors#aFailedScanIsItsOwnCondition`

#### Scenario: A green scan clears the condition it recorded

- **WHEN** the record of the commit under test carries a successful result and an item is open under this row's
  own recorded condition
- **THEN** the run closes that item, and a green record with nothing recorded still reports a row-level clear
  rather than printing nothing
- **Case**: `sensors#aGreenScanClearsItsCondition`

#### Scenario: The record is fetched from its own pipeline, and an unfinished one selects no row

- **WHEN** the checks' run starts on the main branch and the second workflow's run for that commit has not
  completed yet
- **THEN** the run asks that workflow for the one artifact name the workflow publishes and restores the
  declared path, so the row's command learns nothing about where its input came from — and while no completed
  run carries the record, the row is selected by no tick, nothing is written to the tracker for it, and the run
  reports no failed check, because the second pipeline's readiness is reported apart from the build record's
- **Case**: `workflow#theScanRecordIsFetchedBeforeItsRowRuns`

#### Scenario: The publisher pairs the path without writing it

- **WHEN** a row declares a path the second pipeline publishes
- **THEN** the publisher's manifest and its registry check name that path, so the declaration is not refused,
  and the build's own write neither writes the file nor fails for its absence
- **Case**: `inputs#theScanRecordIsTheScanPipelinesOwn`

#### Scenario: The workflow publishes its record for every outcome

- **WHEN** the second workflow's run ends, green or red
- **THEN** the record it writes and the artifact that carries it both run on `always()`, the artifact carries
  the path the row declares, and a run that publishes nothing is reported by the workflow's own structural
  check rather than passing as healthy
- **Case**: `sonar-workflow#theRecordIsPublishedOnEveryOutcome`
