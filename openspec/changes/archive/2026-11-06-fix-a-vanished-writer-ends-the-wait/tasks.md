# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case, before any engine change

Requirement: "A wait ends when the writer of the record it waits for is gone", scenario "A child whose writer
vanished ends the wait".

- [x] 1.1 Add `run#aVanishedWriterEndsTheWait`, its entry in `LOOP_SHELL_CASES` and its dispatch line: the stub child
      records the process group `set -m` made of the run's own subshell, blocks on a fifo, and the case `kill -KILL`s
      that group, so the writer dies without writing the record. The case asserts the run ends inside 60 s at
      `exit 124`, that the transcript carries `LOOP-TIMEOUT: the child's writer`, that the item is `blocked` with
      `bound` as its mechanism and the vanished writer in its note, and that no `agent/needs-decision` label is
      written. Verify: on the base `c0dbcf5c`, with the engine change reverted, it fails with `the run was still
      waiting 60 s after the writer of its child's record was killed` (2026-11-06T07:02:23Z–07:03:27Z,
      `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`).
- [x] 1.2 Add `run#aSlowChildIsNotMistakenForAVanishedWriter`: a live stub that has written nothing holds the wait for
      five seconds, the case asserts the run is still waiting and no line of the kind was written, then releases it and
      asserts the run finishes on the record the child wrote and the item closes. It is green on both sides of the
      change by design — it is the guard for #227's trap, not a second red case.

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`, `run_child`: read the writer's liveness once a second inside the wait, only while the
      record is absent, and end the wait with `ended_by=writer` — never `wait` on the pid. Verify: `bash
      tools/loop-selftest.sh --case 'run#aVanishedWriterEndsTheWait'` PASS (2026-11-06T07:03:30Z,
      `evidence/green-on-change.log`).
- [x] 2.2 `tools/loop/run.sh`, the timeout block: one arm that appends `LOOP-TIMEOUT: the child's writer (pid <n>)
      vanished before it wrote its completion record — the run ended the wait`. Verify: the case asserts that line in
      the transcript and in the item's note, and `bound` as the item's stop reason.
- [x] 2.3 `tools/loop-mutations.sh`: record `82.1`, which drops the condition so the wait is ended by a clock 3 600 s
      away. Verify: `bash tools/loop-mutations.sh --only 82.1` → `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0` (2026-11-06T07:03:44Z–07:04:56Z, `evidence/mutation-82.1.log`).
- [x] 2.4 `guidelines/Backlog.recipes.md`:195 "What an item costs, and the bounds on it": name the reading
      beside the clocks a child is bounded by, with the 2026-11-06 measurement and what the item records. Verify:
      `grep -n "vanished" guidelines/Backlog.recipes.md`.

## 3. Verification

- [x] 3.1 The two cases pass together on the change: `bash tools/loop-selftest.sh --case 'run#a*Writer*'` → 3 of 3
      PASS, 2026-11-06T07:03:30Z–07:03:44Z (`evidence/green-on-change.log`, `evidence/green-on-change.xml`).
- [x] 3.2 The cases that own the wait are unchanged: `--case 'run#child*'` → 10 of 10 PASS (07:00:19Z–07:00:38Z) and
      `--case 'run#*Child*'` → 12 of 12 PASS (06:59:12Z–07:00:15Z), which include `childOutlastingTheWallClockIsBlocked`,
      `childIsEndedAtTheRunsDeadline`, `childThatNeverStartedIsTheRunsFault`, `childPastItsTurnBoundIsBlocked`,
      `childPastItsContextBoundIsBlocked`, `treeOfAChildTheCapEndedIsNotCommitted` and
      `aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`; and `--case 'run#reviewerEndedAtTheRunsDeadlineIsNotADecision'`,
      `--case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'`, `--case 'run#interruptedRunEndsTheChildItStarted'` and
      `--case 'run#noProcessOfASignalledRunOutlivesIt'` all PASS (07:00:15Z).
- [x] 3.3 The mutation set still applies to the tree: `bash tools/loop-mutations.sh --check` → `all 244 cases passed
      — every selected record applies to the file it names` (07:00:48Z).
- [x] 3.4 The change is ready for an unattended worker: `tools/check-change-readiness.sh
      openspec/changes/fix-a-vanished-writer-ends-the-wait` → `LOOP-CHANGE-READY: ok`.
- [x] 3.5 The delta validates: `openspec validate fix-a-vanished-writer-ends-the-wait --strict` → `valid`.
- [x] 3.6 The delta owes `BuildGate.md` §2a's structural check for a `guidelines/**`/`openspec/**`/`tools/**` roster: the
      change tree resolves its routes — `bash tools/check-doc-routes.sh` → `all 329 section references resolve, all 5
      skill references resolve` (2026-11-06T07:09:42Z, exit 0, `evidence/doc-routes.log`) — and the CLI's own doctor
      reads the root as sound — `openspec doctor` → `OpenSpec root: ok`, `References: (none declared)`
      (07:09:43Z, exit 0, `evidence/openspec-doctor.log`).
