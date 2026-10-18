# Tasks — fix-diagnostics-sink-attribution

Parent requirement: `unit-test-suite-runtime`, "A diagnostics assertion is attributed to the lines its own
case wrote" (one ADDED requirement, nine scenarios, one case each). `[#115]` marks the tracker item this
closes (§181).

## 1. The red cases first

- [x] 1.1 One line logged under the tag `OTHER` (with a coordinate-shaped number in it) is added to each
      converted case, and the HEAD assertions are left untouched — the state another class's line leaves the
      shared sink in. Verifies the seven scenarios whose HEAD assertion a foreign line can redden.
- [x] 1.2 Red on HEAD recorded: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.data.SettingsStorageSourceTest" --tests
      "com.naviveylin.service.NotificationHostSendLoggingTest" --tests
      "com.naviveylin.navigation.EngineFaultHandlerTest" -PforceTests --no-build-cache` → `12 tests completed,
      6 failed`, XML `2026-10-18T15:06:12Z` (`SettingsStorageSourceTest` 6/1, `NotificationHostSendLoggingTest`
      2/2, `EngineFaultHandlerTest` 4/3); `./gradlew :auto:testDebugUnitTest --tests
      "com.naviveylin.auto.SessionCarSurfaceHostTest" -PforceTests --no-build-cache` → 27 tests, 1 failure, XML
      `2026-10-18T15:06:35Z`. Messages: `the diagnostics line carries no coordinate`, `expected:<1> but was:<2>`,
      `expected:<2> but was:<3>`, `one adopt, one release expected:<2> but was:<3>`. XMLs in `evidence/red/`.
      No production code touched.

## 2. The fix

- [x] 2.1 `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` filters the file's lines to
      `map source unknown value=`, asserts count 1 and checks the stored value and the absence of a
      coordinate-shaped token on that line. Verifies *a foreign line does not decide the fallback case*.
- [x] 2.2 `NotificationHostSendLoggingTest`'s two cases count `HOST NOTIF post` entries instead of entries.
      Verifies *a foreign line is not counted as a post* and *a sequence of posts is counted as posts*.
- [x] 2.3 `EngineFaultHandlerTest`'s three entry assertions count/read `ENGINE_FAULT_TAG` entries. Verifies the
      three fault-record scenarios.
- [x] 2.4 `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` counts/orders `HOST surface`
      entries. Verifies *a foreign line is not a surface entry*.
- [x] 2.5 The two whole-file *presence* checks — `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn`
      and `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` — filter to
      their own marker/`ROUTE_TAG` and count 1, so a duplicated own line fails and a foreign line is never
      evidence. Verifies *a foreign line is not a fallback line* and *a foreign line is not the calculation
      record*. (A foreign line cannot redden a presence check, so these two are converted for the rule's sake
      rather than from a red run; their guard is the count.)
- [x] 2.6 Focused green, forced, two runs: the `:app` run `SettingsStorageSourceTest` 6/0,
      `NotificationHostSendLoggingTest` 2/0, `EngineFaultHandlerTest` 4/0,
      `NavigationEngineCalculationStateTest` 6/0, XML `2026-10-18T15:22:20Z`–`:15:22:26Z`; the `:auto` case was
      re-run on its own after review 5's cosmetic edit — `SessionCarSurfaceHostTest` 27/0, XML
      `2026-10-18T15:33:31Z`. The 45/0 tally is the composite of both runs, not one run's XML. Evidence in
      `evidence/green/`.
- [x] 2.7 Scenario 9 filters on the calculation record's own marker (`ROUTE calc done:`), not the shared
      `ROUTE` tag which the geometry, panel and reroute paths also log. Verifies *a foreign line is not the
      calculation record* with a marker only this case's subject writes.

## 3. Falsification

- [x] 3.1 Mutation: `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands` reads
      `DiagnosticsLog.readEntries()` again instead of the filtered list — the case fails
      (`expected:<1> but was:<2>`, XML `2026-10-18T15:08:21Z`); restored, forced green.
- [x] 3.2 Mutation: `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` counts
      `DiagnosticsLog.readEntries()` again — `one adopt, one release expected:<2> but was:<3>`, XML
      `2026-10-18T15:08:34Z`; restored, forced green.
- [x] 3.3 Mutation: `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn` loses its filter
      and counts the file's lines — the case fails; restored, forced green. (Guards the two conversions that
      no red run could produce.)
- [x] 3.4 Mutation: `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`
      loses its `ROUTE calc done:` filter — `the configured file holds this case's calculation entry and
      nothing else under the marker …`, XML `2026-10-18T15:22:06Z`; restored, forced green.

## 4. The rule and the neighbours

- [x] 4.1 `guidelines/Design.md` §11 gains the attribution rule with its two measurements (2026-10-10
      `car-favorites-group-level` 4.2, 2026-10-11 `fix-browse-recenter-dwell` 5.7) and the sink-own-tests
      exemption. Its own check is the scenario-to-case table above, since a guideline edit runs no build.
- [x] 4.2 Neighbours: the `:app` mobile and `:auto` suites forced (`-PforceTests --no-build-cache`), so the
      converted classes are judged inside the aggregate that produced both flakes. Tallies in
      `evidence/suites.txt`; the `:app` tally reproduces from `app/build/test-results/testMobileDebugUnitTest`,
      and the `:auto` aggregate's own XML is stored in `evidence/auto-aggregate/` (its build directory was
      overwritten by the later focused re-run — see 2.6). Excluded: `:app` automotive (no flavour input
      changed) and every native task (no compiled production file).

## 5. Review and closure

- [x] 5.1 Independent read-only review of the change against its scenarios and XMLs; its findings are fixed
      (the two remaining whole-file checks, the unverified archive task, the unrecorded aggregate tally, the
      date and citation slips) and the fix re-reviewed by a second fresh reviewer, whose own findings — the
      shared `ROUTE` tag as scenario 9's filter, the pre-recorded PASS line, the green provenance, the class
      tally — are fixed in turn. Verdicts in `review-2.md`, `review-3.md` and `review-4.md`.
- [x] 5.2 `openspec validate fix-diagnostics-sink-attribution --strict` → valid; structural checks:
      `tools/check-doc-routes.sh` (all 89 references resolve), `tools/declared-cases-selftest.sh` (12 passed).
- [x] 5.3 `report.md` carries the scenario → case traceability, the red-on-HEAD tally, the mutations and the
      suite tallies.
- [x] 5.4 The change is archived under `openspec/changes/archive/` and the ADDED requirement merged into
      `openspec/specs/unit-test-suite-runtime/spec.md`; confirmed after the archive step.

## 6. Round 2 — the review of the archived change

No compiled file, no test source and no assertion changes in this round; the four items below are spec,
guideline and evidence corrections, so no build is owed (the `:auto` suite in 6.2 was run to re-measure a
lost artifact, not to refresh a green one).

- [x] 6.1 The ADDED requirement now states its binding scope and names the four cases that still decide from
      the whole file (`RouteGeometryTest.kt:110`, `NavigationEngineRerouteTest.kt:486`,
      `CarHostGuardsTest.kt:69`, `SessionLogTest.kt:70`) in a "Residual paths outside this change" block, in
      both the merged spec and the archive delta (identical text, verified by `diff`). Its prose stays under
      openspec's 500-character requirement-text warning: `openspec validate unit-test-suite-runtime --strict
      --type spec` now reports one warning, at `requirements[7]` ("A Compose case starts from a repaired
      sandbox"), which this change does not touch. `guidelines/Design.md` §11 points at the requirement.
- [x] 6.2 The `:auto` aggregate is stored: `./gradlew :auto:testDebugUnitTest -PforceTests --rerun-tasks` →
      85 classes / 864 tests / 0 failures, XML `2026-10-18T15:37:16Z`–`:15:37:38Z`;
      `evidence/auto-aggregate/TEST-*.xml` (85 files) plus `evidence/auto-aggregate/per-class.tsv`.
      `evidence/suites.txt` states that the earlier `:auto` directory was overwritten by the `15:33:31Z`
      focused re-run. Verifies *the neighbour aggregate is carried by an artifact*.
- [x] 6.3 `guidelines/Design.md` §11 separates the markers from tag-level attribution (`ENGINE_FAULT_TAG`:
      a foreign record under that tag still counts).
- [x] 6.4 `tasks.md` 2.6, `report.md`, `design.md` and `SUMMARY.txt` corrected: the green tally is a composite
      of two runs, the report's scenario numbers follow the delta's order, and `design.md` no longer claims
      the change converted every diagnostics assertion.
