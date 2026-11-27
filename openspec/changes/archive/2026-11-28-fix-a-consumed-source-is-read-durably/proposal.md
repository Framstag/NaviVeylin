# Fix: a consumed change source is read from the record that outlives the run

## Why

The arm that removes the source directory a landed item consumed reads *the current run's* state file, and a run's
state does not outlive the run. Measured against the tree at
`856269d96fd25c7831cbd489b118251d2563ff2c` (issue #1114):

- `tools/loop/loop-clean.sh:306` builds the arm's list with `jq` over `$LOOP_STATE_FILE`:
  `.changeCopies` entries filtered by the issues `.items` records as `done`. Both keys belong to one run.
- `tools/loop/state.sh:311` — `init` writes `items:[]` and no `.changeCopies` at all, so the next run's state names
  none of the previous run's copies. `.pi/issue-loop/run.json` in this project carries no `changeCopies` key today.
- The record that does outlive the run is the item's own `change-copy.json` beside it:
  `tools/loop/run.sh:1179-1181` copies `.changeCopies[$issue]` into `items/<issue>/change-copy.json` before the
  worker starts. `.pi/issue-loop/items/1042/change-copy.json` and `.../1043/change-copy.json` still name
  `openspec/changes/the-displayed-viewport-is-one-projection` and `.../the-car-displayed-viewport-is-one-projection`.
- The item's result outlives the state in the run's event record: `run.sh:2937` emits
  `item-result issue=… result=…`, and `.pi/issue-loop/run-events.jsonl` holds `"kind":"item-result","issue":…,
  "result":"closed"` for every item a run closed (37 such lines today, 105 item results in all).
- Measured 2026-11-27 in this tree: eight sources sit in `openspec/changes/` whose
  `openspec/changes/archive/<date>-<same name>/` twin is complete and whose own tasks are all unchecked
  (`the-displayed-viewport-is-one-projection` 0 of 9, `the-car-displayed-viewport-is-one-projection` 0 of 10,
  `the-loop-core-exists-and-compiles` 0 of 21, and five more). `openspec list --json` reports each as in-progress,
  `tools/project-metrics-report.sh:135` counts them as open changes, and `openspec validate --changes` validates
  them. The sources are gitignored (`.gitignore:82-83`), so no `git status` shows them either.

Root cause, one sentence: the arm reads the one record a run replaces (`$LOOP_STATE_FILE`) and never the two that
outlive it — the item's own `change-copy.json` and the run's `run-events.jsonl` — so a source consumed by a past run
is named by nothing after that run's state is gone.

Red cases, on the base commit — `clean#aConsumedSourceIsReadDurably` and `clean#aChangeWorkedInSlicesIsKept` both
fail because nothing but the current run's state is read (`evidence/red-on-base.log`, `evidence/red-on-base.xml`,
2026-11-27T23:16:12Z, tests=2 failures=2):

```
$ tools/loop/tests/loop-selftest.sh --case clean#aConsumedSourceIsReadDurably
  the durable record still names the consumed source: no line matching /would remove openspec/changes/add-prepared-change \([0-9][0-9]* B\) — the change #31 consumed/
FAIL clean#aConsumedSourceIsReadDurably
$ tools/loop/tests/loop-selftest.sh --case clean#aChangeWorkedInSlicesIsKept
  the change the next slice is prepared from is kept: 'kept openspec/changes/add-prepared-change' not found
FAIL clean#aChangeWorkedInSlicesIsKept
```

The capability the delta belongs to is `agent-issue-loop`, whose requirement
"The consumed change is cleaned from the project's tree, and the run does not write it there" already says the clean
command reports the source; what it did not say is which record names it, and that is the clause this change adds.

Route: `LOOP-ROUTE: guidelines/Backlog.md §6` — the paragraph on the three facts about a tier-B change
("the source directory a landed item consumed is the clean command's to report and remove"), which now names the
record the clean command reads and the reading that keeps a change worked in slices.

Surface, counted before the plan was fixed: `bash tools/look.sh 'changeCopies' --files` — 12 matches in 5 files
(`tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/loop-clean.sh`, `tools/loop/tests/loop-cases.sh`, one
archived proposal). The arm's own vocabulary is `consumed_source`, 11 references, all in `tools/loop/loop-clean.sh`.
So: 2 cases added, 0 rewritten, 0 fixtures realigned; one production file, one requirement, one guideline paragraph.

## What it costs

One file of production code (`tools/loop/loop-clean.sh`), two cases and their two registry rows in
`tools/loop/tests/loop-cases.sh`, one modified requirement, one guideline paragraph. No compiled file, so no build
task stands in this change's place: the gate is the loop's self-test.

## What it does not do

It does not `--apply` on this project's own tree. The eight sources this project holds are reported by the new
reading and removed by the next `tools/loop/loop-clean.sh --apply` a person runs; a change that deletes eight
directories of another item's evidence is not this one.
