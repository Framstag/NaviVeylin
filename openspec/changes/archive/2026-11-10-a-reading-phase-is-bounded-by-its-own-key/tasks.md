# Tasks — a reading phase's context bound, and a rate that counts every cut

## 1. The role's own context stop

- [x] 1.1 `tools/loop/run.sh`: add `child_context_bound_key`, `child_context_bound` and `child_context_bounds` beside
  the item's keys, read through `child_role` as `child_turn_bound` is; the item's key is the fallback. — *spec:
  "The bound on a child is priced in what it spends, not in how many turns it takes"; verified by
  `run#eachRoleIsBoundedByItsOwnContext` and mutation `270.1`.*
- [x] 1.2 `tools/loop/run.sh`: `run_child` ends a child at its role's bound and names the key that fired in the
  `LOOP-TIMEOUT` line. — *spec: same requirement, scenario "A reading phase is bounded by its own key"; verified by
  `270.2`.*
- [x] 1.3 `tools/loop/run.sh`: the run's header prints `item-context-bounds=…` for every role. — *spec: same
  requirement ("both keys SHALL be recorded values named in the run's header"); verified by `270.3`.*
- [x] 1.4 `tools/loop/loop.conf`: record `LOOP_ITEM_CONTEXT_MAX_WORKER_REWORK=250000` and
  `LOOP_ITEM_CONTEXT_MAX_REVIEWER=250000` with the 2026-11-10 measurement and the reason the other four inherit
  200 000; leave `LOOP_ITEM_CONTEXT_MAX` untouched. — *verified by the header assertion in
  `run#eachRoleIsBoundedByItsOwnContext`, which quotes the six figures.*

## 2. The rate the cuts are judged by

- [x] 2.1 `tools/loop/report.sh`: the `bound:` line counts every item a bound ended — the result `cut`, plus the
  `blocked` + `stopReason: bound` pair a pre-`cut` record carries — and reads the archive with the same predicate. —
  *spec: "The rate of items the run's own bound ended is printed beside the archive's"; verified by
  `report#theBoundRateIsNamed` and mutation `270.4`.*

## 3. The gate step that was red before any item

- [x] 3.1 `tools/feature-list/specs.json`: classify `architecture-test-suite`, `package-structure`,
  `domain-vocabulary` and `vocabulary-projection` (`build-tooling`, not user-visible, no surface), the four delta spec
  ids the open changes `package-structure-as-five-kinds` and `domain-vocabulary-is-declared` owe. — *verified by
  `bash tools/gen-feature-list.sh --check-classification` printing `unclassified: 0` and exiting 0.*
- [x] 3.2 `tools/doc-routes.json`: name the two rules no glob reached — `FeatureList.md §1`'s bullet rule and
  `UICar.md §3c`'s never-drawn-frame rule — in the routes that already carry their sections, so
  `tools/check-rule-routes.sh` is green on a tree no change has touched. — *verified: all 587 named rules reached, and
  the two errors the tree carried (`::error … is reached by no glob`) gone; 7 of the archive's 8 gate waivers were
  this suite.*

## 4. Cases and their falsification

- [x] 4.1 `tools/loop/tests/loop-cases.sh`: new case `run#eachRoleIsBoundedByItsOwnContext` — the reviewer reads
  200 000 against its own 250 000 and answers (the item keeps its outcome), and a second run's reviewer reads
  260 000 and is ended with a transcript naming `LOOP_ITEM_CONTEXT_MAX_REVIEWER`. Red on HEAD, where the item's
  160 000 ends the first round. — *verified: `PASS run#eachRoleIsBoundedByItsOwnContext`.*
- [x] 4.2 `tools/loop/tests/loop-cases.sh`: `report#theBoundRateIsNamed` extended with an item a review round's bound
  ended, which carries no mechanism and must still be counted. — *verified: `PASS report#theBoundRateIsNamed`.*
- [x] 4.3 `tools/loop/tests/loop-mutations.sh`: records `270.1`–`270.4`, one per invariant. — *verified by
  `--only "270.1 270.2 270.3 270.4"`: 4 red, 0 not-noticed, 0 restore-failures; `--check` applies all four.*

## 5. Documents

- [x] 5.1 `guidelines/Backlog.recipes.md` §7b: extend the refused-raise record (`#833`, `#836 NOT_PLANNED`) with the
  role-scoped keys, so a reader who meets the refusal also meets the lever that moved instead. — *verified by
  `tools/check-rule-routes.sh` and `tools/check-doc-values.sh`.*
- [x] 5.2 `tools/loop/loop.conf` commentary beside `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` now names the two keys the
  context stop is scoped to, instead of claiming the item's key is the only lever. — *verified by
  `tools/tests/check-doc-values-selftest.sh`'s scope over `loop.conf`.*

## 6. The gate for a `tools/loop/**` delta

- [x] 6.1 `bash tools/loop/tests/loop-selftest.sh --from-git <base>` — every case the delta's files declare.
- [x] 6.2 `bash tools/loop/tests/loop-mutations.sh --check` — every record applies to the line it quotes (the
  harness's cheap pass over all 321).
- [x] 6.3 `bash tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh`,
  `tools/check-doc-values.sh`, `tools/check-todo-references.sh`, `tools/check-rule-routes.sh`,
  `tools/check-portability.sh` — all six exit 0 on this delta.
- [x] 6.5 `bash tools/loop/tests/loop-selftest.sh --files "tools/loop/run.sh tools/loop/loop.conf tools/loop/report.sh
  tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh"` — 398 of 398 cases passed, 530 s.
- [x] 6.4 No Gradle build and no device run: the delta touches no compiled file (`guidelines/Build.md` §2, the scope
  block), and no native, UI or Android Auto surface.
