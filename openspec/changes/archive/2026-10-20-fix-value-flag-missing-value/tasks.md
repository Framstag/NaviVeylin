# Tasks — fix-value-flag-missing-value

## 1. The red case

- [x] 1.1 Add `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `usage#bareValueFlagDoesNotSwallowTheNextFlag`
  and `usage#noUnguardedValueFlagRemains` to `tools/loop-cases.sh` (registry, dispatch and bodies), and
  `create#bareValueFlagNeedsAValue` to `tools/issue-tooling-cases.sh`. Every call is bounded by `timeout 20`,
  so a parser that hangs fails the case instead of taking the suite with it. Verification: the four cases, run
  on the base commit, fail —
  `tools/loop-selftest.sh --case …` reporting `exited 124 instead of 2`, `'needs a value' not found in: run:
  --push must be yes or no`, `152 value flags still consume their value before checking it`, and
  `'--finding needs a value' not found in: create: unknown argument bug` (log `evidence/red-on-HEAD.log`,
  2026-10-20T07:11Z). Parent: `tooling-portability`, "A value flag refuses a missing value before it consumes
  it".

## 2. The fix

- [x] 2.1 Add `need_value` to `tools/issues/lib.sh`, the root of the engine's source graph (sourced by
  `tools/issues/**`, and through them by `tools/loop/lib.sh` and `tools/sensors/lib.sh`). It refuses a missing
  value and a value that is itself a flag, names `$0`'s tool and the flag, exits 2, and shifts nothing.
  Verification: `bash tools/issues/lib.sh` still discovers, and `bash -n tools/issues/lib.sh` is clean;
  `bash tools/loop/run.sh --push` prints `run: --push needs a value` and exits 2.
- [x] 2.2 Insert `need_value "$@"` at the head of the 122 value-flag arms of the 26 engines that source a
  library, and the same check inline at the 30 arms of the 9 engines that source none. Verification:
  `bash -n` clean on all 38 changed scripts; `shellcheck -S warning` reports no new warning (the one line that
  moved under a pre-existing `SC2034` is `tools/loop/labels.sh:50`, whose unused variable predates the
  change); `grep -rn 'shift 2 ;;' tools --include='*.sh'` leaves no arm without the check.

## 3. Verification

- [x] 3.1 The four cases above pass after the change (log `evidence/green-after-fix.log`,
  2026-10-20T07:14Z).
- [x] 3.2 The affected suites, all green: `tools/issue-tooling-selftest.sh` 91 of 91 (log
  `evidence/issue-tooling-selftest.log`), `tools/sensor-selftest.sh` 43 of 43
  (`evidence/sensor-selftest.log`), `tools/check-portability.sh` OK — 43 engines and 2 templates name no project
  value (the helper adds none), `tools/check-doc-routes-selftest.sh` 10 of 10,
  `tools/check-todo-references-selftest.sh` 10 of 10, `tools/check-compose-sandbox-rule-selftest.sh` all three
  cases. `tools/loop-selftest.sh` 111 of 112 (`evidence/loop-selftest.log`): the one failure,
  `worktree#keepsByDefaultAndRemovesOnRequest`, is an artefact of running the suite from inside the loop's own
  worktree — the fixture repository's path then contains `worktrees/loop`, which the case greps for in
  `git worktree list` — and the same case passes in the project's tree at the same commit.
- [x] 3.3 No compiled file is touched, so the change replaces the Gradle build task with that structural check
  and says so instead of running a build "for completeness" (`guidelines/Build.md` §2, the scope block). The
  gate `./gradlew test -PforceTests --no-build-cache` is still run once for the run's own evidence —
  BUILD SUCCESSFUL in 5m 36s, 705 JUnit XMLs, tests=5680 failures=0 errors=0 skipped=0
  (`evidence/gate.log`, `evidence/gate-tally.txt`) — and is quoted as what it is, not as proof that this change
  works.
- [x] 3.4 Exclusions, named: `tools/mutate-selftest.sh` fails one case that predates this change
  (`the portability set has eight records — expected 8, got 13` — `tools/portability-mutations.json` holds 13
  records and is not in this change's diff), and `tools/loop-mutations-selftest.sh` reports
  `RESTORE-FAILED … stayed red after the restore` on cases that themselves fail in this environment, so its
  guard cannot hold here (`tools/check-portability-selftest.sh` refuses to run from a `.pi/` path at all, which
  the loop's own worktree is). Neither is caused by, nor repaired by, this change.
