# The gate's one red step is attributed to work this change does not touch

Written by the tail process (phase F), 2026-11-10, from the change root. The gate ran **once**;
its result is quoted from `evidence/gate-result.txt` and never re-run:

    LOOP-GATE: tests=1126 failures=0 errors=1 (gate.sh full run, head=1b2d1f40 diff=821d5ce7 — failing: bash tools/gen-feature-list.sh --check-classification) — the runner's aggregate verdict line, not retained as a JUnit XML; kept whole in `evidence/gate-result.txt`

Every other step of that run is green — all 1126 tests, `failures=0`; the single `errors=1` is the
step `bash tools/gen-feature-list.sh --check-classification`. Its tally,
`evidence/gate-step-classification.xml`, prints:

    car-only areas: 1 highlights: 19
    gen-feature-list: classification gate failed

## What that step reads, and what sets its `gate_errors`

From `tools/gen-feature-list.sh` in this tree:

- `tools/gen-feature-list.sh:178` — the shipped ids, enumerated under `openspec/specs/`;
- `tools/gen-feature-list.sh:191` — the open changes' delta capability dirs, `openspec/changes/<change>/specs/*/spec.md`,
  archive excluded;
- `tools/gen-feature-list.sh:197` — `unclassified` = (shipped ∪ delta) − the keys of `tools/feature-list/specs.json`;
- `tools/gen-feature-list.sh:220` — any unclassified id sets `gate_errors=1`, and
  `tools/gen-feature-list.sh:280` — in `--check-classification` mode that is
  `gen-feature-list: classification gate failed`, the exact line the tally carries;
- `tools/gen-feature-list.sh:248` — the car-only rule expects exactly one and the tally prints
  `car-only areas: 1`, so that rule passed; `tools/gen-feature-list.sh:279` prints
  `highlights: 19`, unchanged from every earlier red of this step.

The step reads nothing else: no source file, no guideline, no loop file.

## The unclassified ids in this tree right now — none is this change's

Measured with `tools/look.sh 'architecture-test-suite|domain-vocabulary|package-structure|vocabulary-projection|tooling-test-layout' --path tools/feature-list`:
**0 rows** for four ids (the fifth, `tooling-test-layout`, has since been classified at
`tools/feature-list/specs.json:188`). Their delta dirs all exist, and both owners are still open
(`openspec list`, run from the change root):

- `openspec/changes/package-structure-as-five-kinds/specs/architecture-test-suite/spec.md`
- `openspec/changes/package-structure-as-five-kinds/specs/package-structure/spec.md` — owner `0/35 tasks`, open;
- `openspec/changes/domain-vocabulary-is-declared/specs/domain-vocabulary/spec.md`
- `openspec/changes/domain-vocabulary-is-declared/specs/vocabulary-projection/spec.md` — owner `0/29 tasks`, open.

So `unclassified_n ≥ 4 > 0` in this tree, by `tools/gen-feature-list.sh:197` — the condition that
fails the step is present whatever else it also reports, and each id owes its row *in the change
that introduced it, before that change archives* — exactly what
`tools/gen-feature-list.sh:227` prints.

The three stale keys (`tools/feature-list/specs.json:168` `search-name-matching`, `:171`
`search-result-row-icon`, `:183` `start-map-selection`, shipped dirs absent under `openspec/specs/`)
are reported but do **not** fail the gate: `tools/gen-feature-list.sh:252` sets no `gate_errors`
(`tools/gen-feature-list.sh:198` records the rule).

## This change writes none of the step's inputs

Its roster, from `tasks.md` and this item's `LOOP-DIFF-READY` report: `tools/loop/run.sh`,
`tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`,
`guidelines/Backlog.recipes.md` section 7b, and this change's own `openspec/` directory. None is read by
the classification gate. The one overlap is this change's own delta,
`openspec/changes/fix-a-cut-round-names-its-bound/specs/agent-issue-loop/spec.md` — a capability
that already ships (`openspec/specs/agent-issue-loop/spec.md`) and already has its row
(`tools/feature-list/specs.json:8`), so it adds no unclassified id and touches no classification file.

## Red before this work — verbatim copies in this `evidence/` directory

- `evidence/red-before-classification-gate.txt` — items 741's gate of 2026-11-02 (same change root,
  same step, `errors=1 … failing: bash tools/gen-feature-list.sh --check-classification`, noted there
  as "another open change's debt") and item 721's copy of the step's own console, whose summary is
  `car-only areas: 1 / highlights: 19` — this run's signature exactly;
- `evidence/red-before-item-807-gate.txt` — item 807's console on 2026-11-10: the four unclassified
  ids named above, `unclassified: 4`, `car-only areas: 1`, `highlights: 19`;
- `evidence/red-before-item-837-gate.txt` — item 837's gate earlier the same day, `errors=1` on the
  same step; its clock (red test 2026-11-10T08:24:52Z) precedes this item's base red test
  (2026-11-10T15:32:07Z, `evidence/red-on-HEAD.xml`), i.e. the step was red before any of this
  change's work existed.

**Red after this work** follows from the same measurement: the change's roster removes, adds and
edits none of the gate's inputs, so the four ids stay unclassified and the two owners still owe
their rows until they archive — while the steps that read *this* change's paths are green
(the aggregate `tests=1126 failures=0 errors=1`, not retained as a JUnit XML, in
`evidence/gate-result.txt`, and this change's cases green on the change in `evidence/green-on-change.log`).

The waiver travels in this item's report as `LOOP-GATE-WAIVED` /
`LOOP-GATE-WAIVED-SUITE: bash tools/gen-feature-list.sh --check-classification`.
