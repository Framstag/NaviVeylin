# Design — a recorded item's leftovers

## D1 Which leftovers the run may discard

Three candidates, from the narrowest to the widest:

1. **Keep today's rule** (`own_leftovers`: only a child the run ended at a bound). Rejected: it is the rule the
   measurement convicts — 5 runs, 1 699 minutes unspent, the largest single cause of wasted budget in the archive
   after the blocked-pair stop, and every one of those five items had a state entry and a patch already.
2. **Discard any dirt once it has been written to a patch.** Rejected: `keep_leftovers` writes whatever the tree
   holds, including the work of an item a *previous* run was in the middle of and this run has not yet looked at.
   The record would exist but nothing would say the run had the right to take that item off its owner's hands; the
   pre-flight refusal (`run#dirtyKeptWorktreeIsARecordedRefusal`) exists for exactly that and keeps it.
3. **Discard the dirt of an item this run recorded, once its work is in its patch.** Chosen. The predicate is one
   reading of the run's own state (`.items[]`), the record is the patch, and the two together are what issue #664
   already accepted for the cap arm — this is that repair one condition wider, not a new rule.

## D2 What happens to the item's own escalation

- **Escalate again from the refusal.** Rejected: the item's block arm has already written `agent/needs-decision`
  and its question; a second comment and a second label write would make one item look like two decisions in the
  report's `decisions the owner owes` list, which is the defect `state.sh done` already refuses for one record.
- **Escalate nothing.** Chosen: the item's own record and escalation stand, and the run prints which record it is
  relying on, so a reader of the console learns why the tree could be cleaned.

## D3 The cases: replace, and say what is not covered

`run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` and `run#aRefusedStartKeepsTheLeftoversItFound` both drove the
refusal with a `pre-commit` hook, i.e. with an item this run had just recorded — the case D1 now sends down the
discard path. Rewriting them into two versions of the same assertion would be a second record of one fact, so
they become the one case `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`.

What that loses: the refusal's own attribution (escalate, record, stop naming item and files) is reached only by an
owner this run has not recorded, or by a patch that cannot be written. No case in the suite produces either — the
branch marker always names the item the run is working, and an unwritable item directory breaks the run earlier.
The three mutations that pinned those lines (`53.1`–`53.3`) and the one that pinned the refused start's patch
(`62.6`) would therefore report `not noticed`; they are retired with that reason written beside them, and the two
records that replace them (`271.1`, `271.2`) pin the direction the run now takes. A reviewer who wants the failure
path proven needs a case that can make an item directory unwritable mid-run — a fixture change of its own.

One shipped invariant loses its last writer as well, and this is the round-1 reviewer's finding rather than mine:
`tools/loop/state.sh`'s second-account join (a `done` for an item already recorded joins that item's note instead of
appending a second record) had this refusal as its only caller. It stays as `done`'s guard for any future caller, and
it is covered by no case now — which is stated here rather than left for a reader of the mutation set to discover.

## Verification

- `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn` — red on HEAD (the run records one item and stops on the
  tree), green after: two items worked, the patch written before the discard, the stop an item cap.
- `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` — the cap-arm case, unchanged, keeps the `own_leftovers`
  branch honest through the widened line (`13.10`).
- `271.1`, `271.2` — red on their own case, restored, green.
- `loop-selftest --files "tools/loop/run.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh"`,
  `loop-mutations --check`, and the doc checks; no Gradle build (no compiled file in the delta).
