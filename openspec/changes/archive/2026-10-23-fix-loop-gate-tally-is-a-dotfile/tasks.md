# Tasks — the `./` before a tally is named

## 1. The record a reader can find

- [x] 1.1 **The red case.** `gate#aCompiledStepsTallyIsNotHidden` added to `tools/loop-cases.sh`: a throwaway
      project whose profile names `./gradlew test` as its compiled command and a `gradlew` stub at its root, one
      `.kt` path owed the build, then a plain `ls` of the tally directory and a shell `tally/*.xml` glob. Run against
      the engine's `gate.sh` as HEAD carries it — the engine tree copied out of `git show
      HEAD:tools/loop/gate.sh` and run with that `LOOP_ENGINE` — 2026-10-23T13:42:30Z: FAIL, the directory holding
      `._gradlew_test.xml` while `ls` printed nothing. Tally `tests="1" failures="1"` in
      `evidence/red-on-head.xml`, transcript `evidence/red-on-head.log`. **Verification:** the case names the
      directory it read in its own failure message.

- [x] 1.2 **The fix.** `tools/loop/gate.sh`'s tally-naming line (`:173` at the base, `:178` as the change leaves it):
      the name is built from `${cmd#./}` instead of `$cmd`, so the `./`
      of a command run from the project root never reaches the file name; the sanitising, the 80-character cut, the
      directory and every number the tally carries are unchanged. **Verification:** `bash tools/loop-selftest.sh
      --group gate` → `loop-selftest: all 9 cases passed` (`evidence/green-gate-group.log`, tally `tests="9"
      failures="0"` in `evidence/green-gate-group.xml`), the new case among them.

- [x] 1.3 **The falsification.** Record `51.1` in `tools/loop-mutations.sh` restores the `./`-carrying name.
      **Verification:** `bash tools/loop-mutations.sh --check --files tools/loop/gate.sh` → every selected record
      applies (5 of 108, `evidence/mutation-check.log`), and `bash tools/loop-mutations.sh --only 51.1` →
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/revert-check-51.1.log`).

- [x] 1.4 **The record of the rule.** The delta `specs/agent-issue-loop/spec.md` adds the requirement "A tally's
      name is a file a reader finds" with its one scenario naming the case, and `guidelines/Backlog.md`'s section
      "The loop" gains the
      sentence that states it. **Verification:** `openspec validate fix-loop-gate-tally-is-a-dotfile --strict`, and
      `tools/check-doc-routes.sh` for the guideline's route (`evidence/structural-checks.log`).

- [x] 1.5 **The affected cases.** No compiled file is touched, so the build owes nothing
      (`tools/loop/loop.conf:152`); the gate's shell suites are the ones this delta is measured with.
      **Verification:** `./tools/loop/gate.sh --base 677a7e6f` — the project's gate, measured against the run's own
      base so that two other open changes' committed Kotlin cannot be the delta it grades, and green over the four
      suites the pathspec owes: `tools/loop-selftest.sh` (`tests="188" failures="0"`), `tools/loop-mutations-selftest.sh`
      (`tests="34" failures="0"`), `bash tools/loop-mutations.sh --check` (`tests="108" failures="0"`) and
      `tools/check-doc-routes.sh` (`tests="0" failures="0"`) — the gate's own line in `evidence/gate.log`, the four
      tallies in `evidence/gate-tallies/`. The first run of that same command was red — one case,
      `run#engineNamesNoProjectFact`, refused a comment in `gate.sh` that named the project's own build command, and
      the comment was rewritten to name the `./`-form instead (`evidence/gate-first-red.log`); the two cases were
      then run green on their own before the gate was run again.

- [x] 1.6 **Evidence and archive.** Every cited XML copied into `evidence/`, the pointers checked mechanically
      (`bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-gate-tally-is-a-dotfile --root
      .`), the change archived and its delta confirmed in `openspec/specs/agent-issue-loop/spec.md`.
      **Verification:** the check's own output and the archive directory (`evidence/archive.log`).
