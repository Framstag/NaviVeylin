# Tasks — the report splits the spend by phase and role

## 1. The red cases

- [x] 1.1 `tools/loop-cases.sh` — `report#theSpendIsSplitByPhaseRoleAndGate`: one item whose stream records a worker
      that reads, runs `gate.sh` and answers, a reviewer and a tail, with known durations, tokens and one gate
      turn; the state must record `metrics.phases` (7 500 / 5 000 / 300 ms), `metrics.roles` (6 / 1 / 1 Mtok) and
      `metrics.afterGate` (5 of 6 Mtok over one session), and the trend must print the three lines from them.
      Verification: on the base `5b30ed79e61e679773f4a188622fd835aebfc24b`, `bash tools/loop-selftest.sh --case
      'report#theSpendIsSplitByPhaseRoleAndGate'` reads `FAIL` with `expected '7500', got 'null'` — runner tally
      `tests=1 failures=1`, `2026-11-06T14:40:37Z` (evidence: `evidence/red-on-HEAD.log` and its tally
      `evidence/red-on-HEAD.xml`, copied from `.pi/issue-loop/items/759/red-759-a.log`).
- [x] 1.2 `tools/loop-cases.sh` — `report#aStreamWithoutDurationsPrintsDash`: a stream that records no duration and
      no usage, and a state written before the split existed, must print `-` with the reason on all three lines and
      no zero. Verification: `bash tools/loop-selftest.sh --case 'report#aStreamWithoutDurationsPrintsDash'` reads
      `FAIL` on the base with the trend line absent — `tests=1 failures=1`, `2026-11-06T14:40:37Z` (same evidence,
      and `.pi/issue-loop/items/759/red-759-b.log`).

## 2. The recording

- [x] 2.1 `tools/loop/state.sh` — `child_usage_json` reads the stream's sessions beside its turns and returns
      `phases`, `roles` and `afterGate`, each null where nothing was measured; which command is the gate is the
      program `LOOP_GATE_CMD` declares, never a name the engine carries (`run#engineNamesNoProjectFact`); the `done`
      writer stores all three in `metrics`. Verification: task 1.1's six `jq` assertions on the written state pass,
      and the existing figures are unchanged — `bash tools/loop-selftest.sh --group report` green.

## 3. The report

- [x] 3.1 `tools/loop/report.sh` — the trend block prints `phases:`, `roles:` and `after the first gate:` from the
      state, with `-` and its reason where the state holds no such figure. Verification: task 1.1's three trend
      assertions and task 1.2's dash assertions pass; `report#trendFiguresFromState`,
      `report#anItemBelowTheCallsPerTurnFloorIsNamed` and `report#noRoundOrWallPrintsDashNotZero` stay green
      (`--group report`).

## 4. The record the change owes

- [x] 4.1 `openspec/specs/agent-issue-loop/spec.md` — one requirement, three scenarios, each naming its case.
- [x] 4.2 `guidelines/Backlog.recipes.md` section 7b — the measurement behind the three lines (the archive's 227 items,
      the three splits) beside the two bands it belongs with; section 7e — the report's description of the trend gains
      the three figures. Verification: `bash tools/check-doc-routes.sh`.
- [x] 4.3 `tools/loop-mutations.sh` — group `256.x`, three records: the recording dropped (`phases: null`), the
      trend's gate figure zeroed, the after-gate share pinned to a constant. Verification: `bash
      tools/loop-mutations.sh --check --only "256.1 256.2 256.3"` reports every record applying, and `--only "256.1
      256.2 256.3"` reads the named case red on each record's own assertion and green again (`red-when-mutated: 3 ·
      not-noticed: 0 · restore-failures: 0`; evidence `evidence/mutations-256.log`).

## 5. The change's own numbers

- [x] 5.1 `bash tools/loop/measure.sh --change fix-report-splits-the-spend-by-phase` once the change exists, quoted
      into the proposal and kept as `evidence/measure.txt` — and taken again on the base the retry was handed
      (`83d8cea3`), where it reads `LOOP-MEASURE-SUITE cases=347`, `LOOP-MEASURE-CHANGE scenarios=3 cases=3
      unnamed=0` and `LOOP-MEASURE-SURFACE surface: 2 cases added, 0 rewritten, 0 fixtures realigned`, kept as
      `evidence/measure-retry.txt`.
