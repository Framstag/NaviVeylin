# Design — the run decides a previous attempt's branch by what it carries

## D1. The shapes the issue names

**(a) Merge the finished attempt and close the item.** When the branch carries a finished change — one archived
change of its own whose verification record is green — the run merges it onto the base and closes the item.

**(b) Reset or rewind the abandoned branch once its work is archived.** Rewind the branch to the base so the
item is eligible again and a fresh attempt re-does the work from the base.

**(c) Report the refusal as a named stop.** Leave the branch alone, but let the refusal stop the run as a named
outcome instead of counting against the blocked-pair budget.

## D2. The owner settled on shape (a)

The measured case is an item whose attempt already *finished*: its change is archived, its gate is green, and
its work only needs to land. Re-doing it (shape b) throws away a reviewed, archived change and pays for a second
attempt — the opposite of what the loop is for. Shape (c) leaves the finished work unmerged and the item open,
which is the defect itself with a better-sounding report. Shape (a) is the only one that moves the finished work
to where the loop's own closure path can finish it. The decision is settled; this change does not re-open it.

## D3. Why the root cause eliminates the alternatives

The root cause measured on 2026-10-18 (branches `fix/issue-114-…` and `fix/issue-115-…`, each carrying a
finished change, a green gate and an archived change on the same base) is that the run decides the branch by the
fact that it carries *any* commit the base does not have. So the alternative "re-do the work" (b) is not needed
by the evidence — the work is done and archived — and "stop instead" (c) does not remove the block, it only
re-labels it; the owner's decision names the same conclusion. The remaining alternative — "merge without
checking what the branch carries" — is eliminated by the measurement's own pairing: the blocked attempt's
branch carries a red-on-HEAD XML and no green gate, so "carries a finished change" is exactly "carries one
archived change whose verification record is green", and a merge that skipped that check would land an
unreviewed attempt. A list of viable options would mean the root cause was not established; here one fix
follows.

## D4. What "green" means, and why the two branches are distinguishable

A verification record is a JUnit testsuite XML in the archived change's `evidence/` directory that ran at least
one test and recorded zero failures and zero errors (`tests="[1-9]`, `failures="0"`, no `errors="[1-9]"`). A
finished attempt's evidence directory holds its green gate XML beside the red-on-HEAD XML that reproduced the
defect; a blocked attempt's holds only the red XML. The check is therefore "at least one XML records a passing
run", not "no XML records a failure" — the same rule `Build.md` §6 states for a red-on-HEAD pair. If the branch
adds no single archived change of its own (zero, or more than one — more than one root cause is not
loop-eligible), it is not adoptable either.

## D5. Where the decision lives, and the base it advances

`worktree.sh adopt-item` performs the merge in the project tree, where the base branch is checked out — the
pre-screen's adoption happens before the run's worktree exists, so the worktree is not required. `run.sh`
calls it from the two places a previous attempt is learned: the pre-screen's exclusion list
(`adopt_finished_exclusions`, before the exclusions are escalated) and the start's own refusal
(`carries commits the base does not`). A merge moves the base branch, so the base the run recorded advances to
the merge's commit (`state.sh set-base`) — without that, the next `start-item` would refuse the run's own merge
as a stale base and the merged change would leak into the next item's diff. The item is closed through the same
`labels.sh close` path a worker's item goes through, with the merge recorded in the run state and named in the
report, so the adoption is as auditable as a worked item.

## D6. Alternatives the root cause eliminated — summary

- *Rewind the abandoned branch once its work is archived* — throws away an archived, reviewed change and pays
  for a second attempt; the evidence shows the work is finished.
- *Report the refusal as a named stop instead of spending the blocked-pair budget* — leaves the finished work
  unmerged and the item open, so the block is re-labelled, not removed.
- *Merge without checking what the branch carries* — would land a blocked attempt; the measured pair shows the
  evidence distinguishes the two.
