# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A spent quota pauses the run instead of ending it

The run SHALL ask the project's recorded quota command before each item and, when the command holds, SHALL wait
until the instant it names and ask again, moving its own deadline with the wait, so that a condition outside the
run costs it no budget and needs no new command. The check SHALL be switchable for one run — by the run's own
arguments and by the environment — without editing the configuration, and the run's header SHALL name the reader it
armed or that it armed none.

#### Scenario: A window the reader holds on is a pause, not a stop

- **WHEN** the recorded quota command answers with the status its protocol reserves for a hold
- **THEN** `state.sh next` exits 6 — not 3 — with the reader's reason and the instant it named on stdout, and it
  records no stop, so the run's end is still its own condition rather than the provider's
- **Case**: `state#aQuotaWindowIsAHoldNotAStop`

#### Scenario: The run waits and then works the item

- **WHEN** a run reads a hold at the boundary before an item, the reader having named an instant seconds away, and
  the reader lets the run go on its next call
- **THEN** the run prints the pause with the reader's own reason, records it in the run's state with the seconds it
  waited, sleeps until that instant, and works the item — ending on its own item cap
- **Case**: `run#aQuotaHoldPausesTheRunAndResumesIt`

#### Scenario: A pause is not spent budget

- **WHEN** the run waits for a condition outside it, and its budget would otherwise have been spent by the time the
  wait ends
- **THEN** the run's deadline moves by exactly the seconds the wait takes — after which the same run may start
  another item — and the wait is kept in the run's record with the reason it was taken for
- **Case**: `state#aHoldMovesTheDeadlineWithIt`

#### Scenario: A reading that could not be made is not a refusal

- **WHEN** the quota command fails, or is not there at all
- **THEN** the run does not hold: it is named on stderr as a check that could not be made, and the candidate the
  queue holds is handed out as usual
- **Case**: `state#aQuotaCheckThatCannotBeMadeIsNotARefusal`

#### Scenario: The check is a switch the operator holds

- **WHEN** a run is started with the command-line switch that turns the check off, or with the key set to an empty
  value in the environment
- **THEN** no reader is asked for that run — while the same run without the switch asks the one the configuration
  names — and the run's header records which reader it armed, or that it armed none
- **Case**: `run#theQuotaSwitchIsExplicit`

#### Scenario: The report reads the wait as neither spend nor budget

- **WHEN** a run that recorded a wait is reported
- **THEN** the summary's spend figure excludes the wait, the wait and how many there were are named beside it, and
  the figure floors at zero — so a paused run never reports a negative budget left, and its clock and its work are
  two numbers
- **Case**: `report#theSpendIsWorkNotWaiting`

#### Scenario: No recorded command asks nothing

- **WHEN** the project's configuration records no quota command
- **THEN** no process is started, no question is asked, and the run's own conditions are its whole authority — the
  behaviour the engine had before the command existed
- **Case**: `state#noQuotaCommandAsksNothing`
