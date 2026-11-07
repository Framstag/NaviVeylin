# Proposal — a child that would pay for a third full gate is given the scoped command instead

## Why

**The entry, verified in the tree (issue #756).** The rule is stated and nothing enforces it:
`tools/loop/prompts/worker-gate.md:71` ("**Run the project's gate once**, exactly as the run's configuration
states it"), `tools/loop/prompts/worker.md:84` ("the gate is run once, by `{{GATE_CMD}}`") and `:238` ("Do not
re-run a gate to \"refresh\" evidence"). What bounds a child is `tools/loop/run.sh:1234` `child_turns`, which counts
`turn_end` lines and nothing else, and `child_context` beside it (a reading, not a bound); `tools/loop/gate.sh`
holds no memory of having run (`grep -n 'LOOP_CHILD_EVENTS' tools/loop/*.sh` names `run.sh` and the adapter only),
so a child that sees a red gate fixes and pays for the whole gate again. Not already fixed: `grep -rn
'LOOP_GATE_RUNS_MAX' .` over the base tree returns nothing.

**The measurement the item carries (2026-11-03, 227 items and 558 child sessions,
`.pi/issue-loop/items/*/events.jsonl`, durations from each `tool_execution_end`).** The gate was started **910
times and spent 35.1 h** — 60 % of all tool wall clock in the archive; 245 sessions ran it at all, and **117 of
those (48 %) ran it three or more times** (#670 44 times in 174 turns, #116 39 times in 152); 65 items spent more
than 15 minutes inside it. A full round is 600–1 100 s, so a 480-minute run can spend its budget on gate rounds and
never reach the review.

**Root cause, one sentence.** The gate is the loop's only expensive action that a *child* runs rather than the run,
and nothing in the process that pays for it reads how many times it has already been paid for in that child.
**The red test.** `gate#aThirdFullRunInOneSessionIsRefused`, on the base the run was given
(`git show 487fd526:tools/loop/gate.sh`): the third full run measured instead of refusing it — `expected '3', got '0'`,
`loop-selftest: 1 of 1 cases failed` (`evidence/red-run.log`, 2026-11-07T02:44:42Z).

**The owning requirement and guideline section.** Capability `agent-issue-loop` (this delta, "The gate refuses a
third full run in one child"); the rule it enforces is `guidelines/BuildGate.md` §2a, "Agent iteration protocol"
— "iterate with focused suites, gate once" (`guidelines/BuildGate.md:69`).

**Surface, counted before the plan was fixed** — `grep -c "^  'gate#" tools/loop-cases.sh` → `28` (27 before, so
**1 case added**), `grep -l 'gate\.sh' tools/loop/*.sh tools/loop/prompts/*.md tools/*-cases.sh` →
`tools/loop/gate.sh tools/loop/measure.sh tools/issue-tooling-cases.sh tools/loop-cases.sh` (no case rewritten, no
fixture changed):

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 2 engine files + 1 profile key + 1 guideline section + 1 prompt + 1 mutation record — grep -c "^  'gate#" tools/loop-cases.sh; grep -l 'gate\.sh' tools/loop/*.sh tools/loop/prompts/*.md tools/*-cases.sh

## What changes

1. `tools/loop/gate.sh` leaves one record line per **full run it starts** beside the child's own event stream
   (`<events>.gate-runs`), and counts the lines that carry the id of the session it is running in — the id the
   stream's last `session` line names, so a worker's runs and the gate child's are never one child's count. When the
   count reaches the recorded `LOOP_GATE_RUNS_MAX`, a further full run is refused with exit `3`: the refusal names
   every earlier run and prints the commands the delta owes (`--plan`'s own list) instead of running them. A refused
   run and a `--plan` call measure nothing and leave no record.
2. `tools/loop/loop.conf` records `LOOP_GATE_RUNS_MAX=2` beside the other gate bounds, with the measurement that set
   it. Unrecorded, or `0`, is no bound — the rule every other bound in that file follows (D16).
3. `tools/loop/prompts/worker-gate.md` names the refusal where the phase that obeys it runs, so the engine and the
   text that asks for one gate do not disagree. `guidelines/BuildGate.md` §2a is left at its measured size: the rule's
   own section is unchanged and the enforcement is written in the procedure that owns the gate.
4. `tools/loop-mutations.sh` gains one record for the new invariant, and
   `gate#aThirdFullRunInOneSessionIsRefused` is the case it turns red.

## What this does not change

The gate's own steps, its clock, its tallies, its exit codes `0`/`1`/`2`, and what a *first* full run does. It is not
a cheaper gate: it is the same gate, once more than once is a measurement.
