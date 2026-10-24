# fix-loop-unanswered-review-round-is-not-adopted

## Why

The loop's adopt path merges a branch whose last review round **never answered**, and closes the item on it.
Measured 2026-10-22: the change of issue #249 was archived by its round-2 worker and green-gated, the round-2
reviewer was killed mid-answer — `.pi/issue-loop/items/249/reviewer-report-2.md` is 0 bytes — and the run that
started at 19:34 read the branch as a finished attempt, merged it into `main` (`git reflog main`: `9f10749 … merge
fix/issue-249-…: Fast-forward`, 19:35:52) and closed the item. No reviewer had graded that change.

**Root cause, one sentence:** the branch records a reviewer's *answer* and never the round itself, so a round
whose reviewer never wrote is indistinguishable from a round that passed, and `adopt-item` admits it —
`tools/loop/worktree.sh:323-325` (HEAD) refuses only what `carried_review_refusal`
(`tools/loop/worktree.sh:256-259`) finds, i.e. only `evidence/review-failed-round-<n>.md`, which the run writes
after the round has answered (`tools/loop/run.sh:1079`, HEAD); the review loop announces nothing before it starts
the reviewer (`tools/loop/run.sh:989`, HEAD) and records nothing when the round passes.

**Red tests on HEAD, before the fix.** Two cases added to `tools/loop-cases.sh`, run against the engine at
`522d69a7ac21` with the full suite (`evidence/red-HEAD.log`, 2026-10-22T21:05Z); the tally is
`evidence/red-HEAD.xml` (`tests="168" failures="2"`), and each failed for the reason its own line names:

- `worktree#refusesABranchWhoseReviewRoundNeverAnswered` — a branch carrying one archived change of its own, a
  green XML, a passed round 1 and an *announced, unanswered* round 2 reports `branch-state` as `adoptable` and
  `adopt-item` merges it (expected `review-unanswered`, got `adoptable`).
- `run#announcesAndRecordsTheReviewRound` — a successful run's branch carries no record of its review round at
  all (`review-round-1-requested.md` not found in the change's evidence directory).

**Owning capability and guideline section.** The capability is `agent-issue-loop`; the requirement it extends is
"Version control belongs to the run's script, not to the agent", whose scenario "A branch that carries a previous
attempt is refused, named and recorded" enumerates what makes a branch inadmissible for adoption (no green
verification, or the reviewer's refusal). The guideline that owns the loop and states the adoption rule is
`guidelines/Backlog.md` §7 "The loop" ("No item is replayed onto a tree nobody reviewed: a branch that is not
the run's own work is `adopt-item`'s decision, taken on what a stranger to the run can check").

**What the fix has to decide, and what decides it.** The item asks whether a branch whose last review round is
unanswered is a *finished attempt* at all. That question is already answered by the spec: a branch that carries
one archived change of its own is a finished attempt, and an attempt that is not admissible for adoption is
refused and escalated as the owner's decision — never worked again, because starting over would throw the
change away. So no new routing is introduced here: the unanswered round becomes one more way of being
inadmissible, and the existing refusal-and-escalation path carries it. A branch that carries no round at all is
left adoptable on purpose — a review that was never asked for (`--review off`) is not a review that never
answered.

## What Changes

- `tools/loop/worktree.sh` — one reader, `carried_review_state`, replaces `carried_review_refusal`: it names the
  highest round the branch carries and what that round says (`passed`, `failed`, `requested`). `branch_state_of`
  gains the state `review-unanswered`, and `adopt-item` refuses it with the round named.
- `tools/loop/worktree.sh` — a new command, `record-review-round <issue> <change> <round> requested|passed`,
  writes the round onto the item's branch as a commit in the change's own `evidence/` directory;
  `record-review-failure` keeps its name and its `review-failed-round-<n>.md` for the refusal arm.
- `tools/loop/run.sh` — the review loop announces a round before it starts the reviewer and records the answer
  when `PASS` or a zero-blocking report arrives, so the round's whole life is on the branch. A record that
  cannot be written warns and names what a later run will read instead.
- `tools/loop/prescreen.sh` — the comment that states what the branch's state means names the new state; the
  routing needs no change, because every state but `unfinished` is already the owner's decision.
- `tools/loop-cases.sh` — the two cases above, plus `run#commitsWithoutAnAmbientIdentity` realigned: the run now
  commits the announcement and the answer on the item's branch too, so the case counts the run's two *work*
  commits by subject and asserts the identity over every commit since the base.
- `guidelines/Backlog.md` §7 "The loop" — the adoption paragraph states that a review round is part of what the
  branch carries in both directions, and which branch stays adoptable.

**Surface, counted before the plan was fixed.** `grep -rln 'record-review-failure\|review-failed-round\|
carried_review_refusal\|review-refused\|branch-state' --exclude-dir=archive --exclude-dir=.pi .` names five files
(`tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/prescreen.sh`, `tools/loop/run.sh`,
`tools/loop/worktree.sh`) — so the count is **2 cases added, 1 rewritten, 0 fixtures realigned** in
`tools/loop-cases.sh`, one function and two call sites in `worktree.sh`, two call sites in `run.sh`, and two
mutation records in `tools/loop-mutations.sh`. The reviewer prompt states no marker and is untouched.
