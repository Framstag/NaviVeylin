# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run. The
change touches no compiled file, so the structural checks that stand in for a build are the loop's own gate
(`tools/loop/gate.sh`) and `openspec validate --strict`.

- [x] 1. The three cases that reproduce the defect, and their red run on HEAD. The cases are
   `state#aRefusedRunLeavesTheLiveStateAlone` (a refused run replaces the live run's state),
   `state#aDeadOwnersLockIsTakenOver` (a lock whose owner is gone is refused) and
   `report#aKilledRunIsNotALiveRun` (the report calls a killed run a running one). Each is registered in
   `LOOP_SHELL_CASES`, dispatched in `loop_run_shell_case` and implemented in `tools/loop-cases.sh`.
   Verification: the three cases on the engine at `9f107493f4e6` — `FAIL` for each, `EXIT=1`
   (`evidence/red-HEAD.log`, 2026-10-22T20:28Z), with the tally in `evidence/red-HEAD.xml`
   (`tests="3" failures="3"`). Each failed for the reason its own line names: the refused run's
   `run.startedAt` had replaced the live run's, the dead owner's lock was refused with exit 5, and the report
   read `budget left 2 items / 90 min` and `the run is still going`.

- [x] 2. `tools/loop/state.sh` — `cmd_lock` asks whether the lock's owner is still running: a live owner is
   the refusal it is today (exit 5, the lock named), a dead one is a takeover that is announced and names the
   pid it took over from. `cmd_init` records the run that wrote the state in `run.ownerPid`, read from the
   lock's owner, and the state keeps the field when it is written.
   Verification: `state#aDeadOwnersLockIsTakenOver` green — `PASS` in `evidence/focused-cases-green.log`; the
   lock is held by the new owner afterwards, the takeover is announced with the dead pid, and the lock is
   released by its holder alone.

- [x] 3. `tools/loop/run.sh` — the lock is taken before the run writes anything: before `state.sh init`,
   before the pre-screen and before the adoption path can merge a branch. The traps that release the lock
   are installed with it, so every later exit releases it, and the kept-worktree check loses the
   "no run holds the lock" condition, because a live run is now refused at the lock.
   Verification: `state#aRefusedRunLeavesTheLiveStateAlone` green — `PASS` in
   `evidence/focused-cases-green.log`; the live run's `run.startedAt` and the bytes of its state file are
   unchanged after the refusal, and the refusal still exits 5 and names the lock
   (`state#secondRunRefusesWithExit5` still passes).

- [x] 4. `tools/loop/report.sh` and `tools/loop/state.sh show` — a state whose owner is not running and that
   recorded no stop prints no budget left, and its stop line names the process that is gone instead of saying
   the run is still going.
   Verification: `report#aKilledRunIsNotALiveRun` green — `PASS` in `evidence/focused-cases-green.log`; the
   report names the process that is gone, prints no `budget left … min`, and
   `report#impossibleBudgetNamesAnotherRun` and `report#archivedStateIsReported` still pass.

- [x] 5. `tools/loop-mutations.sh` — the mutations for the new invariants: the lock taken before the state is
   written (`41.1`), the liveness of the lock's owner (`41.2`) and the report's dead-run wording (`41.3`);
   the record whose pattern the reordering changed (`13.3`, the kept-worktree check) is rewritten against the
   new line, and `13.6` — the lock telling a live run's tree from a previous run's leftovers — now owns the
   refusal of a live owner's lock. The two records that named a line the engine no longer has (`9.9`, `12.2`,
   stale since issue #227 changed that line) are rewritten as well; the finding that the full set is in no
   gate is filed as issue #264.
   Verification: the **full** set — `bash tools/loop-mutations.sh --timeout 300` → `red-when-mutated: 87 ·
   not-noticed: 0 · restore-failures: 0`, `EXIT=0` (`evidence/mutation-full.log`); the five records this
   change owns alone go red and restore green again in the same run.

- [x] 6. `guidelines/Backlog.md` §7 "The loop" — the paragraph that states when the lock is taken, what a run
   finds when the lock's owner is gone, and what the report says about a run that was killed.
   Verification: `tools/check-doc-routes.sh` — `all 152 section references resolve, all 4 skill references
   resolve`, `EXIT=0` (the gate's suite for `guidelines/**`, `evidence/gate-tallies/tools_check-doc-routes.sh.xml`).

- [x] 7. The project's gate, once, exactly as the run configures it: `./tools/loop/gate.sh`, whose owed
   suites for this pathspec are the Gradle unit tests, `tools/loop-selftest.sh`,
   `tools/loop-mutations-selftest.sh`, `tools/gen-feature-list-selftest.sh`,
   `bash tools/gen-feature-list.sh --check-classification` and `tools/check-doc-routes.sh`.
   Verification: `LOOP-GATE: tests=319 failures=0` — `EXIT=0` (`evidence/gate.log`), the per-suite tallies
   copied out of the build directory into `evidence/gate-tallies/` (`tools_loop-selftest.sh.xml`
   `tests="166" failures="0"`, `tools_loop-mutations-selftest.sh.xml` `tests="34" failures="0"`,
   `tools_gen-feature-list-selftest.sh.xml` `tests="119" failures="0"`,
   `bash_tools_gen-feature-list.sh_--check-classification.xml` `failures="0"`).
   The classification check owed every change under `openspec/` was red before this change for three
   capability ids other open changes had not classified (`search-name-matching`,
   `search-result-row-icon`, `start-map-selection`); the three entries are added to
   `tools/feature-list/specs.json` on the owner's instruction, and the check is green.

- [x] 8. Archive: the change sits under
   `openspec/changes/archive/<date>-fix-loop-lock-is-taken-before-anything-is-written/`, the delta merged into
   `openspec/specs/agent-issue-loop/spec.md`, every cited log and XML in its `evidence/`.
   Verification: archived with `openspec archive fix-loop-lock-is-taken-before-anything-is-written --yes`; the
   requirement "One run at a time holds the repository" and the scenario "A run whose process is gone is not a
   run that is going" are in `openspec/specs/agent-issue-loop/spec.md`;
   `openspec validate agent-issue-loop --type spec` — only the pre-existing "requirement text is very long"
   warnings; `openspec doctor` — root ok; `tools/check-doc-routes.sh` after the archive — `EXIT=0`.
