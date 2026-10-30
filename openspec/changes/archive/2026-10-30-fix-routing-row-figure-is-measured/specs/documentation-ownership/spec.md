# documentation-ownership — deltas

## ADDED Requirements

### Requirement: A routing row's cost is the measurement it claims

A routing row that prices a lettered sub-section SHALL state the section's measured cost, in kB of
bytes/1000 rounded to one decimal: a figure written after the reference SHALL equal that section's cost, and a
range SHALL state the cheapest and the costliest of the lettered sections the row names. A figure the row writes
as a past measurement SHALL NOT be compared. A disagreement SHALL be reported with the row, the reference and
both numbers, without a build, a device or a network.

#### Scenario: A figure beside a reference is the section's measurement

- **WHEN** a routing row states a `kB` figure after a lettered sub-section reference that is not that section's
  cost under the requirement's ruler
- **THEN** the check exits non-zero and reports the row, the reference, the stated figure and the measured one,
  and a row stating the measurement passes
- **Case**: `routed-read#figure` — over a fixture tree the check reports `0.1 kB` against a section measuring
  `1.0 kB`, and accepts the same row once it states `1.0 kB`; the project's own table must pass

#### Scenario: A range is the span of the sections the row names

- **WHEN** a routing row states a range over the lettered sub-sections it names, and that range is not the
  cheapest and the costliest of those sections
- **THEN** the check exits non-zero and reports the row and the measured span, a row stating the measured span
  passes, and a figure the row states as a past measurement is not compared
- **Case**: `routed-read#range` — over a fixture tree the check reports `1.2–2.0 kB each` for sections measuring
  `1.0–3.1 kB`, accepts `1.0–3.1 kB each`, and leaves the row's `against 23.2 kB for §1 before the split` alone
