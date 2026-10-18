# fix-loop-blocked-item-escalation — tasks

Parent requirements: `agent-issue-loop` — *"Closure follows a passing review and carries evidence"* (scenario
"A blocked item is never closed") and *"The run survives interruption"* (one scenario added). Delta scenarios
and the case that exercises each are enumerated in §6.

## 1. The red case (before the fix)

- [x] 1.1 `run#blockedItemIsEscalatedWithItsReason` — an item blocked for a reason that is not a failed review
      round (a child that reports success and commits nothing) must leave, in the same pass:
      `agent/needs-decision`, `status/on-hold`, one comment naming the reason ("base commit") and the branch
      (`fix/issue-21-`), no closure, and the label recorded with the item as `escalatedLabel`.
- [x] 1.2 Red on HEAD, against `tools/loop` as committed, with the case from the working tree: the tracker
      record holds only `--add-label agent/in-progress` and `--remove-label agent/in-progress` —
      `'agent/needs-decision' not found`. **1 of 1 red; tally in `evidence/red-on-head.log`.**

## 2. The fix

- [x] 2.1 `tools/loop/run.sh`, the `*)` arm of the outcome dispatch: `loop_labels escalate "$issue"` with
      `${blocked_reason}` as `--reason` and `"$item_dir, branch fix/issue-$issue-$slug"` as `--evidence`; the
      `if [ "${review_verdict}" = "FAIL" ]` guard and its `needs-decision` call removed, so one blocked
      outcome has one escalation (parent: "A blocked item is never closed").
- [x] 2.2 The same arm records the label the escalation wrote (`--decision-label`), so the report names the
      decision the run itself wrote (issue #177's invariant, unchanged).

## 3. The mutations (one per new predicate)

- [x] 3.1 `14.5` the escalate call made unreachable → `run#blockedItemIsEscalatedWithItsReason` red.
- [x] 3.2 `14.3` re-pointed at the reworded recording line (`# the block's own escalation`) →
      `report#failedReviewEscalationIsNamed` red.
- [x] 3.3 Both went red when mutated and green after the restore:
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0` (`evidence/loop-mutations.log`).

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` — all **63** cases passed (JDK 27, offline, no token, no sandbox);
      `evidence/loop-selftest-final.log`.
- [x] 4.2 The four cases the blocked arm's neighbours own, scoped and green:
      `run#childClaimWithoutCommitIsBlocked`, `report#failedReviewEscalationIsNamed`,
      `report#aVerdictAloneIsNoEscalation`, `run#reviewRoundsCapAtTwo` (`evidence/green-cases.log`).
- [x] 4.3 `bash tools/declared-cases-selftest.sh` — 12 passed, 0 failed, so the case names the delta declares
      resolve (`evidence/declared-cases.log`).
- [x] 4.4 No compiled file is touched, so no Gradle task runs: the structural check is the shell gates above
      (`guidelines/Build.md` §2, the scope block). Named exclusion: no build, no instrumentation test.
- [x] 4.5 `openspec validate fix-loop-blocked-item-escalation --type change` exits 0 — recorded below.
- [ ] 4.6 OWED — the **full** `bash tools/loop-mutations.sh` set was not run: the change is one shell delta
      and the ids it touches (`14.3`, `14.5`) were run scoped and are green both ways (§3.3). The full set is
      owed to whoever next owns `tools/loop/**`.

## 5. The records

- [x] 5.1 This change: proposal, design, the delta on `agent-issue-loop`, these tasks and `evidence/`.
- [x] 5.2 The rollback path named in `proposal.md`, and the three unmeasured paths named in `design.md`'s
      "Not measured, and named".
- [x] 5.3 Issue #174 carries `status/fixed-by` naming this archived change (the item does not carry
      `agent/eligible`, so its closure is the owner's — recorded as owed in the run's report).

## 6. Delta scenarios → the case that exercises each

| delta scenario | case |
|---|---|
| A passing item is closed with evidence | `run#oneItemOrderIsFixed` (unchanged) |
| Closure can be left to a person | `run#mayCloseNoLeavesTheIssueOpen` (unchanged) |
| A blocked item is never closed | `run#blockedItemIsEscalatedWithItsReason`, `run#childClaimWithoutCommitIsBlocked` |
| State is written only between agent processes | `labels#writesOnlyBetweenChildren` (unchanged; the new write happens in the dispatch, after the child exits) |
| An interrupted item leaves no item looking in progress | `labels#trapRemovesInProgressOnSignal` (unchanged) |
| A kept worktree a previous run left dirty is attributed and refused | `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeDiscardsWhatItNames`, `state#secondRunRefusesWithExit5` (unchanged) |
| A skipped item leaves no item looking in progress | `run#aSkipClearsTheInProgressMark` (unchanged) |
| A block's reason is not left to the run's state | `run#blockedItemIsEscalatedWithItsReason` |
| State is not committed | `state#initAndShowRoundTrips` (unchanged) |

`openspec validate fix-loop-blocked-item-escalation --type change`:
`Change 'fix-loop-blocked-item-escalation' is valid` — exit 0.
