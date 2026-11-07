# A `needs-diagnosis` skip stops being declared a shaping verdict

## Why

`tools/tooling.json:47` names four verdicts in `roles.shapingVerdicts` — `needs-diagnosis`, `unspecifiable`,
`too-wide` and `not-spec-shaped` — and `tools/loop/labels.sh:152` writes `agent/needs-shaping` for every verdict that
list names. The run never reaches that write for the first of them: `tools/loop/run.sh:2894` tests the skip verdict
against the decision agent **and** `needs-diagnosis` in one condition and calls `loop_labels escalate`
(`tools/loop/run.sh:2916`), which writes `agent/needs-decision` and no shaping label. `labels#verdictToLabelMatrix`
passed only because it calls `labels.sh` with the verdict directly, which is not a path a run takes.

**Root cause, in one sentence:** the profile declares `needs-diagnosis` a shaping verdict while the run's skipped arm
routes that same verdict to the escalation path, so the declaration and the run disagree about one verdict and
nothing asserts that they agree.

**The red case, on the base `b0daba96`** — one `bash tools/loop-selftest.sh --case
'run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames'` run, 2026-11-07T20:30:57Z–20:31:04Z, which drives a
`needs-diagnosis` skip through `run.sh` and compares the labels the skip's own write adds with the set the profile
implies for that verdict:

```
run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames|   the item carries exactly the labels roles.shapingVerdicts names for a needs-diagnosis skip (issue #738): expected 'agent/needs-decision agent/needs-shaping status/on-hold', got 'agent/needs-decision status/on-hold'
FAIL run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run, and `evidence/red-on-HEAD.xml` its tally (`tests=1 failures=1 errors=0`),
transcribed into a `testsuite` from the runner's own summary — the loop's shell runner writes no JUnit XML.

**Owning requirement:** `openspec/specs/shaping-inbox/spec.md`, "A shaping verdict carries the shaping label"
(`:10`) — the requirement this change restates as three verdicts and one agreement scenario.

**Owning guideline section:** `guidelines/Backlog.md` §5 "Who may write what" (`:115`) — its paragraph "The shaping
inbox is a third state, and it is neither a hold nor a decision" (`:194`) states that four verdicts write the label
and owes the corrected count with the verdict the run escalates instead.

The recorded owner decision on the item settles the direction: option 2 of the question recorded 2026-11-03 — remove
`needs-diagnosis` from `roles.shapingVerdicts` and correct the two documents and the two comments, leaving `run.sh`'s
escalation condition and `labels.sh`'s `needs-diagnosis` arm untouched. The shipped `agent-issue-loop` scenario "The
skips this change does not re-route are unchanged" keeps its text, because after this change it states what the run
does.

surface: 1 profile line (`tools/tooling.json:47`), 1 shipped requirement restated with 1 scenario added
(`openspec/specs/shaping-inbox/spec.md:10`), 2 existing cases realigned (`labels#verdictToLabelMatrix`,
`run#aShapingVerdictWritesTheLabel`) and 1 case added (`run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames`),
1 guideline paragraph corrected (`guidelines/Backlog.md:194`), 2 naming comments corrected
(`tools/loop/labels.sh:18`, `:148`) — counted with `./tools/look.sh 'shapingVerdicts' --files` (27 matches in 15 files,
9 of them live) and `bash tools/loop-selftest.sh --list | wc -l` (`368` before the change, `369` after).

## What Changes

1. **`roles.shapingVerdicts` names three verdicts.** `needs-diagnosis` leaves the list, so the profile declares no
   label the run never writes for it, and the inbox is `unspecifiable`, `too-wide` and `not-spec-shaped`.
2. **The shipped requirement states three answers and how a skip agrees with the list.** The "one root cause does not
   explain this" answer is dropped, and a scenario binds the labels a skip leaves to the set the profile names.
3. **One case asserts the agreement**, driving a `needs-diagnosis` skip through the run and reading the expected set
   from the profile itself, so the declaration and the write cannot drift apart unnoticed again.
4. **The two cases that asserted the old shape are realigned**: the label matrix expects the decision label and the
   hold for `needs-diagnosis`, and the shaping-verdict case expects no shaping label for it.
5. **`guidelines/Backlog.md` §5 and the two naming comments in `tools/loop/labels.sh` say three**, and name the
   verdict the run escalates instead.

## What this does not do

- It does not move the escalation: `tools/loop/run.sh:2894` keeps `needs-diagnosis` in its condition, so the item still lands on
  `agent/needs-decision` with the hold, and it still spends a `LOOP_MAX_DECISIONS` slot.
- It writes no second label: the rejected option 3 is excluded by `shaping-inbox`'s "The shaping state is neither a
  hold nor a decision".
- It does not touch the shipped `agent-issue-loop` scenario "The skips this change does not re-route are unchanged".
- It does not give the shaping inbox a consumer: no skill reads `tools/issues/shaping.sh` today, which the item
  records as a separate filing.
