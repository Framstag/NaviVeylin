# fix-loop-start-preflight-and-branch-verdict — tasks

Parent requirement for every task below: `agent-issue-loop` — *"Version control belongs to the run's script,
not to the agent"* (the previous-attempt scenario) and *"The run survives interruption"* (the kept worktree).
Delta scenarios and the case that exercises each are enumerated in §6.

## 1. The red cases (before the fix)

- [x] 1.1 `prescreen#previousAttemptBranchIsADecision` — a candidate whose branch carries a commit the run's
      base does not must come out of the pre-screen as the profile's decision value, naming the branch as its
      evidence, with the branch that carries nothing of its own left eligible.
- [x] 1.2 `run#leftoverBranchIsRecordedNotDropped` — rewritten to the pre-screen path: the second run must
      record the item in `excluded[]`, escalate it (`agent/needs-decision` + `status/on-hold`, the branch
      named), start no agent process, close nothing, and say "nothing eligible" with exit 0.
- [x] 1.3 `run#dirtyKeptWorktreeIsARecordedRefusal` — a kept worktree the previous run left dirty must refuse
      with exit 5, before any `pre-screen:` line, naming the files and #21 (the branch marker), recording it
      as a blocked item, escalating it, printing the report, and leaving the tree untouched.
- [x] 1.4 `run#resetWorktreeDiscardsWhatItNames` — the same state with `--reset-worktree` must name what it
      discards, discard it, leave the kept worktree clean, keep the previous branches, and then run normally.
- [x] 1.5 `run#branchThatAppearsWhileTheRunIsOpenIsRecorded` — the authoritative start check's guard: a branch
      that appears while the run is open is refused, recorded, escalated and not closed, and the item is
      escalated once, not again when the pre-screen runs a second time.
- [x] 1.6 Red on HEAD, against a copy of `tools/loop` as committed (`git archive HEAD tools/loop`), cases from
      the working tree: 1.1 `expected 'needs-decision', got 'eligible'`; 1.2 `'nothing eligible' not found`;
      1.3 the marker names the run's branch (`loop-20261018…`); 1.4 `--reset-worktree` is an unknown argument
      (`expected '0', got '2'`); 1.5 green on HEAD by design — it guards #170's path. Plus the two defects'
      own measured commands: run 2 exit 5 with `items recorded: 0` and no report, and a pre-screen that queues
      an item whose branch carries a previous attempt. **4 of 5 red; tally in
      `evidence/red-on-head.log`.**

## 2. The fix

- [x] 2.1 `run.sh`: the start-time pre-flight on the kept worktree, before `prescreen.sh` and before the lock;
      it stands aside when the run lock exists, derives the item from the branch marker, records the refusal
      as a blocked item, escalates it, prints the report and exits 5 (parent: the interruption requirement).
- [x] 2.2 `run.sh`: `--reset-worktree` (start-time repair, names what it discards, `reset --hard` + `clean -fd`)
      and `end_run()`, which keeps `--clean-worktree` the end-of-run form on every path where the run ends.
- [x] 2.3 `run.sh`: `escalate_prescreen_decisions` — every exclusion whose verdict is the profile's
      `decisionAgent` value is escalated once, under the item, before the first item is picked.
- [x] 2.4 `prescreen.sh`: `previous_attempt_branch_of` — `git for-each-ref` on `refs/heads/fix/issue-<n>-*`
      plus `git merge-base --is-ancestor` against the run's base, as one more mechanical verdict beside the
      citation sensor.
- [x] 2.5 `state.sh`: `cmd_queue`'s exclusions carry `evidence`, so the run cites the branch it escalated.
- [x] 2.6 `worktree.sh`: `start-item` writes the item's branch into the worktree's branch marker; `create`'s
      own refusal stays for `create` used on its own, with the pre-flight named in its comment.

## 3. The mutations (one per new predicate)

- [x] 3.1 `13.1` the pre-screen's branch query never true → `prescreen#previousAttemptBranchIsADecision` red.
- [x] 3.2 `13.2` the run escalates no decision verdict → `run#leftoverBranchIsRecordedNotDropped` red.
- [x] 3.3 `13.3` the pre-flight never fires → `run#dirtyKeptWorktreeIsARecordedRefusal` red.
- [x] 3.4 `13.4` the refusal is not recorded under the item → the same case red.
- [x] 3.5 `13.5` the repair discards nothing → `run#resetWorktreeDiscardsWhatItNames` red.
- [x] 3.6 `13.6` the pre-flight stops reading the lock → `state#secondRunRefusesWithExit5` red (a live run's
      tree is not leftovers).
- [x] 3.7 `13.7` the end-of-run form is dropped → `worktree#keepsByDefaultAndRemovesOnRequest` red.
- [x] 3.8 `12.7` re-pointed at `run#branchThatAppearsWhileTheRunIsOpenIsRecorded`: the start's own refusal
      being recorded and escalated.
- [x] 3.9 Every selected mutation went red and every restore went green:
      `red-when-mutated: 8 · not-noticed: 0 · restore-failures: 0`.

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` — all 59 cases passed (JDK 27, offline, no token, no sandbox);
      `evidence/loop-selftest-final.log`. The two cases the change touches beyond its own —
      `state#secondRunRefusesWithExit5` and `worktree#keepsByDefaultAndRemovesOnRequest` — were red in the
      first green run and are green with the lock read and `end_run` (§D1, §D5).
- [x] 4.2 `bash tools/loop-mutations.sh` — the full set, every mutation red when mutated, green after the
      restore: `evidence/loop-mutations-final.log`.
- [x] 4.3 `bash tools/loop-mutations-selftest.sh` — the harness's own probes, green.
- [x] 4.4 `bash tools/check-portability.sh` — no project value in an engine, so the new verdict and the new
      reason text name no vocabulary value of their own; `evidence/check-portability.log`.
- [x] 4.5 No compiled file is touched, so no Gradle task runs: the structural check is the four shell gates
      above (`guidelines/Build.md` §2, the scope block). Named exclusion: no build, no instrumentation test.
- [x] 4.6 `openspec validate fix-loop-start-preflight-and-branch-verdict --type change` exits 0 — recorded
      below.

- [x] 4.7 The finish, 2026-10-18 — the change was left unarchived when the run that made it was killed while
      syncing the delta, so the sync was completed and the whole change re-verified: the "State is not
      committed" scenario's `**Case**:` line was the only part of the merge still missing, and the main spec
      now differs from (the delta applied to) HEAD in nothing else, with `openspec validate --specs` green
      (175 passed, 0 failed; the long-requirement warnings are pre-existing). Re-run: the five cases this
      change owns, scoped — `prescreen#previousAttemptBranchIsADecision`,
      `run#leftoverBranchIsRecordedNotDropped`, `run#dirtyKeptWorktreeIsARecordedRefusal`,
      `run#resetWorktreeDiscardsWhatItNames`, `run#branchThatAppearsWhileTheRunIsOpenIsRecorded` — green;
      `bash tools/loop-selftest.sh` all **59** cases passed in 138 s (`evidence/loop-selftest-final.log`);
      `tools/loop-mutations.sh --only '13.1 13.2 13.3 13.4 13.5 13.6 13.7 12.7' --timeout 90` —
      `red-when-mutated: 8 · not-noticed: 0 · restore-failures: 0` (3 min 20 s,
      `evidence/loop-mutations-final.log`); `tools/loop-mutations-selftest.sh` green
      (`evidence/loop-mutations-selftest.log`); `tools/check-portability-selftest.sh` 26 cases
      (`evidence/check-portability-selftest.log`) and `tools/declared-cases-selftest.sh` 12 passed
      (`evidence/declared-cases-selftest.log`). One run of the full mutation set was **not** repeated in the
      finish: the ids this change adds were, and 3.9 records the first pass's
      `red-when-mutated: 8` beside the full set's 26.

## 5. The records

- [x] 5.1 This change: proposal, design, the delta on `agent-issue-loop`, these tasks and `evidence/`.
- [x] 5.2 The rollback path named in `proposal.md`, and the two unmeasured paths named in `design.md`'s
      "Not measured, and named".
- [x] 5.3 Issue #173 and issue #175 carry the closure or the escalation record that cites this archived
      change, the file that makes the defect impossible and the selftest that was run.

## 6. Delta scenarios → the case that exercises each

| delta scenario | case |
|---|---|
| One working tree per run | `worktree#seedsAllFourInputs` |
| One branch per item, from the run's base | `worktree#oneBranchPerItemOffTheBase` |
| A commit carries the run's own identity | `run#commitsWithoutAnAmbientIdentity` |
| A branch that carries no work of its own is reused | `worktree#reusesABranchThatCarriesNoWorkOfItsOwn` |
| A branch that carries a previous attempt is refused, named and recorded | `prescreen#previousAttemptBranchIsADecision`, `run#leftoverBranchIsRecordedNotDropped`, `run#branchThatAppearsWhileTheRunIsOpenIsRecorded` |
| A stale base stops the next item | `worktree#refusesWhenMainAdvanced` |
| Nothing is pushed or opened by default | `worktree#noPushWithoutTheFlag` |
| State is written only between agent processes | `labels#writesOnlyBetweenChildren` |
| An interrupted item leaves no item looking in progress | `labels#trapRemovesInProgressOnSignal` |
| A kept worktree a previous run left dirty is attributed and refused | `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeDiscardsWhatItNames`, `state#secondRunRefusesWithExit5` |
| A skipped item leaves no item looking in progress | `run#aSkipClearsTheInProgressMark` |
| State is not committed | `state#initAndShowRoundTrips` |

`openspec validate fix-loop-start-preflight-and-branch-verdict --type change`:
`Change 'fix-loop-start-preflight-and-branch-verdict' is valid` — exit 0.
