# fix-closure-settled-state

## Why

`tools/issues/close.sh` closed an item with `gh issue close` and never rewrote its `status/*` label, so a
closed item still read `status/on-hold` or `status/open` to every query that asks the tracker. The loop's
own closure path writes the status first (`tools/loop/labels.sh`), so the two ways an item reaches a
settled state disagreed about the state they leave — and a reader cannot tell which path closed an item.
Filed as #181 and, from the other side, #188.

A second defect in the same path, found by the independent review: `update.sh` ended with
`[ "$edit_needed" -eq 1 ] && gh "${args[@]}"`, unchecked. A `gh issue edit` refused for scope, rate
limit or network exited **0** while the JSON report said
`updated: true, labels: [status/fixed-by], removedLabels: [status/on-hold]`. Without that repair this
change's fail-closed branch was unreachable and its guarantee would have been a comment.

## What changed

- `close.sh` resolves the settled state from the profile's `roles` section — `closeStatus`, or
  `retiredStatus` beside `--not-planned` — and writes it through `update.sh --status` **before**
  `gh issue close`. A write the tooling refuses leaves the item open with the state it carried; a closure
  that fails after the write names the state the item now holds.
- The role is resolved before the first write, so a half-adapted profile refuses while the item is
  untouched.
- `update.sh` checks the `gh issue edit` it issues and lands it before the report it prints.
- `tools/tooling.json` and the foreign fixture declare `roles.retiredStatus`; the roles comment names the
  new reader. `close.sh`'s `--help` range follows its doc block, and `.pi/skills/close-issue/SKILL.md`
  documents the three-step write order.

## Spec

No delta: the requirement this restores is stated already — the backlog's invariants (`Backlog.md` §4)
and the loop's closure contract (`openspec/specs/agent-issue-loop/spec.md`) both assume one settled state
per item. This change makes the person-facing path obey it.

## Cases and evidence

`close#writesTheSettledStatus`, `close#keepsTheItemOpenWhenTheStateCannotBeWritten`,
`close#writesNothingWhenTheItemAlreadyCarriesTheState`, `close#refusesAProfileThatNamesNoSettledState`;
mutations A1.1–A1.4 in `tools/close-mutations.json`. Measured on the live tracker: #188 was closed through
the new path and read `status/fixed-by` in the same call.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

- `tools/issues/close.sh`, `tools/issues/update.sh`, `tools/issue-tooling-cases.sh`,
  `tools/issues/fixtures/gh` (a `GH_STUB_FAIL` hook so a case can watch a refused write),
  `tools/tooling.json`, `tools/fixtures/foreign-project/tools/tooling.json`,
  `tools/close-mutations.json`, `.pi/skills/close-issue/SKILL.md`, the CI mutation-list step.
