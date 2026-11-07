# Tasks — the build-duration row measures the pipeline

## 1. The red cases

- [x] 1.1 `tools/sensor-cases.sh` — `sensors#buildDurationCoversThePipeline`: a job-timings record whose every
      Gradle step is the healthy one's while `Install vcpkg dependencies` is an hour longer must file one
      condition whose identity is that job, and its claim must name that step; the same record with the step back
      at 103 s must file nothing, and the row must declare the record the fetcher writes. Verification: on the run's base `bea00bf5da8d8c81304d9271e66715c40b13b714`
      (the nine paths this change touches are identical to the killed run's base `0fb7a802`, `git diff --stat`
      between the two over them prints nothing),
      `bash tools/sensor-selftest.sh --case 'sensors#buildDurationCoversThePipeline'` read `FAIL` with
      `expected '0', got '8'` on the run's status (the row reported `error-the-timing-record-proves-nothing`).
      It is one of the two cases of the combined red run the record carries — `tests=2 failures=2`,
      `2026-11-06T14:06:45Z` (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`); the split into one case
      each is derived from that log, not a second command's output.
- [x] 1.2 `tools/build-inputs-cases.sh` — `inputs#theFetcherPublishesTheJobTimings`: the fetcher given
      `--jobs-record` writes every job's and step's wall time from the stub's jobs API, names the run and the
      commit, and a fetch that did not ask for the record writes none. Verification: on the same base the case
      read `FAIL` — `fetch-build-inputs: unknown argument --jobs-record`, exit 2 — the second case of the same
      `tests=2 failures=2` record, `2026-11-06T14:06:45Z`.

## 2. The producer

- [x] 2.1 `tools/issues/fixtures/gh` — answer `api repos/*/actions/runs/*/jobs` from `jobs.json`, so the record
      is testable with no token and no network. Verification: `bash tools/build-inputs-selftest.sh` — 14 cases,
      0 failed, including the two that read the stub's artifact list and its jobs.
- [x] 2.2 `tools/fetch-build-inputs.sh` — `--jobs-record <path>` writes the record after the artifact is
      restored, deriving `durationMs` from `started_at`/`completed_at`, and dies when the API cannot be read
      (`exit 1`, "cannot tell") rather than writing a partial record. Verification: 1.2's case passes, and its
      second half asserts a fetch without the flag writes nothing.
- [x] 2.3 `tools/build-inputs.sh` — `build/job-timings.json` in `foreign_paths()`, with the comment that names
      the fetcher as its producer. Verification: `bash tools/build-inputs.sh --check` — "every declared input has
      a producer (5 rows checked)".
- [x] 2.4 `.github/workflows/sensors.yml` — the build's fetch passes `--jobs-record build/job-timings.json`; the
      scan's fetch does not. Verification: `grep -n 'jobs-record' .github/workflows/sensors.yml` = 2 lines, the
      comment at 86 and the one call at 97 (the scan's fetch, below it, passes no such flag), and
      `bash tools/build-inputs-selftest.sh --case 'workflow#tickMinutesCoverTheRegistryCrons'` passes.

## 3. The row

- [x] 3.1 `tools/sensors/metrics/build-duration.sh` — measure the record's longest job, name its longest step in
      the claim, and read the bound absolutely from `SENSORS_THRESHOLD`; keep the error findings the row already
      had (no record, unreadable record, no job wall time) because a check that determined nothing writes
      nothing. Verification: 1.1's case passes both halves, including its assertion that the filed condition's
      identity is the job itself, and `sensors#buildDurationRow` passes.
- [x] 3.2 `tools/sensors/sensors.json` — `build-duration`: `input: build/job-timings.json`, `comparison:
      greater-than`, `threshold: 3600000`, `consecutive: 2`, fingerprint fields unchanged. Verification:
      `bash tools/sensor-selftest.sh` — all 85 cases passed, including `Registry#everyRowIsComplete` and
      `guard#refusesARowWithoutThresholdOrConsecutive` (`evidence/sensor-selftest.log`, its tally
      `evidence/sensor-selftest.xml`).

## 4. The two cases that asserted the old measurement

- [x] 4.1 `tools/sensor-cases.sh` — `sensors#buildDurationRow` reads a job-timings fixture: the longest job
      inside the ceiling is a clear recorded on the row's record, and that job past it annotates the open
      condition carrying its identity. Verification: the case passes.
- [x] 4.2 `tools/sensor-cases.sh` — `run#aFilingSetsTheBaseline` moves to a `baseline-multiple` row
      (`package-size`), so the scenario the spec names (a filing's marker is the next measurement's baseline)
      keeps its case and the absolute-bound row does not carry it. Verification: the case passes and still
      asserts `baseline=100000` from the filing's own marker.

## 5. The documentation and the delta

- [x] 5.1 `guidelines/BacklogTooling.recipes.md` §9 — what the build's published record carries and what
      `build-duration` measures (the longest job, an absolute hour, the claim naming the step). Verification:
      `bash tools/check-doc-routes.sh` — every section and skill reference resolves.
- [x] 5.2 `openspec/changes/fix-build-duration-measures-the-pipeline/specs/backlog-sensors/spec.md` — the added
      requirement and its two scenarios, each naming one of the two cases, plus the `## MODIFIED Requirements`
      block for the shipped requirement this change contradicts (`The issues carry the measurement history`):
      the baseline sentence is scoped to a row whose comparison is a multiple of the previous one, and the
      scenario `The baseline is the previous measurement` names the case task 4.2 moves to `package-size`
      instead of the case task 4.1 rewrites. Verification: `bash tools/check-change-readiness.sh
      openspec/changes/fix-build-duration-measures-the-pipeline` prints `LOOP-CHANGE-READY: ok`, and the
      measure block reads `scenarios=8 cases=8 unnamed=0` — the two added and the six the modified requirement
      carries.

## 6. The change's own numbers

- [x] 6.1 `bash tools/loop/measure.sh --change fix-build-duration-measures-the-pipeline` — the block is
      `evidence/measure.txt`; `LOOP-MEASURE-CHANGE … scenarios=8 cases=8 unnamed=0` and `LOOP-MEASURE-SUITE
      cases=330` are quoted in the proposal.
- [x] 6.2 No compiled file is touched, so no Gradle gate is owed and none was run (the run deferred the gate to a
      fresh child in any case). The structural check that stands in: `bash tools/sensor-selftest.sh` (85 cases),
      `bash tools/build-inputs-selftest.sh` (14), `bash tools/issue-tooling-selftest.sh` (158, because the change
      extends the shared stub `gh`), `bash tools/sensor-mutations.sh --list`, `bash
      tools/build-inputs-mutations.sh --list`, `bash tools/build-inputs.sh --check`, `bash
      tools/check-doc-routes.sh`, `check-portability.sh`, `check-openspec-hygiene.sh`, `check-todo-references.sh`
      and `check-change-readiness.sh` — `evidence/structural-checks.log`, tally
      `evidence/structural-checks.xml`, 29 commands / 0 failures.
