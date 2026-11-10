# Design — the cut round quotes the line that ended it

## The root cause

`run_child` ends a child at one of two spend bounds and writes which one into the child's own report, with the
figure it reached (`tools/loop/run.sh:1719`–`:1728`):

- `LOOP-TIMEOUT: the child reached the recorded turn bound of <n> turns (LOOP_CHILD_TURNS_MAX_<ROLE> for the
  <role> role) after <m> turn(s) — the run ended it and everything it started`
- `LOOP-TIMEOUT: the child reached the recorded context bound of <n> tokens (LOOP_ITEM_CONTEXT_MAX) at <m> tokens —
  the run ended it and everything it started`

The two readers that turn that transcript into a cut round — the arm after the gate (`tools/loop/run.sh:2839`
before this change) and the arm before the gate (`:2397`) — test the line for **presence** with
`report_field "$report" LOOP-TIMEOUT` inside `[ -n "$( … )" ]` and then write a fixed sentence that names both
bounds: `at one of its spend bounds (its role's turn bound or the item's context bound)`. The reading they discard
is the only one that says which bound fired and by how much, so the item's reason — the line `report.sh` prints
and the escalation carries — leaves a person choosing between two bounds the transcript beside it already chose
between (issue #829: #753's 200 427 and #819's 200 733 tokens against a 200 000 stop, each with a reason that names
neither the context bound nor its reading).

## The single fix

Read the line once into a variable and quote it in the reason, in both arms:

```
pre_gate_timeout_line=$(report_field "$pre_gate_report" LOOP-TIMEOUT)
…
pre_gate_refusal="the run ended the pre-gate reviewer at one of its spend bounds — $pre_gate_timeout_line — the round was cut, not refused, so the change is left for the next run"
```

```
reviewer_timeout_line=$(report_field "$reviewer_report" LOOP-TIMEOUT)
…
blocked_reason="the run ended the reviewer of round $round at one of its spend bounds — $reviewer_timeout_line — the round was cut, not refused, so the change is left for the next run"
```

The sentence is otherwise byte-identical — the round, the cut and the "left for the next run" clause stay where
they were — because a reader who knows the old line must still recognise the new one. The frame keeps saying the
round was cut by one of the run's bounds; the quoted line says which one and how far past it the child ran. Nothing
parses the sentence: `report.sh` prints the item's note as it stands (`tools/loop/report.sh:277` for the cut line,
`:619` for the resume action) and selects on `result`, so the added clause changes no reading the run takes.

The writer arm's line (`the child's writer (pid <n>) vanished …`) and the deadline arm are quoted unchanged if they
ever reach these two readers; the deadline is decided by the earlier `deadline_claim` branch, and a vanished writer
can only end an item's own child, whose reason already carries the transcript.

## Blast radius

`tools/loop/run.sh` only: two variable reads and two sentences, plus the comment that argues why the line is
quoted. No key, no state field, no report row, no prompt and no fixture reads the sentence.

`tools/loop/tests/loop-cases.sh`: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`'s note assertion is strengthened
from `turn bound` to `turn bound of 3 turns` and `LOOP_CHILD_TURNS_MAX_REVIEWER`; the new
`run#aPreGateReviewEndedAtItsBoundNamesIt` reaches the arm before the gate, which no case reached, and asserts the
same two facts beside the cut and the absent gate child.

`guidelines/Backlog.recipes.md` section 7b states the rule the two arms now follow.

`tools/loop/tests/loop-mutations.sh`: `73.1` quoted the presence test this change renamed, so it is re-pointed at
`if [ -n "$reviewer_timeout_line" ]`; `73.2` is new and replaces the quoted line in the pre-gate arm's reading with
the ambiguous phrase, so the case that owns the new clause goes red. Both records name lines this change writes.

`openspec/changes/fix-a-cut-round-names-its-bound/specs/agent-issue-loop/spec.md`: the `MODIFIED` requirement
restates the five shipped scenarios the delta's first draft omitted, so archiving the delta cannot drop them.

## Rollback

`git revert` of the change's commit. Nothing reads the quoted clause, so no state, branch marker or archive needs
the old wording; an item cut before the change keeps the reason it was written with.

## Alternatives the evidence eliminated

- **A second sentence naming the bound beside the fixed one** — two accounts of one fact that can disagree, which
  is the shape this change removes; the transcript's line is already the single account.
- **Choosing the bound from the item's own `metrics.context`/`turns`** — a re-derivation from a second reading,
  and one that is wrong exactly when the two bounds are close; the transcript line is written by the arm that
  fired.
- **Parsing the bound out of the line into its own field on the item** — a new state field for a fact the reason
  already carries, and every reader of the field would need the sentence's own words anyway.

## Open decisions

none
