# Tasks

## 1. The arm that produces a refused landing is driven (spec `agent-issue-loop`, "An item a merge refused names its landing")

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `run#doesNotCloseOnAMergeTheBaseCannotTake` — two items over the `two`
      fixture, the first item's child writing `uncommitted.txt` and `left-behind.txt` into the project's own tree,
      each item's change and fix named after its count so the second branch carries work of its own. Asserts the second
      item `result: blocked`, `stopReason: unmergeable`, `mergeRefused.branch` a `fix/issue-22-…` branch,
      `mergeRefused.paths` holding **both** paths, the block's note quoting the refusal, no `merged` record, the second
      fix not on the base, and the item escalated rather than closed.
      — done when: `loop-selftest.sh --case run#doesNotCloseOnAMergeTheBaseCannotTake` prints `PASS`.
      — red on HEAD, recorded: `loop-mutations.sh --only 58.2` on a clean clone of `7ecab8e6` prints `RED` then
      `RESTORE-FAILED 58.2 … stayed red after the restore` (`evidence/head-58-2.txt`), because the case the record
      names is not in the base tree. Green on this change: `PASS run#doesNotCloseOnAMergeTheBaseCannotTake`.
- [x] 1.2 `tools/loop/tests/loop-cases.sh`: the case's name is in the selection list and in the dispatch, so
      `driver#aCaseWithNoSelectionRowIsRefused` and `driver#everyCaseDeclaresTheFilesItCanReach` still pass, and
      `loop-selftest.sh --list` counts one case more than at the base (478 → 479).
      — done: `LOOP-MEASURE-SUITE cases=479`, and `loop-selftest.sh --group driver` passes all 34 cases,
      `driver#everyCaseDeclaresTheFilesItCanReach` among them.
- [x] 1.3 `tools/loop/tests/loop-mutations.sh`: `883.4` mutates `run.sh:4343`'s guard to `if false; then` and requires
      `run#doesNotCloseOnAMergeTheBaseCannotTake` to go red, and `58.2` — whose case that is — to notice and restore
      green again.
      — done: `loop-mutations.sh --only '883.4 58.2'` reports `red-when-mutated: 2 · not-noticed: 0 ·
      restore-failures: 0`, and `loop-mutations.sh --check --files tools/loop/run.sh` passes.
      — taken: `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0` (2026-11-17T12:29:51Z and 12:30:24Z,
      `evidence/mutations-883.4-58.2.txt`), and `--check --files tools/loop/run.sh` reports `all 145 cases passed`.

## 2. The owning guideline section names the case

- [x] 2.1 `guidelines/Backlog.recipes.md` section 7d, the paragraph *A refused landing is a record*: the case that drives the
      arm and record `883.4` are named beside `883.2`, and `58.2`'s case is named as the one it holds.
      — done: the paragraph names `run#doesNotCloseOnAMergeTheBaseCannotTake` and `883.4`;
      `tools/check-doc-routes.sh` passes (`all 356 section references resolve`).

## 3. No compiled path, so no build

This change touches no compiled file: `tools/loop/tests/*`, `guidelines/*`, and this change directory. Its structural
check is `bash -n` over the two scripts it edits, `loop-selftest.sh --list` for the selection it adds, and the case and
records of section 1 — no build task stands in for them.
