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
- [x] 2.6 Focused green, forced: `SettingsStorageSourceTest` 6/0, `NotificationHostSendLoggingTest` 2/0,
      `EngineFaultHandlerTest` 4/0, `SessionCarSurfaceHostTest` 27/0, `NavigationEngineCalculationStateTest`
      6/0 — 45 tests, 0 failures, XML `2026-10-18T15:22:20Z`–`:22:30Z`. Evidence in `evidence/green/`.
- [x] 2.7 Scenario 8 filters on the calculation record's own marker (`ROUTE calc done:`), not the shared
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
      converted classes are judged inside the aggregate that produced both flakes. Tallies and the derived
      per-directory counts in `evidence/suites.txt`. Excluded: `:app` automotive (no flavour input changed) and
      every native task (no compiled production file).

## 5. Review and closure

- [x] 5.1 Independent read-only review of the change against its scenarios and XMLs; its findings are fixed
      (the two remaining whole-file checks, the unverified archive task, the unrecorded aggregate tally, the
      date and citation slips) and the fix re-reviewed by a second fresh reviewer, whose own findings — the
      shared `ROUTE` tag as scenario 8's filter, the pre-recorded PASS line, the green provenance, the class
      tally — are fixed in turn. Verdicts in `review-2.md`, `review-3.md` and `review-4.md`.
- [x] 5.2 `openspec validate fix-diagnostics-sink-attribution --strict` → valid; structural checks:
      `tools/check-doc-routes.sh` (all 89 references resolve), `tools/declared-cases-selftest.sh` (12 passed).
- [x] 5.3 `report.md` carries the scenario → case traceability, the red-on-HEAD tally, the mutations and the
      suite tallies.
- [x] 5.4 The change is archived under `openspec/changes/archive/` and the ADDED requirement merged into
      `openspec/specs/unit-test-suite-runtime/spec.md`; confirmed after the archive step.
