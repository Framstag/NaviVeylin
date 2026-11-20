# A round the input cap cut is counted beside the rate

## Why

`LOOP_REVIEW_INPUT_MAX_BYTES` bounds each of the three parts a review round is handed, and what it cuts is written
down twice already: the round's record carries the scale (`metrics.reviewInput`, `tools/loop/state.sh:1033`, written
by `done --review-input`) and the report prints it against the item (`tools/loop/report.sh:365`, `what the bound cut
from a round's input`). The trend's **rate** did not read it: the base's line (`tools/loop/report.sh:517`) totalled
a round's verdict and its count and nothing about what the round was handed, so a round that decided on 59 % of its
diff and a round that read all of it print the same figure.

The run of `2026-11-15T19:32:41Z` is the measurement (`LOOP-MEASURED`, the item's evidence): `#899` had 1 of 1 parts
over the 32 000 B cap, 46 649 of 78 560 B cut (59 %), `#878` 1 of 2 parts, 46 276 of 87 437 B cut (53 %), `#886` 29 %,
`#885` 27 %, `#930` 15 %, `#881` 9 % — six of the run's eleven items had a truncated round — and the same report
printed `review: 5/5 first rounds passed` and read as a clean first-pass record. `#878` closed on round 1 with 47 % of
its own diff in the reviewer's hands.

**Root cause** — the trend's rate is counted from fields of the item's record and never from the scale the round's
own bound recorded, so nothing a cut round says about itself reaches the one line a reader quotes as first-pass
accuracy.

**Red test** (2026-11-20T07:18:41Z, `red-on-HEAD-936.log` in `.pi/issue-loop/items/936`, transcribed as this
change's `evidence/red-on-HEAD-aCutRoundIsCountedBesideTheRate.xml`): `report#aCutRoundIsCountedBesideTheRate` — three
items, three round-1 passes, two of them recording the scale the cap measured — fails on the base with
`loop-selftest: 1 of 1 cases failed`, `tests=1 failures=1`:

```
the rate names how many of the run's rounds were handed a part the cap cut …: 'review: 3/3 first rounds passed
(2 truncated)' not found in: trend — this run's own items; no rate is green or red in one run:
  review: 3/3 first rounds passed · 0 undecided · 0 never reviewed
```

The base's own line is the defect: two of those three rounds were handed the head of their part and the rate says
nothing about it.

**Owning capability**: `agent-issue-loop` — "The report states whether the loop is getting better"
(`openspec/specs/agent-issue-loop/spec.md:3162`), whose scenarios already count the rate from the round's recorded
verdict rather than its count.

**Owning guideline section**: `guidelines/Backlog.recipes.md:1534` "Leftovers, the report and the queue"
— the paragraph at `guidelines/Backlog.recipes.md:1808` that enumerates what the rate tells apart ("four things are told apart and
printed") and what the run's figures do not show.

**Surface** — `tools/look.sh 'first rounds passed'` (26 matches in 15 files: `report.sh` 1, `loop-cases.sh` 5,
`loop-mutations.sh` 2, the rest archived changes and their evidence) and `tools/look.sh 'reviewInput'` (25 matches in
13 files: `report.sh` 3, `state.sh` 1, `loop-cases.sh` 4, `guidelines/Backlog.recipes.md` 1, the spec 2, the
rest archived) name the places that
assert the rule. The scale itself is not this change's work: #924 recorded it and prints it per item, and the only
surface left was the trend's rate.

surface: 1 case added (`report#aCutRoundIsCountedBesideTheRate`), 0 rewritten, 0 fixtures realigned, 1 mutation
record added (`936.1`), 1 guideline paragraph realigned (`guidelines/Backlog.recipes.md:1808`) —
`tools/look.sh 'first rounds passed'`;
`tools/look.sh 'reviewInput'`

## What changes

1. `tools/loop/report.sh` — the trend's `$review` line counts the items whose record carries a cut scale
   (`metrics.reviewInput`) and prints that number in parentheses after the pass rate: `3/3 first rounds passed
   (2 truncated)`. A run no cap cut prints the line it always printed — an empty record is no cut, and `0 truncated`
   would be a measurement nobody made.
2. `tools/loop/tests/loop-cases.sh` — `report#aCutRoundIsCountedBesideTheRate`, one case over the state alone: two
   cut items beside one uncut, then a run whose parts were all under the cap.
3. `tools/loop/tests/loop-mutations.sh` — record `936.1` empties the figure that names the cut rounds, so the case
   goes red on the rate losing it.
4. `guidelines/Backlog.recipes.md:1534` "Leftovers, the report and the queue" — the paragraph that says what
   the rate tells apart names the count and the
   case that holds it.
5. `openspec/changes/fix-a-cut-round-is-counted-beside-the-rate/specs/agent-issue-loop/spec.md` — one scenario added
   to "The report states whether the loop is getting better", and a sentence of that requirement stating the figure.

## What this change does not do

It does not change what a round is handed, nor the record `run.sh` writes: the scale is #924's and is read here
unchanged. It does not count cuts per round, only per item, because the record keeps one scale per item — the scale
of the round the item ended on.
