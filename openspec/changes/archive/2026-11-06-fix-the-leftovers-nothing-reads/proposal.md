# The things the run set aside are read, and named, by the commands that remove leftovers

## Why

The run sets two things aside and reads neither:

- **A blocked attempt's branch.** `worktree.sh start-item` renames an `unfinished` branch aside —
  `loop/leftover/issue-<n>-<slug>-<utc>` (`tools/loop/worktree.sh:298`) — and starts the item on a fresh branch,
  which is right: a commit nothing has reviewed is not a commit to throw away. But the candidate set both removal
  paths enumerate is `git branch --list 'fix/issue-*' 'loop-*'` (`tools/loop/lib.sh:407`, `loop_own_branches`), read
  by `prune-landed` (`tools/loop/worktree.sh:770`) and by `loop-clean.sh` (`tools/loop/loop-clean.sh:212`), and the
  set-aside namespace is in neither pattern. So a set-aside branch is not *kept* by the rule that protects work —
  it is never named at all, and one whose work landed by hand stays for ever. Measured on this tree, 2026-11-06:
  `git branch --list 'loop/leftover/*' | wc -l` → **8** branches, carrying one to three commits each
  (`git cherry main <branch>`, one per branch), and `loop-clean.sh` names none of them.
- **A worktree no run keeps.** The run keeps one tree, resolved to `$LOOP_ROOT/.pi/worktrees/loop`
  (`tools/loop/lib.sh:130`), and that is the only tree any path knows: `loop-clean.sh` removes its recorded build
  paths and the scratch trees of two roots, and nothing enumerates the trees beside it. Measured on this tree the
  same day: `.pi/worktrees/` holds **10** directories — the run's `loop` and nine more, four of which git still
  registers as worktrees (each holding a `.git` file: `779`, `780`, `788`, `789`) and five of which are plain
  directories. The report says nothing about any of them.

**Root cause, in one sentence:** every removal path derives its candidates from the names the loop is still writing
to — the two branch patterns a run makes and the one worktree the configuration names — so the artefacts a run *set
aside* carry no name any sweep reads, and the reading that would protect or release them (patch identity for a
branch, what a tree holds for a worktree) is never asked about them.

**The red cases, on the base `4522a41`** (the engine change the only thing reverted; `evidence/red-on-HEAD.xml`
carries `tests=2 failures=2` and `evidence/red-on-HEAD-worktree.xml` `tests=1 failures=1`, because the shell runner
writes no JUnit XML and each log is kept beside its tally):

- `worktree#aSetAsideBranchWhosePatchesLandedIsRemoved`, 2026-11-06T17:53:09Z–17:53:12Z
  (`evidence/red-on-HEAD.log`): a set-aside branch whose patch the base carries answers
  `would remove fix/issue-21-demo (its patches are in the base)` and never names
  `loop/leftover/issue-21-demo-…`.
- `worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved`, the same run: the sweep answers
  `worktree: no branch of the loop to prune` — a set-aside branch carrying the attempt's only copy is not named
  either.
- `clean#reportsAWorktreeNoRunKeeps`, 2026-11-06T17:53:16Z–17:53:18Z
  (`evidence/red-on-HEAD-worktree.log`): `.pi/worktrees/767` is not found in the report's output, which ends
  `loop-clean: nothing to remove — 1 path(s) kept`.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "A branch the loop made is removed when its work
is in the base" (`:1700`) and "One command reports and removes what the loop left" (`:1022`) — the two requirements
whose statements name what a removal path may remove, and the ones this delta **modifies** rather than adding a
third.

**Owning guideline section:** `guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing"
(`:706`, the sweep's candidate set and its four readings) and the section "Leftovers, the report and the queue"
(`:755`, what the report scans), the two sections the missing names belong to.

surface: 3 cases added, 0 rewritten, 2 tools changed (`lib.sh`, `loop-clean.sh`), 1 mutation record, 2 guideline
paragraphs, 2 requirements modified — counted with `bash tools/loop-selftest.sh --list | wc -l` (336 cases),
`tools/look.sh '^#### Scenario' --path openspec/specs/agent-issue-loop/spec.md` (333 scenarios in the shipped
capability), `tools/look.sh 'loop_own_branches|loop_worktree_branches' --path tools/` (7 matches in 3 files, of
which 2 read the branch set) and `tools/look.sh 'loop/leftover'` (21 matches in 7 files, 6 of them cases).

The change's own measurement block (`./tools/loop/measure.sh --change fix-the-leftovers-nothing-reads`,
2026-11-06T17:58Z, `evidence/measure.txt`):

```
LOOP-MEASURE-PROFILE /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-MEASURE base=6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db paths=8 insertions=290 deletions=41
LOOP-MEASURE-PATH guidelines/Backlog.recipes.md
LOOP-MEASURE-PATH tools/loop-cases.sh
LOOP-MEASURE-PATH tools/loop/lib.sh
LOOP-MEASURE-PATH tools/loop/loop-clean.sh
LOOP-MEASURE-PATH tools/loop/loop.conf
LOOP-MEASURE-PATH tools/loop-mutations.sh
LOOP-MEASURE-PATH tools/loop/run.sh
LOOP-MEASURE-PATH tools/loop/state.sh
LOOP-MEASURE-CHECK tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach
LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db
LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
LOOP-MEASURE-CHECK tools/loop-mutations-selftest.sh
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-portability.sh
LOOP-MEASURE-CHECK tools/check-portability-selftest.sh
LOOP-MEASURE-CHECK tools/mutate-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/portability-mutations.json --list
LOOP-MEASURE-CHECK tools/issue-tooling-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/status-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/create-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/close-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/duplicates-mutations.json --list
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/decision-question-mutations.json --list
LOOP-MEASURE-SUITE cases=336 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE fix-the-leftovers-nothing-reads scenarios=13 cases=13 unnamed=0
LOOP-MEASURE-SURFACE surface: 3 cases added, 0 rewritten, 2 tools changed (`lib.sh`, `loop-clean.sh`), 1 mutation record, 2 guideline
LOOP-MEASURE-END
```

The pathspec above is the diff against the last archived change's base, so it carries the four paths that change
touched as well as this one's; the block is quoted as it printed rather than picked over.

## What Changes

1. **`tools/loop/lib.sh`** — `loop_own_branches` enumerates the set-aside namespace beside the two it already names,
   in a block of its own, so both removal paths ask about a set-aside branch what they ask about every other branch:
   patch identity (`git cherry`), never ancestry. The remote arm's two patterns stay two: a blocked attempt's branch
   is renamed locally and never published.
2. **`tools/loop/loop-clean.sh`** — one arm, "the worktrees under the directory the run's own worktree lives in,
   that the run does not keep": it walks the worktrees git registers, skips the tree the run keeps (and the tree it
   was pointed at), and judges each of the rest by what it holds — uncommitted changes or a HEAD the base does not
   carry keep it and are named as the reason; anything else is named with its size and removed only with `--apply`.
   It runs after the branch arm, so a branch a tree nobody keeps holds is judged by the arm above before the tree is
   released.
3. **`tools/loop-cases.sh`** — the three cases above.
4. **`tools/loop-mutations.sh`** — record `88.1`, which drops the set-aside pattern from `loop_own_branches` again.
5. **`guidelines/Backlog.recipes.md`** — the section "Refusals, records and the landing": the sweep's candidate set
   now names the set-aside namespace and says which reading decides it; the section "Leftovers, the report and the
   queue": the report's arms gain the worktrees it judges by what they hold.

## What this does not do

- **It does not delete an unlanded commit.** A set-aside branch whose patches the base does not carry is kept and
  named, which is what the item's rule protects, and no disposition record is invented to release it: the branch's
  own name is the record — issue, slug and the UTC the run set it aside — and what was missing is a reader, not a
  second file. The item's "superseded" half would only ever delete a commit nothing has reviewed, which its own
  evidence says is right to protect.
- **It does not remove a directory git does not know as a worktree.** Five of the nine neighbours under
  `.pi/worktrees/` are plain directories (`767`, `783`, `issue131`, `issue19`, `openspec`), not registered trees, and
  reading them would need the four refusals `loop_scratch_remove` already owns for a different job. They stay
  untouched; the four registered per-item trees are what this arm names and `--apply` can remove.
- **It does not change the run's end sweep's other refusals** — a checked-out branch, a reviewer's refusal, an open
  pull request, an unreadable forge — and it does not publish or delete anything on a remote.
