# Tasks — fix-calls-per-turn-is-a-reported-number

Each task is verified by the command beside it. The gate is run **once** (`tools/loop/gate.sh`); the iteration
before it used focused selections only.

- [x] 1. The red case, on the base, before any engine change.
  - Write `report#anItemBelowTheCallsPerTurnFloorIsNamed` (declaration, dispatch, body) into `tools/loop-cases.sh`.
  - Verify: `bash tools/loop-selftest.sh --case report#anItemBelowTheCallsPerTurnFloorIsNamed` on the base — one
    `FAIL`, `the item's tool calls are counted from its event stream: expected '6', got 'null'` — log
    `evidence/red-on-head.log`.

- [x] 2. The counts the ratio is derived from.
  - `tools/loop/state.sh`: `child_usage_json` records `calls` and `turns`, and `cmd_done` stores both under
    `metrics`. Scenario: *A report reports the shape a run spent its budget in*.
  - Verify: `bash tools/loop-selftest.sh --case 'run#childUsageIsRecordedPerItem'` — `PASS`, and the case asserts
    `metrics.calls` 5 and `metrics.turns` 2 over the item's two children.

- [x] 3. The table's column.
  - `tools/loop/state.sh`, `cmd_report`: a column `calls` appended after `over`, printing the item's ratio or `-`.
  - Verify: task 1's case reads `table_cell 17` of both rows.

- [x] 4. The trend's rate and the floor.
  - `tools/loop/report.sh`: `calls:  <ratio> calls per turn (<calls> calls / <turns> turns) · below the recorded
    floor of <n> per turn: #<issue> <ratio>`, or the floor as off (naming no item), or `-` with its reason.
  - Verify: task 1's case, green.

- [x] 5. The recorded floor, with its measurement.
  - `tools/loop/loop.conf`: `LOOP_ITEM_CALLS_PER_TURN_MIN=1.05`, the archive's p25, with the derivation in the
    comment beside it.
  - Verify: `grep -n '^LOOP_ITEM_CALLS_PER_TURN_MIN=' tools/loop/loop.conf` reads the same number the comment
    derives.

- [x] 6. The case the new column rewrites, and the case the new counts extend.
  - `tools/loop-cases.sh`: `report#wallTimeAndReasonInTheTable` (17 pipes → 18) and
    `run#childUsageIsRecordedPerItem` (the two counts).
  - Verify: `bash tools/loop-selftest.sh --case 'report#wallTimeAndReasonInTheTable'` and
    `--case 'run#childUsageIsRecordedPerItem'` — both `PASS`, as do `report#anItemOverTheTokenBoundIsFlagged`,
    `report#zeroUsagePrintsDashNotZero`, `report#emptyRunSaysNone`, `report#headerAndTableShape` and
    `report#trendFiguresFromState`.

- [x] 7. The rule the measurement implies.
  - `tools/loop/prompts/worker.md`: independent reads, greps and lookups belong in one turn; a path already read
    is cited rather than opened again; the run reports the ratio and ends a child at the turn bound.
  - Verify: the gate's `tools/loop-selftest.sh` (the prompt is read by the run's own cases).

- [x] 8. The four mutation records.
  - `tools/loop-mutations.sh`: `78.1` (the trend never names an item below the floor), `78.2` (the column never
    prints a ratio), `78.3` (the calls are never recorded), `78.4` (a predating state's dash carries the wrong
    reason).
  - Verify: `bash tools/loop-mutations.sh --only '78.1 78.2 78.3 78.4' --check` — all four apply; the same command
    without `--check` — `red-when-mutated: 4 · not-noticed: 0`, log `evidence/mutations.log`.

- [x] 9. The documents that own the value.
  - `guidelines/Backlog.recipes.md` (the loop's bound paragraph, the report paragraph and the trend paragraph),
    `PORTING.md` (the key list) and `.pi/skills/loop-spend/SKILL.md` (where the *why* lives).
  - Verify: the gate's `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` steps, both green.

- [x] 10. The change's own artifacts validate.
  - `openspec validate fix-calls-per-turn-is-a-reported-number --strict`.
  - Verify: `Change 'fix-calls-per-turn-is-a-reported-number' is valid`.

- [x] 11. The gate, once.
  - Verify: `tools/loop/gate.sh` — the `LOOP-GATE:` line sums every step and names the base the selection was taken
    from (`--from-git 47ec83e5…`, this tree's merge-base with `origin/main`), while the issue-tooling step's own
    line names the **measured** `HEAD` (`064b7254`). The loop suite's own tally is
    `evidence/gate-tally-loop-selftest.xml` (`tests="310" failures="0"`, the whole set: `selected 310 of 310
    cases`), and the record check's is `evidence/gate-tally-mutations-check.xml`. Log `evidence/gate.log`.

- [x] 12. The independent review, and its findings closed.
  - Round 1 (`evidence/review-round-1.md`) refused: two P0s (an apostrophe inside the `state.sh` jq program, and the
    change being taken from the tree by a peer session's merge) and its P1/P2 findings (the floor-off negative
    half, the predating-state clause, the `turn_end` count, the p75, the token-precedent sentence, the floor
    sanitiser, the mutation description, the missing `openspec validate` step).
  - Round 2 (`evidence/review-round-2.md`) checked those closed and refused again on four smaller points, all
    closed here: the table's rule row carried one cell more than its header (17 columns, 18 cells); the p75 was
    over-corrected to 1.21 where the raw ratios give 1.22; `report.sh` said the floor sanitiser refuses `05`, which
    it reads as 5; and the predating-state sub-case left the state's digest stale, so the mutation's red reason
    was the engine's drift banner rather than the assertion.
  - Rounds 3 (`VERDICT: NOTES`) and 4 (`VERDICT: NOTES`) checked those: round 3 closed four outright and found the
    two prose sites that mis-stated the evidence-check result and the p75 sites, round 4 confirmed the closure and
    found only record-keeping (this list's own status line, a typo, the gate line's revision wording, and the
    red-on-HEAD log's missing header), all repaired here. No engine line and no case assertion was found wrong in
    any round.
  - Verify: `evidence/review-round-4.md` — `VERDICT: NOTES`, closed as listed above; no round reported an engine
    or case defect.

- [x] 13. Evidence and archive.
  - Every cited log and tally copied into `evidence/`, and `bash tools/loop/evidence-check.sh
    openspec/changes/fix-calls-per-turn-is-a-reported-number --root .` run once with its output kept in
    `evidence/evidence-check.log`.
  - It reports two classes of finding, ten lines in all, and every one of them is raised by the two verbatim
    review reports rather than by an artifact of this change:
    * `C1` — the section pointers in section-sign form (subsections 7b and 7e of `guidelines/Backlog.recipes.md`)
      inside the two verbatim review reports (`review-round-1.md`, `review-round-2.md`) are read as citations of
      a migrated backlog entry and have no row in the migration's `import-evidence.md`. They are section names,
      not item citations.
    * `C2` — a review report quotes the gate's own `LOOP-GATE:` line, whose `tests=` figure is the **sum over the
      gate's steps**; no JUnit XML carries a summed figure, so the check cannot confirm it. The tallies that
      exist are the per-suite ones, and the one this change relies on is `tests="310" failures="0"
      (evidence/gate-tally-loop-selftest.xml)`.
  - Both are properties of `tools/loop/evidence-check.sh` reading a prose report as a claim, not of this delta;
    they are reported here rather than repaired, the way the #662 change reported a mutation record that could not
    fail.
  - The change is archived into `openspec/changes/archive/2026-11-03-fix-calls-per-turn-is-a-reported-number/` and
    the delta merged into `openspec/specs/agent-issue-loop/spec.md`. The gate was then run once more over the
    archived tree — the steps the archive paths owe, `openspec` hygiene, the classification gate and the
    `tools/loop/**` suite again — and that run is `evidence/gate-after-archive.log` with its loop tally
    `evidence/gate-after-archive-tally-loop-selftest.xml`; it found nothing the first run did not.
