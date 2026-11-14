# Tasks — the checks a child runs by hand are bounded while it runs

## 1. The bound and the reader that counts it

- [x] 1.1 `tools/loop/loop.conf`: record `LOOP_CHILD_CHECKS_MAX=10` with the measurement it is set from — 226 hand-run
  checks holding 1.3 h of the 3.1 h gate bucket of the run started 2026-11-13T05:47:11Z — and the reason for the
  figure: §2a's inner loop, the affected group before and after the fix, the mutation set and the structural checks
  are eight calls, with two spare. — *spec: "The checks a child runs by hand are bounded while it runs"; verified by
  the header assertion in `run#childPastItsCheckBoundIsCut` and by mutation `882.1`.*
- [x] 1.2 `tools/loop/lib.sh`: move `gate_program` out of `state.sh` beside its two readers, and add
  `gate_check_programs` (the programs `LOOP_GATE_SUITES`, `LOOP_GATE_FIRST_STEPS` and `LOOP_GATE_COMPILED_CMD` start,
  each entry `<glob>|<command>`), `gate_command_jq` (the definitions the report already classifies with) and
  `child_check_calls` (the incremental read over the lines the stream has gained). — *spec: same requirement,
  scenario "A call that only names a check's path is not a check"; verified by `run#childPastItsCheckBoundIsCut` and
  mutation `882.2`.*
- [x] 1.3 `tools/loop/run.sh`: read the bound through `loop_conf_value`, normalise a non-numeric value to `0`, and
  print it in the run's header as `child-checks-max=…`, so the bound a run enforces is the bound it names. —
  *verified by the header assertion in `run#childPastItsCheckBoundIsCut`.*
- [x] 1.4 `tools/loop/run.sh`: in the child wait, take the count in the same whole second as the turn and context
  readings, end the process group past the bound, and write the `LOOP-TIMEOUT` line naming the key, the bound and the
  count that reached it. — *spec: same requirement, scenario "A child past the recorded check bound is ended";
  verified by `run#childPastItsCheckBoundIsCut` and mutation `882.1`.*
- [x] 1.5 `tools/loop/state.sh`: read the report's classification from `lib.sh`'s shared definitions rather than
  carrying its own copy, keeping the arithmetic it already printed. — *verified by
  `report#aCallThatNamesTheGateIsNotAGateRun` and `report#aCheckTheGateWouldRunIsTheGateTime` green under their own
  mutations.*

## 2. The child is told what it may spend

- [x] 2.1 `tools/loop/prompts/worker-gate.md`: state the bound beside `LOOP_GATE_RUNS_MAX` — what is counted, when it
  is counted, and that the focused case the change's own diagnosis owes is inside it, so applying and reading the
  result is the child's work and the measurements are the gate's. — *verified by
  `prompt#thePhasePromptNamesWhatItsShellRefuses`, which reads the same skeleton and holds every repository path it
  names to a path the project root carries.*

## 3. Cases and their falsification

- [x] 3.1 `tools/loop/tests/loop-cases.sh`: new case `run#childPastItsCheckBoundIsCut` — a child appends four
  `turn_end` lines (two reads of the gate's file, two checks) and then blocks on a fifo that is never released, against
  a recorded bound of 2; the wall clock and the turn bound keep their recorded defaults, so the only bound it can reach
  is this one. Red on the base tree, where the case cannot shorten the key it does not have. — *verified:
  `PASS run#childPastItsCheckBoundIsCut`, and `FAIL` on `5c045e55` with the same case file.*
- [x] 3.2 `tools/loop/tests/loop-mutations.sh`: records `882.1` (the wait's arm) and `882.2` (the clause that reads
  the program a call runs). — *verified: `RED 882.1`, `RED 882.2`, both restoring green,
  `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`.*
- [x] 3.3 `tools/loop/tests/loop-mutations.sh`: re-point `272.1`, `272.2`, `272.3` and `855.1` at `tools/loop/lib.sh`,
  where the definitions they quote now live — a record left at the old path mutates nothing and reports itself stale.
  — *verified: `loop-mutations: all 359 cases passed`, and each of the four still reddens its own case.*

## 4. The checks this delta owes

- [x] 4.1 `tools/check-doc-values.sh`, `tools/check-doc-routes.sh`, `tools/check-openspec-hygiene.sh`,
  `tools/check-portability.sh`, `tools/tests/check-rule-routes.sh`, `tools/loop/tests/loop-selftest.sh --from-git
  5c045e55` and `--group run`, `tools/loop/tests/loop-mutations-selftest.sh` — green, with the one pre-existing failure
  (`phase#aResultOverTheBoundIsCutAndNamed`, wide selection only) named as the tree's, not this delta's.