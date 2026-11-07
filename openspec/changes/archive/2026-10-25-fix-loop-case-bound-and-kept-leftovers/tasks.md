# Tasks — the loop bounds a case, and keeps what a killed child left

Parent capability: `agent-issue-loop`. The requirements this change adds are "A self-test case is held to a bound
of its own", "A case's own output reaches the suite's log while it runs" and "A run that takes a signal takes its
heartbeat with it"; the two it modifies are "A child the run ended itself leaves no commit" and "A start the
worktree refuses mid-run names the item whose work it holds".

## 1. The cases, red on HEAD before their fix

- [x] 1.1 The bound's case, in the style of `driver#theSuiteRunsItsCasesInAPool`: a nested suite whose
      `tools/loop-cases.sh` declares one stub case that `sleep 600`s, run with `LOOP_CASE_MAX_SECONDS=2`.
      Asserts `FAIL <case> (exceeded 2 s)` on the suite's output, the case's log kept, the suite's summary
      carrying the failure and its status non-zero.
      Parent: "A self-test case is held to a bound of its own".
      **Verify**: on HEAD `timeout 60 bash tools/loop-selftest.sh --case
      'driver#aCaseThatBlocksIsEndedByItsOwnBound'` → exit 124, no `FAIL` line, the nested suite still running
      (`evidence/red-on-head-62.1.log`); after 2.1 the same command → the `FAIL` line, exit 0.
- [x] 1.2 The bound's guard, same nested suite with a stub case that exits 0 after 1 s under the same
      `LOOP_CASE_MAX_SECONDS=2`: asserts the case's own verdict is reported and the bound's line is not.
      Parent: "A self-test case is held to a bound of its own".
      **Verify**: green on HEAD by construction (this case exists to go red under mutation `62.2`), recorded in
      `evidence/green-guard-62.2.log`.
- [x] 1.3 The streamed case line: the same nested suite with a stub case that prints one line and then blocks.
      Asserts that line appears on the *suite's own output*, prefixed with the case's name, before the case ends.
      Parent: "A case's own output reaches the suite's log while it runs".
      **Verify**: on HEAD the prefix is absent from the suite's output (`evidence/red-on-head-62.3.log`); after
      2.2 it is there (`evidence/green-62.3.log`).
- [x] 1.4 The signal's orphan: a fixture run with a blocked child (`PI_STUB_WAIT`, as
      `labels#trapRemovesInProgressOnSignal` builds it), `SIGTERM` to the run, then `loop_pids_under` the case's
      tree must be empty. Do **not** release the child through the fifo — the point is the run's own processes.
      Parent: "A run that takes a signal takes its heartbeat with it".
      **Verify**: on HEAD a process remains (the heartbeat subshell, its `cmd` still `run.sh --items 1 …`) —
      `evidence/red-on-head-62.4.log`; after 2.3 none (`evidence/green-62.4.log`).
- [x] 1.5 The cap-ended patch: a fixture with a small `LOOP_CHILD_MAX_SECONDS` whose stub worker writes a
      tracked edit and an untracked file into the change root. Asserts
      `<item dir>/leftover-from-killed-run.patch` exists, carries both, and that the item's `note` names it.
      Parent: "A child the run ended itself leaves no commit".
      **Verify**: on HEAD no patch exists (`evidence/red-on-head-62.5.log`); after 2.4 both hunks are in it
      (`evidence/green-62.5.log`).
- [x] 1.6 The refused start's patch: a worktree left dirty by an item whose branch marker names it, then a run
      that tries to start another item. Asserts the patch is written into *that* item's directory and the stop
      reason names the path.
      Parent: "A start the worktree refuses mid-run names the item whose work it holds".
      **Verify**: on HEAD the stop reason names `--reset-worktree` and no patch (`evidence/red-on-head-62.6.log`);
      after 2.4 the patch exists and the reason names it (`evidence/green-62.6.log`).

## 2. The fixes

- [x] 2.1 `tools/loop/loop.conf` gains `LOOP_CASE_MAX_SECONDS=900`, validated like `LOOP_CHILD_MAX_SECONDS`, and
      `tools/loop-selftest.sh` starts a **watcher** beside every case: it writes the case's `status` and `bound`
      and ends the case's process group when the bound passes, so the pool records
      `FAIL <case> (exceeded <n> s)` with the case's own log kept, and the next case starts. The watcher is a job
      of the suite's shell (it ends the case's group and cannot be in it), it is ended by `finish_case` while the
      slot directory is still its own (a token keeps a late firing out of a reused slot), and its three standard
      descriptors are `/dev/null` — the shape a suite captured in `$( … )` needs.
      Parent: "A self-test case is held to a bound of its own".
      **Verify**: 1.1 and 1.2 green; `bash -n tools/loop-selftest.sh`; `driver#theSuiteRunsItsCasesInAPool`,
      `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` and `driver#aSecondSuiteInOneRepositoryIsRefused`
      still `PASS`.
- [x] 2.2 `tools/loop-selftest.sh` — the case's stdout/stderr is relayed as it arrives: each line appended to
      `<dir>/out` unmodified and printed to the suite's own output as `<case>| <line>`, with the file repeated (and
      headed) only for a failed case. The relay reads a fifo of the case's own and joins the case's process group,
      and the case runs in the foreground of its subshell so its `status` never waits for the relay — the pipeline
      shape first tried here held the whole `driver#*` group at its 900 s bound, because a nested suite's
      descendants inherited the outer relay's pipe. The relay gets a bounded grace (1 s) **inside the case's own
      subshell, before the status is written**: a relay killed first loses the tail of a failed case's transcript
      (`FAIL` with nothing under it was measured), and a grace in `finish_case` instead delays the sweep that ends
      what a case left — which made the sweep case see its leaked process still running.
      Parent: "A case's own output reaches the suite's log while it runs".
      **Verify**: 1.3 green; `driver#everyCaseDeclaresTheFilesItCanReach` green; a case's `<dir>/out` is
      byte-identical to what it was before the change (assert in 1.3).
- [x] 2.3 `tools/loop/run.sh` — `run_child` sets a global `active_hb` with the heartbeat's pid and clears it
      after `wait "$hb"`; `on_exit` ends it with `loop_end_pid` before it unlocks the state, and leaves the trap
      path otherwise unchanged (the interrupted item, the label, the unlock keep their order).
      Parent: "A run that takes a signal takes its heartbeat with it".
      **Verify**: 1.4 green; `labels#trapRemovesInProgressOnSignal` still `PASS` (three runs, as its own repair
      requires); `run#interruptedRunEndsTheChildItStarted` still `PASS`.
- [x] 2.4 `tools/loop/run.sh` — one helper (`keep_leftovers`) writes the leftovers of a stopped run: `git diff
      HEAD` (staged and unstaged — a refused `end-item` leaves its work staged, which a plain `git diff` does not
      show; measured on 1.6), `diff --no-index /dev/null <file>` per untracked file, the ignored files under the
      profile's `LOOP_UNTRACKED_RECORD_DIR` (new key, default empty), a header naming every other ignored path it
      did not copy, and the same for any file above 256 KiB. It is called on the cap/deadline path (before the next
      item, with `LOOP-LEFTOVERS:` appended to the child's transcript) and on the refused-start path (before the run
      ends, with the path in the stop reason), into the resolved item's
      `.pi/issue-loop/items/<n>/leftover-from-killed-run.patch`. Nothing is committed and nothing is discarded.
- [x] 2.5 `tools/loop-cases.sh` — one helper (`wait_for_stub_child`, 60 s at 0.1 s per tick, with the host fact
      named beside it) replaces the eleven stub-readiness waits, four of which a loaded host made read as "the stub
      child never started" (two Gradle compiles of a second session on this host, 2026-10-25). Nothing else about
      those cases changes, and their assertions are untouched.
      Parent: none — a host bound in the cases, which the guideline names beside the run's own bounds.
      **Verify**: `bash -n tools/loop-cases.sh`; `run#childOutlastingTheWallClockIsBlocked` and
      `run#treeOfAChildTheCapEndedIsNotCommitted` → `PASS` twice each under the load that had failed them
      (`evidence/affected-cases.log`).
      Parent: "A child the run ended itself leaves no commit" and "A start the worktree refuses mid-run names the
      item whose work it holds".
      **Verify**: 1.5 and 1.6 green; `run#treeOfAChildTheCapEndedIsNotCommitted` and
      `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` still `PASS`; `bash -n tools/loop/run.sh`.

## 3. The falsification

- [x] 3.1 `tools/loop-mutations.sh` — records `62.1`–`62.6`, one per invariant: the bound's check, the verdict of
      a case inside its bound, the relay's prefix, `on_exit`'s call that ends the heartbeat, the cap path's patch
      call, the refused-start path's patch call. Each names the case it must turn red.
      Parent: all five requirements.
      **Verify**: `bash tools/loop-mutations.sh --only '62.1 62.2 62.3 62.4 62.5 62.6'` → each `RED` naming its
      case, each restored green, `red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0`, exit 0
      (`evidence/mutations-62.log`); `bash tools/loop-mutations.sh --check` → every record applies. The ids were
      `61.1`–`61.6` until this change met the branch where `61.x` already belonged to another change (#244): the
      block was renumbered `62.1`–`62.6` at the merge, and the transcripts were re-run under the new ids.

## 4. The profile and its portability

- [x] 4.1 `tools/loop/loop.conf` — the two new keys (`LOOP_CASE_MAX_SECONDS`, `LOOP_UNTRACKED_RECORD_DIR`) with
      the comment style of the keys beside them; `PORTING.md`'s list of keys a foreign project edits gains both.
      Parent: the two requirements that name a profile key.
      **Verify**: `bash tools/check-portability.sh` → exit 0; `bash tools/check-portability-selftest.sh` → all
      cases pass; `bash tools/loop-mutations.sh --check` still green.

## 5. The guideline

- [x] 5.1 `guidelines/Backlog.md`, section "7. The loop" — the case bound and what it ends, the streamed case line,
      the heartbeat that ends with a signalled run, and the patch a stopped run keeps beside the tree it does not
      touch, each with the incident's measurement (56 min, 222 silent `PASS` lines, the orphan seen the next day,
      the hand-reconstructed patch of #260).
      Parent: all five requirements.
      **Verify**: `bash tools/check-doc-routes.sh` → exit 0; `bash tools/check-todo-references.sh` → exit 0; the
      section is still routed from `AGENTS.md`'s Documentation Map row for the loop.

## 6. Verification

- [x] 6.1 Re-run every case the delta reaches: the six new ones, the pool's three, the child-lifecycle cases
      (`run#childReportsWhileItWaits`, `run#childOutlastingTheWallClockIsBlocked`, `run#childIsEndedAtTheRunsDeadline`,
      `run#detachedProcessInTheWorktreeIsEndedByTheCap`, `run#interruptedRunEndsTheChildItStarted`) and
      `labels#trapRemovesInProgressOnSignal`.
      **Verify**: every one `PASS`: `failed=0 of 20`, each with its wall clock, in `evidence/affected-cases.log`
      (the two 20 s-ready cases were widened to a 60 s host bound first, and the one attempt each failed before
      that is recorded there beside the re-run rather than hidden).
- [x] 6.2 Run the gate from the change root: `./tools/loop/gate.sh`, and copy every tally into `evidence/gate/`.
      Measured **in an isolated worktree of the base** (`git worktree` at `25887f33` with this change's seven files
      copied in, outside `.pi/` so the portability selftest runs), because a second session has Kotlin/CMake edits
      in the shared checkout: the shared measurement (`evidence/gate-shared-tree-20261025.log`) ran a compiled
      suite that this delta does not owe and was red on that session's work, while the isolated one owes no
      compiled suite at all.
      **Verify**: the gate's own line reports 451 tests, 0 failures, 0 errors — `451` is the sum over the suites
      and is carried by no single tally, so the per-suite numbers are read from `evidence/gate/` (every XML
      `failures="0" errors="0"`) and the aggregate from `evidence/gate.log`, whose suites are
      `tools/loop-selftest.sh --from-git 25887f33 1329s`, `tools/loop-mutations.sh --check 1s`,
      `tools/loop-mutations-selftest.sh 198s`, `tools/check-portability-selftest.sh 22s`, the doc-routes pair, the
      doc-routes mutation list and `tools/check-todo-references.sh 1s`; exit 0, end 2026-10-25T13:54Z; no
      `./gradlew` entry among them, which is the exclusion the line itself names.
- [x] 6.3 Check the change's pointers mechanically:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-case-bound-and-kept-leftovers --root .`.
      **Verify**: exit 0 — `C1 1 checked`, `C2 1 unparsable` (the pair a filtered run prints and writes no XML
      for), `C3 none cited`, `C4 none`, `C5 568 resolved and in range`.
- [x] 6.4 `openspec validate fix-loop-case-bound-and-kept-leftovers --strict`, then archive and confirm the five
      deltas merged into `openspec/specs/agent-issue-loop/spec.md`.
      **Verify**: `Change 'fix-loop-case-bound-and-kept-leftovers' is valid` before the archive; archived as
      `2026-10-25-fix-loop-case-bound-and-kept-leftovers` with `+ 3 added, ~ 2 modified`;
      `openspec/specs/agent-issue-loop/spec.md:2209` carries `### Requirement: A self-test case is held to a bound
      of its own`, and the two modified requirements keep every shipped scenario verbatim beside their added one.
