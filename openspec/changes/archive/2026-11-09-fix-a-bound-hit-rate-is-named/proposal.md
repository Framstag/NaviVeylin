# Proposal — the run prints the rate of the items its own bound ended

## Why

**Root cause.** The report describes a bound-ended item one row at a time and never as a rate. `tools/loop/report.sh`
prints the cut items on a line of their own (`cut short by the run's own bound: …`, `:213`) and the token band as a
run-level line (`over the item token bound …`, `:186`), and nothing in the state or the report counts how many of a
run's items the bound ended against how many it attempted. So a run that hits the bound five times as often as the
archive prints the same shapes as a healthy one.

**Evidence, verified against the records (2026-11-09, issue #809).** The run the item cites,
`.pi/issue-loop/runs/2026-11-06T21:19:06Z.json`, holds 11 item records and exactly two with `stopReason: bound` —
#791 at `context 201122`, 190 turns, and #798 at `200178`, 111 turns — that is 2 of 11, 18 %, and 36.0 Mtok of the
run's 116.4 Mtok spent with nothing landed. Over the whole archive at the time of this change
(`jq -s` over `.pi/issue-loop/runs/*.json`): 407 item records, 11 of them bound, and 160 of the 407 written before
the field existed, so the honest denominator is 247 — 11 of 247, 4.5 %. The item's own 10 of 300 is the same figure
one week earlier. The live record of the run that filed this change is worse still: 6 of 9 items bound (66.7 %).

**The red test.** `report#theBoundRateIsNamed` and `state#aCutRecordsTheFilesItsPatchCarries`, added to
`tools/loop/tests/loop-cases.sh` by this change. On HEAD (base `2efea948`) both fail
(`evidence/red-on-HEAD.log`, 2026-11-09T16:40Z):

```
report#theBoundRateIsNamed|   the run's rate, over the items it attempted: 'bound: 2 of 11 items (18.2 %)' not found in: …
state#aCutRecordsTheFilesItsPatchCarries|   and the number of files that patch carries: expected '3', got 'null'
```

The existing `run#childPastItsContextBoundIsCut` and `report#anItemOverTheTokenBoundIsFlagged` are left as they are:
they pin the stop and the band, not the rate.

**The single fix.** Two halves, one figure:

1. `tools/loop/report.sh` prints `bound:` beside the token band — the run's rate, the archive's beside it, and the
   items the bound ended named with what each kept (`#791 190 turns, 16 files`). The class is the record's own
   mechanism (`stopReason: bound`), so no vocabulary is added, and each side is counted only over the records that can
   name a mechanism: an archive record written before the field existed can never be counted a hit, and counting it in
   the denominator reports the bound rarer than it is — the direction the item was written about.
2. `tools/loop/state.sh done --leftover` records `leftoverFiles` beside the path, read from the patch itself, so a
   cap-ended item's kept work is priced rather than only named. `metrics.turns` was already recorded.

**The requirement the defect contradicts.** `openspec/specs/agent-issue-loop/spec.md`: "The numbers a run records name
the bounds they were measured against" (`:3900`) states the bounds a run's figures are read against and no rate of
hitting one, and "A report reports the shape a run spent its budget in" (`:3265`) states shapes and no rate, which is
why the delta ADDS one requirement beside them rather than rewriting either.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md` §7b "What an item costs, and the bounds on it"
owns the context bound, its p75/p95 measurement and the 10-of-300 figure this change makes a line; §7b gains the
paragraph. The prompt files are untouched: the rate is engine behaviour, and
`tools/look.sh 'leftoverFiles|bound:' --path tools/loop/prompts` → `0 matches`.

**Not in this change.** The item's third fix candidate — start a `feature` item, or one whose change names more than N
files, with the split shape rather than one session — is a change to the child's prompt and to the start of an item,
and it needs a threshold (N) no measurement here fixes. Nothing in the archive says how many files a change must name
before a session cannot finish it, so a number invented now would be the kind of knob this repository has removed
before (issue #804's recorded count, issue #762's p95 rule). It stays a separate item.

## Surface

surface: 2 cases added, 1 mutation pair added (`265.1`, `265.2`), the engine (`tools/loop/report.sh`,
`tools/loop/state.sh`), the case and mutation files, and 2 records (§7b, the spec delta) — `grep -rn leftoverFiles`
names 8 lines in 5 files: `tools/loop/state.sh` (2, one of them the field), `tools/loop/report.sh` (1, the cell),
`tools/loop/tests/loop-cases.sh` (3, the assertions), `tools/loop/tests/loop-mutations.sh` (1, the record) and
`guidelines/Backlog.recipes.md` (1, the paragraph) — and `grep -rln stopReason tools/loop` names 5 files, of which
`report.sh` and `state.sh` are what this change edits. `tools/look.sh 'leftoverFiles|bound:' --path
tools/loop/prompts` → `LOOK: 0 matches in 0 files`: the rule is engine, not a prompt.

## Impact

Additive; no compiled file, so no build and no Gradle task is owed.

- `tools/loop/report.sh` — the `bound_share` helper and the one line it prints.
- `tools/loop/state.sh` — `leftoverFiles` read from the patch `done --leftover` names.
- `tools/loop/tests/loop-cases.sh` — two cases added and registered.
- `tools/loop/tests/loop-mutations.sh` — records `265.1` and `265.2`.
- `guidelines/Backlog.recipes.md` §7b — the paragraph naming the line, its denominators and its measurement.
- `openspec/specs/agent-issue-loop/spec.md` via the delta in `specs/agent-issue-loop/spec.md`.

**Rollback.** Reverting the four files' hunks restores the base byte for byte and returns the two cases to red, which
is the signal the rate is gone. `tools/loop/tests/loop-mutations.sh --check` then reports `265.1` and `265.2` as
`MUTATION-DID-NOT-APPLY`, which names them. A state written by this version carries one extra field
(`leftoverFiles`); a reader of an older state prints that item's turns alone.

## Open decisions

none — the item's `ITEM_DECISION` block records `none`, and the direction is the item's own stated one: measure the
rate and price what a cap-hit kept, without moving the bound.
