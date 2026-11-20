# A container that cannot advance takes no slot

## Why

`tools/loop/prescreen.sh` refuses a container itself — the item's issue type is `roles.containerType`, so
`verdict="container"` at `tools/loop/prescreen.sh:401` — and refuses each child that awaits a decision
(`tools/loop/prescreen.sh:667`, `verdict="$decision_verdict"`), and nothing joins the two. Every child of a container
is screened as an item of its own, so a container whose children are a pile of open decisions, or whose only workable
child this run already cut at a bound, is handed the run's most expensive slots one child at a time.

**Root cause** — the pre-screen judges each candidate alone and never the container it belongs to, so a container the
run cannot advance keeps offering children as work.

**Red test** (2026-11-20T08:08:20Z, `evidence/red-on-HEAD-aContainerCutAtABoundTakesNoFurtherSlot.log` and
`evidence/red-on-HEAD-aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot.log` in this change): two cases, each
`loop-selftest: 1 of 1 cases failed` on the base commit `2c011b769798ae0e5e4dbfbf43b98e9c1e730d99`:

```
prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot|   a container whose remaining child awaits a decision takes no slot: expected 'epic-stalled', got 'eligible'
FAIL prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot
loop-selftest: 1 of 1 cases failed
```

```
prescreen#aContainerCutAtABoundTakesNoFurtherSlot|   the container whose child a bound cut takes no further slot: expected 'epic-stalled', got 'eligible'
FAIL prescreen#aContainerCutAtABoundTakesNoFurtherSlot
loop-selftest: 1 of 1 cases failed
```

The base's own verdict for both is `eligible`: the child of a container that cannot close is queued exactly as an item
of no container is.

**Owning capability**: `agent-issue-loop` — "The run acts only on authorised, eligible items"
(`openspec/specs/agent-issue-loop/spec.md:216`), whose scenarios already say that a candidate the pre-screen refuses is
recorded with its clause and that a refusal no longer holding is withdrawn.

**Owning guideline section**: `guidelines/Backlog.md:87` §3 "What the tracker holds" — the rule that a container is
never work and that its children "carry that work and are selected on their own labels"; the paragraph is where the
container-level refusal belongs and it is the paragraph this change realigns.

**Surface** — `tools/look.sh 'its children carry the work'` (3 matches in 3 files: `tools/loop/prescreen.sh:402`, the
container case in `tools/loop/tests/loop-cases.sh:21809`, `tools/loop/tests/fixtures/scenarios/container/issues.json:4`;
a fourth file once this proposal quotes the pattern) and `tools/look.sh 'stopReason'` (639 matches in 377 files:
`report.sh` 5, `state.sh` 5, `run.sh` 4, `prescreen.sh` 2, `loop-cases.sh` 43, `loop-mutations.sh` 12, the spec 10,
`guidelines/Backlog.recipes.md` 9, the rest archived changes and their evidence) name the places that assert the rule.
The container rule itself already has its case (`prescreen#aContainerTypeIsNotWork`) and is not this change's work;
what was missing is the level between the container and the child, and its surface is one pass in the screen plus the
two reads the pass joins.

surface: 2 cases added (`prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot`,
`prescreen#aContainerCutAtABoundTakesNoFurtherSlot`), 1 fixture added
(`tools/loop/tests/fixtures/scenarios/epic-stalled/issues.json`), 0 cases rewritten, 1 mutation record added (`938.1`),
1 guideline paragraph realigned (`guidelines/Backlog.md:87`) — `tools/look.sh 'its children carry the work'` (3
matches in 3 files), `tools/look.sh 'stopReason'` (639 matches in 377 files)

## What changes

1. `tools/loop/prescreen.sh` — the run's own cuts are read once beside the run's own blocks (`cut_within_run`,
   `tools/loop/prescreen.sh:281`, from `.items[]` whose `result` is `cut` or whose `result` is `blocked` with
   `stopReason` `bound`, carrying their `parent` and the bound's key and value), and one pass after the candidate loop
   (`tools/loop/prescreen.sh:743`) joins the two readings into the verdict `epic-stalled`. A candidate that is
   otherwise queued is refused as `epic-stalled` when its container
   - recorded a child this run cut at a bound — the container spent its slot and collects nothing, so it takes no
     further one; the reason names the child and `KEY=VALUE` of the bound; or
   - has children the screen saw and every one of them is excluded with the decision verdict — the container cannot
     advance until a person answers, and the reason names those items (#938).

   The verdict is the engine's own word, like `container` and `unreadable`: it names the shape of a container's work
   and not a value of the project's vocabulary, so it writes no label and reaches no other verdict's branch. It clears
   by no write of ours — the decision arm when the label it names is gone, the bound arm with the run that recorded
   the cut.
2. `tools/loop/tests/fixtures/scenarios/epic-stalled/issues.json` — one container awaited by a decision, one container
   a bound will cut, one container with a single child, and one item in no container.
3. `tools/loop/tests/loop-cases.sh` — the two cases, declared in `LOOP_SHELL_CASES` and in the dispatch, each naming
   the files it can reach.
4. `tools/loop/tests/loop-mutations.sh` — record `938.1`: the arm that reads the child a bound ended, emptied, so the
   bound case goes red and no other case does.
5. `guidelines/Backlog.md:87` — the container paragraph gains the level between the container and the child: a
   container whose children cannot close is refused as a container, with the verdict that names what it waits for.

## What this does not change

- The child's own verdict. A child that awaits a decision is still `needs-decision`, and a refused child still carries
  its own clause: `epic-stalled` is the verdict of a child that would otherwise have been queued.
- The tracker. No label is written for the verdict, for the reason `blocked` writes none: the decision and the cut it
  names are already visible where the refusal read them.
- Which child of a container is worked when two of them are workable. The rule is about a container with *no* child
  that can close, and a container with two workable children is not refused — that would be a second policy (one slot
  per container per run), and this item did not ask for it.
