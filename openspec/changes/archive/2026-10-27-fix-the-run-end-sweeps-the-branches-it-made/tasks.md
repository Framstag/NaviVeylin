# Tasks — fix-the-run-end-sweeps-the-branches-it-made

## 1. The red case (do first)

- [x] 1.1 Add `run#theEndSweepsTheLandedBranchesItMade` to `tools/loop-cases.sh`, its declaration and its dispatch
      entry: a landed branch, a branch carrying work, a landed branch whose change carries
      `review-failed-round-1.md`, and a landed branch a worktree holds; a read-only `prune-landed` and then one
      item run, after which the landed branch and the run's own `loop-*` branch must be gone while the other three
      stay and are named. Verify: the case is red on HEAD — `evidence/red-on-head.xml`
      (`tests=1 failures=1`, 2026-10-27T15:32:55Z) and `evidence/red-on-head.log`.

## 2. The sweep (`agent-issue-loop` — "The run's end sweeps the branches it made")

- [x] 2.1 `tools/loop/worktree.sh`: add `prune-landed [--apply]` with the four landed readings (ancestry, patch
      identity, identical touched-file content, archived change on the base) and the three refusals (a worktree
      holds it, it carries work, its change carries a reviewer's refusal). Verify:
      `run#theEndSweepsTheLandedBranchesItMade` passes after 2.2; `tools/loop-cases.sh`'s existing
      `clean#sweepsOnlyWhatIsInTheBase` and `report#landedBranchesAreNamedAsRemovable` stay green.
- [x] 2.2 `tools/loop/run.sh`: `end_run` calls `prune-landed --apply` **after** the report and **after** the worktree
      step, unless `dry_run` is set or the run ended before the pre-screen. The order is the sweep's own reason:
      `--clean-worktree` releases the branch that tree held in the step above it, so a sweep run before that step
      would read the run's own `loop-*` branch as held and keep it. Verify:
      `run#theEndSweepsTheLandedBranchesItMade` passes and its output carries the removal and the kept
      reasons; `run#aMergedBranchIsRemoved`, `worktree#keepsByDefaultAndRemovesOnRequest` and
      `report#landedBranchesAreNamedAsRemovable` stay green.
- [x] 2.3 `tools/loop-cases.sh`: `run#theEndSweepsTheLandedBranchesItMade` also carries a landed branch a worktree
      holds and a read-only `prune-landed` before the run, so the case asserts every clause of the scenario — the
      checked-out refusal (`kept … — checked out`), the branch surviving the `--apply` run, and the read-only run
      removing nothing (`would remove …`). Verify: the case passes, and stays green in the gate.
- [x] 2.4 Revert-check for the sweep's destructive half: add mutation record `13.13` to
      `tools/loop-mutations.sh` (the refusal that keeps a branch the base does not carry) and run
      `bash tools/loop-mutations.sh --only 13.13 --out evidence/mutation-13.13.log`. Verify:
      `RED 13.13 run#theEndSweepsTheLandedBranchesItMade` — `the branch that carries work was removed`
      (2026-10-27T16:03:18Z) — `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, and
      `bash tools/loop-mutations.sh --check --only 13.13` reports every selected record applies.

## 3. The guideline (`guidelines/Backlog.md`, section "The loop")

- [x] 3.1 Rewrite the paragraph *A branch the run made is a leftover once its work is in the base* to say the run's
      own end sweeps the landed branches it did not merge, with the refusals and the summary line. Verify:
      `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh` and `tools/check-todo-references.sh` pass.

## 4. Evidence and archive

- [x] 4.1 Copy the red XML, the focused green XMLs, the mutation log and the measure block into
      `openspec/changes/archive/2026-10-27-fix-the-run-end-sweeps-the-branches-it-made/evidence/`, and keep a copy
      in `.pi/issue-loop/items/239/`. Round 2 adds `green-after-corrections.xml` (2026-10-27T16:23:28Z),
      `focused-cases-round2.log` (five cases, timestamped), `mutation-13.13.log`, the re-run `measure.txt` and the
      gate's round-2 tallies. Verify: `bash tools/loop/evidence-check.sh
      openspec/changes/archive/2026-10-27-fix-the-run-end-sweeps-the-branches-it-made --root .` reports no dangling
      pointer.
- [x] 4.2 Run the gate once (`./tools/loop/gate.sh`) and quote its tally — `LOOP-GATE: tests=645 failures=0
      errors=0`, `evidence/gate-run-2-summary.txt` and `evidence/gate-summary.xml`, 21 tallies in
      `evidence/gate-tallies/`. The change is archived at
      `openspec/changes/archive/2026-10-27-fix-the-run-end-sweeps-the-branches-it-made/` and its delta is merged into
      `openspec/specs/agent-issue-loop/spec.md`.
