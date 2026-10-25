## ADDED Requirements

### Requirement: A self-test case is held to a bound of its own

The self-test SHALL bound every case it starts with `LOOP_CASE_MAX_SECONDS` (default `900`), measured from the
case's own start. A case that exceeds it SHALL have its process group ended, be recorded
`FAIL <case> (exceeded <n> s)` with its own log kept, and let the suite run the cases after it, so one case that
never returns cannot hold the suite — and the gate behind it — for ever. The value SHALL be validated like the
profile's other bounds.

#### Scenario: A case that never returns is failed by its own bound

- **WHEN** a case in the pool does not return within `LOOP_CASE_MAX_SECONDS`
- **THEN** the suite ends that case's process group, prints `FAIL <case> (exceeded <n> s)` naming the bound and
  the case, keeps that case's log, runs the cases queued after it, and ends with its own summary and a non-zero
  status — instead of waiting for the case
- **Case**: `driver#aCaseThatBlocksIsEndedByItsOwnBound`

#### Scenario: A case that returns inside its bound is not bounded away

- **WHEN** a case finishes before `LOOP_CASE_MAX_SECONDS`
- **THEN** its result is reported as its own exit status decided — `PASS` or `FAIL` from the case, never the
  bound's line — and the suite's own wall clock is not lengthened by the bound
- **Case**: `driver#aCaseInsideItsBoundKeepsItsOwnVerdict`

### Requirement: A case's own output reaches the suite's log while it runs

The self-test SHALL write what a running case prints to the suite's own output as it arrives, each line prefixed
with the case's name, and SHALL still keep that output in the case's `out` file for its failure text, so a case
that has not ended names itself and what it was doing. Nothing about a case's protocol changes: the prefix is
added to the line, the case's own file is unaltered.

#### Scenario: A case that prints and then blocks is named while it blocks

- **WHEN** a case prints a line and then does not return
- **THEN** that line reaches the suite's own output, prefixed with the case's name, before the case ends — the
  suite a reader watches is not silent between the case's start and its bound
- **Case**: `driver#aRunningCasePrintsItsOwnLines`

### Requirement: A run that takes a signal takes its heartbeat with it

A run ended by `INT` or `TERM` SHALL end the heartbeat subshell its wait started before it exits, by pid, so no
process of a run that is gone keeps reading its tree: the wait loop's `$stop` file is only created on the path
where the wait returns, and the heartbeat has no other end.

#### Scenario: No process of a signalled run outlives it

- **WHEN** a run is inside its wait for a child and takes `SIGTERM`
- **THEN** the run records its interrupted item `blocked` with the reason, removes the in-progress label, unlocks
  the state, exits — and no process whose working directory lies under the run's tree is left behind
- **Case**: `run#noProcessOfASignalledRunOutlivesIt`

## MODIFIED Requirements

### Requirement: A child the run ended itself leaves no commit

The run SHALL commit an item's tree only after the child that produced it has finished by its own exit. A child
the run ended — the wall-clock cap's status, or the `LOOP-TIMEOUT` line its transcript carries — SHALL leave the
branch as it stood: the item is recorded blocked with that transcript, and the tree is kept exactly as the child
left it, because nothing is discarded without `--reset-worktree`. It holds for the rework worker a refusing review
round starts as well. The tree that child left SHALL be readable without the tree itself: before the run moves to
its next item, the changes it left — the tracked diff and each untracked file — SHALL be written to
`<item dir>/leftover-from-killed-run.patch`, and the transcript the ended child leaves SHALL carry a
`LOOP-LEFTOVERS:` line naming that file.

#### Scenario: A worker the cap ends leaves the branch at the base

- **WHEN** the wall-clock cap ends an item's worker and that worker has left changes in the change root
- **THEN** the run commits none of them — the change root's `HEAD` is still the base commit — the item is recorded
  `blocked` with its `LOOP-TIMEOUT` transcript, the tree still carries what the child left (kept, not discarded),
  and the run's own line says the commit was skipped
- **Case**: `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: The cap ends the rework worker of a refusing review round

- **WHEN** a review round refuses the change and the wall-clock cap ends the rework worker that round starts, after
  that worker has written into the change root
- **THEN** the run commits none of it — the branch carries only what the item's own worker had already earned — the
  item is recorded `blocked` with the round's `LOOP-TIMEOUT` transcript, the tree still carries what that worker
  left, and the blocked reason names the tree it did not commit
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: The changes a cap-ended child left are kept as a patch

- **WHEN** the wall-clock cap ends an item's worker that has left modifications and an untracked file in the tree
- **THEN** `<item dir>/leftover-from-killed-run.patch` exists, carries the worker's modifications (staged and
  unstaged, against `HEAD`) and that untracked file's content, and the child's transcript carries a
  `LOOP-LEFTOVERS:` line naming it — so the work is readable after `--reset-worktree` discards the tree
- **Case**: `run#treeOfACapEndedChildIsKeptAsAPatch`

### Requirement: A start the worktree refuses mid-run names the item whose work it holds

A start refused because the worktree is not clean SHALL be attributed before the run ends on it: the item the
worktree's branch marker names SHALL be recorded `blocked` with the leftovers and escalated, the stop reason SHALL
name that item and the files it found, and the candidate that could not start SHALL NOT be recorded. The leftovers
it found SHALL be written to that item's evidence directory as `<item dir>/leftover-from-killed-run.patch` before
the run ends, and the stop reason SHALL name that file beside the files.

#### Scenario: A refused start is attributed to the item whose work the tree holds

- **WHEN** an item's start is refused mid-run because the worktree is not clean
- **THEN** the item the worktree's branch marker names is recorded blocked with the leftovers it left and escalated,
  the stop reason names that item and the files — not the refusal's first line — and the item that could not start
  keeps no record of its own
- **Case**: `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`

#### Scenario: A refused start keeps the leftovers it found

- **WHEN** an item's start is refused because the worktree carries another item's uncommitted work
- **THEN** that work is written to `<that item's dir>/leftover-from-killed-run.patch` before the run ends and the
  stop reason names that path, so the only repair is no longer `--reset-worktree`
- **Case**: `run#aRefusedStartKeepsTheLeftoversItFound`
