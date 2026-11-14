# Tasks — the layout table, the shared names and the source order

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate: `bash tools/loop/tests/loop-selftest.sh
--files tools/loop/…` while iterating, one full `bash tools/loop/tests/loop-selftest.sh` before the change is
complete. No Gradle build is owed — no compiled file changes (`guidelines/Build.md` §2, the scope block).
**Nothing moves in this change.**

## 1. The declaration, describing today

- [x] 1.1 `tools/loop/layout.tsv`: one row per engine script (12 today) with `file`, `bound`, `order`; `bound` =
  `wc -l` today + 25 % rounded up. Verify: `wc -l` per file against the row, printed into the change's evidence.
  Satisfies `Every engine script enters one layout table`.
- [x] 1.2 `tools/loop/shared.tsv`: one row per name more than one file reads — `name`, `assigned by`, `read by`.
  Harvested from a read of the function bodies; `local` names get no row. Verify: the suite is green with nothing
  moved, and the row count is recorded in the evidence. Satisfies `Every name two engine files share is declared`.
- [x] 1.3 The source order rows in `layout.tsv` reproduce the order `run.sh` already sources its files in today
  (`lib.sh` and nothing else); record the order the move will use in the row's comment, so the next child has one
  place to change.

## 2. The three static cases

- [x] 2.1 Case `driver#everyEngineScriptIsInTheTable`: compare the table's file rows against the engine's scripts in
  both directions. Declares `# files: tools/loop/ tools/loop/layout.tsv`.
- [x] 2.2 Case `driver#noEngineFileExceedsItsBound` and `driver#aFileWithoutABoundIsRefused`: `wc -l` per row, and a
  fixture row with no bound and one with `0`.
- [x] 2.3 Case `driver#aNameAssignedTwiceIsRefused`, `driver#anUndeclaredReadIsRefused`,
  `driver#theSharedNamesAreDeclared`: one fixture per refusal plus the set comparison of 1.2.
- [x] 2.4 Case `driver#theSourceOrderIsDeclared` and `driver#aCallToALaterFilesFunctionFails`: the entry's source
  lines against the order rows, and a static compare of each file's called names against the defining file's
  position in that order.
- [x] 2.5 Re-point the `# files:` declarations of the cases that now also read the tables (a declaration that names a
  table is a declaration the selection can use afterwards).

## 3. Documents and evidence

- [x] 3.1 `guidelines/Backlog.md` section 7 names `tools/loop/layout.tsv` and `tools/loop/shared.tsv` as the two files a
  person reads before editing the engine. Verify `bash tools/check-doc-routes.sh`.
- [x] 3.2 `guidelines/BuildGate.md` §2a states that a `tools/loop/**` delta's cost is visible in the table, so a
  reader estimates before iterating.
- [ ] 3.3 Record in the change's evidence: the 12 rows with their line counts and bounds, the shared-name count, and
  the wall time of one full suite run. The first two are taken in this item's evidence directory
  (`.pi/issue-loop/items/890/layout-rows.txt`: 12 rows with their lengths, 90 shared names, the rows themselves); the
  wall time is the gate child's run, because this item's gate is deferred to it.

## 4. Verification

- [ ] 4.1 Full suite green, with its case count and wall time recorded — the gate child's run: this item's gate is
  deferred to it (issue #837), so the number is taken there and not here.
- [x] 4.2 Revert-check for the bound: set one row's bound one line below the file's actual length and
  `driver#noEngineFileExceedsItsBound` must fail; restore, forced green.
- [x] 4.3 Revert-check for the table's completeness: delete one row and `driver#everyEngineScriptIsInTheTable` must
  fail; restore, forced green.
- [x] 4.4 Revert-check for the shared-name rule: delete one row of `shared.tsv` and
  `driver#theSharedNamesAreDeclared` must fail; restore, forced green.
- [x] 4.5 Revert-check for the order rule: swap two order rows and `driver#theSourceOrderIsDeclared` must fail;
  restore, forced green.
- [x] 4.6 File every unrelated defect this work finds with `.pi/skills/create-issue` — none found: the two failures
  this pass met were this change's own declaration (`shared.tsv`'s stale rows, and the bound reader's tab split).
