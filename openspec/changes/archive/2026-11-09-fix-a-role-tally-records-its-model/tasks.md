# Tasks

The change's own line numbers are the tree's as this change leaves it; the base's are `de1cb614`. The red run's own
tally is `evidence/red-on-HEAD.log`, the mutation run's `evidence/mutation-run.log`.

## 1. The red case

- [x] 1.1 Added `report#aRolesTokensRecordTheModelThatAnswered` to `tools/loop/tests/loop-cases.sh` (after
      `case_report_aStreamWithoutDurationsPrintsDash`, registered in the case list and dispatched), with its
      first-line `# files:` declaration. Verify: against the base's reader (which records no model),
      `bash tools/loop/tests/loop-selftest.sh --case 'report#aRolesTokensRecordTheModelThatAnswered'` →
      `FAIL … the reviewer's tokens are recorded under the model that answered them: expected '1000000', got 'null'`
      (2026-11-09T17:46Z; `evidence/red-on-HEAD.log`, exit 1).
- [x] 1.2 Added the sibling shapes as their own cases:
      `report#aSessionThatSwitchedModelRecordsBothTallies` (red on the base in the same run — `… the first model keeps
      the tokens of the turns it answered: expected '1000000', got 'null'`),
      `report#aStateWithoutAModelKeepsItsRoleTotals` (green on the base, because it asserts the shape the base already
      has) and `report#aRoleWhoseTurnNamedNoModelKeepsItsRow` (red on the base: the base prints `worker 1 Mtok ·
      reviewer 3 Mtok` where the change prints `worker (mimo-v2.6-flash) 1 Mtok · reviewer 3 Mtok`).
- [x] 1.3 The declarations' own check is accepted before those runs: `bash tools/loop/tests/loop-selftest.sh --case
      'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS` (`evidence/red-on-HEAD.log`).

## 2. The engine — `tools/loop/state.sh`

- [x] 2.1 The `turn_end` handler keeps the answering model beside the usage, duration and calls it already reads from
      `$m`: `model: (($m.responseModel? // $m.model? // null) | if type == "string" and length > 0 then . else null
      end)` (`:911`). Verify: the red case's `roleModels` assertions read a number.
- [x] 2.2 A `roleModels` pass beside the `roles` pass (`:939`–`:951`) folds each session's modelled turns into a map of
      model to tokens, summed across sessions of one role. Verify: the switch case reads `1 000 000` for the first
      model and `2 000 000` for the second, not a merged row.
- [x] 2.3 The field is added to the object `child_usage_json` returns (`:1027`), to its fallback string (`:1035`), and
      to the `metrics` object `cmd_done` builds (`:726`), `null` where no turn named a model. Verify: the no-model case
      reads `.metrics.roleModels` as `null` and `.metrics.roles.worker` as `1000000`.

## 3. The report — `tools/loop/report.sh`

- [x] 3.1 The `roles:` line prints `role (model) tokens` rows when any item recorded a model, in the role order the
      line already uses, with the role totals as the total it is over (`:466`–`:484`). Verify: the first case reads
      `worker (mimo-v2.6-flash) 1 Mtok · reviewer (minimax-m3.1-flash) 1 Mtok · 2 Mtok over 1 item`.
- [x] 3.2 The tokens of a role no model explained keep a row of the role alone, so the rows add up to the total the
      line is over, and a tally of zero prints no row. Verify: the mixed case reads `worker (mimo-v2.6-flash) 1 Mtok ·
      reviewer 3 Mtok · 4 Mtok over 1 item`.
- [x] 3.3 The three arms that print the role totals are unchanged where no model is recorded. Verify:
      `report#theSpendIsSplitByPhaseRoleAndGate` still reads `worker 6 Mtok · reviewer 1 Mtok · tail 1 Mtok · 8 Mtok
      over 1 item`, and `report#aStateWithoutAModelKeepsItsRoleTotals` reads `worker 1 Mtok over 1 item` for both a
      no-model stream and a state whose field was removed.

## 4. The guideline — `guidelines/Backlog.recipes.md` §7b

- [x] 4.1 The paragraph at `:533` that names `metrics.roles` gained the field beside it: the model read from the same
      `turn_end` message, `responseModel` preferred over `model`, a switch as two tallies, the report's
      `role (model) tokens`, and the two shapes that keep a bare role row. Verify:
      `tools/look.sh 'roleModels' --path guidelines/Backlog.recipes.md --files` finds the paragraph.

## 5. Verification

- [x] 5.1 Each new invariant owes one mutation, and the mutation set carries three for this change under its own
      label (`role-model.1`, `.2`, `.3`, `tools/loop/tests/loop-mutations.sh`), because the item has no `§N` to number
      them by. Verify: `bash tools/loop/tests/loop-mutations.sh --only "role-model.1 role-model.2 role-model.3"` →
      `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`, each red on the assertion that owns it
      (`evidence/mutation-run.log`), and `--check` reports all 3 records still apply (`evidence/mutation-check.log`).
- [x] 5.2 The cases are green on the change, and the siblings the rule is read beside still pass:
      `report#theSpendIsSplitByPhaseRoleAndGate`, `report#aStreamWithoutDurationsPrintsDash`.
- [x] 5.3 The structural checks of the files this change edits — it compiles nothing: `bash -n tools/loop/state.sh`,
      `bash -n tools/loop/report.sh`, `bash -n tools/loop/tests/loop-cases.sh`,
      `bash -n tools/loop/tests/loop-mutations.sh`, `tools/check-openspec-hygiene.sh`,
      `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`, `openspec validate
      fix-a-role-tally-records-its-model --strict`. No build is run for a delta of shell, a guideline paragraph and a
      spec delta.
