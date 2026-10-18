# fix-loop-blocked-item-escalation — design

The root cause is measured (one guarded write in the blocked arm, `run.sh:539-543`), so each decision below
names the alternatives the evidence eliminated rather than a menu of options (`openspec/config.yaml`, design:
the bug-fix exception).

## D1 — The blocked arm escalates, in the pass that holds the reason (`run.sh`)

The reason a run blocks an item lives in `blocked_reason` and is written to `.items[]` — a record
`state.sh init` replaces wholesale on the next run, which is why the measured item (#114) left no trace. The
one place that holds the reason while the item, the evidence directory and the branch are still in hand is the
outcome dispatch, after the child has exited and before `loop_state done`. So the `*)` arm calls
`loop_labels escalate` there, with `${blocked_reason}` as `--reason` and `$item_dir` plus
`fix/issue-$issue-$slug` as `--evidence` — the same call, the same evidence shape and the same recorded label
(`--decision-label`) the `skipped` arm already uses for its decision verdicts.

| alternative | why the evidence eliminates it |
|---|---|
| escalate after the run ends, reading `.items[]` | impossible: the next run's `state.sh init` replaces that file wholesale, and a second process would have to re-derive which items *this* run blocked — the reason dies with the state, which is the defect. |
| carry the previous attempt's note into the re-queued work package instead | not a fix for the measured symptom and out of scope by the issue: the label, the status and the comment would still be missing, so `gh issue list --label agent/needs-decision` (the owner's only durable view) and the decision cap stay blind — and the work package is only built when a slot is spent, one round-trip later. |
| escalate from inside the agent process | impossible: the process is given a prepared package and no tracker credential (spec, "The environment holds no tracker credential"), and `labels.sh` is the run's only label writer. |
| keep the `review_verdict` guard and escalate only the reasons it did not cover | eliminated by measurement: the two shapes disagree on the one outcome they both produce. The guarded path writes `needs-decision` with no comment and no evidence (`run#reviewRoundsCapAtTwo` asserts exactly that), so a failed second round — a decision by definition — would be the one block that names neither its reason nor its branch, and the new case could assert only the unguarded shape. One outcome, one escalation. |

## D2 — The reason text is the run's, and the evidence is the branch (`run.sh`)

`--reason` is the reason the run itself determined (`blocked_reason`), not the worker's verdict: the worker's
report is what the run verified against, and on the paths that matter here there is no worker verdict to quote
("a claim without a commit" is the run saying the claim is empty). `--evidence` names the item's evidence
directory — where the worker's report, the review diffs and the gate XMLs are — and the branch that carries
the attempt, because those two are what a person needs to decide without re-running anything.

Fallback `${blocked_reason:-the item was blocked}`: every `outcome="blocked"` assignment sets the reason, so
the fallback is unreachable today; it is the `labels.sh` contract (`--reason` is required) kept true by
construction if a future blocked path forgets to set one.

## D3 — Two mutations, because the fix rewords a line one of them matched

`14.3` ("the failed second review round recording the label it wrote") matched
`escalated_label="$decision_label"   # the failed second round's escalation`, a line this fix deletes. It is
re-pointed at the block's own recording line so it still removes the same thing — the recorded label the
report reads (issue #177) — and `14.5` is added for the new write itself: the escalate call made unreachable,
the case must go red. Without `14.3`'s repointing the harness reports `MUTATION-DID-NOT-APPLY`, which is a
failure of the harness, never a pass.

## Verification

No compiled file is touched, so no Gradle task runs: the gate is the offline shell selftest of the tool this
change touches (`guidelines/Build.md` §2, the scope block).

- red on HEAD: `bash tools/loop-selftest.sh --case 'run#blockedItemIsEscalatedWithItsReason'` — 1 of 1 red,
  `evidence/red-on-head.log`.
- green: the same case, then the four cases the blocked arm's neighbours own
  (`run#childClaimWithoutCommitIsBlocked`, `report#failedReviewEscalationIsNamed`,
  `report#aVerdictAloneIsNoEscalation`, `run#reviewRoundsCapAtTwo`), then the full
  `bash tools/loop-selftest.sh` once, `evidence/loop-selftest-final.log`.
- falsification: `bash tools/loop-mutations.sh --only '14.3 14.5' --timeout 60` — both red when mutated, both
  green after the restore, `evidence/loop-mutations.log`.
- the spec's declared case names resolve: `bash tools/declared-cases-selftest.sh`.

## Not measured, and named

- **A failed tracker write during a block.** The label is recorded only when `loop_labels` succeeds (the
  `if … then` shape every escalation site uses), but no case drives a failing tracker through a block: the
  stub `gh` never fails.
- **A dry run.** It never reaches the item loop, so it blocks nothing and escalates nothing — unchanged by
  this fix.
- **An item blocked twice.** The second block's comment is appended rather than deduplicated; the issue does
  not ask for deduplication and no case measures it.
