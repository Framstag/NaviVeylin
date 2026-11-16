# Tasks — the read that follows an edit is the file's head, not the file

## 1. Measure before the plan was fixed

- [x] 1.1 `./tools/loop/measure.sh` from the change root: the block names the paths the change touches and the checks
  their pathspec owes; `LOOP-MEASURE-SUITE cases=465 cmd=tools/loop/tests/loop-selftest.sh --list` is the suite this
  change's cases live in, and the block's `LOOP-MEASURE-GATE` line states the tree's gate has not run, which is why no
  gate timing is quoted here.
- [x] 1.2 The surface, counted before the plan: `./tools/look.sh 'verificationResult|VERIFICATION_READ_BOUND|
  LOOP-REPEAT-EDIT'` → 19 matches in 4 files (`evidence/surface-886.txt`); see `proposal.md` §"Surface".
- [x] 1.3 Verification of the item's claim against the tree: the whole-text return is at `tools/pi-trim-result.mjs:294`
  (base `8493f017`), and the case that asserted it — `repeat#aRepeatAfterAnEditIsHandedOverWhole` — existed, so the
  claim was neither stale nor already fixed.

## 2. The red case, first

- [x] 2.1 The plan's cheapest check first, because this delta writes a case:
  `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → PASS.
- [x] 2.2 `repeat#aVerificationReadIsBoundedNotHandedOverWhole` written in `tools/loop/tests/loop-cases.sh:19195`
  (list `:464`, dispatch `:1216`), the old case rewritten and renamed because it asserted the behaviour removed.
  Verification: red on the base before the production change —
  `loop-selftest: 1 of 1 cases failed`, exit 1, `tests=1 failures=1`, 2026-11-16T03:55:58Z (XML not retained — the
  selftest tree it was written in is gone; the failure text is kept)
  (`evidence/red-on-head-repeat-886.log`), the failure `the read after the edit is not the whole file again:
  'LAST-LINE' should not appear in: …`.

## 3. The bound

- [x] 3.1 `VERIFICATION_READ_BOUND = 2000` (`tools/pi-trim-result.mjs:138`), with the measurement it is set against
  stated beside it.
- [x] 3.2 `verificationNotice` (`:145`) — the path, the turn the item read it at, the bound, the size and the range
  call — and `boundedHead` (`:154`), the surrogate-safe cut both bounds now share (`trimResult` uses it too).
- [x] 3.3 `verificationResult` (`:316`) and the branch that calls it (`repeatResult:294`): gate/evidence output whole,
  a text that fits the bound whole, the head plus the notice otherwise, and the withheld volume on the record line the
  unchanged repeat already writes.
- [x] 3.4 No compiled file is touched, so this change replaces its build task with the structural checks of the files it
  edits: `./tools/loop/measure.sh`'s `LOOP-MEASURE-CHECK` selection for `tools/**` and `guidelines/**` — in particular
  `tools/check-doc-values.sh`, `tools/check-doc-routes.sh` and `tools/check-openspec-hygiene.sh`, which are the checks a
  guideline, a spec delta and a change directory owe.

## 4. The cases and the mutation record

- [x] 4.1 The new case green after the change: `bash tools/loop/tests/loop-selftest.sh --case
  'repeat#aVerificationReadIsBoundedNotHandedOverWhole'` → PASS.
- [x] 4.2 The cases the delta reaches green: `--case 'repeat#*'` → 8 of 8 passed, and `--case 'trim#*'` → 3 of 3 passed
  (the cut both bounds share).
- [x] 4.3 The two cases whose adjacent prose said the edit comes back "whole" realigned
  (`tools/loop/tests/loop-cases.sh:19051`, `:19005`): their files fit the bound, so the assertion stands and
  the reason it stands is now written down.
- [x] 4.4 Mutation record `886.1` (`tools/loop/tests/loop-mutations.sh:997`) puts the whole text back. Verification:
  `bash tools/loop/tests/loop-mutations.sh --only 886.1` → RED, then restore green
  (`evidence/mutation-886.1.log`); and `--files ../pi-trim-result.mjs --check` → all 15 selected records apply
  (`evidence/mutations-check-886.log`).

## 5. The prose that states the rule

- [x] 5.1 `guidelines/Backlog.recipes.md` section 7b, the paragraph naming the ledger's line shape (`:201-215`): the
  bound, the
  notice's shape, the record line it writes and the gate-evidence exemption.
- [x] 5.2 The same section's case-and-mutation sentence (`:218-221`) names the new case and `886.1`.
- [x] 5.3 `tools/pi-trim-result.mjs`'s header (`:30-35`) and `repeatResult`'s doc comment: the branch is stated as the
  bound it now is, not as the whole hand-over it was.
- [x] 5.4 The delta spec (`specs/agent-issue-loop/spec.md`): requirement "The repeat rule's ledger is the item's, not the
  child's" modified, its edit clause rewritten, and the scenario the bound owes added.

## 6. Handed over at the diff

- [x] 6.1 Evidence taken as it was produced and left in the item's directory — `red-on-head-repeat-886.log`,
  `green-repeat-886.log`, `mutation-886.1.log`, `mutations-check-886.log`, `surface-886.txt`, `measure-886.log` — for the
  gate child's step 3a to version into this change's `evidence/`, which is the copy `proposal.md` and this file name.
- [x] 6.2 No gate is run here: the run deferred it to a fresh child, and the change is left unarchived for it.
