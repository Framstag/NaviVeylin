# Tasks: a-child-check-skips-the-gates-own-suite

## 1. The classification

- [x] 1.1 `tools/loop/lib.sh`: extend `is_gate` so a check programme invoked without a narrowing flag (`--case`,
      `--tests`, a case name) answers as the gate's own work, beside the `gate.sh` form it already answers for.
- [x] 1.2 Read `child_check_calls` and `gate_check_programs` together and confirm one call is classified once: the
      report's `gateRepeat` count and the child's bound must read the same answer.

## 2. The cases

- [x] 2.1 `run#aChildCheckSkipsTheGatesOwnSuite` — a stream whose only check call is
      `tools/loop/tests/loop-selftest.sh --from-git <base>` counts 0.
- [x] 2.2 `run#aFocusedCaseIsStillAChildCheck` — a stream running one case with `--case` counts 1, and a child that
      reaches `LOOP_CHILD_CHECKS_MAX` focused checks is still ended.
- [x] 2.3 Declare both cases' files in the driver's own registry (`driver#everyCaseDeclaresTheFilesItCanReach`).

## 3. The config comment, and the falsification

- [x] 3.1 `tools/loop/loop.conf`: state beside `LOOP_CHILD_CHECKS_MAX` what it now counts, quoting the run that
      motivated it (253 child checks, 1.9 h, 4 of 11 items at the bound, 0 of 18 in the archive).
- [x] 3.2 Mutate the extended `is_gate` back to its form and confirm 2.1 goes red; restore and confirm
      `tools/loop/tests/loop-selftest.sh --case run#aChildCheckSkipsTheGatesOwnSuite` passes.
- [x] 3.3 Run `tools/loop/tests/loop-selftest.sh`, `loop-mutations.sh --check` and `tools/check-doc-values.sh`.
