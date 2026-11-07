# The one red the gate of the rebased tree carries is the base's, not this change's

The gate of the rebased tree (`evidence/gate-rebased.log`, taken alone on 2026-10-24T05:25Z → 05:42:51Z) ends
`LOOP-GATE: tests=363 failures=1 errors=0 — not retained as a tally` over
`tools/loop-selftest.sh --from-git f2d27735818acd6d05095a9b331f4ee2bae1bb74` (937 s, and the suite that failed),
`bash tools/loop-mutations.sh --check` (2 s), `tools/loop-mutations-selftest.sh` (126 s),
`bash tools/gen-feature-list.sh --check-classification` (3 s) and `tools/check-doc-routes.sh` (1 s); its summary line
is `loop-selftest: 1 of 204 cases failed`. The failing case is `run#aWriterTheRunCanPlaceBlocksTheItem`: `a write the run can place in the child's own tree decides
the item: expected 'blocked', got 'done'`.

**It is not this change's.** The case was run alone in two clean detached worktrees with this change absent — one at
this change's rebase base and one at main's tip, which carries every merge that landed after it. `evidence/gate-base-red-alone.log`
holds both transcripts verbatim:

```
git worktree add -q --detach .pi/worktrees/baseline271-base f5f2a2a7      # this change's rebase base
git worktree add -q --detach .pi/worktrees/baseline271-maintip main       # 23c9f944, with #263 and #279 merged
bash tools/loop-selftest.sh --case 'run#aWriterTheRunCanPlaceBlocksTheItem'   # once in each worktree
```

Both print `a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'` and
`loop-selftest: 1 of 1 cases failed`. The case is #286's (the base commit `f5f2a2a7` is its own) and it reads the
outside-write attribution, not the commit a capped child gets. It is held by issues **#287** (which names it beside
`run#prescreenAnnouncesItselfBeforeItReturns`) and **#288** (its mechanism: red on a clean checkout, its outcome
flipping with the engine tree's own dirt); this change's reproduction was recorded as a comment on #288.

**What the same run shows about this change.** Its own two cases pass (`PASS
run#treeOfAChildTheCapEndedIsNotCommitted`, `PASS run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`), the
check the round-2 reviewer refused it for passes (`driver#everyCaseDeclaresTheFilesItCanReach`), all 125 declared
mutation records apply, the mutation harness's own 34 cases pass, and both documentation checks are green. So the
red is one case this change neither adds nor touches; the count it moves is the base's.
