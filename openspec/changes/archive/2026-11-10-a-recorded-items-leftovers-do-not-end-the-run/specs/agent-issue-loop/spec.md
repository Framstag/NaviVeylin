## REMOVED Requirements

### Requirement: A start the worktree refuses mid-run names the item whose work it holds

**Reason**: the refusal's default is reversed for the case the measurement convicts — an item the same run has
recorded (5 runs, 1 699 minutes unspent; 2026-11-10T08:19:24Z lost 262 of its 480 minutes that way). Attribution and
the named stop remain, for the narrower path where the owner is not one this run recorded. The successor requirement
is "A refused start is attributed, and a recorded item's work is discarded" below.

## ADDED Requirements
### Requirement: A refused start is attributed, and a recorded item's work is discarded

A start refused because the worktree is not clean SHALL NOT end the run when the item the branch marker names was recorded by this run: its leftovers SHALL be written to its own `<item dir>/leftover-from-killed-run.patch`, the tree discarded, and the start retried. For an owner this run did not record, or one whose work cannot be written, the refusal SHALL keep the leftovers, escalate that item, and stop naming it and the files.

#### Scenario: A recorded item's leftovers are kept and the run goes on

- **WHEN** an item's start is refused mid-run because the worktree is not clean, and the item the branch marker names was recorded by this run — as the run of 2026-11-10T08:19:24Z recorded #807 3 s before it refused #100's start and then stopped with 262 of its 480 minutes unspent
- **THEN** that item's remaining work is written to `<its dir>/leftover-from-killed-run.patch`, the line the run prints names the record it relies on, the tree is discarded, the refused start is retried, the next item is worked, and the item keeps the single record and the escalation its own arm wrote
- **Case**: `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`

#### Scenario: A refused start over unrecorded work still ends the run, naming it

- **WHEN** an item's start is refused mid-run and the item the branch marker names is not one this run has recorded, or its work cannot be written to a patch
- **THEN** the leftovers are kept, the item is recorded blocked with them and escalated, the stop reason names that item and the files — not the refusal's first line — and the item that could not start keeps no record of its own
- **AND** no case exercises this path: the branch marker always names the item the run is working, and an unwritable item directory breaks the run earlier. The gap is stated beside the mutation set, which pins the discard (`271.1`, `271.2`) instead

## MODIFIED Requirements

### Requirement: The dirt a run ended itself does not stop it

A start refused because the worktree is not clean SHALL be retried instead of ending the run when the branch marker names an item **this run recorded** whose work this run wrote to `<item dir>/leftover-from-killed-run.patch`: that tree SHALL be discarded, the start retried once, and the run SHALL print and record which item's work it discarded and where the record is. A retry refused again SHALL stop the run with that refusal's own reason, and SHALL NOT replace the record the discard relied on.

#### Scenario: The item after the run's own ended child starts

- **WHEN** a run ends one of its items at the child cap, writes what that child left to the item's patch, records the
  item `blocked`, and the next item's start is refused because the worktree is still not clean
- **THEN** the run names the item whose work it discards and the patch that holds it, discards the tree, starts the
  next item on it, and ends with a tree the next start accepts — the stop it records is the count of items it spent,
  not the dirt
- **Case**: `run#theStartAfterTheRunsOwnEndedChildIsNotRefused`

#### Scenario: An item this run recorded blocked leaves its tree dirty

- **WHEN** a run records an item `blocked` — a refused `end-item` leaves its work staged, say — writes that work to
  the item's patch, and the next item's start is then refused because the worktree is not clean
- **THEN** the run discards that tree, records the discard as a step naming the patch, starts the next item, and the
  blocked item keeps the single record and the escalation its own arm wrote
- **Case**: `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`

#### Scenario: The dirt another item left still stops the run

- **WHEN** an item's start is refused because the worktree carries the uncommitted work of an item this run did **not**
  record, so no record of this run's holds it
- **THEN** nothing is discarded, the item the branch marker names is recorded blocked with the leftovers and
  escalated, the stop reason names that item and the files it found, and the item that could not start keeps no
  record of its own
- **Case**: none — the branch marker always names the item the run is working, so no case can construct an owner this
  run has not recorded; the mutation set records the gap instead (`271.1`, `271.2`)

#### Scenario: A retry refused for a reason of its own

- **WHEN** the run discards the work its own ended child left and retries the start, and the retry is refused for
  something other than the dirt — the branch it would start on carries commits the base does not, say
- **THEN** the stop reason is that refusal's own, the dirt the run removed is not named, and the patch the discard
  relied on still holds the work it held
- **Case**: `run#aRetriedStartStopsWithItsOwnReason`
