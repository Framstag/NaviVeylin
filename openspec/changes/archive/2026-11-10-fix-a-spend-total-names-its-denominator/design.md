# Design

## The root cause

`tools/loop/report.sh:437`–`:447` bind the trend's two spend inputs to one field each — `$phs` from
`metrics.phases`, `$rol` from `metrics.roles` — and each branch below prints that sum as a total `over N items` (the
phases at `:453`–`:473`, the roles at `:474`–`:518`). Both totals are parts of a larger figure the item records:

- `metrics.phases` is the sum of the children's `turn_end` durations and their tools' `tool_execution_end`
  durations (`tools/loop/state.sh:970`–`:976`). An item's `metrics.wallSeconds` (`tools/loop/state.sh:766`) is the
  clock it was worked for, and the difference is the work the loop does between children.
- `metrics.roles` folds only the turns inside a `session` record (`tools/loop/state.sh:977`–`:983`, over the
  `sessions` pass at `:942`–`:966`), while `metrics.tokens` (`:1055`–`:1064`) sums every turn of the stream. A child
  whose stream carries no `session` record is in the second and in no role.

Measured 2026-11-10 (`evidence/measure-surface.txt`): phases 8 187 572 ms against 16 253 s of wall over the same 10
items, and 5 440 384 tokens of item #833 against 3 535 653 by role, the difference being the tokens of the 19 turns
its stream records before its first `session`.

## The single fix

Print each total beside what it is a share of, from fields the state already holds:

- `$phaseItems` / `$roleItems` keep the items behind each sum, so the denominators are read over the same items:
  `$phaseWallS` is `metrics.wallSeconds` summed there, `$tokensTotal` is the same four-part sum `metrics.tokens`
  records, over the items that recorded a role split.
- The phases line keeps its buckets and total and gains ` · <pct> % of the <wall> the same items recorded`.
- The roles line keeps its rows and total and gains, when the two disagree, ` · of the <tokens> the same items
  recorded — <difference> (<pct> %) in turns no session record covers`.
- Either clause is omitted where its denominator is absent or equal, so a state with no wall time, or one whose role
  total is the whole (`report#theSpendIsSplitByPhaseRoleAndGate`), prints the line it always printed.

## Blast radius

- `tools/loop/report.sh`: the two aggregations, the phases clause, the role clause. Nothing else in the file reads
  `$phs` or `$rol`.
- `tools/loop/tests/loop-cases.sh`: one case added (list, dispatch, body).
- `tools/loop/tests/loop-mutations.sh`: two records, one per site.
- `guidelines/Backlog.recipes.md:254`: one sentence.
- `openspec/specs/agent-issue-loop/spec.md`: one scenario under the existing requirement, through this change's
  delta.
- Untouched: `tools/loop/state.sh`, the state's shape, `--json` output, the `after the first gate:` line, the table.

## Rollback

Revert the two clauses in `tools/loop/report.sh` and the case: the trend prints what it printed before, and no other
reader depends on the new text — the fields it prints were already in the state.

## Alternatives the evidence eliminated

- **Record an `unattributed` role in `state.sh` so the rows always add up.** Eliminated: the role is read from the
  prompt path a `session` record carries (`state.sh:977`–`:980`), and a child whose stream has no such record names
  no role, so the bucket would be a guess about who spent it; and a field written at `done` time cannot exist in the
  states already written, which would leave every archived run printing two totals that disagree.
- **Print the run's own window (`startedEpoch`…`stop`) as the clock's denominator.** Eliminated: the window also
  holds the time between items and the work the loop does outside any item, so it would compare the children's
  durations to a clock no child contributed to; `metrics.wallSeconds` is what the same items were worked for.
- **Name the difference in prose and print no second total.** Eliminated by the review rate's own rule in this
  report (`report.sh:497`): a percentage is printed with the two numbers it is a percentage of, because a reader
  cannot check a figure the block never prints.
