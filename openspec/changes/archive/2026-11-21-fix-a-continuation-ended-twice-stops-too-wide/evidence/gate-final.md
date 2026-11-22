# The gate re-run after the rebase onto `09d35ae6`

The branch this change was worked on was cut before `#1022` (the item's session chain) landed, so its rebase onto
`09d35ae6` merged two rules about the same reading: `#1022` continues a cut item's chain up to
`LOOP_ITEM_CONTINUATIONS_MAX`, and this change makes a continuation ended at a **context ceiling** the item's shape
(`too-wide`, no third child). The merge was applied in the engine and in the three cases that the two rules meet in:

- `run.sh` — the chain **stops** when the continuation it just started was ended at a ceiling, so the shape is
  recorded from two children instead of spending a third child to relearn it; `continuation_listing` (this change's
  own reading of what that child listed) lives in `run.sh` and its row in `layout.tsv` owns it, while
  `child_ended_by_context_bound` stays in `child.sh`, where `shared.tsv` places it.
- `run#childPastItsContextBoundIsCut` — the item is `skipped`/`too-wide` after **one** continuation, not after the
  chain's cap of three, which is what this change's requirement states.
- `run#aCutRunsItsGateOnTheKeptWork` — the continuation is ended at the **turn** bound rather than at the ceiling, so
  the case still reads the shape it is about (`cut`, with the gate run on the work it kept) instead of the shape a
  second ceiling now records.

`evidence/gate-output.txt` is the run that certifies the merged tree, `evidence/gate-exit.txt` its exit status, and
`evidence/gate-tree.identity` the tree it names. The files beside them are the record of the round that was worked
and reviewed, kept as they were written then.

- tree: `LOOP-GATE-TREE: head=b7519b824fe65508254d014ccd6740d1ccb081fb diff=1b044c47e14a2cef`
- result: `LOOP-GATE: tests=1303 failures=0 errors=0`
- base: `09d35ae6`, and the three `foreign#`/`porting#` cases that fail there are subtracted as `LOOP-GATE-BASERED`