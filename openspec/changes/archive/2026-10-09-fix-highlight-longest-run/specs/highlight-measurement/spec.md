# Spec Delta

## ADDED Requirements

### Requirement: A row qualifies by its longest colour run

The highlight detector in `tools/measure-highlight.py` SHALL qualify a screenshot row by that row's
**longest** horizontal run of casing-coloured pixels, wherever in the row it lies, and SHALL report that
run's extent and length as the row's contribution to the bounding box and pixel count. The run-length
threshold SHALL be applied to that run and SHALL NOT be raised.

#### Scenario: The longest run is not the rightmost one

- **WHEN** a row holds a long casing-coloured run followed by a shorter one
- **THEN** the row SHALL still qualify as a highlight row
- **AND** the reported bounding box SHALL be the long run's extent, counted as that run's length
- **Case** `bash tools/measure-highlight-selftest.sh` case `longest-run-not-last` — one row, a 41 px run followed by a 5 px one; expects `"bbox": [10, 50, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: The longest run is the rightmost one

- **WHEN** a row holds a short casing-coloured run followed by a longer one
- **THEN** the row SHALL qualify with the long run's extent and length, as it did before the rule was stated
- **Case** `bash tools/measure-highlight-selftest.sh` case `longest-run-is-last` — one row, a 5 px run followed by a 41 px one; expects `"bbox": [100, 140, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: Each qualifying row contributes its own longest run

- **WHEN** two rows qualify and their longest runs lie on different sides of their rows
- **THEN** the bounding box SHALL span both longest runs and the count SHALL be their sum
- **Case** `bash tools/measure-highlight-selftest.sh` case `longest-run-per-row` — a row whose longest run is the left one and a row whose longest run is the right one; expects `"bbox": [10, 100, 5, 6]`, `"highlight_px": 82`, `"inside": true`, exit 0

#### Scenario: One run per row is measured as before

- **WHEN** a row holds a single casing-coloured run
- **THEN** that run SHALL be the row's contribution, unchanged by the longest-run rule
- **Case** `bash tools/measure-highlight-selftest.sh` case `single-run` — one row, one 41 px run; expects `"bbox": [10, 50, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: A single short run never qualifies a row

- **WHEN** a row's only casing-coloured run is shorter than the threshold
- **THEN** the row SHALL NOT qualify and the tool SHALL report no highlight with its no-highlight exit code
- **Case** `bash tools/measure-highlight-selftest.sh` case `short-only` — one 5 px run in a row; expects `"highlight": null`, exit 2

#### Scenario: Short runs are neither summed nor measured by the row's extent

- **WHEN** a row holds two casing-coloured runs that are each shorter than the threshold
- **THEN** the row SHALL NOT qualify, however far apart the runs lie
- **Case** `bash tools/measure-highlight-selftest.sh` case `two-short-runs` — two 5 px runs far apart in one row; expects `"highlight": null`, exit 2
