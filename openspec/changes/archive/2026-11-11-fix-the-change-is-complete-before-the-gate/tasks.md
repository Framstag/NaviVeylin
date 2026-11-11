# Tasks — the change is completed before the gate

## 1. The red tests, on the base commit

- [x] 1.1 `bash tools/loop/tests/loop-selftest.sh --case 'gate#aRedGateRestoresTheChangeToItsUnarchivedName'` on
  `6264d0c3` — red, `1 of 1 cases failed`, because the helper the restore is made of does not exist yet
  (`evidence/red-on-HEAD-aRedGateRestoresTheChangeToItsUnarchivedName.log`, XML
  `tests="1" failures="1"`).
- [x] 1.2 The same for `'gate#theGateCompletesTheChangeBeforeTheGate'` — red: the gate child is told to archive
  nothing and the tail is told to archive and to repair (`evidence/red-on-HEAD-theGateCompletesTheChangeBeforeTheGate.log`,
  XML `tests="1" failures="1"`).
- [x] 1.3 The same for `'gate#aMovedChangeNamesTheSameTree'` — red: a change being worked is in no listing the digest
  reads, and the same files under `archive/` move it (`evidence/red-on-HEAD-aMovedChangeNamesTheSameTree.log`,
  XML `tests="1" failures="1"`).

## 2. The digest reads the change's own files under one name

- [x] 2.1 `tools/loop/lib.sh` — `loop_change_family`, `loop_change_path` (the archive's date prefix stripped) and the
  change-family enumeration inside `loop_tree_identity`, which re-applies the evidence exclusion to that half.
  Verification: `gate#aMovedChangeNamesTheSameTree` passes, and `gate#theGateAndTheRoundNameOneTree` and
  `gate#aThirdFullRunInOneSessionIsRefused` still pass.
- [x] 2.2 `tools/loop/loop.conf` — `LOOP_CHANGE_ARCHIVE_PREFIX` beside `LOOP_CHANGE_SOURCE_DIR`.
  Verification: `bash tools/check-portability.sh` passes, and the case of 2.1 reads the key from the profile.

## 3. A red gate restores the change to its unarchived name

- [x] 3.1 `tools/loop/lib.sh` — `loop_change_archive_dir` and `loop_change_restore`: one directory moved, and a name
  the tree carries no archive of refused with `LOOP-CHANGE-RESTORE-REFUSED:`.
  Verification: `gate#aRedGateRestoresTheChangeToItsUnarchivedName` passes.
- [x] 3.2 `tools/loop/run.sh` — the restore, after the closing child and decided from the item's verdict, with the
  gate's own tally (`gate_tally_state`) or a line that says the gate was not run standing in where no child reported
  one. Verification: the same case reads the two lines it calls, `run#aCloseOverARedGateIsWaivedOrBlocked` closes over
  a waived red tally with the archive standing, and `bash -n tools/loop/run.sh` passes.

## 4. The order each child is told

- [x] 4.1 `tools/loop/prompts/worker-gate.md` — Phase E completes the change (cited XMLs, the mechanical pointer
  check, the archive with its delta) **before** the gate, and a red gate is the run's to restore, not the child's to
  repair.
- [x] 4.2 `tools/loop/prompts/worker-tail.md` — Phase F writes no path outside the change's `evidence/`: it archives
  nothing, repairs nothing, and a pointer it would have fixed is `blocked` with the line it printed.
- [x] 4.3 `tools/loop/prompts/gate-deferred.md`, `tools/loop/prompts/gate-own.md` and `tools/loop/prompts/worker.md` —
  the order stated, so neither shape tells its child to archive after the gate and neither section says the tail writes
  the archive.
  Verification for 4.1–4.3: `gate#theGateCompletesTheChangeBeforeTheGate` passes.

## 5. The records the suite reads

- [x] 5.1 `tools/loop/tests/loop-cases.sh` — the three cases and their two registration sites (`LOOP_SHELL_CASES`,
  the dispatch table). Verification: each case runs green, `evidence/green-<case>.log`.
- [x] 5.2 `tools/loop/tests/loop-mutations.sh` — three records (`262.5` the enumeration, `262.6` the date prefix,
  `262.7` the restore). Verification: `bash tools/loop/tests/loop-selftest.sh --case
  'mutation#everyDeclaredRecordApplies'` passes, and `bash tools/loop/tests/loop-mutations.sh --only
  '262.5 262.6 262.7'` runs each record red with its timestamp and restores it green —
  `evidence/mutation-red-262.log` (`red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`).

## 6. The prose that owns the rule

- [x] 6.1 `guidelines/Backlog.recipes.md`, the section "The gate and its suite" — the paragraph that states the
  derivation now states the change's own files, the order and the restore, with the three cases named. Verification:
  `bash tools/check-doc-routes.sh` and `bash tools/check-doc-values.sh` pass.
- [x] 6.2 `openspec/changes/fix-the-change-is-complete-before-the-gate/specs/agent-issue-loop/spec.md` — one
  requirement removed with its reason, its successor added, and the digest paragraph modified, each scenario naming
  exactly one case. Verification: `openspec validate` and `bash tools/check-openspec-hygiene.sh` pass, and every
  scenario has a case in the list above.

## 7. The change's evidence, before the gate

- [x] 7.1 Every XML cited here is copied into this change's `evidence/` at the moment it is produced, and every
  `tests=`/`failures=` pair quoted here is carried by one of them. Verification:
  `bash tools/loop/evidence-check.sh openspec/changes/fix-the-change-is-complete-before-the-gate --root .` reports
  nothing.
- [x] 7.2 The structural checks this delta owes
  (`check-portability`, `check-openspec-hygiene`, `openspec validate`, `check-doc-routes`, `check-doc-values`) —
  `evidence/structural-checks.log` and `evidence/openspec-and-evidence.log`.

## 9. The rework the first gate bought

- [x] 9.1 The first gate run was red on this change's own paths — `run#aCloseOverARedGateIsWaivedOrBlocked`
  (`expected 'done', got 'blocked'`: the restore fired before the waiver existed) and
  `phase#oneCallPrintsThePhaseState` (a stale assertion of the archive step the change removes). Both repaired in one
  pass: the restore is decided from the item's verdict after the closing child, and the case asserts the phase's
  evidence-only rule instead. Verification: `evidence/rework-round-1.md` with the four focused case logs beside it.
- [x] 9.2 The second and last gate run of this item, over the corrected and completed tree — one line, never re-run.

## 8. The gate is not this session's

- [x] 8.1 The gate runs in the fresh child the run starts after this session's diff: that child applies the
  reviewer's findings, completes the change — the pointer check, the delta and the archive — and runs
  `./tools/loop/gate.sh` once on the completed tree, as `tools/loop/prompts/worker-gate.md` now states. This session
  prints `LOOP-DIFF-READY` and runs no gate.
- [x] 8.2 The delta merged into `openspec/specs/agent-issue-loop/spec.md` and the change archived under
  `openspec/changes/archive/<date>-fix-the-change-is-complete-before-the-gate/` are that child's, and the identity
  beside the diff this session hands over is the tree the gate then measures.
