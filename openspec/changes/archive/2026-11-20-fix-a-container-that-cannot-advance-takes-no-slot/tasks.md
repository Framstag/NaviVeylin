# Tasks — the container as a unit in the pre-screen

Each task names the check that says it is done. The suite is `tools/loop/tests/loop-selftest.sh`; a case is reached by
name (`--case <group>#<name>`) and the whole group by `--group prescreen`.

- [x] 1. **The fixture.** `tools/loop/tests/fixtures/scenarios/epic-stalled/issues.json`: `#31` awaits a decision under
  container `#960`, `#32` is its sibling and workable, `#33` and `#34` sit under container `#961`, `#35` is the only
  child of `#962`, `#36` belongs to no container.
  *Check:* `bash tools/loop/tests/loop-selftest.sh --case prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot`
  reaches the fixture and asserts the verdicts it drives (red on the base, green after task 3).
- [x] 2. **The two cases.** `# files:` first in each body; both names in `LOOP_SHELL_CASES` and in the dispatch, which
  is the third set the driver reads.
  *Check:* `bash tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` passes (PASS,
  2026-11-20T08:19Z), and the group's `--list` names both rows.
- [x] 3. **The screen.** `cut_within_run` (`tools/loop/prescreen.sh:281`) and the pass at `:743` of the same file, which
  refuses a queued candidate as `epic-stalled` when its container recorded a child cut at a bound or has every child
  the screen saw excluded with the decision verdict.
  *Check:* `bash tools/loop/tests/loop-selftest.sh --case 'prescreen#aContainer*'` — 3 of 3 pass — and the group green:
  `bash tools/loop/tests/loop-selftest.sh --group prescreen` (44 cases, all passed, 2026-11-20T08:14Z).
- [x] 4. **The mutation.** Record `938.1` in `tools/loop/tests/loop-mutations.sh`: the bound arm emptied, so
  `prescreen#aContainerCutAtABoundTakesNoFurtherSlot` goes red and the restore goes green.
  *Check:* `bash tools/loop/tests/loop-mutations.sh --check --files tools/loop/prescreen.sh` reports every selected
  record applying (16 of 431), and `bash tools/loop/tests/loop-mutations.sh --only 938.1` reports
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.
- [x] 5. **The guideline.** `guidelines/Backlog.md:87` §3 — the container rule gains the level between the container and
  the child: a container whose children cannot close is refused as a container, naming what it waits for, and the two
  cases are named beside the container's own.
  *Check:* `tools/check-doc-routes.sh` and the doc-value checks in the gate; the paragraph cites
  `prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot` and
  `prescreen#aContainerCutAtABoundTakesNoFurtherSlot`, which exist.
- [x] 6. **No build.** No compiled file is touched, so no Gradle suite is owed and the structural check of the files this
  change edits is the loop suite above; the item's gate is the run's (`./tools/loop/gate.sh`), deferred to the gate
  child.
  *Check:* `git status --porcelain` under the change root names `tools/loop/**`, `guidelines/Backlog.md` and
  `openspec/changes/fix-a-container-that-cannot-advance-takes-no-slot/**` only.
