# Proposal — a filing's title names the drift it reports

## Why

**Root cause.** Three rows pass a **constant** string as a finding's title, and `sensors_file` uses a
finding's title verbatim (`tools/sensors/run.sh:275-276` — the `${id}: ${measurement}` fallback never runs
because the title is not empty), so two conditions of one row reach the tracker under **one title**:

- `tools/sensors/metrics/metadata-invariant.sh:70` — `"an item's own record and its label disagree"`, while
  the measurement is the constant `"drifted"` too, so neither the title nor the number can separate the two
  filings;
- `tools/sensors/metrics/source-size.sh:59` — `"the source file is past the size bound"`;
- `tools/sensors/conditions/broken-main.sh:47` — `"the main build is red"`.

The identity is **not** the cause: `tools/sensors/sensors.json:250` declares
`"fingerprint": { "fields": ["issue", "code"] }`, `tools/sensors/tests/sensor-cases.sh:2609` asserts that field
set, `:2653` and `:2655` assert that each drifted item is its own condition, and the per-item identity is the shipped
requirement `openspec/specs/backlog-sensors/spec.md:527-533` (`sensors#metadataDriftRow`). Two drifted items are
two repairs and two conditions — what a reader cannot tell apart is the two **titles**, which is I3's own
"indistinguishable" arm (`guidelines/Backlog.md:155`, §4). The owner's recorded decision (`#1034`,
`decision: D`) settles the fork the item left open: the title names the drift, at three sites, and the
fingerprint fields are not touched.

**The red test** — three cases written before the fix, red on the run's base
`fa45813e4fd4b79a8becf27b4ecc1ce07cf1a22e` (`evidence/red-on-HEAD-titles.log`):

```
  the first drift's title names the item and the disagreement it carries: an item's own record and its label disagree
an item's own record and its label disagree: '#278: label-without-marker' not found in: an item's own record and its label disagree
FAIL sensors#twoDriftedItemsFileUnderTwoTitles
  the first file's title names the path and its line count: the source file is past the size bound
the source file is past the size bound: 'app/src/main/java/Big.kt: 1600 lines' not found in: the source file is past the size bound
FAIL sensors#twoOversizedFilesFileUnderTwoTitles
  the first cause's title names the job and the case: the main build is red
the main build is red: 'the build is red: app:testMobileDebugUnitTest/com.naviveylin.FooTest.bar' not found in: the main build is red
FAIL sensors#twoFailingCasesFileUnderTwoTitles
```

Three failures, one per site; each assertion reads the title a `gh issue create` actually carried
(`create_titles`, derived from the stub's own record), so a passing case cannot be a body that happens to
mention the drift.

**The fix, once the case is red** — each site composes its title from the finding's own fields, and nothing
else moves: `#<issue>: <code>`, `<path>: <lines> lines`, `the build is red: <job>/<case>`.

Route:

LOOP-ROUTE: guidelines/Backlog.md §4

§4 owes no text edit: I3 already states the rule and names the observation that falsifies it ("the same finding
exists twice, or a refusal is indistinguishable from a failure", `guidelines/Backlog.md:155`), and this change is
the tree satisfying it rather than a rule the document has yet to carry. What a filing's title must name is stated
where the filing shape lives — the capability delta — not in the invariant table.

**Surface** — `tools/look.sh "the main build is red|the source file is past the size bound|an item's own record and its label disagree" --files` names **four** live sites in the tree: the three title literals
above and one fixture body in `tools/sensors/tests/sensor-cases.sh` that quotes the old title text as a
*record's* body (not a title, and left as it stands). Added: `3` cases, `6` registration lines (the
`SENSOR_CASES` list and the dispatch `case`, three names each), `1` helper (`create_titles`). Rewritten: `0`.

## What Changes

- The three rows in `tools/sensors/metrics/metadata-invariant.sh`, `tools/sensors/metrics/source-size.sh` and
  `tools/sensors/conditions/broken-main.sh` pass a title composed from the condition's own fields.
- `tools/sensors/tests/sensor-cases.sh` gains `create_titles` and three cases — one per row — each asserting
  that two conditions of that row are two distinct titles naming their own drift.
- The registry's fingerprint fields, the fingerprints themselves, the rows' thresholds and every closed or
  open item already on the tracker are untouched: the titles of the pairs already filed are history.
