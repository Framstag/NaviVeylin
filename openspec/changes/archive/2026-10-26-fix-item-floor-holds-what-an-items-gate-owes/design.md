# Design — fix-item-floor-holds-what-an-items-gate-owes

## Root cause

One number, measured against the wrong clock. `run.sh`'s `child_cap` gives a child the larger of
`LOOP_CHILD_MAX_SECONDS` and `LOOP_ITEM_GATE_SECONDS` (`5148c875`), so the run no longer ends a child inside its own
item's cost. But `state.sh next` decides whether an item may *start* by another number, `LOOP_ITEM_FLOOR_MINUTES`,
and that one is still 45 min — below the 65 min one item's gate owes. An item started with 45 minutes left pays for
its gate (2 162 s measured) and loses the phases after it (542 s), which is the same discarded green change the
child's bound was fixed for, produced by the other clock.

The measurement is the same one: red round 957 s, focused suite 233 s, green round 972 s, phases 542 s — 2 704 s for
the item against the 2 700 s a 45-minute floor promises. Measured 2026-10-26 on #310; #664 and #666 ended the same
way.

## The fix

`LOOP_ITEM_FLOOR_MINUTES=65` — the floor is the minutes an item is *promised*, so it is derived from the same number
as the child's bound: 45 minutes was the value before the gate's cost was known to either clock.

A case reads both recorded numbers and refuses the relation:
`state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`. It is a configuration case because both numbers are recorded
facts, and because the floor's *use* is already covered (`state#floorWithNoHistoryIsTheConfiguredMinimum`,
`state#floorIsDerivedFromTheFinishedItemsWallTimes`), which read the configured value rather than naming it.

Nothing else moves: the derivation in `state.sh` (median × 1.5, floored), the refusal's message, and the child's bound
are untouched. A project that records no `LOOP_ITEM_GATE_SECONDS` keeps 45 minutes and the case passes — the relation
does not exist there.

## Blast radius

| where | what |
|---|---|
| `tools/loop/loop.conf` | `LOOP_ITEM_FLOOR_MINUTES` 45 → 65 with its measurement |
| `tools/loop-cases.sh` | one case: declared set, dispatch table, body |
| `tools/loop-mutations.sh` | `64.2` |
| `guidelines/Backlog.md` | "The loop" — the floor's paragraph |
| spec delta | one added requirement, two scenarios (the child's bound ships, the floor is this change's) |

No compiled file, no engine logic, no log line.

## Risk

- A run refuses items it would have started: with 65 minutes left it now stops instead of starting one. That is the
  point — an item started there cannot finish — but it is a behaviour change, and the refusal's message already names
  the floor it compared, so a reader sees the number rather than an unexplained stop.
- `LOOP_ITEM_FLOOR_MINUTES` is the *lower bound* of a derived floor, not the floor itself: a run with expensive
  history still raises it (median × 1.5). Nothing about that changes.

## Verification

- Red on the base: `evidence/red-on-base.log` — `the item floor (LOOP_ITEM_FLOOR_MINUTES=45, 2700 s) is below what one
  item's gate owes (3900 s) …`, `FAIL state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`; green on the change.
- The floor's own cases unchanged and passing: `state#floorWithNoHistoryIsTheConfiguredMinimum`,
  `state#floorIsDerivedFromTheFinishedItemsWallTimes` (they read the configured minimum, so 65 does not redden them).
- The invariant: `64.2` records the floor back at 45 min and the case goes red (`evidence/focused-mutations.log`).
- The whole suite and the gate once: `evidence/suite.log`, `evidence/gate.log`.

## Open

- The change that moved the child's bound shipped without a spec requirement or an archived change directory. This
  change supplies the requirement for both clocks; it does not rewrite that change's artifacts, and the omission is
  recorded there rather than here.
