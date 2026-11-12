# Tasks — fix-gate-evidence-is-the-items-own-tally

## 1. The red case (do first)

- [x] 1.1 `run#theGateColumnNamesThisItemsGateResult` (`tools/loop/tests/loop-cases.sh`), registered in
      `LOOP_SHELL_CASES` and the dispatcher: a fixture tree carrying a tracked archived change whose evidence
      directory is named `module-test-results`, an item whose children claim no gate and leave no tally, and a second
      item whose own gate writes one tally where the profile says a gate writes it. Verify: red on the base
      (`4a14574fc6b099a0c867f4c9759f2d8b74c6b01b`) — `evidence/red-on-head.log`, `evidence/red-on-head.xml`
      (`tests=1 failures=1`, 2026-11-12T11:56:24Z): HEAD reads `1` and then `2` test-results XMLs, records `1 xml`
      for an item that ran no gate, and the report never names it.
- [x] 1.2 The declaration case ran first, as the gate's plan puts it: `tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — `PASS driver#everyCaseDeclaresTheFilesItCanReach`
      (2026-11-12T11:56Z, before the red case was run).

## 2. The fix (`agent-issue-loop` — "The gate's evidence is the tally this item's gate wrote")

- [x] 2.1 `tools/loop/run.sh`: the gate's evidence is `<worktree>/<LOOP_GATE_TALLY_DIR>`'s `*.xml` (one level deep,
      the profile's path taken as it stands when it is absolute, nothing when the profile names none), the progress
      line names that directory, and the comment above `clear_stale_test_results` no longer quotes the old refusal.
      Verify: task 1.1 green — `evidence/green-case.log`.
- [x] 2.2 `tools/loop/run.sh`: the no-evidence arm's reason names the directory it read (`the report claims a gate
      result but the gate's tally directory holds no XML`) and its condition reads the child's **claim**, so an item
      that claimed no gate is not blocked by it. Verify: the two cases that quote that reason —
      `run#claimedGateWithoutEvidenceIsBlocked`, `run#staleTestResultsAreNotThisChildsGate` — green in the `run`
      group's log (`evidence/run-cases.log`).
- [x] 2.3 `tools/loop/run.sh`: the item's field is the child's claim when it made one, the count the directory holds
      when it holds any, and **empty** when the item claimed no gate and left no tally — never `0 xml`. Verify: task
      1.1's second half, and `run#theGateColumnNamesThisItemsGateResult` asserts `metrics.gate == ""` for the ungated
      item and that `tools/loop/report.sh` lists it on its `re-run the gate for #21` line — `evidence/green-case.log`.
- [x] 2.4 `tools/loop/tests/loop-cases.sh`: the shared `stub_write` action and seven hand-written actions that close
      an item on a gate result write their tally into `app/build/test-results/loop-gate/`, where the gate writes it.
      Verify: the whole `run` group green — `evidence/run-cases.log`, `loop-selftest: all 147 cases passed — 309.32 s,
      selection: --group run`.
- [x] 2.5 `tools/loop/tests/loop-mutations.sh`: `849.1` (the reading put back to the tree-wide `find`) and `849.2`
      (the empty field put back to `$gate_found xml`); `9.5`'s anchor follows the arm's new condition. Verify:
      `--only '849.1 849.2'` reports both red on the new case and green again after each restore —
      `evidence/mutations-849.log`: `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`.

## 3. The rule beside its measurement

- [x] 3.1 `guidelines/Backlog.recipes.md:755` "The gate and its suite", beside "The directory a run writes its
      tallies into is that run's": one paragraph stating that the run reads an item's gate evidence from that one
      directory, why the tree-wide `find` could not fire the refusal, what an item that ran no gate records, and the
      measurement (2026-11-11, issue #849). Verify: `evidence/doc-checks.log` — `check-doc-routes` (exit 0) and
      `check-doc-routes-selftest`; and `tools/check-openspec-hygiene.sh` over the change's own artifacts —
      `evidence/openspec-hygiene.log`, exit 0.
- [x] 3.2 The change is handed ready to an unattended worker: `bash tools/check-change-readiness.sh
      openspec/changes/fix-gate-evidence-is-the-items-own-tally` — `evidence/readiness.log`.

## 4. The measurement the change owes

- [x] 4.1 `./tools/loop/measure.sh --change fix-gate-evidence-is-the-items-own-tally` over the final change, quoted
      in `evidence/measure-block.log`: the declared case count (`LOOP-MEASURE-SUITE cases=`), the change's own spec
      scenarios counted against the case that names them, the change's own tally (`LOOP-MEASURE-TALLY`), the checks
      this pathspec owes, and the gate's timing line.

--- Rules in scope for this file ---
[From: .pi/worktrees/loop/AGENTS.md]
[Rule body omitted; authoritative source: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/AGENTS.md]
