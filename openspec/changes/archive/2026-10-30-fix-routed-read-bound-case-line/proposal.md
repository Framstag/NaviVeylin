# Proposal — fix-routed-read-bound-case-line

## Why

**The condition, verified in the tree at base `2f954a0` (2026-10-30).**
`openspec/specs/documentation-ownership/spec.md:139` opens the scenario *A section carrying more than one
question is split*, whose body (`:141`–`:143`) says a routed section past 12 000 bytes is split into lettered
sub-sections of at most 12 000 bytes each, and whose Case line — `:144`–`:145` in the base commit's content,
`:148`–`:152` once this change's restatement is merged — names `routed-read#bound` as the case that exercises it:

```
- **Case**: `routed-read#bound` — the cost of a section is the byte count of its lines from the `## ` heading
  through the line before the next `## ` heading, and the case prints it for every section of the document
```

That case cannot fail on an over-bound section. `tools/check-doc-routes-selftest.sh:301` (`case_routed_read_bound`,
registered at `:390`) reads the `--sizes` report, recomputes every section's byte count with `sed`/`wc -c` and
compares the two lists, then compares the pairs the routing table marks with the pairs the report marks — a
section's size is data it prints, never an assertion it makes. Measured on this base, `146 section(s) printed,
116 routed pair(s)` and `all 1 cases passed`, while the same report carries **eight routed sections past the
12 000-byte bound** (`evidence/phase-a.log`, `bash tools/check-doc-routes.sh --root . --sizes`): `Backlog.md §9`
15 389, `Build.md §7` 12 052, `Design.md §5` 12 781, `Design.md §8` 12 058, `MapRendering.md §1` 22 592,
`UI.md §1` 25 206, `UI.md §7a` 13 102, `UI.md §8` 19 681.

**Root cause, one sentence.** The Case line of the split scenario names the ruler's case for a split the ruler
does not enforce, so the spec claims a case failure where the tree has a check's condition — the sibling
scenario *A routed section past the bound is a check's condition*
(`openspec/specs/documentation-ownership/spec.md:170` at the base, `:177` merged) and
its case `sensors#routedSectionSizeRow` (`openspec/specs/documentation-ownership/spec.md:176` at the base,
`:183` merged; declared at
`tools/sensor-cases.sh:1868`) are the half that files
the condition, and the WHEN's second trigger — "or answers more than one question" — has no case at all.

**Red test** (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, `evidence/red-probe.sh`, run on the base
commit's content at 2026-10-30T07:14:08Z): the probe reads the scenario's own block out of the shipped spec and
requires that its Case text names both halves and says which one is the condition; on HEAD it reports
`tests=1 failures=1`:

```
FAIL routed-read#the-case-line-names-both-halves — the scenario does not state:
  [`sensors#routedSectionSizeRow`] [ruler] [check's condition]
```

**Surface.** On this base the command
`grep -rn "routed-read#bound" --include=*.md --include=*.sh --include=*.json . | grep -v openspec/changes/archive`
reports `5` lines in `4` files — the spec's Case line, three lines in `tools/check-doc-routes-selftest.sh`
(`:17`, `:245`, `:390`) and one mutation record in `tools/check-doc-routes-mutations.json:23` — and
`grep -rn "A section carrying more than one question is split" …` reports one line, the spec heading. Nothing
else asserts the pairing: no Guideline restates it, no Gradle case exercises it, and the sensor side is already
carried by `sensors#routedSectionSizeRow`. This change restates one scenario and adds one evidence probe; it adds
no Gradle case, rewrites no case and realigns no fixture.

surface: 1 spec scenario restated (Case line and body), 1 evidence probe case added, 0 cases rewritten, 0 fixtures realigned, 0 compiled files — `grep -rn "routed-read#bound" --include=*.md --include=*.sh --include=*.json . | grep -v openspec/changes/archive` (`5 lines in 4 files` on the base tree: `openspec/specs/documentation-ownership/spec.md:144`, `tools/check-doc-routes-selftest.sh:17,245,390`, `tools/check-doc-routes-mutations.json:23`) and `grep -rn "A section carrying more than one question is split" --include=*.md --include=*.sh . | grep -v openspec/changes/archive` (`1 line`, the spec heading at `:139`).

**Owning capability and guideline section.** The capability is `documentation-ownership`; the rule is its
requirement *The routing cost is measured* (`openspec/specs/documentation-ownership/spec.md:117`), which
`AGENTS.md`'s Documentation Map routes for "how the guidelines themselves are organized". The routing rule the
Case line breaks is the requirement's own: "Each bound SHALL be the bound of the check that measures it"
(`openspec/specs/documentation-ownership/spec.md:124`–`:125`), which is why the split's enforcement half is the
sensor row rather than a second
bound-checking case — and `guidelines/Build.md` §4a owns how a gate's verdict is read, which this change does not
alter.

## What Changes

- Restate the scenario *A section carrying more than one question is split* in the delta of
  `documentation-ownership`: an `- **AND**` bullet states that a routed section still past the bound is the
  condition `sensors#routedSectionSizeRow` files (identity: document and section) while `routed-read#bound` is
  the ruler and the print, and that the "answers more than one question" trigger is a judgement no case asserts;
  the Case line names both cases with what each one asserts.
- Add the change's evidence probe (`evidence/red-probe.sh`), which reads that scenario and fails on HEAD and
  passes once the restatement is archived into the shipped spec.
- No production code, no test source, no fixture and no other requirement changes; the requirement's normative
  text is carried whole in the delta because a MODIFIED block replaces it.

## Capabilities

### New Capabilities

- None — this corrects the case a shipped scenario names; it states no new behaviour.

### Modified Capabilities

- `documentation-ownership` — requirement *The routing cost is measured*: one scenario's `AND` bullet and Case
  line. The requirement's description and its other seven scenarios are carried unchanged, as the requirement
  *A modified requirement carries the scenarios its spec still has* requires.

## Impact

- `openspec/specs/documentation-ownership/spec.md` — via the delta
  `openspec/changes/fix-routed-read-bound-case-line/specs/documentation-ownership/spec.md`, merged on archive:
  one scenario restated, no requirement description changed.
- `openspec/changes/fix-routed-read-bound-case-line/**` — the planning artifacts and evidence.
- Additive and non-breaking: no capability's contract changes, no case id changes, no bound moves, and no file a
  build compiles is touched. Rollback is deleting the change directory before archive, or reverting the single
  scenario in the shipped spec afterwards — the tree carries no runtime behaviour to restore.
- Not touched: `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`, `tools/sensor-cases.sh`,
  `tools/sensors/sensors.json`, every Gradle module.
