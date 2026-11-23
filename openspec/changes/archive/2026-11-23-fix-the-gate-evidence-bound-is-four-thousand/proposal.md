# Proposal: fix-the-gate-evidence-bound-is-four-thousand

## Why

The gate-evidence exemption hands a result 8 000 characters over whole while the archive's recorded bound is 2 000
(issue #1044): `GATE_EVIDENCE_BOUND = 8000` (`tools/pi-trim-result.mjs:180`) and `trimResult` raises the bound to
`Math.max(bound, GATE_EVIDENCE_BOUND)` for the class (`:236`), four times the width issue #1004 measured. Over
`.pi/issue-loop/items/*/tool-result-trims.log` the archive holds **338 results cut at `kept=8000`**, naming
5 015 685 characters between them (mean 14 839, max 26 766) — 338 × 6 000 = 2 028 000 characters more than the
2 000 bound allows, near 20 M tokens at the archive's measured 28.4× re-send factor, ~1.8 % of the two-week spend.
The class exists because `guidelines/BuildEvidence.md` §4d's diagnosis reads a gate log and the suite XMLs whole; the
question is whether 8 000 is the width that keeps the diagnosis, and it is not.

The 338 is an undercount, and the issue names the reading of the real number as the first thing owed: the trim log
records only a result the trimmer *cut*, so the class's results between 2 000 and 8 000 were handed over whole and are
in no log. That reading is **not** taken here — the item's own records under `.pi/issue-loop/items/` are not readable
from an agent's shell (the policy refuses them), so the count stays the log's and is stated as the lower bound it is.
The fix does not rest on the bigger number: it is one constant and one case either way.

## What Changes

- **`GATE_EVIDENCE_BOUND` is 4 000** (`tools/pi-trim-result.mjs`) — half the width the class shipped with and twice
  the bound beside it, still wide enough for §4d's `file:line`, JUnit `timestamp` order, failed step and flake line.
  The higher of the two bounds still applies, so a project that records more than 4 000 keeps raising the class.
- **The exempt class's cut keeps both ends** — head and tail, each half the width — because §4d reads the verdict line
  a gate log opens with *and* the failed step it closes on; the head-only cut the class had would drop the tail.
- **The case moves with the constant** — `adapter#theResultBoundExemptsGateEvidence` asserts the class is held at
  4 000, that a result between the recorded 2 000 and the class's 4 000 is handed over whole, and that a
  5 000-character gate log at `--bound 2000` arrives cut at 4 000, head and tail.
- **The two prose homes move** — `tools/loop/loop.conf`'s bound comment and `guidelines/Backlog.recipes.md` §7.

Not breaking, and the rollback is the one constant back to 8 000; the case's numbers are tied to the constant, so a
later width moves both arms with it.

## Capabilities

### Modified Capabilities

- `agent-issue-loop`: adds the requirement that the gate/evidence class is held at its own 4 000 width, and that its
  cut keeps both the head and the tail.

## Impact

- `tools/pi-trim-result.mjs` — `GATE_EVIDENCE_BOUND`, `trimResult`, `trimNotice`, `boundedTail`, and the comments.
- `tools/loop/tests/loop-cases.sh` — `case_adapter_theResultBoundExemptsGateEvidence`.
- `tools/loop/loop.conf` — the recorded bound's comment block (the key itself is unchanged).
- `guidelines/Backlog.recipes.md` §7 — the paragraph that prices the recorded bound.
- Related items: issue #1004 (the 2 000 bound), issue #815 (the 8 000 this replaces).

surface: 1 constant, 1 predicate call site, 3 functions (`trimResult`, `trimNotice`, `boundedTail`) in
`tools/pi-trim-result.mjs`, 1 case in `tools/loop/tests/loop-cases.sh`, 2 prose homes —
`tools/look.sh 'GATE_EVIDENCE_BOUND' --path tools/pi-trim-result.mjs` and `tools/look.sh 'GATE_EVIDENCE_BOUND|head-and-tail'
--path guidelines/Backlog.recipes.md`
