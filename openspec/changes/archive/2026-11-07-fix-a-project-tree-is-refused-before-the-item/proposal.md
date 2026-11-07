# A project tree that carries uncommitted work is refused before any item starts

## Why

Issue **#798**. A run's start-time pre-flight verifies the branch the project tree is checked out on
(`tools/loop/run.sh:618`) and whether the **kept worktree** is clean (`tools/loop/run.sh:639`–`642`). It never reads
the project's **own** tree for uncommitted work, and that state is read by exactly one thing: the landing step.
`tools/loop/worktree.sh:508` (`merge-item`) and `:548` (`adopt-item`) both refuse `git status --porcelain`'s output,
so the refusal arrives after the item's whole budget and its gate.

**Root cause, in one sentence:** the start-time pre-flight reads the two trees the run creates and may repair — the
checkout and the kept worktree — and not the third tree it merges into, so the only reader of the project tree's
uncommitted work is the merge, which runs after everything the run spent on that item.

**Verified in the tree today:** the project tree's dirt has two readers, both in the landing step —
`tools/look.sh 'project tree is not clean'` → `tools/loop/worktree.sh:508` and `:548` (the two refusals), the
recorded refusal a case replays (`tools/loop-cases.sh:1727`), and one guideline paragraph
(`guidelines/Backlog.recipes.md:723`). No arm of `run.sh`'s pre-flight reads it.

**Verified in this loop's archive today**, which is what the item's five recorded items come to when every record is
read rather than the first four lines of it:

```
$ jq -r '.items[]? | select((.stopReason // "") == "unmergeable") | "#\(.issue)"' .pi/issue-loop/runs/*.json | sort -u
#668
#675
#769
#782
```

Four items, and every one of the four notes names `loop: the project tree is not clean — refusing to merge …` with the
paths that tree carried: #668 left `.github/workflows/build.yml`, `guidelines/Build.md` and four untracked files;
#675 left `tools/loop/loop.conf`, `tools/loop/run.sh` and `tools/loop/state.sh`. The item's own account of #782 —
`guidelines/BuildEvidence.md` and `tools/loop/loop.conf` uncommitted, a run stopped by hand at 11:41 to clean the
tree, one dirty file costing two items and one run — is the same mechanism.

**The red case, on the change root's HEAD `fca2c857`** (one `bash tools/loop-selftest.sh --case
'run#aDirtyProjectTreeIsRefusedBeforeThePreScreen'` run before the engine change, at 2026-11-07T06:35:48Z):

```
run#aDirtyProjectTreeIsRefusedBeforeThePreScreen|   a run whose project tree carries uncommitted work is refused: expected '5', got '0'
--- run#aDirtyProjectTreeIsRefusedBeforeThePreScreen[0] (exit 1)
  a run whose project tree carries uncommitted work is refused: expected '5', got '0'
FAIL run#aDirtyProjectTreeIsRefusedBeforeThePreScreen
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run; its tally is `tests=1 failures=1 errors=0`, read off the runner's own
`1 of 1 cases failed` line — the shell runner writes no JUnit XML of its own, so the log is the record and no XML is
invented beside it. The case's first-line `# files:` declaration is accepted before that run:
`bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The base a run records is the base branch's
commit" (`:2316`), which already carries the run's other start-time refusal of the same tree — "A run whose project
tree is on any other branch, or on a detached HEAD, SHALL be refused before the pre-screen" (`:2320`) — and is
extended here with the cleanliness of that tree and its own scenario. The other two readers of the same fact are
unchanged: the landing step keeps its own refusal for the tree a run is *opened* on and a later writer dirties.

**Owning guideline section:** `guidelines/Backlog.recipes.md` §7d "Refusals, records and the landing", the paragraph
that owns what the start's guard covers (`:701`–`708`, "the start's own guard covers only the tree a run is *opened*
on, #301") and the paragraph that records an adoption the tree refused (`:719`–`733`).

surface: 1 case added, 4 rewritten (2 that dirtied the project tree before the run now do it while the run is live,
2 whose tracker stub left its log in the project tree now keep it in the case's scratch), 0 fixtures realigned, 1
engine site (`run.sh`'s pre-flight, after the module-pin arm), 1 requirement modified (its body and one added
scenario), 1 guideline section, 1 record of `tools/loop-mutations.sh` (90.1) — counted with `bash
tools/loop-selftest.sh --list | wc -l` (348 on the base, 349 on this change), `grep -c '^#### Scenario'
openspec/specs/agent-issue-loop/spec.md` (346), and `tools/look.sh 'project tree is not clean'` (7 matches in 6
files). The two rewritten `run#` cases were found by the `run#*` selection, not by the plan: a case that reached the
landing step by dirtying the tree *before* the run now reaches the pre-flight instead, and a case that reached
`adopt-item` that way reaches it by dirtying the tree while the run is live — which is the window the item names.

## What Changes

1. **The pre-flight reads the third tree.** `tools/loop/run.sh`, after the module-pin arm and before the decision cap:
   the project tree's `git status --porcelain` is read at the start, long before the pre-screen is paid for, and a
   non-empty answer refuses the run with exit 5.
2. **The refusal names what it found.** Every porcelain line is printed, each on its own indented line, and the same
   lines are the run's recorded stop reason — so the report `report.sh` prints from the state names them too.
3. **It says what it may not do.** The refusal states that the run cannot tell its own leftover from an edit its owner
   made while no run was live, and therefore discards nothing: `--reset-worktree` is the kept worktree's repair
   (`run.sh:698`, `discard_kept_worktree`) and it is not offered here.
4. **One case, one mutation, one paragraph.** `run#aDirtyProjectTreeIsRefusedBeforeThePreScreen` asserts the exit, the
   printed paths, the recorded reason, that the queue was never built, that no item was started, that no label was
   written (so no adoption was attempted and nothing was escalated) and that the file is still there;
   `tools/loop-mutations.sh` 90.1 mutates the guard to `if false`; §7d's paragraph says which tree the start's guard
   covers.

## What this does not do

- It does not change the landing step's own refusal: a tree a run is *opened* on and later dirties is still refused by
  `merge-item`/`adopt-item`, and the adoption path still records `unmergeable` (`state.sh adoption-refused`, #760).
- It does not repair anything and it adds no `--reset-project-tree`: the run may not decide that a person's edit is its
  own leftover.
- It does not read ignored paths (`openspec/changes/*/` is gitignored by design, so an unarchived change is not dirt),
  and it does not change the kept worktree's rule, its `--reset-worktree` repair, or the module-pin repair beside it.
- It does not touch the gate: the project's own `LOOP_GATE_CMD` writes into the project tree only through the tree a
  person's gate runs in, and this change adds no gate step.
