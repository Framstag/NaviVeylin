# Spec Delta — highlight-measurement

## ADDED Requirements

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
- **Case**: `bash tools/measure-highlight-selftest.sh` case `no-state` — issue #90's glyph pair (18 px over two rows) with no `--state`; expects `"highlight": null`, `"refused": "no-state"`, exit 2

#### Scenario: Diagnostics without the analysed-segment line are refused

- **WHEN** the supplied diagnostics carry no `MapCanvasVM: segment focus:` line with a `band=[0,N]`
- **THEN** the tool SHALL report no highlight, name the refusal, and exit 2
- **Case**: `bash tools/measure-highlight-selftest.sh` case `no-focus-line` — a capture of unrelated app lines beside the same glyph pair; expects `"highlight": null`, `"refused": "no-focus-line"`, exit 2

#### Scenario: A lookalike diagnostics line under another tag is refused

- **WHEN** the supplied diagnostics carry a `segment focus:` line with a `band=[0,N]` but not the literal `MapCanvasVM: segment focus:` tag
- **THEN** the tool SHALL report no highlight, name `no-focus-line` as the refusal reason, and exit 2
- **Case**: `bash tools/measure-highlight-selftest.sh` case `wrong-tag` — an `Other: segment focus:` line beside the same glyph pair; expects `"highlight": null`, `"refused": "no-focus-line"`, exit 2

#### Scenario: A band that ends at the canvas bottom is no card measured

- **WHEN** the analysed-segment line reports a band ending at the canvas bottom
- **THEN** the tool SHALL refuse, because the frame is not in the state it assumes
- **Case**: `bash tools/measure-highlight-selftest.sh` case `no-card` — the glyph pair with a `band=[0,20]` line on a 20 px canvas; expects `"highlight": null`, `"refused": "no-card"`, exit 2

#### Scenario: A band the two moments disagree on is refused

- **WHEN** the band the detector measures against is not the band the same-moment line reports
- **THEN** the tool SHALL refuse, because the shot and the diagnostics are not one moment
- **Case**: `bash tools/measure-highlight-selftest.sh` case `band-disagrees` — the glyph pair measured against band 15 with a `band=[0,16]` line; expects `"highlight": null`, `"refused": "band-disagrees"`, exit 2
