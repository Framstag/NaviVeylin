# Tasks — the fallback arm that read a throwable as a case is deleted

The base is `7aea9b5d7999f4a8c1689d39aa66247386dc3728`. The gate is not this child's to run: the run hands this
item's one gate to the child after it, so the tasks that write the archive and take the gate result are that child's.

## 1. The red case first

- [x] 1.1 `gate#aLoneAssertionErrorIsNotACase` in `tools/loop/tests/loop-cases.sh`: a gate whose one step prints
      `> Task :app:testMobileDebugUnitTest FAILED`, the `java.lang.AssertionError` under it, no per-test `FAILED`
      line and exits 1; the case asserts the gate exits non-zero, its line reads `LOOP-GATE: tests=0 failures=0
      errors=1`, its tally reads `tests="0" failures="0" errors="1"`, and the step is still named as the failing one.
      Registered in `LOOP_SHELL_CASES` and in the case dispatch, with its `# files:` declaration.
      Verify (the cheapest check a case-writing delta owes, and the plan's first line):
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/declaration-case.log`).
- [x] 1.2 Red against the engine at the base: `git archive 7aea9b5d7999f4a8c1689d39aa66247386dc3728 tools/loop
      tools/issues` into the item's `scratch/base-engine-916`, handed to the suite as `LOOP_ENGINE`, one
      `bash tools/loop/tests/loop-selftest.sh --case 'gate#aLoneAssertionErrorIsNotACase'` run → `FAIL
      gate#aLoneAssertionErrorIsNotACase`, `1 of 1 cases failed`, the base's gate line
      `LOOP-GATE: tests=0 failures=1 errors=0` where the case asserts `tests=0 failures=0 errors=1`
      (`evidence/red-on-HEAD-lone-assertion.log`), transcribed as `evidence/red-on-HEAD-lone-assertion.xml`
      (`tests=1 failures=1 errors=0`, `2026-11-18T22:58:00Z`).

## 2. The engine — `tools/loop/gate.sh`

- [x] 2.1 `finish_step`: the fallback arm is deleted, so a non-zero step's failures are the `<class> > <case> FAILED`
      lines it named and nothing else; a step that named none keeps #268's error (`tests` = the count the suite
      reported, `failures` = 0, `errors` = 1). The comment above the reading states that it is the only one.
      Verify: the red case passes — `evidence/red-case-green.log` carries `PASS gate#aLoneAssertionErrorIsNotACase`
      (`2026-11-18T22:58:05Z`), with the item's `events.jsonl` recording the invocation.
- [x] 2.2 The cases the delta reaches: `bash tools/loop/tests/loop-selftest.sh --case
      'gate#aTestFailureIsAFailureNotAnError'` → `PASS` (`evidence/focused-916-first-arm.log`), and
      `bash tools/loop/tests/loop-selftest.sh --case 'gate#aFailingSuite*'` → `all 3 cases passed`
      (`evidence/focused-268-cases.log`) — the summary shapes and the no-case error the arm never reached.
- [x] 2.3 The records that keep the two readings: `916.2` in `tools/loop/tests/loop-mutations.sh`, putting the
      eliminated reading back in place of the per-test one. Verify: `bash tools/loop/tests/loop-mutations.sh --only
      916.2` → `RED 916.2 … restore … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-916.2.log`); and the record the first round landed still holds its case with this change's
      engine — `--only 916.1` → the same three lines (`evidence/mutation-916.1.log`).

## 3. The documents

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7c "The gate and its suite", the paragraph after #268's: the per-test
      reading is the only one taken, and the shape a step that named no case prints is
      `gate#aLoneAssertionErrorIsNotACase`, kept by record `916.2`. Verify: `tools/check-doc-values.sh` and
      `tools/check-doc-routes.sh` — the structural checks of the file this delta edits, run in place of a build it
      owes none of — both exit 0 (`evidence/doc-checks.log`).
- [x] 3.2 The capability delta in this change: the requirement "A suite that reported no summary invents no number"
      MODIFIED (the throwable named as not-a-case), one scenario added, the three it carried restated. Verify:
      `tools/check-openspec-hygiene.sh` (`evidence/doc-checks.log`).

## 4. Evidence, and what the gate child writes

- [x] 4.1 This change's `evidence/` is written from the runs above (`red-on-HEAD-lone-assertion.log`/`.xml`,
      `red-on-HEAD-gate-tally.xml`, `red-case-green.log`, `red-case-green-gate-tally.xml`, `declaration-case.log`,
      `focused-916-first-arm.log`, `focused-268-cases.log`, `mutation-916.2.log`, `mutation-916.1.log`,
      `doc-checks.log`, `evidence-check.log`), each copied at the moment it was produced.
- [ ] 4.2 The gate child's: run `bash tools/loop/evidence-check.sh <change dir> --root .` over this `evidence/`,
      then archive the change under `openspec/changes/archive/<date>-fix-the-assertion-text-is-not-a-case/` and run
      `./tools/loop/gate.sh --base 7aea9b5d7999f4a8c1689d39aa66247386dc3728` over the completed tree, recording its
      line.
