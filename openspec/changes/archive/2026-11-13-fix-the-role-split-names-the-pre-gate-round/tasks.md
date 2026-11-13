# Tasks

The change's own line numbers are the tree's as this change leaves it; the base is `5d730968` — its
`tools/loop/state.sh`, `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`,
`guidelines/Backlog.recipes.md` are the tree this change edits. The red run's log is kept in the run's evidence
directory, `.pi/issue-loop/items/864/red-on-HEAD.log`, beside the change root rather than inside it.

## 1. The red case

- [x] 1.1 Added `report#thePreGateRoundIsChargedToItsOwnRole` to `tools/loop/tests/loop-cases.sh`, with its first-line
      `# files:` declaration and its id in the case list and in the dispatcher. Verify: against the base's reader,
      `bash tools/loop/tests/loop-selftest.sh --case 'report#thePreGateRoundIsChargedToItsOwnRole'` →
      `FAIL … the round before the gate is a bucket of its own, not part of the reviewer's: expected '4000000', got
      'null'` (2026-11-13T15:13:38Z; `red-on-HEAD.log`, `tests=1 failures=1`).
- [x] 1.2 The declaration's own check is read before anything else: the case is listed by
      `bash tools/loop/tests/loop-selftest.sh --list` and selected by `--files tools/loop/state.sh` (184 cases), so a
      delta to `state.sh` reaches it without the whole suite.

## 2. The engine — `tools/loop/state.sh`

- [x] 2.1 The tokens pass gains the arm in `child_role`'s order — `elif test("pre-gate")` → `reviewer-pre-gate`, read
      after the tail arm and before the reviewer arm (`:1152`) — and its object literal declares
      `"reviewer-pre-gate": null` beside the three (`:1156`). Verify: the case's first assertion reads `4000000`
      (`green-on-fix.log`, `PASS`, 2026-11-13T15:14:15Z).
- [x] 2.2 The model pass gains the same arm (`:1166`) and the same key (`:1173`), so a pre-gate session's model tally is
      keyed as its role total is. Verify: no case of this change exercises a modelled pre-gate session, and the
      sibling `report#aRolesTokensRecordTheModelThatAnswered` still passes — the arm changes which key a
      modelled session lands under, not whether a model is read.
- [x] 2.3 The comment above the field states the bucket and the measurement it was added from (`:916-925`). Verify:
      `tools/look.sh 'reviewer-pre-gate' --path tools/loop/state.sh` finds the paragraph.

## 3. The mutation — `tools/loop/tests/loop-mutations.sh`

- [x] 3.1 Record `864.1` mutates the new arm to `elif false` and names the new case. Verify:
      `bash tools/loop/tests/loop-mutations.sh --only 864.1` → `RED 864.1 … (expected '4000000', got 'null')` and
      `restore 864.1 … green again` (2026-11-13T15:15:10Z; `mutation-864.1.log`, `red-when-mutated: 1 · not-noticed: 0
      · restore-failures: 0`).
- [x] 3.2 No record of the set went stale: `bash tools/loop/tests/loop-mutations.sh --check --files
      tools/loop/state.sh` → `all 35 cases passed — every selected record applies to the file it names`.

## 4. The documents

- [x] 4.1 `openspec/changes/fix-the-role-split-names-the-pre-gate-round/specs/agent-issue-loop/spec.md` widens the
      requirement's role list to name the round before the gate and adds the scenario its case answers. Verify:
      `tools/check-change-readiness.sh openspec/changes/fix-the-role-split-names-the-pre-gate-round` → `ok`.
- [x] 4.2 `guidelines/Backlog.recipes.md:277` ("What an item costs, and the bounds on it") names the new bucket and
      the figure it was filed from, the paragraph that described the split as "beside the three" now says "beside the
      roles' own", and `guidelines/Backlog.recipes.md:1287` ("Leftovers, the report and the queue")'s sentence that states
      what the report splits by role names the round before the gate beside the trio. Verify:
      `tools/look.sh 'reviewer-pre-gate' --path guidelines/Backlog.recipes.md` and
      `tools/look.sh 'tokens split by role' --path guidelines/Backlog.recipes.md` find them.
- [x] 4.3 `tools/loop/report.sh`'s header comment — the text `--help` prints — names the fourth bucket beside the trio
      it already named, so the file's own statement of what the line says is true of the line. Verify:
      `tools/look.sh 'tokens split by' --path tools/loop/report.sh` finds it; no case asserts that header, and the edit
      is a comment line, so the report's behaviour is untouched (`report#theSpendIsSplitByPhaseRoleAndGate` in
      `--group report`, 2026-11-13T15:17:30Z).

## 5. Verification

- [x] 5.1 The named case is green on the change (2026-11-13T15:14:15Z, `green-on-fix.log`).
- [x] 5.2 The cases the delta reaches are green: `bash tools/loop/tests/loop-selftest.sh --group report` →
      `loop-selftest: report 38 cases 298.22 s`, `all 38 cases passed … 33.23 s` (2026-11-13T15:17:30Z,
      `green-group-report.log`), including
      `report#theSpendIsSplitByPhaseRoleAndGate` (whose roles line is byte-identical) and
      `report#aTurnNoSessionRecordReachesIsItsOwnBucket`.
- [x] 5.3 The structural checks of the files this change edits — it compiles nothing: `bash -n tools/loop/state.sh`,
      `bash -n tools/loop/tests/loop-cases.sh`, `bash -n tools/loop/tests/loop-mutations.sh`,
      `tools/check-change-readiness.sh <change>`, `tools/check-openspec-hygiene.sh` and `tools/check-doc-values.sh`.
      No build is run for a delta of shell, a spec delta and a guideline paragraph.

The gate is not this session's: the run has deferred it to the child that follows the diff review, and this change's one
gate is that child's whole self-test — 426 declared cases with this change's one.
