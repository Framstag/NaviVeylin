# Tasks — the run stops committing the tree of a child it ended

## 1. The red case

- [x] `tools/loop-cases.sh`: the case `run#treeOfAChildTheCapEndedIsNotCommitted` (registered in
  `LOOP_SHELL_CASES` and in the dispatch), a stub child that writes `half-applied.txt` into the change root and
  then blocks on a fifo, so the 2 s cap ends it with the file in the tree.
  Verify: `bash tools/loop-selftest.sh --case 'run#treeOfAChildTheCapEndedIsNotCommitted'` — red on HEAD, for the
  reason the case names; the transcript and the tally are `evidence/red-HEAD.log` and `evidence/red-HEAD.xml`
  (`tests="1" failures="1"`).

## 2. The fix

- [x] `tools/loop/run.sh`: read the child's status before the commit; when the run ended the child
  (`worker_exit` is `LOOP_CHILD_TIMEOUT` or the transcript carries `LOOP-TIMEOUT`), `end-item` is not called, and
  `head_now` is read either way.
  Verify: the case from task 1 goes green (`bash tools/loop-selftest.sh --case …`, `PASS`).

## 3. The invariant's own falsification

- [x] `tools/loop-mutations.sh`: record `45.1` restores the unconditional commit and names the new case.
  Verify: `bash tools/loop-mutations.sh --only 45.1` — `RED` for `run#treeOfAChildTheCapEndedIsNotCommitted`, an
  exact restore, and the case green again (`evidence/revert-check-45.1.log`).

## 4. The guideline

- [x] `guidelines/Backlog.md`, section "7. The loop": the cap's paragraph gains what the ended child's tree does not
  get, and the sentence about a run-ended child as a blocked item says it carries no commit.
  Verify: `tools/check-doc-routes.sh` (the gate's own check for a `*.md` path), plus the structural read that the
  paragraph still sits under `## 7. The loop`.

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root — no compiled file changed, so the profile owes the three shell
  suites (`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`) and
  the two documentation checks (`bash tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh`).
  The first run was red on a realignment this change owed (the record was declared outside `MUTATIONS=( … )`, and
  two checks derive the set from that array's text); it is kept as `evidence/gate-first-red.log` and attributed in
  `evidence/gate-attribution.md`.
  Verify: the run's own `LOOP-GATE` line with the tallies copied into `evidence/` (`evidence/gate.log`).

  Two corrections the item's second pass owed, both in `evidence/`. The attempt of 2026-10-23T09 ended at the run's
  cap while this gate was still running, so the `gate.log` it left held two lines and no verdict — the very defect
  this change is about, on this change; that file was replaced by the re-run quoted below. And `45.1` had to be
  re-placed inside `MUTATIONS=( … )` against the merged `main`, which had taken the same line region for `46.1`–
  `52.3`: the rebase resolved it there, and `bash tools/loop-mutations.sh --check` then reported `all 112 cases
  passed — every selected record applies to the file it names`.
  Verify: `evidence/gate.log` — `LOOP-GATE: tests=337 failures=0 errors=0 (tools/loop-selftest.sh 829s, bash
  tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 92s, tools/check-doc-routes.sh 0s)`, the
  self-test reporting `all 191 cases passed`.

## 6. Evidence and archive

- [x] Copy every cited XML into `openspec/changes/fix-loop-ended-child-tree-is-not-committed/evidence/`.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-ended-child-tree-is-not-committed --root .`
  — `evidence-check: PASS`. It first failed C2 on the quoted aggregate `tests=230 failures=6`, which no retained
  tally carries: the record now elides the pair inside the fence and says below it that the aggregate was not
  retained, beside the two per-suite tallies that are (`evidence/gate-attribution.md`).
- [x] `openspec validate fix-loop-ended-child-tree-is-not-committed --strict`, then archive the change and confirm
  the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
