# A child is bounded by its own role's turns, and the item's total only warns

## Why

`run.sh`'s `run_child` ends whichever child reaches `LOOP_ITEM_TURNS_MAX` (250), and the count it compares is
`child_turns` over `items/<n>/events.jsonl` — one stream every child of the item appends to. The budget is the
item's, so the worker, the round before the gate, the gate child, the tail and the round after the gate all spend
from one pot and the last phase inherits the remainder.

**Root cause, in one sentence:** the bound is attached to the item while the work it is meant to bound is the
child's, so crossing it decides *which phase* is discarded — and the phase it can discard is the one that reads
the finished work.

Measured 2026-11-05, per-child `turn_end` counts read from each item's own stream (`#780`: 80/34/42/41/**53**,
where the round-1 reviewer was ended at 53 of the same 250; `#767`: 59/25/19/33/38/73/**3**, where the round-2
worker was given three turns before the item's pot ended it; `#783`, `#760`, `#782`, `#779` each at exactly 250).
The two items that closed used 192 (`#764`) and 241 (`#768`). `8f36abf1` (2026-11-04, "the diff is read before the
gate", #758) added the pre-gate reviewer — 25–40 turns on every item — and left `LOOP_ITEM_TURNS_MAX` and its
derivation comment unchanged. Six of that day's eight blocks were this bound.

surface: 1 requirement added with 2 scenarios, 1 requirement modified for one phrase, 2 cases added to the `run`
group, 2 cases repointed at the new keys, 1 configuration key replaced by 5 (one per role), 1 run-header field
renamed (`item-turns-max=` → `child-turns-max=`), 1 prompt line, 2 guideline sections, 1 engine function
(`child_role`) extended. Counted with `grep -c '^case_run_' tools/loop-cases.sh` and
`bash tools/loop-selftest.sh --list | cut -d'#' -f1 | sort | uniq -c`.

## What Changes

1. **The bound becomes the child's.** `run_child` reads `LOOP_CHILD_TURNS_MAX_<ROLE>` through `child_role`, and
   counts as the child's spend the `turn_end` lines the item's stream gained **since that child started** —
   the stream is shared, so the baseline is taken before the fork and the bound is read as a difference.
   `child_role` learns the two phases it could not tell apart: the gate child (`worker-gate-prompt.md`) and the
   round before the gate (`reviewer-pre-gate-prompt.md`), which both fell under plain `worker`/`reviewer`.
2. **The item's total stops nothing.** `metrics.turns` is still summed over the item's children and the report's
   `calls:` line still prints it beside `LOOP_ITEM_CALLS_PER_TURN_MIN`; the run reads no item-level turn key.
3. **The numbers are re-derived per role** over the ten items that ran the five phases (post-#758), where the
   archive is large enough (worker, review round) over the whole archive: worker 110, round before the gate 55,
   gate child 60, tail 45, review round 55 — each just past its own population's p75, the rule the old 250 was
   derived by when an item had four children.
4. **A block names the role and the key.** The transcript line reads
   `LOOP-TIMEOUT: the child reached the recorded turn bound of <n> turns (LOOP_CHILD_TURNS_MAX_<ROLE> for the <role>
   role) after <k> turn(s)`, and the run header names the five effective bounds.

## What this does not do

- It does not make an item cheaper. A child ended by its bound is still a blocked item; the change makes the
  bound fall on the phase that overspent instead of on whichever phase runs last.
- It does not add an item-level turn **band**. The report already prints the item's turns; a band would be a
  second number to derive and nothing in the report reads one today.
- It does not touch the wall clock, the run's deadline or the memory floor. Those bound the child and the item
  as they did.
