# Agent issue loop

## MODIFIED Requirements

### Requirement: A suite ended by a signal ends the case it was in, and its processes

The loop's self-test SHALL run each case in a process group of its own and SHALL, on every exit path, leave no process of the cases it ran standing in its own scratch root: a signal SHALL end the whole group of the case it was in, and a suite that ends normally SHALL sweep that root before removing it. The signal SHALL be the one that lets a member run its own cleanup, the suite SHALL NOT report a run it did not finish as a pass, and `SIGKILL`'s leftovers SHALL be the clean command's.

#### Scenario: A suite interrupted in the middle of a case ends that case and what it started

- **WHEN** a case is running (a case that starts a child and waits is what that looks like) and the suite
  receives `TERM` — and the same holds for `INT` and `HUP`
- **THEN** the suite is gone at once rather than when its case happens to finish, its exit status is not 0, and
  neither the case's own process nor the child it started is running afterwards
- **Case**: `clean#interruptedSuiteEndsItsProcessGroup`

#### Scenario: A suite that ends normally ends the process its case left

- **WHEN** a case starts a process in a process group of its own at a working directory inside the suite's own
  scratch root and returns while it polls, and the suite then reaches its normal end — no signal is sent
- **THEN** that process is not running once the suite has exited, and the suite's exit status is still 0
- **Case**: `clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft`
