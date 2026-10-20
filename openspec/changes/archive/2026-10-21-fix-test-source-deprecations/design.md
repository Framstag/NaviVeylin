# Design — fix-test-source-deprecations

## Root cause

Three unit-test files call members whose libraries deprecated them and publish a supported successor:
`ShadowSensorManager.addSensor(int, Sensor)`, `ShadowPendingIntent.isBroadcastIntent()` and
`isActivityIntent()` (Robolectric 4.16 in `:app`, 4.14.1 in `:auto`), and the `Notification.defaults`
bitmask (deprecated from API 26, when the concept moved to `NotificationChannel`). Nothing in the build
fails on a deprecation, so the four call sites survived the Robolectric bump and the channel migration with
nothing but a `w:` line to show for it — verified by the 109-warning compile in `proposal.md`'s `## Why`,
of which exactly 7 are the `Deprecated in Java` family and all 7 come from these four sites.

## The fix

Four mechanical replacements, one per call site, plus one guard test:

| site | replaced with | why it is the same operation |
|---|---|---|
| `AmbientLightMonitorTest.kt:35` | `addSensor(sensor)` | the two-argument overload and `addSensor(Sensor)` both put the sensor into `sensorMap`; the second keys it by `Sensor.getType()`, which the test's reflected sensor already carries |
| `NavigationNotificationServiceTapTargetTest.kt:40` | `isBroadcast()` | both bodies read `ShadowPendingIntent.type` — byte-identical |
| `NavigationNotificationServiceTapTargetTest.kt:69` | `isActivity()` | same, `type == ACTIVITY` |
| `CarStyleLoadNotifierTest.kt:88` | `posted.channelId` → that channel's `IMPORTANCE_LOW` | from API 26 the sound and vibration belong to the channel, so "silent" is the channel's importance; the notification's own `sound`/`vibrate` fields are deprecated too, which is why they are not the successor |

The three bytecode claims are read out of `shadows-framework-4.16.jar` and `4.14.1.jar` with `javap`;
they are what makes the first three replacements free rather than a re-specification.

## The guard is a source scan, and it says so

No host test can configure the project it runs inside, so a case cannot assert "the compiler emitted no
deprecation warning". The guard therefore asserts the condition the warning is a function of: the
deprecated call is absent from the unit-test source roots. Each of the three cases asserts the absence
**and** a positive control — the supported successor is present, or (for the `defaults` case) the scan
reached the file that carried it — so a scan that read nothing cannot pass vacuously.

The scan covers `app/src/test/java`, `auto/src/test/java` and `core/src/test/java`: the three roots the
gate's four suites compile from, since `:app`'s two flavors compile the same tree. It skips its own file
(the guard names the members it forbids, so it would otherwise match itself).

## Alternatives the evidence eliminated

- **`@Suppress("DEPRECATION")` on the four call sites.** Eliminated for the three Robolectric members by
  the jars: a supported member exists and is byte-equivalent, so suppressing would silence a warning whose
  cause is removable. The project keeps the annotation for the case where no non-deprecated form exists
  (`LocationService.kt:527`, `MapScreen.kt:1`) — that is the *fourth* site's alternative, and it is
  eliminated there by the measurement: the property the assertion holds ("cannot interrupt guidance") has a
  supported form once it moves to the channel, so a suppression would keep a deprecated read that the
  platform ignores anyway on API 29+.
- **`assertNull(posted.sound)` + `assertNull(posted.vibrate)` as the fourth site's successor.** Eliminated by
  the compile, not by judgement: `Notification.sound` and `Notification.vibrate` are deprecated from the
  same API level, so the substitution left two `Deprecated in Java` lines in place of one (measured; the
  run is why this change states the post-fix count as 0 rather than as the count of the first attempt).
- **Asserting silence through `NotificationChannel.sound`/`shouldVibrate()`.** Eliminated by the framework's
  own defaults: a freshly constructed channel carries the default notification URI and vibration enabled,
  and `IMPORTANCE_LOW` is what suppresses both at post time — so the channel's importance is the property,
  not its sound fields.
- **Adding `allWarningsAsErrors` so the compiler polices this.** Eliminated as wider than the item: the
  four suites carry 91 `ExperimentalCoroutinesApi` opt-in warnings on both HEAD and after this change; that
  family is #219's sibling item, and turning deprecations into errors is a project-wide decision.
- **Downgrading Robolectric to a release where the members are not deprecated.** Eliminated as a reversal:
  the pinned versions are the point (the jars are the reason the successors exist), and the deprecation is
  upstream's statement that the old form goes away.
- **Asserting silence through the notification channel in the fourth site.** Eliminated as a wider claim
  than the test holds: `noticeIsPostedWithTheMessageAndALowImportanceChannel` already asserts
  `IMPORTANCE_LOW` (the channel-based successor of `DEFAULT_SOUND`/`DEFAULT_VIBRATE`), so repeating it here
  would duplicate a claim instead of preserving the per-notification one.

## Blast radius

- Three test classes in two modules, four lines changed; no production code, no build script, no
  dependency, no manifest.
- One new test class in `:app` that reads three repository source roots and touches no app code.
- `guidelines/Build.md` §4: the warning bullet gains the measurement and names the guard.
- Test behaviour: the three changed assertions keep asserting the same property — the sensor is
  registered, the tap target is a broadcast / an activity. The fourth assertion is the one deliberate
  change of form: it now names the channel the notice rides and that channel's importance, because the
  sound and the vibration are the channel's from API 26 while the deprecated bitmask it replaced was
  ignored by the platform at the app's `minSdk` 29.

## Rollback

Restore the four call sites. All three guard cases fail with the offending paths named, and the compile
emits the 7 warning lines again — the rollback is visible in both directions.
