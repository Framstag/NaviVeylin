# Tasks — fix-loop-selftest-selects-the-cases-a-delta-owns

- [x] 1. Red test on HEAD: five cases in `tools/loop-cases.sh` —
  `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`, `driver#aFileSetThatNamesNoCaseIsRefused`,
  `driver#aCoreFileOwesEveryCase`, `driver#everyCaseDeclaresTheFilesItCanReach`,
  `gate#theDeltaHandsASuiteTheBaseItMeasured` — with the nested-suite helper `nested_suite` and the four
  declared stub cases, and their registration in `LOOP_SHELL_CASES` and the name-to-function map.
  Verification: four of the five exit 1 against the driver at the base commit `6683376e`
  (`git show 6683376e:tools/loop-selftest.sh`) with `loop-selftest: unknown argument --files`/`--case-files`,
  and the fifth against `git show 6683376e:tools/loop/gate.sh` with the literal `{base}` left in the plan;
  `evidence/red-driver-group.xml` (`tests="10" failures="4"`), `evidence/red-gate-group.xml`
  (`tests="12" failures="1"`), transcripts `evidence/red-driver.log` and `evidence/red-gate.log`.
- [x] 2. `tools/loop-cases.sh`: the declaration `# files: <token> …` in the first line of all 190 case bodies, the
  convention in the header, `loop_case_core_files`, `loop_case_helper_files` and `loop_case_body_paths`.
  Verification: `bash tools/loop-selftest.sh --case-files` prints 190 rows and no `<undeclared>`;
  `--list` still names 195 cases; `--list --files tools/loop/worktree.sh` names 32 of them, `--files
  tools/loop/run.sh` 110, `--files tools/loop/gate.sh` 26, `--files tools/loop/lib.sh` 195.
- [x] 3. `tools/loop-selftest.sh`: `--files`, `--from-git <base>`, `--case-files`, the declaration parse, the
  selection predicate with the directory-token and core-file rules, the refusal (exit 2) and the
  `selected N of M cases` statement.
  Verification: `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`, `driver#aFileSetThatNamesNoCaseIsRefused`,
  `driver#aCoreFileOwesEveryCase` and `driver#everyCaseDeclaresTheFilesItCanReach` pass;
  `--group driver` green; `--list` unchanged without a filter.
- [x] 4. `tools/loop/gate.sh`: `{base}` substituted in an owed command; `tools/loop/loop.conf`: the self-test's
  two pairs run as `tools/loop-selftest.sh --from-git {base}`, and the key's comment states the bound.
  Verification: `gate#theDeltaHandsASuiteTheBaseItMeasured` passes and `--group gate` is green (12 cases);
  `bash tools/loop/gate.sh --plan --base HEAD~1` prints `tools/loop-selftest.sh --from-git <sha>` and no token.
- [x] 5. `tools/loop-mutations.sh`: records `53.1`–`53.5`, one per invariant the change adds.
  Verification: `bash tools/loop-mutations.sh --check` → `all 116 cases passed`; `--only '53.1 53.2 53.3 53.4 53.5'`
  → `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0`, transcript `evidence/revert-check-53.log`.
- [x] 6. `guidelines/Backlog.md`, section "7. The loop": the paragraph beside the streamed gate states the declaration, the two
  filters, the refusal, the core-file rule and this item's numbers.
  Verification: the paragraph is in section 7 and names `{base}`, `--files`, `--from-git`, the refusal and the
  measured counts; `tools/check-doc-routes.sh` resolves every section reference (owed by the `*.md` path).
- [x] 7. The capability delta validates, and the requirement body is inside the validator's limit.
  Verification: `openspec validate fix-loop-selftest-selects-the-cases-a-delta-owns --strict` → `Change
  'fix-loop-selftest-selects-the-cases-a-delta-owns' is valid`; the body is 452 characters.
- [x] 8. The project's gate, run once. This change touches no compiled file, so it owes the two shell suites and
  the record check for `tools/loop/**`, the doc-route check for the `*.md` paths and the classification check for
  `openspec/*` — and it is owed the whole self-test, because the delta touches the case file, the driver, the
  profile and the library, all of which are core files.
  Verification: `./tools/loop/gate.sh` exits 0 — its summary line names six suites and counts 345 cases with no
  failure and no suite that reported none (`evidence/gate.log`, per-suite tallies `evidence/gate-tallies/`), and
  the self-test's own tally carries `tests="195" failures="0"`. The command
  was started with `LOOP_CONF` naming the worktree's own profile, because the run hands this child the project
  tree's; the compiled command ran too, because the resolved base `c03e7e0b` is older than the items already
  landed on the branch.
- [x] 9. Evidence and archive: every cited XML under this change's `evidence/`, the pointers checked by
  `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-selftest-selects-the-cases-a-delta-owns --root .`
  (`evidence-check: PASS`, 304 `file:line` pointers resolved), the change archived as
  `openspec/changes/archive/2026-10-23-fix-loop-selftest-selects-the-cases-a-delta-owns` and the requirement
  merged into `openspec/specs/agent-issue-loop/spec.md:1743`.
