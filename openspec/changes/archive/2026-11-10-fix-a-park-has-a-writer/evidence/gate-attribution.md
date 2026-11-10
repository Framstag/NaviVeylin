# The gate's one red step is attributed to work this change does not touch

The gate ran once, from the change root against base `95efb9036e86b0eb72930085d00027e9949a3273`
(HEAD `3b9e896e0ca8172b212695160e85d6fa1a59d1d2`). Its aggregate `LOOP-GATE` line — quoted verbatim in the
run's report for this item — is carried whole at `evidence/gate.log:1924`: 991 cases counted across the
suite's steps, no failing case, one error, and the failing step named `bash tools/gen-feature-list.sh
--check-classification`. Every other step is green; the per-step tallies are the XMLs under
`evidence/loop-gate/`.

So one step is red. Its own tally, `evidence/loop-gate/bash_tools_gen-feature-list.sh_--check-classification.xml`:
`tests="0" failures="0" errors="1"`, system-out `car-only areas: 1 highlights: 19 gen-feature-list:
classification gate failed`. The console it printed is in `evidence/gate.log` at lines 1601-1618:

```
gate: bash tools/gen-feature-list.sh --check-classification
gen-feature-list: unclassified spec id(s):
  architecture-test-suite (introduced by the open change package-structure-as-five-kinds — classify it in that change, before it archives)
  domain-vocabulary (introduced by the open change domain-vocabulary-is-declared — classify it in that change, before it archives)
  package-structure (introduced by the open change package-structure-as-five-kinds — classify it in that change, before it archives)
  vocabulary-projection (introduced by the open change domain-vocabulary-is-declared — classify it in that change, before it archives)
gen-feature-list: stale classification entry for a spec that no longer exists:
  search-name-matching
  search-result-row-icon
  start-map-selection
specs read: 186
classified: 189
unclassified: 4
delta spec ids: 18
stale: 3
gen-feature-list: classification gate failed
```

Seven ids are named. None is a path this change writes.

## What this change wrote, measured

`git diff --stat 95efb9036e86b0eb72930085d00027e9949a3273 HEAD` — 8 files, 169 insertions, 8 deletions:

```
.pi/skills/answer-decision/SKILL.md
guidelines/Backlog.md
tools/issues/answer.sh
tools/issues/decisions.sh
tools/issues/lib.sh
tools/issues/tests/decision-question-mutations.json
tools/issues/tests/fixtures/scenarios/parked/issues.json
tools/issues/tests/issue-tooling-cases.sh
```

The same diff restricted to the paths the red step reads —
`git diff --stat <base> HEAD -- tools/feature-list/specs.json openspec/specs openspec/changes/package-structure-as-five-kinds openspec/changes/domain-vocabulary-is-declared` —
prints nothing (0 lines). This change writes no classification row, no capability spec directory, and neither
of the two open changes' directories; its own affected-file list is `proposal.md:66-69`.

## The four unclassified ids are two other open changes' deltas

The step's own message attributes them: *introduced by the open change …*. The deltas exist only under those
changes, not under `openspec/specs/`:

```
openspec/changes/package-structure-as-five-kinds/specs/architecture-test-suite/spec.md
openspec/changes/package-structure-as-five-kinds/specs/package-structure/spec.md
openspec/changes/domain-vocabulary-is-declared/specs/domain-vocabulary/spec.md
openspec/changes/domain-vocabulary-is-declared/specs/vocabulary-projection/spec.md
```

and `tools/feature-list/specs.json` carries no row for any of the four (a search of the four ids in that file
finds 0). Both directories were created at `2026-11-10 05:06` and `2026-11-10 05:08`
(`stat -c '%y %n'` on the two directories), before this item's red run on the base at `2026-11-10T10:28:12Z`
(`red-on-HEAD.xml`) — they are not this item's files, and each owes its own row *before that change archives*,
which is exactly what the step says.

## The three stale entries were red before this work and stay red after it

At the run's base, `git show 95efb9036e86b0eb72930085d00027e9949a3273:tools/feature-list/specs.json` still
carries all three keys (a count of the three quoted ids → `3`), while
`git ls-tree --name-only <base> openspec/specs/` matches none of them (→ `0`) — the entries were already dead at
the base, and no spec directory has since appeared (0 matches under `openspec/specs/` in the current tree).
The three live keys are read at `tools/feature-list/specs.json:168`,
`tools/feature-list/specs.json:171` and `tools/feature-list/specs.json:182`. Nothing this change wrote moves
either count: its diff touches none of those files (measured above).

The same three lines were already red in earlier items' gates and recorded as belonging to nobody's change —
e.g. `openspec/changes/archive/2026-11-03-fix-rejected-trip-update-rearms-publishing/evidence/gate-round2-attribution.md:37-39`.

## Every step that reads this change's paths is green

- `tools/issues/tests/issue-tooling-selftest.sh` — `evidence/loop-gate/tools_issues_tests_issue-tooling-selftest.sh.xml`:
  `tests="166" failures="0" errors="0"`, `all 166 cases passed … HEAD 3b9e896e`. Both cases this change binds
  (`answer#parkWritesTheAnswerAndKeepsTheMode`, `decisions#aParkedItemIsListedAsParked`) run inside it.
- The structural steps — `tools/check-doc-values.sh`, `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`,
  `tools/check-openspec-hygiene.sh`, `tools/check-portability.sh` — all carry `failures="0" errors="0"`
  (`evidence/loop-gate/*.xml`, copied from the item's `gate-tally/`).
- `./gradlew test` is green and irrelevant: `proposal.md:53` — no compiled file changes.

So the red step reads a classification file and two other changes' directories, and this change reads neither.
Fixing the step means writing four rows owed by two other open changes and clearing three entries that predate
the base — neither is this change's claim. The step is waived, named below, not closed over in silence.
