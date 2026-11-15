# Tasks

## 1. Measure before changing code

- [x] Count the archive's `cut` records and the ones that name no class: 169 state files, 511 items, 39 `cut`, 24
      with `bound` + key, 15 with none — all fifteen naming a review round the run ended
      (`evidence/archive-census.txt`).
- [x] Read the shipped requirement the records conform to: "An item the run's own bound cut is recorded `cut`, not
      `blocked`", whose second paragraph says a round the run ended "carries none, as it did before".
- [x] Read #923's change: it recorded the round's ending in `metrics.roundBound` and left the item's class off,
      because that is what the sentence above asked for.

## 2. The record writes the class for a review round

- [x] `run.sh`: the pre-gate round's cut arm sets `stop_reason="bound"` beside the reason it already writes; the key
      comes from the existing reader over that reason's quoted `LOOP-TIMEOUT` line.
- [x] `run.sh`: the after-gate turn/context arm and the reviewer-deadline arm set `stop_reason="bound"` beside
      `outcome="cut"`.
- [x] `run.sh`: between the last arm and the outcome write, a cut whose class is a bound and whose key is empty takes
      the key and figure the round recorded, on the same `--bound-key`/`--bound-value` arguments the item's own
      child uses.
- [x] `metrics.roundBound` is unchanged: the round, its key and its figure, written exactly as #923 wrote them.

## 3. The readers state the new rule

- [x] `state.sh`: the `report` and `show` prose, and the `done` rewrite's prose, say a round's ending travels with
      the item's class. No predicate changes — the `stop` column already prints `bound <key>=<value>` when the record
      carries both.
- [x] `lib.sh`: `bound_class_jq`'s prose says the class is the run's write and that no class is derived for a record
      written before this change.
- [x] `report.sh`: the bound rate's two prose blocks say a new record answers in both fields and an old one by its
      result. No expression changes; the figures do not move.

## 4. The spec delta

- [x] `specs/agent-issue-loop/spec.md` MODIFIED "An item the run's own bound cut is recorded `cut`, not `blocked`":
      the clause that a round "carries none" becomes the clause that a round a bound ended carries the class and the
      key, and the two scenarios that state it gain the assertion. The other six scenarios are carried verbatim.
- [x] `openspec validate fix-issue-898-a-review-round-bound-is-the-items-stop-class --strict` — valid.

## 5. The case and the mutations

- [x] `run#aReviewRoundBoundIsTheItemsStopClass`: the item carries `bound`, `boundKey` and `boundValue` for a review
      round the reviewer's turn bound ended, keeps `metrics.roundBound`, and the report's `stop` cell names the pair.
      Registered in the selection list and in the dispatch `case`.
- [x] `run#aPreGateReviewEndedAtItsBoundNamesIt` gains the class and key assertion for the round before the gate.
- [x] `run#aRoundTheRunEndedRecordsItsBound` no longer asserts that the item carries no class.
- [x] Red on the pre-fix tree, both cases (`evidence/pre-fix-red.txt`).
- [x] `898.1`, `898.2`, `898.3` in `loop-mutations.sh`: the after-gate class, the pre-gate class, and the key the
      round's line yields. Each reds its named case and restores green (`evidence/mutation-898.log`).
- [x] `--check` over the three records: all apply.

## 6. The prose that repeated the clause

- [x] `guidelines/Backlog.recipes.md` §7b: the deadline cut and the round's cut both carry `bound` and its key.
- [x] `guidelines/Backlog.recipes.md` §7b: the bound rate's paragraph says the class covers a round since #898, and a
      record written before it answers by its result.
- [x] `guidelines/Backlog.recipes.md` §7e: the class paragraph, the branch-record paragraph (a round's cut is the
      item's class as well, and the `stop` cell prints the pair) and the `roundBound` paragraph (the round is the
      field beside the class, not instead of it).

## 7. Evidence

- [x] `evidence/pre-fix-red.txt`, `evidence/mutation-898.log`, `evidence/focused-cases.txt`,
      `evidence/archive-census.txt`.

## 8. Left to the parent

- [ ] The whole case set, the full mutation set and the build gate.
- [ ] Archive: this change is not archived here, and it does not merge, push or delete the branch.