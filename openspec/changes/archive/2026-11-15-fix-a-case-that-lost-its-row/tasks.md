# Tasks — a case that loses its row is refused

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `driver#aCaseWithNoSelectionRowIsRefused`, registered in
      `LOOP_SHELL_CASES` (`:430`) and in the dispatcher arm (`:1179`), declaring
      `tools/issues/ tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh tools/loop/` in its first line.
      It builds a nested suite whose cases file declares one row and defines the bodies `case_stub_kept` and
      `case_stub_lost` with an arm for each — the shape `1eddc98c` wrote — runs the driver over it with `--case`, and
      asserts exit 1, that the refusal names `stub#lost` as a body with no row in `LOOP_SHELL_CASES`, and that no case
      ran.
      **Verified** red on the base `d794277b`, with this change's cases file and the base's driver:
      `evidence/red-on-base.log` and its JUnit carrier `evidence/red-on-base.xml` (`tests=1 failures=1`,
      2026-11-15T22:37:47Z), the assertion answering `PASS stub#kept: expected '1', got '0'` —
      `evidence/scratch/red-on-base.sh` is the probe that produced it, and `evidence/registration-probe.log` runs the
      same suite through the base's driver (`exit 0`, `PASS stub#kept`) and this tree's (`exit 1`, the refusal).
- [x] 1.2 The cheap declaration check the delta owes first — `tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`.
      **Verified** `PASS driver#everyCaseDeclaresTheFilesItCanReach`, 2026-11-15T22:37:26Z
      (`evidence/declaration-check.log`).

## 2. The fix

- [x] 2.1 `tools/loop/tests/loop-selftest.sh:457` — the block after `shell_cases=$(loop_shell_cases)` that derives the
      bodies and the arms of the cases file it sourced and compares them with the declared set, refusing with exit 1
      before `--list` and before any case runs: a body with no row, a row with no body or no arm, an arm with no row,
      and an arm whose function is not the body its name derives. Its comment states the mechanism, the measurement
      that filed the item, the three shapes that are a nested fixture's own, and the case that holds it.
      **Verified** `PASS driver#aCaseWithNoSelectionRowIsRefused` (2026-11-15T22:37:43Z, `evidence/green-case.log`)
      and the driver's own refusal over the defective suite (`evidence/registration-probe.log`, exit 1).
- [x] 2.2 The suite this change lives in is unchanged otherwise: `bash tools/loop/tests/loop-selftest.sh --group driver`
      → `all 32 cases passed` (49.32 s, 510.99 s of case time), so no nested fixture the group drives is read as this
      suite's own bodies (`evidence/driver-group.log`, 2026-11-15T22:37:55Z).
- [x] 2.3 The declared case count after the addition: `LOOP-MEASURE-SUITE cases=462 cmd=tools/loop/tests/loop-selftest.sh
      --list`, from the change's own measure block, and `--list` exits 0 over the whole tree with the check in place
      (`evidence/list-check.log`).

## 3. The delta and the structural check

- [x] 3.1 `openspec/changes/fix-a-case-that-lost-its-row/specs/agent-issue-loop/spec.md` — one ADDED requirement and
      its scenario, naming the case; no existing requirement moves, because
      `tools/look.sh 'LOOP_SHELL_CASES|selection row|dispatch arm' --path openspec/specs/agent-issue-loop/spec.md` → 0
      matches.
- [x] 3.2 The structural check of the files this delta edits, in place of a build task: `bash
      tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent: 27 open change(s) carry task markers, the
      operation guidance is quoted, and every capability spec states its Purpose.` (2026-11-15T22:52Z)

## 4. Left to the gate child

- [ ] 4.1 The archive: the change directory is unarchived and its pointers are the gate child's to complete, with the
      gate run over the completed tree (issue #837). Nothing here writes the archive.
