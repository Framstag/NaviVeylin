# Spec Delta

## ADDED Requirements

### Requirement: A route calculation during navigation is drawn on the map surface

While a navigation session is active and a route calculation is in progress — a reroute above all — the car session SHALL keep the navigation view on screen and draw the wait on its map surface: the destination-facing wording of a calculation in progress together with the reported percentage. The drawn percentage SHALL NOT change for a change smaller than five points, and the previously drawn route SHALL stay drawn and usable throughout.

#### Scenario: A reroute's progress appears on the map surface

- **WHEN** navigation is active and a route calculation is in progress
- **THEN** the map surface SHALL state that a route is being calculated
- **AND** it SHALL show the reported percentage once one is reported
- **Case**: `auto: CalculationStripTest.theStripAppearsWithTheCalculation`

#### Scenario: The map stays usable underneath

- **WHEN** the wait is drawn on the map surface
- **THEN** the map SHALL still pan, zoom and follow the vehicle
- **AND** the previously drawn route SHALL still be drawn
- **AND** the host instruction panel SHALL NOT be replaced by the wait
- **Case**: `none — surface interactivity and the drawn route are measured on the AAOS AVD (tasks 7.2), because the host forwards gestures and owns the surface lifecycle`

#### Scenario: Progress costs no host traffic

- **WHEN** the reported percentage advances by less than five points
- **THEN** the drawn percentage SHALL NOT change
- **Case**: `auto: CalculationStripTest.aPercentChangeBelowTheStepKeepsTheStripUnchanged`

#### Scenario: No progress reported yet

- **WHEN** a calculation is in progress and no percentage has been reported
- **THEN** the map surface SHALL state the wait without a percentage
- **Case**: `auto: CalculationStripTest.theStripStatesTheWaitWithoutAPercent`

#### Scenario: The wait ends with the calculation

- **WHEN** the calculation succeeds, fails or is cancelled
- **THEN** the map-surface wait SHALL end
- **Case**: `auto: CalculationStripTest.theWaitEndsWithTheCalculation`

### Requirement: A failed route calculation during navigation is reported on the map surface

When a route calculation fails while navigation is active, the car session SHALL report the failure on the map surface for a bounded time and SHALL NOT push a screen over the navigation view. The reported wording SHALL be the localized reason the failure carries.

#### Scenario: A failed reroute leaves the map on screen

- **WHEN** a route calculation fails while navigation is active
- **THEN** the map surface SHALL report the failure
- **AND** no error screen SHALL be pushed over the navigation view
- **AND** the navigation view SHALL stay the displayed screen
- **Case**: `auto: SessionScreenStackTest.aFailedReroutePushesNoScreen`

#### Scenario: The failure notice ends by itself

- **WHEN** the failure has been reported on the surface and the driver takes no action
- **THEN** the report SHALL end after its bounded time without a tap
- **Case**: `auto: CalculationNoticeDwellTest.theSurfaceReportEndsAfterItsDwell`

#### Scenario: A retried calculation clears the failure

- **WHEN** a new calculation starts while a failure is reported on the surface
- **THEN** the failure report SHALL be replaced by the new calculation's wait
- **Case**: `auto: CalculationStripTest.aNewCalculationReplacesTheFailureReport`

## MODIFIED Requirements

### Requirement: Car wait notice while a route is being calculated
While a calculation is in progress, navigation is not active, and it outlasts a short configured delay, the car session SHALL show a notice that a route is being calculated, carrying the destination when known and the percentage when known; the notice SHALL NOT appear before the delay has elapsed. While navigation is active this wait notice SHALL NOT be shown, however long the calculation runs — the map surface carries that wait instead.

#### Scenario: Notice appears only after the delay

- **WHEN** a calculation is still running after the configured delay has elapsed and navigation is not active
- **THEN** the car session SHALL show the calculation notice
- **Case**: `auto: CalculationNoticeDecisionTest.aFinishedDelayIsNotAnArmAnyMore`

#### Scenario: Fast calculation shows no notice

- **WHEN** a calculation completes before the configured delay elapses
- **THEN** no calculation notice SHALL be shown
- **Case**: `auto: CalculationNoticeDecisionTest.theNoticeIsStaleOnceItsCalculationIsGone`

#### Scenario: Notice carries the percentage

- **WHEN** the notice is shown and a percentage is known
- **THEN** the notice SHALL display the percentage
- **AND** it SHALL display an updated percentage as the calculation progresses
- **Case**: `auto: CalculationNoticeDecisionTest.theNoticeIsOnlyRefreshedWhenTheDisplayedStepChanges`

#### Scenario: Notice offers a way out when not navigating

- **WHEN** the notice is shown and navigation is not active
- **THEN** it SHALL offer to cancel the calculation
- **Case**: `auto: RouteCalculatingScreenTest.theNoticeOffersCancelOnlyWhenNotNavigating`

#### Scenario: A reroute shows no notice

- **WHEN** a calculation runs while navigation is active
- **THEN** no calculation notice SHALL be pushed
- **AND** this SHALL hold however long the calculation runs
- **Case**: `auto: CalculationNoticeDecisionTest.aRerouteIsNeverArmed`

### Requirement: The car notice never outlives its calculation
The car session SHALL keep the calculation notice consistent with the live calculation: replaced by the navigation view when the route arrives and navigation starts, replaced by the error notice when the calculation fails, and removed when the calculation is cancelled; at most one calculation notice SHALL exist at a time. The notice this requirement governs is the one shown while navigation is not active.

#### Scenario: Route arrives

- **WHEN** the route arrives and navigation starts
- **THEN** the navigation view SHALL replace the calculation notice
- **Case**: `auto: CalculationNoticeDwellTest.aNoticeThatOutlastedItsDwellIsReplacedAtOnce`

#### Scenario: Calculation fails

- **WHEN** the calculation fails
- **THEN** the error notice SHALL replace the calculation notice
- **Case**: `auto: RoutingFailureNoticeTest.theFailureReplacesTheCalculationNotice`

#### Scenario: Calculation cancelled from the notice

- **WHEN** the driver cancels from the calculation notice
- **THEN** the notice SHALL be removed
- **AND** the screen the driver came from SHALL be shown
- **Case**: `auto: CalculationNoticeDwellTest.theDriversOwnAnswerIsNeverDelayedByTheDwell`

#### Scenario: One notice only

- **WHEN** a second calculation starts while the notice is displayed
- **THEN** the existing notice SHALL be updated
- **AND** no second calculation notice SHALL be pushed
- **Case**: `auto: CalculationNoticeDecisionTest.nothingIsArmedWithoutACalculationOrWithANoticeAlreadyUp`

#### Scenario: Calculation ended while the session was not visible

- **WHEN** a calculation ends while the car session is not started
- **THEN** no calculation notice SHALL be shown when the session starts again
- **Case**: `auto: CalculationNoticeDecisionTest.aNewerCalculationTakesTheDelayOverFromTheOlderOne`
