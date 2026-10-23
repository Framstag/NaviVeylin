# Tasks

- [x] 1. Red on HEAD — `evidence/red-HEAD.log` (2026-10-23T20:0xZ), `evidence/red-HEAD.xml`
      (`tests="1" failures="1"`): a detached throwaway worktree at `c1b7a619`, the case file from this change,
      `expected 'blocked', got 'done'`
- [x] 2. `tools/loop/run.sh`
  - [x] 2.1 `pid_in_child_tree` — the child's process group, then the ancestor chain (bounded at 64 hops)
  - [x] 2.2 `outside_write_pids` takes extra pids to skip, so the heartbeat is never part of its own answer
  - [x] 2.3 `outside_write_seen` — the two `LOOP-OUTSIDE-WRITE-*` lines: what changed and when, and the processes
        alive then with the placed ones marked
  - [x] 2.4 the heartbeat reads the tree once per tick, once per child, into `$2.outside-write`; `run_child`
        appends it to the transcript and hands the placed pids to the decision
  - [x] 2.5 `outside_write_decides` — placed, or strict; the worker, the round-2 worker and the review round all use
        it
- [x] 3. Green — the five cases of the outside-write rule PASS (`run#childWriteOutsideTheWorktreeIsNoted`,
      `run#aWriterTheRunCanPlaceBlocksTheItem`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`,
      `run#strictOutsideWriteBlocksTheItem`, `run#strictOutsideWriteDiscardsTheReviewRound`); `evidence/green-fix.log`
- [x] 4. Falsification — `evidence/revert-check-12.log`: `bash tools/loop-mutations.sh --only "12.3 12.4 12.9 12.10"`
      → `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`; `12.3` and `12.4` re-anchored, `12.9`
      re-anchored, `12.10` added for the in-time reading; `--check` applies all 123 records
- [x] 5. `guidelines/Backlog.md` §7 states the rule and the measurement that forced it (a writer is gone at the exit
      reading; the case that assumed otherwise read `expected 'blocked', got 'done'`)
- [x] 6. Gate once from the change root → `evidence/gate.log`: `failures=0 errors=0`, 359 cases over the three
      suites (selftest 832 s, mutations `--check` 1 s, mutation selftest 103 s, classification, doc routes); the
      per-suite tallies are 202, 123 and 34, each carried by its own XML in `evidence/gate-tallies/`
- [x] 7. `openspec validate … --strict` valid; `evidence-check.sh` on the archived change: PASS
- [x] 8. Archived as `2026-10-23-fix-loop-a-difference-is-attributed-while-the-writer-lives`, recorded on #286 as the
      attribution half its own body named as the next step
