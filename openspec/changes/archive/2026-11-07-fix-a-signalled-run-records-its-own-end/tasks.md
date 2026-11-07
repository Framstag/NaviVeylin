# Tasks — a signalled run records its own stop, and HUP is trapped beside INT and TERM

## 1. The cases (written first: they are the red test)

- [x] 1.1 `driver#everyCaseDeclaresTheFilesItCanReach` — the cheapest check a delta that writes a case owes, run
      before anything else, because the suite that refuses a `# files:` declaration arrives at the end of a gate.
      Verify: `bash tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` — PASS on the base with
      both cases in place (`evidence/red-on-HEAD.log`, 2026-11-07T07:32:43Z).
- [x] 1.2 `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` — a run blocked on its child, `HUP` sent to the pid the
      state's `.run.ownerPid` names, then: status `129`, `run.stop.reason` naming `SIGHUP`, `remove-label
      agent/in-progress` recorded exactly once, the item `blocked`, the child it started no longer running (a `kill -0`
      probe, the way `run#interruptedRunEndsTheChildItStarted` takes it), and no lock left behind. Verify: on the base
      it failed at the stop reason — `'SIGHUP' not found in:` (`evidence/red-on-HEAD.log`, 07:32:53Z).
- [x] 1.3 `run#interruptedRunEndsTheChildItStarted` — one assertion added beside the status it already checks
      (`run.stop.reason` names `SIGTERM`), and a wait for the run's own end before the record is read: the signal goes
      to the run's process group, so the subshell the case waits for is gone while the trap is still finishing, and
      the lock is released after the run's record is written. Verify: on the base it failed with `'SIGTERM' not found
      in:` (`evidence/red-on-HEAD.log`, 07:32:49Z).

## 2. The engine

- [x] 2.1 `tools/loop/run.sh` traps `HUP` beside `INT` and `TERM`, and `on_signal` keeps the signal's name so the
      record names it rather than an exit code. Verify: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` PASS
      (`evidence/green-on-change.log`, 07:33:32Z) — status `129`, and the child ended, which the case reads with a
      `kill -0` probe and a release taken afterwards, the way `run#interruptedRunEndsTheChildItStarted` does.
- [x] 2.2 `tools/loop/run.sh`'s interrupted branch calls `loop_state stop "interrupted by SIG<NAME>"` before the label
      is unpicked and before `loop_state unlock`. Verify: both cases PASS, and
      `labels#trapRemovesInProgressOnSignal` PASS — the item's own record is unchanged
      (`evidence/green-on-change.log`, 07:33:37Z). The order itself is read from `tools/loop/run.sh:586`–`:594` and no
      case asserts it; design.md states that limit.
- [x] 2.3 The mutation record that names the line this restructure re-indented is realigned, not the case:
      `tools/loop-mutations.sh` 9.4 now names the line inside the nested test. Verify:
      `bash tools/loop-mutations.sh --check` → "all 267 cases passed" (`evidence/checks.log`, 07:34:07Z).
- [x] 2.4 `tools/loop-mutations.sh` 9.13 names the invariant this change adds — the trap's own stop write — so the
      case's reason assertion is what notices the line's deletion, and nothing else in it has to fail first. Verify:
      `bash tools/loop-mutations.sh --only 9.13` → `'SIGHUP' not found in:` at `2026-11-07T07:33:50Z`, then
      `restore 9.13 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-9.13.log`); the declared set still applies whole — `all 267 cases passed`
      (`evidence/checks.log`, 07:34:07Z).

## 3. The text the engine serves

- [x] 3.1 `guidelines/Backlog.recipes.md` §7d states the rule: a run a signal ended records that signal as its stop,
      `HUP` is trapped with `INT` and `TERM`, and the item's record is unchanged. Verify:
      `bash tools/check-doc-routes.sh` → all 329 section references resolve (`evidence/checks.log`, 07:34:02Z); no
      routing row's byte figure moves, because `AGENTS.md` prices `Backlog.md`'s §7a–§7e and not this file.

## 4. Verification (no compiled file is touched, so no build and no Gradle suite is owed)

- [x] 4.1 The two cases that name the requirement, plus the two the interrupted path already had — PASS each, and the
      two XMLs are beside the logs (`evidence/red-on-HEAD.xml`, `tests=2 failures=2`; `evidence/green-on-change.xml`,
      `tests=4 failures=0`). Verify: `evidence/green-on-change.log`, 07:33:22Z–07:33:43Z. The HUP case's own green on
      the corrected tree — its child clause asserted — is 07:33:32Z; the gate re-runs both cases over the whole change.
- [x] 4.2 The structural checks this delta owes: `bash tools/check-openspec-hygiene.sh` (exit 0),
      `openspec validate fix-a-signalled-run-records-its-own-end --strict` ("is valid", after the delta kept the existing
      scenario's title and a MODIFIED requirement replaced the whole block), `bash tools/loop-mutations.sh --check`
      (267 of 267), `bash tools/check-doc-routes.sh` (329 + 5), `bash tools/check-todo-references.sh` (66 citations
      resolve), and the suite's own count — `bash tools/loop-selftest.sh --list | wc -l` → `349` in the change and
      `348` at the base `fca2c857`, with the run-case count at `129` from the base's `128` (`grep -c "^  'run#"
      tools/loop-cases.sh`). Verify: `evidence/checks-2.log`, `evidence/red-on-HEAD.log`,
- [x] 4.3 Hand the diff over: the gate is not this child's (`LOOP-DIFF-READY`). The gate child runs
      `./tools/loop/gate.sh` over the whole change and the suite that covers `tools/loop/**`. Done by hand on the
      first two commits: `LOOP-GATE: tests=880 failures=0 errors=0` over 21 tallies (`evidence/gate.log`,
      `evidence/gate-tally/`). The correction round then changed docs, comments and one line of `on_signal`: its own
      re-record is `evidence/checks-2.log` (strict validate, hygiene, doc-routes, 66 citations, 267 mutation records,
      and the two counts), and the four cases that cover it were re-run green before the commit.
- [x] 4.4 Review: round 1 refused the change on one blocking finding — the claim "whether or not an item was in
      flight" is asserted by no case — recorded at `evidence/review-failed-round-1.md`; the correction is the commit
      after it, and round 2 passed (`evidence/review-round-2-passed.md`).
