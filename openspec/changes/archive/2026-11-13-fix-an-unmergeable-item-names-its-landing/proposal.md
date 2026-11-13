# Proposal — an item a merge refused names its landing

## Why

**Root cause.** `report.sh` has no arm for an item the run stopped on `stopReason: unmergeable`. The item reaches the
action list through one arm only — the decision list (`tools/loop/report.sh:307`, base `647b289a`), which prints
`decide #<n> — the run escalated it (blocked)` and the item's own note beside it — and the branch arm beside it
(`report.sh:679`) prints a branch with what it carries and no command. So the one thing a caller needs for a refused
landing — the branch, its count, the command that lands it, and, when the refusal was a dirty tree, the paths that
refused it — is printed nowhere, although every part of it is already in the record or in git.

**Evidence, verified against the tree (2026-11-13, issue #863).** The run of 2026-11-11T20:36:10Z blocked #859 on
exactly this mechanism: `.pi/issue-loop/runs/2026-11-11T20:36:10Z.json` records `.items[] | select(.issue == 859)`
as `result: blocked`, `stopReason: unmergeable`, with a green gate
(`metrics.gate: tests=245 failures=0 errors=0`; that gate's XML is not retained — the run record carries the
pair), `metrics.reviewVerdict: PASS`, and a note whose refusal names the
branch `fix/issue-859-the-answer-s-shape-has-a-second-home-and` and three paths of dirt — while `merged.branch` is
`""` and `merged.commits` is `""`, because the arm that records a landing is the one the refusal stopped short of.
The item reports that branch as carrying five commits; this state does not carry the figure, so it is the item's,
not this change's (the count this change prints is the branch table's, and that branch has since been swept out of
this tree — `evidence/action-list-after.log` is the same record read on this change's tree).
The report of that state, read on this base and quoted verbatim in `evidence/action-list-before.log`, printed as its
first action:

```
  1. decide #859 — the run escalated it (blocked): the branch fix/issue-859-the-answer-s-shape-has-a-second-home-and
     could not be merged onto the base, so the item is not closed on a fix the tree does not have — loop: the project
     tree is not clean — refusing to merge … onto the base:\n M tools/loop/loop.conf\n?? docs/…md\n?? tools/bench/…
```

— a decision, and neither the branch's count, nor the command that lands it, nor the paths the refusal had just
listed. 9 840 445 tokens and 22 m 19 s of that item were payable only by a person who worked the command out by hand.

**The red test.** `report#anUnmergeableItemNamesItsLanding`, added to the suite by this change, fails on this base:

```
FAIL report#anUnmergeableItemNamesItsLanding
  and the one command that lands it: 'git -C …/repo merge --ff-only fix/issue-21-two-mechanisms' not found in:
  4. land fix/issue-21-two-mechanisms — 1 commit(s) the base does not have — item #21
```

`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml` — XML `tests=1 failures=1 errors=0` (2026-11-13T13:33:40Z),
run against the base engine with this change's case in it.

**Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, the scenario "The report names the actions the
caller owes" (`:138`) under the requirement "One command starts a budgeted run, with no dialogue" (`:11`): the action
list is read *instead of* the tracker, and "a branch without its owner" is named there as the
defect that sends the caller back to `gh`. The rule this delta owes is the same sentence one step on: the branch, its
count **and the command** — the item's spec delta adds that requirement.

**Owning guideline section.** `guidelines/Backlog.recipes.md`, section 7e "Leftovers, the report and the queue"
(`:1436`–`:1455`), which lists the action list's arms and closes with "`none` is printed only when all seven sources
were read and empty". The delta adds the eighth arm there, because a rule that is only in the report is a rule a
reader cannot check.

**The single fix.** A block in `report.sh` beside the branch arm: for every item whose `stopReason` is
`unmergeable`, one action line naming the branch the refusal wrote, what it carries (read from the branch table by
the same patch identity the other land lines use), the command `git -C <root> merge --ff-only <branch>`, and — when
the refusal listed paths — those paths and the `git -C <root> status --porcelain` that lists them now. The branch arm
below then skips that branch, so the landing is one line (#850's rule) while the item's `decide` line stays where the
run escalated it. No state field, no engine change, no new
command: the report is a reader and stays one.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — plus 1 mutation record (`863.1`), 1 report block and its
`none` sentence, and 1 paragraph in `guidelines/Backlog.recipes.md` section 7e — from `tools/look.sh 'actions the caller
owes' --files` (live files: `tools/loop/report.sh` 2, `tools/loop/tests/loop-cases.sh` 3,
`openspec/specs/agent-issue-loop/spec.md` 2).
