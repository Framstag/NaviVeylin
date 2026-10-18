# Let the report name the decisions the run itself wrote

## Why

Issue **#177**. The 2026-10-18 run printed `decisions the owner owes: none` in its report while
`labels.sh escalate` had just written `agent/needs-decision` + `status/on-hold` on #110 (state verdict
`needs-diagnosis`), #114 and #115 (state verdict `blocked`). The next run then refused at the preflight with
`refusing to start: 3 item(s) already await a decision and the cap is 3` (exit 5) — a stop the previous run's
own report said could not happen.

**Root cause: the report derives its decision list from the verdict a worker chose, while the run escalates on
the facts it finds.** `tools/loop/report.sh` names `[.excluded[] | select(.verdict == $d)] +
[.items[] | select(.verdict == $d)]` with `$d` = the profile's `decisionAgent` value, and
`tools/loop/state.sh cmd_done` records the worker's own `verdict`. But `run.sh` writes the label on three
paths where the verdict is a different word: a skip whose verdict is `needs-diagnosis`
(`run.sh:507`), a branch a previous attempt left (`run.sh:349`, verdict recorded `blocked`) and a second
review round that failed (`run.sh:524`, verdict recorded `blocked`) — plus the kept-worktree refusal
(`run.sh:186`). The label and the report disagree by construction, because the report reads a field the
escalation never writes.

Measured on HEAD (`evidence/red-on-head.log`, the engine as committed in `.pi/tmp/head177`, the new cases run
against it):

```
--- report#escalatedSkipNamesTheLabelWritten
  the report names the item the run escalated, not the verdict it chose: expected '#21', got 'none'
--- report#failedReviewEscalationIsNamed
  the report names the blocked item as a decision owed: expected '#21', got 'none'
--- report#aVerdictAloneIsNoEscalation
loop: done: unknown argument --decision-label
  only the item whose escalation recorded the label is listed: expected '#22', got 'none'
tally: 3 of 3 red on HEAD
```

In both runs the tracker writes happened (the gh record carries `agent/needs-decision`) and the state records
`needs-diagnosis` resp. `blocked` — the two facts the report has to reconcile.

**The requirement the defect contradicts** is `agent-issue-loop`'s *"A pile of open decisions stops the
run"*: the run "SHALL refuse to start when more items awaiting a person's decision are open than the
configured cap allows, and SHALL report them instead of producing more work". A report that says `none` in
the run that fills the cap reports nothing, and the refusal it produces is unannounced.

## What changes

**One fix: the run records the label its escalation wrote on the item, and the report names the items from
that record.**

1. **`state.sh`** — `done` gains `--decision-label <label>`: the decision label the run's escalation wrote on
   this item, recorded per item as `escalatedLabel` and empty when nothing was escalated. The flag is an
   optional argument on an existing command; the verdict keeps its meaning.
2. **`run.sh`** — each of the four item-level escalation sites sets the label from the write itself
   (`if loop_labels escalate …; then escalated_label="$decision_label"; fi`) and passes it to the item's own
   `loop_state done`. The pre-screen's exclusions are unchanged: `escalate_prescreen_decisions` escalates
   exactly the exclusions whose verdict value *is* the decision label, which is the one clause the report
   repeats for them.
3. **`report.sh`** — `decisions the owner owes` reads `[.items[] | select(.escalatedLabel != "")]` instead of
   `[.items[] | select(.verdict == $d)]`. The line's wording, the exclusion clause and the rest of the report
   are untouched.
4. **Three cases** (all red on HEAD) and **four mutations** for the new invariant.

## Impact

| file | change |
|---|---|
| `tools/loop/state.sh` | `done --decision-label`: the label the escalation wrote, recorded as `escalatedLabel` |
| `tools/loop/run.sh` | the four item-level escalation sites (kept worktree, leftover branch, skip, failed second review) record the label they wrote |
| `tools/loop/report.sh` | the decision list reads the recorded label; its comment names why the verdict is not evidence of an escalation |
| `tools/loop-cases.sh` | `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation` |
| `tools/loop-mutations.sh` | `14.1`–`14.4` |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement modified: *"A pile of open decisions stops the run"* |

No compiled file changes: the verification is `tools/loop-selftest.sh` and `tools/loop-mutations.sh`, both
offline. No guideline section states the report's derivation, so none is updated.

**Additive**, not breaking: one optional flag is added, the decision list's derivation is corrected, the
label test and the cap refusal keep their meaning.

**Rollback**: drop the `--decision-label` arm, the four recordings and the new derivation — the three cases
then fail exactly as recorded in `evidence/red-on-head.log`.

**Not claimed here**: nothing about a *dry run*, which writes no label at all and whose report therefore names
the exclusions it would escalate; and nothing about an item escalated beyond the run (a person's own
`agent/needs-decision` write) — both named in `design.md`, neither measured.
