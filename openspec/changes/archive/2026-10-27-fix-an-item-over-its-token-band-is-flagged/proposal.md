# Proposal — fix-an-item-over-its-token-band-is-flagged

## Why

Item #662, verified in the tree on the run's base `7c0e5690`:

- `tools/loop/report.sh:11` states the report's three answers and names the spend — "the table's `wall`,
  `tools`, `tokens` and `context` columns — the last three summed from the child's own event stream … — and the
  run-level `spent`". `tools/loop/state.sh:585` is the table's header and `tools/loop/state.sh:601` the `tokens`
  cell. Nothing compares either number with anything: `grep -rn 'TOKENS_WARN\|token bound'
  tools/ guidelines/ .pi/skills/` finds nothing, and the only reader of the band is the analysis tool
  `tools/loop-spend.py`, which explains *why* an item cost what it did and reports no verdict either.
- **The band, measured** (`python3 evidence/token-band.py .pi/issue-loop`,
  log `evidence/token-band.log`): 164 items with a recorded `metrics.tokens` across `.pi/issue-loop/run.json` and
  `runs/*.json` — median 8 656 911, p75 16 858 818 (1.95× the median, 40 of 164 items above it), p90 22 958 848
  (16 items), maximum 44 163 688.
- **The red test**: `report#anItemOverTheTokenBoundIsFlagged` fails on the base —
  `evidence/red-on-head-report-anItemOverTheTokenBoundIsFlagged.xml`, `tests="1" failures="1" errors="0"`
  (2026-10-26T23:02:15Z), log `evidence/red-on-head.log`: `'over the item token bound (1000000 tokens): #21
  2.5x' not found in: …`. It is false because the capability is absent, not because a number moved.
- **The owning requirement** is the scenario `A run is recorded` of `agent-issue-loop`
  (`openspec/specs/agent-issue-loop/spec.md:83`), which requires the report to carry the spend and names no
  verdict over it; this change adds that verdict as one requirement of its own. The owning guideline section is
  `guidelines/Backlog.md`, the "The loop" section — the report paragraph (line 664) and "What an item costs"
  (line 296).
- **The surface**, counted before the plan was fixed (`grep -rn 'issue | class | verdict' --include=*.sh
  --include=*.md`; `grep -rn 'table_cell' tools/loop-cases.sh`): **1 case added** (`report#anItemOverTheTokenBoundIsFlagged`),
  **1 case rewritten** (`report#wallTimeAndReasonInTheTable`, the only reader of the table's column count and
  column indices — 16 pipes become 17), **0 fixtures realigned** (the case writes its own two event streams, as
  `report#zeroUsagePrintsDashNotZero` already does), and **2 mutation records added**.

## What changes

1. `tools/loop/loop.conf` — `LOOP_ITEM_TOKENS_WARN=16858818`, the archive's p75, beside the two clocks it
   belongs with. `0` (or a conf that records no key) means the flag is off, so a ported project owes nothing.
2. `tools/loop/report.sh` — one run-level line naming the items above the bound and how far above, or saying the
   flag is off. It states the bound, because the table's ratio is read against it.
3. `tools/loop/state.sh` — a sixteenth column, `over`, appended after `stop` (the file's own convention: a column
   is appended so a reader that quoted the earlier shape keeps its columns in order), printing `2.5x` or `-`.
4. `tools/loop-cases.sh` — the new case, and the shape case's pipe count.
5. `tools/loop-mutations.sh` — records `65.1` (the run-level line) and `65.2` (the column), one per site.
6. `guidelines/Backlog.md`, the "The loop" section, `PORTING.md` and `.pi/skills/loop-spend/SKILL.md` — the
   recorded value, its measurement, and where the *why* lives.

A **warning**, never a stop: the item's own text says so, and the reason is the one the loop's other bounds
record — a bound that ended an item would trade a measurement for a refusal and would fire hardest on the items
that were hardest to land. Nothing in `run.sh` reads the key.

## Impact

No engine decision changes, no state field is added (the report derives the flag from `metrics.tokens`, which is
already recorded), and an item below the bound is reported exactly as before. The `tools/loop/**` pathspec owes
the loop's whole self-test, the record check and the full mutation set — that is the gate.

The full mutation pass (`bash tools/loop-mutations.sh --from-git <base>`, log `evidence/mutations-from-git.log`)
selects 32 of the 180 records for these files and reports **31 red, 1 unnoticed**: `54.1`, the record that strips
the `tools/build-inputs*|tools/build-inputs-selftest.sh` pair from the gate's map, is green under the mutation
because a second pair (the `tools/sensors/sensors.json` row) owes the same command, and the case compares the
set of commands rather than the pairs. The same record is unnoticed against the base commit's own `loop.conf`
(`bash tools/loop-mutations.sh --only 54.1 --engine <a copy of tools/ with the base loop.conf>`), so it is a
defect of the tree at the base and not of this delta; it is reported here rather than repaired, because a record
that cannot fail is its own change with its own case.
