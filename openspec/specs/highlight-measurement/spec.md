# highlight-measurement Specification

## Purpose
How a device screenshot's analysed-segment highlight is located and measured reproducibly, with no device
and no image editor: the row rule the detector applies, the extent and pixel count it reports for a row, and
the exit-code/verdict contract its callers depend on (`tools/measure-highlight.py`, device-free harness
`tools/tests/measure-highlight-selftest.sh`; used by the `pixel-check` workflow and `guidelines/Build.md` §11).

## Requirements

### Requirement: A row qualifies by its longest colour run

The highlight detector in `tools/measure-highlight.py` SHALL qualify a screenshot row by that row's
**longest** horizontal run of casing-coloured pixels, wherever in the row it lies, and SHALL report that
run's extent and length as the row's contribution to the bounding box and pixel count. The run-length
threshold SHALL be applied to that run and SHALL NOT be raised.

#### Scenario: The longest run is not the rightmost one

- **WHEN** a row holds a long casing-coloured run followed by a shorter one
- **THEN** the row SHALL still qualify as a highlight row
- **AND** the reported bounding box SHALL be the long run's extent, counted as that run's length
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `longest-run-not-last` — one row, a 41 px run followed by a 5 px one; expects `"bbox": [10, 50, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: The longest run is the rightmost one

- **WHEN** a row holds a short casing-coloured run followed by a longer one
- **THEN** the row SHALL qualify with the long run's extent and length, as it did before the rule was stated
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `longest-run-is-last` — one row, a 5 px run followed by a 41 px one; expects `"bbox": [100, 140, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: Each qualifying row contributes its own longest run

- **WHEN** two rows qualify and their longest runs lie on different sides of their rows
- **THEN** the bounding box SHALL span both longest runs and the count SHALL be their sum
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `longest-run-per-row` — a row whose longest run is the left one and a row whose longest run is the right one; expects `"bbox": [10, 100, 5, 6]`, `"highlight_px": 82`, `"inside": true`, exit 0

#### Scenario: One run per row is measured as before

- **WHEN** a row holds a single casing-coloured run
- **THEN** that run SHALL be the row's contribution, unchanged by the longest-run rule
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `single-run` — one row, one 41 px run; expects `"bbox": [10, 50, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0

#### Scenario: A single short run never qualifies a row

- **WHEN** a row's only casing-coloured run is shorter than the threshold
- **THEN** the row SHALL NOT qualify and the tool SHALL report no highlight with its no-highlight exit code
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `short-only` — one 5 px run in a row; expects `"highlight": null`, exit 2

#### Scenario: Short runs are neither summed nor measured by the row's extent

- **WHEN** a row holds two casing-coloured runs that are each shorter than the threshold
- **THEN** the row SHALL NOT qualify, however far apart the runs lie
- **Case** `bash tools/tests/measure-highlight-selftest.sh` case `two-short-runs` — two 5 px runs far apart in one row; expects `"highlight": null`, exit 2

### Requirement: A verdict requires the same-moment frame state

The highlight detector in `tools/measure-highlight.py` SHALL report a verdict — `inside` or `clipped` — only when
the same-moment diagnostics establish that an analysed segment was on screen: a `--state` capture carrying a
`MapCanvasVM: segment focus:` line whose `band=[0,N]` ends above the canvas bottom and equals the band the
detector measures against. Without such a line the detector SHALL refuse rather than report: it SHALL report no
highlight, name the reason in its verdict line and in its JSON output, and exit with its no-highlight code 2. The
exit-code contract SHALL stay at three values, and a refusal SHALL never be printed as `inside` or `clipped`.

#### Scenario: A frame with no same-moment state is refused

- **WHEN** a screenshot carries a casing-coloured match but no same-moment diagnostics are supplied
- **THEN** the tool SHALL report no highlight, name `no-state` as the refusal reason, and exit 2
- **Case**: `bash tools/tests/measure-highlight-selftest.sh` case `no-state` — issue #90's glyph pair (18 px over two rows) with no `--state`; expects `"highlight": null`, `"refused": "no-state"`, exit 2

#### Scenario: Diagnostics without the analysed-segment line are refused

- **WHEN** the supplied diagnostics carry no `MapCanvasVM: segment focus:` line with a `band=[0,N]`
- **THEN** the tool SHALL report no highlight, name the refusal, and exit 2
- **Case**: `bash tools/tests/measure-highlight-selftest.sh` case `no-focus-line` — a capture of unrelated app lines beside the same glyph pair; expects `"highlight": null`, `"refused": "no-focus-line"`, exit 2

#### Scenario: A lookalike diagnostics line under another tag is refused

- **WHEN** the supplied diagnostics carry a `segment focus:` line with a `band=[0,N]` but not the literal `MapCanvasVM: segment focus:` tag
- **THEN** the tool SHALL report no highlight, name `no-focus-line` as the refusal reason, and exit 2
- **Case**: `bash tools/tests/measure-highlight-selftest.sh` case `wrong-tag` — an `Other: segment focus:` line beside the same glyph pair; expects `"highlight": null`, `"refused": "no-focus-line"`, exit 2

#### Scenario: A band that ends at the canvas bottom is no card measured

- **WHEN** the analysed-segment line reports a band ending at the canvas bottom
- **THEN** the tool SHALL refuse, because the frame is not in the state it assumes
- **Case**: `bash tools/tests/measure-highlight-selftest.sh` case `no-card` — the glyph pair with a `band=[0,20]` line on a 20 px canvas; expects `"highlight": null`, `"refused": "no-card"`, exit 2

#### Scenario: A band the two moments disagree on is refused

- **WHEN** the band the detector measures against is not the band the same-moment line reports
- **THEN** the tool SHALL refuse, because the shot and the diagnostics are not one moment
- **Case**: `bash tools/tests/measure-highlight-selftest.sh` case `band-disagrees` — the glyph pair measured against band 15 with a `band=[0,16]` line; expects `"highlight": null`, `"refused": "band-disagrees"`, exit 2
