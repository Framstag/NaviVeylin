# Evidence — fix-routed-read-bound-case-line (issue #711)

Change archived as `openspec/changes/archive/2026-10-30-fix-routed-read-bound-case-line`. Base commit
`2f954a04b3f75e138c529fffe7985ca011375866`. Path touched: one requirement of
`openspec/specs/documentation-ownership/spec.md`, through the delta; no compiled file, no case script, no fixture.

## Phase A — the claim, verified, and the measurement that shows it

`evidence/phase-a.log`:

- `bash tools/check-doc-routes.sh --root . --sizes | grep '^check-doc-routes: section-cost ' | awk '$9=="routed" && $5+0 > 12000'`
  prints **eight** routed sections past the 12 000-byte bound on this base — `Backlog.md §9` 15 389,
  `Build.md §7` 12 052, `Design.md §5` 12 781, `Design.md §8` 12 058, `MapRendering.md §1` 22 592,
  `UI.md §1` 25 206, `UI.md §7a` 13 102, `UI.md §8` 19 681 — and `wc -l` over that list prints `8`.
- `bash tools/check-doc-routes-selftest.sh --case routed-read#bound` prints `PASS routed-read#bound` and
  `146 section(s) printed, 116 routed pair(s), each equal to the ruler's own sed count`, exit 0: the case the
  split scenario names is green while eight sections sit past the bound, because the case checks the ruler and
  the print, not the bound.

So the item's premise holds: the scenario's Case line overstates what `routed-read#bound` asserts, and the
enforceable half of the split rule is the sibling scenario's condition.

## Red on HEAD

`evidence/red-probe.sh` over the base commit's shipped spec, `--xml evidence/red-on-head.xml`
(`evidence/red-on-head.log`):

```
FAIL routed-read#the-case-line-names-both-halves — the scenario does not state:
  [`sensors#routedSectionSizeRow`] [ruler] [check's condition] (2026-10-30T07:14:08Z)
```

`tests=1 failures=1 errors=0`, timestamp `2026-10-30T07:14:08Z`.

## Green after the restatement merged

`bash evidence/red-probe.sh --root <change root> --xml evidence/green-after-archive.xml`
(`evidence/green-after-archive.log`), after `openspec archive` merged the delta:

```
PASS routed-read#the-case-line-names-both-halves — the scenario names the ruler case and the sensor case, and
  says the split is a check condition (2026-10-30T07:15:04Z)
```

`tests=1 failures=0 errors=0`, timestamp `2026-10-30T07:15:04Z`.

## Revert-check — the mutation is the item's own text

`evidence/revert-check.sh` (`evidence/revert-check.log`) puts the merged requirement block back to the base
commit's text — the scenario names `routed-read#bound` alone — and requires the probe to go red, then restores
the archived restatement byte-identically and requires it green:

```
## mutated — the split scenario names `routed-read#bound` alone, as HEAD had it
FAIL routed-read#the-case-line-names-both-halves — the scenario does not state:
  [`sensors#routedSectionSizeRow`] [ruler] [check's condition] (2026-10-30T07:15:18Z)

## restored — the archived restatement back in place
PASS routed-read#the-case-line-names-both-halves — the scenario names the ruler case and the sensor case, and
  says the split is a check condition (2026-10-30T07:15:18Z)
```

The restore is verified with `cmp -s` against the backup, so the mutation leaves no diff behind.

## The gate, run once

`./tools/loop/gate.sh` from the change root, after the archive (the delta is gitignored while open, so the
gate's pathspec is only owed once the change sits under `openspec/changes/archive/`). `evidence/gate.log`, its
seven per-suite tallies in `evidence/gate-tallies/`, and their sum in `evidence/gate-summary.xml` (0 + 21 + 0 + 6
+ 0 + 0 + 0):

```
LOOP-GATE: tests=27 failures=0 errors=0 (bash tools/gen-feature-list.sh --check-classification 4s,
tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 3s,
tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s,
tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s,
tools/check-openspec-hygiene-selftest.sh 0s)
```

Exit 0. `openspec validate fix-routed-read-bound-case-line --strict` printed
`Change 'fix-routed-read-bound-case-line' is valid` (`evidence/openspec-validate.log`). The measuring block taken
over the archived change reports the gate's own timing as `LOOP-MEASURE-GATE seconds=8 suites=7` and its tallies
one per suite (`evidence/measure-after-archive.txt`).

Two notes a reviewer will want, neither of them this change's:

- the gate's classification step prints `stale: 3` (`search-name-matching`, `search-result-row-icon`,
  `start-map-selection` are classification entries beside specs that no longer exist). It exits 0 and is the
  base tree's state; this change adds no classification entry and no spec id.
- `LOOP-MEASURE-CHANGE fix-routed-read-bound-case-line scenarios=9 cases=7 unnamed=2` (`evidence/measure.txt`).
  The two unnamed scenarios are the requirement's own shipped ones — *Numbers for representative changes* and
  *An unmeasured saving is not claimed* — carried unchanged because a MODIFIED block carries the requirement
  whole; the archived `2026-10-29-fix-unbounded-agent-context` carried the same block with the same
  `scenarios=9 cases=7 unnamed=2`.

## Line numbers, because the merge moved them

Quotes of the shipped spec give the base commit's line numbers where the sentence is the base's, and the merged
numbers where the sentence is this change's: the scenario heading stays `:139`, the Case line is `:144`–`:145`
at the base and `:148`–`:152` after the merge, the sibling scenario is `:170` → `:177` and its Case `:176` →
`:183`. The requirement description (`:117`–`:126`) is identical in both, so the requirement's own sentences keep
their numbers.

`git diff --stat` after the merge is `openspec/specs/documentation-ownership/spec.md | 11 +++++++++--`
(9 insertions, 2 deletions): the restatement and nothing else.
