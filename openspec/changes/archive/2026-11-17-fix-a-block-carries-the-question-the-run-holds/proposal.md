# Proposal — a block carries the question the run holds

## Why

`tools/loop/run.sh`'s blocked arm asks exactly one source for the fork it escalates: the reviewer's report. A block
that ends before any review round has no reviewer at all, so `decision_field` is empty and the arm writes its own
sentence — "retry the item, or abandon it".

The worker's own `LOOP-DECISION` is already parsed in the same scope (`$worker_decision`, from `$final_report`) and
already trusted on the neighbouring arm: a skip that means a decision is escalated with it. The block arm never looks
at it, although `prompts/worker.md` requires it for a blocked verdict — "a skip on `needs-decision`, `needs-diagnosis`
or `stale`, or a blocked item".

Measured on #894 (2026-11-17): the child's report carried three options and a recommendation, the item was escalated
with the run's own retry/abandon sentence (`issues/894#issuecomment-6950362496`), and the fork was only findable by
opening `.pi/issue-loop/items/894/worker-continuation-report.md` — a directory a person answering the queue does not
have. `answer.sh` quotes the last comment's question (`tools/issues/answer.sh`, the read `issues_question_of` makes),
so the owner was asked to answer a question the run invented while the real one sat unread.

The count is the same measure the queue already carries: `tools/issues/decisions.sh` lists the items awaiting a
decision, and `.pi/issue-loop/run.json`'s item record for #894 shows the state that produced this item —
`result: "blocked"`, `reviewRounds: 0`, `reviewVerdict: "off"`, `escalatedLabel: "agent/needs-decision"` — a block
with no review round behind it and a decision label written anyway.

## What Changes

1. **The block arm reads the worker's decision.** `decision_field` is the reviewer's `LOOP-DECISION`, and the worker's
   when the reviewer wrote none. The run's own sentence stays as the last resort, for a run that holds no question at
   all.
2. **The rule says which source is which.** `guidelines/Backlog.md` "Rule: a block asks its own question" named the
   reviewer as the only source and called the run's sentence the answer to the reviewer's silence; it now names the
   order — the reviewer's, then the worker's, then the run's own.
3. **A case holds it.** `run#aBlockedItemCarriesTheWorkersDecision` drives a blocked verdict that carries a question
   and no review round, and asserts the item's comment carries the worker's text and not the run's sentence. A
   mutation record (`29.3`) takes the fallback out and the case goes red.

## Scope

`tools/loop/run.sh`, its self-test and its mutation set, and the one rule in `guidelines/Backlog.md`. No app code, no
native code, no dependency, no Gradle change, no persisted data. The shipped behaviour is kept: a block that holds no
question still gets the run's own two-way sentence (case `run#blockedItemAsksItsOwnQuestion`), and a block that ran a
review round still quotes the reviewer (case `run#escalationCarriesTheDecision`).

**The branch carries a second item.** `41dc62e3` (issue #954, `tools/loop/prompts/reviewer.md` plus its case
assertions and record `954.1`) was committed by another session on top of this change's `b08c26b7`, so the gate record
covers the branch and not this item alone; #960's own share is the four loop cases and records `29.3`/`29.6`. The two
land together — the assertions in this item's commit read the clause in that one, so neither half is green alone.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None as a modification: the shipped scenario `An escalation names the decision` is scoped to "after a failed review"
and keeps holding word for word — the reviewer's decision is still what that path carries. What this change adds is
the other side of the same fact, a block that never reached a round, so it is declared as a new requirement below.

`agent-issue-loop` gains one:

- the question a block carries is the one the run holds, and the run's own sentence is what a block with no question
  falls back to.

surface: 1 requirement and 3 scenarios in 1 delta file, 1 fallback line in `tools/loop/run.sh`, 1 new case plus 1
rewritten fixture and 1 mutation record, 1 rule in `guidelines/Backlog.md`. Re-take with
`bash tools/loop/tests/loop-selftest.sh --case run#aBlockedItemCarriesTheWorkersDecision` and
`bash tools/loop/tests/loop-mutations.sh --only 29.3`.
