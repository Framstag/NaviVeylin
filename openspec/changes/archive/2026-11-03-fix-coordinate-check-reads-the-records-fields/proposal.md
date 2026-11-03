# The coordinate check reads the record's fields, not the line's timing numbers

## Why

Issue #728. Two diagnostics cases decide "this line carries no coordinate" on a substring of the **whole line**
the reader returns, and that line carries numbers that are not positions (the two cites below are the lines at
the run's base, `4367cb4d`; this change rewrites them):

- `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationStateTest.kt:230-231` —
  `assertFalse("no coordinate precision in the entry", entry.contains("52.5"))` and
  `assertFalse("no coordinate precision in the entry", entry.contains("13.4"))`.
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt:498-501` —
  `assertFalse("no coordinate reaches the line", line.contains("52.5") || line.contains("13.45"))`.

`DiagnosticsLog.readEntries()` returns each line as `DiagnosticsLog` wrote it, and
`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:156` formats the prefix every line carries as
`SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)`. That stamp's `ss.SSS` field **is** the forbidden text
whenever the wall clock reads second `:13` with millisecond `4xx` — the stamp `[2026-10-31 13:41:13.412]`
contains `13.4` — and second `:52` with millisecond `5xx` contains `52.5`. The calculation record also carries
its own measurements (`duration=13ms`, `RouteCalculationDiagnostics.kt:37`), and the reroute record carries a
decimal distance (`deviation_m=40.0`, and `13.45` for a 13.45 m deviation), so a measurement satisfies the
check as well.

So the gate's answer for these two cases is decided by elapsed real time, which is the item's own words: "the
gate's green/red answer can be produced by a duration value, so an unrelated red is indistinguishable from a
real one without the per-case re-run". The red the item quotes is that assertion:
`:app:testAutomotiveDebugUnitTest` reported 2212 tests, 1 failed, `AssertionError: no coordinate precision in
the entry` at `NavigationEngineCalculationStateTest.kt:231`, green alone and green on the next aggregate run.

Reproduced on the tree at the run's base `4367cb4d` (a reading, not a build): a line stamped
`[2026-11-03 13:41:13.412]` and carrying `ROUTE calc done: … duration=13ms …` satisfies HEAD's check twice over —
and the same run with the stamp pinned to `13:41:13.412` reddens the case on HEAD — `1` failure of the six in
that class, at `NavigationEngineCalculationStateTest.kt:231` (the XML is quoted in `tasks.md` and carried under
`evidence/`).

```
$ printf '%s\n' '[2026-11-03 13:41:13.412] ROUTE calc done: source=acquisition duration=13ms percents=2 outcome=ok reachedPct=-1 reason=- nearestRoadM=-1' | grep -c '13\.4'
1
$ printf '%s\n' '[2026-11-03 13:52:52.512] reroute confirmed offroute_ms=9000 confirm_ms=0 deviation_m=13.45' | grep -c '52\.5\|13\.45'
1
```

and the same run with the stamp pinned to `13:41:13.412` reddens the case on HEAD — `1` failure of the six in
that class, at `NavigationEngineCalculationStateTest.kt:231` (the XML is quoted in `tasks.md` and carried under
`evidence/`).

The owning capability is `unit-test-suite-runtime` — "A module's failure set is independent of host load and
concurrency", whose scenario "A formerly flaky case is decided by its subject" already states the rule
("none of them passes or fails on the strength of elapsed real time")
(`openspec/specs/unit-test-suite-runtime/spec.md:166-180`). The guideline section that owns a diagnostics
assertion is `guidelines/BuildTests.md` §6f "Diagnosing a red run" (`AGENTS.md`'s row "writing or fixing a
test"), and the shape a coordinate is told by is `auto-diagnostics`'s "Diagnostics carry no coordinates".

Surface, counted rather than assumed (`grep -rn 'assertFalse' -A 3 app/src/test --include=*.kt` piped through
`grep 'contains("5\|contains("13'`, and the same over `auto/src/test`, `core/src/test`):

- **0 cases added, 2 rewritten, 0 fixtures realigned** — the two cases above.
- The change's own numbers are the loop's measurement of it, quoted rather than re-derived
  (`evidence/measure-block.txt`): `LOOP-MEASURE base=55b5d658f079ada3764ca4c737af8e53dd23e528 paths=27
  insertions=496 deletions=6` over that block's pathspec, of which this change is the three `app/src/test`
  paths it names; the loop's declared case count is `LOOP-MEASURE-SUITE cases=308 cmd=tools/loop-selftest.sh --list`
  (the shell suite, not the Kotlin one), and the owed checks in the pathspec are `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` plus the compose-sandbox check.
- Four sibling negative checks were read and left alone, because each pins text a stamp cannot produce:
  `DeepLinkPrivacyTest.kt:46` (`51.5142`, four decimals), `DiagnosticsCoordinatePrivacyTest.kt:167` (`51.51420`,
  five), `DiagnosticsLogRetentionTest.kt:175,197,201,226,275` (`51.513298135108705`). A `ss.SSS` field holds
  three decimals, so none of those substrings is reachable from a clock reading.

## What Changes

- `app/src/test/java/com/naviveylin/test/DiagnosticsRecords.kt` (new) — `recordFields(line, marker)`, the
  record's own `key=value` fields after the marker its subject writes, and `coordinateShapedToken`, the
  coordinate shape `auto-diagnostics` names (1–3 integer digits, 4+ decimals).
- `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates` — decides on the
  record's fields: its key set is exactly the calculation's own seven fields, no field value is
  coordinate-shaped, and `duration` is a whole number of milliseconds.
- `NavigationEngineRerouteTest.aConfirmedRerouteWritesTheLatencyDiagnosticLine` — the same decision over the
  reroute record's three fields.
