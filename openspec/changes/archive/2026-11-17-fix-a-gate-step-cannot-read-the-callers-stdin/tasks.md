# Tasks

## 1. The step is given a stdin of its own (spec `agent-issue-loop`, "A gate step is given a stdin of its own")

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `gate#aStepCannotReadTheCallersStdin` — a fifo whose writer side
      the case holds read-write (`exec 8<> "$fifo"`), a `gate.sh` started with that fifo as its stdin, and a suite whose
      first act is `read -r line`; a bounded wait, so a step the read stops is reported as the case's defect and not as
      its own wall clock; `LOOP_GATE_STEP_MAX_SECONDS=10` so the red costs ten seconds. Declared in `LOOP_SHELL_CASES` and
      in the dispatch row, with `# files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh
      tools/tooling.json`.
      — red before the fix: `FAIL gate#aStepCannotReadTheCallersStdin` — `the gate ran the step that reads its stdin to
      completion: … the step overran its bound of 10s and was ended … expected '0', got '1'`
      (`evidence/red-on-head.log`).
- [x] 1.2 `tools/loop/gate.sh:582`: `2>&1 < /dev/null` on the step start line, and the rule above it — the `set -m`
      group, the `SIGTTIN` measurement of 2026-11-16, the 75 minutes of a 600-minute budget, and the sibling start line
      in `tools/loop/run.sh:2125` that already carries the redirect.
      — green after it: `PASS gate#aStepCannotReadTheCallersStdin (0.29 s)` (`evidence/green-case.log`).
- [x] 1.3 `tools/loop/tests/loop-mutations.sh`: `947.1` removes `< /dev/null` from the start line; `63.1` moves onto the
      changed line (`… 2>&1 < /dev/null &$`) so it still quotes a line that exists, and keeps its own invariant.
      — `--check --files 'tools/loop/gate.sh tools/loop/tests/loop-mutations.sh'` → `all 21 cases passed`
      (`evidence/mutations-check.log`); `--only '63.1 947.1'` → `RED 63.1`, `RED 947.1`, both restored green,
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-947-1.log`).
- [x] 1.4 `openspec/changes/fix-a-gate-step-cannot-read-the-callers-stdin/specs/agent-issue-loop/spec.md`: the ADDED
      requirement and its one scenario, naming the case.
      — `openspec validate fix-a-gate-step-cannot-read-the-callers-stdin --strict`.

## 2. Verification

- [x] 2.1 The new case focused, before and after the fix (1.1, 1.2).
- [x] 2.2 The two cases the start line reaches, which the redirect and the record both touch:
      `gate#aStepEndsWithItsCommandNotWithItsWatchers` (green through both records' restores) and
      `gate#aStepCannotReadTheCallersStdin`.
- [x] 2.3 The declaration and fifo checks the new case reaches: `driver#everyCaseDeclaresTheFilesItCanReach`,
      `driver#noFixtureFifoIsOpenedWriteOnly` — `PASS` each (`evidence/driver-cases.log`).
- [x] 2.4 The structural checks, since no compiled file is touched (`guidelines/Build.md` §2, the scope block):
      `bash -n` over the three edited shell files, `openspec validate … --strict`, `tools/check-doc-routes.sh` — all three
      exit `0` (`evidence/structural-checks.log`). No Gradle build and no Android suite is owed, and none was run.
- [x] 2.5 The full self-test suite of `tools/loop/**` once on the tree that lands:
      `bash tools/loop/tests/loop-selftest.sh` → `all 476 cases passed (JDK 21, offline, no token, no sandbox) —
      1241.44 s`, exit `0` (`evidence/full-suite.log`).
