# wire-sensor-inputs — report and evidence

Change: `openspec/changes/wire-sensor-inputs`. Applied 2026-10-18 on `main` (commit of the working tree that
carried it). Closes what issue **#161** deferred; files issue **#166**.

No compiled file changed — no Kotlin, no C++, no Gradle build logic — so no Gradle gate is owed
(`guidelines/Build.md` §2, scope block). The verification is the shell gates, the two workflow files, and the
row matrix below.

## What landed

| file | change |
|---|---|
| `tools/build-inputs.sh` | new — the producer: `--manifest`, `--write`, `--check`, `--root` |
| `tools/fetch-build-inputs.sh` | new — a commit's record, exit 3 = not built yet |
| `tools/build-inputs-cases.sh` | new — 10 cases |
| `tools/build-inputs-selftest.sh` | new — their offline harness (stub `gh` on PATH) |
| `tools/build-inputs-mutations.sh` | new — 10 revert-check mutations |
| `tools/issues/fixtures/gh` | stub `gh` learned `run list`, `run download` and the artifacts API answer |
| `tools/sensors/conditions/broken-main.sh` | a green build with nothing recorded reports a clear |
| `tools/sensors/conditions/pr-conflicts.sh` | no conflict of the owner's reports a clear |
| `tools/sensor-cases.sh` | two cases for those clears; the pipeline case now asserts *where* the workflow's own token may appear |
| `tools/sensors/sensors.json` | `build-duration` → `build/gate-timings.json`; `package-size` → the debug APK |
| `.github/workflows/build.yml` | gate-timing init script on the test step; per-module Kover XML; publish + upload the record (`if: always()`); one `Check the build inputs` step |
| `.github/workflows/sensors.yml` | `actions: read`; the fetch step; rows gated on the record; `SENSORS_MODE`; `schedule` restored; header rewritten |
| `guidelines/Backlog.md` | §9 (who publishes a row's inputs, the partial record of a red build), §10 (the producer/fetcher as the second thing a project adapts), the change table's row 7 |

## Traceability — every delta scenario to its case

| requirement (delta) | scenario | case |
|---|---|---|
| MODIFIED — the checks' own pipeline | *the schedule fires without a person* | `workflow#tickMinutesCoverTheRegistryCrons` |
| MODIFIED — the checks' own pipeline | *change validation is unaffected* | `sensor:workflow#onlyTwoTriggers` (no `pull_request`/`workflow_run` anywhere) |
| MODIFIED — the checks' own pipeline | *two runs cannot race* | `workflow#onlyTwoTriggers` (the `concurrency:` group) |
| MODIFIED — the checks' own pipeline | *the pipeline's own changes are reviewed* | `workflow#onlyTwoTriggers` (`CODEOWNERS` covers `/.github/workflows/`) |
| MODIFIED — the checks' own pipeline | *the tracker's identity is never the workflow's own token; every tracker write waits for the record; a dispatch's `apply` is read as a boolean* | `workflow#onlyTwoTriggers` (three assertions, mutations 5.8, 5.11, 5.12) |
| MODIFIED — the checks' own pipeline | *a dispatch from another branch cannot act* | `guard#refusesOffMain` (sensor suite, pre-existing and unchanged) |
| MODIFIED — the checks' own pipeline | *the identity is a concrete person* / *the workflow's own token is read-only* | `sensor:workflow#onlyTwoTriggers` (token used once, inside the fetch step) |
| MODIFIED — three outcomes | *a row whose condition is simply absent says so* | `sensor:sensors#brokenMainGreenReportsClear`, `sensor:sensors#prConflictsNothingReportsClear` |
| ADDED — a declared input has a producer | *the producer's own manifest is the bound* | `inputs#manifestIsTheProducerSet` |
| ADDED — a declared input has a producer | *an input no pipeline publishes is refused* | `inputs#refusesADeclaredInputWithNoProducer` |
| ADDED — a declared input has a producer | *a run reads the record of the commit it examines* | `inputs#fetchRestoresTheDeclaredPaths` |
| ADDED — a declared input has a producer | *no record yet is not a failed check* | `inputs#noRecordExitsThree` |
| ADDED — a declared input has a producer | *the record is one artifact, not a download per row* | `inputs#fetchRestoresTheDeclaredPaths` (declared tree unpacked as declared, another commit's newest run unused) |

Scenarios not changed but named by this change's own work: *the check itself failed* and *failing is not
clearing* keep their pre-existing cases; the red-build partial record is exercised by
`inputs#aRedBuildStillCarriesTheResultRecord` and `inputs#resultRecordShapes`.

## Evidence

### Suites (all on the host, offline, no token)

```
tools/build-inputs-selftest.sh        10 cases, 0 failed
tools/build-inputs-mutations.sh       12 behaved, 0 did not
tools/sensor-selftest.sh              36 cases passed (JDK 27, offline, no token)
tools/check-portability.sh            OK — 42 engines and 2 templates name no project value (133 values)
tools/check-portability-selftest.sh   21 cases passed
tools/check-doc-routes.sh             all 89 section references resolve
tools/declared-cases-selftest.sh      12 passed, 0 failed
openspec validate wire-sensor-inputs --strict   valid
```

The mutation run, one line per invariant:

```
5.1  inputs#manifestIsTheProducerSet                 the manifest is the set of paths the producer writes
5.2  inputs#refusesADeclaredInputWithNoProducer      a declared input outside the manifest is refused
5.3  inputs#convertsPerModuleCoverage                the module percentage is the report's own counter,
                                                     not the first one in the document
5.4  inputs#resultRecordShapes                       the result record is written for every build
5.5  inputs#refusesAnotherCommitsRecord              the record is the commit's own
5.6  inputs#noRecordExitsThree                       an absent record is not ready, and not a failed check
5.7  workflow#tickMinutesCoverTheRegistryCrons       the tick's minutes cover the registry's
5.8  sensor:workflow#onlyTwoTriggers                 the workflow's own token never writes to the tracker
5.9  sensor:sensors#brokenMainGreenReportsClear      a condition row with nothing to report still reports
5.10 sensor:sensors#prConflictsNothingReportsClear   the same duty on the pull-request row
5.11 sensor:workflow#onlyTwoTriggers                 every tracker write waits for the record
5.12 sensor:workflow#onlyTwoTriggers                 the dispatch apply is read as the boolean it is
```

### The row matrix — issue #161's verification, on the host

The record was built from this tree's own build outputs (`bash tools/build-inputs.sh --write`), with the
release APK copied to the debug path as a stand-in (see *Owed* below). Then every row, one at a time,
`--dry-run` — coverage and hotspot report one finding per module and per path, so several lines each:

```
build-duration  clear      293842
package-size    clear      33421503
coverage        clear      77.2      (app)
coverage        violation  51.4      (auto, floor 70)
coverage        violation  68.8      (core, floor 70)
coverage        violation  25.7      (osmscout-client-java, floor 70)
broken-main     clear      success
pr-conflicts    clear      none
hotspot         violation  5902008 … (16 paths of this tree, floor 5000)
```

No row reported `error`, and the run exits 0. The publisher's own output for that tree:

```
build-inputs: wrote the record for /home/tim/projects/NaviVeylin
build-inputs:   build/gate-timings.json
build-inputs:   app/build/reports/jacoco/coverage.json
build-inputs:   app/build/gate-result.json
build-inputs:   app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
app/build/reports/jacoco/coverage.json → {"modules":{"app":77.2,"auto":51.4,"core":68.8,"osmscout-client-java":25.7}}
```

`osmscout-client-java`'s 25.7 % is the **corrected** number — the first conversion read the first counter of
Gradle's single-line JaCoCo report (a class's `missed="0" covered="12"`) and wrote 100.0. See R1 below.

The late-tick measurement that became issue **#166** (the same day, on the same tree):

```
run.sh --due --dry-run --now 2026-10-18T00:00:00Z   → selects broken-main
run.sh --due --dry-run --now 2026-10-18T00:07:00Z   → no rows selected at 2026-10-18T00:07:00Z
```

### Structural

Both workflow files parse. `sensors.yml`'s trigger set is `schedule` + `workflow_dispatch` (the cap), its
`permissions:` carries `contents: read`, `actions: read` and `issues: write` for the job, and `github.token`
appears exactly once — in the record fetch.

## Independent review (writer ≠ reviewer)

A fresh read-only child reviewed the diff against the delta and the evidence before the archive
(`.pi/tmp/wire-sensor-inputs-review.md`). It found two blockers, both real, both fixed here:

- **R1 (blocker) — the coverage conversion was wrong for the real report shape.** The first version read the
  XML line by line and took the first counter on the line. Gradle's JaCoCo report is a **single line**
  (`wc -l` = 0), so `osmscout-client-java` came out as **100.0** (a class's `missed=0 covered=12`) where the
  report's own counter says **25.7** — a clear where the 70 % floor demands a violation, and the *shipped*
  `coverage.json` carried the wrong number. The fixture was pretty-printed, so no case could see it.
  **Fix**: parse the document, not the lines — the report's own counters come last, so the percentage is the
  *last* `<counter type="INSTRUCTION">` in the (newline-stripped) file; the fixture now emits the minified
  shape for two modules, mutation 5.3 flips `tail`↔`head`, and the evidence above is re-recorded with 25.7.
- **R2 (blocker) — a manual dispatch could never write.** `inputs.apply == 'true'` is never true for a
  `type: boolean` input (the runner coerces the boolean to a number and the string to NaN), so
  `SENSORS_MODE` was `--dry-run` for every dispatch — a silent regression of the previous `${{ … }}` string
  form. **Fix**: `inputs.apply == true`, asserted by `workflow#onlyTwoTriggers` (both directions) and by
  mutation 5.12.

Fixed from its other findings:

- **R3 (should-fix)**: the labels and the citation-repair steps ran before the `ready` gate, so a scheduled
  run with no record could still create labels and repair citations — contradicting the spec scenario that
  says such a run writes nothing. Both steps are now gated like the rows, and the case counts the three
  gates (mutation 5.11). Design D5 says so.
- **R4 (nit)**: `grep -Fxq -- "$path"` in `--check`, so a registry row declaring `--help` cannot pass as a
  published path.
- **R5 (nit)**: the traceability table now names a case for every delta scenario, including the two it had
  left implicit and the three assertions R2/R3 added.
- **R6 (nit)**: the manifest is the union of two named groups (`written_paths`, `produced_paths`) instead of
  one flat list, and `--write` asserts every published path exists afterwards — so a path in the manifest
  that nothing writes fails the write, and a writer's path missing from the manifest is a loud refusal in
  `--check` rather than a silent pass.
- **R7 (nit)**: the reviewer read "the documented gate" in `build.yml` as imprecise. It is the documented
  full gate (`guidelines/Build.md` §3: `test -PforceTests --no-build-cache`), so the comment stands as
  written; the other Gradle invocations are deliberately not instrumented, because the row measures the gate.
- **R8 (observation)**: the "no compiled file" scope rule is literally satisfied, and it is what let R1
  through — the host fixture, not the gate, is what catches a conversion bug. That is exactly why the
  minified fixture is now permanent.

Nothing was left open as a known defect. The review's own "could not verify" list (the artifact round trip,
`actions/upload-artifact` on a red build, a live scheduled tick) matches *Owed* above.

## Owed, and what is not evidenced here

- **The dispatched run on `main`** (task 4.5, issue #161's own owed verification): `sensors.yml` cannot be
  dispatched from a feature branch — its guard refuses any ref but `main` — so the record above is the host
  substitute. After this lands, one `workflow_dispatch` of `sensors.yml` with `apply: false` is the
  end-to-end record: it must fetch this commit's `sensor-inputs` artifact and select rows whose verdicts are
  all `violation`/`clear`, none `error`.
- **The artifact round trip through GitHub** (upload in `build.yml`, download in `sensors.yml`) is exercised
  on the host only through the stub `gh`; neither workflow step has run in CI.
- **The stand-in**: this tree has no debug APK, so the release APK was copied to
  `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk` for the matrix. That file is a gitignored build
  output; CI puts the real one there.
- **A red build's record is partial by design** (design D10): the coverage row then reports a failed check
  and the run exits 8, with `broken-main` still filing the regression. That path is proven on the host by
  `inputs#aRedBuildStillCarriesTheResultRecord`, not in CI.
- **`hotspot`'s violation** above is a real finding of this tree (5.9 MB in the scanned file set), recorded
  by a dry run; nothing was filed.
