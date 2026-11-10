# Design — the program a command starts, not every word of it

## Root cause

`is_gate` (`tools/loop/state.sh`, inside the one jq program `child_usage_json` runs) tested every
whitespace-separated word of a tool command for the gate's program name, so a command that merely named the gate's
path counted as a gate run. Two figures are read off the first matching turn — `phases.gateMs` (the durations of the
calls collected as gate calls) and `metrics.afterGate` (the tokens of the turns from that turn on) — and both were
wrong for the same reason. The command shapes that made it wrong are not corner cases: the worker's own Phase D
opens with `{{GATE_CMD}} --plan --base {{BASE}}` (`tools/loop/prompts/worker.md:261`), and a child reading the gate
to understand its plan writes `sed -n 1,60p tools/loop/gate.sh`.

## The single fix

`is_gate` reads the **program** the command starts:

1. the command is cut into words on whitespace and on the shell's separators (`;`, `&&`, `||`, `|`), so a piped or
   chained call is read by the segment that starts it;
2. a leading `cd <dir>` is dropped (its `&&` was already a cut);
3. leading `VAR=value` assignments are dropped;
4. `bash`, `sh`, `exec`, `command`, `timeout <n>`, `env` and `nohup` are dropped, with `timeout`'s numeric argument,
   and steps 3–4 repeat;
5. the remaining first word must be the gate's program (`gate.sh`, or any path ending in `/gate.sh`);
6. no word of the command may be `--plan`.

One predicate, so both figures are corrected with one edit, and the same cut is applied where the call's duration
is attributed. The words of the `LOOP_GATE_CMD`-derived program name are the profile's, never this engine's
(`run#engineNamesNoProjectFact`); `--plan` is the engine's own word — it is what the worker's prompt asks for and what
`measure.sh` reads — and the gate already refuses to count it (`openspec/specs/agent-issue-loop/spec.md`, "A child
pays for the full gate at most the recorded number of times").

## Blast radius

- `tools/loop/state.sh` — the predicate only. `metrics.gateMs` and `metrics.afterGate` change value; nothing else
  reads either (`tools/loop/report.sh` prints them, `guidelines/Backlog.recipes.md:556`: "a figure, never a stop").
- `tools/loop/tests/loop-cases.sh` — one case, both arms (a mention and a plan are no gate; an invocation behind
  `cd`, `timeout` and `bash` still is), declared in its first line beside the case it reads.
- `tools/loop/tests/loop-mutations.sh` — three records, one per rule.
- The spec delta and the one guideline clause that own the figure.

A state written by the fixed engine records a smaller `gateMs` and, on an item whose only gate words were a read or
a plan, `afterGate: null`. An item that ran its gate records the same split as before. No run decision, budget,
bound or verdict reads either figure, so no item outcome moves.

## Rollback

Revert the `is_gate` block to the two-line predicate it replaced. `report#aCallThatNamesTheGateIsNotAGateRun` then
reports `tests=1 failures=1` (its first assertion) and the 267.x mutations report `MUTATION-DID-NOT-APPLY`; nothing
else in the tree changes, because nothing else was edited for the fix but the case, the mutations and the two prose
sites that state the rule.

## Alternatives the evidence eliminated

1. **The item's own proposal — the tail must not run in the gate's session.** Eliminated: it already does not.
   Since #744 a gate ends the child that ran it and `tail_after_gate` (`tools/loop/run.sh:1801`) renders a prompt and
   calls `run_child`; since #758 the gate is a child of its own. The five items' streams show it: three sessions in
   `items/808/events.jsonl` — `worker-prompt-round2`, `worker-tail-round2-prompt`, `reviewer-prompt-2` — and the
   tail's 361 279 tokens are outside every gated session. A change that split the tail again would be the split it
   already has.
2. **Exclude only `--plan`.** Eliminated by two of the five items: `items/738` (gate turn 42 of 55) and
   `items/819` (turn 44 of 62) split at `sed`/`git show`/`grep` over the gate's own file, with no plan call in the
   session. A plan-only exclusion leaves those two records as wrong as they were.
3. **Compare the whole command against `LOOP_GATE_CMD`.** Eliminated by the children's own wording: the calls that
   do run the gate carry `cd <dir> && …`, `timeout 300 …` and `bash …` prefixes the profile's value does not have,
   and the state reads the command as the tool call carried it.
4. **Have the gate child's report name its own gate turn instead of reading the stream.** Eliminated by the
   requirement the figure belongs to: the figures are read from what the item's event stream records when its record
   is written, never from a report the run would have to find again — and the stream is the only place a session's
   turn index exists.
