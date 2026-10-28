# Tasks

Every task serves the one scenario this change adds to `navigation-ongoing-notification` — "A known zero
distance is not an unknown distance".

## 1. Red case

- [x] 1.1 Add `zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown` to
  `app/src/test/java/com/naviveylin/service/NavigationNotificationContentFormatterTest.kt`, asserting the
  phone stats line, the car hint's secondary text, and that a `NaN` distance still reads `--`. Verify on HEAD
  (`c29691865337325c0b293b57b72068d0468540da`):
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.service.NavigationNotificationContentFormatterTest.zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown" -PforceTests --no-build-cache`
  → `BUILD FAILED`, `1 test completed, 1 failed`,
  `java.lang.AssertionError: zero distance must render 0 m in the notification: in -- · arrive 14:32 · 0 min · 12.4 km`.
  XML `evidence/red-zero-distance.xml`: `tests="1" failures="1" errors="0" timestamp="2026-10-28T18:08:55.848Z"`.

## 2. The fix

- [x] 2.1 `NavigationNotificationContent.kt` — `formatDistanceCompact(meters: Double?, locale)` returns `--`
  only for `null`/`NaN`, clamps negative values to zero and formats every known value (including `0.0`) with
  `formatDistanceNumber`; `navigationContent` passes `step?.remainingMeters` instead of `?: 0.0`. Verify: the
  red case alone is green, and the new case's `NaN` half still reads `in --`.
- [x] 2.2 Revert-check (one mutation, the reported one): change the guard to
  `meters == null || meters.isNaN() || meters <= 0.0` with a `REVERT-CHECK MUTATION` marker, run the case →
  red with `in --`, remove the marker and the added clause, `grep -c 'REVERT-CHECK MUTATION'` → `0`, then run
  the case forced → green. Evidence: `evidence/revert-check.log`.
- [x] 2.3 Run the changed case plus its neighbour:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.service.NavigationNotificationContentFormatterTest" --tests "com.naviveylin.service.CarHintContentTest" -PforceTests --no-build-cache`
  → green: `NavigationNotificationContentFormatterTest` `tests="18" failures="0"` and `CarHintContentTest`
  `tests="6" failures="0"` (`2026-10-28T18:20:05Z`). Copy the XMLs into `evidence/`.

## 3. Gate and archive

- [x] 3.1 Run the project gate once, from the change root: `./tools/loop/gate.sh`; it exited `0` after 6m 41s
  and printed `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly
  --no-build-cache 403s, tools/check-compose-sandbox-rule.sh 0s,
  tools/check-compose-sandbox-rule-selftest.sh 0s)`. The wrapper XML the gate writes carries `tests="0"`; the
  same run's per-module XMLs carry 2135 + 2135 + 885 + 642 + 112 cases, zero failed, zero errors
  (`evidence/evidence.md` records the command). `evidence/gate.log` holds the transcript; copy the XMLs the
  change cites into `evidence/` and the item directory.
- [x] 3.2 Check pointers mechanically:
  `bash tools/loop/evidence-check.sh openspec/changes/fix-zero-distance-reads-zero-not-unknown --root .`
  → `ok C1 …`, `ok C2 test tallies: 11 pairs confirmed`, `ok C3 /tmp pointers: none cited`,
  `ok C4 mutations: no REVERT-CHECK MUTATION marker`, `ok C5 file:line: 262 resolved`, `evidence-check: PASS`.
- [x] 3.3 Independent read-only review of the diff against the one scenario: verdict **OK with notes**, no P0.
  Its P1 (a step with absent progress must not become a known zero) is fixed by passing
  `step.remainingDistance` at both call sites instead of the `:core` extension that collapses null to `0.0`;
  its P2s are settled: the missing-step phone path is now asserted to read `in --`, the test comment cites this
  change's `navigation-ongoing-notification` requirement, and the restored-green XML is retained as
  `evidence/revert-restored.xml`. Zero CRITICAL issues.
- [x] 3.4 Sync the delta into `openspec/specs/navigation-ongoing-notification/spec.md`, archive the change,
  and confirm the archived directory holds the merged scenario. Verify:
  `openspec archive fix-zero-distance-reads-zero-not-unknown --yes` → `Specs updated successfully`, archived as
  `2026-10-28-fix-zero-distance-reads-zero-not-unknown`; `grep -n 'A known zero distance'
  openspec/specs/navigation-ongoing-notification/spec.md` → line 156; archived directory exists and carries the
  scenario.
