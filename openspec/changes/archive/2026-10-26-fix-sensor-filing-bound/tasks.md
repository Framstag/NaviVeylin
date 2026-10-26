# Tasks — fix-sensor-filing-bound

## 1. The red tests, before the fix

- [x] 1.1 `tools/sensors/fixtures/registry-bounded.json` — the fixture registry the seven cases drive: the
  registry's default `maxFilings: 2`, a row declaring 3, a row declaring 0. The four existing registry fixtures gain
  the key, because it is required. Verified: `jq -e .` over all five, and every existing case green
  (`evidence/focused-existing-cases.log`).
- [x] 1.2 `run#aRowPastItsBoundFilesItsBoundAndOneSummary` in `SENSOR_CASES` and the dispatch table. Red on the base:
  `the bound's worth of conditions is filed: expected '2', got '4'`; tally
  `evidence/red-on-head-aRowPastItsBoundFilesItsBoundAndOneSummary.xml` (`tests="1" failures="1"`), log
  `evidence/red-on-head.log` (2026-10-26T18:13:26Z).
- [x] 1.3 `run#aRowDeclaresItsOwnBound` — red on the base `expected '3', got '5'`; tally
  `evidence/red-on-head-aRowDeclaresItsOwnBound.xml`.
- [x] 1.4 `run#theSummaryClearsWhenTheRowIsBackUnderItsBound` — red on the base `expected '1', got '0'` (nothing
  closes a summary, because nothing files one); tally
  `evidence/red-on-head-theSummaryClearsWhenTheRowIsBackUnderItsBound.xml`.
- [x] 1.5 `run#aRowUnderItsBoundFilesNoSummary` — green on the base by construction: it asserts the absence the fix
  must not introduce (no summary when nothing was left over). Its evidence is mutation `8.24`.
- [x] 1.6 `run#aSummaryClosedByAPersonIsNotReopened` — red on the base too, on its first assertion
  (`expected '2', got '3'`: without a bound, every condition is filed and no summary exists to be left alone);
  the half that pins the closure rule is falsified by mutation `8.25`. Tally
  `evidence/red-on-head-aSummaryClosedByAPersonIsNotReopened.xml`.
- [x] 1.7 `run#aSecondOverBoundRunListsWhatItDidNotFile` — the steady state of an over-bound row: an item exists,
  and the identities this run did not file reach the tracker on it. Falsified by mutation `8.27`.
- [x] 1.8 `run#aFailedCheckDoesNotClearTheSummary` — a row holding a summary whose finding failed determines
  nothing, so nothing is closed. Falsified by mutation `8.26`.

## 2. The bound, in the engine

- [x] 2.1 `tools/sensors/lib.sh` — `sensors_row_max_filings` (row's own `maxFilings`, else the registry's),
  `row-max-filings-not-a-count` in `sensors_row_problems`, `registry-max-filings-missing` and
  `registry-max-filings-not-a-count` in `sensors_registry_problems`, and the summary's exclusion in
  `sensors_conditions_json`. Verified by `run#aRowDeclaresItsOwnBound` (the refusal half) and the registry cases.
- [x] 2.2 `tools/sensors/run.sh` — the per-row bound and counters, the absent-issue branch that stops filing at the
  bound, `sensors_summary_outcome` with its file/annotate/reopen/close paths and its refusal to overrule a person's
  closure, and `sensors_file`'s optional body. Verified by the five bound cases green
  (`evidence/focused-bound-cases.log`) and by the affected lifecycle cases — the whole suite
  (`evidence/focused-existing-cases.log`).
- [x] 2.3 `tools/sensors/sensors.json` — `"maxFilings": 10`, the project's declared bound, with the reason in the
  registry's own comment beside `threshold`'s. Verified by `Registry#everyRowIsComplete` (the shipped registry
  reports no problem).
- [x] 2.4 `tools/sensors/run.sh`'s summary writers — each checks the writer's exit code and fails the row when the
  tracker refused the write, and one item's list is bounded at `SENSORS_SUMMARY_LIST_MAX` (20) identities with the
  rest named by number. Verified by `run#aSecondOverBoundRunListsWhatItDidNotFile` and
  `run#aFailedCheckDoesNotClearTheSummary`.

## 3. The documents

- [x] 3.1 `guidelines/Backlog.md` §9 "Checks" — the paragraph that names the bound, the one summary and the
  clearing, beside the three rules the section already states. Verified by `tools/check-doc-routes.sh` and
  `tools/check-todo-references.sh` in the gate.
- [x] 3.2 `PORTING.md` — the registry's own description gains `maxFilings`. Verified by
  `tools/check-portability.sh` (the engine still names no project value) and `tools/check-doc-routes.sh`.

## 4. Falsification

- [x] 4.1 Mutation `8.23` — the absent-issue branch ignores the count (every condition filed whatever the bound):
  reddens `run#aRowPastItsBoundFilesItsBoundAndOneSummary`, restore green — `evidence/mutation-8.23.log`.
- [x] 4.2 Mutation `8.24` — the summary fires with nothing left over: reddens
  `run#aRowUnderItsBoundFilesNoSummary`, restore green — `evidence/mutation-8.24.log`.
- [x] 4.3 Mutation `8.25` — the summary's closure is reopened whatever closed it: reddens
  `run#aSummaryClosedByAPersonIsNotReopened`, restore green — `evidence/mutation-8.25.log`.
- [x] 4.4 Mutation `8.26` — a run whose finding failed still clears the summary: reddens
  `run#aFailedCheckDoesNotClearTheSummary`, restore green — `evidence/mutation-8.26.log`.
- [x] 4.5 Mutation `8.27` — the annotation names no identities: reddens
  `run#aSecondOverBoundRunListsWhatItDidNotFile`, restore green — `evidence/mutation-8.27.log`.
- [x] 4.6 The whole mutation set, not only the new records: `bash tools/sensor-mutations.sh` — 27 behaved, 0 did
  not (`evidence/mutations.log`).
- [x] 4.7 `bash tools/sensor-mutations.sh --list` — each new record is listed and applies to the file it names (inside
  the gate).

## 5. The spec and the record

- [x] 5.1 The delta `openspec/changes/fix-sensor-filing-bound/specs/backlog-sensors/spec.md`: one added requirement,
  five scenarios, each naming exactly one case. Verified by `openspec validate fix-sensor-filing-bound --strict`.
- [x] 5.2 `evidence/` — the red tallies and their log, the focused green log, the whole sensor suite, both new
  mutation logs and the whole mutation set, the gate log with one tally per suite, and the surface counts. Verified
  by `bash tools/loop/evidence-check.sh openspec/changes/fix-sensor-filing-bound --root .`.

## 6. The gate, once

- [x] 6.1 `tools/build-inputs.sh`'s executable bit — the profile's gate mapping runs the script directly while CI
  prefixes `bash`, so the step is red on a base where the bit is missing and this change touches the registry it
  reads. Verified: `./tools/build-inputs.sh --check` → `every declared input has a producer (4 rows checked)`, exit 0.
- [x] 6.2 `bash tools/loop/gate.sh --base main` run from the change root, once; its plan for this pathspec is the
  shell suites alone (no compiled path changed, so no Gradle step), 3m59s of them, every suite green and `errors=0`
  (`evidence/gate.log`). The sensor suite's own count is `tests=61 failures=0`
  (`evidence/gate-tools_sensor-selftest.sh.xml`), and one tally per suite sits beside it
  (`evidence/gate-*.xml`). Every case suite the profile maps for `tools/sensors/**`, `tools/sensor-*.sh`, `*.md` and
  `openspec/**` ran green.
