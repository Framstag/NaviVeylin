# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A start resumes the work a bound-ended child left

The start of an item SHALL apply `<item dir>/leftover-from-killed-run.patch` — what a child an earlier run ended at a bound left — onto the branch it cut from the base, before a worker exists, and SHALL commit none of it: the item is worked on that work and the branch's `HEAD` stays the base commit. Where neither a plain nor a 3-way apply takes it, the tree SHALL be put back on the base and the non-apply recorded in the item's note, so it stays workable and nothing is discarded unrecorded.

#### Scenario: The work a bound-ended child left is applied before the worker starts

- **WHEN** an earlier run's bound ended an item's child, leaving that child's work in the item's
  `leftover-from-killed-run.patch`, the branch at the base, and the next run starts the item
- **THEN** the start applies that patch onto the branch it cut from the base, before a worker exists — plain, and 3-way
  when the base moved under the diff — so the tree the worker is handed carries the earlier work, the branch is the
  item's and its `HEAD` is still the base commit, and the item's record names the resume
- **Case**: `run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart`

#### Scenario: A kept patch the base does not take is recorded, not discarded

- **WHEN** the patch neither applies plainly nor 3-way — the measured shapes are `git apply --check` exit 1 on
  `items/791` and `items/729` and exit 128 on `items/16`
- **THEN** the tree is put back on the base, including whatever the 3-way attempt had already staged, the item is
  worked from the base, and the item's note names the patch that did not apply, so a reader learns why the attempt
  began from zero and the work is not discarded unrecorded
- **Case**: `run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase`
