# Tasks

The change's own line numbers are the tree's as this change leaves it; the base's are `c0ddb97c`. The red run's own
tally is `evidence/red-on-HEAD.log`, the mutation run's `evidence/mutation-run.log`, the surface count
`evidence/measure-surface.txt`.

## 1. The red case

- [x] 1.1 Added `report#theSpendTotalsNameWhatTheyAreAShareOf` to `tools/loop/tests/loop-cases.sh` (after
      `case_report_theSpendIsSplitByPhaseRoleAndGate`, registered in the case list and dispatched), with its
      first-line `# files:` declaration. Verify: against the base's report,
      `bash tools/loop/tests/loop-selftest.sh --case 'report#theSpendTotalsNameWhatTheyAreAShareOf'` →
      `FAIL … the phases' total is printed beside the wall the same items recorded … not found in:   phases: model
      9.5 s · gate 5 s · other 0.3 s · 14.8 s over 1 item` (2026-11-10T02:18:29Z; `evidence/red-on-HEAD.log`, exit 1).
- [x] 1.2 The declaration's own check is accepted before that run:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/red-on-HEAD.log`; it named the missing `tools/loop/gate.sh` in the declaration first, which is why
      the case's first line names it).

## 2. The report — `tools/loop/report.sh`

- [x] 2.1 The two sums keep the items behind them (`$phaseItems`, `$roleItems`), so each denominator is read over the
      same items as its total, and the two denominators are bound there: `$phaseWallS` from `metrics.wallSeconds`,
      `$tokensTotal` from the four parts `metrics.tokens` records. Verify: the red case reads
      `.items[0].metrics.tokens.cacheRead` = `10000000` against `[.items[0].metrics.roles[]] | add` = `8000000`, the
      two halves the line prints.
- [x] 2.2 The `phases:` line prints `· <pct> % of the <wall> the same items recorded`, its buckets and total
      unchanged. Verify: the red case reads `model 9.5 s · gate 5 s · other 0.3 s · 14.8 s over 1 item · 35 % of the
      42 s the same items recorded`.
- [x] 2.3 The `roles:` line prints `· of the <tokens> the same items recorded — <difference> (<pct> %) in turns no
      session record covers`, its rows and total unchanged, and only where the two disagree. Verify: the red case
      reads `… 8 Mtok over 1 item · of the 10 Mtok the same items recorded — 2 Mtok (20 %) in turns no session record
      covers`, and `report#theSpendIsSplitByPhaseRoleAndGate` still reads
      `worker 6 Mtok · reviewer 1 Mtok · tail 1 Mtok · 8 Mtok over 1 item` with no clause, its role total being the
      whole.
- [x] 2.4 The dash lines are untouched. Verify: `report#aStreamWithoutDurationsPrintsDash` → `PASS`
      (`evidence/green-on-change.log`).
- [x] 2.5 Each clause is left out where its denominator is absent or equal, so a state whose two figures agree prints
      the line it always printed. Verify: the red case's second state — one item whose every turn is inside one of its
      sessions and which recorded no wall — reads `phases: … over 1 item` with no `% of the` and `roles:  … 8 Mtok
      over 1 item` with no `in turns no session record covers`.

## 3. The guideline — `guidelines/Backlog.recipes.md:254`

- [x] 3.1 The paragraph at `:538` that names the four `metrics.*` figures gained the rule the two lines follow: each
      total printed beside the figure it is over, the phases' against the wall the same items recorded and the role
      total against the tokens those items recorded, the difference being the turns no `session` record covers. Verify:
      `tools/look.sh 'the difference the sessionless turns make' --path guidelines/Backlog.recipes.md --files` finds
      the paragraph.

## 4. Verification

- [x] 4.1 One mutation per site (`832.1`, `832.2`, `tools/loop/tests/loop-mutations.sh`). Verify:
      `bash tools/loop/tests/loop-mutations.sh --only "832.1 832.2"` → `red-when-mutated: 2 · not-noticed: 0 ·
      restore-failures: 0`, each on the assertion that owns it (`evidence/mutation-run.log`), and `--check` reports
      both records still apply (`evidence/mutation-check.log`).
- [x] 4.2 The siblings the rule is read beside still pass:
      `report#theSpendIsSplitByPhaseRoleAndGate`, `report#aStreamWithoutDurationsPrintsDash`,
      `report#aRolesTokensRecordTheModelThatAnswered`, `report#aSessionThatSwitchedModelRecordsBothTallies`,
      `report#aStateWithoutAModelKeepsItsRoleTotals`, `report#aRoleWhoseTurnNamedNoModelKeepsItsRow`,
      `report#trendFiguresFromState`, `report#anItemBelowTheCallsPerTurnFloorIsNamed`,
      `report#anItemRecordsItsRepeatReads`, `report#zeroUsagePrintsDashNotZero` (`evidence/green-on-change.log`).
- [x] 4.3 The structural checks of the files this change edits — it compiles nothing: `bash -n tools/loop/report.sh`,
      `bash -n tools/loop/tests/loop-cases.sh`, `bash -n tools/loop/tests/loop-mutations.sh`,
      `tools/check-openspec-hygiene.sh`, `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`,
      `openspec validate fix-a-spend-total-names-its-denominator --strict` (`evidence/structural-checks.log`). No
      build is run for a delta of shell and prose.
