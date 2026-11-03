# Proposal — fix-calls-per-turn-is-a-reported-number

## Why

Item #757, verified in the tree on `main` at `47ec83e5` (re-applied on `5e2de33e`):

- **The shape of a run is not a number anywhere.** `tools/loop/prompts/worker.md` said nothing about how many
  independent calls one turn may carry — its only rule about calls is the one the measuring block states, "One
  call per measurement" — and nothing measured the shape. `tools/loop/state.sh`'s `child_usage_json` counted the
  tools an item called per name and the tokens each turn spent, and held no `turn_end` count, so no reader could
  derive the ratio; `tools/loop/report.sh` printed the review rate, the item wall median and the landings, and no
  figure of the shape.
- **The shape, measured**: over `.pi/issue-loop/items` (227 items, 2026-11-03, the streams `tools/loop-spend.py`
  reads) the archive carries **36 825 `tool_execution_end` lines in 33 183 `turn_end` lines — 1.11 calls per
  turn** — at 78 k tokens a turn, 97 % of it cache read. Per item: p10 0.99, p25 1.05, p50 1.11, p75 1.22, p90
  1.32, maximum 1.61; no item ever amortised two calls in one turn. The same archive spends 8 160 `ctx_read`
  calls, 3 909 of them a second read of a path the session had already read — each one a whole turn.
- **Why it costs what it costs**: every cost in this loop is turns times context, so halving the calls per turn is
  worth roughly a third of the archive's spend without touching the gate, the review or an item. The levers that
  exist for it (`tools/look.sh` #687, the session split at its first gate #688) were built for exactly this, and
  nothing measured whether they were used.
- **The red test**: `report#anItemBelowTheCallsPerTurnFloorIsNamed` fails on the base (worktree at `5e2de33e`, the
  case copied in, `bash tools/loop-selftest.sh --case` there) — log `evidence/red-on-head.log`, ending
  `FAIL report#anItemBelowTheCallsPerTurnFloorIsNamed` with `the item's tool calls are counted from its event
  stream: expected '6', got 'null'`. It is false because the capability is absent, not because a number moved.
- **The owning requirement** is the capability's report contract as the last two requirements state it —
  `agent-issue-loop` "A report warns when an item outspends a recorded band" (`openspec/specs/agent-issue-loop/spec.md`)
  is the same kind of figure for the *amount* spent and says nothing about the *shape*; this change adds that
  requirement. The owning guideline is `guidelines/Backlog.recipes.md` (the loop's bound paragraph, the report
  paragraph and the trend paragraph), and the recorded value's home is `tools/loop/loop.conf`.
- **The surface**, counted before the plan was fixed (`grep -n 'case_report_anItemOverTheTokenBoundIsFlagged'
  tools/loop-cases.sh`; `grep -n 'sixteen columns' tools/loop-cases.sh`; `grep -n 'metrics.tools.read'
  tools/loop-cases.sh`): **1 case added** (`report#anItemBelowTheCallsPerTurnFloorIsNamed`), **1 case rewritten**
  (`report#wallTimeAndReasonInTheTable`, the only reader of the table's column count — 17 pipes become 18), **1
  case extended** (`run#childUsageIsRecordedPerItem`, which pins what a child's stream records), **0 fixtures
  realigned** (the new case writes its own two event streams), and **4 mutation records added**.

## What changes

1. `tools/loop/state.sh` — `child_usage_json` records `calls` (every `tool_execution_end`) and `turns` (every
   `turn_end`) beside the tools and the tokens, summed over an item's children as its tokens are; `cmd_report`
   appends a `calls` column after `over`, printing the item's ratio or `-`.
2. `tools/loop/report.sh` — the trend prints the run's calls per turn with the two counts it is derived from, and
   names the items below the recorded floor (or prints the floor as off, or `-` with its reason where nobody
   measured one — and apart from a state written before the two counts existed).
3. `tools/loop/loop.conf` — `LOOP_ITEM_CALLS_PER_TURN_MIN=1.05`, the archive's p25, with its measurement.
4. `tools/loop/prompts/worker.md` — the rule the measurement implies: independent reads, greps and lookups belong
   in one turn, and a path already read is cited rather than opened again.
5. `tools/loop-cases.sh`, `tools/loop-mutations.sh` — the new case, the shape case's pipe count, the extended
   child-usage case, and one mutation per new site (`78.1`–`78.4`).
6. `guidelines/Backlog.recipes.md`, `PORTING.md`, `.pi/skills/loop-spend/SKILL.md` — the recorded value, its
   measurement and where the key lives.

A **warning, never a stop**: nothing in `run.sh` reads the floor, and an item below it is worked, reviewed, gated
and closed exactly as before. The stop this figure complements already exists — `LOOP_ITEM_TURNS_MAX`.

## Impact

Additive, and no compiled file changes: the gate is the loop's own shell suite. `metrics` gains two keys on every
new item record and the report gains one column at its end, so a reader that quoted the earlier shape keeps its
indices; a state written before the change prints `-` in the new column and `-` with its reason in the trend. No
engine decision changes. Rollback: revert the commit — no state, no branch and no tracker write depends on the new
fields.

The `tools/loop/**` pathspec owes the loop's whole self-test, the record check and the full mutation set: that is
the gate (`tools/loop/gate.sh`), run once.

## Reported, not repaired

`tools/loop/evidence-check.sh` exits 1 on this change with ten findings in two classes, every one of them raised
by the two verbatim review reports and none by an artifact of the delta: the reports name **guideline**
subsections (7b and 7e of `guidelines/Backlog.recipes.md`) with a section-sign prefix, which the check reads as a
migrated backlog entry; and one report quotes the gate's `LOOP-GATE:` line, whose `tests=` figure is a sum over the
gate's steps that no JUnit XML carries — the per-suite tallies are what exists. Both are properties of the check's
reading of prose, not of this change, and each is reported here rather than repaired inside a delta that owns
neither.
