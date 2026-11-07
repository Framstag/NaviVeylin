# fix-the-tool-result-cap-is-8-000-characters

## Why

The item asks whether the tool-result cap should move: `tools/loop/loop.conf:372` records
`LOOP_TOOL_RESULT_MAX_CHARS=8000`, `tools/pi-child-events.sh:86` re-reads that line and exports it into every child it
starts (and `tools/loop/phase.sh:79` names it in the prompt a child is rendered), and an item whose results are cut at
it still reached the context bound — `.pi/issue-loop/items/791/`
carries 69 `LOOP-TOOL-RESULT-TRIM` lines that cut `817168` characters down to `552000`
(`grep -o 'chars=[0-9]*' .pi/issue-loop/items/791/tool-result-trims.log | cut -d= -f2 | jq -s 'add'` and the same over
`kept=`), and the item's own record of the item that paid for it — the run of 2026-11-07T06:33:29Z — reads
`metrics.context = 200104` against `LOOP_ITEM_CONTEXT_MAX=200000` (`tools/loop/loop.conf:192`), a figure this change
repeats from the item rather than re-measuring, the state that carried it having been replaced by the next run
(`.pi/issue-loop/run.json` holds this run, not that one). The one measurement that would price a tighter cap —
one run at another bound, judged against this baseline — has never been taken, and the reason it cannot be read
straight afterwards is that **no state records which bound its children ran under**: `state.sh init` wrote the
budget, the review rounds, the decision ceiling and the base into `.run`, and the three bounds every item figure is
read against stayed only in `tools/loop/loop.conf`, a line a person edits between runs.

**Root cause, in one sentence:** a run's recorded numbers do not carry the bounds they were measured against, so a
run made at another `LOOP_TOOL_RESULT_MAX_CHARS` — the experiment this item asks for — is indistinguishable from the
8 000 baseline once the configuration file has moved on, and an item's `context` cell is a number beside no stop.

**The red case, on the base `f47316cd`** — `tools/loop-selftest.sh --case
'state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst'`, 2026-11-07T15:12:58Z, run against the base engine with
the case as it now stands (`git diff --stat tools/loop/state.sh tools/loop/report.sh` empty at the time of the run):

```
state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst|   the record names the character bound its items' results were cut at: expected '8000', got 'null'
FAIL state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is that run transcribed into a `testsuite` (`tests=1 failures=1 errors=0`), because the
loop's shell runner writes no JUnit XML; `evidence/red-on-HEAD.log` is the runner's own output.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md` — added, "The numbers a run records name the bounds
they were measured against". It sits beside "A run is recorded" (`spec.md:104`), the scenario whose table already
carries an item's `context` column, and it owes that column the number it is read against.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:878 "Leftovers, the report and the queue" — the
paragraph "The report a run prints at its end is the run's product" (`:997`) owns what a run's figures are and what
they are read with, and now owes the sentence that the bounds travel with them.

surface: 5 files carry the rule (`./tools/look.sh 'toolResultMaxChars' --files` — 10 matches in 5 files after the
change: `tools/loop/state.sh` 3, `tools/loop/report.sh` 1, `tools/loop-cases.sh` 4, `tools/loop-mutations.sh` 1,
`guidelines/Backlog.recipes.md` 1; `./tools/look.sh 'itemContextMax' --files` — 7 matches in 4 files, the same
set without `tools/loop-mutations.sh` and with 2 in `tools/loop-cases.sh`. Both commands as run over the whole tree
report one more file and their own matches — `16 matches in 8 files` and `11 matches in 7 files` — because this
change's `proposal.md`, `design.md` and `tasks.md` name the keys too; the figures above are the rule files), 1 case added
(`tools/loop-selftest.sh --list | wc -l` → 356, 355 before), 1 mutation record added (`bash
 tools/loop-mutations.sh --list | wc -l` → 273, 272 before), 0 fixtures realigned, 1 requirement added with 1
scenario, 1 guideline section extended.

## What Changes

1. **`tools/loop/state.sh` reads the three bounds once, at `init`, from the configuration file the run resolved, and
   writes them into `.run`** — `toolResultMaxChars`, `itemContextWarn`, `itemContextMax` — each the number that file
   records, `0` when it records none (which is the "no bound" the run already reads). `init` prints them on a line of
   its own.
2. **`state.sh show` prints a `bounds` line** beside `budget` and `flags`, and `report.sh` names the same three in
   its header, so the report's table — whose `context` column is the largest context an item reached — carries the
   stop and the warning that column is read against.
3. **`tools/loop-cases.sh` asserts it**: `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` pins each key to
   the number `tools/loop/loop.conf` records rather than to a literal, and asserts both readers print it.
4. **`tools/loop-mutations.sh` record `95.1`** writes a result bound no configuration holds, so the case that names
   the invariant is red when the record stops following the configuration.

## What this does not do

- It does not move `LOOP_TOOL_RESULT_MAX_CHARS`, and it does not choose 4 000, 8 000 or any other value: the value
  stays the one line it is. **#815 still owes both halves of its named deliverable** — the one run at another
  `LOOP_TOOL_RESULT_MAX_CHARS`, and the assertion that pairs an item's reached `metrics.context` with the trim bound
  it was measured under. This change only leaves a record those two can be written against; it does not take the
  measurement, and the archive must not read the cap as priced. Whether the cap should move is the experiment's
  answer, not this change's.
- It does not count the items that reach `LOOP_ITEM_CONTEXT_WARN`/`MAX` — the rate #809 owns. It records the bounds
  that rate is read against and nothing else.
- It does not record these per item (`metrics`): the bound is one fact of the run, the same for every child it
  starts, and a copy per item is a second place for it to drift from.
- It does not make an inherited `LOOP_TOOL_RESULT_MAX_CHARS` win over the recorded one, and it does not read the
  bound from the shell variable at all: the adapter re-reads the file for every child it starts, so the file's line is
  the bound a run applies, and the experiment is an edit to that line for one run — which is exactly why the record
  has to name the number it applied. See `design.md`, "Alternatives".
