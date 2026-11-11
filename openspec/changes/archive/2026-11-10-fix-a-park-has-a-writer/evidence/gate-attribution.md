# The gate over the landing tree: no red step, and the stale entries are main's own

The gate ran once over the final tree, from the change root against base
`8361c0fe387626a2768d0d119b43961f75ebf485` (`origin/main` at the landing), HEAD
`0246b5ed0f18f171b7f043e30e726a85dbc8dad3`, and named the tree it measured before its first step:

```
LOOP-GATE-TREE: head=0246b5ed0f18f171b7f043e30e726a85dbc8dad3 diff=d2255f7c45599879
LOOP-GATE-TALLY: app/build/test-results/loop-gate run=1042717
```

The aggregate line, quoted whole at `evidence/gate.log:487`:

```
LOOP-GATE: tests=243 failures=0 errors=0 (tools/issues/tests/issue-tooling-selftest.sh 155s, tools/issues/check-metadata.sh 1s, tools/issues/tests/metadata-mutations.sh --list 4s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 0s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 9s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 1s, tools/tests/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/tests/check-portability-selftest.sh 27s, tools/tests/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s)
```

Seventeen steps, no failing case, no erroring step; the per-step tallies are the XMLs under
`evidence/loop-gate/`, and the case step's own tally carries the tree's stamp —
`all 166 cases passed (JDK 27, offline, no token, HEAD 0246b5ed, tools e3b0c44298fc, 2026-11-11T19:56:17Z)`.
No `FROM-CACHE` line: no Gradle run was owed, because no compiled path changed.

The aggregate `tests=243` is the sum of those seventeen tallies, and no single XML carries a sum — so
`tools/loop/evidence-check.sh` (run on this change as an archived one) reports that one pair as `DRIFT C2`
rather than as confirmed, while the case tally `tests="166" failures="0" errors="0"` is confirmed against
`evidence/loop-gate/tools_issues_tests_issue-tooling-selftest.sh.xml`. The drift is the aggregate's shape, not a
missing record: every step's own numbers are in the tallies, and `evidence/gate.log:487` is the line that sums
them.

## The step that was red in the round-2 gate is green, and the ids it prints are main's

`bash tools/gen-feature-list.sh --check-classification` exited 0 in 3s (`evidence/gate.log:200-214`): the four
unclassified ids the round-2 gate named belong to open changes that have since archived, and this tree's run reads
`unclassified: 0`. What remains is `stale: 5` (`evidence/gate.log:201-211`), the same five ids a run of the same
command on `origin/main`'s own tree prints:

```
architecture-test-suite
package-structure
search-name-matching
search-result-row-icon
start-map-selection
specs read: 189
classified: 194
unclassified: 0
stale: 5
```

Measured on 2026-11-11 in `/home/tim/projects/NaviVeylin` at `8361c0fe` — identical to the gate's step, exit 0.
None of the five is a path this change writes.

## What this change wrote, measured

`git diff --stat 8361c0fe HEAD` — 50 files, 2816 insertions, 11 deletions, of which 37 files are this change's
`evidence/` logs (the pathspec `LOOP_REVIEW_EXCLUDE_PATHSPEC` leaves them out of the tree's identity). The
non-evidence paths:

```
.pi/skills/answer-decision/SKILL.md
guidelines/Backlog.md
openspec/changes/archive/2026-11-10-fix-a-park-has-a-writer/{design.md,proposal.md,tasks.md,specs/decision-queue/spec.md}
openspec/specs/decision-queue/spec.md
tools/issues/{answer.sh,decisions.sh,lib.sh}
tools/issues/tests/{decision-question-mutations.json,issue-tooling-cases.sh}
tools/issues/tests/fixtures/scenarios/parked/issues.json
```

The classification step reads `openspec/specs/*/spec.md` for the id set and `tools/feature-list/specs.json` for the
rows. This change adds a requirement to `openspec/specs/decision-queue/spec.md` — an existing capability, so no id
moves — and touches no classification row: `git diff --stat 8361c0fe HEAD -- tools/feature-list/specs.json` prints
nothing.

## Which suites the gate owed, and which it did not

The delta writes no compiled file, so `LOOP_GATE_COMPILED_CMD` was not owed and no Gradle suite ran; it writes no
path under `tools/loop/`, so the loop's own suites (`tools/loop/tests/loop-selftest.sh`,
`tools/loop/tests/loop-mutations.sh`, `tools/loop/tests/loop-cases.sh`) are not in the owed set either. What the
`tools/issues/*` and `*.md` and `openspec/*` rows select is exactly the seventeen steps above.

## The record this replaces

The round-1 gate (`head=3b9e896e`, `diff=2fc00f19688fae33`) and the round-2 gate (`head=08062c83`,
`diff=3744bc7fa2578c34`) were over trees the rounds did not read; the round-2 review's blocking finding is that
mismatch. Their logs and tallies are not kept in this archive — `evidence/gate.log` and `evidence/loop-gate/` are
this gate's only.
