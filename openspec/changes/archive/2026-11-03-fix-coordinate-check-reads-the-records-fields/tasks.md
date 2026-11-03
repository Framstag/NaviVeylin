# Tasks — the coordinate check reads the record's fields

## 1. Falsify the mechanism on HEAD

- [x] 1.1 One production mutation, on HEAD, before any fix: `DiagnosticsLog.timestamped` pinned to
      `[2026-11-03 13:41:13.412]` (the stamp of second `:13`, millisecond `412`), marked
      `// REVERT-CHECK MUTATION`. Verification: `grep -c 'REVERT-CHECK MUTATION'` = 1 before the run, 0 after.
- [x] 1.2 Run the two cases the mutation reaches
      (`:app:testMobileDebugUnitTest --tests '*NavigationEngineCalculationStateTest*' --tests '*NavigationEngineRerouteTest*' -PforceTests --no-build-cache`).
      Verification: `theEndOfACalculationIsRecordedWithoutCoordinates` fails with the assertion's own message
      (`no coordinate precision in the entry`), on HEAD's own check — the record carries no coordinate.
- [x] 1.3 Restore the mutation exactly, and confirm `grep -c 'REVERT-CHECK MUTATION'` = 0 over `app/src`.

## 2. Add the record-field decision

- [x] 2.1 `app/src/test/java/com/naviveylin/test/DiagnosticsRecords.kt` — `recordFields(line, marker)` and
      `coordinateShapedToken`. Verification: the file compiles with the two cases below (task 3.3 / 4.1), and
      `grep -c 'lat=\|lon=' app/src/test/java/com/naviveylin/test/DiagnosticsRecords.kt` = 0.

## 3. Rewrite the calculation case

- [x] 3.1 Replace `NavigationEngineCalculationStateTest.kt:230-231` with the record's key-set assertion, the
      coordinate-shape assertion over its values, and the `duration` shape assertion.
      Verification: the assertion names the entry, so a red quotes the record it read.
- [x] 3.2 Keep the case's other assertions untouched (marker filter, `source=`, `outcome=`, `duration=`,
      `percents=`, and the single-record file assertion), so the delta is the coordinate decision only.

## 4. Rewrite the reroute case

- [x] 4.1 Replace `NavigationEngineRerouteTest.kt:498-501` with the same decision over the reroute record's own
      three fields. Verification: the case's `deviation_m=` range assertion still runs (the numeric parse above
      it is unchanged).

## 5. Falsify the guard

- [x] 5.1 One production mutation, after the fix: append ` center=52.5200` to `routeCalculationDoneLine`, marked
      `// REVERT-CHECK MUTATION`. Verification: the rewritten calculation case fails on the key-set assertion,
      not on a compile error. A first attempt appended `lat=52.5200 lon=13.4050` and was rejected as a
      falsification: the sink's retention pass pruned the line (`LogLineCoordinates.carriesPosition`,
      `DiagnosticsLog.kt:604`), so the case failed with `NoSuchElementException: List contains no element matching
      the predicate` at the entry selection — a wrong reason, not the assertion. The bare `center=52.5200` field
      is the shape that predicate deliberately lets through (its own documented limitation), so the case is what
      decides it.
- [x] 5.2 Restore it, `grep -c 'REVERT-CHECK MUTATION'` = 0 over `app/src`, then a **forced** green run of both
      classes (`-PforceTests --no-build-cache`), whose XML `timestamp` is after the restore.

## 6. Gate

- [x] 6.1 `./tools/loop/gate.sh` once, from the change root. Verification: the gate's own line (`evidence/gate.log`) reads `LOOP-GATE: tests=60 failures=0 errors=1`, an aggregate sum of its step tallies that is not retained as one XML; each step's XML is under `evidence/`.
      `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` took 282 s and passed (`tests="0" failures="0" errors="0"` in `evidence/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`, which reports what the task printed). The one red step is `bash tools/gen-feature-list.sh --check-classification`,
      and it is **not this change's**: it names two spec ids introduced by *other* open changes in this inherited
      worktree (`auto/off-route-indicator` by `car-reroute-without-losing-the-map`, `tooling-test-layout` by
      `separate-tooling-test-code`) and three stale classification entries (`search-name-matching`,
      `search-result-row-icon`, `start-map-selection`). `unit-test-suite-runtime`, the capability this change's
      delta extends, is already classified (`tools/feature-list/specs.json:186`), and the check's own report
      names no id of this change. `evidence/gate.log`, `evidence/gradlew_test_*.xml`,
      `evidence/bash_tools_gen-feature-list.sh_--check-classification.xml` carry the run and the red step's own
      XML.
- [x] 6.2 No second gate attempt (the gate is evidence, and its red is attributed here rather than re-run).

## Verification record

| task | run | tally |
|---|---|---|
| 1.2 red on HEAD | mutation A (stamp pinned to `[2026-11-03 13:41:13.412]`), `:app:testMobileDebugUnitTest --tests '*NavigationEngineCalculationStateTest*' --tests '*NavigationEngineRerouteTest*' -PforceTests --no-build-cache` | `23 tests completed, 1 failed`; `NavigationEngineCalculationStateTest` tests="6" failures="1" `2026-11-03T12:49:51.176Z`, `NavigationEngineRerouteTest` tests="17" failures="0" — the failure is HEAD's own assertion, `AssertionError: no coordinate precision in the entry`, at `NavigationEngineCalculationStateTest.kt:231` (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD-*.xml`) |
| 1.3 restore | `grep -rn 'REVERT-CHECK MUTATION' app/src core/src auto/src` | `0` |
| 5.1 guard mutation | mutation B (`routeCalculationDoneLine` appends ` center=52.5200`), both test classes plus `NavigationEngineCalculationDiagnosticsTest` | `30 tests completed, 1 failed`; `NavigationEngineCalculationStateTest` tests="6" failures="1" `2026-11-03T12:51:12.943Z` — `AssertionError: the record carries the calculation's own fields, and a position is not one of them: … center=52.5200`, i.e. the key-set assertion, not a compile error (`evidence/guard-mutation.log`, `evidence/guard-mutation-NavigationEngineCalculationStateTest.xml`) |
| 5.2 forced green | restore, then the same three classes with `-PforceTests --no-build-cache` | `BUILD SUCCESSFUL`; tests="6" failures="0" errors="0" `2026-11-03T12:51:29.562Z`, tests="17" failures="0" `2026-11-03T12:51:29.863Z`, tests="7" failures="0" `2026-11-03T12:51:29.818Z` — every timestamp after the restore (`evidence/green.log`, `evidence/green-*.xml`) |
