# Proposal

## Why

Two items reached the tracker with the same defect in it: an item that cannot be worked until another one lands was
worked anyway.

Item **#919** (the parent) is the declaration: the tracker holds a dependency relation natively — `Issue.blockedBy`
reads it, `addBlockedBy` writes it — and the tooling read neither, so the only place an order between two items
could be stated was prose in the epic's body. The epic #906 states its order as a sentence ("898 first …, then 899,
then 900-902, …, then 903, 904, 905"), and nothing reads a sentence.

Item **#913** (the child) is the measured within-run case. The run of `2026-11-14T13:41:53Z` recorded item #898
`blocked` with `stopReason: child-blocked` — its own prerequisite never landed — and then started the six items of
the same change (`#900`, `#901`, `#903`, `#904`, `#899`, `#902`) anyway. The run state
(`.pi/issue-loop/run.json`) records the result `skipped` over 25/21/27/35/26/26 turns: 160 turns, 254 tool calls,
4.97 M of 67.45 M tokens (7.4 %), 25 m 31 s of 209 item minutes, and **0 landings**. Those six items carry no edge
to each other; what they share is the change, whose earlier task was the prerequisite that had just been refused.

So there are two sources of one relation and one reader serves both: the tracker's edge, and the block this run
itself recorded. Fixing only the tracker's edge would leave #913's run repeating exactly what it measured, because
at the time of that run no edge existed.

## What Changes

- The issue tooling writes the tracker's dependency edge: `create.sh` takes `--blocked-by <number>`, and `.blockedBy`
  in a finding's own JSON, and writes `addBlockedBy` for each predecessor. A predecessor is read before the filing,
  so a number nobody filed refuses with nothing written. A number named as both `--parent` and `--blocked-by` is
  refused: a parent holds an item, it does not precede it.
- The pre-screen reads the edge and excludes a blocked candidate with the verdict `blocked-by:#N`, recording which
  source it read (`blockedBy:#N`, or `run-blocked:#N` for a block this run recorded).
- The same reader excludes a candidate whose change an item of this run recorded `blocked` names — the #913 case —
  unless the item that names the change was `done` (a landing, not a block).
- The verdict is a role value (`blockedVerdict`), optional like `shapingVerdicts`: an empty value, and a profile
  that omits the key, blocks nothing. That is what keeps a profile written before this change running.
- No label is written for this verdict, unlike the other refusals: the edge the verdict was read from is already
  visible on the tracker, so the state the label would carry is one query away and a second copy of it would be the
  thing to keep in step. The within-run source has no such edge, but its record is the run's own state, which the
  run's report already prints.

## Capabilities

### New Capabilities

none

### Modified Capabilities

- `issue-backlog`: an item's predecessors are the tracker's own relation, written by the create path and read from
  the tracker; the profile declares the verdict a blocked candidate gets, and an empty or absent declaration blocks
  nothing.
- `agent-issue-loop`: a candidate the tracker holds blocked, and a candidate whose change this run recorded blocked,
  are not work; the verdict names its predecessor and its source, and the exclusion is a refusal and not a cut.

## Impact

**Tooling.** `tools/issues/create.sh` and `tools/issues/lib.sh` (the write), `tools/loop/prescreen.sh` and
`tools/loop/lib.sh` (the read and the optional role key), `tools/tooling.json` and the foreign fixture's profile
(the value), `tools/issues/tests/fixtures/gh` (the mutation and `--jq`), the loop's `shared.tsv` (one row for the
`blocked` reading `report.sh` assigns and `prescreen.sh` now reads).

**Tests.** Two loop cases (the tracker edge, the within-run source) plus one for the empty and omitted role key, and
one issue-tooling case for the write's four refusals.

**Documents.** None beyond the deltas here. `guidelines/Backlog.md` §3 owns what the tracker holds, and the edge is
part of it; the pre-screen's verdicts are `guidelines/Backlog.md` §7's subject, and no sentence of either changes.

## Verification

- Every case was falsified: with `roles.blockedVerdict` emptied, `prescreen#aBlockedCandidateWaitsForItsPredecessor`
  reports `expected 'blocked-by:#90', got 'eligible'`; the write's case calls `--blocked-by` on a create path that
  does not have it, and the flag is a usage error there.
- The suite cases run offline, under the stub `gh` and the stub `pi`; no token, no network and no tracker write.
- `tools/check-portability.sh` is green: the new role value is read from the project's profile like every other, and
  the foreign fixture declares a deliberately different one (`waiting-on`).

## Open decisions

none