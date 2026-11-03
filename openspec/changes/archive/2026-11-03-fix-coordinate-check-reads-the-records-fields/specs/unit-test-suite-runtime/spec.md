# unit-test-suite-runtime — deltas

## ADDED Requirements

### Requirement: A coordinate check decides on the record's fields, not on the line's timing numbers

A case that asserts a diagnostics record carries no coordinate SHALL decide it from the fields the record's own
marker introduces, and SHALL NOT decide it from a substring of the whole line: the reader prefixes every line
with `[yyyy-MM-dd HH:mm:ss.SSS]`, whose `ss.SSS` field reads `13.412` at second `:13`, and a record also carries
its own measurements (`duration=13ms`, `deviation_m=40.0`) — so a clock reading or a measurement satisfied the
check and the case's result was decided by elapsed real time (issue #728).

#### Scenario: A stamp that reads like a coordinate is not one

- **WHEN** the calculation record is read from a file whose stamp is `[2026-11-03 13:41:13.412]` and whose
  `duration=` field is `13ms`
- **THEN** the case decides on the record's own key set and on the coordinate shape of each field value, and
  stays green
- **Case**: `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`

#### Scenario: A distance field is not a position

- **WHEN** the reroute record's `deviation_m=` value is a decimal distance and the line's stamp carries seconds
  `52` with milliseconds `5xx`
- **THEN** the case decides on the record's own `offroute_ms`, `confirm_ms` and `deviation_m` fields, and stays
  green
- **Case**: `NavigationEngineRerouteTest.aConfirmedRerouteWritesTheLatencyDiagnosticLine`

#### Scenario: A coordinate in the record still fails the case

- **WHEN** a position field is interpolated into the calculation record
- **THEN** the case fails, naming the field the schema does not allow
- **Case**: `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`
