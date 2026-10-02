# Spec Delta — auto-diagnostics

## ADDED Requirements

### Requirement: Coordinate-carrying entries do not survive the retention pass

The system SHALL remove an existing diagnostic entry that carries a device position during the
retention pass, **regardless of the entry's age**: the retention window SHALL NOT be the only reason a
line is dropped, so a position written before this rule existed does not stay in the file for the rest
of the window.

- An entry carries a device position when it names a coordinate — a latitude/longitude token,
  delimited as a field — together with a number at coordinate precision
- The rule SHALL also recognise a position written without such a name: two coordinate-precision
  numbers written as one comma-separated pair
- An entry that carries precision-free identity instead SHALL be kept: magnification, screen pixel,
  map database or map file name, accuracy, bearing, object label and/or object id
- The removal SHALL cover the active file and the rotated file
- The removal SHALL run inside the retention pass on the logging worker; a log or diagnostics call
  SHALL NOT perform it, or any other filesystem work, on the calling thread
- The removal SHALL be reported once per pass, and the report SHALL NOT name the removed position
- The rule SHALL NOT alter what is logged at runtime (new entries remain the build gate's concern),
  and SHALL NOT affect user-facing coordinate display (labels, sheets, favourites)

#### Scenario: A pre-change coordinate entry is removed on the first pass

- **WHEN** the log file contains a position-carrying entry written by an earlier build, and that entry is younger than the retention window
- **THEN** the entry is gone from the file after the logging worker's first retention pass
- **AND** it is gone from the rotated file as well

#### Scenario: A position without a coordinate field name is removed too

- **WHEN** the log contains an entry whose position is an unnamed comma-separated pair of numbers at coordinate precision (the pre-redaction car render shape)
- **THEN** the entry is removed by the retention pass as well

#### Scenario: Identity entries survive the purge

- **WHEN** the log contains entries that name a magnification, a screen pixel, a map database or map file name, an accuracy, a bearing, or an object label/id
- **THEN** every one of those entries is still present after the retention pass

#### Scenario: The removal is reported without naming the position

- **WHEN** a position-carrying entry is removed
- **THEN** the diagnostics record the removal once for that pass
- **AND** neither the report nor any other new entry contains the removed position

#### Scenario: Purging never blocks the caller

- **WHEN** a log or diagnostics call is made while a coordinate purge is pending
- **THEN** the calling thread performs no filesystem work (bounded in-memory enqueue only)

#### Scenario: Age pruning is unchanged

- **WHEN** the log contains entries older than the retention window and entries that carry no position
- **THEN** the old entries are removed exactly as before, and no coordinate-free entry is removed by the coordinate rule
