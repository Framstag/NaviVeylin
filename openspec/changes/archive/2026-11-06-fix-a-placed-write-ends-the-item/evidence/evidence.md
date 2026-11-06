# Evidence — fix-a-placed-write-ends-the-item

Every artifact below is a copy of what the run's own processes wrote; none was produced by the archive pass.

## The gate, once, by the gate child

`gate.log` — the gate's full output (1747 lines), rendered in `.pi/worktrees/loop` after the pre-gate review's
findings were applied. Its tally per step is one JUnit file in this directory, copied from the gate's own tally
directory (`app/build/test-results/loop-gate`, the path `tools/loop/loop.conf` names for
`LOOP_GATE_TALLY_DIR`). Summed over the twenty-six tallies: 867 cases, 0 failures, 0 errors — the gate line's
tally is that sum, and no single JUnit file carries it.

The step the two cases live in is `tools_loop-selftest.sh_--from-git_6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db.xml`,
whose own testsuite line reads `tests="343" failures="0" errors="0"` — the whole loop suite, the added case
included. `gate-plan.log` is the same gate's plan (`--plan`), naming the steps before it ran.

## Red on the base commit `54373f19` — the two cases, before the engine moved

- `red-on-HEAD-placed-write.log` — `run#aWriterTheRunCanPlaceBlocksTheItem` fails on HEAD:
  `the reading that ended the item is recorded as a step of its own: expected '1', got '0'`.
- `red-on-HEAD-scratch.log` — `run#aChildIsGivenOneScratchDirectory` fails on HEAD:
  `the worker's prompt names no scratch directory`.

Each log is a one-case suite: `loop-selftest: 1 of 1 cases failed`. No gate tally carries these runs because the
gate is the gate child's and it ran after the fix; the red halves are these two logs.

## Green after the engine moved

- `green-placed-write.log` — `PASS run#aWriterTheRunCanPlaceBlocksTheItem`.
- `green-scratch.log` — `PASS run#aChildIsGivenOneScratchDirectory`.

## The engine's cases that reach the moved lines

`scoped-suite.log` — the scoped suite the tail's task 7 names, all green: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`,
`run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`, `run#childWriteOutsideTheWorktreeIsNoted`,
`run#strictOutsideWriteBlocksTheItem`, `run#strictOutsideWriteDiscardsTheReviewRound`, `run#childUsageIsRecordedPerItem`,
`run#thePreGateReviewRunsBeforeTheGate`.
