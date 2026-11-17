# Tasks

## 1. The ceiling is the pool's width (spec `agent-issue-loop`, "A self-test case is held to a bound of its own")

- [x] 1.1 `tools/loop/tests/loop-cases.sh:1354-1384`: rewrite the rule paragraph — what #928 derived, the 2026-11-17
      measurement that overruled it, and why the pool's width is the figure the load cannot stretch — delete
      `STOP_WAIT_STAGE_FACTOR` and `STOP_WAIT_TICK_CEILING`, and make `stop_wait_deadline` take no argument: it reads
      `LOOP_SELFTEST_WORKERS` (`0`, empty or non-numeric → `1`) and answers `STOP_WAIT_TICK_FLOOR × workers` ticks.
      `wait_for_run_stop`'s third-argument default becomes `$(stop_wait_deadline)`.
      — done: `bash -n tools/loop/tests/loop-cases.sh` clean; `grep -n 'STOP_WAIT_STAGE_FACTOR\|STOP_WAIT_TICK_CEILING'`
      over `tools/` returns nothing.
- [x] 1.2 `tools/loop/tests/loop-selftest.sh:196-202`: export `LOOP_SELFTEST_WORKERS="$workers"` beside `case_max`,
      with the comment naming `stop_wait_deadline` as its reader and why the number is exported rather than measured.
      — done: the case `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` prints the pool it derived from (16 on this
      host) in its own ceiling, and passes.
- [x] 1.3 `case_run_aHupEndedRunRecordsItsStopAndUnpicksItsItem`: drop `stage_started`/`stage_ms` and the derivation,
      take the deadline from `stop_wait_deadline`, and name the pool — not a staging figure — in the failure text.
      — done: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` `PASS`.
- [x] 1.4 `run#theStopWaitIsDecidedByTheStateTheRunWrites`: replace the three staging-derivation assertions with the
      ceiling's four readings (no pool / `0` / four / sixteen) and add the relation that the ceiling stays inside the
      case's own bound, read from `LOOP_CASE_MAX_SECONDS` and the same pool.
      — red on HEAD, recorded: install HEAD's `STOP_WAIT_*` block beside the new case and run it —
      `a pool of four allows 30 s per slot, in the ticks the wait sleeps in: expected '1200', got '300'`, `FAIL`. Green
      on this change: `PASS run#theStopWaitIsDecidedByTheStateTheRunWrites`.
- [x] 1.5 `tools/loop/tests/loop-mutations.sh:1351-1361`: move `928.1` onto the line this change leaves — it removes
      the pool's width (`printf '%s' $(( STOP_WAIT_TICK_FLOOR * workers ))` → `printf '%s' $STOP_WAIT_TICK_FLOOR`) —
      and rewrite the 928.x comment to say what the pool's width is and what the record removes. `928.2` unchanged.
      — done: `--check --only "928.1 928.2"` → `all 2 cases passed`; a full run of the two →
      `RED 928.1 run#theStopWaitIsDecidedByTheStateTheRunWrites` (`expected '1200', got '300'`),
      `RED 928.2 run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` (empty state named at its ceiling), both restored
      green.
- [x] 1.6 `guidelines/Backlog.recipes.md` §7b: replace the paragraph that priced the staging derivation with the rule —
      the pool's width as the ceiling, the 2026-11-17 measurement, why no cap is needed, and which case and which
      mutation hold it. — done: the paragraph names `LOOP_SELFTEST_WORKERS`, 120 s / 480 s, `928.1` and `928.2`.
- [x] 1.7 `case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`: the case's own ending of the run it started is
      patient the way its wait is — `TERM` sent by the case, the state the run writes waited for under
      `stop_wait_deadline`, and `loop_end_pid`/`wait_for_pid_end` kept as the last resort.
      — measured 2026-11-17 with sixteen concurrent instances of the case: **14 of 16** failed on `the run recorded the
      item its signal interrupted: expected 'blocked', got ''` before the ending was patient (the 2 s `TERM`→`KILL`
      grace inside `loop_end_pid` kills a run that has not finished ending), and **16 of 16** pass after;
      `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` focused: `PASS`.
- [x] 1.8 `openspec/changes/fix-the-stop-wait-ceiling-is-the-pools-width/specs/agent-issue-loop/spec.md`: MODIFIED
      requirement "A self-test case is held to a bound of its own", carrying every scenario the shipped spec has, with
      the fourth scenario's WHEN/THEN restated for the pool-derived clock and the relation to the case's own bound.
      — done: `openspec validate fix-the-stop-wait-ceiling-is-the-pools-width --strict`.

## 2. Verification

- [x] 2.1 The cases the ceiling guards, focused: `run#theStopWaitIsDecidedByTheStateTheRunWrites`,
      `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`,
      `run#noProcessOfASignalledRunOutlivesIt` — `PASS` each.
- [x] 2.2 The revert-check the new invariant owes: `928.1` removes the pool's width, the guard case's
      `a pool of four allows 30 s per slot … expected '1200', got '300'` must fail, restore, and the case must be green
      again — recorded in 1.5 and 2.1.
- [x] 2.3 The structural checks, since no compiled file is touched (`guidelines/Build.md` §2, the scope block):
      `bash -n` over `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-selftest.sh` and
      `tools/loop/tests/loop-mutations.sh`; `openspec validate fix-the-stop-wait-ceiling-is-the-pools-width --strict`;
      `tools/check-doc-routes.sh`. No Gradle build and no Android suite is owed, and none was run.
- [x] 2.4 The full self-test suite at the pool this host runs (sixteen cases), twice: run 1 `474 PASS, 2 FAIL` —
      `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the second site, task 1.7) and
      `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, the latter green focused and green in run 2; run 2 before
task 1.7 `474 PASS, 1 FAIL` — the same HUP sibling. Both runs are recorded in `evidence/full-suite.md`.
- [x] 2.5 The full suite once more after task 1.7, so the gate the change owes is read on the tree that lands: `all 475
      cases passed (JDK 21, offline, no token, no sandbox) — 1320.60 s`, exit `0`.
