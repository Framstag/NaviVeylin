# Evidence — fix-loop-close-names-its-transition

## The measurement of this change's own delta

One `./tools/loop/measure.sh --change 2026-10-29-fix-loop-close-names-its-transition`, run from the change root
after the archive moved; the full block is `evidence/measure.txt`. The lines this change quotes:

```
LOOP-MEASURE base=42d3e0004206ebfee39e11942db0bef375e1909e paths=95 insertions=2795 deletions=19
LOOP-MEASURE-SUITE cases=277 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-CHANGE 2026-10-29-fix-loop-close-names-its-transition scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 cases rewritten, 1 fixture scenario added, 1 engine write edited,
  1 mutation record added, 1 guideline row realigned — `grep -rn 'run_labels "$p" close\|--add-label
  status/fixed-by' tools/loop-cases.sh` … `grep -c 'displace-settled' tools/loop/labels.sh` → `0` on HEAD
  against `1` after the fix.
```

`LOOP-MEASURE-GATE` carried `seconds=-` because the gate had not run when the block was taken; the gate's own
time is in `evidence/gate.md`. The delta has no compiled path of its own (`tools/**`, `guidelines/**`,
`openspec/**` — `guidelines/Build.md` §2), and the gate's compiled step ran only because the measured base is
older than the tree's last few commits.

## The red run

`labels#closeNamesTheSettledTransition` (`tools/loop-cases.sh`), over
`tools/loop/fixtures/scenarios/retired/issues.json` — item 21 carries `status/retired`, the other settled value:

- `2026-10-29T08:26:49Z`, HEAD's write path: `FAIL labels#closeNamesTheSettledTransition` — «a closure over the
  other settled state was refused: status-settled: #21 carries status/retired, a settled state of the project
  profile — pass --displace-settled to name the transition», `loop-selftest: 1 of 1 cases failed`, exit 1.
  `evidence/red-on-head.log`, XML `tests="1" failures="1"` in `evidence/red-on-head.xml`.
- Green after the fix, `2026-10-29T08:26:5xZ`: `bash tools/loop-selftest.sh --group labels` → `all 7 cases
  passed`, exit 0 (`evidence/green-labels.log`), and the same case inside the gate's 277-case selection.

## The revert-check

`tools/loop-mutations.sh` record `70.1`, `RED` at `2026-10-29T08:27:08Z` with the same refusal, `restore 70.1 …
green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
(`evidence/revert-check.log`, XML `tests="1" failures="1"` in `evidence/revert-check-mutated.xml`), and
`bash tools/loop-mutations.sh --check` applies all 202 records including `70.1` (gate tally).

## The gate

Once, from the change root, on the archived tree; `LOOP-GATE: tests=676 failures=0 errors=0`, exit 0
(`evidence/gate.md`, `evidence/gate-summary.xml`, per-suite XMLs under `evidence/gate-tallies/`).

## Pointers

Every number above is quoted from a command's own output; the XMLs a `tests=`/`failures=` pair in this change
cites are in `evidence/`, and `tools/loop/evidence-check.sh` resolves every `file:line` and `§N`.
