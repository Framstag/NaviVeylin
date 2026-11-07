# Design — the split is recorded where the stream is read, and printed where the state is read

## Root cause

`tools/loop/report.sh` reads the state file and git and nothing else (its header, line 6: "every number below is
read from the state file (or from git) rather than from a report a child wrote"), and the state only ever records
what an item *spent*: `child_usage_json` (`tools/loop/state.sh:667-706`) folds an item's whole event stream into
`{tools, calls, turns, tokens, cost, context}` and the `done` writer stores it at
`tools/loop/state.sh:548-556`. The stream carries far more than that per line — `turn_end` carries the turn's own
`durationMs` and the assistant message's `toolCall` blocks with their arguments, `tool_execution_end` carries the
tool's `durationMs`, and a `session` record separates one child from the next — and none of those reach the state,
so the trend block has no phase, no role and no position to split an item's spend at.

## The single fix

Extend that one reader, and print what it now records. Nothing else in the run changes.

1. **`child_usage_json`** gains a `sessions` pass beside its `turns` pass — a session's prompt (its first `user`
   message, which is the prompt file's path), its turns' usage and the calls each turn made — and three fields:

   - `phases: {modelMs, gateMs, otherMs} | null`. Model time is the sum of the `turn_end` messages' own
     `durationMs`; the gate is the sum of the `tool_execution_end` durations whose `toolCallId` belongs to a call
     whose command matches the program the project declares as its gate (`LOOP_GATE_CMD`'s own first path word —
     `run#engineNamesNoProjectFact` refuses an engine that names one, so `tools/loop-spend.py:55`'s pattern is not
     copied here);
     every other tool's durations are the third bucket. The three are disjoint — a turn's `durationMs` is the model
     response and the tool's duration is the execution that follows it (measured: 10 of item 750's 142 turns have a
     tool duration larger than their turn's, so the turn cannot be containing the tool) — so they add up to the
     clock the item's children spent working.
   - `roles: {worker, reviewer, tail} | null` — the per-session tokens (the same four fields `metrics.tokens` sums,
     read per turn as the `turns` pass reads them) keyed by the role the session's prompt path names:
     `worker-tail` → `tail`, `reviewer` → `reviewer`, anything else → `worker`. A role no session of the item
     carried stays null.
   - `afterGate: {tokens, total, sessions} | null` — over the sessions that ran a gate, the tokens of the turns
     from the first gate turn on, and those sessions' own tokens, so the share has both halves in the record.

   Each is null — never zero — where nothing was measured, the rule the function's header already states for its six
   existing figures.

2. **The trend block** (`tools/loop/report.sh:247-292`) prints three lines from those fields, in the block's own
   shape (a figure, then the numbers it is a share of, then what it is over):

   ```
     phases: model 30.1 h · gate 35.1 h · other 12.8 h · 78.0 h over 227 items
     roles:  worker 2168 Mtok · reviewer 402 Mtok · tail 508 Mtok · 3078 Mtok over 227 items
     after the first gate: 61 % — 1581 of 2591 Mtok, from the first gate of 189 sessions
   ```

   The figures in that shape are the item's own: 89.6 h spent, model 30.1 h, `gate.sh` 35.1 h, the loop's
   self-tests 11.6 h — the third bucket is the 12.8 h those leave, the self-tests among them, and the difference
   between 78.0 h and 89.6 h is time in no turn and no tool.

   and `-` with its reason where the state holds nothing: `no item recorded a phase duration`, `no item recorded a
   session role`, `no item of this run ran a gate in its stream` — or, where the items carry tokens and the state
   holds no such figure, the sentence the `calls:` line already uses for a state written before its field existed.

## Blast radius

- The recording is one function in `state.sh` and one object in the `done` writer's `metrics`; the report is one
  `jq` program and its format string. No caller, flag or exit status changes.
- Every existing figure is byte-identical: `tokens`, `context`, `calls`, `turns`, `tools` and `cost` are computed
  by the passes they were computed by before, and the new passes read the same events without writing to them.
- A stream with no `session` record (an older harness, or a plain-text log another agent wrote) yields no roles and
  no after-gate position, and a stream whose records carry no `durationMs` yields no phases — all three print as
  absent, which is what the second case asserts.
- Cost: `done` runs a few more passes over the item's stream — the same file the function already slurps once.
  Measured on this item's own stream while the delta was applied: see `evidence/measure.txt`.

## The alternatives the evidence eliminated

- **The report reading the item's event streams itself.** Eliminated by the rule its header states and by #757's
  precedent: the recording is done where the stream is read (`done`), and the report derives from the state — a
  state kept as `.pi/issue-loop/runs/<startedAt>.json` may be reported after the run's item directory has been
  swept, and then a report that read streams would print a run without its spend.
- **The role read from the word "reviewer" in the prompt text**, which is `tools/loop-spend.py`'s rule. Eliminated
  by the stream itself: the tail is started with `worker-tail-prompt.md`, so that rule folds the tail into the
  worker. Measured on item 750's stream: the three sessions' first user messages are `worker-prompt.md`,
  `worker-tail-prompt.md` and `reviewer-prompt-1.md` — the path names the role, and `run.sh` already names the five
  roles from it (`child_role`, `tools/loop/run.sh:1161`).
- **The wall from `metrics.wallSeconds`.** Eliminated by what it measures: it is the item's whole clock as the run
  passed it to `done --wall`, which has no phase in it and cannot be split.
- **A per-item column for the split.** Eliminated by the number of items: three more columns in a table that
  already carries 19 would be read per item, and the question the item asks is about the run.
