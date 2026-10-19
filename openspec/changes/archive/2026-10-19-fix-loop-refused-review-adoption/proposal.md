# fix-loop-refused-review-adoption

## Why

The run of 2026-10-19T07:12:47Z blocked #117 and #119 after their second review round (`VERDICT: FAIL`,
both on `## 6. Evidence pointers`) and escalated them. Each branch adds exactly one archived change and
carries a green `TEST-*.xml`, so `carried_change` and `carried_verification` both accepted them: the next
run's pre-screen recorded `needs-decision` with the branch as evidence, `run.sh` handed it to
`adopt_finished_attempt`, and `worktree.sh adopt-item` merged and closed the item — a decision the reviewer
had just refused (#185).

## What changed

- A review that ends `FAIL` is recorded **on the item branch**: `worktree.sh record-review-failure
  <issue> <change> <round>` writes `openspec/changes/archive/<change>/evidence/review-failed-round-N.md`
  and commits it with the run's own identity. Evidence already travels with a change (invariant 1); the
  verdict was the half that did not.
- `adopt-item` refuses a branch whose change carries that file, names the round, and says how to clear the
  refusal: apply the corrections and merge by hand, or delete the verdict file deliberately.
- The review of this change found that the #183 clause had made this same adoption path unreachable; the
  ordering fix keeps the branch as the evidence (see `2026-10-19-fix-loop-awaiting-decision`).

## Spec

`openspec/specs/agent-issue-loop/spec.md`, new scenario "A refused review travels with the branch", applied
in the same commit (910a016).

## Cases and evidence

`run#recordsAFailedReviewOnTheBranch` (a full run with a reviewer that FAILs twice leaves the marker as a
commit on the item branch) and `worktree#refusesABranchWhoseReviewFailed` (a branch carrying one archived
change and a green XML, otherwise adoptable, is refused with `round 2` named); mutations 18.1, 18.2.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/run.sh`, `tools/loop/worktree.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`,
`openspec/specs/agent-issue-loop/spec.md`.

## Open

An escalated branch is unadoptable until a person acts. That is the intent, and the refusal names the two
ways out; it is recorded here because a future reader will ask how such a branch is ever merged.
