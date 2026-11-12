# A worker that reaches the context checkpoint is continued before the bound

## Why

`LOOP_ITEM_CONTEXT_WARN` is a checkpoint and not a stop: the run prints one `LOOP-CONTEXT:` line when the child's
own reading crosses it (`tools/loop/run.sh:1766-1770`) and the child runs on in the same session until
`LOOP_ITEM_CONTEXT_MAX` ends it (`tools/loop/run.sh:1771-1778`). The configuration says what that line is worth and
what is owed: the checkpoint is "the cheaper half of the bound below it", and the split it names is "a separate
change" (`tools/loop/loop.conf:226-241`).

**Root cause, in one sentence:** the run reads the reading at which a fresh session is cheapest and only writes it
down, so the item keeps paying the grown context on every turn until the stop ends it — and the stop is the run's
most expensive outcome.

The item's run of `2026-11-11T11:37:08Z` (`.pi/issue-loop/runs/2026-11-11T11:37:08Z.json`, read with
`jq -r '.items[] | select(.issue==847 or .issue==839) | {issue, result, stopReason, context: .metrics.context, turns:
.metrics.turns, wall: .metrics.wallSeconds}'`):

- #847 `cut`, `bound`, 200 174 tokens, 142 turns, 845.9 s; #839 `cut`, `bound`, 200 869 tokens, 125 turns,
  1 388.9 s — both `piExit` 124, both keeping a patch, neither closing;
- together 33 275 315 of the run's 75 442 305 tokens (44.1 %) and 2 235 s of its item wall
  (`[.items[] | select(.issue==847 or .issue==839) | (.metrics.tokens.input + .metrics.tokens.cacheRead +
  .metrics.tokens.output)] | add`, and `.metrics.wallSeconds` summed over the same two);
- both crossed the recorded 143 124 long before the stop: the two sessions ran at roughly 123 k and 126 k tokens a
  turn over 142 and 125 turns, so the checkpoint arrived near turn 100 of a 200-turn bound and the run did nothing
  with the line.

The bound stays 200 000 (#833's refusal, #836's `NOT_PLANNED`) and the checkpoint stays 143 124 (#851 kept both).
What changes is that the checkpoint is taken rather than printed.

## What Changes

When the item's **first worker** crosses `LOOP_ITEM_CONTEXT_WARN` below `LOOP_ITEM_CONTEXT_MAX`, the run ends that
child the way it ends a cut, keeps the work it left, and continues the item in the same run exactly as issue #851
landed that continuation — one child, on the work package and the kept patch — recorded as `worker-checkpoint` so a
reader tells it from `worker-continuation` (the child the stop ended) and from `worker-tail`. A child whose reading
stays below the checkpoint keeps its one session.

**The one fork the item left is answered by the tree, not re-opened.** The item asks whether the split is
unconditional or only while the run has "an item's worth" of deadline left; #851 asked the same question of its own
step and its `design.md` D3 settled it: unconditional while the run's own deadline has not passed, because a
continuation started after the deadline would be cut by the clock and name the clock instead of the reading that
actually ended the work. This change reads that recorded answer and applies it to the checkpoint split, so the two
splits cannot disagree.

**Surfaces.** `tools/loop/run.sh` (the checkpoint split in `run_child`, the transcript line it writes, the
continuation's step name, the split's reason), `tools/loop/worktree.sh` (`continue-item` names the reason in the
item's own note), `tools/loop/prompts/worker-continuation.md` (the split it was handed, not only the stop),
`tools/loop/loop.conf` (the checkpoint's own paragraph), `tools/loop/tests/loop-cases.sh` (3 cases added),
`tools/loop/tests/loop-mutations.sh` (3 records), `guidelines/Backlog.recipes.md`:517, the capability
`agent-issue-loop`.

surface: 3 cases added, 0 rewritten, 0 fixtures realigned, 3 mutation records added, 1 prompt skeleton realigned,
1 guideline section realigned, 1 spec requirement modified (3 scenarios added) —
`tools/look.sh 'LOOP_ITEM_CONTEXT_WARN' --files` (25 files; the ones this delta edits are `tools/loop/run.sh`,
`tools/loop/state.sh`'s readers, `tools/loop/loop.conf`, `tools/loop/tests/loop-cases.sh`, the spec and the
checkpoint paragraph of `guidelines/Backlog.recipes.md`:517).
