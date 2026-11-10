# A recorded item's leftovers are kept and discarded, not the end of the run

## Why

The loop's mid-run start refusal ends the whole run when the kept worktree is dirty and the item its branch marker
names is not one a child of this run was **ended at a bound** for. Measured 2026-11-10
(`.pi/issue-loop/runs/2026-11-10T08:19:24Z.json`): #807 was recorded `blocked` at 11:27:56Z, and the start of #100
was refused at 11:27:59Z over #807's own leftovers — `M openspec/specs/voice-guidance/spec.md` and an untracked
archive directory — so the run stopped at 11:58Z having spent **218 of its 480 minutes**, 262 minutes and its
remaining queue unspent.

That is not one bad night. Over the archive, ten runs ended on this refusal; in **five of them** the leftovers
belonged to an item the same run had recorded, and those five spent **1 699 minutes** unspent (433 · 117 · 299 · 234 ·
354 · 262 minutes for the six run records that name an owner in their own `items[]`). The rule the refusal protects
is that nothing is discarded **unrecorded** — and an item this run recorded has, by then, both a state entry and a
patch holding its work. The refusal was answering a question the run had already answered about itself, in the
same shape issue #664 cured for the cap arm: there the run ended the child at a bound and `own_leftovers` let it
discard; every other way a recorded item leaves dirt was still read as "another item's".

## What

- `recorded_here <item>`: the item stands in this run's own state (`$.items[]`).
- `recorded_leftovers_of <item>`: that, plus the work written to the item's patch — `own_leftovers_of` when the cap
  arm already wrote it, `keep_leftovers` otherwise, which is the same repair and the same record.
- The mid-run refusal discards and retries the start when `recorded_leftovers_of` holds, printing the record it
  relies on. Otherwise it behaves exactly as before: keep the leftovers, escalate the item, record it `blocked`,
  and stop with the item and the files named.

What the change costs, stated: an item the run itself blocked no longer escalates a **second** time through this
arm (its own block arm already wrote its label and its question), and the refusal's own attribution — escalate,
record, stop naming the item and the files — is now the path for an owner this run did not record, which no case in
the suite reaches. That gap is recorded where the mutations live (`13.x`, next to the retired `53.1`–`53.3` and
`62.6`) rather than left to be discovered.

## Impact

- `tools/loop/run.sh` — `recorded_here`, `recorded_leftovers_of`, and the mid-run arm's first branch.
- `tools/loop/tests/loop-cases.sh` — `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` and
  `run#aRefusedStartKeepsTheLeftoversItFound` are replaced by
  `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`; the case lists and the dispatch entry follow.
- `tools/loop/tests/loop-mutations.sh` — `13.10` re-pointed at the new line, `271.1`/`271.2` added, `53.1`–`53.3` and
  `62.6` retired with the reason written beside them.
- `guidelines/Backlog.recipes.md` §7d — the recipe that names the refusal's repair gains the recorded case.

Additive in behaviour, a reversal in one clause: a run that today stops now continues, and a run that today
escalates the same item twice now escalates it once. Rollback is the revert of the three files.

No Android module, no native boundary and no UI surface is touched; the delta compiles nothing.
