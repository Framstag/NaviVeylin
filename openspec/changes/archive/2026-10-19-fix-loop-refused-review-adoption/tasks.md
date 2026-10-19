# fix-loop-refused-review-adoption — tasks

- [x] 1.1 A case in which a full run's reviewer FAILs twice, asserting the verdict is a commit on the item
      branch and not a machine-local file. Red on HEAD.
- [x] 1.2 `worktree.sh record-review-failure` writes `evidence/review-failed-round-N.md` inside the archived
      change and commits it with the run's identity.
- [x] 2.1 A case in which an otherwise adoptable branch (one archived change, green XML) carries the verdict
      and `adopt-item` refuses it, naming the round. Red on HEAD.
- [x] 2.2 The refusal says how the refusal is cleared (corrections and a hand merge, or deleting the file
      deliberately).
- [x] 3.1 Mutations 18.1 (the refusal) and 18.2 (the recording): both red-then-green.
- [x] 3.2 #176's positive path still green: `run#mergesAFinishedAttemptAndClosesTheItem`.
- [x] 4.1 Review follow-up: the pre-screen keeps the branch as the evidence for an escalated item
      (`prescreen#escalatedItemKeepsItsBranchAsEvidence`, mutation 20.2).
