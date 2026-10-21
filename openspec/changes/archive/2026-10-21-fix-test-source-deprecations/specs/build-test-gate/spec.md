## ADDED Requirements

### Requirement: The unit-test source sets call no deprecated API

The Kotlin unit-test source sets the gate compiles — `app/src/test/java` (shared by both `:app` flavors),
`auto/src/test/java` and `core/src/test/java` — SHALL call no member that its library marks deprecated, so
that a gate run reports no `… is deprecated. Deprecated in Java.` warning from test sources.

#### Scenario: A sensor is registered through the supported overload

- **WHEN** the unit-test source sets are scanned for `ShadowSensorManager` sensor registration
- **THEN** no source calls `addSensor(int, Sensor)` and at least one calls `addSensor(Sensor)` — exercised
  by `UnitTestSourceDeprecationBaselineTest.no unit test registers a sensor through the deprecated type-and-sensor overload`

#### Scenario: A pending-intent kind is read through the supported predicates

- **WHEN** the unit-test source sets are scanned for the pending-intent kind predicates
- **THEN** no source calls `isActivityIntent()`/`isBroadcastIntent()` and at least one calls
  `isActivity()`/`isBroadcast()` — exercised by
  `UnitTestSourceDeprecationBaselineTest.no unit test reads the pending-intent kind through the deprecated predicates`

#### Scenario: Notification silence is asserted on the object that now owns it

- **WHEN** the unit-test source sets are scanned for the notification silence assertion
- **THEN** no source reads `Notification.defaults`, the scan reaches the notification test that asserts
  silence, and that test reads the channel the posted notice rides (`posted.channelId`) and that channel's
  `IMPORTANCE_LOW` — exercised by
  `UnitTestSourceDeprecationBaselineTest.no unit test asserts notification silence through the deprecated defaults bitmask`
