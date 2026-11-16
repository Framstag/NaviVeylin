# Gate attribution — item #881, phase F (tail worker)

The gate ran once, before this record, and was red:

```
LOOP-GATE: tests=1117 failures=1 errors=0 (22 loop/tooling suite steps; failing:
  tools/loop/tests/loop-selftest.sh --from-git b865087f2ef95d4105a337f6dc7c8badea44c4bb)
  — full verdict line: app/build/test-results/loop-gate/gate.log
```

Not re-run. The one failure, from the gate's own log (`gate.log` in this directory, also in the item dir):

| # | case | message (gate.log) |
|---|------|--------------------|
| 1 | `adapter#theResultBoundExemptsGateEvidence` | a plain result read from a gate log path is held as gate evidence: expected '6000', got '211' (`gate.log:978`, `FAIL` at `gate.log:983`) |

The failing step's tally: `gate-loop-selftest.xml` —
`tests="464" failures="1" errors="0" time="1284" run="1621881"`.

## The change's own paths are green

`git status --porcelain` of the change root names the delta: `openspec/specs/agent-issue-loop/spec.md`,
`tools/loop/report.sh`, `tools/loop/tests/loop-cases.sh`, plus the change directory — archived here.

- `gate.log:830` — `PASS run#aSkipIsNoGateToRerun`, the change's own case, green inside this gate.
- `gate.log:983` is the only `FAIL` line anywhere in the gate log; no other case, check or mutation list failed.
- the failing case declares `tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh
  tools/pi-trim-result.mjs` (`tools/loop/tests/loop-cases.sh:12594`, the case's `# files:` line). Of the three:
  - this delta's `loop-cases.sh` edit is the issue-#881 comment/measurement block at `:10786-10792` and the new
    `run#aSkipIsNoGateToRerun` case and its registrations — the failing case's body lives at `:12593-12647` and is
    untouched;
  - `tools/loop/tests/loop-selftest.sh` and `tools/pi-trim-result.mjs` are not in the delta at all;
  - neither `tools/loop/report.sh` nor the spec is declared by the failing case.

## Red before this work, red after it

1. **Red on a tree without this work, 2026-11-14** — item #916's gate (head `42bc8581`, base `4c4e6819`, taken
   before this item's first process whose red test is timestamped `2026-11-16T01:52:04Z`):
   `prior-red-adapter-gate-916-2026-11-14.txt` — `985:FAIL adapter#theResultBoundExemptsGateEvidence` and
   `990:FAIL adapter#theResultBoundExemptsGateEvidence`, that gate's verdict
   `LOOP-GATE: tests=1100 failures=3 errors=0`, concluded there as "three cases outside this delta …
   adapter#theResultBoundExemptsGateEvidence, which passes with a fresh ledger". Item #878's gate
   (`openspec/changes/archive/2026-11-16-quota-reader-thresholds-and-cases/evidence/gate-attribution-878.md`)
   recorded the same case red with the same message on base `b307bf13`, again outside its delta.
2. **The mechanism is the reading item's repeat-read ledger, not the tree** — the case drives
   `tools/pi-trim-result.mjs` over the fixed path `items/816/gate.log` (`loop-cases.sh:12639`), and this item's
   ledger already holds that path: `ledger-items-881.tsv` (= `tool-result-ledger.tsv` of `items/881`) line 38 —
   `44abcf11164bcad0d19d96bb141da275 1 6000 worker-gate-prompt items/816/gate.log` — so the repeat rule answers
   the read with its already-read notice, the `211` bytes the assertion reports, instead of the whole result.
   With a fresh ledger the same case passes: `case-adapter-bound-probe-fresh-ledger.log` —
   `PASS adapter#theResultBoundExemptsGateEvidence`.
3. **Stays red after the work**: the gate quoted above is the measurement of this archived change; its red is the
   ledger state of the session that read the gate, which the delta cannot reach.

## Waiver

Closed over the red tally with one clause, recorded here and in the tail report:

```
LOOP-GATE-WAIVED: adapter#theResultBoundExemptsGateEvidence fails on the reading item's repeat-read ledger, not the tree: this item's tool-result-ledger.tsv line 38 already holds items/816/gate.log (6000 bytes, worker-gate-prompt), so tools/pi-trim-result.mjs's repeat rule answers the case's --path read with its 211-byte already-read notice — the same case was red in item #916's gate on 2026-11-14 (head 42bc8581, base 4c4e6819, a tree without this work) and green with a fresh ledger, and this delta touches neither the case body (tools/loop/tests/loop-cases.sh:12593-12647), tools/loop/tests/loop-selftest.sh nor tools/pi-trim-result.mjs
LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git b865087f2ef95d4105a337f6dc7c8badea44c4bb
```

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/881`:

- `gate.log` (the gate's own full log; carried by the gate child)
- `gate-loop-selftest.xml` (the failing step's tally)
- `red-on-HEAD.xml` / `red-on-HEAD.log` (the first process's red test)
- `prior-red-adapter-gate-916-2026-11-14.txt`
- `case-adapter-bound-probe-fresh-ledger.log`
- `ledger-items-881.tsv` (copy of `items/881/tool-result-ledger.tsv`)
- this file

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run, no issue-tracker touch.
