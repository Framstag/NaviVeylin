# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The report says which phase and which role spent the run's clock

A run's spend SHALL be split where it was spent: the clock by the phase that spent it — the model time, the gate,
and the other tools the children ran — and the tokens by the role that spent them (worker, reviewer, tail), both
read from what an item's own event stream records when its record is written, never from a stream the report would
have to find again. It SHALL also state the share of the tokens the run spent after an item's first gate. Each
figure SHALL print `-` with its reason where nothing was measured, never a zero that reads as a measurement.

#### Scenario: The clock and the tokens are split where they were spent

- **WHEN** an item's stream records turns with durations and tool calls with durations, one of them running
  `gate.sh`, and sessions whose prompt paths name a worker, a reviewer and a tail
- **THEN** the state records the sum of the turn durations as the model time, the durations of the calls that ran
  the gate as the gate time and every other tool's durations beside them, and the tokens of each session under the
  role its prompt names, the tail told apart from the worker; and the trend prints `model … · gate … · other …`
  with their total and `worker … · reviewer … · tail …` with the tokens they are a share of
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: The share spent after the first gate is stated with both its numbers

- **WHEN** a session of the item runs the gate at a known turn and spends a known number of tokens from that turn on
- **THEN** the trend prints the share of that session's tokens spent from its first gate on, with the tokens after
  the gate and the session total it is a share of, counted over the sessions that ran a gate rather than over every
  session of the item
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: A figure nobody measured prints as absent, not as zero

- **WHEN** an item's stream records no duration and no usage, or the state was written before the split was
  recorded
- **THEN** each of the three lines prints `-` with its reason — no duration measured, no gate to split at, or a
  state written before the report read the figure — rather than a zero that reads as a measurement that was made
- **Case**: `report#aStreamWithoutDurationsPrintsDash`
