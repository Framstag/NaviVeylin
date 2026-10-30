# Design — fix-routed-read-bound-case-line

## Root cause

The split scenario's Case line names `routed-read#bound`, a case that prints every section's cost and checks it
against the requirement's ruler (`tools/check-doc-routes-selftest.sh:301`) and therefore cannot fail on a
past-bound section. The enforceable half of the split rule lives in the sibling scenario *A routed section past
the bound is a check's condition* and its case `sensors#routedSectionSizeRow`; the scenario under review does not
say so, and its WHEN carries a second trigger with no case at all. The sentence the reader is misled by is two
lines of prose (`openspec/specs/documentation-ownership/spec.md:144`–`:145` in the base commit, `:148`–`:152`
after the merge), and the tree's measurement of it is
eight routed sections past the bound beside a green case (`evidence/phase-a.log`).

## The single fix

Restate the scenario in a `documentation-ownership` delta:

1. an `- **AND**` bullet names which half is a condition: a routed section still past the bound is the condition
   `sensors#routedSectionSizeRow` files, under the identity of its document and section, `routed-read#bound` is
   the ruler and the print, and the "answers more than one question" trigger is a judgement no case asserts;
2. the Case line names both cases with what each asserts — `routed-read#bound` the ruler and the print (it fails
   when a printed cost is not the ruler's count), `sensors#routedSectionSizeRow` the filing (the condition carries
   document and section, and closes when the section shrinks under the bound).

No case id changes, no bound moves, no check is retuned. The delta carries the requirement whole because the
MODIFIED block replaces it (requirement *A modified requirement carries the scenarios its spec still has*), so the
verification compares the block against the shipped requirement scenario by scenario.

## Alternatives the evidence eliminated

- **Make `routed-read#bound` fail when a routed section is past the bound.** Impossible: the requirement's own
  sentence — "Each bound SHALL be the bound of the check that measures it, so retuning either one is one edit in
  the checks' registry" (`openspec/specs/documentation-ownership/spec.md:124`–`:125`) — forbids a second check
  restating the bound, and a case failure
  files nothing, so the eight sections measured today (`Backlog.md §9`, `Build.md §7`, `Design.md §5`,
  `Design.md §8`, `MapRendering.md §1`, `UI.md §1`, `UI.md §7a`, `UI.md §8`) would read as eight red cases while
  the shipped sensor scenario says a past-bound section is a condition, not a failure.
- **Add a case that reads the sensor's recorded conditions** (the item's second option). Impossible as a host
  case: the conditions live on the tracker, not in the tree, so no case in `tools/` can read them without the
  network the suites forbid; and `sensors#routedSectionSizeRow` already exercises the filed condition and its
  clear under the same identity (`tools/sensor-cases.sh:1868`–`:1930`), so a second case would restate a case the
  spec already names one scenario away.
- **Change the case's description instead of the spec.** Impossible: `routed-read#bound`'s description already
  states what the case does (ruler and print); the misstatement is in the spec's Case line, which is the artifact
  a reader routes from.
- **Drop the scenario and leave the sibling only.** Impossible: the scenario states a rule the sibling does not —
  the split and the sub-section bound — and dropping it would leave the split unstated in `documentation-ownership`
  while `AGENTS.md` and three archived changes cite it.

## Blast radius

- `openspec/changes/fix-routed-read-bound-case-line/specs/documentation-ownership/spec.md` — the delta; on archive
  it replaces the requirement in `openspec/specs/documentation-ownership/spec.md` (one scenario restated, the
  requirement's description and its other seven scenarios carried unchanged).
- `openspec/changes/fix-routed-read-bound-case-line/evidence/**` — the probe, its red run and its green run.
- Nothing else: no compiled file, no case script, no fixture, no Gradle module, no guideline.

## Rollback

Before archive, delete the change directory. After archive, restore the two prose lines of the scenario's Case
text in `openspec/specs/documentation-ownership/spec.md` — the tree carries no runtime behaviour to restore. The
rollback is the mutation the revert-check task exercises: the probe must go red when the Case line is narrowed
back to `routed-read#bound` alone, and green again when the restatement returns.

## Verification

- Red on HEAD: `evidence/red-probe.sh` reports `tests=1 failures=1` (`evidence/red-on-head.xml`,
  2026-10-30T07:14:08Z), naming the three tokens the scenario lacks.
- Green after archive: the same probe over the merged spec reports `tests=1 failures=0`
  (`evidence/green-after-archive.xml`).
- Structural: `openspec validate --strict`, the delta carried whole, and the gate's documentation suites
  (`openspec/**` and `*.md` pathspec: `gen-feature-list --check-classification`, `check-doc-routes`,
  `check-doc-routes-selftest`, `check-doc-routes` mutations `--list`, `check-todo-references`,
  `check-openspec-hygiene`, `check-openspec-hygiene-selftest`) once, by `./tools/loop/gate.sh`.
- No device run is owed: nothing renders.
