# fix-loop-hold-is-not-a-decision

## Why

`tools/loop/prescreen.sh` treated the bare hold state as a decision in waiting: any item on hold became the
decision verdict, with the fixed sentence "the item already awaits a decision (…)" and no question, and because
the decision *label* was absent the run escalated it — writing the label, which is exactly what the decision cap
counts. Measured 2026-10-20: **12** items carried `agent/needs-decision` against the default cap of 3, so the loop
refused to start before its pre-screen. At least two of the twelve were refuted claims (#109, #110) that nobody
had asked a question about (#193).

## What Changes

- The awaiting-decision clause splits on the recorded state rather than on the hold: the decision **label** makes
  an item a decision (excluded, not escalated again); a hold without it is excluded with the new profile role
  `heldVerdict` (`tools/tooling.json`, `roles.heldVerdict`), which writes no label, posts no comment and counts
  against nothing.
- The report prints the `held` exclusions alongside the rest, so the item is visible without being turned into a
  question.
- The `held` fixture scenario carries one item on hold with no decision label, so the two shapes cannot be
  confused by a future case.

## Spec

`openspec/specs/agent-issue-loop/spec.md`, "A hold is not a decision" — added in this pass with the fix.

## Cases and evidence

`prescreen#aHoldWithNoDecisionIsNotADecision` (red on HEAD: verdict `needs-decision` and a label write),
`prescreen#itemAwaitingADecisionIsNotQueued` (the escalated items keep their behaviour), and
`run#awaitingDecisionIsNotEscalatedAgain` (the run writes nothing for the bare hold). Mutation 21.1.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/prescreen.sh`, `tools/tooling.json` (one role; a foreign profile declares it too, `check-portability`
enforces that every declared value is read rather than repeated), `tools/loop-cases.sh`,
`tools/loop/fixtures/scenarios/held/`, `tools/loop-mutations.sh`, the spec above.

Rollback: revert the commit. Items a previous run escalated keep their labels, so the cap count returns to the old
number — which is why the drain of the twelve was done first, by hand, with the reason recorded on each item.
