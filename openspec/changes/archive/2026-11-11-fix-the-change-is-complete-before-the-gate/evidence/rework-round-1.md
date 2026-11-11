# Rework of review round 1 — the restore is decided by the item's verdict, not by the tally

The first gate run of this change was red with two failures, both on this change's own paths
(`tools/loop/tests/loop-cases.sh`, `tools/loop/run.sh`, `tools/loop/prompts/worker-tail.md`):

```
FAIL run#aCloseOverARedGateIsWaivedOrBlocked
     the waived close is the item's own verdict: expected 'done', got 'blocked'
FAIL phase#oneCallPrintsThePhaseState
     the phase's own archive step is printed: LOOP-PHASE: F — the gate's result, and the record of it
```

Both are the change read by what the change made false, and both were repaired in one pass:

1. **The restore fired before the judgement that makes an archive legitimate.** The restore block sat between the
   gate child and the tail and read the gate's tally alone, so a red tally the closing child **waived** with
   `LOOP-GATE-WAIVED` (issue #811, `run#aCloseOverARedGateIsWaivedOrBlocked`) was unarchived before the tail could
   report the waiver — the archive the close was taken under was gone and the item read `blocked`. The block moved to
   after the closing child's report: the predicate is the item's **verdict** (`blocked`), and only where no child
   reported one does the gate's own tally stand in (`gate_tally_state`, or a line that says the gate was not run).
   `worker-tail.md` Phase F item 2 now says the run restores the change once the verdict is `blocked`, and the delta's
   restore scenario carries the waived close as its second AND.
2. **A case asserted the step the change removed.** `phase#oneCallPrintsThePhaseState` asserted that phase F's
   checklist prints `Archive the change`; the tail archives nothing after this change (the change is completed before
   the gate), so the case now asserts the absence of that step and the evidence-only rule that replaced it.

Focused verification, all PASS, logs beside this file:

- `green-run-aCloseOverARedGateIsWaivedOrBlocked.log`
- `green-phase-oneCallPrintsThePhaseState.log`
- `green-gate-aRedGateRestoresTheChangeToItsUnarchivedName.log`
- `green-run-aRedGateIsBlockedByTheTail.log`

The gate over the corrected tree is this item's second and last run of it.
