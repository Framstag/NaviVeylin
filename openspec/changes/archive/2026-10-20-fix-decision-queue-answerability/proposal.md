# Proposal

## Why

The decision queue is a query over one label (`tools/issues/decisions.sh`), and the run's ceiling counts that
query. Two mechanisms put entries in it that nobody can answer. Measured 2026-10-20 on the live tracker:

- **A blocked item is escalated with a reason and no question.** Item #91 carried `agent/needs-decision` and
  its only statement was `reported success but the tree is still at the base commit — nothing was
  committed` — a fact about the failed child, not a choice. The blocked path appends `**The decision:**`
  only when the reviewer's report carries a `LOOP-DECISION`, and a block that never reached a review round
  has none.
- **A closed item keeps the label.** #81 was closed and still carried `agent/needs-decision`, so it appeared
  in the queue and counted against the ceiling; seven "awaiting a decision" entries were printed, one of them
  a closed item.

Issue #194 fixed the neighbouring case (an escalation that leaves *no* statement) by naming it in the queue;
this is the case where a statement exists and is still not answerable.

## What Changes

- A block that reaches the escalation without a reviewer decision states the only two-way choice a block
  leaves — **retry the item, or abandon it** — with the run's reason as the ground for either answer.
- A closure takes the decision label off the item in the same write that records the settled state, so
  "closed" and "awaits a decision" can never both be true. An item that does not carry the label is
  untouched.

## Capabilities

### Modified Capabilities

- `agent-issue-loop`: "Closure follows a passing review and carries evidence" gains the scenario that a
  closure removes the decision label; one requirement is added — **a block asks its own question** — so an
  escalation never holds a decision field that only repeats the reason.

## Impact

- `tools/loop/run.sh` — the blocked path's escalation reason.
- `tools/issues/close.sh` — the closure's label write (through `update.sh`, so the vocabulary and the
  reserved-label refusal still apply).
- `tools/loop-cases.sh` — `run#blockedItemAsksItsOwnQuestion`, `labels#closeRemovesTheDecisionLabel`.
- `tools/loop-mutations.sh` — `29.1`, `29.2`.
- `guidelines/Backlog.md` §5 — one clause: a closure leaves no decision label behind.

Additive, not breaking: an item without the label and a block whose reviewer wrote a decision are unchanged.
Rollback: revert the two hunks; labels written stay as they are (a person can set them again).

Verification: the two cases red on HEAD, green after; mutations red-when-mutated; `tools/loop-selftest.sh`
120/120; `tools/issue-tooling-selftest.sh` 91/91; `check-portability.sh`. No Gradle gate — no compiled file
(`guidelines/Build.md` §2, the scope block).
