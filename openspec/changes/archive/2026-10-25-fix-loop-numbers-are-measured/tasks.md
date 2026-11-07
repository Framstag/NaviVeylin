# Tasks — a number a change quotes is a measurement, and the block carries the ones a worker used to guess

Parent capability: `agent-issue-loop`. The requirements this change modifies are "The worker is handed one command
that answers what the change owes" and "A review's task states what the round may run, and quotes the gate instead
of running it".

## 1. The cases, red on HEAD before their fix

- [x] 1.1 `measure#printsTheSuiteCountAndTheGateTiming`, in the style of `measure#printsTheNumbersTheReviewerAsksFor`:
      a throwaway project whose profile names a case-listing command that prints three cases and a tally directory
      holding two tallies with their own `time`. Asserts `LOOP-MEASURE-SUITE cases=3` with the command named beside
      it, `LOOP-MEASURE-GATE seconds=12 suites=2`, `time=7s` on a tally line, and — with the key stripped — the
      `cases=-` line. Parent: "The worker is handed one command that answers what the change owes".
      **Verify**: on HEAD the block carries no `LOOP-MEASURE-SUITE` line (`evidence/red-on-head-measure_printsTheSuiteCountAndTheGateTiming.log`);
      after 2.2 it passes (`evidence/green-measure_printsTheSuiteCountAndTheGateTiming.log`).
- [x] 1.2 The worker rule, asserted by extending `prompt#workerIsHandedTheMeasureBlock`. Parent: the same requirement.
      **Verify**: on HEAD the phrase is absent (`evidence/red-on-head-prompt_workerIsHandedTheMeasureBlock.log`);
      after 2.3 it is there (`evidence/green-prompt_workerIsHandedTheMeasureBlock.log`).
- [x] 1.3 The reviewer rule, asserted by extending `prompt#reviewerIsBoundedToReading`. Parent: "A review's task
      states what the round may run, and quotes the gate instead of running it".
      **Verify**: on HEAD the phrase is absent (`evidence/red-on-head-prompt_reviewerIsBoundedToReading.log`);
      after 2.4 it is there (`evidence/green-prompt_reviewerIsBoundedToReading.log`).

## 2. The fix

- [x] 2.1 `tools/loop/loop.conf` — `LOOP_SUITE_CASES_CMD`, the project's case-listing command, with the measurement
      that motivates it.
- [x] 2.2 `tools/loop/measure.sh` — run the case-listing command and print `LOOP-MEASURE-SUITE cases=<n> cmd=<cmd>`;
      read each tally's `time` into its line; sum the gate directory's times into `LOOP-MEASURE-GATE seconds= suites=`.
- [x] 2.3 `tools/loop/prompts/worker.md` — the number rule, and the two block line names in the measure section.
- [x] 2.4 `tools/loop/prompts/reviewer.md` — item 6 and the `NOTE:` definition carry the number rule and its grading.
- [x] 2.5 `tools/loop-mutations.sh` — records `61.1`–`61.4`, one per invariant (the count line, the timing line, the
      worker sentence, the reviewer sentence).
      **Verify**: `bash tools/loop-mutations.sh --only '61.1 61.2 61.3 61.4' --out <log>` → `red-when-mutated: 4 ·
      not-noticed: 0 · restore-failures: 0` (`evidence/revert-check.log`).

## 3. Check the rule covers the failure it was built for

- [x] 3.1 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/archive/2026-10-25-fix-loop-numbers-are-measured --root .`
      resolves every pointer this change carries (the path is the archive entry the change moves to).
- [x] 3.2 `openspec validate fix-loop-numbers-are-measured --strict` passes.

## 4. The gate and the record

- [x] 4.1 The delta's focused cases: the three named cases green (`evidence/green-*.log`), the four mutation records
      red on the invariant they remove (`evidence/revert-check.log`).
- [x] 4.2 The change's own gate: `bash tools/loop/gate.sh --base <base>` run once, its `LOOP-GATE:` line quoted into
      `evidence/`.
- [x] 4.3 The change is archived, its delta merged into `openspec/specs/agent-issue-loop/spec.md`.
