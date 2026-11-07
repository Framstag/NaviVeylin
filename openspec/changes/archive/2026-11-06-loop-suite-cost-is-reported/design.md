# Design — a cost that is paid where it is invisible is a cost nobody owns

## Root cause

Every figure the suite could report exists while it runs and is thrown away. `start_case` records a slot's start
(`tools/loop-selftest.sh:498`) and `finish_case` clears it (`:560`); the only reader is the watcher's token, which
needs the value to recognise a slot it no longer owns. The summary (`:771`) states the count, the JDK feature and
the selection — nothing about time. A group's cost therefore exists only as the difference between two timestamps
a person reads in the GitHub UI, per step, and never per group.

The consequence is visible in the case file: **166 → 314 cases in twelve days** (`tools/loop-cases.sh`,
`grep -c '^case_'`), each addition reviewed for what it asserts and by nobody for what it costs. The number the
guidelines quote (221 cases, 4 min 24 s, 2026-10-25) is a hand-run measurement, and it was already 41 cases and
one growth step stale when it was written.

## The fix, and the trim it prices

1. **Report.** The summary gains one line per group and one for the selection:

   ```
   loop-selftest: state 24 cases 39.47 s | report 20 cases 29.54 s | prescreen 30 cases 32.96 s | …
   loop-selftest: all 24 cases passed (JDK 27, offline, no token, no sandbox) — 54.96 s, selection: --group state
   ```

   `evidence/groups-before.txt`, the runs' own summary lines. The `PASS` line is unchanged; `--times` adds a
   per-case figure for the case being examined. The gate's tally already records each suite's wall clock beside its
   counts at this base (`tools/loop/gate.sh`), so the figure lands beside the gate's own; this change writes no line
   of that file.

2. **Trim, priced and rejected.** The item's Scope named three groups whose cases looked expensive — `state` (24),
   `report` (20), `prescreen` (30): 74 cases paying 101.97 s — on the premise that each builds a prepared project per
   case and that the setup is the cost. Measured, that fixture is 13 ms of a 1.10–1.64 s case — 0.8–1.2 % — so
   merging all 74 into as few as one per fixture removes 73 setups and saves 0.95 s of those 101.97 s, 0.9 %, for 74
   rewritten case bodies (`evidence/trim.txt`). No case was merged; `evidence/merged-cases.txt` records the empty
   mapping. The merge is a change of its own size and its own risk, and the report is what lets that change aim at
   the group that actually pays.

## Why not the alternatives

- **Compare against a stored baseline and fail the run.** The project's own measurement says no: the same gate
  measured 8m25s, 9m46s and 5m45s on three occasions, and the same 5 680 tests took 3m47s on a quiet host and
  spent 30 minutes at 194 MB free RAM (`guidelines/Backlog.recipes.md`:458, the measurement behind `LOOP_MIN_FREE_MIB`). A
  baseline's verdict is a verdict about the host.
- **Time each case always.** The `PASS` line is read by the mutation harness's records and by the leftovers cases,
  which invoke the driver and parse what it prints; a figure in the middle of that line is a change to a
  contract three callers depend on, for a number a reader needs only while investigating.
- **Put the figures in the state file instead of the summary.** The state file is a run's record; the suite's log
  is what a person and the gate read. `guidelines/Backlog.recipes.md`:458 "The gate and its suite" already states that a suite's numbers are the
  suite's own — the summary is that place.
- **Trim without reporting.** The merge the Scope named was priced and skipped — 74 bodies for 0.9 % of the 101.97 s
  they pay (`evidence/trim.txt`) — so the question this alternative answers is "why measure first": a trim taken
  without a figure would have been 74 rewritten bodies aimed at what a fixture looked like it cost, and "it got
  faster" is not a measurement either before or after the edit.

## Blast radius

- Every caller that reads the summary reads one line more; the summary's last line keeps its shape and its counts,
  so a parser that reads the counts is unaffected.
- No case is merged, so nothing in the case set is renamed and no mutation record is re-pointed:
  `evidence/merged-cases.txt` records the empty mapping. The mutation set gains the two records this change's cases
  need (`85.1` the per-group accumulation, `85.2` the `--times` gate), and `bash tools/loop-mutations.sh --check`
  proves the whole set still applies.

## Open decisions

- **The trim is skipped, recorded here as a decision and not as an omission.** 74 case bodies for 0.9 % of their
  101.97 s (`evidence/trim.txt`): the fixture the plan blamed is 0.8–1.2 % of a case, so the merge is a change of its
  own, and this change's report is what a later trim aims with.
