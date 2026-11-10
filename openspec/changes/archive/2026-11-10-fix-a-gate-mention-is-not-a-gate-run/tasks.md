# Tasks — a call that names the gate is not a call that ran it

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `report#aCallThatNamesTheGateIsNotAGateRun`, registered in
      `LOOP_SHELL_CASES` and in the dispatcher, declaring the files it reaches in its first line. Two states: a
      session whose only `gate.sh` words are a `sed` read and a `--plan` call (no gate, no gate time, the trend's
      dash), and a session that runs the gate behind `cd … &&` and `timeout 300` (5 000 ms of gate time, the split
      at that turn).
      **Verified** red on the base commit with `state.sh` restored from it: `evidence/red-on-HEAD.log` and its JUnit
      carrier `evidence/red-on-HEAD.xml` (`tests=1 failures=1`; the run's `date -u` was 2026-11-10T00:08:46Z, in the
      item's record), log ending `loop-selftest: 1 of 1 cases failed` — the first assertion answers
      `afterGate = {sessions: 1, tokens: 6 000 000, total: 6 000 000}` where the case requires `null`.
- [x] 1.2 The cheap declaration check the delta owes first — `loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — passes with the new case.
      **Verified** `PASS driver#everyCaseDeclaresTheFilesItCanReach`.

## 2. The fix

- [x] 2.1 `tools/loop/state.sh`: `is_gate` reads the command's program — assignments, a `cd …` prefix and the
      `bash`/`sh`/`exec`/`command`/`timeout <n>`/`env`/`nohup` wrappers skipped — and excludes a `--plan` call.
      **Verified** by the case above passing on the changed tree, and by `report#theSpendIsSplitByPhaseRoleAndGate`
      — the case the figure already had — still passing unchanged.
- [x] 2.2 `report#` group: the 30 cases that read the state's spend, run together.
      **Verified** `loop-selftest: all 30 cases passed`, 2026-11-10.

## 3. The mutations

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: `267.1` (the plan exclusion), `267.2` (the `cd …` prefix) and `267.3`
      (the program word rather than the last word) — one per rule the case pins.
      **Verified** `--only '267.1 267.2 267.3'`: `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`.

## 4. The prose that owns the rule

- [x] 4.1 `openspec/changes/fix-a-gate-mention-is-not-a-gate-run/specs/agent-issue-loop/spec.md`: the clause on what
      counts as the gate, and the scenario `A call that names the gate is not a call that ran it`, naming the case.
- [x] 4.2 `guidelines/Backlog.recipes.md` (section 7b): one clause in the paragraph that owns the three splits,
      naming the measurement, the issue and what the split was really taken at.
      **Verified** `tools/check-doc-routes.sh` and `tools/check-openspec-hygiene.sh` — the structural checks these
      two files owe; the gate runs them again.

## 5. The gate

- The gate is `./tools/loop/gate.sh`, and it is this change's own: the whole `loop-selftest.sh` set — which a delta to
  `tools/loop/tests/loop-cases.sh` is owed — `loop-mutations.sh --check`, and the structural checks of the files this
  change edits. Round 1's gate (`tests=978 failures=0 errors=0`) is in `evidence/gate-run-line.md` and
  `evidence/gate-summary.xml`; round 2 ran it again on the tree this round's corrections produced, so that the gate's
  record names the tree the round's diff carries — round 1's record named another tree, because it ran before the tail
  archived the change and merged the delta into the shipped spec.

## 6. Round 2 — the corrections the review asked for

- [x] 6.1 The review's blocking finding — the gate's record did not attest the tree the round read. The gate was run
      again on the tree this round's corrections produced.
- [x] 6.2 The measurements the record did not carry: the five records' split points, their tokens and the tail's
      session are read by a probe this change versions (`evidence/split-point.jq`, its output
      `evidence/split-point.log`), and the surface count by `tools/look.sh` (`evidence/look-surface.log`).
- [x] 6.3 §5 above states the gate this change ran, not a gate deferred to a child that follows.
- [x] 6.4 The case re-run on this round's tree: `evidence/green-on-change-round2.log` — `PASS
      report#aCallThatNamesTheGateIsNotAGateRun`, run 2026-11-10T00:39:30Z.
- [x] 6.5 The declaration check the delta owes first, re-run on this round's tree: `PASS
      driver#everyCaseDeclaresTheFilesItCanReach` (`bash tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`).
