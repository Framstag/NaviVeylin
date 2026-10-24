# Tasks

Ordered. The change touches no compiled file, so the structural checks that stand in for a build are the loop's
own gate (`tools/loop/gate.sh`) and `openspec validate --strict`.

- [x] 1. The case that reproduces the defect, and its red run on HEAD:
   `run#childIsEndedAtTheRunsDeadline` — a child that blocks forever, a run whose recorded deadline is moved two
   seconds away, and a child cap of 3 600 s so only the deadline can end it. Registered in `LOOP_SHELL_CASES`,
   dispatched in `loop_run_shell_case`, implemented in `tools/loop-cases.sh`.
   Verification: the suite on the engine at `49dfd7db` with its own `run.sh` — `loop-selftest: 1 of 1 cases
   failed`, the case failing with "the run was still waiting 60 s after its own recorded deadline passed"
   (`evidence/red-HEAD.log`; the tally is `evidence/red-HEAD.xml`, `tests="1" failures="1"`).

- [x] 2. `tools/loop/run.sh` — the wait reads `run.deadlineEpoch` from the run's own state on every tick and, when
   it has passed, ends the child and everything it started exactly as the cap arm does; the transcript gains
   `LOOP-DEADLINE: the run's deadline passed at <deadlineAt> — …` beside the `LOOP-TIMEOUT` line, whose wording now
   names the clock that ended it; the run records the stop through `stop_run`.
   Verification: `run#childIsEndedAtTheRunsDeadline` green (transcript, item verdict, item note and recorded
   stop), `run#childOutlastingTheWallClockIsBlocked` still green for the cap arm, and mutation `23.2` making the
   new case red.

- [x] 3. `tools/loop/run.sh` — the completion record is cleared before the child is started, not after it.
   Verification: `run#childOutlastingTheWallClockIsBlocked` and `run#childIsEndedAtTheRunsDeadline` green (the
   record is the only event the wait ends on), and `tools/loop-mutations.sh --check` clean, whose `9.9`/`12.2`
   records quote the child-start line this leaves untouched.

- [x] 4. `tools/loop-mutations.sh` — `23.1` realigned to the poll's rewritten comparison; `23.2` (the deadline
   branch) added. A `23.3` for the `stop_run` line was written and removed: the case cannot see it, because
   `state.sh next` overwrites that stop with the deadline it names, and the reason is recorded in the file's own
   comment instead of in a record that would pass by asserting an adjacent thing.
   Verification: `tools/loop-mutations.sh --only '23.1 23.2'` — each record red when mutated, green after its
   restore (`evidence/mutations-23.log`, `evidence/mutations-23-1.log`).

- [x] 5. `guidelines/Backlog.md`, its section "The loop" — the paragraph that states what bounds a child names the run's own
   deadline beside the child cap, its transcript lines and its stop, and the completion record's ordering.
   Verification: `tools/check-doc-routes.sh` green in the gate's log.

- [x] 6. The change's own gate, once, from the change root: `tools/loop/gate.sh`.
   Verification: `LOOP-GATE: tests=377 failures=1 errors=0` over `tools/loop-selftest.sh`,
   `tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`, `tools/check-doc-routes.sh`, its
   self-test, the mutation listing and `tools/check-todo-references.sh` (`evidence/gate.log`, `evidence/gate.xml`),
   with the seven per-suite tallies under `evidence/gate-tallies/`. The one failure is
   `run#aWriterTheRunCanPlaceBlocksTheItem`, which fails identically with HEAD's `run.sh` and is issue `#288`
   (`evidence/attribute.log`, `evidence/writer-flake.log`).

- [x] 7. The full mutation set of the engine file this change touches, before the change is archived:
   `tools/loop-mutations.sh --files tools/loop/run.sh`.
   Verification: `red-when-mutated: 58 · not-noticed: 0 · restore-failures: 3` (`evidence/mutation-run-sh.log`),
   including `23.1` and `23.2`; the three restore failures are `12.10` (its case is the pre-existing `#288` red)
   and `34.1`/`34.2` (a case that fails identically with HEAD's `run.sh` on this shared host,
   `evidence/prescreen-attribute.log`).

- [x] 7a. The independent, read-only review before archive: a fresh child read the frozen change (`run.sh`,
   `loop-cases.sh`, `loop-mutations.sh`, `Backlog.md`, the diff), the spec scenarios, the gate log and the tallies.
   Verification: it reported two `BLOCKING` contradictions — a `23.3` record this change no longer adds, and a
   spec scenario claiming the deadline outranks the item cap in `state.sh next` — both corrected in the artifacts;
   the corrections and the one stale-snapshot finding are recorded in `evidence/evidence.md` §3a.

- [x] 7b. The post-review correction: the blocked reason of a rework worker the run ended now names both clocks
   (`tools/loop/run.sh`, the `blocked_reason` of the round-2 arm), because the deadline arm can end that child
   too and the text named only the cap.
   Verification: the four cases that reach that arm re-run green, and `tools/loop-mutations.sh --check` clean
   (`evidence/post-review-cases.log`).

- [x] 8. `openspec validate fix-loop-child-outlives-its-deadline --strict`, then archive; the delta merged into
   `openspec/specs/agent-issue-loop/spec.md` and `.pi/skills/fix-loop/scripts/evidence-check.sh` clean over the
   change directory.
   Verification: `Change 'fix-loop-child-outlives-its-deadline' is valid`, the change archived as
   `openspec/changes/archive/2026-10-24-fix-loop-child-outlives-its-deadline/`, and `evidence-check.sh`
   reporting no unresolved pointer.
