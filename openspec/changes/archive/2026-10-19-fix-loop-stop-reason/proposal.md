# fix-loop-stop-reason

## Why

The run of 2026-10-19T07:12:47Z ended after 66 of its 480 minutes with `closed 0 · skipped 2 · blocked 2 ·
budget left 96 items / 414 min`. Nothing in the output named the stop condition. `state.sh next` prints it
(`two consecutive items ended blocked`) and exits 3, but `run.sh` captured that stdout into `next_out` and
discarded it before the `break`, so the counters were the whole account of a run that ended for a reason
nothing recorded (#184).

## What changed

- `state.sh stop <reason>` records the condition as `run.stop = {reason, at}` — the state file is the
  durable record and the only writer stays `state.sh`.
- `run.sh` prints `stop: <reason>` and records it through `stop_run`, on every path that leaves the item
  loop: the `next` stop conditions, `gh could not read #N`, a refused start, and a failed in-progress write.
- `report.sh` prints `stop: <reason>` above the item table, and says plainly that the run is still going
  when nothing is recorded — the report can be printed mid-run. The JSON report needed nothing: it already
  emits `.run`.
- `state.sh show` prints the stop reason and the round budget too; its `--help` range follows its block.

## Spec

No delta: the requirement ("the run justifies itself with numbers") is already stated; what was missing was
one of the numbers. `openspec/specs/agent-issue-loop/spec.md` needed no edit for this one.

## Cases and evidence

`run#stopReasonIsPrintedAndReported`. It cuts the run's output at the report header before asserting: the
first version grepped the whole output and **passed with the run silent**, because the report repeats the
same line at run end — the second mechanism hiding the first. Mutations 16.1, 16.2, 16.3 and 20.3.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`.
