# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The spend split is taken at the call that ran, and the suite's tail is stated beside the gate's

`tools/loop-spend.py` SHALL take a session's split point from the call that **ran** a program and not from a word in
the command's text: the item's gate is a run of the gate's own program that asks for no step (`--plan`, `--list`,
`--help` run none), and a focused suite run is a run of the suite's program, narrowed or whole. The report and the JSON
SHALL state the share after the first gate run, and the share after the first focused suite run beside it, each over
the sessions that made that call and each naming the call it was taken at. No figure SHALL be labelled the gate's that
is taken at the earlier of the two.

#### Scenario: A focused suite run is not the item's gate

- **WHEN** a session runs a `sed` over `gate.sh`, a `--plan` call and then a focused suite run before it finally runs
  the gate behind a `cd … && timeout <n> bash …` wrapper
- **THEN** the split is at the gate's turn, and the earlier calls move it neither to the reading turn nor to the
  suite's
- **Case**: `spend#aFocusedSuiteRunIsNotTheItemsGate`

#### Scenario: The suite's own tail is stated beside the gate's

- **WHEN** the report prints the share after the first gate
- **THEN** the share after the first suite run is printed beside it, over the sessions that ran one, so the two
  populations are not folded into one denominator
- **Case**: `spend#theFocusedRunsTailIsStatedBesideTheGates`

#### Scenario: The JSON and the machine line carry both positions

- **WHEN** `--json` or `--quiet` is read by a caller
- **THEN** `afterFirstGate` and `afterFirstSuite` (the `after=` and `suite=` keys of `LOOP-SPEND-GATE`) are both
  present and equal to the figures the human report printed
- **Case**: `spend#jsonCarriesBothVerificationPositions`
