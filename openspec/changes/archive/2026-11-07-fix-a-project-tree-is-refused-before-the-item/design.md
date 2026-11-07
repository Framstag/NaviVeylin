# Design

## Root cause

`run.sh`'s start-time pre-flight verifies the two trees the run owns and may repair — the project tree's **checkout**
(`tools/loop/run.sh:618`, `symbolic-ref`) and the **kept worktree**'s cleanliness (`:639`–`642`, `worktree list
--porcelain` plus `status --porcelain` inside the worktree). It does not read the project tree's own
`git status --porcelain`, which is the state the merge depends on: both merges that land an item refuse it
(`tools/loop/worktree.sh:508` in `merge-item`, `:548` in `adopt-item`). The one reader of that fact therefore runs at
the end of the item's life rather than the start of the run's, and the item is recorded `unmergeable` with its budget
already spent (#668, #675, #769, #782 — the four `stopReason: unmergeable` records in `.pi/issue-loop/runs/*.json`,
every one of them naming the same refusal).

## The single fix

One guard, beside the checkout guard it belongs with, reading the project tree the landing step merges into:

```sh
project_dirt=$(git -C "$LOOP_ROOT" status --porcelain 2>/dev/null)
if [ -n "$project_dirt" ]; then
  … print "refusing to start: …", the paths indented, the sentence about what the run cannot tell, the command that
    lists them …
  stop_run "the project tree carries uncommitted work the landing step would merge over: <paths> — the run cannot tell
    its own leftover from an edit made while it was not running, so nothing is discarded; commit or discard them and
    re-run"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
fi
```

The inputs are ones the run already has (its own root) and the reading is `git status --porcelain`'s default — tracked
modifications, staged changes and untracked paths git does not ignore (`openspec/changes/*/` is ignored by design, so
an unarchived change is not dirt; `new_project`'s fixture ignores `.pi/`, so the state the run itself writes is not
dirt either). The refusal prints the paths one per line, and puts the same lines into the reason, because a refusal is
the run's own record and the console scrolls away (#787's rule, which the decision cap already follows).

Where it goes is the whole design: after the checkout guard (a tree on the wrong branch cannot land anything at all,
so that refusal is the more fundamental one) and after the module-pin arm, which reads the same tree's status for a
reason of its own — a base whose gitlink names a commit no clone holds puts the module's path in that status, and that
refusal names a repair (`push origin`) this one does not, so the specific arm reads the fact first and this guard sees
what is left. It sits before every read the run pays for afterwards: the decision cap's two `list.sh` reads and the
pre-screen. `adopt-item` runs from the pre-screen's own pass (`adopt_finished_exclusions`), so "before the pre-screen"
is also "before any adoption is attempted".

## What the evidence eliminated

- **Repair the tree instead of refusing.** The kept worktree's dirt has a repair (`--reset-worktree`,
  `discard_kept_worktree` at `run.sh:698`) because the run wrote it. A project tree's dirt may be its owner's edit made
  while no run was live, and no reading available to the run separates the two: the run's own leftovers are already
  recorded under `.pi/issue-loop/items/<n>/leftover-from-killed-run.patch`, which is gitignored and therefore
  invisible to `git status` (#664, #308). A repair here would discard a person's work to save a minute.
- **Refuse at the landing step only, and record it better.** That is the tree today, and it is what the four records
  measure: the refusal is correct and arrives after the budget. Nothing about the landing step's refusal changes.
- **Fold it into the checkout guard.** One `if` would read two different facts and could name only one of them in its
  reason; the checkout case already asserts a reason naming a branch, and a dirty tree is not a branch. Two guards, one
  message each.
- **Ask the pre-screen to exclude the item when the tree is dirty.** The pre-screen builds a queue for the tracker and
  spends a per-item classification; the tree's state is not a fact about any item, and a run that queued items it
  cannot land is the defect, not the repair.

## Blast radius

- **What the run does.** A run opened on a dirty project tree now stops at exit 5 with `run.stop.reason` set, the
  report printed, and no worktree created. A clean tree is unchanged: the guard is one `git status` on a directory the
  run already knows, and `new_project`'s fixture is clean at that point (`.pi/`, `local.properties`, `build/` and
  `openspec/changes/*/` are ignored), so the 344 existing cases keep the path they had.
- **Who reads the record.** `report.sh` prints `.run.stop.reason` and nothing else changed; `state.sh` is untouched.
- **What it does not reach.** The kept worktree's refusal and its repair, the module-pin repair, the landing step's own
  refusals and the adoption's `unmergeable` record.
- **Files.** `tools/loop/run.sh`, `tools/loop-cases.sh` (one case, its list entry and its dispatch entry, and four
  cases the new guard reaches: `run#doesNotCloseOnAMergeTheBaseCannotTake` and
  `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable` now dirty the tree *while the run is live* — by the item's own
  child and by the case's tracker stub — because a tree dirty before the run is now refused before the item;
  `run#awaitingDecisionIsNotEscalatedAgain` and `prescreen#aHoldWithNoDecisionIsNotADecision` keep their stub's request
  log in the case's scratch instead of the project tree, which the guard would read as dirt the case left itself),
  `tools/loop-mutations.sh` (90.1), the change's spec delta (one modified requirement, one added scenario) and
  `guidelines/Backlog.recipes.md` §7d. No compiled file, no Gradle path, no fixture.
- **Cost.** One `git status` per run start (the guard reads it once and reuses the value), and one mutation record that
  runs one case twice in the loop's own mutation harness.

## Rollback

`git revert` of the one commit: the pre-flight returns to the checkout guard alone, the case's first assertion fails
with `expected '5', got '0'` as it did on `ec6a84a`, mutation 90.1 no longer applies to any line and
`tools/loop-mutations.sh --check` names it, and §7d's paragraph returns to "the start's own guard covers only the tree
a run is *opened* on". Nothing is written outside the tree — no state file shape, no tracker field, no worktree
content — so a revert needs no repair step.

## Open decisions

None. The item records no decision and one change follows: refuse at the start, discard nothing, name the paths.
