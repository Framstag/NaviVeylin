# Tasks — a bound-ended item names the bound it spent

The items are ticked because the work is in the tree: this change was written after commit `cdf0648c` landed, and
every task below names the path and line that carry it.

## 1. Measure before changing code

- [x] 1.1 Read the class the archive carried: `bound 6 / child-blocked 1 / refused 9 / unclassified 119 /
  unmergeable 5` over 158 states (issue #923), and the two items a reviewer bound ended that carried no class at all —
  `#898` at the reviewer's 110 turns, `#883` at `LOOP_ITEM_CONTEXT_MAX=250000` with 250 418 recorded.
- [x] 1.2 Confirm what the tree already did with that class: `f9f616c5` (the landing of #898) records `bound` for a
  round the run ended, so the class exists and what is missing is the key and the figure beside it.
- [x] 1.3 Read where a child's ending is written down: the child writes a `LOOP-TIMEOUT` line naming the key and the
  figure, so the key is a reading and not a second decision by the run — `bound_key_value` (`tools/loop/run.sh:1252`)
  parses the turn bound, the context bound and the check bound out of it.

## 2. One reading, four phases

- [x] 2.1 `bound_key_value` returns `<KEY> <value>` or nothing — the three sed patterns for a turn, a context and a
  check bound, and no key where the line names a clock (`tools/loop/run.sh:1252-1266`).
- [x] 2.2 The four phases the spec names write it — worker, gate child, tail, rework worker — through the shared
  `bound_args` (`run.sh:3008-3012`, `:3930-3935`), so a bound-ended item carries `boundKey` and `boundValue`.
- [x] 2.3 A bound no configuration records a value for keeps the class alone: the item's own clock, the run's deadline
  and a vanished writer write `bound` with no key and no figure, and the comment at `run.sh:3931-3935` says why — a
  clock is not a value `loop.conf` records.

## 3. The round's own ending

- [x] 3.1 `run.sh` keeps the round, the key and the figure in `round_bound_round`/`round_bound_key`/
  `round_bound_value` (`:3001-3003`), sets them for the round before the gate (`:3104-3107`) and for each round after
  it (`:3736-3739`), and passes them as `--round-bound*` (`:4076`).
- [x] 3.2 The item's own class is not the round's: `metrics.roundBound` is written and the item's `stopReason` stays
  empty, as the shipped requirement asks.

## 4. The record takes the five fields

- [x] 4.1 `state.sh` accepts `--bound-key`, `--bound-value`, `--round-bound`, `--round-bound-key` and
  `--round-bound-value` (`tools/loop/state.sh:721-722`, `:686`).
- [x] 4.2 The shape is checked at the door: the three round flags travel together, the key is a `LOOP_…` one and the
  figure is a number (`state.sh:790-806`), each refusal naming what it refused.
- [x] 4.3 The fields are stored — `boundKey`, `boundValue` (the figure as a number) and
  `roundBound: {round, key, value}` (`state.sh:947-955`).

## 5. One derivation, two readers

- [x] 5.1 `bound_class_jq` (`tools/loop/lib.sh:326`) holds `bound_named`, `bound_round`, `bound_of` and `stop_class`,
  declared shared in `tools/loop/shared.tsv:19` and read by both `state.sh` and `report.sh`.
- [x] 5.2 It reads a record written before the fields existed back to the bound its own note names, telling a round
  from the item's own cut by the reviewer's role in the key and the round's own verdict.

## 6. The table and the report

- [x] 6.1 `state.sh report` prints the key and the figure in the `stop` cell and the round ending in a `round` column
  appended after every column a reader already quoted (`state.sh:342-345`, `:1514-1515`, `:1522-1523`).
- [x] 6.2 `report.sh` counts the rounds a bound ended on a line of its own (`report.sh:323`, `:342`) beside the item
  rate it does not replace (`:290`); the two figures are never folded into each other.

## 7. The cases and the mutation records

- [x] 7.1 `run#aBoundEndedItemRecordsItsBound` (`tools/loop/tests/loop-cases.sh:7672`) — a worker turn bound of 3, the
  item `cut`, `bound` with `LOOP_CHILD_TURNS_MAX_WORKER=3`, the state's `stop` cell and the report line.
- [x] 7.2 `run#aRoundTheRunEndedRecordsItsBound` (`:7738`) — a reviewer ended at 3 turns, `metrics.roundBound` with the
  round, the key and the figure, the round column, `rounds a bound ended: 1 of 1 item(s)` and `bound: 1 of 1 items`.
- [x] 7.3 Mutation records `923.1` and `923.2` (`tools/loop/tests/loop-mutations.sh:1165`, `:1166`) empty the key each
  case reads, and each case must go red.
- [x] 7.4 `report#wallTimeAndReasonInTheTable` and the shared-name table were realigned with the two new fields
  (`driver#theSharedNamesAreDeclared`).

## 8. The guideline and the delta

- [x] 8.1 `guidelines/Backlog.recipes.md` §7e carries the key, the figure and the round's own field beside the bound
  paragraph it already had.
- [x] 8.2 The delta modifies `agent-issue-loop`'s requirement at `openspec/specs/agent-issue-loop/spec.md:3481`,
  keeping its eight shipped scenarios verbatim and adding three.

## 9. Evidence

- [x] 9.1 `evidence/surface.txt` — the commit's own diffstat, and `evidence/gate.txt` — the suite line the commit
  reported and the reason no run's tally names this item.
- [x] 9.2 `evidence/anchors.txt` — every anchor this change cites, as the command and its output read from the base
  tree.
- [x] 9.3 `evidence/cases-and-mutations.txt` — the two cases, the two records, and the third arm no record pins.

## 10. What this change does not cover

- [x] 10.1 The derivation `bound_class_jq` makes of a record written before the two fields existed is recorded as
  uncovered: no case pins it, the delta's scenario for it says `Case: none`, and `cdf0648c` says "No case pins that
  path yet".
- [x] 10.2 The archive is left un-re-totalled: the 119 classless states the issue counts are still classless, and this
  change names that instead of rewriting them.
- [x] 10.3 The gate's two reds at the commit are recorded as not this change's and not fixed here —
  `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` and `phase#oneCallPrintsThePhaseState`, the second red on the base
  tree as well.
- [x] 10.4 `state.sh done --result cut` still accepts a `cut` with no key, on purpose: a bound no configuration records
  a value for is a legitimate record, so the door refuses a malformed key and not an absent one.