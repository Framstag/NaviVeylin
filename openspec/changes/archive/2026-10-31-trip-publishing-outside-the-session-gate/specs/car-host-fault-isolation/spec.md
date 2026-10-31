## ADDED Requirements

### Requirement: Trip metadata publishing is bounded by the navigation period

While navigation is active the app SHALL publish trip metadata whether or not the car session is started, and
while navigation is inactive it SHALL send none. The session-start bound of "Bounded host-facing traffic while
not visible" covers the started/ended pair and the screen and template mutations it names; it SHALL NOT defer or
drop a trip send, because the vehicle cluster and heads-up display keep showing guidance while the car app is
not visible.

#### Scenario: Trip keeps updating while the session is not started

- **WHEN** navigation is active, the car session is not started, and a displayed trip value (manoeuvre,
  rounded distance, remaining time) changes
- **THEN** the trip update is sent to the host
- **AND** the session records no deferred host sync for it and makes no `navigationStarted` or
  `navigationEnded` call
- **Case**: `auto: NavigationSessionTripSendTest.tripKeepsUpdatingWhileTheSessionIsNotStarted`

#### Scenario: No trip send outside the navigation period

- **WHEN** navigation is inactive and a navigation-state emission arrives
- **THEN** no trip update is sent to the host, whether or not the car session is started
- **Case**: `auto: NavigationSessionTripSendTest.noTripSendOutsideTheNavigationPeriod`

#### Scenario: The trip send is not routed through the session gate

- **WHEN** the trip collector's decision is written or changed
- **THEN** it is taken from the navigation period and not from the session-start bound
- **AND** the build refuses a collector that asks the session gate
- **Case**: `auto: checkTripSendGate` (scanner cases: `buildSrc: TripSendGateScannerTest.refusesTheGateQuestionOnTheTripCollector`, `…refusesACollectorThatDropsTheDecision`)
