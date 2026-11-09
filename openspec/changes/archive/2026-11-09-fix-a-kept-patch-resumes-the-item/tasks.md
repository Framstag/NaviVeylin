# Tasks

Reference: `proposal.md` for the scope and the red run, `design.md` for why and for the alternatives the decision
eliminated, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The two owed cases

Requirement: "A start resumes the work a bound-ended child left", scenarios "The work a bound-ended child left is
applied before the worker starts" and "A kept patch the base does not take is recorded, not discarded".

- [x] 1.1 Add `run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart` to `tools/loop/tests/loop-cases.sh`: a first run whose
      child the 2 s cap ends leaves the patch; the second run (with the `--reset-worktree` repair the refusal names)
      starts the item and its worker writes the tree it was handed — the case asserts the untracked and the tracked half
      of the patch are in it, that the branch is the item's, that its `HEAD` is still the base commit, and that the
      item's record names the resume.
- [x] 1.2 Add `run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase` beside it — a kept patch whose second
      half no tree of this base can take, with a first half a plain apply would have created — and assert the worker
      starts on the base tree (so the 3-way's staging was undone), on the base commit, with the non-apply in the item's
      note and in the report.
- [x] 1.3 Both names are declared in `LOOP_SHELL_CASES` and dispatched in `loop-cases.sh`'s table, and both carry a
      `# files:` declaration naming the engine paths they reach (`tools/loop/{lib,run,state,worktree}.sh`).
- [x] 1.4 The red run on the base, before the fix: `--case` each of the two on a detached worktree at `0fc36948`
      carrying only the new case file — both `FAIL`, and for the right assertion (`evidence/red-on-HEAD.log`):
      `the worker starts on the tree the kept patch was applied to … 'half-applied.txt' not found`
      and `the item's note records the non-apply … 'does not apply to this base' not found`.
- [x] 1.5 The first check the delta owes, run before any suite: `tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` (`evidence/first-check.log`).

## 2. The apply, and the record of a non-apply

- [x] 2.1 `tools/loop/lib.sh`: `loop_leftover_patch_of` — the one derivation of `<item dir>/leftover-from-killed-run.patch`,
      which `run.sh`'s writer and the start's new reader now share.
- [x] 2.2 `tools/loop/worktree.sh`: `resume_from_leftover` applied from `cmd_start_item` after the branch is settled and
      before the marker is written — plain `git apply`, then `--3way`, then `git reset --hard` + `git clean -fd` and the
      non-apply recorded — and `record_resume`, which writes the outcome through `state.sh record-resume`.
- [x] 2.3 `tools/loop/state.sh`: `record-resume <issue> --patch <path> --outcome applied|no-apply`, writing
      `.resumes[<issue>]`, and `done` folding that sentence into the item's first note.
- [x] 2.4 `tools/loop/run.sh`: `leftover_patch_of` removed in favour of the library function (three call sites).
- [x] 2.5 The three cases the owner requires to stay exactly as they are, run on the change: `run#treeOfAChildTheCapEndedIsNotCommitted`,
      `run#treeOfACapEndedChildIsKeptAsAPatch` and `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` all `PASS`
      (`evidence/green-on-change.log`), beside the four start paths the new step reaches
      (`run#aRefusedStartKeepsTheLeftoversItFound`, `run#theStartAfterTheRunsOwnEndedChildIsNotRefused`,
      `run#aRetriedStartStopsWithItsOwnReason`, `run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin`).

## 3. The falsification (one mutation per invariant)

- [x] 3.1 `266.1` — the start reading the item's patch → `RED run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart`,
      `restore … green again` (`evidence/mutation-266.log`).
- [x] 3.2 `266.2` — the record of a non-applying patch → `RED …theItemStartsFromTheBase` on the note assertion, restore
      green.
- [x] 3.3 `266.3` — the reading in `done` that puts the resume into the note → `RED` on the same assertion, restore green.
- [x] 3.4 `tools/loop/tests/loop-mutations.sh --check` over the whole set, because a record is a quote and this change
      edited the files `92.x`, `97.x` and the rest quote (`evidence/mutation-check.log`).

## 4. The gate

- [x] 4.1 `tools/loop/gate.sh --base main` on the change, in bounded steps, with `LOOP-GATE-TREE` and `LOOP-GATE` quoted
      verbatim in the archive (`evidence/gate.txt`).
- [x] 4.2 The whole loop suite standalone, timed before the gate was believed about it:
      `tools/loop/tests/loop-selftest.sh --from-git main` (`evidence/suite.log`).
