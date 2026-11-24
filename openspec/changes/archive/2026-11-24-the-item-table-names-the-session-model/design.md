# Design: the-item-table-names-the-session-model

## Context

`state.sh` freezes `metrics.roleModels` per item while the run is live: for each role, the model the child ran in and
the tokens that model spent (`{"worker": {"deepseek-v4.1-flash": 6294288}}`). `report.sh` reads the same object for its
run-wide `roles:` line and ignores it for the table, whose `tokens` column is the item's whole spend.

The models differ because the role's model is chosen per child (the decision model for a repair, the configured model
for a fresh phase), so two items of one run can share neither their model nor their price. A column is the smallest
rendering that makes the table read honestly, and it is the column a reader comparing two item costs needs first.

## Decision 1 — the row shows the item's worker model and its fan-out

`worker` is the role that spends the item's own work, so its model leads the cell; a second model on the same role is
shown as `+1` rather than listed, because the table's row is one line and the full split stays in `roleModels`, one
`jq` away. An item with no recorded model prints `-`, which is the report's existing spelling for "not measured".

## Decision 2 — no state field, no new key

Everything the column needs is already frozen. The change is one expression in the table's builder and its header,
which is why it is filed as a report-only change: one function, one case, no run contract touched.

The owning guideline section is `guidelines/Backlog.recipes.md`'s account of what a report row carries, where every
column appended to the row is described with the issue that added it (#757, #814) — this diff adds the `model`
column's sentence there, carrying #972.

## Decision 3 — what was rejected

- **A `model` block per item.** The table is already nineteen columns; a per-role model block belongs in the JSON
  (`--json` already emits `items`), not in a printed row.
- **A model column per role.** Five roles across nineteen columns is a second table, and the row's question is what
  the item cost and on which model's budget.
- **Printing the model only in the trend block.** It is there today, run-wide, and it is what makes #931 and #963
  look comparable when they are not.

## The falsification

Both cases are red on the current tree: the two-model item's row has no cell for the pair, and the older state's row
prints nothing where `-` is asserted. The second case is the one that keeps the change honest about a state written
before the field existed — a dash is not a zero.

## Open decisions

none
