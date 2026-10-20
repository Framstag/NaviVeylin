# Tasks — fix-value-flag-missing-value

## 1. The red case

- [x] 1.1 Add `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `usage#bareValueFlagDoesNotSwallowTheNextFlag`
  and `usage#noUnguardedValueFlagRemains` to `tools/loop-cases.sh` (registry, dispatch and bodies), and
  `create#bareValueFlagNeedsAValue` to `tools/issue-tooling-cases.sh`. Every call is bounded by `timeout 20`,
  so a parser that hangs fails the case instead of taking the suite with it. Verification: the four cases, run
  on the base commit, fail —
  `tools/loop-selftest.sh --case …` reporting `exited 124 instead of 2`, `'needs a value' not found in: run:
  --push must be yes or no`, and `152 value flags still consume their value before checking it`, and
  `'--finding needs a value' not found in: create: unknown argument bug`. The red runs are archived per case:
  `evidence/red-on-HEAD.log` (2026-10-20T07:11:51Z — bare flag, swallow, create) and
  `evidence/red-on-HEAD-sweep.log` (2026-10-20T07:11:37Z — the sweep, 152).
  `evidence/red-on-HEAD-first-run.log` is the sweep's first run (154: it still counted the two literals inside
  `loop-cases.sh`) and is kept only to show why that number was superseded. Parent: `tooling-portability`,
  "A value flag refuses a missing value before it consumes it".

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
  change); the sweep case leaves no `shift 2 ;;` arm without the check.

## 3. Verification

- [x] 3.1 The four cases above pass after the change (log `evidence/green-after-fix.log`,
  2026-10-20T07:14:17Z).
- [x] 3.2 The affected suites, all green: `tools/issue-tooling-selftest.sh` 91 of 91 (log
  `evidence/issue-tooling-selftest.log`), `tools/sensor-selftest.sh` 43 of 43
  (`evidence/sensor-selftest.log`), `tools/check-portability.sh` OK — 43 engines and 2 templates name no project
  value (`evidence/check-portability.log`), `tools/check-doc-routes-selftest.sh` 10 of 10
  (`evidence/check-doc-routes-selftest.log`), `tools/check-todo-references-selftest.sh` 10 of 10
  (`evidence/check-todo-references-selftest.log`), `tools/check-compose-sandbox-rule-selftest.sh` all three
  cases (`evidence/check-compose-sandbox-rule-selftest.log`), `tools/declared-cases-selftest.sh` 12 of 12
  (`evidence/declared-cases.log`). `tools/loop-selftest.sh` 111 of 112 (`evidence/loop-selftest.log`; 112 cases
  then — the round-2 corrections add the single-shot case, see 4.2): the one
  failure, `worktree#keepsByDefaultAndRemovesOnRequest`, is an artefact of the suite's root path containing
  `worktrees/loop` — the fixture repository then carries that string and the case greps for it in
  `git worktree list`; the identical case passes from the project's tree with the scratch root moved to
  `/tmp/loop-probe-tmp` (`evidence/project-tree-worktree-case.log`, the engine's checksum recorded there,
  nothing written into the project's tree).
- [x] 3.3 No compiled file is touched, so the change replaces the Gradle build task with that structural check
  and says so instead of running a build "for completeness" (`guidelines/Build.md` §2, the scope block). The
  gate `./gradlew test -PforceTests --no-build-cache` was run once for the run's own evidence —
  BUILD SUCCESSFUL in 5m 36s, 705 JUnit XMLs, tests=5680 failures=0 errors=0 skipped=0
  (`evidence/gate.log`, `evidence/gate-tally.txt`) — and is quoted as what it is, not as proof that this change
  works.
- [x] 3.4 Exclusions, named and now recorded: `tools/mutate-selftest.sh` fails one case that predates this
  change (`the portability set has eight records — expected 8, got 13` — `tools/portability-mutations.json`
  holds 13 records and is not in this change's diff): 26 passed, 1 failed, exit 1
  (`evidence/excluded-suites.log`). `tools/loop-mutations-selftest.sh` passed this time, exit 0, with `tools/`
  byte-identical afterwards (same log), so the round-1 exclusion of it is withdrawn. Neither run is caused by,
  nor repaired by, this change.

## 4. The review's corrections (2026-10-20)

The read-only review of round 1 returned two blocking findings and seven notes. Each correction below names
the evidence that closes it.

- [x] 4.1 **B1, the ten single-shot arms.** `tools/loop-selftest.sh`, `tools/sensor-selftest.sh`,
  `tools/build-inputs-selftest.sh`, `tools/issues/check-metadata-selftest.sh`,
  `tools/issue-tooling-selftest.sh`, `tools/check-portability-selftest.sh`,
  `tools/resolve-stale-selftest.sh`, `tools/sensor-mutations.sh`, `tools/build-inputs-mutations.sh` and
  `tools/issues/metadata-mutations.sh` now carry the inline check in their `--case`/`--only` arm, the same
  expression the nine standalone engines use. Verification: `evidence/green-round2.log` — each of the ten
  refuses a bare flag with exit 2 and `--case needs a value`/`--only needs a value`, and each still lists its
  cases in its normal `--list` mode; `evidence/red-round2-single-shot-and-sweep.log` — with the ten guards
  removed, `bash tools/sensor-selftest.sh --case` printed `all 43 cases passed` and exited 0, exactly the
  operator hazard the review measured.
- [x] 4.2 **B1, the sweep could not see them.** `case_usage_noUnguardedValueFlagRemains` now reads arm lines
  only (`:--flag) …`) for either `${2:-}` or `shift 2 ;;`, scans every harness (`-selftest.sh`,
  `-mutations.sh`) and excludes only the `*-cases.sh` harness files. New case
  `usage#bareValueFlagInASingleShotHarnessIsAUsageError` asserts the refusal of all ten, so the scenario has a
  case of its own rather than resting on a grep. Verification: `evidence/red-round2-single-shot-and-sweep.log`
  — 10 offenders named and the new case red with the guards removed; both green after the restore.
  Requirement text in `tooling-portability` now states the scan and the one permitted exclusion, so the rule is
  not only a case comment.
- [x] 4.3 **B2, the red-evidence pointers and the 152/154.** The `152` now cites
  `evidence/red-on-HEAD-sweep.log` (the round-1 run's own log, archived in this pass); the `154` run is
  labelled superseded; the three-case log is no longer claimed to hold four cases. Verification: the files are
  in `evidence/`, and `proposal.md`, `design.md` and this file point at them per row.
- [x] 4.4 **NOTE, the assertion named no flag.** The bare-flag case asserts `<flag> needs a value` for the flag
  it passed, and the swallow case asserts the *first* flag (`--push needs a value`, `--finding needs a value`,
  `--case needs a value`) instead of the bare phrase `needs a value`. Verification: the three swallow entries
  in `tools/loop-cases.sh`, green in `evidence/green-round2.log`.
- [x] 4.5 **NOTE, the adapter's rationale was orphaned.** The three-line comment that belongs to
  `case_adapter_modeFlagIsItsOwnArgument` sits directly above it again; the value-flag block (comment and
  cases) was moved above it. Verification: `evidence/green-round2.log` (the adapter case still passes) and the
  file order itself.
- [x] 4.6 **NOTE, the `--`-leading value had no stated escape.** `proposal.md` and `design.md` now say the
  limitation and that there is no escape — no flag accepts `--flag=value` — and the requirement states it, so
  a later need adds that form instead of weakening the guard. Verification: the requirement text and both
  rationale paragraphs.
- [x] 4.7 **NOTE, the suite's exclusions had no logs.** Both excluded suites were run in this pass
  (`evidence/excluded-suites.log`): `mutate-selftest.sh` reproduces its one pre-existing failure,
  `loop-mutations-selftest.sh` passes and left `tools/` unchanged. Verification: that log.
- [x] 4.8 **NOTE, the unasserted clause and the inline root cause.** Scenario 1's THEN no longer promises "no
  header, no state write, no child started", which neither case asserts; the root cause moved out of the
  requirement sentence into `design.md`, and the requirement states only the contract. Verification: the
  spec's requirement block and `design.md`, "Root cause".
