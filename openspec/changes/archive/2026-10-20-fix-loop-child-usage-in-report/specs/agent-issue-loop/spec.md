# agent-issue-loop — deltas

## MODIFIED Requirements

### Requirement: One command starts a budgeted run, with no dialogue

A run SHALL be started by one command whose budget is supplied as arguments and recorded, and SHALL
require no interactive answer, so that the same run can be repeated and audited.

#### Scenario: The budget is arguments, not a prompt

- **WHEN** a run is started
- **THEN** the item cap, the time budget, whether a review is taken and whether closure is allowed are
  arguments whose effective values are recorded and printed in the run's header

#### Scenario: The run stops on its own authority

- **WHEN** the item cap is reached, the time budget is spent, the queue is empty, two items in a row were
  blocked, or the remaining time cannot pay for another item
- **THEN** the run stops without needing a further decision

#### Scenario: The floor the run refuses by is what its own items cost

A constant floor could not tell a 100-minute item (#126, 2026-10-19) from a 12-minute one, so it admitted the
first with 45 minutes left and refused 28 minutes of cheap work after it (issue #206).

- **WHEN** the remaining time is compared against the floor, and the run has finished at least one item whose
  wall time it recorded (`done --wall SECONDS`)
- **THEN** the floor is derived from those recorded wall times — their median minutes times the 1.5 spread the
  measured costs show, rounded up to the whole minute — and is never below the configured
  `LOOP_ITEM_FLOOR_MINUTES`, and the refusal names the floor, the median, how many finished items it came from
  and the configured minimum, e.g. `only 28 min left, below the 90 min floor for the next item (median 60 min
  over 3 finished items, configured minimum 45)`
- **Case**: `state#floorIsDerivedFromTheFinishedItemsWallTimes`

#### Scenario: A run with no measurement keeps the configured minimum

- **WHEN** no finished item of the run records a wall time — the run has just started, or every item so far
  was recorded without `--wall`
- **THEN** the floor is the configured `LOOP_ITEM_FLOOR_MINUTES`, and the refusal names that value together
  with the reason no measurement was used, rather than implying a measurement it does not have
- **Case**: `state#floorWithNoHistoryIsTheConfiguredMinimum`

#### Scenario: A child that outlasts the configured wall-clock is ended and recorded

The budget is consulted between items, so a child process is the one place a spent budget could not be
noticed: the run waited for it with no limit, and a gate that never returns could spend the whole run inside
one wait.

- **WHEN** an agent process runs longer than the configured child wall-clock
  (`LOOP_CHILD_MAX_SECONDS`, default 2700 s = 45 min)
- **THEN** the run ends that process and every process it started, appends one machine-readable line to the
  report it was writing (`LOOP-TIMEOUT: the child exceeded <n> s …`), and returns a status that is not the
  child's own, so the item is recorded `blocked` with that transcript and the run goes on to its next stop
  condition instead of spending its clock in one wait
- **Case**: `run#childOutlastingTheWallClockIsBlocked`

#### Scenario: A run is recorded

- **WHEN** a run ends
- **THEN** a report exists naming each item, its verdict, its wall time, its spend, its review rounds and what
  it closed, so the run justifies itself with numbers — every item's row carries the wall time the run recorded
  for it, the tools its children called with their count, the tokens they used with their cost and the largest
  context they reached, and, when the item's result is not a closure, the reason the item recorded, and the
  columns the report already had keep their name and their order
- **Case**: `report#wallTimeAndReasonInTheTable`

#### Scenario: An item's spend is read from the child's own stream

The agent reports its tool calls, its tokens and its cost in its event stream, and the loop used to start it in
a mode that discards the stream, so the one measurement of spend that exists was never read (issue #211).

- **WHEN** a child's process produced an event stream and the item is recorded with it
- **THEN** the item's metrics hold the count of each tool its children called, the tokens they used summed over
  their responses (each response counted once, at its last usage record, not once per streamed update), what
  those tokens cost, and the largest context any of them reached — the biggest input-plus-cache-read of a
  response, or a compaction's `tokensBefore` when the context was compacted — and the row the report prints
  shows them in the `tools`, `tokens` and `context` columns
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: Nothing reported is printed as zero

A provider that streams no usage leaves zeros behind, and a zero in a column of numbers reads as a measurement
that was made.

- **WHEN** an item's stream holds no usage record, holds only zeroed ones, or no stream was written at all —
  a project whose agent command prints plain text is the third case
- **THEN** the report prints `-` in the `tools`, `tokens` and `context` columns rather than `0`, the item is
  worked and recorded exactly as before, and nothing about the run depends on the stream existing
- **Case**: `report#zeroUsagePrintsDashNotZero`, `run#plainTextChildStillReports`

#### Scenario: The report names the actions the caller owes

The report is read instead of the tracker, so a list of ids is not an answer: a decision without its question,
a branch without its owner, a gate without its log and a kept worktree without its path send the caller back
to `gh` to reconstruct what the run already knew (issue #208).

- **WHEN** a run ends holding a decision it escalated, a branch that carries commits the run's base does not
  have, a kept worktree, an item whose gate never completed, or a citation the pre-screen refused
- **THEN** the report prints one named action for each: the decision with the question the run wrote beside
  it — or, where the state does not hold that text, where the question is (`gh issue view <n>`) rather than an
  invented one — the branch with what it carries and the item that owns it, the kept worktree with its path,
  the item's transcript for the gate that never completed, and the repair command for the refused citation
- **Case**: `report#actionsNameDecisionAndBranch`

#### Scenario: An action list is empty only when the state says so

- **WHEN** no decision, no branch carrying commits, no kept worktree, no incomplete gate and no refused
  citation is recorded — or when the state names any one of them
- **THEN** the report prints the action list heading with `none` and what that covers in the first case, and
  never prints `none` in the second, so an empty list cannot hide a branch that carries work
- **Case**: `report#emptyRunSaysNone`

### Requirement: A run reports the wait it cannot get from the child

A run SHALL print what it is doing while a child process is in flight, from what the run can observe itself,
because a child's answer arrives when it exits. It SHALL name the wait when a child starts, report the child's
progress on a configured cadence, and report the end with the child's exit status, so a working child and a hung
one stay distinguishable.

#### Scenario: The wait is announced when the child starts

- **WHEN** the run starts a child process — the worker, a review round or the gate
- **THEN** it prints one line naming the phase and the transcript's path before the child can have written
  anything, so the console never shows a start of the item followed by silence whose cause is unknown
- **Case**: `run#childReportsWhileItWaits`

#### Scenario: A running child is reported on a cadence, with numbers read from the tree

- **WHEN** the child has not yet ended and the configured interval (`LOOP_PROGRESS_SECONDS`, default 30) has
  elapsed
- **THEN** the run prints a line carrying the time already spent, the transcript's size, the worktree's
  changed-file count and its commit count since the run's base
- **AND** every one of those numbers is read from the file system and from git in the tree the child writes,
  not from the child's own claim, so the line can be printed while the child is unable to say anything
- **AND** a configured interval that is empty, non-numeric or zero falls back to the default rather than
  stopping the reporting
- **Case**: `run#childReportsWhileItWaits`

#### Scenario: The child's end is reported with its own exit status

- **WHEN** the child process ends, however it ends
- **THEN** the run prints one line naming the elapsed time, the exit status and the final transcript size, so
  the phase that follows can be read against what the previous one actually did
- **Case**: `run#childReportsWhileItWaits`
