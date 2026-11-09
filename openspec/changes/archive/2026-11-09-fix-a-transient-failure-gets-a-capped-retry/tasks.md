# Tasks

Reference: `proposal.md` for the scope and the red run, `design.md` for why and for the alternatives the evidence
eliminated, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "A child that never started is retried before the run stops", scenarios "A child that never started is
retried and the item is then worked" and "The retry cap is a cap".

- [x] 1.1 Add `run#aChildThatNeverStartedIsRetriedBeforeTheRunStops` to `tools/loop/tests/loop-cases.sh` — its stub's first
      worker child leaves an empty answer and the tree at the base and its second writes the item's archive and gate
      evidence, and the case asserts the run retried, printed the delay it waited and the cap it read, started the
      worker a second time, and recorded the item `done` with no escalation. `tools/loop/loop.conf` and
      `tools/loop/run.sh` are in its `# files:` declaration.
- [x] 1.2 Add `run#theRetryCapIsSpentAndTheRunStops` beside it — the child always refuses, and the case asserts the
      run spent `LOOP_RETRY_MAX=2` retries, started no fourth child, and left a stop reason naming the cap and the
      delays. Verify: on the base both fail — `the retry names the delay it waited: 'waiting 0 s' not found …` and
      `the run spent the two retries and started no fourth child: expected '3', got '1'`
      (`evidence/red-on-HEAD.log`).
- [x] 1.3 The two stop cases that pre-existed name `LOOP_RETRY_MAX=0`, which is the project that records no retry, so
      they keep asserting the stop rather than the retry (`run#childThatNeverStartedIsTheRunsFault`,
      `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`).
- [x] 1.4 The red run recorded before the fix: both cases, base `f11595fa` — XML `tests=2 failures=2`,
      2026-11-08T20:11:45Z–20:11:57Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`).
- [x] 1.5 The first check the delta owes, run before any suite: `bash tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — `PASS` in 2026-11-08T20:12:49Z (`evidence/first-check.log`), so
      both declarations name what their case reaches.

## 2. The retry budget

- [x] 2.1 `tools/loop/loop.conf`: the two keys `LOOP_RETRY_MAX=3` and `LOOP_RETRY_DELAYS="30 120 300"` beside the
      refusal streak, with the measurement and the rule that the run's own budget stops stay final.
- [x] 2.2 `tools/loop/run.sh`: read both keys beside the clocks before the item loop, and at the never-started arm
      spend the class's budget — the recorded delay, a printed line naming the attempt and the keys, a re-run of the
      item from the queue, and a stop that names the retries when the cap is spent.
- [x] 2.3 Verify 2.1–2.2: the four never-started cases `PASS`, 2026-11-08T16:29:23Z–16:29:53Z
      (`evidence/green-on-change.log`); and the whole `run` group green — `all 138 cases passed … 260.98 s`
      (`evidence/run-group.log`).

## 3. The rule's record

- [x] 3.1 `guidelines/Backlog.recipes.md` §7d: the paragraph that states the retry, names the keys and the class,
      carries the measured count of runs the stops cost, and names the two cases and the two mutation records, beside
      the paragraph it qualifies (the never-started stop).
- [x] 3.2 `tools/loop/tests/loop-mutations.sh`: `264.1` mutates the retry guard so the never-started case fails without the
      retry; `264.2` mutates the cap's comparison so a class that keeps failing spends one retry too many and the cap
      case fails.

## 4. Verification

- [x] 4.1 The revert-check: `bash tools/loop/tests/loop-mutations.sh --only '264.1 264.2'` — `RED 264.1` (the never-started case
      failing on the missing `waiting 0 s` line), `restore 264.1 … green again`, `RED 264.2` (the cap case reading
      `expected '3', got '4'`), `restore 264.2 … green again`, `red-when-mutated: 2 · not-noticed: 0 ·
      restore-failures: 0`, 2026-11-08T21:37:46Z–21:38:20Z (`evidence/mutation-264.log`).
- [x] 4.2 Every record this delta's paths select still applies: `bash tools/loop/tests/loop-mutations.sh --check --from-git
      f11595fa` — `selected 129 of 304 records (files: guidelines/Backlog.recipes.md tools/loop-cases.sh
      tools/loop-mutations.sh tools/loop/loop.conf tools/loop/run.sh)`, `all 129 cases passed — every selected record
      applies to the file it names`, 2026-11-08T20:13:01Z (`evidence/mutation-check.log`).
- [x] 4.3 The structural checks of the files the diff edits and of the artifacts it writes: `bash -n
      tools/loop/run.sh`, `bash -n tools/loop/tests/loop-cases.sh`, `bash -n tools/loop/tests/loop-mutations.sh` all exit 0;
      `bash tools/check-openspec-hygiene.sh` and `bash tools/check-todo-references.sh` exit 0; `bash
      tools/check-doc-routes.sh` — `all 329 section references resolve` (`evidence/structural-checks.log`,
      2026-11-08T20:13:02Z–20:13:05Z). No compiled file is touched, so no build is owed and the Gradle tasks this
      project's tasks rule asks for are replaced by these checks.
- [x] 4.4 The change's readiness: `bash tools/check-change-readiness.sh
      openspec/changes/fix-a-transient-failure-gets-a-capped-retry` — `LOOP-CHANGE-READY: ok`, 2026-11-08T20:13:05Z
      (`evidence/readiness.log`).
- [x] 4.5 The numbers the change quotes, taken once the pathspec moved: `./tools/loop/measure.sh --change
      fix-a-transient-failure-gets-a-capped-retry` (`evidence/measure.txt`).

## 5. The rebase onto the moved tooling tests

`#740` landed while this change was being finished and moved every tooling suite under `tests/`, so the change was
rebased onto `main` (`839ffc71`): git followed the renames, and the three path references that did not follow by
themselves — the two `# files:` declarations of the new cases (`tools/loop/tests/fixtures/scenarios/`) and §7d's
record path (`tools/loop/tests/loop-mutations.sh`) — were repaired by hand.

- [x] 5.1 The five cases on the rebased tree, each `PASS`: `driver#everyCaseDeclaresTheFilesItCanReach`, the two new
      cases, and the two stop cases that name `LOOP_RETRY_MAX=0` (`.pi/tmp/cases1.log`, 2026-11-09T11:22Z–11:23Z).
- [x] 5.2 The revert-check on the rebased tree: `bash tools/loop/tests/loop-mutations.sh --only '264.1 264.2'` —
      `RED 264.1`, `restore 264.1 … green again`, `RED 264.2` (the cap case reading `expected '3', got '4'`),
      `restore 264.2 … green again`, `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`
      (`.pi/tmp/mut264.log`, 2026-11-09T11:35Z).
- [x] 5.3 The change's gate: `tools/loop/gate.sh --base main` — `LOOP-GATE-TREE: head=92e526da71e75bf061597cb55ea39d7f4fe71d99
diff=7bb84d4b46b53e03`, `LOOP-GATE: tests=567 failures=0 errors=1 … — failing: tools/loop/tests/loop-selftest.sh
      --from-git main` (`evidence/gate.txt`). The one error is a step that overran its 1800 s bound, and the case it
      stopped in hangs on `main` too: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` reaches `rc=124` at a 240 s
      bound in a throwaway worktree at `839ffc71` with nothing else running, and again at 150 s
      (`evidence/waiver-gate-step-hup.txt`). Recorded as a waiver rather than paid for in re-runs.

The item's one gate — `tools/loop/gate.sh`'s plan for this delta, the loop suite's `--from-git` selection and the
mutation check it owes — is run by the gate child that follows the diff review in the run's order, so this change
child ran only the cases its delta reaches and the first check the plan names, which is the rule this change states.
