# Design — fix-diagnostics-sink-attribution

## Root cause

`DiagnosticsLog` keeps its target file and its pending ring in process-wide state (the statics at
`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:80`, `:95`, `:106`); `initForTest(file)` (`:175`)
re-points that one target through `configureTarget` (`:210-224`) for whoever runs next. A case therefore does
not own the file it installs: anything else in the same JVM that logs while the case holds it — a
`MapCanvasViewModel` coroutine a previous class left running, the warm-up traffic of a neighbouring class — is
buffered in the same ring and flushed to the same file by the same worker (`workerLoop`, `:455-520`). Nine
assertions in the suite then measure that file as if
it held only their own subject's lines: a whole-text `contains("52.")`, and `entries.size` / `entries.single()`
over `DiagnosticsLog.readEntries()`. The defect is in the assertion, not in the sink: the file is a
diagnostics stream, and one shared stream is what the app wants.

## The single fix

**Every assertion on the diagnostics stream narrows to the lines its own subject wrote**, using the tag or
message marker that only its subject's entries carry, and keeps its count, order and content assertions on
that filtered list:

| case | marker |
|---|---|
| `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` | `map source unknown value=` (`MapSourceSettings.kt:20`) |
| `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn` | `repository source without a base URL` (`MapSourceSettings.kt:30`) |
| `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp`, `everyPostAddsExactlyOneEntry` | `HOST NOTIF post` (`NavigationNotificationService`) |
| `EngineFaultHandlerTest.aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce`, `aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands`, `theHandlerMirrorsToLogcatAndTheRecordReachesTheFile` | `ENGINE_FAULT_TAG` |
| `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` | `ROUTE calc done:` (`RouteCalculationDiagnostics.kt:29-40`) |
| `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis` | `HOST surface` |

Each converted case keeps one line logged under the tag `OTHER` (a coordinate-shaped number in it) as the
reproduction the red run used: the line is what another class would have written, and the case now proves it
cannot decide the result. The count assertions stay — they now count own lines, so a duplicated own entry
still fails, and a missing one still fails.

## Alternatives the evidence eliminated

1. **`DiagnosticsLog.reset()` in `@Before`, the item's stated precondition for a file-wide grep.** Impossible:
   both flaky classes already reset before they install their target (`SettingsStorageSourceTest.setUp`,
   `NotificationHostSendLoggingTest` uses a fresh `TemporaryFolder`), and the foreign entries still arrived
   *inside* the case's own window — the reset closes the window before the target, not the window after it.
2. **A per-case target that only the case's code can write.** Impossible at the seam: the offending line comes
   from async work the library still runs, which carries no case identity, and production needs exactly one
   process-wide file (`DiagnosticsLog.init`, `:164-172` — its comment records why a stale per-test pointer must
   not survive). Any target-level rule would have to know *who* logged, which is not available.
3. **Dropping the coordinate assertion and leaving the rule to `DiagnosticsCoordinatePrivacyTest`.** Not a
   second fix — the item offers it as the same one ("assert the line's own text"). The filter keeps both halves
   of the assertion at the cost of one call, so the weaker variant is not taken.

## Blast radius

Test sources only, five files in two modules, plus one guideline section and one spec requirement. No
production file, no API, no threading, no lifecycle, no flavour input — therefore no gate obligation beyond
the affected cases (`guidelines/Build.md` §2, the scope block: a delta that touches test sources runs the
changed cases). The risk a filter introduces is a hidden line: a duplicated or missing own entry. Both are
still caught, because every converted assertion counts its own lines rather than asserting presence alone.

## Rollback

Revert the five test files; delete the §11 bullet and the added requirement. Nothing references either.

## Verification

- Red: the seven cases named in the proposal's Why, on base `184ba56`, with the foreign line injected and the
  HEAD assertions untouched — `:app` 12 tests / 6 failures (XML `2026-10-18T15:06:12Z`), `:auto` 27 tests /
  1 failure (XML `2026-10-18T15:06:35Z`). The other two converted assertions are whole-file *presence* checks,
  which a foreign line cannot redden; they are converted for the rule's sake and guarded by their own count.
- Green: the same five classes forced, `:app` 18 tests / 0 failures (6 + 2 + 4 + 6), `:auto` 27 tests /
  0 failures — 45 in total (`evidence/green/`).
- Neighbours: the `:app` mobile and `:auto` suites forced, so the converted classes are judged in the loaded
  aggregate that produced both flakes.
- Revert-check: drop one filter (make the assertion whole-sink again) — that case must fail, restored green
  forced with `-PforceTests --no-build-cache`.
- No on-device check: the change is a host-test assertion; nothing it changes is observable on a device.
