# Tasks — fix-the-run-end-sweeps-the-branches-it-made

## 1. The red case (do first)

- [x] 1.1 Add `run#theEndSweepsTheLandedBranchesItMade` to `tools/loop-cases.sh`, its declaration and its dispatch
      entry: a landed branch, a branch carrying work, and a landed branch whose change carries
      `review-failed-round-1.md`; one item runs and the landed branch and the run's own `loop-*` branch must be gone
      while the other two stay. Verify: the case is red on HEAD — `evidence/red-on-head.xml`
      (`tests=1 failures=1`, 2026-10-27T15:32:55Z) and `evidence/red-on-head.log`.

## 2. The sweep (`agent-issue-loop` — "The run's end sweeps the branches it made")

- [x] 2.1 `tools/loop/worktree.sh`: add `prune-landed [--apply]` with the four landed readings (ancestry, patch
      identity, identical touched-file content, archived change on the base) and the three refusals (a worktree
      holds it, it carries work, its change carries a reviewer's refusal). Verify:
      `run#theEndSweepsTheLandedBranchesItMade` passes after 2.2; `tools/loop-cases.sh`'s existing
      `clean#sweepsOnlyWhatIsInTheBase` and `report#landedBranchesAreNamedAsRemovable` stay green.
- [x] 2.2 `tools/loop/run.sh`: `end_run` calls `prune-landed --apply` before `report.sh`, unless `dry_run` is set.
      Verify: `run#theEndSweepsTheLandedBranchesItMade` passes and its output carries the removal and the kept
      reasons; `run#aMergedBranchIsRemoved`, `worktree#keepsByDefaultAndRemovesOnRequest` and
      `report#landedBranchesAreNamedAsRemovable` stay green.

## 3. The guideline (`guidelines/Backlog.md`, section "The loop")

- [x] 3.1 Rewrite the paragraph *A branch the run made is a leftover once its work is in the base* to say the run's
      own end sweeps the landed branches it did not merge, with the refusals and the summary line. Verify:
      `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh` and `tools/check-todo-references.sh` pass.

## 4. Evidence and archive

- [x] 4.1 Copy the red XML and the focused green XML into
      `openspec/changes/fix-the-run-end-sweeps-the-branches-it-made/evidence/`, and keep a copy in
      `.pi/issue-loop/items/239/`. Verify: `bash tools/loop/evidence-check.sh
      openspec/changes/fix-the-run-end-sweeps-the-branches-it-made --root .` reports no dangling pointer.
- [x] 4.2 Run the gate once (`./tools/loop/gate.sh`) and quote its tally. Archive the change into
      `openspec/changes/archive/2026-10-27-fix-the-run-end-sweeps-the-branches-it-made/` and confirm the delta
      merged into `openspec/specs/agent-issue-loop/spec.md`.
