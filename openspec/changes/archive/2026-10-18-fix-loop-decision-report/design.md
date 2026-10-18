# fix-loop-decision-report — design

The root cause is measured, so each decision below names the alternatives the evidence eliminated rather than
a menu of options (`openspec/config.yaml`, design: the bug-fix exception).

## D1 — The escalation is recorded where it is written (`state.sh`, `run.sh`)

`report.sh` read `.items[].verdict` while `run.sh` wrote the label on three paths where the verdict is another
word (`needs-diagnosis`, `blocked` twice), measured on HEAD as `expected '#21', got 'none'` in both
end-to-end cases while the gh record carried `agent/needs-decision`
(`evidence/red-on-head.log`). The label is recorded with the item — `state.sh done --decision-label` — at the
four item-level sites that escalate, and each site sets it from the write itself
(`if loop_labels escalate …; then escalated_label="$decision_label"; fi`), so the record cannot claim a label
the tracker does not hold.

| alternative | why the evidence eliminates it |
|---|---|
| make the report accept the verdicts the escalations end on (`needs-diagnosis`, `blocked`) | impossible: `blocked` is not a decision — a worker that exited non-zero, a child that wrote outside the tree and a claim without a commit all end blocked and are escalated nowhere. A verdict list would have to name the *cause* of the block, which is exactly the label; measured: `run#childClaimWithoutCommitIsBlocked` and `run#childWriteOutsideTheWorktreeIsBlocked` block an item with no escalation at all. |
| ask the tracker in `report.sh` which items carry the decision label | rejected: it makes the report depend on a credential and a network the run may not have at its end, and it can only answer for the whole tracker, not for what *this run* wrote — the issue asks for the decisions this run left behind. |
| change `labels.sh`/`state.sh done` so the recorded `verdict` becomes the label written | impossible without collapsing two facts into one field: the verdict a worker chose is what the report's per-item table shows and what `run#yieldIsCountedFromState` asserts, and overwriting it would make a skip look like an escalation. |
| record the escalation in a new top-level list (`state.sh escalate <issue>`) | a second write per escalation and a second thing to keep in step; `done` already runs on every path that escalates an item, and it is the run's only per-item record. The label rides on the record that is written anyway. |

## D2 — The pre-screen's exclusions keep the one predicate the escalation uses (`report.sh`, `run.sh`)

`escalate_prescreen_decisions` escalates `.excluded[] | select(.verdict == $decision_agent)`, so for an
exclusion the verdict value *is* the label written, and the report repeats that clause unchanged. Recording
it as well would need a state write for an item the pre-screen never queued — a second mechanism for the same
fact. The two clauses are therefore: an item carries the label its escalation recorded; an exclusion is
escalated by its verdict value, which is the profile's `decisionAgent` role read from the same profile by both
files (`tools/check-portability.sh` still finds no vocabulary value in the engine). The pre-screen's
second-pass guard — an item already recorded is not escalated again — is unchanged, and unchanged is why the
end-to-end cases use two candidates: with one candidate the run's own second pre-screen pass would re-exclude
the finished item on its branch and supply the report's list by accident, which is how the first draft of
`report#escalatedSkipNamesTheLabelWritten` passed on HEAD.

## D3 — The record is written only when the write happened (`run.sh`)

Every escalation site replaced its `loop_labels … || true` with `if loop_labels …; then
escalated_label="$decision_label"; fi`. `|| true` is kept in spirit — a failed tracker write still does not
end the run — but it no longer lets the record claim a label the tracker refused. No case proves that path
(the stub tracker does not fail), and it is named as unmeasured below.

| alternative | why the evidence eliminates it |
|---|---|
| keep `|| true` and set the label unconditionally | rejected: it is the same defect one level down — the report would name a decision the tracker never took, which is the disagreement this change removes, read in the other direction. |
| fail the run when an escalation cannot be written | rejected: `labels.sh` already exits 3 on a policy refusal and the run treats a label failure as non-fatal everywhere (`run#mayCloseNoLeavesTheIssueOpen` relies on a failed close not ending the run); one failed label is not a reason to abandon the items that come after it. |

## D4 — The report's third case is state-driven, not run-driven (`tools/loop-cases.sh`)

The two end-to-end cases prove the whole path (worker verdict → escalation → recorded label → report). They
cannot prove the *negative* bound, because a full run always has a branch and a second pre-screen pass may
re-exclude the item. `report#aVerdictAloneIsNoEscalation` therefore builds the state through the same public
interface (`state.sh queue`, `state.sh done`) and asserts that an item with no recorded label is named by
nothing — the bound that keeps the fix from degenerating into "list every item".

## Verification

`bash tools/loop-selftest.sh` (62 cases, the three new ones included) and
`bash tools/loop-mutations.sh --only '14.1 14.2 14.3 14.4'` are the gate; no compiled file is touched, so no
Gradle task runs (`guidelines/Build.md` §2, the scope block). Evidence: `evidence/red-on-head.log`,
`evidence/loop-selftest-final.log`, `evidence/loop-mutations.log`.

## Not measured, and named

* **A dry run.** `--dry-run` writes no label and its report still lists the exclusions whose verdict is the
  decision value — the report names the decisions the run *would* leave, and `run#dryRunTouchesNothing`
  asserts no label is written. Whether the line should say so is a wording question the issue does not raise.
* **An item escalated by a person, not by the run.** The list is what *this run* wrote; an item already
  awaiting a decision when the run starts appears only if this run escalates it again or the preflight names
  it — the preflight's own output is the cap's announcement, and it is unchanged.
* **A failed tracker write.** The label is recorded only when `loop_labels` succeeds (D3), but no case drives
  a failing tracker through an escalation.
