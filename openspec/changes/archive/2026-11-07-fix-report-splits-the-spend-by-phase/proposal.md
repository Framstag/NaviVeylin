# The report splits a run's spend by the phase and the role that spent it

## Why

Issue #759. `tools/loop/report.sh:247` opens the trend block — review pass rate, item wall median/min/max,
landings — and the run's spend appears only as a total (`spent N min`) and as per-item columns (`wall`, `tokens`,
`context`). A run that spent two thirds of its clock inside `./tools/loop/gate.sh` and a run that spent it reading
print the same shapes, which is the defect `LOOP_ITEM_TOKENS_WARN` was added to cure one level down (#662): the
number is printed beside nothing it can be judged against.

Measured over 227 items (2026-11-03, the archive's own `tool_execution_end` durations, counted by hand for this
item — no recorded `tools/loop-spend.py` run accompanies these figures): 89.6 h spent, of which
model time 30.1 h, `tools/loop/gate.sh` 35.1 h and the loop's own self-tests 11.6 h — and **1 581 of 2 591 Mtok
(61 %) spent after the item's first gate**, while the reviewer holds 402 Mtok against the worker's 2 168. None of
those three splits is in any report the loop has written; they were readable only by summing the raw event streams
by hand (`tools/loop-spend.py`, whose `byRole`, `modelMs` and `afterFirstGate` fields are exactly those three
numbers).

Root cause, one sentence: the state's reader of an item's stream records *what* a child spent and never *when* or
*as whom* — `child_usage_json` (`tools/loop/state.sh:667-706`, written into `metrics` at
`tools/loop/state.sh:548-556`) sums the item's six figures (tools, calls, turns, tokens, cost, context) over all of
its sessions and drops the two things the same lines also carry, the per-turn `durationMs` and the
`tool_execution_end` durations — so the trend block (`tools/loop/report.sh:247-292`) has nothing to split and
prints one `calls:` line about the shape of the spend.

The red cases, on the run's base `5b30ed79e61e679773f4a188622fd835aebfc24b`, `2026-11-06T14:40:37Z` (log:
`.pi/issue-loop/items/759/red-759-a.log` and `red-759-b.log`, copied as `evidence/red-on-HEAD.log`):

```
$ bash tools/loop-selftest.sh --case 'report#theSpendIsSplitByPhaseRoleAndGate'
  the model time is the sum of the children's own turn durations: expected '7500', got 'null'
FAIL report#theSpendIsSplitByPhaseRoleAndGate

$ bash tools/loop-selftest.sh --case 'report#aStreamWithoutDurationsPrintsDash'
  the trend prints a dash and its reason, not three zeros: '- · no item recorded a phase duration' not found in:
FAIL report#aStreamWithoutDurationsPrintsDash
```

The runner's own tally, per invocation: `tests=1 failures=1` (one case ran each time), `2026-11-06T14:40:37Z`
(`evidence/red-on-HEAD.xml`). Green on the change, same two invocations: `tests=1 failures=0`,
`2026-11-06T14:41:01Z` (`evidence/after-fix.log`, `evidence/after-fix.xml`), and the family beside them
(`bash tools/loop-selftest.sh --group report`) green at 22 of 22 cases, 137.81 s — a reading of the 2026-11-06 tree,
before the retry's `state.sh` edit; the retry's own green on the two cases is `evidence/after-fix-retry.log`
(`2026-11-07T02:16:40Z`), and its measure reads `cases=347` (`evidence/measure-retry.txt`).

The retry's own red run, on the base this attempt was handed (`83d8cea3`, the run's base commit), `2026-11-07T02:14:38Z`:
the same two cases, the same two failures (`tests=1 failures=1` each, `evidence/red-on-HEAD-retry.log` and its
`red-on-HEAD-retry.xml`), so the cases are red on the tree the change is applied to and not only on the one it was
planned against. The retry also carries the one fix a reviewer's finding owed: the gate program is read from the
project's declaration instead of being named by this engine, which is what `run#engineNamesNoProjectFact` refused
on the first attempt (`tools/loop/state.sh`, `child_usage_json`).

The single fix: the state records the three figures the stream already carries — the model time (`turn_end`'s own
`durationMs`), the gate time (the durations of the tool calls whose command runs the project's declared gate —
`LOOP_GATE_CMD`'s own program, never a name the engine carries) and every
other tool's durations, the tokens of each session keyed by the role its prompt names, and the tokens spent from
the session's first gate turn on — and the trend block prints three lines from them, each `-` with its reason where
nothing was measured. It is #757's shape exactly: the recording is done where the stream is read, and the report
derives its line from the state and from nothing else.

Owning capability and requirement: `agent-issue-loop`, "A report reports the shape a run spent its budget in"
(`openspec/specs/agent-issue-loop/spec.md:3151-3159`), whose rate #757 landed the same way, and its sibling "The
report states whether the loop is getting better" (`:2892-2898`), which is the trend block the three lines join.
Owning guideline section: `guidelines/Backlog.md` section 7b "What an item costs, and the bounds on it" and
section 7e "Leftovers, the report and the queue", whose bodies are `guidelines/Backlog.recipes.md` section 7b (the
cost measurements, where #662 and #757 are recorded) and section 7e (the report's own description).

Surface, counted rather than assumed:

surface: 2 cases added, 0 rewritten, 0 fixtures realigned — `grep -c "^  'report#" tools/loop-cases.sh` = 22 (20 on the
base), and no stream fixture moves.

- **2 cases added** — the report family grows from 20 to 22 declared cases (`grep -c "^  'report#" tools/loop-cases.sh`
  = 22 after the delta, 20 before), and the two are the only ones that name any of the three new lines; the 13
  places that already name a trend figure (`grep -c 'calls:  \|wall:   \|review: ' tools/loop-cases.sh` = 13) read
  the block as a set of lines and are untouched.
- The rule lives at two sites in the engine: the reader (`child_usage_json`, one function, `tools/loop/state.sh`)
  and the trend block (`tools/loop/report.sh`), the two places #757 touched for its ratio.
- No stream fixture moves: the stream shapes are written inline by the cases that need one
  (`grep -c tool_execution_end tools/loop-cases.sh` = 20 before the delta), and `grep -l tool_execution_end
  tools/loop/fixtures/*` names none — the `fixtures/` tree holds the `gh` scenarios, not event streams.
- The shipped spec gains one requirement and three scenarios, the guideline two passages (`Backlog.recipes.md`
  section 7b, section 7e), and the mutation registry one group (`256.x`, three records against the two new cases).

This change's own numbers, quoted from its own block (`bash tools/loop/measure.sh --change
fix-report-splits-the-spend-by-phase`, kept as `evidence/measure.txt`): `LOOP-MEASURE-CHANGE
fix-report-splits-the-spend-by-phase scenarios=3 cases=3 unnamed=0` — every scenario of the delta names a case that
exists — `LOOP-MEASURE-SUITE cases=332 cmd=tools/loop-selftest.sh --list` for the project's declared count (330 on
the base, the 2 being this delta's own cases), `LOOP-MEASURE-SURFACE surface: 2 cases added, 0 rewritten, 0 fixtures
realigned — `grep -c "^  'report#" tools/loop-cases.sh` = 22 (20 on the base)`, and `LOOP-MEASURE-GATE
seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run`.

## What Changes

- `tools/loop/state.sh` — `child_usage_json` also reads the sessions of the stream: `metrics.phases`
  (`modelMs`, `gateMs`, `otherMs`), `metrics.roles` (`worker`, `reviewer`, `tail`, each null where no session of
  that role existed), and `metrics.afterGate` (`tokens`, `total`, `sessions`), each null where nothing was
  measured — never a zero.
- `tools/loop/report.sh` — the trend block prints `phases:`, `roles:` and `after the first gate:`, each from the
  state and each `-` with its reason where the state holds no such figure.
- `tools/loop-cases.sh` — the two cases above.
- `tools/loop-mutations.sh` — group `256.x`: the recording dropped, the trend's gate figure zeroed, the after-gate
  share pinned to a constant. `bash tools/loop-mutations.sh --only "256.1 256.2 256.3"` reads each red on its own
  assertion and green again (`evidence/mutations-256.log`: `red-when-mutated: 3 · not-noticed: 0 ·
  restore-failures: 0`).
- `openspec/specs/agent-issue-loop/spec.md` — one requirement, three scenarios.
- `guidelines/Backlog.recipes.md` section 7b and section 7e — the measurement behind the three lines and the
  report's description of them.

## Impact

- Affected files: `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`,
  `guidelines/Backlog.recipes.md`, `openspec/specs/agent-issue-loop/spec.md`, and this change. No compiled file, no
  Gradle module, no native source, no registry key, no profile key.
- Additive: no existing figure, column, verdict or line changes; a state written before the delta holds no
  `phases`/`roles`/`afterGate` and the three lines say so.
- Rollback: revert the two engine hunks and the two cases; nothing else reads the three metrics.
