# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A child never outlives the run's deadline

A run's time budget SHALL bound the child it is waiting for, not only the item it starts next, so that a
process can never spend a budget the run was not given; and a run the deadline ends SHALL leave the deadline
as its recorded stop.

#### Scenario: The run's deadline ends the child it is waiting for

The deadline was consulted between items, so one wait was the place a spent budget could not be noticed:
measured 2026-10-20, eleven of fifteen stuck runs were past their own `--minutes 90`, the longest heartbeat
read `291m30s`, and none carried a `LOOP-TIMEOUT` line (issue #227).

- **WHEN** the run's own recorded deadline passes while an agent process is still running
- **THEN** the run ends that process and everything it started, writes one machine-readable line naming the
  deadline that ended it (`LOOP-DEADLINE: the run's deadline passed at <deadlineAt> …`) beside the
  `LOOP-TIMEOUT` line the run already reads as its own timeout, and records the item `blocked` with that
  transcript instead of waiting for a clock it was never given
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: A deadline-ended child leaves a recorded stop, and the deadline when it is the binding one

A run that ends without a stop reason is replaced as `the state being replaced never recorded a stop reason`,
which is how the 2026-10-20 stretch was found hours later (issue #227).

- **WHEN** the run's deadline ended a child and the run then leaves its item loop
- **THEN** the state records a stop reason, and it names the deadline — `deadline passed at <deadlineAt>` — when
  the deadline is the condition that ends the run; when another recorded condition was reached first (the item
  cap), that condition is the stop, exactly as `state.sh next` already orders them
- **Case**: `run#childIsEndedAtTheRunsDeadline`
