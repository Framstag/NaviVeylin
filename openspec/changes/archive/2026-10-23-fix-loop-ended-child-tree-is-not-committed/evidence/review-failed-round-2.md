The reviewer refused this change in review round 2.

Recorded by hand for issue #271 at 2026-10-23T20:43:27Z, on the item branch, because a branch is what a later run
can see: its archived change and its green verification say nothing about this verdict, and adopting it would close
the item on a decision the reviewer had just refused. The full report is in the loop's item directory
(`.pi/issue-loop/items/271/reviewer-report-2.md`, beside the graded diff `review-2-rebased.diff`); this pass answers
on the branch the round that `review-round-2-requested.md` announced, because the run that announced it was ended by
its own cap.

The refusal is one blocking finding, and it is the one the rebase created: the two cases this change adds carry no
`# files:` declaration, which the base's own check `driver#everyCaseDeclaresTheFilesItCanReach`
(`tools/loop-cases.sh`, merged with #276) refuses, so the rebased tree is not green and the carried
`LOOP-GATE: tests=339 failures=0` cannot attest it. The rest of the report is notes: pointers the rebase moved, two
sentences in the archived task record that no artefact carries, one inexact guideline clause, and one dead variable.

`LOOP-BLOCKING: 1`, `VERDICT: FAIL`, `LOOP-CORRECTIONS-ONLY: yes` — every finding is subtractive or metadata-only,
so one worker pass plus a fresh gate answers the round.
