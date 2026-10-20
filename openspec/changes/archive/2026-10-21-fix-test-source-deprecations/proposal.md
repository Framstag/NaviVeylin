# fix-test-source-deprecations

## Why

Issue #219 (the test-source half of the aggregate #65): four deprecated APIs are still called from three
unit-test files. Verified on HEAD `3211d4d` on 2026-10-20 by compiling the four unit-test source sets with
warnings on — `./gradlew :app:compileMobileDebugUnitTestKotlin :app:compileAutomotiveDebugUnitTestKotlin
:auto:compileDebugUnitTestKotlin :core:compileDebugUnitTestKotlin --no-build-cache
-Pkotlin.incremental=false` (log `evidence/head-compile-baseline.log`):

- 109 `w:` lines in total, and exactly **7** of them are the `… is deprecated. Deprecated in Java.` family
  — the item's count holds, and the count is 7 because `:app`'s two flavors compile the same
  `app/src/test/java` twice:

```
auto/src/test/java/com/naviveylin/auto/CarStyleLoadNotifierTest.kt:88:54
  'field defaults: Int' is deprecated. Deprecated in Java.
app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt:35:22
  'fun addSensor(p0: Int, p1: Sensor!): Unit' is deprecated. Deprecated in Java.          (× 2 flavors)
app/src/test/java/com/naviveylin/service/NavigationNotificationServiceTapTargetTest.kt:40:20
  'fun isBroadcastIntent(): Boolean' is deprecated. Deprecated in Java.                  (× 2 flavors)
app/src/test/java/com/naviveylin/service/NavigationNotificationServiceTapTargetTest.kt:69:20
  'fun isActivityIntent(): Boolean' is deprecated. Deprecated in Java.                   (× 2 flavors)
```

So four call sites, not seven: `:app` compiles `app/src/test/java` once per flavor, `:auto` and `:core`
once each. The item's own arithmetic ("2 + 2 + 2 + 1") is this per-suite count.

**Root cause, one sentence:** three test files still call the pre-deprecation forms of members whose
libraries publish a supported successor — `ShadowSensorManager.addSensor(int, Sensor)`,
`ShadowPendingIntent.isBroadcastIntent()`/`isActivityIntent()` (Robolectric 4.16 in `:app`, 4.14.1 in
`:auto`) and the API-26 `Notification.defaults` bitmask — and nothing in the build fails on a
deprecation, so the call sites outlived both the Robolectric bump and the notification-channel migration.

**Each successor is a mechanical replacement, verified in the pinned jars:**

- `addSensor(Sensor)` stores into the same `sensorMap` keyed by `Sensor.getType()` (bytecode of
  `shadows-framework-4.16.jar`), and the test's sensor is built with `mType = Sensor.TYPE_LIGHT`.
- `isBroadcast()` and `isBroadcastIntent()` — and `isActivity()`/`isActivityIntent()` — are byte-identical
  in the same jar: both read `ShadowPendingIntent.type`.
- `Notification.defaults` has no non-deprecated form: the field is deprecated from API 26 and so are the
  notification's own `sound` and `vibrate` fields (measured — asserting those two left two `Deprecated in
  Java` lines where there had been one). From API 26 the sound and the vibration belong to the
  `NotificationChannel`, and the platform ignores per-notification values for a channel-based post, so the
  supported assertion of "silent" is the channel the notice rides and that channel's importance.

**Red test on HEAD** — `UnitTestSourceDeprecationBaselineTest` (`:app` unit tests, file
`app/src/test/java/com/naviveylin/build/UnitTestSourceDeprecationBaselineTest.kt`), run with
`./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.UnitTestSourceDeprecationBaselineTest'
-PforceTests --no-build-cache`:

- `no unit test registers a sensor through the deprecated type-and-sensor overload` — fails,
  `… deprecated …: [app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt]`
- `no unit test reads the pending-intent kind through the deprecated predicates` — fails,
  `… deprecated …: [app/src/test/java/com/naviveylin/service/NavigationNotificationServiceTapTargetTest.kt]`
- `no unit test asserts notification silence through the deprecated defaults bitmask` — fails,
  `… deprecated …: [auto/src/test/java/com/naviveylin/auto/CarStyleLoadNotifierTest.kt]`
- XML: `red-TEST-com.naviveylin.build.UnitTestSourceDeprecationBaselineTest.xml`, **tests=3 failures=3**,
  timestamp `2026-10-20T22:19:20.692Z` (`evidence/`).

**Owning capability:** `build-test-gate` — the requirement this change adds is "The unit-test source sets
call no deprecated API", the source-set half of the gate's result evaluation. **Owning guideline section:**
`guidelines/Build.md` §4 "Result evaluation", whose bullet reads "Warnings: the build must have no
warnings — report them, don't ignore" (routed by `AGENTS.md`'s Documentation Map: "build, run, force a
rerun, read a gate result → `Build.md` §2, §3, §4").

## What changes

- `app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt`: `addSensor(Sensor.TYPE_LIGHT, sensor)`
  → `addSensor(sensor)`.
- `app/src/test/java/com/naviveylin/service/NavigationNotificationServiceTapTargetTest.kt`:
  `isBroadcastIntent()` → `isBroadcast()`, `isActivityIntent()` → `isActivity()`.
- `auto/src/test/java/com/naviveylin/auto/CarStyleLoadNotifierTest.kt`: the `posted.defaults == 0`
  assertion becomes an assertion on the channel the posted notice rides (`posted.channelId`) and that
  channel's `IMPORTANCE_LOW`.
- `app/src/test/java/com/naviveylin/build/UnitTestSourceDeprecationBaselineTest.kt`: the guard — three
  cases, each asserting one deprecated call family absent from the three unit-test source roots and its
  supported successor present.
- `guidelines/Build.md` §4: the warning bullet records the measurement above (109 warnings, 7 of the
  deprecation family, four call sites) and names the guard that holds the absence.

No production code, no build script, no dependency changes. Rollback: restore the four call sites; all
three guard cases fail, so the regression is visible rather than silent.
