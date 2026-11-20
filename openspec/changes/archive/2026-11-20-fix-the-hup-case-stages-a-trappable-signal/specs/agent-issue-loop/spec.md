# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: An interrupted run ends the child it started

A run that takes a signal SHALL end the child it was waiting for, together with everything that child started,
before it records the item's outcome — the child is put in a process group of its own so the wall-clock cap can
bound it, and the same group SHALL be ended by the run's own traps. A case that measures that path SHALL send the
signal to a run whose shell can trap it: a signal the run inherited ignored is a signal no trap of its can answer, so
the case SHALL stage the run's disposition — not the figure its wait allows — and SHALL fail naming the disposition
when the staging is missing, rather than wait on a run nothing can end.

#### Scenario: A run ended by TERM leaves no child working

- **WHEN** a run is waiting for a child that is in the middle of its phase (a child blocked on a fifo is what
  that looks like) and the run receives `TERM`
- **THEN** the run exits with the signal's status rather than a finished run's, the child it started is
  no longer running, and the run's own record names `TERM` as the reason it stopped
- **Case**: `run#interruptedRunEndsTheChildItStarted`

#### Scenario: A run a vanished terminal ended takes the same path and leaves no item looking in progress

- **WHEN** a run is waiting for a child mid-item and receives `HUP`, the signal a terminal or a harness sends when
  it goes away
- **THEN** the run exits with status `129`, the child it started is no longer running, the item it was working is
  recorded `blocked`, its in-progress marking is removed, the run's record names `HUP` as the reason it stopped, and
  no lock is left behind
- **Case**: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`

#### Scenario: The HUP a case sends reaches a trap its run can install

- **WHEN** the case starts the run it is about to signal in a suite that was itself started detached, so `SIGHUP` is
  ignored on entry to every process of it (the gate starts its own steps with `nohup`)
- **THEN** the run is started with `SIGHUP`'s disposition back at its default, the case asserts from the run's own
  process that `SIGHUP` is not ignored before it signals, and a tree that lost that staging fails the case naming
  `SIGHUP` at once instead of waiting out the deadline on a run no signal can end
- **Case**: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`
