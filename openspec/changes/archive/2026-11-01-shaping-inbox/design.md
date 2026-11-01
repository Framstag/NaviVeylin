# Design — the shaping inbox

## Context

The tracker's vocabulary already distinguishes a decision from a hold, and the distinction cost two defects to get
right:

| state | who writes it | what it says | can a person act on it |
|---|---|---|---|
| `agent/needs-decision` | the run's escalation, a block, a stale skip | a question with options and a recommendation | yes — `decisions.sh`, `answer.sh` |
| `status/on-hold`, no question | the worker's shaping verdicts, the device lane, a person | *this run could not take it* | no — the tree states it "SHALL NOT be escalated" (#193) |

The shaping verdicts are the ones in between: they are not a question (the run is forbidden to guess one), and they
are not a device or capacity limit either — they say the item's *shape* is wrong. Four of them
(`needs-diagnosis`, `unspecifiable`, `too-wide`, `not-spec-shaped`), 39 items on the tracker at 2026-10-30.

`finding-shape-at-filing` (C2) makes shaping an ordinary act at filing time. This change gives the same act a place
to happen for an item that is already in the tracker.

## Goals / Non-Goals

**Goals:**

- An item the loop cannot work is visible as work a person owes, with the verdict the run reached printed from the
  item itself.
- No second answer mechanism: leaving the inbox is a write by a writer that already exists.
- No effect on the run's own stopping rules.

**Non-Goals:**

- Ranking the inbox. The band already ranks the items (`issue-metadata`); the queue lists, it does not score.
- Deciding *how* to reshape an item. That is an explore session.
- Retiring stale items — `cleanup-todo` and `resolve-stale.sh` own that.
- A cap. The decision cap exists because unanswered decisions block new work; shaping work does not, and a cap would
  make the run refuse for a queue it cannot fill (`spec agent-issue-loop`, "A pile of open decisions stops the run").

## Decisions

### D1 — A new namespace value, not a second status

`agent/needs-shaping`, beside `needs-decision` and `needs-device`, because the `agent` namespace is already the one
that answers *what may be done with this item next* and the three existing values are exactly its siblings. The
`status` namespace answers *what state the item is in*, and both facts are true at once: an item is `on-hold` (a run
could not take it) **and** `needs-shaping` (a person must reshape it).

**Eliminated: a second status value**, e.g. `status/needs-shaping`. Status replacement is single-valued by
construction — `update.sh --status` replaces every other value in the namespace — so the item would have to lose
the fact that a run held it, which the next reader needs to see the verdict.

**Eliminated: reusing `agent/needs-decision`.** `answer.sh` refuses an item that does not carry the decision label
and the queue reads a question off the item; an item with no question would break the one invariant that queue has.

### D2 — Which verdicts carry the label is configuration

`roles.shapingVerdicts` in `tools/tooling.json`, beside the other roles the engines read. The verdict names are the
loop's own; which of them mean "a person must shape this" is the project's judgement, and a ported project with
three verdicts declares three. An empty list is a declaration, not an omission — the convention the file states for
every key.

### D3 — The queue prints the item's record, it does not summarise it

`decisions.sh` established the shape and its reason: an item's escalation wrote a verdict word above the sentence,
so a reader that took the first line answered the verdict instead of the question (#243, `ISSUES_QUESTION_JQ`). The
shaping queue therefore prints the verdict word **and** the clause, from an extraction that lives in
`tools/issues/lib.sh` beside the question's, so the two queues cannot disagree about the same comment.

### D4 — No new answer verb

Every exit is an existing writer: `update.sh` (reshape, remove the label), `close.sh` (settled, label removed in the
same write — the rule `decision-queue` already states for the decision label), `answer.sh` (the person decides the
verdict was really a fork). A `shaping-answer.sh` would be a fourth vocabulary of transitions for the same three
outcomes, and the tree has spent two changes learning that the second format drifts.

### D5 — The run writes the label on the path it already uses, in the same call

The skip path in `tools/loop/labels.sh` already writes the state and the accounting line in one call, because *"a
state whose reason lives only there cannot be answered, re-checked or even understood"* (#192). The label joins that
call; it does not become a second write, which would leave a window where the item is held and nameless.

## Open decisions

none

## Risks / Trade-offs

- **The inbox can grow without bound.** Accepted and deliberate (see Non-Goals). The mitigation is that it is
  *visible*: a queue with a count is a number a triage pass can rank, and `triage-todo` already reads bands.
- **`too-wide` also means "split into children", which the queue cannot do.** The queue states it (`too-wide`) and
  the split is an explore session that files the children and removes the label from the parent. Stated so a reader
  does not expect a mechanical split.
- **A verdict word is not a question, so a reader may still not know what to change.** The clause the run writes is
  what names the artifact and the line; if a clause turns out to be unusable the fix is in the worker's verdict
  text, which is `agent-issue-loop`'s, not this change's.
- **Three writers touch the label.** `update.sh`, `close.sh` and the run's skip path, all through the one label gate
  (`label-opaque`, `label-reserved`). The cases assert each removes or writes it in the same call as its outcome.

## Verification

- `tools/issue-tooling-selftest.sh` gains the nine `shaping#` cases; `tools/loop-selftest.sh` gains
  `run#aShapingVerdictWritesTheLabel`, `run#aVerdictOutsideTheListWritesNoLabel`,
  `run#theShapingInboxDoesNotCountAgainstTheCeiling` and `labels#closeRemovesTheShapingLabel`, and
  `labels#verdictToLabelMatrix`'s four shaping rows are re-aimed.
- `tools/loop-mutations.sh` gains `75.1`–`75.3`: the label written beside the hold, the list that decides which
  verdicts do, and the state write that takes it off. Each goes red on its named case and green again after the
  restore.
- `tools/tooling.json`'s new key is read by `tools/check-portability.sh`'s value sweep: if the value appears in an
  engine, the check fails.
- No compiled file is touched → no Gradle gate. Structural checks: both suites, `tools/issues/check-metadata.sh`,
  `tools/check-doc-routes.sh` and `openspec validate shaping-inbox --strict`.
