# Design — the refused write is a recorded transition, not a retry

## The root cause, and the one fix

A block writes two transitions through `labels.sh` (`unpick`, then `escalate`) and both call sites ignored the
exit status. `labels.sh` already propagates the tooling's status — `update.sh` exits `ISSUES_EXIT_UNREACHABLE`
(75) after the four attempts `issues/lib.sh`'s `gh` seam makes, and policy refusals exit 3 — so the engine only
had to stop discarding it. The fix is one variable per item (`label_failures`), the transitions that were
refused, carried into `state.sh done` as `.labelFailures` and into the item's `--note` as prose, and one more
source in `report.sh`'s action list.

**Why the same variable and not two records.** `.escalatedLabel` already means "the label this run wrote on the
item" (issue #177, `tools/loop/state.sh:418`). A refused transition is the complement of that fact, so it is
recorded beside it rather than in a second mechanism: the report keeps one predicate per item.

**Why the note carries it too.** `state.sh report` (the table `report.sh` prints) shows an item's `note` as its
reason, and the item's evidence asks for the table to name an item "whose decision was never written" — the
`note` is the only column a reader of the table sees. `.labelFailures` is the machine-readable half the action
list selects on; the sentence in the note is the human half. Both are composed once, in `run.sh`.

## Blast radius

- `run.sh`: the blocked exit path only. The skip path's `unpick` (`run.sh:1422`) keeps its `|| true`: an item a
  run skipped carries its decision label or a hold, and the change that owns the skip path is the one that
  measured it (`run#aSkipClearsTheInProgressMark`). Widening here would have made one delta own two exit paths
  with different evidence.
- `state.sh`: one option, one field. `.labelFailures` is additive, so a state written before this change reads
  as `[]` wherever the report asks (`(.labelFailures // [])`).
- `report.sh`: one more `while` loop in the action list, after the branches and before the gate line; the
  `none` fallback now names six sources, which is the sentence `guidelines/Backlog.md` §7 repeats.
- Nothing else reads `.items[]`: `state.sh report`'s table, `next` and the budget arithmetic read `result` and
  `metrics`, which are untouched.

## Rollback

Remove the `--label-failures` argument from `run.sh`'s blocked path and the option from `state.sh`; the field
then stops being written and the report's loop prints nothing, because it selects on a field a state without it
does not have. No migration is needed in either direction.

## The alternatives the evidence eliminated

- **Stop the run on a refused write.** It answers nothing. The escalation's write and the tracker are the same
  failed transport, so a run that stops is the run with no account at all; and it would leave the item, the
  state and the report exactly as the defect left them, while `state.sh done` is the only place the fact can be
  kept. The engine already treats a refused *closure* the same way it treats a refused escalation: the closure
  records `closed: skipped` and the run goes on (`run.sh:1387`). The item's own text offers both shapes, but
  only this one delivers the consequence it asks for — "so `tools/loop/state.sh report` can name an item whose
  decision was never written".
- **Retry the write at the end of the run.** `issues/lib.sh`'s `gh` seam has already retried it four times by
  the time `labels.sh` returns; a second retry loop in the engine would be a second, unmeasured copy of that
  policy, and it would still have to record the outcome when the retries fail.
- **Read the failure from the tracker afterwards.** A run that re-reads the item's labels to see whether the
  write landed spends a network call per item on the one path where the network just failed, and cannot tell a
  refused write from a tracker a person changed. The refused status is the fact; the run holds it for free.
