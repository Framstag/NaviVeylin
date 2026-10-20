# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A run's state outlives the run that replaces it

The run SHALL keep the state a new run replaces, SHALL be able to report on a kept run afterwards, and SHALL
name the run its numbers belong to rather than print a budget left computed from another run's clock.

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
