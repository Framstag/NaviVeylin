# backlog-sensors — deltas

## ADDED Requirements

### Requirement: A mechanical sweep closes only what its own rule states

A rule that is a function of the tracker's own fields MAY be applied by the scheduled worker instead of by a
person, and SHALL then close only what its rule names, report every group it cannot decide, and print the
candidates it refuses to act on.

#### Scenario: A duplicate group closes the younger item, and then points the survivor at it

- **WHEN** the sweep finds several waiting items carrying one fingerprint marker, and the younger one carries
  the label that authorises automated work
- **THEN** the younger item is closed through `close.sh` with a reason naming the surviving item and the
  surviving item's number as its evidence, **and then** the survivor receives a pointer comment naming the
  closed item, and nothing else is written
- **Case**: `duplicates#youngerDuplicateIsClosedWithAPointer`

#### Scenario: The older item survives

- **WHEN** the sweep picks the survivor of a duplicate group
- **THEN** it is the item with the lowest issue number, because that number is immutable while labels and
  body length are not, and the closure names it
- **Case**: `duplicates#olderItemSurvives`

#### Scenario: A duplicate that may not be closed is made visible once

- **WHEN** the younger item of a group does not carry the authorisation label, so `close.sh` would refuse
- **THEN** the sweep posts the same pointer comment on it and leaves it open, and the run's output names it
  — the authorisation is never widened to make the rule fire; and a duplicate that already carries the
  pointer's marker is not commented on again
- **Case**: `duplicates#unauthorisedDuplicateIsCommentedNotClosed`, `duplicates#aMarkedDuplicateIsNotCommentedTwice`

#### Scenario: An identifier collision is reported, never repaired

- **WHEN** two open items carry the same `nvid` marker
- **THEN** both issue numbers appear in the sweep's output and in the workflow's summary, and no write
  happens — which item legitimately carries the identifier is recorded in the migration table, not derivable
  from the tracker
- **Case**: `duplicates#collisionIsReportedAndNothingIsWritten`

#### Scenario: A candidate is printed and not acted on

- **WHEN** an item's number appears in a commit that is an ancestor of `main`
- **THEN** the sweep prints it as a candidate and closes nothing, because a cherry-picked commit names the
  same item on a branch, and a message may mention an item without delivering it
- **Case**: `duplicates#landedCandidateIsPrintedNotClosed`

#### Scenario: A clean tracker is a pass, not a failure

- **WHEN** no duplicate, no collision and no candidate exist
- **THEN** the sweep prints `nothing to do` and exits 0, and a dry run writes nothing in every case
- **Case**: `duplicates#cleanTrackerExitsZeroWithoutAWrite`
