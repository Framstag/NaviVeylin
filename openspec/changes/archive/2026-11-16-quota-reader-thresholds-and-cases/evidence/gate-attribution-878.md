# Gate attribution — item #878, phase F (tail worker)

The gate ran once, before this record, and was red:

```
LOOP-GATE: tests=1122 failures=2 errors=0 (…) — failing:
  tools/loop/tests/loop-selftest.sh --from-git b307bf131b8ada6dbfd8c6e1d0acbcfb461519dd
```

Not re-run. The two failures, from the gate's own log (`gate.log` in this directory, also in the item dir):

| # | case | message (gate.log) |
|---|------|--------------------|
| 1 | `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` | the run recorded the item its signal interrupted: expected 'blocked', got '' (`gate.log:636`) |
| 2 | `adapter#theResultBoundExemptsGateEvidence` | a plain result read from a gate log path is held as gate evidence: expected '6000', got '211' (`gate.log:983`) |

The failing step's tally: `gate-selftest-from-git-b307bf13.xml` —
`tests="463" failures="2" errors="0" run="2046830"`.

## The change's own paths are green

The delta this change carries is four files (`git diff --stat b307bf131b8ada6dbfd8c6e1d0acbcfb461519dd..HEAD`):

```
guidelines/Backlog.recipes.md           |  10 +-
tools/loop-quota.sh                     |  37 +++++-
tools/loop/loop.conf                    |  14 ++-
tools/loop/tests/loop-quota-selftest.sh | 205 ++++++++++++++++++++++++++++++++
```

- the change's own suite passed in this gate: `gate.log` lines 153–160 — all six `quota#` cases `ok`,
  `loop-quota-selftest: all 6 cases passed`.
- no `FAIL quota#` line exists anywhere in `gate.log`.
- neither failing case declares a path the delta touches:
  - `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` declares
    `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json`
    (`tools/loop/tests/loop-cases.sh`, the `# files:` line of the case).
  - `adapter#theResultBoundExemptsGateEvidence` declares
    `tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh tools/pi-trim-result.mjs`
    (same file, case body).

## Red before this work, red after it

1. **`run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`** — red on 2026-11-13, before this item's first
   process (its red test is timestamped `2026-11-16T00:56:02Z`):
   - `prior-red-aHup-2026-11-13.log` — `FAIL`, timestamp `2026-11-13T22:52:39Z` (copy of the red-on-HEAD of
     `openspec/changes/archive/2026-11-14-fix-a-hup-case-ends-on-its-own-clock`).
   - the case is a signal case whose own comment in `tools/loop/tests/loop-cases.sh` calls it "The measured red of
     issue #860, staged on purpose"; the archived gate of
     `openspec/changes/archive/2026-11-15-fix-the-record-names-the-items-epic/evidence/gate.txt` recorded the same
     behaviour in a pooled run on 2026-11-15 — `FAIL` in the pool, `PASS` when the case is run alone, and concluded:
     "The two `run#` cases are the load's, not the delta's: each passes alone, and neither path this change touches
     is declared by them."
2. **`adapter#theResultBoundExemptsGateEvidence`** — red in another change's gate on 2026-11-14T21:25:52Z, on a
   tree that carries none of this work (item #916's gate, head `42bc8581`, base `4c4e6819`):
   - `prior-red-adapter-gate-916-2026-11-14.txt` — `985:FAIL adapter#theResultBoundExemptsGateEvidence`.
   - the mechanism is the reading item's own repeat-read ledger, not the tree: the case drives
     `tools/pi-trim-result.mjs` over the fixed path `items/816/gate.log`, and this item's ledger already holds that
     path from the gate child's own reading (`ledger-items-878.tsv`: `worker-gate-prompt … 6000 … items/816/gate.log`),
     so the repeat rule replaces the 6 000-byte result with the already-read notice — the `211` bytes the assertion
     reports. With a fresh ledger the same case passes: `case-adapter-bound-probe-fresh-ledger.log` —
     `PASS adapter#theResultBoundExemptsGateEvidence`. Item #916 recorded the same diagnosis for the same case
     ("not idempotent under an item's ledger … the delta touches neither the trimmer nor that case").

Stays red after the work: both rows above are this gate, measured on the archived change.

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/878`:

- `gate.log` (the gate's own full log; the gate child carried it first)
- `gate-run-r2.txt` (the gate child's own tail of it)
- `gate-selftest-from-git-b307bf13.xml` (the failing step's tally, from `app/build/test-results/loop-gate/` of the change root)
- `prior-red-aHup-2026-11-13.log`
- `prior-red-adapter-gate-916-2026-11-14.txt`
- `case-adapter-bound-probe-fresh-ledger.log`
- `ledger-items-878.tsv`

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run, no issue-tracker touch.
