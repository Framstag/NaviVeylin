# A reading phase is bounded by its own key, and the bound's rate counts every cut

## Why

The loop's context stop is one number for phases that are not one population, and on 2026-11-10 that made it the
run's leading stop. Over the day's three runs (`.pi/issue-loop/runs/2026-11-09T21:42:41Z.json`,
`runs/2026-11-10T08:19:24Z.json`, `run.json`) 19 items were attempted and 9 recorded `cut`:

- 8 of the 9 ended at a largest context of **199 178 – 211 475** against the recorded `LOOP_ITEM_CONTEXT_MAX=200000`
  (0.4 % under the stop to 5.7 % over), while the six items that closed read 114 522, 116 750, 155 145, 174 700,
  191 637 and 192 886 — the largest **3.6 % under** the stop, so the stop sat inside the population that finishes.
- Every one of the eight belonged to the role whose work **is** reading the item: four to the rework worker that
  answers a finding list (`reviewVerdict: round2-ended`) and three to the review round that reads the diff
  (`cut-short`). The ninth, `#831`, was ended by the rework round's turn bound.
- The 9 cuts cost **383 minutes** of wall clock inside items that a later run then worked again; one of today's
  three runs also lost 262 of its 480 minutes to a stop that is not this change's subject.

The second defect is the measurement itself. The report's `bound:` line counts `stopReason: bound`, and the spec keeps
that field off a round a review bound ended (`agent-issue-loop`, "An item the run's own bound cut is recorded `cut`,
not `blocked`"). So two of today's three runs recorded three cuts each and the line named none of them: the rate that
exists to expose a run hitting its bounds five times as often as the archive printed `0 of N` while items were being
thrown away.

## What

1. `LOOP_ITEM_CONTEXT_MAX_<ROLE>` — a role's own context stop, read through the child's role exactly as
   `LOOP_CHILD_TURNS_MAX_<ROLE>` already is, with `LOOP_ITEM_CONTEXT_MAX` as the fallback for a role that records
   none. `loop.conf` records 250 000 for the two reading roles (`WORKER_REWORK`, `REVIEWER`) — the archive's own
   maximum largest context (244 764, §7b of `guidelines/Backlog.recipes.md`) rounded up — and leaves the item's
   200 000 for the four work phases.
2. The run's header prints every role's figure beside the turn bounds (`item-context-bounds=…`), so the bound a run
   enforces is the bound it prints.
3. The report's `bound:` line counts **every** item the run's own bound ended: the result `cut`, which exists only
   for that, plus the `blocked` + `stopReason: bound` pair a record written before `cut` existed carries.

The refusal of a wider **item** bound (#833, #836 `NOT_PLANNED`, recorded in `guidelines/Backlog.recipes.md` §7b) is
not reversed: the item's key stays 200 000, and this scopes the lever to the phases that refusal was not measuring.

## Impact

- `tools/loop/run.sh` — `child_context_bound` / `child_context_bound_key` / `child_context_bounds`, the wait's stop,
   and the `LOOP-TIMEOUT` line naming the key that fired.
- `tools/loop/loop.conf` — two keys recorded with their measurement; the item's key unchanged.
- `tools/loop/report.sh` — the `bound:` line's hits and its archive clause.
- `tools/loop/tests/loop-cases.sh` — one new case, one extended case.
- `tools/loop/tests/loop-mutations.sh` — four records (`270.1`–`270.4`).
- `guidelines/Backlog.recipes.md` §7b — the refusal record extended by the scoping.
- `tools/feature-list/specs.json` — four classification rows the open changes
  `package-structure-as-five-kinds` and `domain-vocabulary-is-declared` owe, without which the gate step
  `tools/gen-feature-list.sh --check-classification` is red for every item that touches `openspec/**` (measured
  2026-11-10: it reddened 4 of 4 gates that day and forced one hand-written waiver).
- `tools/doc-routes.json` — the two named rules that no glob reached, so `tools/check-rule-routes.sh` was red for
  every item that touches a `*.md` or that map: **7 of the archive's 8 recorded gate waivers** are on that one suite
  (`jq -r '.items[]? | select(.metrics.gateWaived != null) | .metrics.gateWaivedSuite'` over
  `.pi/issue-loop/runs/*.json`), each paid as a hand-written paragraph in a child's transcript.

Additive: a profile that records none of the new keys is bounded exactly as before, and the report's line changes
only its tally. Rollback is the revert of the three files, which lands the old arithmetic unchanged.
No Android module, no native boundary and no UI surface is touched; no `guidelines/` document other than
`Backlog.recipes.md` §7b makes a claim this change moves.
