# Proposal

## Why

A worker's `stale` skip is gate 1 of its eligibility check — "the claim is verified in the tree today" — and its
meaning is that the item's premise no longer holds. Until this change the run recorded that verdict as a bare
hold:

- `tools/loop/run.sh:1849-1850` escalates only `needs-decision`, `needs-diagnosis` and `falsified`; every other
  skip, `stale` included, took the hold branch at `tools/loop/run.sh:1875`;
- `tools/loop/labels.sh:88` mapped `stale` to a hold with a comment and no decision label;
- the pre-screen then meets an item on hold with no decision label at `tools/loop/prescreen.sh:375` and reports it
  as `held` — "the item is on hold with no recorded decision … neither work nor a decision this run may take".

The red run's recorded `gh` output is that state, in one place: the stale skip writes `--add-label status/on-hold`
and a comment, and `--add-label agent/needs-decision` is the assertion it fails
(`evidence/stale-skip-red.xml`). So the one verdict that says nothing is left to do put the item where nothing can
reach it — not work, not a question, and open for ever. The item reports the same park on #147, whose own record
(`.pi/issue-loop/items/147/evidence-stale.md`, `worker-report.md`) carries the `skipped` / `stale` verdict and
`No change planned`; the `status/on-hold` beside the run's comment and the hand closure through
`tools/issues/close.sh --not-planned` are that item body's own record of it (2026-10-27), not a measurement this
change re-took.

The `falsified` skip already escapes that park (`openspec/specs/agent-issue-loop/spec.md`, "A skip that falsifies
the premise is closed as mis-diagnosed"), but a falsification closes only with a measurement **and** a review of
the unchanged tree; `stale` carries neither — the worker prompt stops at the verification
(`tools/loop/prompts/worker.md:98-102`) and the run never starts a review for a skip. Closing on it would close an
item on a claim nobody measured, which is the state that requirement refuses.

Owning capability: `agent-issue-loop` — the new "A stale skip asks its own question", and two shipped requirements
this change narrows rather than routing around: "A hold is not a decision" (whose listed hold writers now exclude
`stale`) and "A state the run writes is readable from the item" (whose hold scenario named a skip the run no
longer holds). Owning guideline section: `guidelines/Backlog.md` §5 "Who may write what", the rules that keep the
decision queue answerable — which this change adds one of.

Red test, on HEAD, before the fix:

```
FAIL run#staleSkipAsksItsQuestion
  a stale skip leaves a decision, not a bare hold nobody can answer: '--add-label agent/needs-decision' not found in: gh issue edit 21 --add-label agent/in-progress
```

`XML tests="1" failures="1"` (`evidence/stale-skip-red.xml`). The file's own `timestamp` is the second of its two
runs — `2026-10-27T19:38:00Z`, the guard-only mutation (`bash tools/loop-mutations.sh --only 21.3`) reproducing the
same FAIL; its `system-out` records the HEAD run itself, `2026-10-27T19:36:18Z`, whose prose copy is
`evidence/revert-check.txt`.

The second new case is red on HEAD too, measured the same way — the guard-only mutation, the case, the restore
(`evidence/stale-skip-keeps-question-red.xml`, `2026-10-27T20:07:09Z`):

```
FAIL run#staleSkipKeepsTheWorkersQuestion
  the comment carries the worker's question: 'recommended: retire it' not found in: gh issue edit 21 --add-label agent/in-progress
loop-selftest: 1 of 1 cases failed
```

## What Changes

- A `stale` skip escalates: the run writes the decision label and the hold together, with one comment carrying
  the verdict's reason and the question — retire the item as stale, or re-diagnose it. `tools/loop/run.sh`'s skip
  path admits the verdict to the escalation branch it already has (`run.sh:1850`).
- A stale skip whose report carries no `LOOP-DECISION` gets the run's own stated fork, exactly as a block with no
  reviewer decision does (`run.sh:1861-1869`): the escalation must hold something a person can answer.
- `tools/loop/labels.sh` drops its `stale` verdict: the run no longer writes it, so the writer's hold list
  (`labels.sh:88`), the usage message that advertises it (`labels.sh:128`) and the header that described it
  (`labels.sh:18-20`) all lose it, and a `labels.sh stale` call now stops as an unknown verdict. What the label
  writer can still write as a hold are the four verdicts that state no question (`unspecifiable`, `too-wide`,
  `not-spec-shaped`, `needs-target`), plus the device and decision verdicts' rows, untouched.
- `tools/loop/prompts/worker.md:98-102` tells the worker that a stale skip is a question and that its
  `LOOP-DECISION` is required with it.
- The delta narrows the two shipped requirements the old mapping rested on — "A hold is not a decision" and "A
  state the run writes is readable from the item" — so no shipped scenario still asserts that a `stale` skip
  leaves no decision label.

Not in scope: closing a stale item, the falsification closure, the device verdict, the decision cap and the
pre-screen's held reporting — all unchanged.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `agent-issue-loop`: a new requirement, "A stale skip asks its own question", and three existing requirements
  restated — "A hold is not a decision" and "A state the run writes is readable from the item", whose hold
  vocabulary and hold scenario no longer name `stale`, and "A skip that falsifies the premise is closed as
  mis-diagnosed", whose "every other skip keeps the state it writes today" now names the hold verdicts.

## Impact

Tooling only: `tools/loop/run.sh`, `tools/loop/labels.sh`, `tools/loop/prompts/worker.md`, their cases
(`tools/loop-cases.sh`) and one mutation record (`tools/loop-mutations.sh`). No application source, no resource,
no native code, no compiled file — so the change owes no build.

surface: 2 cases added (`run#staleSkipAsksItsQuestion`, `run#staleSkipKeepsTheWorkersQuestion`), 2 cases rewritten
(`labels#verdictToLabelMatrix` gained its two missing hold rows and the `stale` refusal;
`labels#holdCarriesItsReason` names `unspecifiable` instead of `stale`), 2 engine files, 3 shipped requirements
restated, 1 prompt, 1 mutation record, 0 fixtures realigned —
`grep -rn 'stale' tools/loop/labels.sh tools/loop/run.sh tools/loop/prompts/worker.md tools/loop-cases.sh
tools/loop-mutations.sh` (filtered of the unrelated stale-base, stale-tree, stale-citation and `clear-stale` rows).
