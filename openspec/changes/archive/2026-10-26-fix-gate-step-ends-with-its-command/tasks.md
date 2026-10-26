# fix-gate-step-ends-with-its-command — tasks

- [x] 1.1 (`agent-issue-loop` — *The gate streams a suite's output while it runs*) `tools/loop/gate.sh`: the step's
      output is a file the command writes, read on the console by `follow_stream` (`tail -c +N`, drained on the stop
      file), and the step's status and wall clock are the command's own — no `| tee`, no `PIPESTATUS`. Verify:
      `bash tools/loop-selftest.sh --case gate#aStepEndsWithItsCommandNotWithItsWatchers` green, and the three cases
      that assert the old observable green (`gate#theSuitesOutputReachesTheConsoleWhileItRuns`,
      `gate#aFailingSuiteIsReadFromTheSameStream`, `gate#theSummaryNamesWhatEachSuiteCost`) — `evidence/green-cases.log`.
- [x] 1.2 `tools/loop-cases.sh`: the new case `gate#aStepEndsWithItsCommandNotWithItsWatchers`, in the declared set,
      the dispatch table and the file declaration. It is red on the base (`evidence/red-on-head.log`,
      `evidence/red-on-head.xml`) and green after 1.1. Verify: `bash tools/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` and `driver#theListIsFilteredTheSameWayASuiteRunIs` green —
      `evidence/green-cases.log`.
- [x] 1.3 `tools/loop-mutations.sh`: `63.1` restores `$cmd 2>&1 | tee "$stream" &` in the command's place and is
      noticed by `gate#aStepEndsWithItsCommandNotWithItsWatchers`; `46.1` no longer quotes the pipe and is noticed by
      `gate#theSuitesOutputReachesTheConsoleWhileItRuns`. Verify: `bash tools/loop-mutations.sh --check` (172 records
      apply) and `bash tools/loop-mutations.sh --only '46.1 63.1'` → `red-when-mutated: 2 · not-noticed: 0 ·
      restore-failures: 0` (`evidence/loop-mutations-315.log`).
- [x] 2.1 The spec delta `specs/agent-issue-loop/spec.md`: *The gate streams a suite's output while it runs* carries
      both shipped scenarios verbatim plus *A step ends with the command it ran*. Verify:
      `openspec validate 2026-10-26-fix-gate-step-ends-with-its-command --strict` and the body's length (412
      characters, the limit is 500) — `evidence/validate.log`.
- [x] 3.1 Structural checks for a delta that touches no compiled file: `check-portability`, the loop's own case set,
      the issue tooling, `check-doc-routes`, `check-openspec-hygiene`, `check-todo-references`,
      `gen-feature-list.sh --check-classification`, the mutation lists and `mutate-selftest`. No Gradle build is owed.
- [x] 3.2 Gate: `./tools/loop/gate.sh` once. Result quoted in `evidence/gate.log`; its tallies copied from the
      profile's tally directory into `evidence/`.
- [x] 3.3 The change's own evidence pointers: `bash tools/loop/evidence-check.sh
      openspec/changes/2026-10-26-fix-gate-step-ends-with-its-command --root .` PASS (`evidence/evidence-check.log`).
- [x] 3.4 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
