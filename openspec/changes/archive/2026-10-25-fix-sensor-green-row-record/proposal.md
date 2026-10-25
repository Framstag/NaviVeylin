# Proposal — fix-sensor-green-row-record

## Why

One defect, measured on `main`, issue #257: **a check that runs and is green leaves no trace in the tracker, so
the report reads it as a check that never ran.**

**Root cause (one).** `sensors_outcome_clear` in `tools/sensors/run.sh` (the `if [ -z "$issue" ]` branch) writes
nothing when no issue matches the finding's fingerprint — it only calls `sensors_report`. Every tracker-facing
number the report prints comes from an issue (`tools/sensors/report.sh`, `print_row`): a row whose measurements
never landed on one prints `row=<id> last=never …` and `stale row=<id> no measurement on record — the row has
never run or never filed`. The observed run 39544058388 (2026-10-21T04:26Z) carries both lines for
`package-size` in the same job that logged `verdict=clear measurement=128285406 issue=- action=nothing filed,
nothing to clear`. Seven of the eight rows print this line today; only `source-size` (4 issues) and
`broken-main` (1) have ever filed a condition issue. A `comparison: baseline-multiple` row is hit twice by the
same cause: with no issue to carry a measurement, `SENSORS_BASELINE` is empty and `package-size` can never
compute a bound at all.

**The single resulting fix.** A clear whose identity no condition issue carries is recorded on a **record
issue**: one issue per row, filed on the first such clear, carrying the row's own label and the state the
registry declares for records (`record.status`), and never closed. Every later clear that no open condition
issue carries is posted on it as a comment, so the row's timeline — and therefore the baseline a
`baseline-multiple` row compares against — is continuous. `report.sh` already aggregates a row's measurements
by its label, so it stops printing `last=never` for a row that ran.

**Red test.** `bash tools/sensor-selftest.sh --case 'run#aClearWithNoIssueReachesTheRecord'` on HEAD (the fix
stashed): `the clear files the row's record issue: expected '1', got '0'` — `FAIL`, exit 1. After the fix:
`PASS`, and `bash tools/sensor-selftest.sh` prints `all 49 cases passed`. The companion case
`lib#theRecordIsNotACondition` is red on HEAD too (`the row's record is not a condition: expected '[]', got
'[{"number":9,…}]'`), because without the fix a record issue is read as a condition to clear.

**The requirement the defect contradicts.** `backlog-sensors`'s scenario *A row whose condition is simply
absent says so* states that a clear naming the row's own identity reaches the tracker's history "as a verdict
rather than as the row's silence". Nothing wrote it, so the requirement is **modified** — the requirement body
is kept whole, its other five scenarios are repeated verbatim, that scenario's `THEN` names the record issue and
its case, and two scenarios are added (*The report tells a green row from one that never ran*, *A row's record
is not a condition*), each naming the one case that exercises it.

## What Changes

- **`tools/sensors/run.sh`** — the record path: `sensors_record_body`, `sensors_file_record` (files the row's
  record once, reading `.record.status` from the registry and taking the created issue's number from the
  writer's own output), `sensors_record_number` (a per-run cache, so one run files one record per row),
  `sensors_record_clear` and `sensors_comment` (a comment without a body write). `sensors_outcome_clear` sends
  an absent *or already-closed* condition identity to the record; `sensors_labels_json` takes the record's state
  as an optional argument.
- **`tools/sensors/lib.sh`** — `sensors_record_issue` finds the record by its own marker (`<!-- sensor-record:
  <id> -->`), never by a fingerprint; `sensors_conditions_json` excludes record issues, so no sensor reads a
  record as a condition to clear; `sensors_last_marker` sorts by the markers' own instants, because a row now
  carries a record issue beside its condition issues; `sensors_registry_problems` refuses a registry that
  declares no `record.status` or one outside the vocabulary.
- **`tools/sensors/sensors.json`** — the `record` block (`status: on-hold`): the state that keeps a record out
  of every work selector. The fixtures (`registry.json`, `registry-no-consecutive.json`,
  `registry-no-threshold.json`) and the foreign-project fixture carry the same block.
- **`tools/sensor-cases.sh`** — three cases: `run#aClearWithNoIssueReachesTheRecord` (files the record, then
  finds it again by its marker and comments on it), `lib#theRecordIsNotACondition`, and
  `report#aGreenRowIsNotNever` (a record is read as a measurement, the row with none is still `never`). Four
  existing cases that counted *any* filing as a condition now count condition filings and record filings apart
  (`condition_creates`, `record_creates`).
- **`tools/sensor-mutations.sh`** — mutation `8.18`: the record call is removed, so the reproducer case is red.
- **`tools/fixtures/foreign-project/tools/sensors/sensors.json`** and **`PORTING.md`** — the record block and the
  recipe's new registry key.
- **`openspec/specs/backlog-sensors/spec.md`** — one requirement modified when this change archives.

**Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
resource — the tools, one registry, one fixture and one spec. `--within`, the three verdicts, the hysteresis,
the escalation and the ownership scope are untouched; the only behaviour that changes is the clear that used to
write nothing. Rollback path: revert this change's commit — the record path disappears, a green row writes
nothing again, and the red test's failure on HEAD reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at all,
so no Gradle build and no test suite is owed (`guidelines/Build.md` §2, its scope block); the verification is
the sensor tooling's own offline suite, its mutation harness and the structural checks named in `tasks.md`.

**Guidelines.** `guidelines/Backlog.md` §9 owns the checks' doctrine and its portability section §10 owns the
registry-as-configuration split; nothing there contradicts a per-row record issue, and no guideline states where
a green row's verdict lands, so no guideline is edited beyond `PORTING.md`'s registry-key list (a rule goes
where the measurement lives, and this measurement is a case in `tools/sensor-cases.sh`).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: one requirement modified — `A check reports one of three outcomes, and only clearing
  closes`. Its shipped description is unchanged, five of its six scenarios are copied whole, *A row whose
  condition is simply absent says so* now names the record issue and its case, and two scenarios are added.
  The delta lives at `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine and its registry)
- `tools/sensors/run.sh` — the record path and the clear's two branches
- `tools/sensors/lib.sh` — `sensors_record_issue`, `sensors_conditions_json`, `sensors_last_marker`,
  `sensors_registry_problems`
- `tools/sensors/sensors.json` — `record.status`

Cases and their harness
- `tools/sensor-cases.sh` — three cases, two helpers, four realigned counts
- `tools/sensor-mutations.sh` — mutation `8.18`

Fixtures, porting and specs
- `tools/sensors/fixtures/registry*.json`, `tools/fixtures/foreign-project/tools/sensors/sensors.json`
- `PORTING.md`, `openspec/specs/backlog-sensors/spec.md`

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's own gate (49 cases)
- `bash tools/sensor-mutations.sh --only 8.18` — the change-time falsification of the record path
- `bash tools/check-portability.sh` and `bash tools/portability-cases.sh` — the engine still names no project
  value and the foreign project still runs with its own registry
- Not run, deliberately: no Gradle build or test suite (no compiled file is touched; `guidelines/Build.md`
  scope block), and no live `workflow_dispatch` of `sensors.yml` (its guard refuses every ref but `main`).
