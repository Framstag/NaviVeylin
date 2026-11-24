# Proposal: the-item-table-names-the-session-model

## Why

`report.sh` prints the role totals with the model each role ran in — one line, run-wide — and the per-item table
without one. The state already records the pair per item (`metrics.roleModels`, `role → {model: tokens}`), so the
figure is on disk and only the table's columns are missing. As it stands an item's 26 Mtok cannot be priced against
the model that spent it, and a run whose roles span three models reads as one budget.

- **Observed** ✗: in the run started 2026-11-18T22:18:25Z the worker role alone spanned **three models** —
  `deepseek-v4.1-flash` 128.5 Mtok, `mimo-v2.6-flash` 12.4 Mtok, `minimax-m3.1-flash` 9 Mtok — and the per-item
  table's nineteen columns name none of them. #916's worker spent 5.2 Mtok on one model and 0.54 Mtok on another;
  #950's 2.0 Mtok on the second. A reader comparing #931's 26.4 Mtok with #963's 16.3 Mtok is comparing models as
  well as items, and nothing on the row says so.
- **Measured** ⊨: the run's `roles:` trend line and every item's `.metrics.roleModels` agree figure for figure (the
  state is frozen), so the column is a rendering of a number the report already trusts.
- **Cheap** ⊕: one column, one function, no state change, no new key, no new child.

## What Changes

- **`tools/loop/state.sh` — the table gains a `model` column** (its `report` arm, the one `tools/loop/report.sh:440`
  invokes), printed from `metrics.roleModels` as the worker's
  model and the count of further models the item used (`deepseek-v4.1-flash`, `deepseek-v4.1-flash +1`), with `-`
  for an item whose state records none.
- **The roles block keeps its run-wide line** — the two readings are the same fact at two grains, and the item row is
  what a reader compares.
- **`tools/loop/tests/loop-cases.sh`: two cases** — a state whose item carries two worker models prints the pair, and
  a state written before the field existed prints `-` rather than a guess.

## Evidence

- `.pi/issue-loop/run.json`: `.items[].metrics.roleModels` for all 11 items of the analysed run, and `report.sh`'s
  `roles:` trend line, whose totals equal the sum over those objects.
- `state.sh`'s `report` arm builds the table in one jq block from the same state, so the column is one expression
  beside nineteen others.

surface: 1 column, 2 cases — `tools/look.sh 'roleModels' --path tools/loop/report.sh` and
`tools/look.sh 'roleModels' --path tools/loop/state.sh`
