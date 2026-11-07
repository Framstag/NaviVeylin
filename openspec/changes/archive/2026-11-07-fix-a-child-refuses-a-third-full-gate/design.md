# Design — the count a child can take of its own gate runs

## Root cause

`tools/loop/gate.sh` had no memory of having run, and the only thing that bounded a child —
`run.sh`'s `child_turns` (`tools/loop/run.sh:1234`) — counts `turn_end` lines and cannot see the cost of an action
inside a turn. The gate is the loop's most expensive action (910 starts, 35.1 h, 60 % of all tool wall clock over
227 items) and the only one a *child* runs, so the one bound the run has does not cover it, and the prompt that asks
for it (`tools/loop/prompts/worker-gate.md:71`) is a request with nothing behind it.

## The single fix

`gate.sh` counts its own full runs in the child that started it, and refuses the one that would exceed the recorded
number.

**Where the count comes from.** A run leaves its own record: `gate.sh` appends `<session id>\t<command>` to
`<events>.gate-runs`, a file beside the stream the child's adapter keeps (`LOOP_CHILD_EVENTS`, which `run.sh`
exports per item and every process the child starts inherits), and counts the lines that carry the id of the session
it is running in. The signal is produced by the thing being counted, so "a command that mentioned the gate" and "a
command that ran the gate" cannot be confused.

**Why the session id.** The stream is the item's and holds every child the item has had, so a record without a
session could not say whose run it was: the worker's two runs and the gate child's first would be one child's three.
The id is the last `session` line's, which the stream carries once per child — and the run's own phase split gives the
gate child its first full run, which is exactly the run the loop means to spend.

**Why not the stream's own tool calls.** That was this change's first shape and it does not hold: the stream carries
every command the child ran, and a command that *mentions* the gate's file — a `grep` over it, a `sed` of it, the
`bash -n` an edit is checked with — is indistinguishable from one that runs it.

**Why the record is written after the refusal, not before.** A run that is refused measured nothing, and a run is
counted when it is *started*, so the line is written once and immediately before the steps — the refusal cannot
consume the run it refuses, and a run killed mid-way is still a run the child paid for.

**Why `3` and not `1`.** `1` is "a suite failed" and `2` is "usage or a base this tree cannot resolve"; a refusal is
neither a red nor a green measurement, so it is its own exit status. The refusal writes no `LOOP-GATE:` verdict line,
which is what a caller reading the log uses to tell "measured" from "not measured".

## Blast radius

- `tools/loop/gate.sh` — one configuration key, the record line and the count, one refusal site before the first
  step, and the usage header's exit list. Nothing else in the file moves: the steps, the clock, the tallies and the
  exit codes `0`/`1`/`2` are untouched.
- `tools/loop-selftest.sh` — one `unset LOOP_CHILD_EVENTS`, so a case never inherits the run's own stream (a case's
  gate would otherwise be charged for the runs the child that started the suite made).
- `tools/loop/loop.conf` — one key, in the project-specific block, with the measurement behind its value.
- `guidelines/Backlog.recipes.md`:530 ("The gate and its suite") and `tools/loop/prompts/worker-gate.md` — the
  loop's procedure and the phase that obeys it name the refusal, so the engine and the text that asks for one gate
  do not disagree. `guidelines/BuildGate.md` §2a already prices its sections in the routing row, so the rule's own
  section is left at its measured size and the enforcement is written in the procedure that owns the gate.
- `tools/loop-cases.sh`, `tools/loop-mutations.sh` — one case, one mutation record.
- The `tools/loop-selftest.sh` case count moves by one (`LOOP-MEASURE-SUITE cases=348` after this delta).

## Alternatives the evidence eliminated

- **A `LOOP_GATE_RUNS_MAX` enforced in `run.sh`** (ending the child that starts a third gate). Eliminated: the run
  sees the child's tool calls only as they are written, and ending a child mid-gate throws away the work the child
  was about to fix — the measured failure mode this archive already records for the wall clock (an item recorded
  `blocked` with its gate log green and its work uncommitted, issue #663). A refusal leaves the child running with
  the command it should have run.
- **Counting the stream's own tool calls.** Eliminated by this change's own first run: the gate's file is mentioned
  by every `grep` and `sed` a child runs over it, so the `gate#*` cases were refused their first run on a tree that
  was working on the gate. A count has to be produced by the run it counts.
- **Counting every start line minus one** (the current invocation). Eliminated on the same evidence and by a race:
  the harness writes the start line from one process and starts the gate in another, so on a host where the write
  lands late the subtraction refuses the *second* run.
- **Refusing inside the prompt** (a line in `worker.md` saying "two at most"). Eliminated: the prompt already said
  it — `worker-gate.md:48` is "run the gate once" — and 117 of 245 sessions ran it three or more times anyway. The
  issue's own direction, and the reason this change is in `gate.sh` and not in a prompt.

## Rollback

Delete the `LOOP_GATE_RUNS_MAX` line from `tools/loop/loop.conf`: the engine's default is *no bound*, so the gate
returns to the tree's behaviour before this change with no code edit and no second release. Reverting the commit
alone also restores it, and neither touches the stream, the tallies or any evidence.
