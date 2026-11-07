# The suite reports what it cost, and a trim is priced against that number

## Why

The loop's self-test grew from **166 cases on 2026-10-22 to 314 on 2026-11-03** — measured with
`git show <sha>:tools/loop-cases.sh | grep -c '^case_'` over the commits that touched the case file:

| date | commit | cases |
|---|---|---|
| 2026-10-22 | `cf86e545` | 166 |
| 2026-10-25 | `6bf78d3f` | 236 |
| 2026-10-29 | `cffc710d` | 284 |
| 2026-11-01 | `650028bf` | 309 |
| 2026-11-03 | `064b7254` | 314 |

Nothing in the suite reports that number as a cost. `--list` names cases, the summary counts them, and the driver
records when a case started (`case_slot_started`, `tools/loop-selftest.sh:498`) only to stop a stale bound's
watcher — the elapsed time of a case, of a group and of the selection is printed nowhere. The one figure this
project has for the suite's duration is a hand-run measurement that quotes a case count three growth steps old
(221 cases, 4 min 24 s, 2026-10-25, `guidelines/Backlog.md`:276 "The gate and its suite"), against 312 cases paying **17 min 15 s** on the
central runner today.

**Root cause, in one sentence.** The suite's cost is invisible at the place it is paid, so adding a case costs the
author nothing visible, and the number everyone quotes was measured once by hand and then aged.

## Why the trim the item's Scope named is not in this change

A report with no bound is a number nobody acts on, so the scope priced a trim of the three groups whose cases looked
cheapest per case — `state` (24 cases), `report` (20), `prescreen` (30), 74 cases paying 101.97 s — and the
measurement refused it before a case was edited. The fixture the plan blamed (`new_project`, `git init`, the prepared
project) is 13 ms of a 1.10–1.64 s case, 0.8–1.2 % of it, so merging all 74 into as few as one per fixture removes
73 setups and saves 0.95 s of those 101.97 s — 0.9 % — for 74 rewritten case bodies (`evidence/trim.txt`). No case is
merged, nothing was re-pointed, and `evidence/merged-cases.txt` records the empty mapping. The trim is a change of its
own size and its own risk, aimed with the report this change lands at the group that actually pays.

surface: 1 requirement added with 3 scenarios, 2 cases added to the `driver` group, the summary extended with one
line per group, 1 guideline section extended — `bash tools/loop-selftest.sh --list | cut -d'#' -f1 | sort | uniq -c`
for the counts and `bash tools/loop-selftest.sh --group state --jobs 1` for a group's cost.

## What Changes

- **`tools/loop-selftest.sh`** — the summary carries the elapsed time of each group and of the whole selection
  (whole seconds, one line per group), and a per-case figure only when the invocation asks for it
  (`--times`), because the `PASS` line's shape is read by the mutation harness and by the leftovers cases.
  `case_slot_started` becomes a record, not only a token.
- **`tools/loop-cases.sh`** — two cases added to the `driver` group: `driver#theSummaryNamesWhatEachGroupCost`
  (a two-group stub suite whose summary must name each group's cost and count, and the selection's own figure) and
  `driver#theTimesFigureIsAskedForNotAssumed` (the `PASS` line's shape with and without `--times`). No case is merged.
- **`guidelines/Backlog.recipes.md`**:458 "The gate and its suite" — the paragraph that states the suite's cost gains the report and the rule
  that a group's cost is read before cases are added to it.

## What This Does Not Do

- It does not compare a run against a stored baseline. The measured baselines this project has disagree by minutes
  on the same host (`guidelines/Backlog.recipes.md`:458: 8m25s, 9m46s, 5m45s for the same gate), so the report is this
  run's own measurement and never a verdict.
- It does not add the per-case figure to a passing case's line. Two suites of cases read that line.
