# Wire the sensor pipeline's inputs, and turn its schedule back on

## Why

Issue **#161**: *the sensor pipeline never materialises its rows' inputs, so every scheduled run fails.* Four of
the registry's six rows read a file that no pipeline publishes, so each reports `error`, posts nothing and makes
the run exit 8 — the owner removed the `schedule` trigger on 2026-10-17 and left `sensors.yml` dispatch-only.
Nothing connected a row's declared `input` to a producer: that is the missing bound this change adds.

## Detail: the four declared inputs and who published them

| row | declared input | who published it before this change |
|---|---|---|
| `build-duration` | `app/build/gate-timings.json` | nobody — CI runs Gradle without `-I tools/gate-timings.init.gradle.kts`, and the record lands at `build/gate-timings.json` |
| `package-size` | `app/build/outputs/apk/mobileRelease/app-mobile-release.apk` | nobody — `build.yml` assembles the **debug** APK |
| `coverage` | `app/build/reports/jacoco/coverage.json` | nobody — CI produces Kover HTML/XML and a JaCoCo XML, never this JSON shape |
| `broken-main` | `app/build/gate-result.json` | nobody — no workflow wrote a build result record at all |

The sensor pipeline downloaded none of it, and only `pr-conflicts` worked because it reads the API rather than a
file. The registry's checks — the reason the registry exists — had not run since 2026-10-17.

**The bound**: a row may declare a path no pipeline writes, and the only thing that noticed was a scheduled run
reporting `error` an hour later. The bound is now a producer's own manifest, checked against the registry, and its
red case asserts it.

## What changes

1. **A publisher, `tools/build-inputs.sh`** (project-side, beside `gate-timings.init.gradle.kts`):
   `--write` materialises exactly the paths the rows declare — the gate timing record, the coverage JSON
   in the documented `{"modules":{…}}` shape (converted from the per-module kover XML and the
   `osmscout-client-java` JaCoCo XML), and a build result record; the package artifact it names but does
   not copy. `--check` reads the registry and **refuses, naming the row**, when a declared input is not
   among the paths it publishes. `--manifest` prints that set, so the bound and the producer cannot drift.
2. **A fetcher, `tools/fetch-build-inputs.sh`**: finds the latest completed `build.yml` run for a commit,
   downloads its `sensor-inputs` artifact into the working tree, and exits **3** when no record exists for
   that commit yet — so "the build has not finished yet" is not a failed check.
3. **`build.yml`** publishes the record: the Gradle steps run with the gate-timing init script, the
   coverage step adds the per-module kover XML reports, and one `if: always()` step runs `--write`, runs
   `--check`, and uploads the tree as the artifact `sensor-inputs`.
4. **`sensors.yml`** gains `actions: read` for itself, a fetch step before the rows, an `if:` so that the
   rows run only when the record for `GITHUB_SHA` exists, and the **schedule trigger back**
   (`0,30 * * * *`, matching the registry's declared minutes).
5. **The registry**: `build-duration`'s input corrected to `build/gate-timings.json`; `package-size`'s
   input moved to the debug APK CI actually builds — a deliberate deviation from "the shipped artifact",
   recorded in design D4 and reversible in one line when a release pipeline publishes the shipped APK.
6. **Cases and a falsification harness** for both scripts, a CI step that lists them, and the ownership
   rows in `guidelines/Backlog.md`.
7. **A defect the wiring exposed, fixed here** (it blocks the same promise): the two *condition* rows
   printed nothing when there was nothing to report — a green build with no recorded condition, a project
   with no conflicting change — and the runner reads a row that printed nothing as a **failed check**, so
   `broken-main` and `pr-conflicts` would have erred on every healthy run. Each now reports a clear naming
   its own identity, which is what "the condition is absent" is in the three-outcome contract; the runner's
   safety net (silence is a failure) is unchanged.

## Impact

| file | change |
|---|---|
| `tools/build-inputs.sh` | new — publisher, `--write` / `--check` / `--manifest` |
| `tools/fetch-build-inputs.sh` | new — fetch a commit's `sensor-inputs` artifact, exit 3 = not yet |
| `tools/build-inputs-cases.sh`, `tools/build-inputs-selftest.sh` | new — cases and their harness (stub `gh`, throwaway tree) |
| `tools/build-inputs-mutations.sh` | new — one mutation per invariant this change adds (revert-check) |
| `tools/sensor-mutations.sh` | unchanged; the new set is its own file (the sensors engine is not mutated here) |
| `.github/workflows/build.yml` | gate-timing init script on the Gradle steps; per-module kover XML; publish + upload the record; list the new cases and mutations |
| `.github/workflows/sensors.yml` | `actions: read`; fetch step; rows gated on the record; `schedule` restored; header note rewritten |
| `tools/sensors/sensors.json` | two `input` paths corrected (registry is configuration, not engine) |
| `tools/sensors/conditions/broken-main.sh`, `conditions/pr-conflicts.sh` | a row with nothing to report reports a clear, so a healthy run is not a failed check |
| `tools/sensor-cases.sh` | two cases for those clears and the token rule; the pipeline case now asserts *which* step uses the workflow's own token |
| `guidelines/Backlog.md` | the sensor section's claim about the schedule, and the publisher/fetcher's ownership row |
| `openspec/specs/backlog-sensors/spec.md` | one requirement modified, one added (this change's deltas) |

No compiled file changes: no Kotlin, C++ or Gradle build logic. The verification is the shell gates
(`guidelines/Build.md` §2, scope block): the new selftests, the sensor suite, the mutation sets, and the
structural checks over the two workflow files.

**Additive, not breaking.** The registry's rows, the runner, the report and every row's measurement are
untouched. **Rollback**: delete the two workflow steps and the `schedule` line, restore the two registry
paths — the pipeline is back to today's dispatch-only state.

## Related

- Supersedes nothing; completes the wiring issue #161 deferred, and leaves the PAT scope question in #160
  untouched (no personal token gains a scope here).
- Files issue **#166**: the engine matches a row's cadence against the run's own minute exactly, so a tick
  GitHub fires late selects no row and exits 0 — measured on the host
  (`--now 2026-10-18T00:00:00Z` selects `broken-main`, `--now 2026-10-18T00:07:00Z` selects nothing). It is
  a property of the selection, not of the wiring, so it is filed rather than folded in.
