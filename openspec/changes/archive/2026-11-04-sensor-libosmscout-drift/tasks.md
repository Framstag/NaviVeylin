# Tasks

Parent spec for every task: `backlog-sensors`, the ADDED requirement "A row whose condition recurs by design
declares its own recurrence bound", unless a task names another.

The gate for this change is `tools/sensor-selftest.sh` (offline, JDK 17+, stub `gh`, no Gradle): it sources
`tools/sensor-cases.sh`, which is the file that grows below. No compiled module is touched, so there is no Gradle
build and no JVM suite to run — `guidelines/Build.md` §2's scope block names the structural checks instead.

## 1. The row's own recurrence bound is a validated count

- [x] 1.1 `tools/sensors/lib.sh`: extend `sensors_row_problems` so a row's optional `k` is validated — absent is
      fine, otherwise a count of at least one, refused as `row-k-not-a-count: <value>` beside the existing
      `max-filings` check. Verification: `bash tools/sensor-selftest.sh --case Registry#everyRowIsComplete` stays
      green, and 1.2's case names the reason by string.
- [x] 1.2 New case `lib#aRowBoundIsACount` in `tools/sensor-cases.sh`, registered in `SENSOR_CASES`: a fixture
      registry with `"k": 0` and one with `"k": "many"` are both refused with exit 9 and a reason naming the row
      and the field, and a row with `"k": 100` passes validation. Verification: run it on HEAD **before** 1.1 and
      record the red (the reason does not exist yet) in the change's notes.
- [x] 1.3 New case `run#aRowDeclaresItsOwnRecurrenceBound`: with a fixture registry whose row declares
      `"k": 5`, a closed-as-completed item at `occurrence: 3` is reopened, and one at `occurrence: 5` escalates
      instead. Verification: green — the engine already reads `.k` (`run.sh:509`), so this case pins behaviour that
      nothing exercised; say so in the case's comment rather than presenting it as a fix.
- [x] 1.4 Revert-check for 1.1: remove the `0` arm of the new check, `bash tools/sensor-selftest.sh --case
      lib#aRowBoundIsACount` must fail; restore, then forced green.

## 2. The row and its measurement

- [x] 2.1 Append the `libosmscout-lag` row to `tools/sensors/sensors.json`: `threshold: 1`,
      `comparison: "boolean"`, `consecutive: 1`, `cron: "0 13 * * *"`, `class: "improvement"`,
      `category: "native-jni"`, `lifecycle: "file+close"`, `"k": 100` with its one-line reason,
      `fingerprint.fields: ["repo", "branch"]`, and `upstream: {repo, branch, submodule}` per design D5.
      Verification: `jq -r '.rows[].id' tools/sensors/sensors.json` lists it once, and
      `bash tools/sensors/run.sh --rows libosmscout-lag --due --dry-run` reports no registry problem (exit 9 would
      name one).
- [x] 2.2 Write `tools/sensors/metrics/libosmscout-lag.sh`: read the three `upstream` keys from `$SENSORS_ROW` and
      refuse-with-`sensors_emit_error` when one is empty; read the pinned SHA with
      `git ls-tree HEAD <submodule>`; read upstream's head and `ahead_by` from one `compare` request; emit one
      finding — verdict `violation` with the claim naming the count and the merged-into branch, `clear` at `0`,
      `error` for any unreadable answer. Verification: `bash tools/sensors/run.sh --rows libosmscout-lag --due
      --dry-run` prints one line whose measurement equals the real `ahead_by` (40 against pin `7c449bf66` and
      master `9fd3a1e62` on 2026-11-04) and writes nothing.
- [x] 2.3 New case covering the three emission shapes offline: a stub `curl` in `tools/sensors/fixtures/` put first
      on `PATH` by the case (the pattern the stub `gh` already uses) answers `ahead_by: 40` → `violation`,
      `ahead_by: 0` → `clear`, and HTTP 403/404/500 → `error` with a reason token and no write to the tracker.
      Verification: `bash tools/sensor-selftest.sh --case run#libosmscoutLagReadsThreeOutcomes` green, offline.
- [x] 2.4 Revert-check for 2.2's error path: make the script answer `clear` when the request fails; the case from
      2.3 must fail on the `error` arm; restore, then forced green.
- [x] 2.5 Record the measured baseline in the change's notes: the pin, upstream's head and the count on the day the
      row lands, so the first item's own trend has a starting point a reader can check.

## 3. The pipeline selects the row

- [x] 3.1 Append `libosmscout-lag` to the `--rows` list of the "condition rows and repository rows" step in
      `.github/workflows/sensors.yml` — not the record step, which is gated on `conclusion == success`. Verifying:
      `bash tools/sensor-selftest.sh --case workflow#aRedRecordRunsNoRecordRow` green (it asserts the three lists
      together equal the registry exactly, so it is red between 2.1 and this task).
- [x] 3.2 Revert-check: remove the id from that list, the same case must fail naming the unselected row; restore,
      then forced green.

## 4. The documentation the row makes untrue

- [x] 4.1 `guidelines/BacklogTooling.md` §9: add `libosmscout-lag` to the sentence that names the declared rows, and
      state there that this row declares its own recurrence bound because its condition recurs by design.
- [x] 4.2 `guidelines/BacklogTooling.recipes.md` §9: one paragraph for the row — what it measures and where the
      number comes from, why the bound is "any drift" rather than a lag, why `k` is declared, and the measured
      figure it started from (40 commits, pin `7c449bf66`, master `9fd3a1e62`, 2026-11-04).
- [x] 4.3 Confirm the credential paragraph of `guidelines/BacklogTooling.md` §9 is still true with a row that reads a
      public repository unauthenticated: verification is that scenario
      `sensors#theDocumentedCredentialCoversEveryDeclaredRow` passes unchanged — if it reads the registry's rows and
      needs a sentence about a row needing no scope, add it to the same paragraph.

## 5. Verify the change as a whole

- [x] 5.1 Run `bash tools/sensor-selftest.sh` in full — every case, not the three new ones — and record the run's
      case count and result. Exclusions, named: no Gradle build, no JVM unit suite, no device step, because the
      change touches no compiled file, no app surface and no native source.
- [x] 5.2 Run `tools/check-portability.sh` (the row's script is scanned as an engine file, so a project value in it
      is a failure) and `tools/check-metadata.sh` in its token-free mode.
- [x] 5.3 Run `openspec validate sensor-libosmscout-drift --strict`, and check every scenario in the delta names a
      case: `run#aRowDeclaresItsOwnRecurrenceBound` (1.3), `run#escalatesPastKRecurrences` (existing),
      `lib#aRowBoundIsACount` (1.2).
- [ ] 5.4 Confirm the first real run behaves: read the row's report line for one scheduled tick (or one dispatched
      `apply=true` run) and check that the item was filed once with `sensor/libosmscout-lag`, `class/improvement`,
      `category/native-jni`, `status/open`, `source/ci` — and **no** `agent/eligible`, which is the fact that keeps
      the loop out of it.

  5.4 is not spent here: it needs a scheduled tick or a dispatched run against the tracker, and this session writes
  no tracker item. The row's report line and its labels are read on the first tick after the pipeline carries the id.
