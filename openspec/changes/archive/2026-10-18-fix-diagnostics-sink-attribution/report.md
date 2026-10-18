# Report — fix-diagnostics-sink-attribution

Closes tracker item **#115** (§181). Base commit `184ba56`.

## What the defect was

`DiagnosticsLog`'s target file and pending ring are process-wide, so a line logged by *any* work running in
the same JVM while a test case holds its target is flushed into that case's file. Nine assertions in the suite
measured that file as if it could only hold their own subject's lines: a whole-file `contains("52.")`, six
total `entries.size` counts and two whole-file `readText().contains(…)` presence checks. Two of them flaked in
full gate runs the owners could not attribute (2026-10-10 `car-favorites-group-level` 4.2; 2026-10-11
`fix-browse-recenter-dwell` 5.7).

## Scenario → case traceability

| # | requirement scenario | case | evidence |
|---|---|---|---|
| 1 | A foreign line does not decide the fallback case | `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` | red 6/1 · green 6/0 |
| 2 | A foreign line is not counted as a post | `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp` | red 2/2 · green 2/0 |
| 3 | A sequence of posts is counted as posts | `NotificationHostSendLoggingTest.everyPostAddsExactlyOneEntry` | red 2/2 (`expected:<2> but was:<3>`) · green 2/0 |
| 4 | A foreign line is not a fallback line | `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn` | no red possible (presence check) · mutation `settings-fallback-whole-sink.xml` 6/1 · green 6/0 |
| 5 | A foreign line is not a fault record | `EngineFaultHandlerTest.aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce` | red 4/3 · green 4/0 |
| 6 | A foreign line is not a second fault record | `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands` | red 4/3 · mutation `engine-fault-whole-sink.xml` 4/1 · green 4/0 |
| 7 | A foreign line is not a fault record for the file assertion | `EngineFaultHandlerTest.theHandlerMirrorsToLogcatAndTheRecordReachesTheFile` | red 4/3 · green 4/0 |
| 8 | A foreign line is not the calculation record | `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` | no red possible (presence check) · mutation `calculation-record-whole-sink.xml` 6/1 · green 6/0 |
| 9 | A foreign line is not a surface entry | `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` | red 27/1 · mutation `auto-surface-whole-sink.xml` 27/1 · green 27/0 |

## Red on HEAD (base `184ba56`, HEAD assertions untouched, the foreign line injected)

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.SettingsStorageSourceTest" \
  --tests "com.naviveylin.service.NotificationHostSendLoggingTest" \
  --tests "com.naviveylin.navigation.EngineFaultHandlerTest" -PforceTests --no-build-cache
12 tests completed, 6 failed            XML 2026-10-18T15:06:12Z / :27.191Z / :12.997Z
./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SessionCarSurfaceHostTest" \
  -PforceTests --no-build-cache
27 tests completed, 1 failed            XML 2026-10-18T15:06:35Z
```

Messages, verbatim: `the diagnostics line carries no coordinate`; `expected:<1> but was:<2>`;
`expected:<2> but was:<3>`; `the record is written before the publish step expected:<1> but was:<2>`;
`one adopt, one release expected:<2> but was:<3>`. XMLs in `evidence/red/`.

## Green after the fix

Focused, forced, `evidence/green/` holds that run's own XML, `2026-10-18T15:22:20Z`–`:15:22:30Z`:
`SettingsStorageSourceTest` 6/0, `NotificationHostSendLoggingTest` 2/0, `EngineFaultHandlerTest` 4/0,
`NavigationEngineCalculationStateTest` 6/0, `SessionCarSurfaceHostTest` 27/0 — 45 tests, 0 failures. The
`:auto` file in `evidence/green/` was refreshed after review 5's cosmetic edit (XML `2026-10-18T15:33:31Z`);
the other four are the focused run's files (`2026-10-18T15:22:20Z`–`:15:22:26Z`).

Neighbours — the aggregate that produced both flakes (`evidence/suites.txt`, numbers derived by awk over that
run's own JUnit XML; the per-class XML of the five changed classes is stored from the focused run instead):

```
./gradlew :app:testMobileDebugUnitTest :auto:testDebugUnitTest -PforceTests --no-build-cache
BUILD SUCCESSFUL in 1m 59s          (15:22:47Z – 15:24:41Z)
:app mobile  275 classes / 2059 tests / 0 failures / 0 errors
auto         85 classes /  864 tests / 0 failures / 0 errors
```

Excluded and why: `:app` automotive (the change touches only sources both flavours share, no flavour input —
`guidelines/Build.md` §4 "Which flavors a run must cover"), and every native task (no compiled production
file). The change touches test sources only, so no full gate is owed (`guidelines/Build.md` §2, scope block);
the two suites above are the loaded aggregate the flakes came from.

## Falsification (revert-check)

| mutation | case that must fail | result |
|---|---|---|
| `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands` reads `readEntries()` again | that case | `the record is written before the publish step expected:<1> but was:<2>`, XML `2026-10-18T15:08:21Z`; restored, forced green |
| `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` counts `readEntries()` again | that case | `one adopt, one release expected:<2> but was:<3>`, XML `2026-10-18T15:08:34Z`; restored, forced green |
| `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn` loses its filter | that case | `the fallback logs exactly one line … expected:<1> but was:<2>`, XML `2026-10-18T15:15:13Z`; restored, forced green |
| `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` loses its `ROUTE calc done:` filter | that case | `the configured file holds this case's calculation entry and nothing else under the marker …`, XML `2026-10-18T15:22:06Z`; restored, forced green |

The two presence checks need the mutation rather than a red run: a foreign line can only *add* text, so it
cannot redden a `contains(…)` assertion. Their guard is the count/filter they now carry, which the mutations
prove.

## Structural checks

`openspec validate fix-diagnostics-sink-attribution --strict` → *valid*. `bash tools/check-doc-routes.sh` →
all 89 section references resolve. `bash tools/declared-cases-selftest.sh` → 12 passed, 0 failed.

## Independent review

Read-only reviewers, none of them the author. Each verdict after the first is recorded in its own file
(`review-2.md`, `review-3.md`, `review-4.md`); review 1's findings were returned in-session and are recorded
only in this section — the one review with no file. A verdict is written into a review file only after that
review has been given.

**Review 1 — FAIL.** Seven scenarios PASS, with one CRITICAL and two MAJOR findings:

| finding | disposition |
|---|---|
| CRITICAL: the claim "closes the shape" was false — `SettingsStorageSourceTest.kt:120` and `NavigationEngineCalculationStateTest.kt:229` still decided from the whole file | fixed: both now filter to their own marker/`ROUTE_TAG` and count; scenarios 4 and 8 added, two mutations recorded |
| MAJOR: `tasks.md` 5.2 was marked done for an archive that had not happened | fixed: the archive task is unchecked until the archive step runs, and is checked afterwards |
| MAJOR: the aggregate neighbour tally had no stored evidence | fixed: `evidence/suites.txt` now states how the numbers were derived (awk over the run's own XML in the two result directories) |
| MINOR: the sink's own test classes were not named as an exemption | fixed in the §11 bullet and here: `DiagnosticsLogTest`, `DiagnosticsLogRetentionTest`, `DiagnosticsLogWritePathTest` assert on the sink itself, so the file is their subject |
| MINOR: dates disagreed between artifacts | fixed: 2026-10-10 for `car-favorites-group-level` 4.2, 2026-10-11 for `fix-browse-recenter-dwell` 5.7, everywhere |
| MINOR: `DiagnosticsLog.kt:210-224` described as the statics | fixed: statics named at `:80`/`:95`/`:106`, `configureTarget` at `:210-224` |
| MINOR: duplicate evidence copies at `evidence/` root | deleted |
| MINOR: the §11 bullet omitted the fallback marker | added |
| MINOR: `contains("52.")` is a weak predicate | tightened to the coordinate-shaped regex the privacy class uses |

**Review 2 — FAIL** (`review-2.md`). Seven scenarios PASS, scenario 8 FAIL, two MAJOR:

| finding | disposition |
|---|---|
| MAJOR: scenario 8 filtered on the shared `ROUTE` tag, so a foreign `ROUTE` line could still redden it, and its THEN (`outcome=ok`) was not what the assertion implemented | fixed: both the entry selection and the file assertion use the calculation record's own marker `ROUTE calc done:`; THEN aligned; mutation re-recorded (`2026-10-18T15:22:06Z`, 6/1) |
| MAJOR: the report pre-recorded a review-2 PASS that had not happened | fixed: `review-2.md` is the real record, and this section states the FAIL |
| MINOR: the stored green XMLs were the aggregate run's files while the report cited a focused run | fixed: `evidence/green/` holds the focused run's own files (`15:22:20Z`–`:22:30Z`) and `evidence/suites.txt` says which run produced which number |
| MINOR: `NavigationEngineCalculationStateTest` was recorded as 9/0 | fixed: 6/0, 45 in total |
| MINOR: an unused `val file = initDiagnostics()` remained | fixed |
| MINOR: scenarios 5–8 filter at tag level, so a foreign record under the same tag would still count | accepted, disclosed below |
| MINOR: the §11 bullet listed four markers and did not spell out the sink-own exemption | fixed |
| MINOR: `RouteGeometryTest.kt:110` and `SessionLogTest.kt:70` carry the same shapes outside this diff (`ui/route/RouteGeometryTest.kt` for the first) | disclosed below; not caused by this change |

**Review 3 — FAIL** (`review-3.md`). All nine scenarios PASS; one P0, one P1 and three P2:

| finding | disposition |
|---|---|
| P0: `report.md` again pre-recorded a verdict that had not happened ("Review 3 — PASS", and "Two read-only reviewers" with three listed) | fixed: this section states review 3's real verdict, and no verdict line is written before its review file exists |
| P1: the residual list claimed exhaustiveness and missed two shapes (`CarHostGuardsTest.kt:69`, `NavigationEngineRerouteTest.kt:486`) | fixed: both named below; no code change owed, both are outside the diff |
| P2: arithmetic — six `entries.size` counts, not seven; "four more assertions" while six were listed; `design.md` still said four test files and 12 `:app` tests | fixed: six / six / five files and 18 `:app` tests |
| P2: scenario 1's THEN ignored the count, which also fails on a duplicated or missing own line | fixed in the spec delta |

**Review 4 — FAIL** (`review-4.md`). All nine scenarios PASS; test code and evidence confirmed sound. Its
findings were about this section's own bookkeeping:

| finding | disposition |
|---|---|
| P0: the intro said "two" reviewers while three were listed, and review 1 has no file although a verdict is recorded for it | fixed: the paragraph no longer counts, names review 1 as the one fileless review, and states when a verdict is written into a file |
| P2: `evidence/suites.txt` said the focused re-runs came "later" than the aggregate; they predate it | fixed: the chronology is stated as it is |
| P2: `RouteGeometryTest.kt:110` is ambiguous (two classes share the name) | fixed: cited as `app/src/test/java/com/naviveylin/ui/route/RouteGeometryTest.kt:110` |

**Review 5 — PASS** (`review-5.md`): nine scenarios PASS, no P0 and no P1. Four P2 — the severity tally of
review 3, the short path in the historical review-2 row, scenario 1's THEN not naming the "stored value"
condition, and a fully-qualified `DiagnosticsLog.log` call in `SessionCarSurfaceHostTest`. All four are fixed;
the assertions and their scenarios are unchanged by them, and the `:auto` class was re-run forced afterwards
(XML `2026-10-18T15:33:31Z`, 27 tests, 0 failures).

## Notes for the reader

- No production file changed; the app's diagnostics behaviour is untouched, so nothing here is observable on
  a device and no device measurement applies.
- **Disclosed residuals.** Scenarios 5–7 attribute by tag (`ENGINE_FAULT_TAG`), which is the closest stable
  identity for a fault record a case provokes itself through a coroutine name; a foreign record under that
  tag would still count. Four shapes outside this diff carry the same weakness and were left alone:
  `app/src/test/java/com/naviveylin/ui/route/RouteGeometryTest.kt:110` (`ROUTE` tag with `.last { }`),
  `NavigationEngineRerouteTest.kt:486`
  (`single { }` on the shared `REROUTE` tag), `CarHostGuardsTest.kt:69` (a whole-sink negative,
  `entries().none { it.contains("rejected") }`) and `SessionLogTest.kt:70` (the same, on
  `geo:1.0,2.0` — only a line carrying that exact string could redden it).
- The sink's own test classes (`DiagnosticsLogTest`, `DiagnosticsLogRetentionTest`,
  `DiagnosticsLogWritePathTest`) are the exemption the §11 bullet names: the file *is* their subject.
- Each converted case keeps a line logged under the tag `OTHER` as the standing reproduction: the next
  regression to a whole-sink assertion is red in an idle focused run, not only in a loaded gate.
