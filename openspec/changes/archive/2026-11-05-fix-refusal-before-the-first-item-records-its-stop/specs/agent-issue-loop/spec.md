## MODIFIED Requirements

### Requirement: A pile of open decisions stops the run

The run SHALL refuse to start when more candidate items awaiting a person's decision are open than the configured
cap allows, and SHALL report them instead of producing more work. An item is a candidate only when it carries
both the decision label and the loop's opt-in, so a decision parked by removing `agent/eligible` SHALL NOT count
(#745).

#### Scenario: The cap is reached

- **WHEN** the number of candidate items awaiting a decision reaches the cap
- **THEN** the run refuses to start, prints those items and states the count it compared, so nothing new is
  produced while decisions accumulate, and records that refusal as the run's own stop reason — naming both counts
  it compared and the items it printed — so a later report prints the refusal instead of the sentence a killed
  process leaves (#787)
- **Case**: `run#decisionCapRefusesToStart`, `run#decisionCapCountsOnlyCandidates`,
  `run#theDecisionCapRecordsItsStopReason`

#### Scenario: An item needing a decision is escalated, not abandoned

- **WHEN** an item turns out to need a decision while it is being worked
- **THEN** the work already done is preserved and named on the item — the root cause, the failing case and
  the branch — and the item is marked as awaiting a decision, so the work is not thrown away
- **Case**: `run#needsDecisionIsEscalatedWithTheWork`

#### Scenario: The report names the decisions the run itself wrote

- **WHEN** a run ends after escalating an item — a skip whose verdict means a decision, a branch a previous
  attempt left, an item that already awaited a decision, or a review round that failed
- **THEN** the report's list of decisions the owner owes names that item, read from the label the run's
  escalation wrote and recorded with the item rather than from the verdict the worker chose, and names no
  item this run did not escalate — so the run that fills the cap is the run whose report shows the cap
  coming
- **Case**: `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`

### Requirement: The run survives interruption

The run's state SHALL have exactly one writer, SHALL live where it cannot be committed, and SHALL be
sufficient to continue after an interrupt without repeating work already done.

#### Scenario: State is written only between agent processes

- **WHEN** an agent process is running
- **THEN** no state is written, so a bookkeeping edit cannot be attributed to that process's work
- **Case**: `labels#writesOnlyBetweenChildren`

#### Scenario: An interrupted item leaves no item looking in progress

- **WHEN** the run is interrupted while an item is in flight
- **THEN** the in-progress marking is removed, the item is recorded as blocked, and the next run can take
  it up again
- **Case**: `labels#trapRemovesInProgressOnSignal`

#### Scenario: A kept worktree a previous run left dirty is attributed and refused

- **WHEN** a run starts, no run holds the run lock, and the worktree it keeps from a previous run is not clean
- **THEN** the run names the files and the item whose branch the tree's marker names, records the refusal
  as a blocked item under it, escalates it with the branch as evidence and prints its report — before the
  pre-screen is paid for — and it leaves the tree exactly as it found it, unless the run asked for
  `--reset-worktree`, which names what it discards before discarding it and leaves the commits on the
  previous branches alone
- **Case**: `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeDiscardsWhatItNames`, `state#secondRunRefusesWithExit5`

#### Scenario: A refused start is the run's own recorded end

- **WHEN** a run ends at one of its refusals before the first item — a kept worktree it may not clean, the repair
  `--reset-worktree` that cannot clean it, or the decision cap — and that refusal never reached an item it works
- **THEN** the refusal is written as the run's stop reason, naming what the refusal printed, so a report read after
  that run's process is gone prints the refusal and not "it was killed and recorded no stop" (#787)
- **Case**: `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeNamesTheDirtItCannotDiscard`,
  `run#theDecisionCapRecordsItsStopReason`

#### Scenario: A skipped item leaves no item looking in progress

- **WHEN** an item ends with a skip verdict
- **THEN** the in-progress marking is removed with it, so the tracker never shows an item as being worked
  while no run holds it
- **Case**: `run#aSkipClearsTheInProgressMark`

#### Scenario: A block's reason is not left to the run's state

- **WHEN** the run blocks an item and writes the reason into its own state
- **THEN** the reason is also written on the item itself, between agent processes, because the state is
  replaced wholesale by the next run — so the decision list, the cap that counts it and the next attempt all
  read a block the state no longer holds
- **Case**: `run#blockedItemIsEscalatedWithItsReason`

#### Scenario: State is not committed

- **WHEN** the run ends
- **THEN** its state file is not part of the repository's history
- **Case**: `state#initAndShowRoundTrips`
