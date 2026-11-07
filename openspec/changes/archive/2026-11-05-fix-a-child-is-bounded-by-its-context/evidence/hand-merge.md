# The hand merge of this change onto `528d8e2a`

This change was finished and gated on 2026-11-05 against base `62ab008d`. By 2026-11-06 the base had moved
through #786 (`9321eaf9`) and #787 (`528d8e2a`), which changed the same loop files: the item's single turn
bound became one key per role (`LOOP_CHILD_TURNS_MAX_<ROLE>`), a block at a bound became a `cut`, and the
review-round record grew a `cut` value. The loop's own adopt path could not land the branch —
`worktree.sh adopt-item` refused with a cherry-pick conflict in `guidelines/BacklogTooling.md`,
`tools/loop-cases.sh`, `tools/loop/loop.conf` and `tools/loop/run.sh` — so the two commits were replayed by
hand onto `528d8e2a` and the conflicts resolved by keeping both behaviours:

| file | resolution |
|---|---|
| `tools/loop/loop.conf` | the per-role keys and the `bound`-mechanism comment of #786 kept whole; `LOOP_ITEM_CONTEXT_WARN` / `LOOP_ITEM_CONTEXT_MAX` appended after them, with the bound described as one of two spend bounds |
| `tools/loop/run.sh` | the run header carries `child-turns-max` **and** `item-context-warn`/`item-context-max`; `run_child` keeps #786's per-role turn transcript line **and** gains the `context` arm beside it; the reviewer and pre-gate cut arms keep #785's `cut` outcome, with the reason widened to "one of its spend bounds" |
| `tools/loop-cases.sh` | #786's two cases (`eachChildIsBoundedByItsOwnRolesTurns`, `aRoleWithNoRecordedTurnBoundRunsUnbounded`) kept, and this change's two (`childPastItsContextBoundIsBlocked`, `theRecordedZeroKeepsTheContextBoundOff`) added beside them; both pairs declared and dispatched |
| `guidelines/BacklogTooling.md` | the configuration table keeps the per-role turn keys and lists the two context keys after them |
| `guidelines/Backlog.recipes.md` | the levers paragraph names the per-role turn bounds and the context bound as the stops the calls-per-turn band complements |
| `openspec/specs/agent-issue-loop/spec.md` | #786's requirement "A child is bounded by its own role's turns" kept whole; this change's requirement "The bound on a child is priced in what it spends, not in how many turns it takes" added after it with its two scenarios; the pre-gate cut scenario's `WHEN` names both bounds |

## What was checked, on this tree

```
bash tools/loop-selftest.sh --from-git 528d8e2a --jobs 4
  loop-selftest: all 322 cases passed (JDK 27, offline, no token, no sandbox)

bash tools/loop-mutations.sh --check
  all 243 cases passed — every selected record applies to the file it names

bash tools/check-doc-routes.sh
  all 329 section references resolve, all 5 skill references resolve

bash tools/check-todo-references.sh
  202 line(s) scanned, 66 distinct citation(s), 162 in the recorded table

bash tools/check-openspec-hygiene.sh
  OpenSpec artifacts are consistent: 23 open change(s) carry task markers …
```

The gate tallies under `evidence/gate-tallies/` are the ones the attempt recorded against `62ab008d`; they are
kept as the record of that run and are **not** a verification of this merge. This file is that verification,
and it names the command that produced it.

## What the replay does not carry

The reviewer's pre-gate report from 2026-11-05 (`items/760/`, review round before the gate) was read against
the old base; no reviewer has read this merged diff. The suite above is a mechanical check of the tree, not a
review of the change's reasoning.
