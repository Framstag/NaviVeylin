# fix-loop-decision-report — tasks

Parent requirement for every task below: `agent-issue-loop` — *"A pile of open decisions stops the run"* (the
cap it refuses on, and the report that has to announce it). Delta scenarios and the case that exercises each
are enumerated in §6.

## 1. The red cases (before the fix)

- [x] 1.1 `report#escalatedSkipNamesTheLabelWritten` — a skip the worker reports as `needs-diagnosis` is
      escalated (`agent/needs-decision` + `status/on-hold`), and the report's list of decisions the owner owes
      must name `#21` although the recorded verdict is `needs-diagnosis`; the number of items it names must
      equal the number of escalations the tracker recorded.
- [x] 1.2 `report#failedReviewEscalationIsNamed` — two failed review rounds end the item blocked with the
      decision label written, and the same list must name it.
- [x] 1.3 `report#aVerdictAloneIsNoEscalation` — a state built through `state.sh done`: only the item whose
      escalation recorded the label is named; an item that is merely blocked is not.
- [x] 1.4 Red on HEAD, against the engine as committed (`git archive HEAD tools/loop tools/issues` in
      `.pi/tmp/head177`, cases from the working tree): 1.1 `expected '#21', got 'none'`; 1.2
      `expected '#21', got 'none'`; 1.3 `loop: done: unknown argument --decision-label` plus
      `expected '#22', got 'none'`. **3 of 3 red; tally in `evidence/red-on-head.log`.**
      The issue's own measurement — the tracker labelled, the report saying `none` — is reproduced by 1.1/1.2.

## 2. The fix

- [x] 2.1 `state.sh`: `done --decision-label <label>` records the decision label the run's escalation wrote on
      the item (`escalatedLabel`, empty when nothing was escalated), with the comment naming why the verdict is
      a different fact (parent: the cap requirement's report half).
- [x] 2.2 `run.sh`: the four item-level escalation sites record the label they wrote — the kept worktree
      (`~L186`), the leftover branch (`~L349`), the skip (`~L507`) and the failed second review round
      (`~L524`) — each as `if loop_labels …; then escalated_label="$decision_label"; fi`, so the record cannot
      claim a label the tracker refused. `escalate_prescreen_decisions` is unchanged.
- [x] 2.3 `report.sh`: `decisions the owner owes` reads `[.items[] | select(.escalatedLabel != "")]`; the
      exclusion clause, the line's wording and the rest of the report are untouched.

## 3. The mutations (one per new predicate)

- [x] 3.1 `14.1` the list read from the verdict again (the defect's own shape) → 1.1 red.
- [x] 3.2 `14.2` the skip site recording no label → 1.1 red.
- [x] 3.3 `14.3` the failed-second-round site recording no label → 1.2 red.
- [x] 3.4 `14.4` the list naming every item instead of the escalated ones → 1.3 red.
- [x] 3.5 Every selected mutation went red and every restore went green:
      `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0` (`evidence/loop-mutations.log`).

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` — all 62 cases passed, offline, no token, no sandbox;
      `evidence/loop-selftest-final.log`. The cases the change touches beyond its own — the four escalation
      paths (`run#needsDecisionIsEscalatedWithTheWork`, `run#reviewRoundsCapAtTwo`,
      `run#leftoverBranchIsRecordedNotDropped`, `run#dirtyKeptWorktreeIsARecordedRefusal`), the two existing
      report cases and `state#initAndShowRoundTrips` — were run scoped first and are green.
- [x] 4.2 `bash tools/loop-mutations.sh --only '14.1 14.2 14.3 14.4' --timeout 60` — the four ids this change
      adds, every one red when mutated and green after its restore (`evidence/loop-mutations.log`). The full
      mutation set is **not** re-run: this change adds ids and changes no other mutation's target line, and
      the ids a sibling change owns are its own to run.
- [x] 4.3 No compiled file is touched, so no Gradle task runs: the structural check is the two shell gates
      above plus `bash -n` on the three scripts and the cases file (`guidelines/Build.md` §2, the scope
      block). Named exclusion: no build, no instrumentation test, no mutation-set run.
- [x] 4.4 `openspec validate fix-loop-decision-report --type change` exits 0 — recorded below.

## 5. The records

- [x] 5.1 This change: proposal, design, the delta on `agent-issue-loop`, these tasks and `evidence/`.
- [x] 5.2 The rollback path named in `proposal.md`, and the three unmeasured paths named in `design.md`'s
      "Not measured, and named" (a dry run, an item a person escalated, a failed tracker write).
- [x] 5.3 Issue #177 carries its closure or its escalation record, citing this archived change, the file that
      makes the defect impossible and the selftest that was run.

## 6. Delta scenarios → the case that exercises each

| delta scenario | case |
|---|---|
| The cap is reached | `run#decisionCapRefusesToStart` |
| An item needing a decision is escalated, not abandoned | `run#needsDecisionIsEscalatedWithTheWork` |
| The report names the decisions the run itself wrote | `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation` |

`openspec validate fix-loop-decision-report --type change`:
`Change 'fix-loop-decision-report' is valid` — exit 0.
