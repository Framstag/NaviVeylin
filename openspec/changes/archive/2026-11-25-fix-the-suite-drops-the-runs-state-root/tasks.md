# Tasks — the suite drops the run's state root

- [x] 1. The red case first, in `tools/loop/tests/loop-cases.sh` (`case_driver_theSuiteDoesNotInheritTheRunsRoots`):
      `LOOP_STATE_FILE` joins the `for` list at the top of the body, and the comment says which failure the fourth
      name holds. Red proof from a shell the run exported —
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#theSuiteDoesNotInheritTheRunsRoots'` — first failure
      `the driver handed the run's own root to the suite: LOOP_STATE_FILE=…`. A shell suite carries no JUnit XML of
      its own (`.pi/skills/revert-check`, "When the gate is a shell self-test, not Gradle"): the tally is the suite's
      own line, transcribed into the carrier shape in `evidence/red-on-HEAD.xml`. Evidence:
      `evidence/red-on-HEAD.log`.

- [x] 2. The arm a case's own suite holds, in the same case: a suite a case starts under
      `LOOP_STATE_FILE=<scratch>/run.json` must hand no case that path — the `LOOP_SELFTEST_NESTED_ARM` guard keeps the
      arm from nesting a third time. Verified non-vacuous: with the drop removed from `loop-selftest.sh` and an outer
      environment that exports no `LOOP_STATE_FILE`, the case reddens on that arm alone and names a path under its own
      scratch tree. Evidence: `evidence/nested-arm-red.log`.

- [x] 3. Add the drop: `LOOP_STATE_FILE` on the `unset` list in `tools/loop/tests/loop-selftest.sh`, and the paragraph
      above it extended with the reason the state file is a root a case may not inherit — the run's record stands
      under this tree, so a case that inherited it wrote its fixture items into the live run. Verified by task 1's
      case, green: `PASS driver#theSuiteDoesNotInheritTheRunsRoots` (`evidence/green.log`).

- [x] 4. The mutation records, in `tools/loop/tests/loop-mutations.sh`: `1009.3`'s pattern realigned to the extended
      line (a mutation that no longer matches its target never landed), and `1066.1` added, which puts back the three
      roots #1009 measured and leaves the state file out. Both redden the named case and go green after the restore;
      `mutation#everyDeclaredRecordApplies` passes, so both patterns land. Evidence: `evidence/mutations.log`,
      `evidence/green.log`.

- [x] 5. Structural check of the files this change edits, in place of a build (the delta edits no compiled path):
      `bash -n` on the three files, `bash tools/check-case-names.sh`, `bash tools/check-portability.sh` and
      `openspec validate --strict fix-the-suite-drops-the-runs-state-root` — all green. Evidence:
      `evidence/structural-checks.log`.

- [x] 6. The surface, counted rather than planned: `tools/look.sh 'LOOP_STATE_FILE'` — 136 matches in 43 files, and
      the rule this change moves is asserted in three places (the `unset` line, the case's guard list, the mutation
      records). Evidence: `evidence/surface.log`.

- [x] 7. The measuring command's block for this change, kept beside the evidence:
      `./tools/loop/measure.sh --change fix-the-suite-drops-the-runs-state-root`. Its `LOOP-MEASURE-CHECK` lines are
      the gate's and are quoted, not run again here. Evidence: `evidence/measure.log`.

- [x] 8. The change complete before the gate: every log it cites under `evidence/`, and
      `bash tools/loop/evidence-check.sh <this change> --root .` run and answered. Evidence:
      `evidence/evidence-check.log`.

- [x] 9. The repair the recorded decision chose (owner, 2026-11-24T21:08:45Z, option 1), on a tree the run put back
      on its base after the first gate: the case's `# files:` declaration takes `tools/loop/run.sh`, the script its
      new nested arm reaches, so `driver#everyCaseDeclaresTheFilesItCanReach` no longer fails on this change's paths;
      record `1009.3`'s pattern follows the extended `unset` line and record `1066.1` is the fourth name's own
      mutation. Verified serially, not beside another mutation run: `PASS driver#everyCaseDeclaresTheFilesItCanReach`,
      `PASS driver#theSuiteDoesNotInheritTheRunsRoots`, `bash tools/loop/tests/loop-mutations.sh --check` — all 463
      records apply to the file they name — `--only '1009.3 1066.1'` red on both and green after each restore, and
      `bash tools/loop/tests/loop-mutations-selftest.sh` green, 39 of 39. Evidence: `evidence/structural-checks.log`,
      `evidence/mutations.log`.

- [x] 10. The repair the recorded decision chose (owner, 2026-11-25T05:25:46Z, option A), on a tree the run put back on
      its base after that gate: record `1066.1`'s file field spells `tools/loop/tests/loop-selftest.sh`, not
      `tests/loop-selftest.sh`, so `tests/loop-selftest.sh` keeps the single record `1009.3` gives it and
      `tools/loop/phase.sh` stays the cheapest file the file-set leg scopes by
      (`mutation#theFileSetLegScopesByTheCheapestFile`, green in `evidence/mutations-selftest.log`, 39 of 39). Every
      line this round re-took on base `184376ec`, in a shell that exports `LOOP_STATE_FILE`: the case red before the
      drop (`evidence/red-on-HEAD.log`, carried into `evidence/red-on-HEAD.xml`), green after it
      (`evidence/green.log`), `--check` green on all 465 records and `--only '1009.3 1066.1'` red on both for the
      reason each names and green after the restore (`evidence/mutations.log`), the declaration case green, and the
      structural checks green (`evidence/structural-checks.log`).
