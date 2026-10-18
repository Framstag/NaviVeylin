# Independent review 2 (fresh read-only reviewer)

Scope: the change's 9 scenarios, the diff (5 test files + `guidelines/Design.md` §11), `evidence/**`,
`tasks.md`. Verdict as returned:

**REVIEW-VERDICT: FAIL**

## Scenario verdicts

| scenario | verdict |
|---|---|
| 1 `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` | PASS |
| 2 `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp` | PASS |
| 3 `NotificationHostSendLoggingTest.everyPostAddsExactlyOneEntry` | PASS |
| 4 `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn` | PASS |
| 5 `EngineFaultHandlerTest.aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce` | PASS |
| 6 `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands` | PASS |
| 7 `EngineFaultHandlerTest.theHandlerMirrorsToLogcatAndTheRecordReachesTheFile` | PASS |
| 8 `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` | **FAIL** |
| 9 `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` | PASS |

## Findings and disposition

| # | severity | finding | disposition |
|---|---|---|---|
| 1 | MAJOR | scenario 8's filter was the shared `ROUTE_TAG` (`"ROUTE"`), not the case's own marker: `RouteGeometry` (`ROUTE route geometry absent`), `RoutePanelViewModel` (`ROUTE route analysis`) and `NavigationEngine` all log under it, so a foreign `ROUTE` line could still redden the case — and the spec's THEN named `outcome=ok`, which the filter did not implement. Same weakness at the `entry` selection one line above | fixed: both places filter on the calculation record's own marker, `"ROUTE calc done:"` (`RouteCalculationDiagnostics.kt:29-40`); the THEN is aligned; the mutation re-recorded (`evidence/mutations/calculation-record-whole-sink.xml`, `15:22:06Z`, `6/1`) |
| 2 | MAJOR | `report.md` recorded "Review 2 — PASS" before this review ran, and no `review-2.md` existed | fixed: this file is the real record, and the report states the FAIL and its disposition |
| 3 | MINOR | the stored green XMLs were copies of the aggregate run, not of the focused run the report cited | fixed: `evidence/green/` now holds the focused run's own files (`2026-10-18T15:22:20Z`–`:22:30Z`), and `evidence/suites.txt` says which run produced which number |
| 4 | MINOR | `tasks.md` 2.6 claimed `NavigationEngineCalculationStateTest` 9/0; the class has 6 tests | fixed: 6/0, 45 tests in total |
| 5 | MINOR | `val file = initDiagnostics()` became unused in `NavigationEngineCalculationStateTest` | fixed: the binding is gone |
| 6 | MINOR | scenarios 5–8 filter at tag level, so a foreign record under the same tag would still count | accepted and disclosed: the tag is the closest stable attribution for a record whose coroutine name the case itself supplies; disclosed in `report.md` |
| 7 | MINOR | `guidelines/Design.md` §11 listed four markers while six are in use, and the sink-own exemption was not spelled out | fixed: the bullet names the sink-own test classes as the exemption |
| 8 | MINOR (outside the diff) | `RouteGeometryTest.kt:110` uses the shared `ROUTE` tag with `.last { }`; `SessionLogTest.kt:70` keeps a whole-sink negative | disclosed in `report.md`; not caused by this diff |
