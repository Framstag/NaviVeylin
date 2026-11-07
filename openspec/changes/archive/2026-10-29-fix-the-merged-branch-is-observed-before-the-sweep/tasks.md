# Tasks — fix-the-merged-branch-is-observed-before-the-sweep

## 1. The observation, in the case the record names

- [x] 1.1 `tools/loop-cases.sh`, `case_run_aMergedBranchIsRemoved`: the run's output is kept instead of discarded
      (`out=$(run_one_item …)`), the last `branches:` line the run's own report printed is read out of it, and the
      case fails when that line is missing or names a `fix/issue-*` branch. The case keeps its name, its `# files:`
      declaration (now also `tools/loop/report.sh`, whose line it reads), its worktree assertion and its
      base-content assertion. *Verified*: `bash tools/loop-selftest.sh --case run#aMergedBranchIsRemoved` →
      `PASS run#aMergedBranchIsRemoved`, exit 0 on the tree without a mutation (`evidence/case-green.log`).
- [x] 1.2 The assertion reads the point in the run the item is about: the release's deletion is observable there,
      the sweep's is not. *Verified*: on the tree with `42.2` applied by hand the report line names the item's
      branch and the sweep removes it afterwards, while on the same tree without the mutation the line names only
      the run's own `loop-<timestamp>` branch (`evidence/probe-42.2-run-output.log`).

## 2. The record is noticed (the red test this item owes)

- [x] 2.1 On HEAD, before 1.1, `bash tools/loop-mutations.sh --only '42.2'` printed
      `GREEN  42.2  run#aMergedBranchIsRemoved   <-- the case did not notice the mutation` and
      `red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0`, exit 1 (`evidence/red-on-head.log`, quoted in
      the proposal). Taken once; not re-run.
- [x] 2.2 With 1.1 applied the same record goes red for the right reason. *Verified*:
      `bash tools/loop-mutations.sh --only '42.1 42.2'` → both `RED`, each followed by `restore … green again`,
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, exit 0 (`evidence/revert-check.log`); `42.2`'s
      failure message is the new assertion's own sentence, and `42.1`'s is still the worktree line, so each record
      still has its own adjacent assertion.
- [x] 2.3 The record itself is not edited, so its expressions still apply. *Verified*: `git diff --stat` lists no
      `tools/loop-mutations.sh`, and the gate's `bash tools/loop-mutations.sh --check` step reports
      `tests=202 failures=0` (`evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml`).

## 3. The delta is falsifiable against the spec

- [x] 3.1 `specs/agent-issue-loop/spec.md` — one added scenario under "A branch the loop made is removed when its
      work is in the base", naming `run#aMergedBranchIsRemoved`; the requirement's own text is carried unchanged.
      *Verified*: `openspec validate fix-the-merged-branch-is-observed-before-the-sweep --strict` →
      `Change '…' is valid`, and the named case exists and ran in the gate's suite.

## 4. The gate, once

- [x] 4.1 `./tools/loop/gate.sh`, run once from the change root; its `LOOP-GATE:` line quoted into
      `evidence/evidence.md` and the full transcript kept as `evidence/gate.log` with every per-suite tally in
      `evidence/gate-tallies/`. No compiled file is touched, so the map selected the shell suites and the
      documentation checks; the shell suite ran all 277 cases green (`tests=277 failures=0`) and the gate ended
      `tests=676 failures=0 errors=0`. Not run a second time.

## 5. Evidence and archive

- [x] 5.1 Every cited log and tally copied into `<change>/evidence/` and into
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/698/`; `evidence/measure.txt` holds the one measure
      block of this change's own delta.
- [x] 5.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-merged-branch-is-observed-before-the-sweep
      --root .` reports no dangling pointer, before and after the archive.
- [x] 5.3 Archived; the directory exists under `openspec/changes/archive/` and the delta merged into
      `openspec/specs/agent-issue-loop/spec.md` with its new scenario.
- [x] 5.4 Left the tree as the change leaves it: no commit, no push, no version control, no tracker write.
