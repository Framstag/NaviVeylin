# Tasks: the-item-table-names-the-session-model

## 1. The column

- [x] 1.1 `tools/loop/state.sh` (the `report` arm `tools/loop/report.sh:440` invokes): add `model` to the per-item
      table's header and its row builder, read from
      `metrics.roleModels.worker` — the model with the most tokens, `+N` for further models, `-` when none is recorded.
- [x] 1.2 Confirm the `--json` path and the trend block are unchanged — the diff's two hunks are the header string and
      the row expression, and `report#aRolesTokensRecordTheModelThatAnswered`, which reads the trend's `roles:` line,
      passes.

## 2. The cases

- [x] 2.1 `report#theItemTableNamesTheSessionModel` — an item whose worker ran on two models prints both facts.
- [x] 2.2 `report#aStateWithNoModelPrintsADash` — a state written before the field existed prints `-`, not a guess.
- [x] 2.3 Declare both cases' files in the driver's own registry (`driver#everyCaseDeclaresTheFilesItCanReach`),
      which passes.

## 3. The falsification

- [x] 3.1 Mutate the column out and confirm both cases go red; restore, then run
      `tools/loop/tests/loop-selftest.sh --case report#theItemTableNamesTheSessionModel` and the full
      `tools/loop/tests/loop-selftest.sh`. Red and the restore are in `evidence/red-when-mutated.log`; the cases and
      the two the diff reaches are in `evidence/green-on-change.log`; the full suite is the gate's own selection.
- [x] 3.2 State the owning guideline section — `guidelines/Backlog.recipes.md`, the account of what a report row
      carries, where each appended column is described with the issue that added it (#757, #814): the `model`
      column's sentence carries #972, and it is in this diff.
