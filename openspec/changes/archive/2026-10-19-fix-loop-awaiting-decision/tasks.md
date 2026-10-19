# fix-loop-awaiting-decision — tasks

- [x] 1.1 A case over the `decisions` fixture that asserts an item awaiting a decision is not a candidate.
      Red on HEAD: verdict `eligible`.
- [x] 1.2 The pre-screen excludes such an item with the profile's decision verdict, and `state.sh queue`
      carries the `alreadyEscalated` flag.
- [x] 1.3 `run.sh` skips an exclusion that is already escalated, so no second comment is posted.
- [x] 2.1 `alreadyEscalated` follows the label, not the hold; a fourth fixture item (#44) holds without a
      label and is escalated once (mutation 20.1).
- [x] 2.2 The clause runs after the previous-attempt-branch rule, which keeps the branch as the evidence;
      case `prescreen#escalatedItemKeepsItsBranchAsEvidence`, mutation 20.2.
- [x] 3.1 Mutations 17.1, 17.2, 20.1, 20.2: four red, four green after the restore.
- [x] 3.2 Regression pass: `loop-selftest` 76/76, `report#*` and `run#mergesAFinishedAttemptAndClosesTheItem`
      still green.
