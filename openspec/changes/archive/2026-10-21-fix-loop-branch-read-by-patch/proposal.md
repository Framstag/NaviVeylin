# fix-loop-branch-read-by-patch

## Why

Measured in the tree at base `2e732f0` (issue #228, `class/bug`, `category/tooling`): the loop reads a branch by
**object identity** — `git rev-list --count "$base..$branch"` in the report and `git merge-base --is-ancestor` in
the start — while the loop itself lands an item's work as a **new** commit (`fix: … (#N)`). A branch whose work
was landed that way, or re-landed, is therefore never an ancestor of the base, and its item is refused for ever.

**Root cause, in one sentence.** Both readers ask whether the base's *commit objects* contain the branch, when the
question they mean is whether the base's *patches* contain it, so a branch whose patch is already on the base is
read as a previous attempt that carries unlanded work.

The evidence, in the tree at HEAD:

- `tools/loop/report.sh:179` — `if ahead=$(git -C "$LOOP_ROOT" rev-list --count "$base..$branch" …)`: every branch
  with commits the base does not have is printed as `land <branch> — N commit(s) the base does not have`, and a
  branch whose patches all landed by hand contributes one of those lines for ever.
- `tools/loop/worktree.sh:144` — `if gitw merge-base --is-ancestor "$branch" "$base"; then` … `else loop_refuse`:
  the item's branch is refused with exit 5 and the item can only be started again by a person deleting the
  branch, and the tooling has no delete path by design (`grep -rn 'push --delete\|branch -D' tools/` is empty).
- `tools/loop/prescreen.sh:138` — the same ancestry question on the branch-name prefix, which turns the leftover
  into a `needs-decision` exclusion *before* the start ever asks, so the item spends one of the run's decision
  slots each run.

Measured on this host (probe `probe-landed-branch.sh`, `.pi/issue-loop/items/228/probe-landed-branch.txt`,
2026-10-21T05:52:12Z): in a throwaway repository, a branch whose patch was landed as `fix: the landed work (#21)`
reads `rev-list --count main..fix/issue-21-demo` → **1** and `merge-base --is-ancestor` → **not an ancestor**,
while `git cherry main fix/issue-21-demo` → **0 unlanded**. Two readers of the same branch disagree by exactly
the landing commit.

**The three cases this change adds fail on HEAD `2e732f0`** (a shell gate has no JUnit XML; the case's own
message and exit status are the record — `evidence/red/`, produced by `bash tools/loop-selftest.sh --case <name>`
against this change's cases file, 2026-10-21T05:50:29Z…05:50:35Z):

| case | red on HEAD |
|---|---|
| `worktree#reusesABranchWhoseWorkAlreadyLanded` | `a branch whose work the base already carries was refused: loop: branch fix/issue-21-demo already exists and carries commits the base does not — a previous attempt's work is on it:` (exit 1) |
| `prescreen#landedBranchIsNotADecision` | `a branch whose every patch the base carries is not a previous attempt: expected 'eligible', got 'needs-decision'` (exit 1) |
| `report#landedBranchIsNoAction` | `a branch whose patches are in the base is not an action: 'land fix/issue-21-landed' should not appear in: actions the caller owes: 1. land fix/issue-21-landed — 1 commit(s) the base does not have — item #21` (exit 1) |

The population the tracker measured (`git cherry` on the branches of one 2026-10-20 report) is no longer in this
repository: the six `fix/issue-*` branches it holds today all carry unlanded patches
(`1·1`, `3·3`, `4·4`, `4·4`, `5·5`, `5·5` — `probe-branch-identity.sh`,
`.pi/issue-loop/items/228/probe-branch-identity.txt`), because the branch of every landed item was deleted by
hand. What is measured here is therefore the mechanism and the three cases, not the population.

**The owning requirement and the owning rule.** Capability `agent-issue-loop` — the requirement "Version control
belongs to the run's script, not to the agent" (its scenarios "A branch that carries no work of its own is
reused" and "A branch that carries a previous attempt is refused, named and recorded") and the requirement "The
report names the actions the caller owes" (its scenario for a branch that carries commits). The behaviour this
contradicts is stated as *what a branch carries*; the tree implemented it as *which commit objects it holds*. The
guideline that owns the loop's prose is `guidelines/Backlog.md` §7 "The loop" (the report's named action list and
the branch "that carries commits the base does not have"), and the loop's own facts are
`.pi/skills/fix-loop/SKILL.md` §"The report a run prints" and `REFERENCE.md` §"The branch".

**Why it matters.** The refusal is permanent and only a person can clear it, so every landed-by-hand branch keeps
spending a decision slot of the run's own ceiling, and the report's action list makes a leftover look like work
to land — the run's product asking the caller for a decision it does not owe.

## What Changes

- `tools/loop/lib.sh` — one helper, `loop_unlanded_commits <repo> <base> <branch>`: the commits on a branch whose
  **patch** the base does not carry (`git cherry`), printed as `<sha> <subject>`; it returns 1 when git cannot
  answer, so no caller reads "git cannot say" as "nothing to land".
- `tools/loop/worktree.sh` (`cmd_start_item`) — reuses the item's branch when nothing on it is unlanded, so a
  branch whose work landed as a new commit is a leftover like the empty branch it already handled, and refuses
  with the unlanded commits named otherwise. The branch is reset onto the recorded base on reuse, exactly as
  before: what it carried is in the base by patch, so nothing is thrown away.
- `tools/loop/prescreen.sh` (`previous_attempt_branch_of`) — the same question on the branch-name prefix, so a
  leftover is not recorded as `needs-decision`; a branch git cannot compare is still read as carrying work,
  because the refusal is the safe side of that answer.
- `tools/loop/report.sh` — the action list counts what a branch carries by patch, and drops a branch with nothing
  unlanded. A range git cannot answer is still named rather than counted as zero.
- `tools/loop-cases.sh` — one case per surface (three, registered in `--list` and the dispatch map), plus the
  `landed_branch` fixture they share: a branch committed, then its patch cherry-picked onto `main` and amended to
  the loop's own landing message, so the landing commit is a different object with an identical patch.
- `tools/loop-mutations.sh` — three mutations (33.1–33.3) that restore the ancestry question at each site, and
  the two existing mutations on the start's clause (12.5, 12.6) and the pre-screen's (13.1) updated to the lines
  this change leaves behind.
- `guidelines/Backlog.md` §7, `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md` — the prose says what a branch is
  read by: patch identity, not ancestry, with the measurement.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: "Version control belongs to the run's script, not to the agent" — a branch's work is asked
  by patch identity, so a landed branch is reused rather than refused, and a refusal names the unlanded commits.
- `agent-issue-loop`: "The report names the actions the caller owes" — a branch is named as an action only while
  it carries a patch the base does not have.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — both requirements above are **MODIFIED** (their requirement text
  and the scenarios that ask about a branch's commits). No other requirement, scenario or case is touched: the
  adoption path ("A finished attempt's branch is merged and its item closed"), the review-failure refusal, the
  stale-base refusal and the empty-action-list rule keep their wording and their cases.

## Impact

| file | module / area |
|---|---|
| `tools/loop/lib.sh` | the shared helpers every loop script sources — one new helper |
| `tools/loop/worktree.sh` | `cmd_start_item`'s branch reuse/refusal only |
| `tools/loop/prescreen.sh` | `previous_attempt_branch_of` only |
| `tools/loop/report.sh` | the branch loop of the action list only |
| `tools/loop-cases.sh` | the loop's offline gate: three cases and one fixture helper |
| `tools/loop-mutations.sh` | three mutations for the revert-check, three updated |
| `guidelines/Backlog.md`, `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md` | the loop's own prose |

No Android module, resource, native source, JNI bridge, manifest, Gradle file, stylesheet or prompt skeleton is
touched, so the parity, native-build, configuration-portability and licence rules do not arise:
`loop_unlanded_commits` names no project value and the cases name no project fact, so
`tools/check-portability.sh` reads the same configuration as before. The change touches no compiled file, so no
Gradle gate applies: the gate for `tools/loop/**` is `tools/loop-selftest.sh`, offline.

## Additive or breaking, and rollback

**Additive to every input and output.** No flag, exit code, state field, label, report line, archive step or
branch name changes: a branch that carries unlanded work is refused exactly as before, and a branch with nothing
unlanded is reused where it used to be refused. The only behaviour that stops is the false refusal.

Rollback: revert this commit. The start's and the pre-screen's question go back to ancestry, the report counts
`rev-list` again, and the three cases fail again with the same three messages. Nothing on disk needs repairing: a
branch this change reused carries no work of its own, and a branch it stopped naming in the report still exists
for a person to delete.
