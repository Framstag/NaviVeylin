# Proposal — fix-sensor-filing-bound

## Why

One defect, verified in this tree, issue #666: **nothing bounds how many items one run of one check may file, so a
mis-fitted bound spends a whole tick's write budget in one run.**

**Root cause (one).** The violation path files an item for every condition whose fingerprint the tracker does not
already carry — `tools/sensors/run.sh:455` on the base (`if [ -z "$issue" ]; then sensors_file …`) — and the runner
reaches it once per line its command printed (`tools/sensors/run.sh:571-584`, the `while IFS= read -r line` loop in
`sensors_run_row`). Nothing between those two places counts what the run has written: `consecutive`, the one bound a
row carries today, is read only on the *reopen* path and on the clear path
(`tools/sensors/run.sh:492`, `:529`), so a first filing is unconditional. The only brake that ran on the item's own
tick was downstream and belonged to another tool — `tools/issues/duplicates.sh --max-items 25` in the workflow's
hygiene step, closing items by a rule about duplicates, not about this run's volume.

**Verified, not quoted from the tracker.** Measured in this tree on the base commit `f568b1e6`:

```
$ git show f568b1e6:tools/sensors/run.sh | grep -c 'MAX_FILINGS'
0
$ git show f568b1e6:tools/sensors/lib.sh | grep -c 'maxFilings'
0
$ git show f568b1e6:tools/sensors/run.sh | sed -n '450,459p'
sensors_outcome_violation() { # <row> <id> <finding> <issues> <baseline>
  ...
  issue=$(sensors_issue_for_fingerprint "$issues" "$fp")
  if [ -z "$issue" ]; then
    sensors_file "$row" "$id" "$finding" "$baseline"
```

The item's own field evidence — `337` items (#321–#657) from one row in 36 minutes, tick `07:41:51Z` of 2026-10-26,
run `40287372367` — is the tracker's record of that mechanism firing; this change measures the mechanism, which is
what a host test can settle.

**The red tests** — five cases that fail on the base, because no bound exists to make them true, and two that pin
what the fix must not introduce. Log `evidence/red-on-head.log`, tallies beside it, run 2026-10-26T18:13:26Z:

- `run#aRowPastItsBoundFilesItsBoundAndOneSummary` — `expected '2', got '4'` (the bound's worth of condition
  items is not filed; every condition is), `tests="1" failures="1"`:
  `evidence/red-on-head-aRowPastItsBoundFilesItsBoundAndOneSummary.xml`
- `run#aRowDeclaresItsOwnBound` — `expected '3', got '5'`, `tests="1" failures="1"`:
  `evidence/red-on-head-aRowDeclaresItsOwnBound.xml`
- `run#theSummaryClearsWhenTheRowIsBackUnderItsBound` — `expected '1', got '0'` (nothing closes a summary, because
  nothing files one), `tests="1" failures="1"`:
  `evidence/red-on-head-theSummaryClearsWhenTheRowIsBackUnderItsBound.xml`
- `run#aRowUnderItsBoundFilesNoSummary` — green on the base by construction: it asserts an *absence* ("no summary
  is filed when nothing was left over"), which is the half that keeps the bound from over-firing. Its evidence is
  the mutation: `8.24` makes the summary fire with nothing left over and reddens it.
- `run#aFailedCheckDoesNotClearTheSummary` — green on the base for the same reason (nothing files a summary, so
  nothing can close one); `8.26` makes an errored run clear the summary and reddens it.
- `run#aSecondOverBoundRunListsWhatItDidNotFile` and `run#aSummaryClosedByAPersonIsNotReopened` fail on the base on
  their first assertion (`expected '2', got '3'`), because without a bound every condition is filed and no summary
  exists; the half that pins each rule is falsified by `8.27` and `8.25`.

**Owning capability and guideline section.** The behaviour is a new requirement of `backlog-sensors`, beside *A
check reports one of three outcomes, and only clearing closes* — the requirement whose "file, or reopen the item
this condition already has, or annotate it" line is the path this bound sits on
(`openspec/specs/backlog-sensors/spec.md:32`). The prose that owns a check's declaration and its bounds is
`guidelines/Backlog.md` §9 "Checks" (`guidelines/Backlog.md:709` lists what a row declares); it gains the paragraph
that names this bound, and `PORTING.md:50` — the registry's own description of what a project declares — gains the
key. No guideline sentence is rewritten.

surface: 7 cases added, 0 rewritten, 1 case helper realigned (`condition_creates` stops counting a summary as a
condition), 5 mutation records added (8.23–8.27), 1 requirement added with 7 scenarios, 6 registry fixtures
realigned (the four existing ones gain `maxFilings`, the portability fixture included) plus 1 new fixture registry —
the counts are
`bash tools/sensor-selftest.sh --list | wc -l` (61 declared cases, 54 on the base), `grep -c "^  '8\." tools/sensor-mutations.sh` (22 records on the base, 27 after),
`git ls-tree --name-only <base> tools/sensors/fixtures/` (4 registry fixtures on the base, 5 after) and for the
fixtures
`grep -n "recorded \"\$log\" 'issue create'\|condition_creates\|record_creates" tools/sensor-cases.sh` —
`evidence/surface-head.log`.

## What changes

- `tools/sensors/lib.sh` — `sensors_row_max_filings <row>`: the row's own `maxFilings`, else the registry's
  `.maxFilings`. `sensors_row_problems` refuses a row whose declared count is not a positive count
  (`row-max-filings-not-a-count`), and `sensors_registry_problems` refuses a registry that declares none
  (`registry-max-filings-missing`) or a value that is not one (`registry-max-filings-not-a-count`). The engine names
  no number (design D12): the value is the registry's.
- `tools/sensors/run.sh` — `sensors_run_row` reads the row's bound, refuses it when it is not a positive count (the
  direct-run half of the guard's check, as `record.status` already has), counts its new filings, and past the bound
  collects the finding instead of filing it and reports it as `not filed`. After the loop it calls
  `sensors_summary_outcome`, which files, annotates, reopens or closes the row's one summary item — annotating
  instead of reopening when a person closed it as anything but a completed fix, the rule the condition path already
  follows. `sensors_outcome_violation`'s absent-issue branch consults the count; `sensors_file` takes an explicit
  body, which is how the summary carries its list.
- `tools/sensors/report.sh`'s input — `sensors_conditions_json` excludes the summary by its own marker exactly as it
  excludes the row's record issue, so no sensor reads a summary as a condition it may clear.
- `tools/sensors/sensors.json` — `"maxFilings": 10`, the project's declared bound. 10 because the largest number of
  conditions any row files in one run today is two (`run#sensors_brokenMainFingerprintDistinguishesCauses`), and a
  bound that a red build's per-case items can still fit under is the point; the number is one edit in this file, like
  `source-size`'s 1 500.
- `tools/sensors/fixtures/` — the four existing registry fixtures gain the key (it is required), and
  `registry-bounded.json` is new: the registry's default 2, a row declaring 3, and a row declaring 0.
  `tools/fixtures/foreign-project/tools/sensors/sensors.json` gains `maxFilings: 3` too — it is a registry the
  portability cases run `run.sh` against, so without the key that row is refused.
- `tools/sensor-cases.sh` — the seven cases above, in the declared set and the dispatch table, and `summary_creates`
  beside `record_creates`/`condition_creates`.
- `tools/sensor-mutations.sh` — `8.23` (the bound never consulted), `8.24` (the summary fires with nothing left
  over), `8.25` (a person's closure is reopened), `8.26` (an errored run clears the summary) and `8.27` (the
  annotation names no identities), one per new invariant.
- `guidelines/Backlog.md` §9 and `PORTING.md` — the paragraph and the key.
- `tools/build-inputs.sh` — its executable bit. The profile's gate mapping invokes this script directly
  (`tools/loop/loop.conf`, `tools/build-inputs*|tools/build-inputs.sh --check`) while CI prefixes `bash`, so on a
  tree where the bit is missing that step is red for any change touching `tools/sensors/sensors.json` — this one
  does. One bit, no content: `./tools/build-inputs.sh --check` prints `every declared input has a producer (4 rows
  checked)` and exits 0.
- `openspec/specs/backlog-sensors/spec.md` — one added requirement, four scenarios.

## Capabilities

### Modified Capabilities

- `backlog-sensors`: adds *A row's filings in one run are bounded* — the bound a check declares over its own output,
  the one summary item that names what it did not file, and the clearing of that summary.

## Impact

`tools/sensors/lib.sh`, `tools/sensors/run.sh`, `tools/sensors/sensors.json`, `tools/sensor-cases.sh`,
`tools/sensor-mutations.sh`, five registry fixtures, `guidelines/Backlog.md` §9, `PORTING.md`, the spec delta, and
one bit on `tools/build-inputs.sh`. No compiled file, so no Gradle suite is owed by these paths; the gate's own plan
for this pathspec (`bash tools/loop/gate.sh --plan --base main`) is the shell suites alone, and the gate ran them
once: every suite green and `errors=0` (`evidence/gate.log`, one tally per suite beside it), the sensor suite's own
count `tests=61 failures=0` (`evidence/gate-tools_sensor-selftest.sh.xml`). Additive: a registry that
declares no bound is refused, which is the one breaking edge and it is a refusable configuration rather than a
behaviour. Rollback: revert the branch — the bound is read from the registry, so a tree whose registry carries no
`maxFilings` behaves exactly as it does today.

## Spec

`ADDED` **A row's filings in one run are bounded** in `backlog-sensors`. Body: 383 characters against the
validator's 500. Scenarios: *A row past its bound files the bound and one summary*
(`run#aRowPastItsBoundFilesItsBoundAndOneSummary`), *A row inside its bound files nothing extra*
(`run#aRowUnderItsBoundFilesNoSummary`), *A row may declare its own bound* (`run#aRowDeclaresItsOwnBound`),
*The summary clears when a run leaves nothing over* (`run#theSummaryClearsWhenTheRowIsBackUnderItsBound`),
*A summary a person closed is not reopened* (`run#aSummaryClosedByAPersonIsNotReopened`), *A second over-bound run
names its own overflow* (`run#aSecondOverBoundRunListsWhatItDidNotFile`) and *A failed check clears nothing*
(`run#aFailedCheckDoesNotClearTheSummary`).

No existing requirement changes: *A check reports one of three outcomes* keeps its body and every scenario — what it
says about the *present* outcome is still true, one filing at a time; the new requirement states the bound that
outcome now runs under.

## Open

The item's second candidate repair — *the cap refuses the row (`sensors_fail_row`) and writes nothing* — is not
taken, and the evidence eliminates it rather than ranking it: a row that fails writes nothing at all, so its 337
conditions would leave no trace of what it measured, and "the check determined nothing" is exactly the reading
`broken-main` and the clear path exist to prevent (spec `backlog-sensors`, *The check itself failed*). The first
candidate — the bound plus one summary that names the rest — keeps every condition visible while spending one item.
