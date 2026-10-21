# backlog-sensors — deltas

## ADDED Requirements

### Requirement: A source unit's size is a declared check

A project's checks SHALL include a measurement of a source unit's own size, so that a source file too large to
hold in one reading is a condition a check reports rather than something only a reviewer notices. The unit, the
language scope and the bound SHALL be the row's — declared in the registry, never in the measurement — and a
distinct unit (one path) SHALL be a distinct condition, so an item closes when that path drops under the bound.

#### Scenario: A file past the bound is one condition, per path

- **WHEN** a tracked source file of a declared language is measured past the row's bound
- **THEN** it is reported as a violation carrying that file's line count and a fingerprint derived from its
  path, and a file inside the bound is not reported
- **Case**: `sensors#sourceSizeRow`

#### Scenario: The bound, the unit and the scope are the row's

- **WHEN** the measurement runs
- **THEN** the bound it applies comes from the registry row and appears nowhere in the measurement, and a file
  whose language the row does not declare is not a condition — so retuning the check is one edit in the
  registry and a change of scope is a change of declaration
- **Case**: `sensors#sourceSizeRow`

#### Scenario: A tree inside the bound says so

- **WHEN** no tracked source file of the declared languages is past the bound and no condition is recorded
- **THEN** the row reports a clear naming its own identity, because the runner reads a row that printed nothing
  as a failed check
- **Case**: `sensors#sourceSizeRow`
