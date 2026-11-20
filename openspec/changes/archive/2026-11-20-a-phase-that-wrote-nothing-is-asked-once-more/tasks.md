# Tasks: a-phase-that-wrote-nothing-is-asked-once-more

## 1. The retry

- [x] 1.1 `tools/loop/run.sh`: where `change_answered` answers false, relaunch the phase once from the checkpoint
      prompt when the child exited 0, and record the retry on the item.
- [x] 1.2 Confirm the bound guard (#224's `LOOP_CHILD_TIMEOUT` / `gate_exit -eq 0`) is read by the new arm, so a child
      the run ended is never relaunched.
- [x] 1.3 Block with the attempt count in the reason when the second answer is silent too.

## 2. The cases

- [x] 2.1 `run#aPhaseThatWroteNothingIsAskedOnceMore` — an empty change report, exit 0, one relaunch, an answer at the
      second asking.
- [x] 2.2 `run#aChildTheRunEndedIsNotAskedAgain` — a report carrying `LOOP-TIMEOUT`, no relaunch.
- [x] 2.3 Declare both cases' files in the driver's own registry (`driver#everyCaseDeclaresTheFilesItCanReach`).

## 3. The report, and the falsification

- [x] 3.1 `tools/loop/report.sh`: count the retried items on the trend block, so a run that pays this twice says so.
- [x] 3.2 Mutate the retry arm away and confirm 2.1 goes red; restore, then run
      `tools/loop/tests/loop-selftest.sh`, `loop-mutations.sh --check` and `tools/check-doc-values.sh`.
