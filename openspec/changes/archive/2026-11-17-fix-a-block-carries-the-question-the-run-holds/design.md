# Design — a block carries the question the run holds

## Context

See `proposal.md` — Why. Constraints:

- **The reviewer's decision stays the first source.** A round that read the diff holds the newest question about it,
  and the shipped scenario says so.
- **`worker_decision` is already in scope.** It is read from `$final_report` beside the other fields and consumed by
  the skip arm; this change consumes the same value one arm over, so no second read of a report happens.
- **The run's sentence keeps a job.** A block whose report holds no `LOOP-DECISION` at all — a worker that died
  before writing one, a round whose answer arrived cut — still has to leave the owner something answerable.

## Goals / Non-Goals

Goals: an item's escalation carries the fork the run holds; the owner never reads a question the run invented while a
child's own question sat in the evidence directory.

Non-goals: changing *which* blocks escalate (that is `the-run-fault-block-asks-no-question`), re-posting a decision
that arrives after an escalation was already written, and reading a decision out of a transcript rather than a report.

## Decisions

### D1 — The order is reviewer, worker, run

Alternatives:

- **Worker first** (rejected): a review round that read the diff has the current question — its options name what the
  reviewer refused, where the worker's name what the worker found. Precedence is what the shipped scenario asserts.
- **Worker only when no review round ran** (rejected as a distinction without a difference): "no round ran" is exactly
  the case where `$reviewer_report` is empty, so testing it would be a second way of saying the same thing, and a
  round whose report exists but carries no decision has to fall through anyway.
- **Concat both** (rejected): two questions on one item is the defect #225 closed — an item asks one thing.

Chosen: `[ -n "$decision_field" ] || decision_field="$worker_decision"`, one line under the reviewer's read.

### D2 — The fallback is not deleted

The run's own sentence is what a block with no question in hand says, and the case `run#blockedItemAsksItsOwnQuestion`
holds it. Deleting it would leave such a block labelled and silent, which is the state #194 is about.

### D3 — The invariant is held by two cases and two mutations

`run#aBlockedItemCarriesTheWorkersDecision` is the only case that can see the *fallback* (the other two pass with the
line deleted: `run#escalationCarriesTheDecision` quotes a reviewer, and `run#blockedItemAsksItsOwnQuestion`'s reports
carry no decision at all), so mutation `29.3` removes the fallback and that case is its red proof.
`run#aReviewersDecisionOutranksTheWorkers` is the only case that can see the *order* — both reports carry a decision and
the reviewer's must win — so mutation `29.6` makes the fallback unconditional and that case is its red proof. Review
round 1 found the second gap: the first draft shipped the precedence as a scenario no case could fail on.

### D4 — The two escalation scenarios stay separate claims

The shipped `An escalation names the decision` (`openspec/specs/agent-issue-loop/spec.md`) is scoped to "[a] run
[that] escalates an item after a failed review" and states that the reviewer's decision is what that path carries. This
change's third scenario states the tie-break — the reviewer's wins when both reports carry one — a different claim
about a state the shipped scenario does not reach. It is declared `ADDED` for that reason rather than as a `MODIFIED`
block rewriting a requirement this change does not change; the review graded the choice defensible and recorded the
duplicate ownership as a note.

## Risks / Trade-offs

- **A worker's option list can be stale** — a block that ran no review round is escalated with what the worker knew.
  Accepted: the alternative is the run's invention, which is strictly less informed.
- **Two reports can disagree** — the reviewer's and the worker's. The reviewer wins, and the record keeps both
  transcripts, so the disagreement is readable rather than hidden.

## Open decisions

none
