# Design — fix-decision-cap-counts-candidates

## Context

The loop holds two definitions of "awaiting a decision": `tools/loop/prescreen.sh` asks the tracker with
`--eligible-only`, so it only ever meets items the owner has opted in; `tools/loop/run.sh:698` asks without it, so
the ceiling it enforces is over a different population. The second definition is the one that stops work, and it
stops work on questions nobody can answer.

## Decision 1 — the cap counts the population the pre-screen offers

`--eligible-only` is the tooling's own answer to "which items is this loop allowed to work", and the ceiling's
input must be a subset of it: a decision the run may not work is not a decision the run may be stopped by. The
change passes the flag at step 6, which is the single-site fix — no new query, no new profile key, and the two
definitions of "awaiting a decision" become one (`tools/issues/list.sh:150` is the filter, over the profile's
`eligibilityLabels`).

Alternatives rejected: counting the pre-screen's own queue (the cap must be read *before* the pre-screen is paid
for, which is why step 6 precedes step 7); a new `--candidate-only` flag in `tools/issues/list.sh` (the tooling
already has the population, and a second spelling of it is a second thing to keep true).

## Decision 2 — the refusal names both counts

The owner's question after a refusal is "which of these can I answer?". The line now reads
`N of the M item(s) that await a decision are candidates, and the cap is X`, and the items printed below it are
the N candidates. The labelled total M costs one extra tracker read, and it is taken **inside** the refusal
branch: a run that starts — the path every run normally takes — pays exactly what it paid before. The wording
keeps the phrase *await a decision*, which `run#decisionCapRefusesToStart` and
`run#theShapingInboxDoesNotCountAgainstTheCeiling` both quote.

## Decision 3 — the fixture is a new scenario directory, not a fifth item in `decisions`

`tools/loop/fixtures/scenarios/decisions/issues.json` is read by four cases (two pre-screen, one closure, one
run), two of which assert a workable item does **not** exist ("nothing eligible"). The shape this change needs —
two candidates, one parked decision *and* one workable item, so the run can spend the slot the parked decision
must not take — would have changed those cases' subject. `parked-decisions/` carries exactly that shape.

## Decision 4 — mutation record 9.6 is left alone

`tools/loop-mutations.sh` record 9.6 anchors on `^if [ "${open_decisions:-0}" -ge "$max_decisions" ]; then`, which
this change keeps verbatim as the line's head; only the two reads above it changed. The record therefore still
applies (`mutation#everyDeclaredRecordApplies` is green), and the falsification for this change's own invariant is
the red case on HEAD (dropping `--eligible-only` is what HEAD is).

## Open decisions

none

## Risks

A tracker where a decision carries `agent/eligible` but no hold, and the owner removes the opt-in to park it, is
now invisible to the cap — which is the point: the item is not a candidate and no run can work it. An item the
loop escalates keeps its opt-in, so no escalation the run itself writes can escape its own ceiling.
