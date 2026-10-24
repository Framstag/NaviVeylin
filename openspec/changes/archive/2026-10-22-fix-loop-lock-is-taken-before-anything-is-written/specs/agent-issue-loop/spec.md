# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: One run at a time holds the repository

One run at a time SHALL hold a repository: the lock that keeps one run per repository SHALL be taken before
the run writes anything, a run that finds a live lock SHALL be refused without writing state, branch or
label, and a lock whose owner is no longer running SHALL be taken over and announced.

#### Scenario: A second run is refused before it writes anything

- **WHEN** a run is started while another run holds the lock
- **THEN** the second run is refused with a message that names the lock and its owner, it writes no state, no
  branch and no label, and the live run's state is the one left in place — the run that was refused never
  replaced the record of the run that is working
- **Case**: `state#aRefusedRunLeavesTheLiveStateAlone`

#### Scenario: A lock left by a run that is gone is taken over

- **WHEN** a run finds a lock whose owner is no longer running
- **THEN** it takes the lock over, says so in one line naming the process that is gone, and starts — a run
  that was killed leaves no tombstone that refuses every run after it
- **Case**: `state#aDeadOwnersLockIsTakenOver`

## MODIFIED Requirements

### Requirement: A run's state outlives the run that replaces it

The run SHALL keep the state a new run replaces, SHALL be able to report on a kept run afterwards, and SHALL
name the run its numbers belong to rather than print a budget left computed from another run's clock, and
SHALL tell a run whose process is gone from one that is still going.

#### Scenario: The state a run replaces is kept, named by that run's start

- **WHEN** `state.sh init` is asked to start a run and a state file already exists
- **THEN** the existing file is copied, before anything is written, to `<state-dir>/runs/<run.startedAt>.json`
  — the start time the state carries itself, under a directory beside the state file unless the project
  records another one (`LOOP_RUNS_DIR`) — and the new state is the one in place, so no finished run's counters,
  verdicts or stop reason are lost to the next run
- **Case**: `state#initKeepsTheStateItReplaces`

#### Scenario: A run that never recorded a stop is announced, not refused

- **WHEN** the state being replaced carries no `run.stop.reason` — a run that was killed or crashed
- **THEN** `init` says so on stderr in one line and still starts the run, so a crashed run stays restartable
  without a flag, while a state that did record its end is replaced without the warning
- **Case**: `state#initAnnouncesARunWithoutAStop`

#### Scenario: A kept run is reported on after the next run started

- **WHEN** `report.sh --state <file>` is given a state a later run replaced
- **THEN** the report is that run's — its budget, its counters, its item table, its skips and its stop reason —
  and without `--state` the report is the run in place, exactly as before
- **Case**: `report#archivedStateIsReported`

#### Scenario: The budget line belongs to the run whose state it reads

- **WHEN** the report prints the budget left, and the state's window (`run.startedEpoch` + `run.minutes`) closed
  without a recorded stop, so the elapsed time since its start cannot be its spend
- **THEN** the line says the numbers belong to another run —
  `budget left unknown: these numbers belong to another run — its window closed at <run.deadlineAt> and it
  recorded no stop` — instead of printing a budget left below zero, and a run that recorded its stop reports its
  own spend to that stop however long ago it ended
- **Case**: `report#impossibleBudgetNamesAnotherRun`

#### Scenario: A run whose process is gone is not a run that is going

- **WHEN** the state names the process that wrote it, that process is not running, and the run recorded no
  stop — a run that was killed
- **THEN** the report prints no budget left for it, and its stop line names the process that is gone instead
  of saying the run is still going, so a killed run and a running one cannot be read alike
- **Case**: `report#aKilledRunIsNotALiveRun`
