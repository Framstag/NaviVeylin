# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: The report says which phase and which role spent the run's clock

A run's spend SHALL be split where it was spent: the clock by the phase that spent it — the model time, the gate,
and the other tools the children ran — and the tokens by the role that spent them (worker, reviewer, tail) and by the
model that answered them, both read from what an item's own event stream records when its record is written, never
from a stream the report would have to find again. The tokens of a turn no `session` record reaches SHALL be a bucket
of their own, so the buckets the split holds are exactly the tokens the item's total holds. The phase line SHALL name
the item clock its three buckets are a share of, and what the buckets leave outside themselves, wherever the item's
record holds that clock. It SHALL also state the share of the tokens the run spent after an item's first gate. Each
figure SHALL print `-` with its reason where nothing was measured, never a zero that reads as a measurement, and a
role whose turns named no model SHALL keep its totals and name no model. A call SHALL count as the gate only where
its command starts the gate's program, past the assignments and wrappers a child writes, and a command whose argument
is the gate's own path, and a `--plan` call, SHALL be neither.

#### Scenario: The clock and the tokens are split where they were spent

- **WHEN** an item's stream records turns with durations and tool calls with durations, one of them running
  `gate.sh`, and sessions whose prompt paths name a worker, a reviewer and a tail
- **THEN** the state records the sum of the turn durations as the model time, the durations of the calls that ran
  the gate as the gate time and every other tool's durations beside them, and the tokens of each session under the
  role its prompt names, the tail told apart from the worker; and the trend prints `model … · gate … · other …`
  with their total and `worker … · reviewer … · tail …` with the tokens they are a share of
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: A turn no session record reaches is a bucket of its own

- **WHEN** an item's stream records a turn before its first `session` record — the record a rotation took off the
  head, or one a harness writes late — and turns inside the session that follows
- **THEN** the state records the first turns' tokens under a bucket of their own beside the role the session names,
  the model those turns named keyed as that bucket rather than under the worker, the buckets adding up to the tokens
  the item's total holds, and the trend prints every bucket the record holds — so the figure it prints is the spend it
  is over rather than the part of it a role happened to reach, and no tally is printed twice
- **Case**: `report#aTurnNoSessionRecordReachesIsItsOwnBucket`

#### Scenario: The phase line names the clock its buckets are a share of

- **WHEN** an item's stream records turn durations and tool durations and the item's record carries its own wall
  clock, or carries none
- **THEN** the trend prints the three buckets' total with the share of the item wall it is and the time the buckets
  leave outside both a turn and a tool call (`1.3 s over 1 item, 3 % of the 42 s of item wall — 40.7 s of it in
  neither a turn nor a tool`), and where the record holds no wall it prints that the total has no clock to be read
  against rather than printing it as the run's
- **Case**: `report#thePhaseLineNamesTheClockItIsAShareOf`

#### Scenario: The share spent after the first gate is stated with both its numbers

- **WHEN** a session of the item runs the gate at a known turn and spends a known number of tokens from that turn on
- **THEN** the trend prints the share of that session's tokens spent from its first gate on, with the tokens after
  the gate and the session total it is a share of, counted over the sessions that ran a gate rather than over every
  session of the item
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: A call that names the gate is not a call that ran it

- **WHEN** a session of an item reads the gate's own file (`sed -n 1,60p tools/loop/gate.sh`, `git show
  HEAD:tools/loop/gate.sh`, a `grep` over it) and asks the gate for a plan (`gate.sh --plan --base <sha>`), and
  another session runs the gate behind a `cd … &&` prefix, a `timeout <n>` word and `bash` — the wrappers a child
  writes are `bash`, `sh`, `exec`, `command`, `timeout <n>`, `env` and `nohup`
- **THEN** the reading session records no gate time and no turn to split at — its two commands are the other tools
  they are and the trend prints `-` with its reason for the run that ran no gate — and the session that started the
  gate behind those words records the call's duration as the gate time and the item is split at that turn
- **Case**: `report#aCallThatNamesTheGateIsNotAGateRun`

#### Scenario: A role's tokens name the model that answered them

- **WHEN** an item's stream records a reviewer session and a worker session whose turns name a model — the one the
  provider answered with, or the requested one when it reports none
- **THEN** the state records each role's tokens under the model that spent them, beside the role's own total, and the
  trend prints each tally as `role (model) tokens` with the role totals it is over, so a run that prices a role apart
  can be read back from the item's own record rather than from the configuration
- **Case**: `report#aRolesTokensRecordTheModelThatAnswered`

#### Scenario: A session that switched model is two tallies

- **WHEN** two turns of one session of an item name different models and spend different numbers of tokens
- **THEN** the state records each model's own tokens rather than one row priced at whichever model came first, and the
  trend prints one `role (model) tokens` row per model
- **Case**: `report#aSessionThatSwitchedModelRecordsBothTallies`

#### Scenario: A state that records no model keeps its role totals

- **WHEN** an item's stream records usage but no turn names a model, or a state was written before the model was
  recorded
- **THEN** the item records no model tally rather than an empty or zero one, and the trend prints the role totals it
  always printed and names no model
- **Case**: `report#aStateWithoutAModelKeepsItsRoleTotals`

#### Scenario: A role whose turns named no model keeps a row of its own

- **WHEN** one role of an item answered on a named model and another role's turns named none, so part of the item's
  spend no model explains
- **THEN** the explained tally prints as `role (model) tokens` and the unexplained part prints as the role alone, so
  the rows the line prints still add up to the total it is over
- **Case**: `report#aRoleWhoseTurnNamedNoModelKeepsItsRow`

#### Scenario: A figure nobody measured prints as absent, not as zero

- **WHEN** an item's stream records no duration and no usage, or the state was written before the split was
  recorded
- **THEN** each of the three lines prints `-` with its reason — no duration measured, no gate to split at, or a
  state written before the report read the figure — rather than a zero that reads as a measurement that was made
- **Case**: `report#aStreamWithoutDurationsPrintsDash`
