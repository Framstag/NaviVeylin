# Tasks — consuming a prepared change

## 1. The copy

- [x] 1.1 `tools/loop/worktree.sh`: a function that copies `openspec/changes/<name>/` from the project's tree into
      the item's worktree, refusing a source the tree does not hold and recording `source`, `files` and `bytes` in
      the run's state for the item.
      Verification: case `worktree#theNamedChangeIsCopiedIntoTheItem` asserts the file list and the three recorded
      values.
- [x] 1.2 The copy runs before the worker process is started, in the same step that prepares the item's branch, and
      its failure leaves no branch and no child.
      Verification: case `worktree#aMissingChangeRefusesTheItem` asserts exit status 5, the path in the message, an
      empty branch list and no child process.
- [x] 1.3 The copy follows no symlink out of the source and copies no build output — it copies the change directory
      only.
      Verification: a fixture whose change directory contains a symlink is copied without its target, asserted by
      the same case.

## 2. The pre-screen's tier branch

- [x] 2.1 `tools/loop/prescreen.sh` reads `tier` and `change` through the body-record reader C2 adds, and treats a
      tier-A item exactly as today.
      Verification: case `prescreen#aTierAItemPassesUnchanged`, and the suite's existing pre-screen cases still pass.
- [x] 2.2 Tier B: the change directory must exist and the profile's `checks.changeReady` must report `ok` for it,
      both decided before the worktree is cut; a refusal writes the checker's verdict line on the item as a finding
      and starts no child.
      Verification: case `prescreen#aChangeThatIsNoLongerReadyIsRefused`.
- [x] 2.3 A closed item is not re-selected, so a consumed change cannot be consumed twice while its source is still
      on disk.
      Verification: case `prescreen#aClosedItemIsNotSelected`.

## 3. The package

- [x] 3.1 `tools/issues/core/WorkPackage.java`: a third block, `<<<ITEM_CHANGE` / `ITEM_CHANGE`, carrying `change:`,
      `path:`, `readiness:` and the recorded decisions; `change: none` when the item names no change.
      Verification: cases `prompt#thePackageCarriesTheChange` and `prompt#aTierAItemSaysSo` assert exactly one
      opening and one closing marker each.
- [x] 3.2 The renderer neutralises the block's own markers inside any field, in the way the two existing blocks do.
      Verification: `prompt#inlineInjectionCannotReachTheInstructionPosition` still passes and a new assertion for
      `ITEM_CHANGE` is added to it.

## 4. The worker and the reviewer

- [x] 4.1 `tools/loop/prompts/worker.md`: Phase A re-verifies the named change against the tree; Phase C states
      *do not re-plan* with its reason; a fork the change does not settle is `needs-decision` naming the fold's line;
      a claim that moved is needs-diagnosis naming the path.
      Verification: cases `run#aTierBItemWritesNoSecondChange`, `run#aForkTheChangeDoesNotAnswerIsNamed`,
      `run#aChangeWhosePremiseMovedIsNotRePlanned`.
- [x] 4.2 `tools/loop/prompts/reviewer.md`: the round reads the item's `ITEM_CHANGE` and `ITEM_DECISION` blocks
      beside the change, and a `## Open decisions: none` the recorded question contradicts is a **blocking** finding.
      Verification: a case asserting the reviewer's prompt names both blocks, and one asserting the blocking
      classification of that finding.

## 5. The clean command

- [x] 5.1 `tools/loop/loop-clean.sh`: report the consumed change's source directory with its size, and remove it
      under `--apply`, beside the worktree's recorded build paths; a second run finds nothing.
      Verification: cases `clean#aConsumedChangeIsReported` and `clean#aSecondRunFindsNothing`.

## 6. The documents

- [x] 6.1 `guidelines/Backlog.md` §6: the tier-B shape stated as a fact — the change is committed nowhere, the run
      copies it in, the source is the clean command's.
      Verification: §6 states the three facts and references the capability rather than restating its requirements.
- [x] 6.2 `guidelines/Backlog.md`, section 7 "The loop": the phase table gains the preparation step, one row, and the paragraph on what
      a child is started with gains the change block.
      Verification: `grep -n 'ITEM_CHANGE' guidelines/Backlog.md` finds "The loop"'s index paragraph, and the phase
      table's row is in `guidelines/Backlog.recipes.md`, section 7a (the corpus split moved the bodies there).
- [x] 6.3 `tools/loop/loop.conf`: the copy's recorded bounds — the directory family, and that it is a path a ported
      project declares.
      Verification: `bash tools/check-portability.sh` passes; the key is read, never hard-coded in the engine.

## 7. Verification and archive

- [x] 7.1 `bash tools/loop-selftest.sh --from-git <base>` passes with the new cases, and
      `tools/loop-mutations.sh --check` shows one record per new refusal.
      Verification: the suite's case count before and after is recorded in `evidence/suite-cases.txt` with the
      command that printed it.
- [x] 7.2 `openspec validate loop-consumes-a-prepared-change --strict` exits 0;
      `bash tools/loop/evidence-check.sh openspec/changes/loop-consumes-a-prepared-change --root .` resolves every
      pointer.
      Verification: both exit 0, outputs in `evidence/`.
- [x] 7.3 Archive; confirm the four requirements merged into `openspec/specs/agent-issue-loop/spec.md` and that the
      fourteen scenarios of *"Each item is worked by a fresh process…"* are unchanged.
      Verification: `openspec show agent-issue-loop --type spec --json | jq
      '[.requirements[] | select(.text | test("handed to its worktree"))] | length'` is 1, and the
      fresh-process requirement's scenario count is still 14.
