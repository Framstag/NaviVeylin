# Proposal — a bound-ended item names the bound it spent

**This change describes work that has already landed.** The whole of it is commit `cdf0648c` (its ref decor being
`pi-subagents/923-66e8498-93a2-s0-t0`), merged as `7af5d54c` and carried by the base
`929b9e8f` this delta is written on. Nothing here is a plan to write code: it is the record the change did not
have when it shipped ("Not done: … no OpenSpec change accompanies this diff"), written against what the tree now
does. Where the issue's own premise is older than the tree, the delta follows the tree.

## Why

**Root cause.** The class a bound leaves on an item was a word about the run and not about the item: `bound` said
the run ended it, never what it spent. `run.sh` wrote `stopReason: bound` from three arms that knew a child had
been ended, and nothing read the child's own `LOOP-TIMEOUT` line back for the key and the figure — so the record of
a cut item could be totalled by class and by nothing else. A review round the run itself ended recorded nothing at
all: the item was `cut` and its round left no field, so the rounds the run paid for and could not read were folded
into the items it threw away.

**Evidence, from the item (issue #923, read 2026-11-15).** `#898` and `#883` were `verdict: cut` with
`stopReason: null` although a bound ended both — `#898` at the reviewer's 110 turns, `#883` at
`LOOP_ITEM_CONTEXT_MAX=250000` with 250 418 recorded — while `#916`, `#871`, `#872` and `#878` in the same run
carried `stopReason: bound`; over the archive's 158 states the classes were `bound 6 / child-blocked 1 / refused 9 /
unclassified 119 / unmergeable 5`. The run's own bound rate was computed over four states and silently missed two of
the six. Part of that premise the tree has since overtaken: `#898`'s own landing (`f9f616c5`) records `bound` for a
review round the run ended, and the two no-class records the issue cites are what this change's own derivation is
for.

**The single fix, as it landed.** The class stops being a word the run chose and becomes a reading of the child's
own ending: `bound_key_value` (`tools/loop/run.sh:1252`) parses `<KEY> <value>` out of the `LOOP-TIMEOUT` line the
child wrote — the turn bound, the item's context bound or the check bound — and the four phases the spec names
(worker, gate child, tail, rework worker) write `boundKey`/`boundValue` beside `stopReason: bound`. A bound no
configuration records a value for keeps the class alone: the item's own clock, the run's deadline and a vanished
writer are written with no key and no figure (`run.sh:3927-3936`). `state.sh` grows `--bound-key`,
`--bound-value`, `--round-bound`, `--round-bound-key` and `--round-bound-value`, refuses a key that is not a
`LOOP_…` one and a figure that is not a number (`state.sh:790-806`), stores `boundKey`/`boundValue` beside the class
and the round's own ending as `metrics.roundBound: {round, key, value}` (`state.sh:947-955`).

**One derivation, not two.** `bound_class_jq` (`tools/loop/lib.sh:326`, declared shared in
`tools/loop/shared.tsv:19`) is the jq definitions `state.sh` and `report.sh` both read the archive through:
`bound_named`, `bound_round`, `bound_of` and `stop_class`. It reads a record written before the fields existed back
to the bound its own note names, telling a round from the item's own cut by the reviewer's role in the key and the
round's own verdict. On synthetic records it reads `#916` as `bound LOOP_CHILD_TURNS_MAX_WORKER=3` and `#898` as a
round ended at `LOOP_ITEM_CONTEXT_MAX_REVIEWER=200000`.

**The two figures stay apart.** `state.sh report` prints the round as a column appended after every column a reader
already quoted (`state.sh:342-345`), so a table read by position still reads; `report.sh` counts the rounds a bound
ended on a line of its own (`report.sh:323`, `:342`) beside the item rate it does not replace (`:290`) — the class
answers how many items the run threw away, the round line answers how many rounds it paid for and could not read.

**The red tests.** Two cases, both written red against the engine that preceded the change: `run#aBoundEndedItemRecordsItsBound`
(`tools/loop/tests/loop-cases.sh:7672`) and `run#aRoundTheRunEndedRecordsItsBound` (`:7738`), with mutation records
`923.1` and `923.2` (`tools/loop/tests/loop-mutations.sh:1165`, `:1166`) that empty the key each case reads.

**Owning requirement.** `openspec/specs/agent-issue-loop/spec.md:3481`, "An item the run's own bound cut is recorded
`cut`, not `blocked`" — owned by `agent-issue-loop`; the delta modifies it and keeps its eight shipped scenarios
verbatim.

**Owning guideline section.** `guidelines/Backlog.recipes.md` §7e, whose bound paragraph this change extends with
the key, the figure and the round's own field.

## Surface

surface: 8 files, +398 −21 (`cdf0648c`'s own diffstat, in `evidence/surface.txt`): `tools/loop/run.sh` +76,
`tools/loop/state.sh` +89, `tools/loop/lib.sh` +43, `tools/loop/report.sh` +21, `tools/loop/shared.tsv` +1,
`tools/loop/tests/loop-cases.sh` +157, `tools/loop/tests/loop-mutations.sh` +12, `guidelines/Backlog.recipes.md`
+20. Two cases added and one realigned (`report#wallTimeAndReasonInTheTable`), one shared-name row added
(`driver#theSharedNamesAreDeclared`), two mutation records, one requirement modified with three scenarios added.

## What Changes

1. **`bound_key_value`** (`run.sh:1252`): one reading of the child's `LOOP-TIMEOUT` line, into the key the bound is
   recorded in and the figure it reached, and nothing where the line names no bound.
2. **Four phases write it** — the worker, the gate child, the tail and the rework worker — so a bound-ended item
   carries `stopReason: bound` with `boundKey`/`boundValue`, and a bound with no configured value carries the class
   alone.
3. **A review round the run ended** writes `metrics.roundBound` with the round (`1`, or `pre-gate`), the key and the
   figure, and leaves the item's `stopReason` empty — the item's class is not the round's.
4. **`state.sh`** accepts and shapes the five flags, stores the two fields, and reads a record written before them
   through the same derivation.
5. **The table and the report**: the round column after the existing columns, and the round count on a line of its
   own beside the item rate.
6. **Two cases, two mutation records, the guideline and the delta** as above.

## What this does not do

- It does not re-total the archive. The commit says so itself, and the 119 classless states the issue counts are
  still classless; the derivation reads the ones that carry a note and leaves the rest as they are.
- It does not pin the derivation with a case. `bound_class_jq`'s reading of a record written before the two fields
  existed is asserted by no case — `cdf0648c`'s own message says "No case pins that path yet" — and the delta's
  scenario for it says `Case: none` rather than claiming one.
- It does not make a key mandatory. A bound the configuration records no value for is a legitimate record with the
  class alone, and `state.sh` accepts it: the door refuses a malformed key, not an absent one.
- It does not fix the gate's own two reds: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` (green alone, red under
  the host's parallel suites) and `phase#oneCallPrintsThePhaseState` (red on the base tree too). Both are named in
  the commit's suite line and neither is this change's.