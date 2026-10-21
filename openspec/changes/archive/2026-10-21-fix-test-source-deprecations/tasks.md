## 1. Red test (spec: build-test-gate — "The unit-test source sets call no deprecated API")

- [x] 1.1 Write `app/src/test/java/com/naviveylin/build/UnitTestSourceDeprecationBaselineTest.kt` with the three cases the delta names — the sensor registration, the pending-intent predicates, the notification bitmask — before touching any call site. Verify: `./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.UnitTestSourceDeprecationBaselineTest' -PforceTests --no-build-cache` on HEAD fails all three, XML `tests=3 failures=3`, timestamp `2026-10-20T22:19:20.692Z`, copied to `evidence/`. — **done 2026-10-20**: red run quoted in `proposal.md`'s `## Why`; `evidence/red-TEST-com.naviveylin.build.UnitTestSourceDeprecationBaselineTest.xml` (tests=3 failures=3).

## 2. The four replacements (spec: "A sensor is registered through the supported overload", "A pending-intent kind is read through the supported predicates", "Notification silence is asserted without the deprecated bitmask")

- [x] 2.1 `AmbientLightMonitorTest.kt:35`: `shadowOf(sm).addSensor(Sensor.TYPE_LIGHT, sensor)` → `shadowOf(sm).addSensor(sensor)`. Verify: `grep -n 'addSensor' app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt` shows the single-argument call only.
- [x] 2.2 `NavigationNotificationServiceTapTargetTest.kt:40,69`: `shadow.isBroadcastIntent()` → `shadow.isBroadcast()`, `shadow.isActivityIntent()` → `shadow.isActivity()`. Verify: `grep -n 'isBroadcast\|isActivity' app/src/test/java/com/naviveylin/service/NavigationNotificationServiceTapTargetTest.kt` names no `…Intent()` predicate.
- [x] 2.3 `CarStyleLoadNotifierTest.kt:88`: `assertEquals("no sound/vibration", 0, posted.defaults)` → the channel the posted notice rides (`posted.channelId`) and that channel's `IMPORTANCE_LOW`. Verify: `grep -n 'posted.defaults' auto/src/test/java/com/naviveylin/auto/CarStyleLoadNotifierTest.kt` finds no call (the comment above the new assertion is the only mention) and the case still asserts that guidance is not interrupted.
- [x] 2.4 Compile the four unit-test source sets with warnings on and confirm the `Deprecated in Java` family is gone: `./gradlew :app:compileMobileDebugUnitTestKotlin :app:compileAutomotiveDebugUnitTestKotlin :auto:compileDebugUnitTestKotlin :core:compileDebugUnitTestKotlin --no-build-cache -Pkotlin.incremental=false`. Verify: `grep -c 'Deprecated in Java'` reports `0` against HEAD's `7`. — **done 2026-10-20**: log `evidence/head-compile-baseline.log` is the HEAD half; the post-fix half is `evidence/fixed-compile.log`.

## 3. The guard, re-run green (spec: all three scenarios)

- [x] 3.1 Re-run the three cases forced: `./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.UnitTestSourceDeprecationBaselineTest' -PforceTests --no-build-cache`. Verify: BUILD SUCCESSFUL, XML `tests=3 failures=0`, timestamp `2026-10-20T22:19:38.440Z`, copied to `evidence/`. — **done 2026-10-20**: `evidence/green-TEST-com.naviveylin.build.UnitTestSourceDeprecationBaselineTest.xml`.
- [x] 3.2 Run the three affected classes themselves — `AmbientLightMonitorTest` (7 cases), `NavigationNotificationServiceTapTargetTest` (2, `:app`) and `CarStyleLoadNotifierTest` (5, `:auto`) — so the replacements are shown to keep the assertions they edit. Verify: one BUILD SUCCESSFUL with `failures=0`, XMLs copied to `evidence/`, timestamps `2026-10-20T22:19:36.403Z`–`22:19:39.480Z`. — **done 2026-10-20**.

## 4. The guideline records the measurement once

- [x] 4.1 `guidelines/Build.md` §4 "Result evaluation": the warning bullet records the 2026-10-20 measurement (109 warnings, 7 of the deprecation family, four call sites in three files), names this change and the guard test, and points at the spec requirement. Verify: `sed -n '/^## 4. Result evaluation/,/^## 5/p' guidelines/Build.md | grep -n 'Deprecated in Java'` reports the bullet, and no other guideline restates the counts. — **done 2026-10-20**.

## 5. Falsification of the new guard (revert-check)

- [x] 5.1 Falsify the guard: the pre-fix run of 1.1 (HEAD carries all four sites) is the mutation that fails the three cases, and the green run of 3.1 is the restore. Verify: each case fails with the offending path in the message and passes again afterwards; both XMLs are in `evidence/`. — **done 2026-10-20**: no separate mutation run was made, because the only input the guard reads is the text it scans, so the red run *is* three mutated call sites at once rather than one restored state; round 2 added the single-site mutation the missing assertion needed (task 8.2).

## 6. Gate

- [x] 6.1 Run the project's gate **once**, exactly as the run configures it: `./gradlew test -PforceTests --no-build-cache`. Verify: `BUILD SUCCESSFUL`; each suite's tally transcribed into `evidence/gate-tally.txt` with the gate's own timestamps. — **done 2026-10-20**: `BUILD SUCCESSFUL in 4m 1s` (189 actionable tasks: 32 executed, 157 up-to-date); tallies `:app` mobile 2075 tests, `:app` automotive 2075, `:auto` 866, `:core` 642, `:osmscout-client-java` 48 — **5706 tests / 0 failures / 0 errors**, result timestamps `2026-10-20T22:20:24.846Z`–`2026-10-20T22:24:21.609Z`; transcribed in `evidence/gate-tally.txt`, with the guard class's XML from both `:app` flavors next to it.
- [x] 6.2 Record the exclusions this change does not cover: the gate is `./gradlew test`, so no AAB is assembled and no native ABI built (the delta touches test sources and one guideline). Verify: this task states the exclusion, so the change implies no run it did not make. — **done 2026-10-20**.

## 7. Archive

- [x] 7.1 Archive the change and confirm the delta merged into `openspec/specs/build-test-gate/spec.md`. Verify: `openspec/changes/archive/2026-10-21-fix-test-source-deprecations/` exists and the capability spec carries "The unit-test source sets call no deprecated API" (`openspec/specs/build-test-gate/spec.md:235`). — **done 2026-10-20** via `openspec archive fix-test-source-deprecations --yes`; the CLI reported `build-test-gate: update`, `+ 1 added`.

## 8. Round 2 — the review's two blocking findings

- [x] 8.1 Extend the third case's positive control from a filename check to the assertion it stands for: the
  silence test function `noticeIsSilentSoGuidanceIsNotInterrupted` reads `posted.channelId` and
  `IMPORTANCE_LOW`. Verify: `./gradlew :app:testMobileDebugUnitTest --tests
  'com.naviveylin.build.UnitTestSourceDeprecationBaselineTest' -PforceTests --no-build-cache` is green and
  the XML is copied to `evidence/`. — **done 2026-10-20**: focused green `tests=3 failures=0` at
  `2026-10-20T22:29:52.647Z` (before a KDoc-only edit), and the gate's own guard XMLs below are the run of
  the final source.
- [x] 8.2 Mutate that assertion to the eliminated alternative (`assertNull(posted.sound)`), re-run the case
  forced, restore. Verify: the case fails with "the silence assertion must read the channel the posted
  notice rides…", XML `tests=3 failures=1` copied to `evidence/` as
  `round2-mutation-silence-read-…xml`; the restore re-runs green. — **done 2026-10-20**: mutation failed at
  `22:29:59.230Z` with `tests=3 failures=1 errors=0` and exactly that message; restore left
  `CarStyleLoadNotifierTest.kt` byte-identical to the committed version, green in the gate.
- [x] 8.3 Delete the `design.md` bullet that declares the implemented fourth-site fix an eliminated
  alternative, and correct the two `UNPROVEN` recollections and the `guidelines/Build.md` §4 remainder
  sentence the review found. Verify: `grep -n 'Asserting silence through the notification channel in the
  fourth site' design.md` finds nothing, and the surviving text marks the first-attempt reading as a
  recollection. — **done 2026-10-20**: the bullet is gone, both claims now say what measured them and what
  did not, and Build.md §4 separates the 18 non-opt-in lines into 7 of this family, 2 of another
  (`quadraticBezierTo`) and 9 non-deprecation.
- [x] 8.4 Re-run the gate once: `./gradlew test -PforceTests --no-build-cache`. Verify: `BUILD SUCCESSFUL`,
  the console log and per-suite tallies transcribed into `evidence/gate-tally.txt` and
  `evidence/gate-console.log`. — **done 2026-10-20**: `BUILD SUCCESSFUL in 3m 34s` (189 tasks: 29 executed,
  160 up-to-date), 5706 tests / 0 failures / 0 errors, guard `tests=3 failures=0` in both `:app` flavors.
