# Tasks

- [x] 1. Red on HEAD — `evidence/red-HEAD.log` (2026-10-23T17:58:26Z), `evidence/red-HEAD.xml`
      (`tests="4" failures="4"`): the engine at `f084f734` in a throwaway worktree, the case file from this change
  - [x] 1.1 `run#childWriteOutsideTheWorktreeIsNoted` — `expected 'done', got 'blocked'`
  - [x] 1.2 `run#dirtyBaseRefusesTheMergeAndNamesThePath` — `'is not clean' not found in: the worker wrote
        outside the worktree — the project's own tree changed while it ran`
  - [x] 1.3 `run#strictOutsideWriteBlocksTheItem` — `--strict-outside-write` is not a flag the engine at HEAD takes
  - [x] 1.4 `run#strictOutsideWriteDiscardsTheReviewRound` — same
- [x] 2. `tools/loop/run.sh`
  - [x] 2.1 `project_state_diff`, `outside_write_clause`, `outside_write_pids`, `outside_write_line` (folded from
        #285), and `run_child` appends the line instead of the constant sentence
  - [x] 2.2 `--strict-outside-write yes|no`, default `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, named in the run header
        and in `--help`
  - [x] 2.3 the worker path and the round-2 worker path record the difference as a `state.sh` step and continue by
        default; the strict run blocks as before, with the names in the reason
  - [x] 2.4 the review round's read-only comparison measures the worktree only, and a difference in the project's
        tree is recorded — and discards the round only under strict
- [x] 3. Green — `evidence/green-fix.log`: the four cases PASS; the gate's own run of the suite is
      `tools/loop-selftest.sh` 194 cases (`evidence/gate-tallies/tools_loop-selftest.sh.xml`)
- [x] 4. Falsification — `evidence/revert-check-12.log`
  - [x] 4.1 record `12.3` re-anchored on the new call site (`RED 12.3 run#childWriteOutsideTheWorktreeIsNoted`)
  - [x] 4.2 record `12.4` re-anchored on the strict review reading (`RED 12.4
        run#strictOutsideWriteDiscardsTheReviewRound`), new record `12.9` on the strict worker verdict
        (`RED 12.9 run#strictOutsideWriteBlocksTheItem`)
  - [x] 4.3 `bash tools/loop-mutations.sh --check` — all 115 records apply;
        `--only "12.3 12.4 12.9"` → `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`
- [x] 5. `tools/loop/loop.conf` gains `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT=off` with the measurement that sets it
- [x] 6. `guidelines/Backlog.md` §7 states the rule and the measurement (5 of 139 items, the three cases of
      2026-10-23, the landing rule that stays)
- [x] 7. Gate once from the change root — `evidence/gate.log`: the gate's closing line has `failures=0 errors=0`
      and 343 cases over the three suites (`tools/loop-selftest.sh` 757s, `bash tools/loop-mutations.sh --check`
      1s, `tools/loop-mutations-selftest.sh` 104s, `bash tools/gen-feature-list.sh --check-classification` 3s,
      `tools/check-doc-routes.sh` 0s); the per-suite tallies are 194, 115 and 34, each carried by its own XML in
      `evidence/gate-tallies/`
- [x] 8. `openspec validate fix-loop-outside-write-is-recorded-with-what-changed --strict` → valid;
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh` after the archive
- [x] 9. Archived as `2026-10-23-fix-loop-outside-write-is-recorded-with-what-changed`; #285 records that its
      naming half is carried here, and #286 is the item this change answers
