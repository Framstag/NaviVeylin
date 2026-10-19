# fix-loop-awaiting-decision

## Why

The pre-screen read only the in-flight status, so an item the loop had itself escalated — carrying
`agent/needs-decision` and `status/on-hold` — stayed `agent/eligible`, was queued again, was re-derived and
was escalated a second time. #110 collected three identical conclusions across two days (2026-10-18T14:45Z,
2026-10-18T15:59Z, 2026-10-19T06:11Z), and the work spent on it is the same budget the decision cap exists
to protect (#183).

## What changed

- One clause leaves an item that already awaits a decision to its owner. It is excluded with the profile's
  decision verdict and recorded in the run's exclusion list.
- `alreadyEscalated` follows the decision **label**, not the hold: the refusal paths (`too-wide`,
  `unspecifiable`) and the repair path (`resolve-stale.sh` parks an item) write the same hold, and an item
  they parked must still be escalated once — otherwise nothing outside the run's machine-local state names
  it. The `decisions` fixture gained #44 (hold, no label) so a case can tell the two apart.
- The clause runs after the previous-attempt-branch rule and after the citation sensor, and the branch rule
  keeps the branch as the evidence. The review of this change found the opposite order made #176's adopt
  path unreachable: every escalated item lost the field `adopt_finished_exclusions` selects on.
- `run.sh`'s `escalate_prescreen_decisions` skips an exclusion that is already escalated, so the exclusion
  does not become a second identical comment.

## Spec

`openspec/specs/agent-issue-loop/spec.md`, "The report names the decisions the run itself wrote" — the list
of decisions now also comes from an item that was already awaiting one. Applied in the same commit
(910a016), because the fix and its contract were one pass.

## Cases and evidence

`prescreen#itemAwaitingADecisionIsNotQueued` (red on HEAD: verdict `eligible` for #41–#43 of the
`decisions` fixture), `prescreen#escalatedItemKeepsItsBranchAsEvidence`,
`run#awaitingDecisionIsNotEscalatedAgain`; mutations 17.1, 17.2, 20.1, 20.2.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/prescreen.sh`, `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`, `tools/loop/fixtures/scenarios/decisions/issues.json`.
