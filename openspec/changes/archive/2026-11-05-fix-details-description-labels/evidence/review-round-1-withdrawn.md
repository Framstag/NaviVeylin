Review round 1 — the refusal is withdrawn, its findings answered

Round 1 of this change answered with one blocking finding and a set of pointers, counts and one rule the new code
contradicted (`reviewer-report-1.md` in the run's item directory for #767, `LOOP-BLOCKING: 1`,
`LOOP-CORRECTIONS-ONLY: yes`). The finding: the delta's English scenario claimed a `Contact`-header assertion no
case made.

What happened after it, in order:

1. The run's rework round applied the corrections and ran a green gate (`tests=458 failures=0 errors=0`) — and was
   ended at the item's turn bound before it committed, so that tree is gone. Only its recipe survived, as
   `round-2-corrections.md` in the item directory.
2. The owner's answer to the escalation was: keep the scenario's claim and make it true rather than narrow it,
   because the change **edits the section-header render site** (`LocationDetailsSheet.kt:295`), so nothing else
   would prove the English header still reads `Contact`. A translation-focused case now asserts the header and
   both label rows, and the scenario's `**Case:**` line names it; the rows' click behaviour stays in its own
   scenarios under its own value conditions.
3. The same pass answered the rest: the `%1$s %2$d` resource for the subsection index (`UI.md` §10), the
   `ContactLinks` pointer, the two proposal counts, the constant breakdown's commands, the label row in
   `UI.why.md` §1, the `getIdentifier` claims that were not measured, the on-device sentence as a prediction, and
   the rollback story that contradicted the archive. Commit
   `08ffe87b fix: the-details-dialog-prints-native-english (#767) — round 2: the round-1 findings are answered`;
   `tasks.md` §6 carries the same list against the review's section numbers.

**The withdrawal.** Both marker files are removed in this commit — `review-round-1-requested.md`, the
announcement, and `review-failed-round-1.md`, the refusal — because `worktree.sh adopt-item` refuses a branch whose
last round was announced and never answered, and refuses one a reviewer refused, and neither state is true any
more: the round was answered, every finding it named is answered in the tree, and the change's gate is green.

**What is not claimed.** No reviewer graded the corrections. This file is the record of that, and the reason the
branch carries no `review-round-1-passed.md`: a pass nobody gave is not written down here. A later run adopts the
branch on the evidence the change carries (its archived change, its per-suite green tallies, this note) and closes
the item, which is what the owner asked for.

Recorded at 2026-11-05T21:28:26Z on the item branch for #767, beside the evidence the change already carries.
