# Tasks — fix-loop-own-leftovers-do-not-stop-run

## 1. The red case (requirement: *The dirt a run ended itself does not stop it*)

- [x] Write `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` in `tools/loop-cases.sh`: two eligible items, the
  first child overrunning a 2 s cap with an untracked file written first, the second child ready to work; declared
  in the case set and the dispatch table. Verify the file still parses (`bash -n tools/loop-cases.sh`).
- [x] Run it on the base and record the red: `evidence/red-on-head.log`, tally `evidence/red-on-head.xml`
  (`tests="1" failures="1"`, 2026-10-26T09:13:35Z) — `expected '2', got '1'` worker prompts, because the run stopped
  on the leftover the cap-ended child left. Nothing in the tree changes for this task.

## 2. The retry (same requirement)

- [x] Extract the `--reset-worktree` verbs into `discard_kept_worktree` with no change in behaviour, and call it
  from the start-time path. Verify: `run#resetWorktreeDiscardsWhatItNames`,
  `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard`, `run#resetWorktreeNamesTheDirtItCannotDiscard` pass.
- [x] Record the items whose leftovers `keep_leftovers` wrote (`own_leftovers`, set on the one path that writes a
  patch) and answer `own_leftovers_of` from the item plus the derived patch path. Verify: no case yet — the next
  task is what makes it observable.
- [x] Discard that tree and retry `start-item` once in the `*"not clean"*` arm; leave every other refusal path as it
  is. Verify: the new case passes, and `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` and
  `run#aRefusedStartKeepsTheLeftoversItFound` still stop as before (`evidence/focused-run-cases.log`, `all 90 cases
  passed` over the cases `tools/loop/run.sh` declares).

## 3. The invariant, one mutation per check

- [x] Add record `13.10` to `tools/loop-mutations.sh` (the retry never happens → the new case goes red) and re-point
  `13.5`, `13.8`, `13.9`, `53.1`, `53.3`, `62.6` at the re-indented lines without changing what they assert. Verify:
  `bash tools/loop-mutations.sh --check --files tools/loop/run.sh` reports `all 72 cases passed`, and
  `--only "13.5 13.8 13.9 13.10 53.1 53.3 62.6"` reports `red-when-mutated: 7 · not-noticed: 0 ·
  restore-failures: 0` (`evidence/focused-mutations.log`).

## 4. The guideline that owns the refusal

- [x] `guidelines/Backlog.md`, "The loop", paragraph *What it does with a start the worktree refuses*: state the
  exception (the run's own ended child, patch in place, discarded and retried once) and keep the rule for the dirt
  of another item. Verify: `tools/check-doc-routes.sh` and `tools/check-portability.sh` pass — the paragraph names no
  project path and no new section.

## 5. The gate, once

- [x] Run `./tools/loop/gate.sh` from the change root and copy every XML it writes into `evidence/`. Verify:
  `LOOP-GATE: tests=… failures=0 errors=0`, and `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .`
  resolves every pointer this change quotes. The first run came back `errors=1` in 0 s on
  `tools/loop-mutations-selftest.sh` — a line splice had dropped the harness's executable bit, so the step never
  started (`evidence/gate-round1-red-mode-bit.log`); the mode was restored, the step ran green on its own
  (`all 34 cases passed`), and the second and last gate run reports `tests=603 failures=0 errors=0`
  (`evidence/gate-round2.log`).

## 6. Archive

- [x] Confirm every scenario names its case (2 scenarios in the added requirement, 3 repeated in the modified one);
  archive the change and check the delta merged into `openspec/specs/agent-issue-loop/spec.md`.

## 7. Round 2 — the two findings of the first review round

- [x] The retried start's own refusal is the one recorded (`run.sh:1286`–`1291`): dirt keeps the attribution,
  anything else stops the run with the retry's first line. Verify: `run#aRetriedStartStopsWithItsOwnReason` passes and
  `13.11` reddens it — `evidence/focused-mutations-round2.log`, `RED 13.11 run#aRetriedStartStopsWithItsOwnReason`,
  `red-when-mutated: 9 · not-noticed: 0 · restore-failures: 0` over the nine records the arm and its neighbours own.
- [x] The attribution does not write an item's patch again when this run already wrote it (`run.sh:1299`). Verify: the
  case's patch assertion (`half-applied.txt` still there) and the shipped `run#aRefusedStartKeepsTheLeftoversItFound`.
- [x] One derivation of the patch path (`leftover_patch_of`) for its three readers, and the repair's prefix as a
  parameter. Verify: `run#resetWorktreeDiscardsWhatItNames`, `run#resetWorktreeNamesTheDirtItCannotDiscard`,
  `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard` pass with their records `13.5`, `13.8`, `13.9`.
- [x] A scenario for the retry's own reason, in both the delta and the shipped spec, and the requirement's body
  restated within its 500-character limit.
- [x] The gate again, once, on the round-2 tree — `evidence/gate-final.log`: `LOOP-GATE: tests=605 failures=0
  errors=0`, 23 suites, exit 0, run from the change root on the corrected tree (the round-2 run's own tallies
  are kept beside it in `evidence/round-2/`, so this run did not overwrite them).
