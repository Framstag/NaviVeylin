# Design — fix-routed-read-numbers-verbatim-cases

## Root cause

The shipped spec's requirement *The routing cost is measured* names a case for each preservation claim a split
makes. Two of those names have no harness behind them: `routed-read#numbers`
(`openspec/specs/documentation-ownership/spec.md:159`) is not declared by
`tools/check-doc-routes-selftest.sh` (its declarations are `routed-read#bound` and `routed-read#route`), and
`routed-read#verbatim` (`:174`–`:175`) points at `evidence/red-probe.sh` inside the archived change
`2026-10-22-fix-build-md-routed-read-size`. The archived probe implemented both checks once, against that one
split's diff; the archive kept the file but no suite runs it. A reader following either Case line reaches a case
that cannot be executed in the tree.

## The single fix

Two edits, one per claim:

1. **Carry `routed-read#numbers` as a real case** in `tools/check-doc-routes-selftest.sh`, beside
   `routed-read#bound` and `routed-read#route`: a fixture document splits `## 1.` into `## 1a.` and `## 1b.`
   while keeping the parent heading, its body cites all three, the routing table routes the two sub-sections,
   and the route check accepts the set. The case then removes the parent heading and requires the `§1` citation
   to be reported, and removes a sub-section heading and requires its citation to be reported. That is exactly
   the scenario's three clauses: the parent number is still a heading, every citation resolves, and the route
   check accepts the document set. One mutation record (`guideline-body-reference`) replaces the check's body
   extraction with a no-op, so the case is shown capable of failing.
2. **Restate `routed-read#verbatim`'s Case line** to say the property is a one-off judgement over the split's
   diff, recorded by the change that made the split, and that no harness in the tree can assert it over a later
   tree. The scenario keeps its rule; the shipped spec stops naming a file in an archived change.

No case id changes, no bound moves, and `tools/check-doc-routes.sh` is not edited. The delta carries the
requirement whole because the MODIFIED block replaces it (requirement *A modified requirement carries the
scenarios its spec still has*).

## Alternatives the evidence eliminated

- **Restate `routed-read#numbers` instead of carrying it.** The case is cheap and the check already resolves
  every citation and every heading: the fixture costs one function in the suite that already owns `#bound` and
  `#route`, and the gate already runs that suite for `tools/check-doc-routes*` and for `*.md`. Restating it would
  leave the split's number unasserted while a runnable case exists, which is the gap the item reports.
- **Keep the archived `evidence/red-probe.sh` as the verbatim case.** The archive is history: its probe compares
  one split's `git diff`, and `tools/check-doc-routes.sh` explicitly skips change artifacts as history
  (`tools/check-doc-routes.sh:15`–`:16`). A later tree has no such diff, so no suite can rerun it; keeping the
  pointer is the defect.
- **Promote `routed-read#verbatim` into the route suite.** The property needs the pre-split document and the
  post-split document to compare. A suite that ran on a later tree would have neither, and a fixture pair written
  inside the case would only compare text the case itself wrote — it would assert nothing about a real split.
- **Drop either scenario.** The split's number and citations and the split's text are rules the sibling scenarios
  do not state; dropping them would remove the preservation half of the split requirement.

## Blast radius

- `tools/check-doc-routes-selftest.sh` — one case function and its registration, plus the comments that say how
  many `routed-read#*` cases exist. No check semantics change.
- `tools/check-doc-routes-mutations.json` — one mutation record that falsifies the new case.
- `openspec/changes/fix-routed-read-numbers-verbatim-cases/specs/documentation-ownership/spec.md` — the delta;
  on archive it replaces the requirement in `openspec/specs/documentation-ownership/spec.md` (two Case lines
  restated, the requirement's description and its other seven scenarios carried unchanged).
- `openspec/changes/fix-routed-read-numbers-verbatim-cases/evidence/**` — the probe, its red run and its green
  run.
- Nothing else: no compiled file, no fixture directory, no Gradle module, no guideline, no sensor row.

## Rollback

Before archive, delete the change directory. After archive, restore the two Case lines in the shipped spec and
delete `case_routed_read_numbers` plus its `echo_case` registration from the suite — the tree carries no runtime
behaviour to restore. The rollback is the mutation the revert-check task exercises: with the case removed the
probe must go red, and with it restored the probe must go green.

## Verification

- Red on HEAD: `evidence/red-probe.sh` reports `tests=1 failures=1` (`evidence/red-on-head.xml`,
  2026-10-30T07:22:14Z), naming the undeclared case, its failed run and the archived verbatim pointer.
- Green after archive: the same probe over the merged spec reports `tests=1 failures=0`
  (`evidence/green-after-archive.xml`).
- Structural: `bash tools/check-doc-routes-selftest.sh --case routed-read#numbers` passes, the whole suite passes
  (22 cases), `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --only
  guideline-body-reference` goes red and restores green, `openspec validate --strict` accepts the change, and the
  gate's documentation suites (`tools/check-doc-routes*` and `openspec/**`/`*.md` pathspecs) run once by
  `./tools/loop/gate.sh`.
- No device run is owed: nothing renders.
