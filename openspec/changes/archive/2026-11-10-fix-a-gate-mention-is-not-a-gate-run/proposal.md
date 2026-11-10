# Proposal — a call that names the gate is not a call that ran it

## Why

**Root cause.** `tools/loop/state.sh`, `is_gate` in the jq program `child_usage_json` runs (`:937` on the base
commit), read every whitespace-separated word of a tool command as a possible program —
`any(.[]; . == $gate or endswith("/" + $gate))` — so the report's first gate turn was the first turn whose command
*mentioned* `gate.sh` anywhere. Both figures taken from it inherit the error: `phases.gateMs` (the sum of the
durations of the calls `gateIds` collects) and `metrics.afterGate` (the tokens of the turns from that turn on).
The item was filed from the share this produced.

**Evidence, verified against the tree (issue #830).** The item's own figures are real and reproduce. The item was
filed from `metrics.afterGate.tokens` 15 822 480 of `total` 23 570 758 over five sessions, and
`tools/loop/report.sh:519` prints that share (`:439` only selects it); the same two numbers fall out of
the item's own streams, read with the probe this change versions (`evidence/split-point.jq`, its output
`evidence/split-point.log`): five sessions split at a gate word, 15 822 480 tokens after the split of 23 570 758 in
all. What the split was taken at is not a gate. Over the five
items the item was filed from, the session each record split at contains one of three shapes, and no other
`gate.sh` word (every figure below is the probe's):

- `gate.sh --plan --base <sha>` — the call `tools/loop/prompts/worker.md:261` orders **first** in Phase D, before any
  work: `items/719/events.jsonl` (a `worker-prompt-round2` session, gate turn 28 of 37, 1 544 852 of 3 114 185
  tokens after it), `items/808/events.jsonl` (turn 36 of 77, 5 175 756 of 6 549 119) and `items/810/events.jsonl`
  (turn 21 of 82, 5 096 453 of 5 696 297) — 50 %, 79 % and 89 % of those sessions read as "after the gate".
- a read of the gate's own file — `sed -n '1,60p' tools/loop/gate.sh`, `git show <sha>:tools/loop/gate.sh | sed …`,
  `grep -n "FROM-CACHE\|gate.sh" tools/loop/gate.sh` — in `items/738/events.jsonl` (turn 42 of 55, 1 505 774 of
  3 677 074) and `items/819/events.jsonl` (turn 44 of 62, 2 499 645 of 4 531 083).

The item's *mechanism* does not hold and this change withdraws it: the tail is not the gate's session. Since #744 a
gate ends the child that ran it and `tail_after_gate` (`tools/loop/run.sh:1865`) starts a fresh child, and since #758
the gate is a child of its own (`tools/loop/prompts/gate-deferred.md`); the same streams show the tail as a session
of its own — `items/808/events.jsonl`'s three sessions are `worker-prompt-round2`, `worker-tail-round2-prompt` and
`reviewer-prompt-2`, and the tail's 361 279 tokens are not in any gated session (the probe's output,
`evidence/split-point.log`, names that session and its tokens). So the 67 % was not spend carried by
the tail: it was the item's own derivation, split at a `--plan` call or a read. What this change does not do is bound
the tail's spend: the tail already runs in a session of its own, so the item's remedy is already in the tree and
what was wrong was the measurement the item was filed from.

**The red test.** `report#aCallThatNamesTheGateIsNotAGateRun` (`tools/loop/tests/loop-cases.sh`), red on the base
commit `c0ddb97c` with `tools/loop/state.sh` restored from it (`evidence/red-on-HEAD.log` and its JUnit carrier
`evidence/red-on-HEAD.xml`; the run was taken at 2026-11-10T00:08:46Z — the `date -u` of that command in the item's
record, since the log itself carries no timestamp): a
stream whose only `gate.sh` words are a `sed` read and a `--plan` call records no gate to split at, and the base
answers `afterGate = {sessions: 1, tokens: 6 000 000, total: 6 000 000}` — the whole item as "after the gate". The
run's tally, carried by `evidence/red-on-HEAD.xml`, is `tests=1 failures=1` (the log ends
`loop-selftest: 1 of 1 cases failed`). The same case pins the
other arm, so the fix cannot pass by finding no gate at all: a session that runs the gate behind `cd … &&` and
`timeout 300` and `bash` still records its 5 000 ms as the gate time and splits at that turn.

**The requirement the item contradicts.** `openspec/specs/agent-issue-loop/spec.md:3277`, "The report says which
phase and which role spent the run's clock", says the figures are read from what the stream records and that a call
"running `gate.sh`" is the gate; the state's own detection was not that call. The delta adds the clause and one
scenario; no other requirement moves.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md:254`, section 7b "What an item costs, and the
bounds on it", owns the three splits and states them (its paragraph at `:541`–`:557` measures and describes
`metrics.phases`, `metrics.roles`, `metrics.roleModels` and `metrics.afterGate`). It gains one clause naming what
counts as the gate, because the figure it describes was read the wrong way.

## What Changes

- `tools/loop/state.sh` — `is_gate` reads the command's **program**: leading `VAR=value` assignments, a `cd …`
  prefix and the `bash`/`sh`/`exec`/`command`/`timeout <n>`/`env`/`nohup` wrappers are skipped, the next word is the
  program, and a command carrying `--plan` runs nothing. One predicate, so `phases.gateMs` and `metrics.afterGate`
  are corrected together.
- `tools/loop/tests/loop-cases.sh` — the case `report#aCallThatNamesTheGateIsNotAGateRun`, registered in the case
  list and the dispatcher, with both arms: a mention and a plan are no gate; an invocation behind `cd`, `timeout` and `bash` is.
- `tools/loop/tests/loop-mutations.sh` — three records, `272.1`–`272.3`, one per rule the case pins. Renumbered
  from the branch's own `267.1`–`267.3`: the tree had spent `267.1` and `267.2` on the stream rotation by the time this
  landed (`fix-rotation-keeps-the-records`), and the merged tree's records carry the new numbers everywhere.
- `openspec/changes/archive/2026-11-10-fix-a-gate-mention-is-not-a-gate-run/specs/agent-issue-loop/spec.md` — the
  requirement's clause and the new scenario (archived with the change).
- `guidelines/Backlog.recipes.md:558` — one clause in the paragraph that follows the one that owns the figure.

surface: 1 case added, 0 cases rewritten, 0 fixtures realigned, 1 requirement modified, 1 scenario added,
3 mutations added, 1 guideline clause — the figure under it, re-taken on the tree that lands
(`git grep -c -E 'afterGate|gateMs'`, tracked files only, this change's own log excluded) is 54 matched lines in 18
files (`evidence/look-surface.log`, run 2026-11-10T20:41:49Z at `bf350dd0`), of which the tree's live assertions are
the one case above, its three mutation records and the one guideline paragraph; the rest are the archived changes
that wrote the figure, the engine files it lives in, and the skill that reads it. The suite's declared case count is
`LOOP-MEASURE-SUITE cases=393`.

## Impact

Behaviour: the two spend figures only; no run reads them (`guidelines/Backlog.recipes.md:556`, "a figure, never a
stop"), so no gate, budget, bound or outcome changes. A state written by this engine records smaller `gateMs` and a
`null` `afterGate` where the split was taken at a mention. Rollback: revert the `is_gate` block to the one-line
predicate; the case then reports `tests=1 failures=1` again. No compiled file, so no Gradle task is owed; the gate for
`tools/loop/**` is the offline self-test.
