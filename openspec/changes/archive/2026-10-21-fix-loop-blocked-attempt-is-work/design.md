# Design — a branch is read by what it carries, and a blocked attempt is work

Base `2e732f0`. Issue #232, measured on #230. The requirements this answers are the delta in
`specs/agent-issue-loop/spec.md`; the gate is `tools/loop-selftest.sh`.

## D1 — A branch is credited with a change only when its own commit added it

`carried_change` decides adoption, so a false credit is a wrong verdict, not a cosmetic one. Three facts can
make a directory "added by the branch" without being the branch's work: a stale base that lost a change another
branch merged (measured: `2026-10-19-fix-i18n-gate-state-field-text` is on #230's branch and not on `main` after
#126's commit swept it in and a later change dropped it), a cherry-pick, and a rebase.

**Decision.** Credit a change only when the commit that added its directory names the item: the loop lands work
as `fix: <slug> (#<issue>)` and `fix: <slug> (#<issue>) — archive with its evidence` (`end-item`), so the subject
is the one fact both sides share. The adding commit is the *earliest* one (`--reverse | head -1`), because
later commits add evidence files into the same directory — taking the newest made the reviewer-refusal marker
itself the "adding" commit and refused a branch that carried no refusal.

**Rejected:** comparing the branch's commits against the base's patch-ids (`git cherry`) — that answers "did
this work land", a different question (#228), and it would still credit a directory both branches added.

## D2 — A branch with commits and no archived change of its own is work, not a question

#175 made every leftover branch a decision, on the premise that starting over throws the attempt away. The
premise is what this change breaks: the branch is renamed, not deleted, so nothing is thrown away — and the
decision it produced was unanswerable. Measured on #230: `needs-decision`, `status/on-hold`, one comment with no
question, and the item unworkable for good, because the loop has no branch-delete path by design.

**Decision.** Split the verdict by `branch_state_of`:

| what the branch is | what the run does |
|---|---|
| `adoptable` — one own archived change, green, no reviewer refusal | record `needs-decision`; the adoption path merges and closes it |
| `review-refused` — the same, refused by the reviewer | record `needs-decision` (#185: the owner's) |
| `archived-not-green` — the change is there, never verified | record `needs-decision` (#176: the owner's) |
| `unfinished` — commits, no archived change of its own | **queue the item as work**; `start-item` sets the branch aside |
| `landed` — every commit already in the base | queue the item; `start-item` reuses the branch |
| `absent` | queue the item; `start-item` creates the branch |

A state the command does not produce (a non-zero exit, an unreadable branch) is the owner's: the failure mode of
a misread is a discarded attempt, and keeping an attempt is the one thing the branch rule exists for.

## D3 — The branch is renamed, never deleted

Renaming to `loop/leftover/issue-<N>-<slug>-<utc>` keeps the commits reachable while taking the branch out of
`fix/issue-<N>-*`, which is the only prefix the pre-screen and the start read. So the next run sees a clean
item, the attempt is still in `git log --all`, and no path in `tools/loop/` deletes anything — the invariant
`grep -rn 'push --delete\|branch -D' tools/` states (#228).

**Cost, named.** Blocked attempts accumulate as `loop/leftover/*` refs. They are cheap (a ref plus the commits
they already had) and they are visible in `git branch --list 'loop/leftover/*'`, which is where a person
pruning them looks. Nothing prunes them automatically, on purpose: the commit is the attempt's only record.

## D4 — One reader of the branch, three callers

The pre-screen, the start and the adoption all ask "what is this branch", and the case for #230 exists because
they answered it differently. `branch_state_of` is the one implementation; `adopt-item` keeps its own detailed
refusals (it prints the change and the round), the pre-screen asks the read-only `branch-state` command, and
`start-item` asks the function. The command prints `<state> TAB <change> TAB <evidence>` so a caller reads one
field without parsing prose.
