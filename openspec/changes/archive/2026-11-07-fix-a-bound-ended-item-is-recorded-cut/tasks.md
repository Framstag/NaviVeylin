# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red cases

Requirement: "An item the run's own bound cut is recorded `cut`, not `blocked`", scenarios "A child the turn bound
ended is recorded cut, not blocked" and "A child the item's own wall clock ended is recorded cut, not blocked".

- [x] 1.1 Rewrite `run#childPastItsTurnBoundIsBlocked` as `run#childPastItsTurnBoundIsCut`: the child takes its role's
      three turns past a recorded bound of three and is ended there, and the case asserts `result=cut`, `verdict=cut`,
      `stopReason=bound`, the count in the note and the sentence that leaves the attempt to the next run, no
      `agent/needs-decision` label, no close, and a report that counts the item as a cut (`blocked 0 · cut 1`) and
      names it on the run's own bound line. Verify: on `6e9319f1` the case fails with
      `expected 'cut', got 'blocked'` (2026-11-07T00:37:14Z, `evidence/red-on-HEAD.xml`).
- [x] 1.2 Rewrite `run#childOutlastingTheWallClockIsBlocked` as `run#childOutlastingTheWallClockIsCut`: the same
      child-cap fixture, asserting `result=cut`, `verdict=cut`, `stopReason=bound`, the cap named in the note, the
      resume sentence, and no decision label. Verify: on `6e9319f1` it fails with `expected 'cut', got 'blocked'`
      (2026-11-07T00:37:14Z, `evidence/red-on-HEAD.xml`).
- [x] 1.3 `run#childPastItsTurnBoundIsCut`'s first-line `# files:` declaration names `tools/loop/report.sh`, because
      the case reads the report's cut row through `run_report` and the reach table owes that path (`run_report` →
      `tools/loop/report.sh`). Verify: `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'`
      green — it refused the first version of this case with
      `a case does not declare what it can be reached by: run#childPastItsTurnBoundIsCut:tools/loop/report.sh(run_report)`
      (2026-11-06 gate, `.pi/issue-loop/items/790/gate.log`). The green reading is the gate child's own case step,
      which has not run yet.

## 2. The cases the result realigns

- [x] 2.1 `run#childIsEndedAtTheRunsDeadline`: the deadline-ended worker is recorded `cut` (was `blocked`); the rest
      of the case — the `LOOP-DEADLINE`/`LOOP-TIMEOUT` transcript, no label, the deadline as the run's stop reason —
      is unchanged. Verify: green on the change.
- [x] 2.2 `run#treeOfAChildTheCapEndedIsNotCommitted`: `cut` instead of `blocked`, beside the assertions that already
      hold — `end-item skipped`, the branch at the base, the half-applied file still in the tree — plus the note
      naming the leftover patch. Verify: green on the change.
- [x] 2.3 `run#theRecordedZeroKeepsTheTurnBoundOff`: the wall clock is what ends the child with the turn bound off,
      so the item is `cut` with the same reason a bound gives. Verify: green on the change.
- [x] 2.4 `run#childPastItsContextBoundIsBlocked` rewritten as `run#childPastItsContextBoundIsCut`: the context bound
      is the same arm and the same `bound` mechanism, so the item is `cut`/`cut` with `stopReason=bound`. Verify:
      green on the change; red on the base with `expected 'cut', got 'blocked'`.
- [x] 2.5 `run#theRecordedZeroKeepsTheContextBoundOff`: `cut` instead of `blocked`, the wall clock being what ends
      the child with the context bound off. Verify: green on the change.
- [x] 2.6 `run#aVanishedWriterEndsTheWait`: `cut` instead of `blocked`. Its own comment already read the vanished
      writer as "a bound the run enforced, not a refusal of the change" and asserted `stopReason=bound` and no
      escalation; `blocked` was the one field left contradicting them. Verify: green on the change.
- [x] 2.7 The three case renames/repointings reach the case list, the dispatch and the shipped spec's `Case:` lines
      (`tools/loop-cases.sh:192`, `:357`, `:572`, `:736`; the delta's `Case:` lines repoint
      `openspec/specs/agent-issue-loop/spec.md:69`, `:2343`, `:3227`). Verify:
      `tools/loop-selftest.sh --list` names `run#childPastItsContextBoundIsCut` and no `...IsBlocked` variant.
- [x] 2.8 `run#aRotatedStreamStillCountsTheTurnsItDropped` (#802, landed after this plan) and
      `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` assert `cut`: both end a child on the same arm and the
      same `bound` mechanism, so both were `blocked` by the result the change moves. Verify: green on the change; the
      first was the scoped suite's only red before its realignment (2026-11-07T00:43–00:50Z, 1 of 276 failed), and the
      second's `stopReason=bound` assertion reddens on mutation 92.3 (2026-11-07T00:53:25Z,
      `evidence/mutations-92.3.log`).

## 3. The engine

- [x] 3.1 `tools/loop/run.sh`: `outcome="cut"` when `stop_reason` is `bound`, beside the pre-gate cut, with the
      comment that names the one mechanism the two arms now share.
- [x] 3.2 `tools/loop/run.sh`, the `cut` arm: pass `--stop-reason "$stop_reason"`, and add the sentence a bound-ended
      item's note carries — the attempt is left for the next run rather than asked about.
- [x] 3.3 `tools/loop/run.sh`, the block arm: drop the escalation guard that excused a block caused by the run's
      deadline or a bound, and the note branch that appended the resume sentence to such a block. Both causes leave
      a `cut` above the arm, so every block it records is escalated, and the note it writes says only what a block
      owes.
- [x] 3.4 `tools/loop/state.sh`: accept `--stop-reason bound` with `--result cut`, and nothing else with `cut`; the
      refusal of `refused`/`unmergeable` on a non-block is unchanged. Verify: `state#aBlockedItemNamesTheMechanismThatStoppedIt`
      green (it still refuses a mechanism on a `done`).
- [x] 3.5 `tools/loop/report.sh`: the bound row is `select(.result == "cut")` again, and its comment says the row
      reads the result, not the mechanism.
- [x] 3.6 `tools/loop-mutations.sh`: 23.1 and 81.1 renamed to the renamed cases; 68.2 and 68.6 retired with the guard
      they quoted, and 92.1–92.3 added for the result, the mechanism the item's own child keeps beside it and the same
      mechanism at the rework arm — the ids `85.x`–`91.x` are taken on the base by #782, #794, #796, #803, #802 and
      #797. Verify: `bash tools/loop-mutations.sh --check` (262 records, every one applies); 92.1, 92.2, 92.3, 81.1
      and 23.1 red-when-mutated and green again on restore (2026-11-07, `evidence/mutations-92.log`,
      `evidence/mutations-92.3.log`).

## 4. The prose the rule lives in

- [x] 4.1 The spec delta's six modified requirements — "An item the run's own bound cut is recorded `cut`, not
      `blocked`", "A block records the mechanism that stopped it", "A child the run ended itself leaves no commit",
      "One command starts a budgeted run, with no dialogue" (its wall-clock scenario's result and case), "The bound on
      a child is priced in what it spends, not in how many turns it takes" (its context bound) and "A wait ends when
      the writer of the record it waits for is gone" — each carries every scenario name the shipped spec has, the
      `blocked` ones say `cut`, and the legacy `blocked` + `bound` pair is stated as the rule `state.sh:289` holds.
      Verify: `openspec validate fix-a-bound-ended-item-is-recorded-cut` (valid, 0 issues) and
      `tools/check-openspec-hygiene.sh`, both green (`evidence/change-checks.log`).
- [x] 4.2 `guidelines/Backlog.recipes.md`, section 7b (`:301-316`) and section 7e (`:768-790`): the paragraphs that said a
      bound-ended item is recorded `blocked` say `cut`, name the child as well as the round, and keep the measured
      figures they carried. Verify: `tools/check-doc-routes.sh` and `tools/check-todo-references.sh`, both green
      (`evidence/change-checks.log`).

## 5. Verification

- [x] 5.1 Both red cases pass on the change, and the fourteen realigned cases pass beside them
      (the scoped suite's own tally, `evidence/scoped-suite.xml`):
      `run#childPastItsTurnBoundIsCut`, `run#childOutlastingTheWallClockIsCut`,
      `run#childIsEndedAtTheRunsDeadline`, `run#treeOfAChildTheCapEndedIsNotCommitted`,
      `run#theRecordedZeroKeepsTheTurnBoundOff`, `run#treeOfACapEndedChildIsKeptAsAPatch`,
      `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`, `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`,
      `state#aBlockedItemNamesTheMechanismThatStoppedIt`, `state#aBlockAtTheRunsBoundIsNoRefusal`,
      `run#childPastItsContextBoundIsCut`, `run#theRecordedZeroKeepsTheContextBoundOff`,
      `run#aVanishedWriterEndsTheWait`, `run#aRotatedStreamStillCountsTheTurnsItDropped`. The scoped suite does not
      select `driver#everyCaseDeclaresTheFilesItCanReach` (it declares `tools/loop-cases.sh tools/loop-selftest.sh`,
      none of the three engine paths), so the declaration it guards is read by the gate's own case step. Verify: the
      scoped suite over the three engine paths this change
      touches (`./tools/loop-selftest.sh --files "tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh"`,
      276 of the 345-case set) is green after the realignment — `tests=276 failures=0 errors=0`, 363.02 s,
      2026-11-07T00:53–00:59Z — and the gate the run's child applies re-runs it on the committed tree, together with
      the two assertions this pass added to `run#childIsEndedAtTheRunsDeadline` (`stopReason=bound`) and
      `run#childPastItsTurnBoundIsCut` (no comment), which that tally predates.
- [x] 5.2 The gate is **not** this worker's (the run deferred it): the diff is left for the gate child, and no
      `tools/loop/gate.sh` run is claimed here.
