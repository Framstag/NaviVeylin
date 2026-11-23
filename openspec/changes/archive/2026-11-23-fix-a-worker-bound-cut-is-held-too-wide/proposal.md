# Proposal — a cut at the worker's own turn bound is held too-wide

## Why

The `cut)` arm of `tools/loop/run.sh` writes one label write for every bound the run enforces: `loop_labels unpick`,
which takes `agent/in-progress` off and leaves the item `agent/eligible`. That is right for a bound that leaves work
behind — a review round ended at its turn bound, a gate child or a tail ended at the child clock, a child the run's
deadline ended, the rework worker of a refusing round — because the next run resumes that phase from the branch.

It is wrong for the one bound where nothing was left to resume. Measured on the item this change answers (#1039):
#1021 was cut at the worker role's recorded turn bound (200 turns) on three consecutive runs and #984 on two, each
`result: cut`, `stopReason: bound`, `boundKey: LOOP_CHILD_TURNS_MAX_WORKER`, `boundValue: 200`, `merged.sha: null`,
248 and 234 leftover files, 0 commits, about 10 min and 0.48 USD. Nothing merged, so the tree was still the base, so
the next run had nothing to resume: it started from the base and paid the same 200 turns, and the item arrived at the
pre-screen as fresh work three times without one line saying its size is the problem.

## What changes

The arm already reads the key from the child's own `LOOP-TIMEOUT` line (`bound_key`, `run.sh:2158`). When the bound is
that one — the item's **own** worker, the worker role's turn bound, nothing merged — the arm now also writes the
shaping verdict the context ceiling already writes for the same finding (`run.sh:2099`): `loop_labels too-wide`, which
puts `status/on-hold`, adds `agent/needs-shaping` and posts the one comment whose first line is
`too-wide: the item wants splitting into children`. The item's note gains the reason instead of saying it is left for
the next run. Every other bound keeps the plain `unpick` and the existing sentence, and the rework worker's key
(`LOOP_CHILD_TURNS_MAX_WORKER_REWORK`) is not this one, so a refusing round's rework keeps its phase reading.

## Red case

`run#aBoundEndedItemRecordsItsBound` — the case that already covers this arm and shortens `LOOP_CHILD_TURNS_MAX_WORKER`
to 3 — gains the label assertions and fails on the base commit, which writes only
`gh issue edit 21 --remove-label agent/in-progress`: `evidence/red-on-base.log`, 1 of 1 cases failed,
2026-11-23T09:41:36Z. `run#childPastItsTurnBoundIsCut` is realigned where it asserted the absence of the hold.

## Surface

Five files, counted before the plan (`tools/look.sh` over the cut arm's note and over the bound keys):

| # | where | what it says about this arm |
|---|-------|-----------------------------|
| 1 | `tools/loop/run.sh` | the arm and its note sentence |
| 2 | `tools/loop/tests/loop-cases.sh` | `run#aBoundEndedItemRecordsItsBound` (the red case), `run#childPastItsTurnBoundIsCut` |
| 3 | `guidelines/Backlog.recipes.md`, the section *Leftovers, the report and the queue* | the paragraph that calls the item's own worker's cut "a phase, not a finding" — the owning guideline section |
| 4 | `openspec/specs/agent-issue-loop/spec.md` | the requirement `A block records the mechanism that stopped it`, whose scenario names this case |
| 5 | `tools/loop/tests/loop-mutations.sh` `81.x` | its comment only: the case asserts the absence of an escalation, which it still does |

`guidelines/Backlog.md` and the prompts state no label behaviour for a cut and stay as they are. The standalone case
for this arm and its mutation are child B of #1033; this child adds neither.
