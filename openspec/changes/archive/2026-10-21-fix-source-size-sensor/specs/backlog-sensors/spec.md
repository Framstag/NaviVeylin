# backlog-sensors — deltas

## ADDED Requirements

### Requirement: A source file's size is a declared check

A project's checks SHALL include a measurement of a source file's own size, so that a source file too large to
hold in one reading is a condition a check reports rather than something only a reviewer notices. The bound SHALL
be the row's, so retuning the check is one edit in the registry; the unit and the language scope are the
measurement's own, fixed by the owner's recorded decision, and a distinct path SHALL be a distinct condition, so
an item closes when that path drops under the bound.

#### Scenario: A file past the bound is one condition, per path

- **WHEN** a tracked source file of the measured languages is measured past the row's bound
- **THEN** it is reported as a violation carrying that file's line count and a fingerprint derived from its
  path, and a file inside the bound is not reported
- **Case**: `sensors#sourceSizeRow`

#### Scenario: The bound is the row's, the unit and the scope are the measurement's

- **WHEN** the measurement runs
- **THEN** the bound it applies comes from the registry row and appears nowhere in the measurement, and a file
  outside the measured languages is not a condition — so retuning the check is one edit in the registry, while a
  change of unit or scope is a change to the recorded decision the measurement's own header names
- **Case**: `sensors#sourceSizeRow`

#### Scenario: A tree inside the bound says so

- **WHEN** no tracked source file of the measured languages is past the bound and no condition is recorded
- **THEN** the row reports a clear keyed to the row's own identity (`"path":"none"`), because the runner reads a
  row that printed nothing as a failed check
- **Case**: `sensors#sourceSizeRow`

#### Scenario: A recorded condition inside the bound clears under its own path

- **WHEN** a recorded open condition's path is measured inside the bound
- **THEN** the row reports a clear naming that path and its line count, so the issue carrying the condition closes
  on the identity it holds
- **Case**: `sensors#sourceSizeRow`
