# Tasks — the cap ends what the child left working in the tree

## 1. The red case

- [x] `tools/loop-cases.sh`: the case `run#detachedProcessInTheWorktreeIsEndedByTheCap` (registered in
  `LOOP_SHELL_CASES` and in the dispatch), which starts a stub child that leaves a writer at the worktree's root
  in a session of its own.
  Verify: `bash tools/loop-selftest.sh --case 'run#detachedProcessInTheWorktreeIsEndedByTheCap'` — red on HEAD,
  for the reason the case names; the tally copied into `evidence/red-HEAD.xml` (`tests="1" failures="1"`).

## 2. The fix

- [x] `tools/loop/run.sh`: in the cap branch of `run_child`, after the `LOOP-TIMEOUT` line, end every process
  still working in the worktree with `loop_end_pids_under` and append each one to the transcript as
  `LOOP-ENDED: …`.
  Verify: the case from task 1 goes green (`bash tools/loop-selftest.sh --case …`, `PASS`).

## 3. The invariant's own falsification

- [x] `tools/loop-mutations.sh`: record `44.1` removes the sweep and names the new case.
  Verify: `bash tools/loop-mutations.sh --only 44.1` — `RED` for `run#detachedProcessInTheWorktreeIsEndedByTheCap`,
  an exact restore, and the case green again; the transcript goes into `evidence/revert-check-44.1.log`.

## 4. The guideline

- [x] `guidelines/Backlog.md`, the section "7. The loop": the paragraph that claims "everything it started" gains the mechanism — the
  tree test, the transcript line — and the measurement that produced it (issue #272).
  Verify: `tools/check-doc-routes.sh` (the gate's own check for a `*.md` path), plus the structural read that the
  paragraph still sits under `## 7. The loop`.

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root — no compiled path changed, so the profile owes the two shell
  suites (`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`)
  and the two `openspec/*`/`*.md` checks (`gen-feature-list.sh --check-classification`, `check-doc-routes.sh`).
  Verify: the run's own `LOOP-GATE` line with the tallies copied into `evidence/`.

## 6. Evidence and archive

- [x] Copy every cited XML into `openspec/changes/fix-loop-cap-ends-a-detached-survivor/evidence/`.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-cap-ends-a-detached-survivor --root .`
- [x] `openspec validate fix-loop-cap-ends-a-detached-survivor --strict`, then archive the change and confirm the
  delta merged into `openspec/specs/agent-issue-loop/spec.md`.
