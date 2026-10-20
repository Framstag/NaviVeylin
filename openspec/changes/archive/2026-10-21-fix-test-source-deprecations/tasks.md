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

- [x] 5.1 Mutate one call site back (`addSensor(Sensor.TYPE_LIGHT, sensor)`), re-run the sensor case forced, restore, re-run forced green. Verify: the mutation fails the sensor case with the offending path in the message, the restore passes. — **done 2026-10-20**: the red run of 1.1 *is* that mutation (HEAD carries all four sites), the green run of 3.1 is the restore; both XMLs are in `evidence/`, so no separate mutation run was made for a guard whose only input is the text it scans.

## 6. Gate

- [x] 6.1 Run the project's gate **once**, exactly as the run configures it: `./gradlew test -PforceTests --no-build-cache`. Verify: `BUILD SUCCESSFUL`; each suite's tally transcribed into `evidence/gate-tally.txt` with the gate's own timestamps. — **done 2026-10-20**: `BUILD SUCCESSFUL in 4m 1s` (189 actionable tasks: 32 executed, 157 up-to-date); tallies `:app` mobile 2075 tests, `:app` automotive 2075, `:auto` 866, `:core` 642, `:osmscout-client-java` 48 — **5706 tests / 0 failures / 0 errors**, result timestamps `2026-10-20T22:20:24.846Z`–`2026-10-20T22:24:21.609Z`; transcribed in `evidence/gate-tally.txt`, with the guard class's XML from both `:app` flavors next to it.
- [x] 6.2 Record the exclusions this change does not cover: the gate is `./gradlew test`, so no AAB is assembled and no native ABI built (the delta touches test sources and one guideline). Verify: this task states the exclusion, so the change implies no run it did not make. — **done 2026-10-20**.

## 7. Archive

- [x] 7.1 Archive the change and confirm the delta merged into `openspec/specs/build-test-gate/spec.md`. Verify: `openspec/changes/archive/2026-10-21-fix-test-source-deprecations/` exists and the capability spec carries "The unit-test source sets call no deprecated API" (`openspec/specs/build-test-gate/spec.md:235`). — **done 2026-10-20** via `openspec archive fix-test-source-deprecations --yes`; the CLI reported `build-test-gate: update`, `+ 1 added`.
