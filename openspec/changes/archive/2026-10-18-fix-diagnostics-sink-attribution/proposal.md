# A diagnostics assertion is attributed to the lines its own case wrote

## Why

Issue **#115** (`§181`) — two observed flakes of the same shape, both in a full forced gate:

1. `car-favorites-group-level` task 4.2, found 2026-10-10 (that gate attempt started 14:03:26Z, `:app` mobile) failed
   exactly one case: `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn` with
   `the diagnostics line carries no coordinate`
   (`app/src/test/java/com/naviveylin/data/SettingsStorageSourceTest.kt:95`, `assertFalse(logged.contains("52."))`).
   The class passes alone and in a single-JVM pair with `DiagnosticsCoordinatePrivacyTest`; the immediate
   re-run of the whole gate was green (5107 cases, 0 failures).
2. `fix-browse-recenter-dwell` task 5.7 (first gate attempt, 2026-10-11, `:app` mobile, `BUILD FAILED in
   4m 11s`) failed exactly one case: `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp`
   (`app/src/test/java/com/naviveylin/service/NotificationHostSendLoggingTest.kt:45`) with
   `expected:<1> but was:<5>` — four extra entries in its fresh `TemporaryFolder` file.

**Root cause (one mechanism).** `DiagnosticsLog`'s file target and pending ring are process-wide (its statics
at `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:80`, `:95`, `:106`; `initForTest` at `:175`
re-points that one target through `configureTarget`, `:210-224`), so a line that *any* code in the same JVM
enqueues while a case holds its target — work a previous class left running, not a line this case asked for —
is flushed into *this* case's file, and the cases above assert over that file's whole contents
(`logged.contains("52.")` at `SettingsStorageSourceTest.kt:95`; `assertEquals(1, entries.size)` at
`NotificationHostSendLoggingTest.kt:45` and `assertEquals(2, DiagnosticsLog.readEntries().size)` at `:62`) as if
it could only hold their own lines. `"52."` is not even a coordinate test: any `…52.…` number satisfies it.
The coordinate rule itself (`guidelines/Logging.md` §3, the `checkNoCoordinatesInLogs` gate,
`DiagnosticsCoordinatePrivacyTest`) is intact — what flakes is the ability to attribute a line to the case
that produced it.

**The same shape in six more assertions**, found while bounding the fix (a total `entries.size`, a whole-file
substring or a `single { }` over the shared sink is the same assertion): `EngineFaultHandlerTest.kt:49`, `:76`
and `:112` (`:app`), `SessionCarSurfaceHostTest.kt:232` (`:auto`,
`assertEquals("one adopt, one release", 2, entries.size)`), `SettingsStorageSourceTest.kt:120` and
`NavigationEngineCalculationStateTest.kt:229` (both whole-file `readText().contains(...)`). Every other
diagnostics assertion in the suite already narrows to its own tag or marker (`entries.any { }`,
`filter { }`), and the sink's own test classes are the exception by subject, so this change closes the shape,
not a sample of it.

**Red on HEAD** — the foreign line injected, the HEAD assertion untouched, run at base `184ba56`:

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.SettingsStorageSourceTest" \
  --tests "com.naviveylin.service.NotificationHostSendLoggingTest" \
  --tests "com.naviveylin.navigation.EngineFaultHandlerTest" -PforceTests --no-build-cache
12 tests completed, 6 failed        (XML 2026-10-18T15:06:12Z)
./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SessionCarSurfaceHostTest" -PforceTests --no-build-cache
27 tests, 1 failure                 (XML 2026-10-18T15:06:35Z)
```

Failing cases and messages: `unknownStoredSourceFallsBackToBuiltIn` — `the diagnostics line carries no
coordinate`; `aPostIsRecordedWithItsContentAndTimestamp` — `expected:<1> but was:<2>`;
`everyPostAddsExactlyOneEntry` — `expected:<2> but was:<3>`; `aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce`,
`aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands` and
`theHandlerMirrorsToLogcatAndTheRecordReachesTheFile` — `expected:<1> but was:<2>`;
`surfaceLifetimeIsRecordedForDiagnosis` — `one adopt, one release expected:<2> but was:<3>`.

**Owning requirement**: `unit-test-suite-runtime` — "A module's failure set is independent of host load and
concurrency" (the failing class must not depend on what else ran in the fork). This change adds one
requirement next to it: "A diagnostics assertion is attributed to the lines its own case wrote".

**Owning guideline**: `guidelines/Design.md` §11 "Testing" — the bullet that already refuses an invented
count and a logger re-init between act and assert (§11, "Assert what the contract requires — never an invented
count", "Assert the invariant, not 'nothing happened'").

## What changes

1. **The nine whole-sink assertions name the lines their own subject wrote** — the fallback line by its
   `map source unknown value=` / `repository source without a base URL` marker, a post by `HOST NOTIF post`, a
   fault record by `ENGINE_FAULT_TAG`, a calculation record by its `ROUTE calc done:` marker, a surface entry
   by `HOST surface` — and keep count/order/content assertions on that filtered list.
2. **Each converted case keeps a simulated foreign line** as the reproduction the red run above used, so the
   next whole-sink assertion regression is red on the spot rather than in a loaded gate.
3. **`guidelines/Design.md` §11 gains the rule** the two flakes proved, with their measurements.

Test sources and one guideline section only: no production code, no behaviour, no API. Additive.
**Rollback**: revert the four test files and drop the §11 bullet and the added requirement — nothing else
depends on them.

## Impact

| file | change |
|---|---|
| `app/src/test/java/com/naviveylin/data/SettingsStorageSourceTest.kt` | both fallback cases filter to their own line before the coordinate/content assertions |
| `app/src/test/java/com/naviveylin/service/NotificationHostSendLoggingTest.kt` | both cases count `HOST NOTIF post` entries, not entries |
| `app/src/test/java/com/naviveylin/navigation/EngineFaultHandlerTest.kt` | three cases count/read `ENGINE_FAULT_TAG` entries |
| `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationStateTest.kt` | the calculation-record case counts its own `ROUTE_TAG` entry in the file |
| `auto/src/test/java/com/naviveylin/auto/SessionCarSurfaceHostTest.kt` | the surface-lifetime case counts `HOST surface` entries |
| `openspec/specs/unit-test-suite-runtime/spec.md` | one ADDED requirement, nine scenarios, one case each |
| `guidelines/Design.md` §11 | the attribution rule with its two measurements |
