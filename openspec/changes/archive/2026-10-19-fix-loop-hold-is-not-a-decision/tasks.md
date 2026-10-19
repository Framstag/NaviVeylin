# fix-loop-hold-is-not-a-decision — tasks

- [x] 1.1 A case over a backlog whose only item is a hold with no decision label: red on HEAD (verdict
      `needs-decision`, a label write), green on the fix (verdict `held`, no write, no comment).
- [x] 1.2 A second case at the run level: the run stops as "nothing eligible", writes no decision label and no
      comment, and records the exclusion as held.
- [x] 2.1 `prescreen.sh`: the awaiting clause splits on the label; the bare hold gets the profile's
      `heldVerdict`.
- [x] 2.2 `tools/tooling.json`: the new role, declared so a foreign project can rename it and
      `check-portability` keeps forbidding project values in the engine.
- [x] 2.3 The `held` fixture scenario and the two expectations that changed in `prescreen#itemAwaitingADecision`
      / `run#awaitingDecisionIsNotEscalatedAgain`.
- [x] 3.1 Mutation 21.1 (`verdict="$held_verdict"` → `"$decision_verdict"`) goes red on
      `prescreen#aHoldWithNoDecisionIsNotADecision`, green after the restore.
- [x] 3.2 `tools/loop-selftest.sh` 78/78; `tools/check-portability.sh` OK (40 engines, 98 values).
- [x] 3.3 The drain of the twelve items that the old clause escalated was done before the fix landed, each with
      its reason recorded on the tracker.
