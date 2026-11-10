# Tasks — a recorded item's leftovers do not end the run

## 1. The rule

- [x] 1.1 `tools/loop/run.sh`: `recorded_here` reads the item out of this run's own state; `recorded_leftovers_of`
  adds the patch — `own_leftovers_of` where the cap arm wrote it, `keep_leftovers` otherwise. — *spec: "A start the
  worktree refuses mid-run names the item whose work it holds"; verified by
  `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn` and mutations `271.1`, `271.2`.*
- [x] 1.2 `tools/loop/run.sh`: the mid-run refusal discards and retries the start when `recorded_leftovers_of` holds,
  printing the record it relies on; every other owner keeps today's path (keep, escalate, record, stop naming item
  and files). — *same requirement; the unchanged half is `run#dirtyKeptWorktreeIsARecordedRefusal` (start-time) and
  `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` (cap arm, `13.10`).*

## 2. Cases

- [x] 2.1 `tools/loop/tests/loop-cases.sh`: `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn` replaces
  `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` and `run#aRefusedStartKeepsTheLeftoversItFound` — the `pre-commit`
  hook leaves #21's work in the tree, the run records it, writes the patch, discards, starts #22 and ends on its item
  cap. — *verified: red on HEAD (one item recorded, stop reason `the worktree is not clean`), green after.*
- [x] 2.2 `tools/loop/tests/loop-cases.sh`: both old names removed from the case list and the dispatch switch.

## 3. Mutations

- [x] 3.1 `13.10` re-pointed at the widened line it now quotes (`recorded_leftovers_of`), case
  `run#theStartAfterTheRunsOwnEndedChildIsNotRefused`. — *verified: RED.*
- [x] 3.2 `271.1` (`recorded_here` reads nothing) and `271.2` (`keep_leftovers` writes no record before the discard),
  case `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`. — *verified: both RED, restore green.*
- [x] 3.3 `53.1`–`53.3` and `62.6` retired, with the reason (their lines are reached only by the refusal's failure
  path, which no case exercises) written beside the block. — *verified: `loop-mutations --check` passes over the
  remaining 318 records.*

## 4. Documents

- [x] 4.1 `guidelines/Backlog.recipes.md` §7d: the refusal's recipe gains the recorded case and the measurement
  (5 runs, 1 699 minutes) beside the repair it already named. — *verified by `tools/check-doc-values.sh`,
  `tools/check-doc-routes.sh`, `tools/check-rule-routes.sh`.*
- [x] 4.2 The change's `design.md` states the coverage the retirement leaves open (an unwritable item directory), so
  a reviewer meets it as a fact rather than as a silent gap.

## 5. Gate for a `tools/loop/**` delta

- [x] 5.1 `loop-selftest.sh --files "tools/loop/run.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh"`.
- [x] 5.2 `loop-mutations.sh --check` over the whole set.
- [x] 5.3 `check-doc-routes.sh`, `check-doc-values.sh`, `check-rule-routes.sh`, `check-todo-references.sh`,
  `check-portability.sh`, `check-openspec-hygiene.sh`, `gen-feature-list.sh --check-classification`.
- [x] 5.4 No Gradle build and no device run: the delta touches no compiled file (`guidelines/Build.md` §2, the scope
  block), and no native, UI or Android Auto surface.
