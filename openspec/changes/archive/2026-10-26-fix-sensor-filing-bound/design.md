# Design — fix-sensor-filing-bound

## The root cause

`sensors_outcome_violation`'s first branch is the whole of a first filing (`tools/sensors/run.sh:455` on the base):

```bash
  issue=$(sensors_issue_for_fingerprint "$issues" "$fp")
  if [ -z "$issue" ]; then
    sensors_file "$row" "$id" "$finding" "$baseline"
```

The runner enters that branch once per line the row's command printed
(`tools/sensors/run.sh:571-584`, `while IFS= read -r line; do … sensors_outcome …; done`), and nothing between the
two places counts what the run has written. A row's only bound today, `consecutive`
(`tools/sensors/run.sh:492`, `:529`), is consulted on the reopen and clear paths — so it bounds a *condition's*
history, never the *run's* output. The number of items one tick writes is therefore the number of conditions the
row's bound happens to hold, which is the item's 337.

## The single fix

One bound, applied where the filing happens, and one item for what it holds back.

1. **The bound is declared, not named by the engine.** `sensors_row_max_filings <row>` reads the row's own
   `maxFilings`, else the registry's `.maxFilings` (design D12 — the engine names no project value, exactly as
   `threshold`, `consecutive` and `escalation.labels` arrive from the registry). A registry that declares none, and
   a row that declares a value that is not a positive count, are refused by name (`registry-max-filings-missing`,
   `registry-max-filings-not-a-count`, `row-max-filings-not-a-count`) instead of being read as "no bound" — the
   pattern `record.status` already follows (#257).
2. **Counted at the one place that files.** `sensors_run_row` resets `SENSORS_FILED`/`SENSORS_UNFILED` per row and
   reads the bound; `sensors_outcome_violation`'s absent-issue branch files while the count is below the bound and
   otherwise appends the finding to `SENSORS_UNFILED` and reports it as `not filed`. An annotation, a reopen and a
   comment are not filings and spend nothing, which is what makes the bound a bound on the *tracker's* growth
   rather than on how much a check reports.
3. **What is held back leaves as one item.** After the loop, `sensors_summary_outcome` files the row's one summary
   when anything was left over, annotates it while the row stays over, reopens it if a closure exists, and closes it
   when a run leaves nothing over. The conditions are listed in the order the check reported them — the check is its
   own ranker (`hotspot` scores size × changes, `source-size` prints descending) — so the worst offenders become
   items and the tail is named. Every one of those writes checks the writer's exit code and fails the run when the
   tracker refused it: a closure or a comment the run did not make must not be reported as one it did — the class
   mutation `8.25` and the case `run#aSecondOverBoundRunListsWhatItDidNotFile` pin.
4. **The summary is not a condition of the check's.** Its identity is the engine's own digest
   (`{"sensor": <id>, "kind": "filing-summary"}`), its body carries `<!-- filing-summary: <id> -->`, and
   `sensors_conditions_json` excludes that marker the way it excludes the row's record issue (#257): a sensor reads
   the conditions *it* reported, and a summary is the run's report about itself. It also carries **no measurement
   marker**, and neither its filing nor its annotation posts one — `report.sh` reads a row's last measurement from a
   marker comment (`tools/sensors/report.sh:47-57`), so a marker carrying the count of unfiled conditions would
   become the baseline a `baseline-multiple` row compares against. The count is the run's own arithmetic, not a
   measurement of the row. The list an item carries is bounded at twenty identities with the rest named by number,
   because a run can hold any number of conditions and GitHub refuses a body past 65 536 characters — the count is
   the measurement and the list the evidence.
5. **The summary closes on the run that leaves nothing over, not after `consecutive` clears.** Hysteresis exists
   because a measurement can flap around a bound; the count of what one run did not file cannot — it is the run's own
   integer. Closing on the first such run is what keeps the tracker from holding a permanent "something was hidden
   here once" item. A closure a person made is not reopened: the summary path reads the closure reason exactly as
   the condition path does (design D6) and annotates instead, because `reopen.sh` refuses a not-planned closure —
   without that check the report would claim a reopen that never happened.

## Why not the alternatives

- **The cap refuses the row (`sensors_fail_row`) and writes nothing** — the item's second candidate. Eliminated: a
  failed row writes nothing at all, so the conditions the run measured leave no trace, and "the check determined
  nothing" is the exact reading *The check itself failed* exists to keep apart from a measured condition. 337
  conditions would become one `error` line and no item, which is cheaper for the tracker and dishonest about the
  measurement.
- **Cap per condition identity over the row's lifetime (a `consecutive`-like counter in the issue body).** Eliminated:
  the mechanism the item reports is one run's volume, and a lifetime counter cannot bound a first tick — every
  condition in it has a lifetime count of zero. The two bounds answer different questions, and this change adds only
  the missing one.
- **A cap in the workflow (`tools/issues/duplicates.sh --max-items`-style step) instead of in the engine.** Eliminated:
  that is the brake that did run on the item's tick, and it closed items by a rule about duplicates rather than about
  the run's volume — a downstream sweep can only withdraw items, never stop the run from writing them or name what it
  suppressed. The engine is where the write happens.
- **Ranking the overflow by a metric the engine computes (largest measurement first).** Eliminated: the engine holds
  no unit, so ranking by `measurement` would compare a file's line count with a module's coverage percentage. The row
  already ranks its own findings — it is the only thing that knows the unit — so the engine keeps the order it was
  given and files the head of it.
- **A bound on the row's total writes including annotations.** Eliminated: a condition that is still present must keep
  recording its measurement on its item, otherwise the run's own evidence stops arriving for conditions that are
  already known — and an annotation cannot make the tracker grow.

## Blast radius

- `tools/sensors/lib.sh` — one accessor, two refusal reasons (registry, row).
- `tools/sensors/run.sh` — the absent-issue branch, the per-row bound and counters, four small functions for the
  summary and one comment helper; `sensors_file` gains an optional body.
- `tools/sensors/report.sh`'s input (`sensors_conditions_json` in `lib.sh`) — one exclusion clause.
- `tools/sensors/sensors.json` — `maxFilings: 10`, the project's value; five fixtures realign or are added.
- `tools/sensor-cases.sh` — seven cases, one helper, one helper tightened.
- `tools/sensor-mutations.sh` — five records, one per new invariant.
- `tools/fixtures/foreign-project/tools/sensors/sensors.json` — the portability fixture's registry gains
  `maxFilings`: a registry without it is refused, and the portability cases run `run.sh` on that row.
- `tools/build-inputs.sh` — one bit (its executable bit), because the profile's gate mapping invokes the script
  directly while CI prefixes `bash`, and this change touches the registry that step reads.
- `guidelines/Backlog.md` §9, `PORTING.md` — the paragraph and the key.
- The spec delta. No compiled file, so no Gradle suite is owed by these paths.

## Rollback

Revert the branch. A tree whose registry carries no `maxFilings` is refused rather than run unbounded (deliberately:
a silent fallback is how the 337-item class would come back), so the rollback also restores the four fixture
registries — the diff carries all of it, and no tracker state, tally or report cell changes shape.

## Risks

- **A row legitimately over its bound files its head only.** A red build with more failing cases than the bound files
  the first ten and names the rest in one item; the tail keeps being named there rather than getting items of its
  own. That is the trade the item asks for, and retuning the row's `maxFilings` is one edit.
- **One more item on the tracker per row that is over its bound.** It is filed once per row (its identity is the
  row's), not once per run.
