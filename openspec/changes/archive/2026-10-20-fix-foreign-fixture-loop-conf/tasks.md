# tasks — fix-foreign-fixture-loop-conf

Spec: `openspec/changes/fix-foreign-fixture-loop-conf/specs/tooling-portability/spec.md`, requirement "The
synthetic project is a fixture, not a second project", scenario "The fixture declares every budget the engine
reads". Issue #198.

## 1. Reproduce the red

- [x] 1.1 Read the failing CI job's log and name the failing step (`Check portability`, 3 of 26 cases, message
      `loop: run: --max-review-rounds must be a positive integer`) — `evidence/ci/build-job-39384065781.txt`.
      Verification: the log names the step, the three cases and the message.
- [x] 1.2 Reproduce on HEAD `f2e57ee` in a clean offline clone: `bash tools/check-portability-selftest.sh` →
      `3 of 26 cases failed`, same three case names — `evidence/red/on-head.txt`. Verification: the tally and
      the case names match CI.
- [x] 1.3 Add the case `foreign#fixtureDeclaresEveryBudgetDefault` (helper `engine_budget_keys` + dispatch and
      `PORTABILITY_CASES` registration) and record it red against the unfixed fixture — 4 of 27 failed, the new
      case first — `evidence/red/fixtureDeclaresEveryBudgetDefault.txt`. Verification: the case is red for the
      missing key it names, not for a broken helper.

## 2. The fix

- [x] 2.1 Record `LOOP_MAX_REVIEW_ROUNDS_DEFAULT=2` in
      `tools/fixtures/foreign-project/tools/loop/loop.conf`. Verification: the new case and the three symptom
      cases pass — `evidence/green/suite.txt` (`all 27 cases passed`), and `bash tools/check-portability.sh
      --quiet` still prints `OK — 43 engines and 2 templates name no project value` —
      `evidence/green/check.txt`.
- [x] 2.2 Update the suite's case count where it is documented: `AGENTS.md` (Documentation Map route) and
      `.pi/skills/portability-check/SKILL.md` (description and command table), 26 → 27. Verification:
      `grep -rn "26 cases\|26-case" AGENTS.md .pi/skills/` finds nothing.

## 3. Falsify the bound

- [x] 3.1 `revert-check`: remove `LOOP_MAX_REVIEW_ROUNDS_DEFAULT=2` from the fixture again — 4 of 27 cases
      fail, `foreign#fixtureDeclaresEveryBudgetDefault` among them — restore, re-run green — 27 of 27 —
      `evidence/green/revert-check.txt`. Verification: the same line is the only mutation and the same case is
      the one that fails.

## 4. Verify the change

- [x] 4.1 Structural check in place of the build gate (the delta touches no compiled file, so
      `guidelines/Build.md` §2's scope block applies): `bash tools/check-portability.sh --quiet` and
      `bash tools/check-portability-selftest.sh` — both green, quoted above. No Gradle task, no device and no
      emulator are owed by this change, and none was run.
- [x] 4.2 Spec/case traceability: the one new scenario names `foreign#fixtureDeclaresEveryBudgetDefault`, which
      exists in `tools/portability-cases.sh` and is green in `evidence/green/suite.txt`.
- [x] 4.3 Traceability and root cause, re-read against the tree: the one new scenario names
      `foreign#fixtureDeclaresEveryBudgetDefault`, which exists in `tools/portability-cases.sh` and is green in
      `evidence/green/suite.txt`; `tools/loop/run.sh:73` and `tools/loop/run.sh:80` still read and refuse the
      key as the proposal says, and no engine file is in the diff. Verification: `git status` names only the
      four files of `## Impact`. The independent review of the diff is the run's own, not this task's.

## 5. Archive

- [x] 5.1 Archive the change and confirm the scenario merged into
      `openspec/specs/tooling-portability/spec.md` and the directory exists under
      `openspec/changes/archive/`. Verification: `openspec list --json` no longer lists the change, and
      `grep -n "fixture declares every budget" openspec/specs/tooling-portability/spec.md` finds the scenario.
