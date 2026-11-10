# Gate attribution for this item's single gate run

Gate line (gate runner's own tally — not retained as a JUnit XML; its log is this item's gate log): `tests=955 failures=0 errors=1 (…) — failing: bash tools/gen-feature-list.sh --check-classification`, `GATE-EXIT=1`. The failing output is copied verbatim in
`evidence/gate-classification-red.txt`; the red-before proof is copied verbatim in
`evidence/red-before-item-837-gate.txt`.

## What the failing step is red for, and whose it is

`unclassified: 4` — four delta spec ids the gate itself attributes to two other open
changes: `package-structure-as-five-kinds` (`architecture-test-suite`,
`package-structure`) and `domain-vocabulary-is-declared` (`domain-vocabulary`,
`vocabulary-projection`). An unclassified spec id is the condition that sets
`gate_errors` (`tools/gen-feature-list.sh:214`); a stale classification entry is
reported without failing (`tools/gen-feature-list.sh:246`), so the three stale entries
in the same output are not the failing condition at all. Each open change owes its
classification line in that change, before it archives — the gate says so in the
output itself.

This change's diff roster is `guidelines/Backlog.recipes.md`,
`guidelines/BacklogTooling.md`, `tools/check-doc-values.sh`,
`tools/tests/check-doc-values-selftest.sh` and `tools/loop/loop.conf`. The
classification gate reads none of them: it reads `openspec/specs`, each open change's
delta specs and the data under `tools/feature-list`. Its own delta adds no
capability id — the unclassified list would name this change if it did.

## Red before, red after

- Before: the same step was the failing step of issue-loop item 837's gate run in this
  same change root (tests=987 failures=0 errors=1 — failing: the same command; that runner tally is not retained as a JUnit XML either), an
  item whose red test predates this item's by 58 minutes; quoted in
  `evidence/red-before-item-837-gate.txt`.
- After: the step is red on this change's own tree in this item's gate run, quoted in
  `evidence/gate-classification-red.txt`.

## The cases this change answers for, green in the same run

`tools/check-doc-values.sh` exited 0 printing `LOOP-CHILD-TURNS-MAX: 6 roles, prose
matches config`, and every `values#` case of `tools/tests/check-doc-values-selftest.sh`
passed inside that same gate run — recorded in `evidence/green-on-change.log` and
`evidence/green-on-change.xml`.

## The clause waived

LOOP-GATE-WAIVED: the classification gate's four unclassified delta spec ids belong to
two other open changes — `package-structure-as-five-kinds` (architecture-test-suite,
package-structure) and `domain-vocabulary-is-declared` (domain-vocabulary,
vocabulary-projection) — which owe their classification lines in those changes; this
change's diff touches only guidelines and tools the gate does not read, the step was
already red before this work (item 837's gate) and stays red after it (this item's
gate log).
LOOP-GATE-WAIVED-SUITE: bash tools/gen-feature-list.sh --check-classification
