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
  The case carries the unknown half on both surfaces — a step whose `remainingDistance` is `null` and one whose
  distance is `NaN`, each asserted on the phone stats line and the car hint — and the clamp for a negative
  distance; the red half on HEAD is the zero assertion above, because HEAD's `<= 0.0` guard renders `in --`
  for the known zero the case starts from.

## 2. The fix

- [x] 2.1 `NavigationNotificationContent.kt` — `formatDistanceCompact(meters: Double?, locale)` returns `--`
  only for `null`/`NaN`, clamps negative values to zero and formats every known value (including `0.0`) with
  `formatDistanceNumber`; `navigationContent` passes `step?.remainingDistance` instead of `?: 0.0`, and
  `carHint` passes `step.remainingDistance` for a live step. Verify: the red case alone is green, and the case's
  `null` and `NaN` halves still read `in --` / `-- · 14:32`.
- [x] 2.2 Revert-check (one mutation, the reported one): change the guard to
  `meters == null || meters.isNaN() || meters <= 0.0` with a `REVERT-CHECK MUTATION` marker, run the case →
  red with `in --`, remove the marker and the added clause, `grep -c 'REVERT-CHECK MUTATION'` → `0`, then run
  the case forced → green. Evidence: `evidence/revert-check.log`.
- [x] 2.3 Run the changed case plus its neighbour:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.service.NavigationNotificationContentFormatterTest" --tests "com.naviveylin.service.CarHintContentTest" -PforceTests --no-build-cache`
  → green: `NavigationNotificationContentFormatterTest` `tests="18" failures="0"` and `CarHintContentTest`
  `tests="6" failures="0"` (`2026-10-28T18:32:26Z`, after the corrections; round 1 read `18:20:05Z`). Copy the XMLs
  into `evidence/`.

## 3. Gate and archive

- [x] 3.1 Run the project gate once, from the change root: `./tools/loop/gate.sh`; in round 1 it exited `0` after
  6m 41s and printed `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly
  --no-build-cache 403s, tools/check-compose-sandbox-rule.sh 0s,
  tools/check-compose-sandbox-rule-selftest.sh 0s)`. The corrections moved the tree, so the gate ran once more on
  the corrected tree: exit `0`, `LOOP-GATE: tests=6 failures=0 errors=0 (./gradlew test -PforceTests
  -PjvmTestsOnly --no-build-cache 274s, …)`, recorded in `evidence/gate.log`. The wrapper's number is its own count
  of the shell self-test's `all 6 cases passed` line, not the build's execution — Gradle prints no line it can
  read, so the compiled step contributes `tests=0` (`tools/loop/gate.sh:274-279`). The same run's per-module XMLs
  carry 2135 + 2135 + 885 + 642 + 112 cases, zero failed, zero errors; `evidence/gate-modules.txt` holds them with
  the command that reads them, and the gate's own per-suite tallies are copied under `evidence/gate-tallies/`.
- [x] 3.2 Check pointers mechanically, from the change root, against the archived change:
  `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-28-fix-zero-distance-reads-zero-not-unknown --root .`
  → `ok C1 …`, `ok C2 test tallies: … pairs confirmed`, `ok C3 /tmp pointers: none cited`,
  `ok C4 mutations: no REVERT-CHECK MUTATION marker`, `ok C5 file:line: … resolved`, `evidence-check: PASS`;
  the transcript is archived as `evidence/evidence-check.log`.
- [x] 3.3 Round-2 corrections, after the round-1 review: the requirement now states the known zero and the
  unknown (absent or `NaN`) distance in terms of the nullable `remainingDistance` instead of naming the `:core`
  extension `remainingMeters`, in both the archived delta and the shipped spec; the case asserts the unknown
  half on the phone and the car for an absent distance as well as a `NaN` one; and the stale `step?.remainingMeters`
  call-site text in `proposal.md` and 2.1 is corrected. Verify: the focused XMLs named in 2.3.
- [x] 3.4 Sync the delta into `openspec/specs/navigation-ongoing-notification/spec.md`, archive the change,
  and confirm the archived directory holds the merged scenario. Verify:
  `openspec archive fix-zero-distance-reads-zero-not-unknown --yes` → `Specs updated successfully`, archived as
  `2026-10-28-fix-zero-distance-reads-zero-not-unknown`; `grep -n 'A known zero distance'
  openspec/specs/navigation-ongoing-notification/spec.md` → line 156; archived directory exists and carries the
  scenario.
