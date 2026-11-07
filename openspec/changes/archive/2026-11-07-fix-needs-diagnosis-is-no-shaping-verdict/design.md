# Design — the profile stops declaring a label the run never writes

## Root cause

One verdict is answered twice, in two places that were never compared:

- `tools/tooling.json:47` declares `needs-diagnosis` a shaping verdict, and `tools/loop/labels.sh:152` writes
  `agent/needs-shaping` for every verdict that list names;
- `tools/loop/run.sh:2894` routes a `needs-diagnosis` skip to `loop_labels escalate` (`:2916`), which writes
  `agent/needs-decision` and the hold — never the shaping label, because the escalation path is not the hold path.

The two artifacts could disagree without a red case because the only case that walked the label matrix called
`labels.sh` with the verdict directly (`labels#verdictToLabelMatrix`), which is not a path a run takes; the one case
that drove `needs-diagnosis` through the run (`report#escalatedSkipNamesTheLabelWritten`) asserted the decision label
it wrote and nothing about the shaping label it did not.

**In one sentence:** the profile declared `needs-diagnosis` a shaping verdict while the run's skipped arm escalated
that verdict, and no case asserted that the labels a skip leaves are the set the profile names.

## The fix

The owner's recorded decision (item #738, 2026-11-03) is option 2: the declaration is the half that is wrong, so
`needs-diagnosis` leaves `roles.shapingVerdicts` and the inbox is three verdicts. Then:

- the shipped requirement in `openspec/specs/shaping-inbox/spec.md` restates the three answers and gains one
  scenario, "The labels a skip leaves are the set the profile names";
- one case, `run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames`, drives a `needs-diagnosis` skip through the run
  and compares the labels the skip's own write adds with the set the profile implies for that verdict, so the
  declaration and the write are asserted to be one answer;
- the two cases that asserted the old shape are realigned — `labels#verdictToLabelMatrix` expects the decision label
  and the hold, and `run#aShapingVerdictWritesTheLabel` expects no shaping label — and the guideline and the two
  naming comments in `labels.sh` say three.

`run.sh`'s escalation condition and `labels.sh`'s `needs-diagnosis` arm are untouched: the item still leaves the run
on `agent/needs-decision` and the hold, with the question the worker's `LOOP-DECISION` carries. No runtime behaviour
changes at all.

## Blast radius

- `tools/tooling.json`: one list loses one value. `tools/loop/lib.sh`'s `loop_shaping_verdicts` and
  `tools/loop/labels.sh`'s gate read the list and need no edit; `tools/check-portability.sh` excludes the list from
  its project-value sweep, and `tools/issue-tooling-cases.sh`'s `shaping#anEmptyListIsAnEmptyQueue` writes its own
  profile, so neither is affected.
- `tools/loop-cases.sh`: one case added (369 declared cases, from 368) and two realigned. The new case declares
  `tools/loop/run.sh`, `tools/loop/labels.sh`, the scenario fixtures, the child events and `tools/tooling.json`.
- `guidelines/Backlog.md` §5: one paragraph, its count and its list. No section number and no routing row changes.
- `tools/loop/labels.sh`: two comments, no code. The mutation record `75.1` mutates `add_labels+=("$shaping_label")`,
  which this change does not touch, and the case it names still fails under it because three verdicts still write
  the label.
- `openspec/specs/shaping-inbox/spec.md`: restated through this change's delta only; the shipped file is merged at
  archive.

## Alternatives eliminated

- **Drop `needs-diagnosis` from `tools/loop/run.sh:2894`, so the item takes the shaping path** (the item's candidate a). It
  removes the item from the decision queue and the ceiling, but it reverses `shaping-inbox`'s recorded "the shipped
  requirement keeps its text" and costs a shipped scenario — a design fork the owner answered against.
- **Write both labels** (candidate c). `shaping-inbox`'s "The shaping state is neither a hold nor a decision" names
  it as the defect the decision queue was built to end: an item nobody asked a question about would spend a decision
  slot.
- **Assert the agreement without changing the profile.** A case that pins today's behaviour — the list names the
  verdict, the run writes no label — would make the disagreement a requirement, and the shaping inbox's own queue
  would keep listing nothing for the verdict it was built for.

## Rollback

Revert the change: the list regains `needs-diagnosis`, the new case and its scenario are deleted, the two realigned
cases return to expecting the shaping label, and the guideline paragraph and the two comments say four again. No
state, no key and no runtime behaviour changes, so the revert is the deleted value.

## Open decisions

none
