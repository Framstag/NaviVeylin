# A finished attempt's branch is merged and its item closed

## Why

The loop's previous-attempt path only ever *refuses*. A branch that carries a commit the base does not have is
recorded as a decision and escalated, whether that branch holds a **blocked** attempt or a **finished** one —
an archived change whose verification came back green. A finished attempt is therefore refused exactly like a
blocked one: the run records the exclusion, spends nothing, and the item stays open with its finished work
unmerged. Because two such refusals end a run (the blocked-pair budget), a base whose previous run left two
finished attempts makes every later run stop at the pre-screen with "nothing processed" — the finished work is
never merged and the item is never closed, and only a person can recover it.

Measured, root cause, in one line: **the run decides a previous attempt's branch by the fact that it carries a
commit the base does not, never by what that commit carries — so a finished attempt is treated as a blocked
one and neither merged nor closed.**

Evidence:

- Issue `#176`, measured 2026-10-18: two branches, `fix/issue-114-…` and `fix/issue-115-…`, each carried a
  finished change, a green gate run and an archived change on the same base; the pre-screen excluded both as
  `needs-decision`, the run reported "nothing processed", and the owner had to merge both by hand. The two
  branches no longer exist in this tree (they were merged or removed after the measurement), so the run's own
  recorded outcome is the reproduction.
- `evidence/red-on-head.log` — with the mechanism restored to `HEAD 8ecaa81`, both named cases fail on the
  stashed HEAD engine: `run#mergesAFinishedAttemptAndClosesTheItem` fails because the run never names the
  branch it merged (`adopted: branch fix/issue-21-previous-attempt` not found), and
  `run#doesNotMergeAnAttemptWithoutAGreenVerification` fails because the refusal never names what it looked for
  (`carries no green verification record` not found); both exit 1.
- `evidence/green-on-fix.log` — the same two cases pass against the mechanism in this change, exit 0.

The contradicting requirement is the one this change modifies: **"Version control belongs to the run's script,
not to the agent"** — its scenario "A branch that carries a previous attempt is refused, named and recorded"
states that a branch carrying a commit the base does not have SHALL be refused and recorded as awaiting a
decision, with no case for a branch whose attempt already finished. The run cannot both refuse a finished
attempt and merge it, so the requirement has to say which branch is which.

## What changes

The run decides a previous attempt's branch by **what the branch carries**, in one place
(`worktree.sh adopt-item`, called by `run.sh` from both the pre-screen path and the start-refusal path):

- a branch that carries exactly one archived change of its own **and** a green verification record inside that
  change's evidence is **adopted**: its commits are merged onto the base (fast-forward when the base is an
  ancestor, cherry-pick otherwise), the base the run recorded advances to the merge, and the item is closed
  through the same closure path a worker's item goes through — with the branch, its commits and the evidence it
  was checked for recorded in the run state and named in the report;
- a branch that does not carry that evidence is **not touched**: its refusal and its escalation stay exactly as
  they were, and the run still records the decision.

Out of scope: the definition of "green" beyond "a JUnit testsuite XML in the change's evidence directory with
at least one test and no failures" (a blocked attempt's evidence directory holds only its red-on-HEAD XML, so
the two are distinguishable); anything about publishing or opening a change request; the pre-screen's own
`needs-decision` verdict for a branch the adoption leaves untouched.
