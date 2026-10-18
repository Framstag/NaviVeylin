# Tasks — wire-sensor-inputs

Parent requirements are in `specs/backlog-sensors/spec.md` (two MODIFIED, one ADDED). Every scenario of the
delta names its case below; `[#161]` marks the verification issue #161 owes, `[#166]` the issue this change
filed for what it does not fix.

## 1. The publisher — `tools/build-inputs.sh`

- [x] 1.1 `tools/build-inputs.sh` with three verbs: `--manifest` prints the paths it publishes, `--write`
      materialises them from the workspace, `--check [--registry <file>]` refuses a registry whose row
      declares an input outside the manifest, naming the row id and the path. The manifest is derived from
      the writing code, never a second list. Verifies *the producer's own manifest is the bound* and *an
      input no pipeline publishes is refused* — cases `inputs#manifestIsTheProducerSet`,
      `inputs#refusesADeclaredInputWithNoProducer` (mutations 5.1, 5.2).
- [x] 1.2 `--write` for the four declared paths: the gate timing record, the per-module Kover XMLs plus
      `osmscout-client-java`'s JaCoCo XML converted into `app/build/reports/jacoco/coverage.json` in the
      documented `{"modules":{…}}` shape (`awk`, no python3), `app/build/gate-result.json` per design D8, and
      the package APK named in the manifest without copying it. Verifies the conversion and the three result
      shapes — cases `inputs#writesTheGateTimingRecord`, `inputs#convertsPerModuleCoverage`,
      `inputs#resultRecordShapes` (mutations 5.3, 5.4).
- [x] 1.3 `tools/build-inputs-cases.sh` and `tools/build-inputs-selftest.sh` (environment + runner,
      throwaway tree, no network, stub `gh` on PATH). Verifies every case above runs and is registered.
- [x] 1.4 The result record is written **before** an incomplete record is reported, so a red build still
      carries it (design D10) — case `inputs#aRedBuildStillCarriesTheResultRecord`.

## 2. The fetcher — `tools/fetch-build-inputs.sh`

- [x] 2.1 `tools/fetch-build-inputs.sh`: find the newest *completed* run of a workflow, compare the head SHA
      in the script itself (design D9 — no `gh --commit`, so the guard is reachable by a case), download its
      artifact and unpack it at the destination so every declared path is restored as declared; exit **0**
      fetched, **3** no run or no artifact yet, 1 otherwise. Verifies *a run reads the record of the commit
      it examines* and *the record is one artifact, not a download per row* — cases
      `inputs#fetchRestoresTheDeclaredPaths`, `inputs#refusesAnotherCommitsRecord` (mutation 5.5).
- [x] 2.2 The no-record path: a commit with no completed run, and a run without the artifact, both exit 3.
      Verifies *no record yet is not a failed check* — case `inputs#noRecordExitsThree` (mutation 5.6).
- [x] 2.3 Both cases run against the shared stub `gh` (its `run list`, `run download` and artifact-list
      answers were added for this, in `tools/issues/fixtures/gh`), and the whole suite is green.

## 3. The build pipeline publishes the record

- [x] 3.1 `build.yml`: the test step runs with `-I tools/gate-timings.init.gradle.kts` (the documented gate,
      so `build/gate-timings.json` is that invocation's wall time), and the coverage step names the
      per-module `:app:/:auto:/:core:koverXmlReport` tasks beside the merged reports — one invocation, so the
      test tasks behind them run once. Verified on the host: `build/gate-timings.json` carries `durationMs`
      and every per-module Kover XML exists.
- [x] 3.2 `build.yml`: one `if: always()` step runs `--write`, and an `if: always()` upload publishes the
      written tree as the artifact `sensor-inputs` (`if-no-files-found: error`). Verified on the host:
      `--write` exits 0 and prints exactly the manifest's four paths.
- [x] 3.3 `build.yml`: one `Check the build inputs` step runs the new selftest, `--check` (the bound in the
      pipeline that produces the record) and the mutation set's `--list`.

## 4. The sensor pipeline consumes it, and the schedule returns

- [x] 4.1 `tools/sensors/sensors.json`: `build-duration`'s input moved to `build/gate-timings.json`;
      `package-size`'s input moved to the debug APK CI builds (design D4). Verifies: `--check` passes over
      the repository's own registry, and the sensor suite stays green.
- [x] 4.2 `sensors.yml`: `actions: read` on the job; a fetch step using `${{ github.token }}` that runs
      `fetch-build-inputs.sh --commit "$GITHUB_SHA"` and records `ready=true|false` (exit 3 is not a
      failure); the rows step gated on that output; the report step still `if: always()`. Also: the mode is
      now `SENSORS_MODE`, so a *scheduled* run writes (the dispatch input cannot answer for it) — a dispatch
      keeps its own choice and still defaults to a dry run.
- [x] 4.3 `sensors.yml`: the `schedule` trigger restored (`0,30 * * * *`, matching the registry's minutes)
      and the header note rewritten. Verifies *the schedule fires without a person* and *the tick's minutes
      cover the registry's crons* — case `workflow#tickMinutesCoverTheRegistryCrons` (mutation 5.7), beside
      `workflow#onlyTwoTriggers` for the trigger cap and the narrowed token rule (mutation 5.8).
- [x] 4.4 The condition rows' silence, which the wiring made live: `broken-main` reports a clear for the
      build's own identity when the build is green and nothing is recorded, `pr-conflicts` when no conflict
      of the owner's is open (design D11; the runner's "printed no finding" rule is what made this a failed
      check). Verifies *a row whose condition is simply absent says so* — cases
      `sensors#brokenMainGreenReportsClear`, `sensors#prConflictsNothingReportsClear` (mutations 5.9, 5.10).
- [x] 4.5 `[#161]` Host substitute for the end-to-end record: with the record built from this tree's own
      outputs, every selected row reports a verdict and none reports `error` (the matrix is in `report.md`;
      the release APK stood in for the debug one). The dispatched run on `main` — the real evidence — is
      owed and named as such in `report.md`, because a dispatch from a branch is refused by the guard.

## 5. Falsification — one mutation per invariant

- [x] 5.1 `tools/build-inputs-mutations.sh` (same shape as `tools/sensor-mutations.sh`), with a suite prefix
      (`sensor:<case>`) for the cases the sensor tooling owns. Twelve mutations: the manifest, the refusal,
      the coverage counter (last vs first in the document), the result record, the commit identity, the
      exit-3 path, the tick minutes, the workflow's own token, the three `ready` gates, the dispatch's
      boolean comparison, and the two condition-row clears.
- [x] 5.2 `bash tools/build-inputs-mutations.sh` — 12 behaved, 0 did not; every mutation made its named case
      fail and the tree was restored (recorded in `report.md`).

## 6. Documentation and evidence

- [x] 6.1 `guidelines/Backlog.md`: the checks' section now states who publishes a row's inputs, how the
      record is fetched and that a red build's record is partial by construction; `§10 Portability` gained
      the producer/fetcher as the second thing a project adapts; the change table gained row 7.
- [x] 6.2 `[#166]` Filed: the late-tick gap (design D7), with the measured selection pair
      (`--now 2026-10-18T00:00:00Z` → `broken-main`; `--now 2026-10-18T00:07:00Z` → no rows selected).
- [x] 6.3 Structural checks (no compiled file changes — `guidelines/Build.md` §2, scope block):
      `bash tools/check-portability.sh` (42 engines, 133 values), `bash tools/check-portability-selftest.sh`
      (21 cases), `bash tools/check-doc-routes.sh` (89 references), `bash tools/declared-cases-selftest.sh`
      (12 passed), `bash tools/sensor-selftest.sh` (36 cases), `bash tools/build-inputs-selftest.sh`
      (10 cases), and both workflow files parse with `sensors.yml`'s triggers = `schedule` +
      `workflow_dispatch`.
- [x] 6.4 `openspec validate wire-sensor-inputs --strict` passes, and `report.md` carries the traceability
      table (every delta scenario → its case) and the evidence.
- [x] 6.5 Independent read-only review before the archive (the writer may not be the reviewer). Two blockers
      and six further findings, all fixed: R1 the coverage conversion misread the real single-line JaCoCo
      report (minified fixture, document-level parse, mutation 5.3 — design D12), R2 the dispatch's `apply`
      was compared as a string and could never write (`inputs.apply == true`, case assertion + mutation 5.12
      — design D13), R3 the labels and citation-repair steps wrote before the record gate (gated, assertion +
      mutation 5.11 — design D14), R4 `grep --`, R5 traceability gaps, R6 the manifest groups (design D15).
      The review's own "could not verify" list is the `Owed` list in `report.md`.
