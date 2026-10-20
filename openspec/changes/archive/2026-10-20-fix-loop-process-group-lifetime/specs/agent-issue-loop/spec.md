# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A suite ended by a signal ends the case it was in, and its processes

The loop's self-test SHALL run each case in a process group of its own and SHALL end that whole group when a
signal ends the suite, so no case subshell and no process a case started outlives it. The signal it sends SHALL
be the one that lets a member run its own cleanup, and the suite SHALL NOT report a run it did not finish as a
pass. `SIGKILL` cannot be trapped; the processes it leaves SHALL be the leftovers the clean command reports.

#### Scenario: A suite interrupted in the middle of a case ends that case and what it started

- **WHEN** a case is running (a case that starts a child and waits is what that looks like) and the suite
  receives `TERM` — and the same holds for `INT` and `HUP`
- **THEN** the suite is gone at once rather than when its case happens to finish, its exit status is not 0, and
  neither the case's own process nor the child it started is running afterwards
- **Case**: `clean#interruptedSuiteEndsItsProcessGroup`

### Requirement: An interrupted run ends the child it started

A run that takes a signal SHALL end the child it was waiting for, together with everything that child started,
before it records the item's outcome — the child is put in a process group of its own so the wall-clock cap can
bound it, and the same group SHALL be ended by the run's own traps.

#### Scenario: A run ended by TERM leaves no child working

- **WHEN** a run is waiting for a child that is in the middle of its phase (a child blocked on a fifo is what
  that looks like) and the run receives `TERM`
- **THEN** the run exits with the signal's status rather than a finished run's, and the child it started is no
  longer running
- **Case**: `run#interruptedRunEndsTheChildItStarted`

### Requirement: The leftover report names the processes, not only the trees

The clean command SHALL report the processes whose working directory lies inside a scratch tree it is about to
remove, and SHALL end exactly those with `--apply`, before the tree goes. It SHALL NOT signal a process whose
working directory is inside a tree it keeps — that one belongs to a live suite — and the paths and bytes it
already reports SHALL keep their shape, with the process count added only when there is one.

#### Scenario: The report names a process and ends nothing

- **WHEN** `loop-clean` is run without `--apply` on a scratch root holding a stale tree with a process standing
  in it and a fresh tree with a process of its own
- **THEN** it names the stale tree's process beside the tree it would remove, names no process in the fresh tree,
  ends neither process and removes neither tree
- **Case**: `clean#reportsAndEndsAProcessLeftInAScratchTree`

#### Scenario: With `--apply` it ends that process and leaves a live suite's alone

- **WHEN** `loop-clean --apply` is run on the same scratch root
- **THEN** the process standing in the stale tree is gone and so is the tree, the process in the fresh tree and
  the fresh tree itself are untouched, and the summary counts the one process it ended
- **Case**: `clean#reportsAndEndsAProcessLeftInAScratchTree`
