# Tasks

Carried by the tracker item #919 (the declaration and the reader) and its child #913 (the within-run case). Both land
in one change because both are one reader of one relation: a split would have left #913's run repeating what it
measured until the second half arrived.

## 1. The tracker's edge

- [x] 1.1 Write the dependency edge from the create path: `--blocked-by <number>` and a finding's `.blockedBy` (one
  number or a list), deduplicated, written with `addBlockedBy` after the filing and reported in the result. Parent
  spec: `issue-backlog` — A dependency is the tracker's own relation. Case: `create#writesTheDependencyItWasGiven`.
  The read is `tools/issues/lib.sh`'s `issues_issue_node_id`, the write its `issues_blocked_by_add`.
- [x] 1.2 Refuse what would block nothing, before the item exists: a predecessor the tracker does not hold, a
  non-numeric value, and the item's own parent. Parent spec: `issue-backlog` — An edge that would block nothing is
  refused before the item exists. Case: `create#writesTheDependencyItWasGiven`. The predecessor's handle is read
  before `gh issue create`, so the refusal leaves no item behind; the mutation that cannot be atomic — `addBlockedBy`
  failing after the filing — is reported with the item's number.
- [x] 1.3 Read the edge in the pre-screen: `blocked_by_of()` answers the first `blockedBy` node whose state is not
  `CLOSED`, and the verdict is `blocked-by:#N` with `blockedBy:#N` as its evidence and no label written. Parent spec:
  `agent-issue-loop` — A blocked candidate is not work. Case: `prescreen#aBlockedCandidateWaitsForItsPredecessor`.

## 2. The block this run recorded

- [x] 2.1 Hold the change-mates of an item the run recorded `blocked`: the reader's second source is `.items[]`'s
  `result == "blocked"` with the candidate's own change, recorded as `run-blocked:#N`. Parent spec:
  `agent-issue-loop` — A candidate shares the change a blocked item named. Case:
  `prescreen#theRunHoldsWhatItsBlockedItemShares`. The filter excludes `result: done` (a landing is not a block) and
  carries `state.sh`'s own `stopReason: bound` exclusion.
- [x] 2.2 The verdict is the profile's, and it is optional: `blockedVerdict` in `tools/tooling.json`, declared in
  `LOOP_OPTIONAL_ROLE_KEYS` beside `shapingVerdicts`, with an empty value and an omitted key blocking nothing. Parent
  spec: `issue-backlog` — The profile declares the verdict a blocked candidate gets. Case:
  `prescreen#anEmptyBlockedVerdictBlocksNothing`. The foreign fixture declares `waiting-on`, so the portability run
  cannot pass by accidental similarity.

## 3. What the surrounding engine does with the verdict

- [x] 3.1 The exclusion is an exclusion and not an attempt: the candidate reaches `.excluded[]` and never `.queue[]`, so
  it can be no `items[]` entry either — `state.sh next`'s refusal streak reads the items a run recorded `blocked`,
  and a blocked candidate spends no turn and adds no streak. `run.sh` prints it with the other excluded verdicts
  (`jq -r '.excluded[] | …'`), and `escalate_prescreen_decisions` does not escalate it (it selects only the decision
  verdicts), because the tracker already shows the edge. Parent spec: `agent-issue-loop` — A blocked candidate is not
  work. No new case: the existing `run#` cases and `state#` cases read the verdict list, and this verdict is another
  member of it.
- [x] 3.2 `tools/loop/shared.tsv` declares the one name the reader made shared: `blocked`, assigned by `report.sh` and
  read by `prescreen.sh`. Parent spec: `agent-issue-loop` — Every name two engine files share is declared in one
  place. Case: `driver#theSharedNamesAreDeclared`.

## 4. Falsification

- [x] 4.1 Empty the role in the project's profile and run the tracker-edge case: it reports
  `expected 'blocked-by:#90', got 'eligible'`. Restored, and the case is green.
- [x] 4.2 The write's case is red on the create path without the flag — `--blocked-by` is an unknown option there —
  and green with it, including the two refusals that write nothing.

## 5. Harness repair found on the way

- [x] 5.1 Three mutation records of the `898.x` block carried an apostrophe inside their single-quoted description
  ("the run's item #898"), which split each record into a stray `item` element; `mutation#everyDeclaredRecordApplies`
  was red on the base for it. The three descriptions now read "item #898 of the run", and the record set is green
  again. This is a harness repair, not part of the relation: it is named here because the change's gate could not be
  green while it stood.