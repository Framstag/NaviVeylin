# Proposal — fix-item-floor-holds-what-an-items-gate-owes

## Why

The child's bound stopped being one constant in `fix/issue-663-the-child-cap-follows-what-its-gate-owes`: a child is
now given the larger of `LOOP_CHILD_MAX_SECONDS` and `LOOP_ITEM_GATE_SECONDS` (3 900 s = 65 min on this host). The run
answers "may this item start?" with a *second* clock, and that one was left where the child's bound had been:
`LOOP_ITEM_FLOOR_MINUTES=45` (2 700 s) is below what one item's gate owes, so `state.sh next` starts an item with
45 minutes left that needs 65 for its gate alone. The item's gate comes back green and the phases after it are lost
— the loss the child's bound no longer takes, through the other door.

Found by the review of that change (round 1, finding P1, measured 2026-10-26): the same measurement that gave the
child its floor — a red round of 957 s, one focused suite of 233 s, a green round of 972 s and 542 s of phases,
2 704 s in all — applies to the floor, because the floor is the number of minutes an item is *promised* before it
starts.

**The red test.** `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes` — on the base's `loop.conf`
(`LOOP_ITEM_FLOOR_MINUTES=45`, `LOOP_ITEM_GATE_SECONDS=3900`):

```
the item floor (LOOP_ITEM_FLOOR_MINUTES=45, 2700 s) is below what one item's gate owes (3900 s):
an item started there pays for its gate and loses the phases after a green round
FAIL state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes
```

**Owning capability and guideline section.** `agent-issue-loop`: the floor is the budget requirement's own rule
(`openspec/specs/agent-issue-loop/spec.md`, *One command starts a budgeted run* — the scenarios *The floor the run
refuses by is what its own items cost* and *A run with no measurement keeps the configured minimum*), and its prose
is `guidelines/Backlog.md`, "The loop", beside the child's bound. The requirement added here owns the relation between
the two clocks and the gate cost, which neither of them states today.

## What changes

- `tools/loop/loop.conf` — `LOOP_ITEM_FLOOR_MINUTES` 45 → 65, with the measurement and the relation beside it.
- `tools/loop-cases.sh` — `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`, in the declared set, the dispatch
  table and the body. It reads the two recorded numbers, so it is a configuration case and runs in milliseconds.
- `tools/loop-mutations.sh` — `64.2` records the floor back at 45 min and reddens the case.
- `guidelines/Backlog.md`, "The loop" — the floor's paragraph states the relation.
- the spec delta — one added requirement with two scenarios: the child's bound (shipped behaviour, case
  `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`) and the item floor.

## Spec

`ADDED` **Both clocks a run refuses by hold what one item's gate owes** — the profile records what an item's gate
owes and neither clock is recorded below it. Scenarios: *The child's bound follows what one item's gate owes*
(`run#theChildCapIsNeverBelowWhatOneItemsGateOwes`) and *The item floor is not below what one item's gate owes*
(`state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`).

## Impact

One recorded value, one case, one mutation record, one guideline paragraph, the spec delta. No compiled file, no
engine logic, no log line.

surface: 1 case added, 1 mutation record added (64.2), 1 requirement added with 2 scenarios (one naming a case that
ships, one naming the case this change adds), 1 recorded value (`LOOP_ITEM_FLOOR_MINUTES` 45 → 65).

## Open

- The change that moved the child's bound (`5148c875`) shipped without a spec requirement and without an archived
  change directory. This change supplies the requirement for both clocks rather than only its own half, so the
  behaviour `5148c875` added is no longer unpinned; its own artifacts are not rewritten here.
