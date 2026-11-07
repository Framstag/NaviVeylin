# route-calculation-feedback Specification

## Purpose
Makes the wait for a route observable: the shared state reports that a route calculation is running, how far along it is, and when it has been superseded, so every surface can show progress and offer a way out instead of leaving the driver in silence.

## Requirements

### Requirement: In-flight route calculation is part of the shared navigation state
The system SHALL expose the in-flight route calculation in the shared navigation state — that a calculation is running, the destination it is calculating for, and a request token that increases with every new calculation — so every surface observes the same wait.

#### Scenario: Calculation begins
- **WHEN** a surface starts navigation to a destination and a start position is available
- **THEN** the state SHALL report a calculation in progress
- **AND** the calculation's request token SHALL be greater than the token of the calculation it follows

#### Scenario: Calculation succeeds
- **WHEN** the calculated route arrives
- **THEN** the state SHALL report no calculation in progress

#### Scenario: Calculation fails
- **WHEN** the calculation reports an error
- **THEN** the state SHALL report no calculation in progress together with the error

### Requirement: A superseded calculation never clears the live calculation's state
The system SHALL identify each calculation by its request token, so the end of a calculation that a newer request superseded SHALL NOT change the state of the newer calculation.

#### Scenario: Second request supersedes the first
- **WHEN** a navigation request is made while an earlier calculation is still running
- **THEN** the completion, failure or cancellation of the earlier calculation SHALL leave the state reporting the newer calculation in progress

#### Scenario: Cancellation of a superseded calculation is ignored
- **WHEN** a superseded calculation is aborted and reports its cancellation
- **THEN** the state SHALL still report the live calculation in progress

### Requirement: Route calculation progress is exposed as a percentage
While a route calculation is in progress the system SHALL expose its progress as an integer percentage between 0 and 99, as reported by the routing engine, and SHALL NOT report 100 before the route has arrived.

#### Scenario: Progress reported
- **WHEN** the routing engine reports progress during a calculation
- **THEN** the state SHALL carry that percentage

#### Scenario: Progress never completes the wait
- **WHEN** the routing engine reports its highest progress value before the route arrives
- **THEN** the state SHALL still report the calculation in progress
- **AND** the percentage SHALL NOT exceed 99

#### Scenario: No progress reported yet
- **WHEN** a calculation has started but no progress has been reported
- **THEN** the state SHALL report the calculation in progress without a percentage

### Requirement: Cancelling a calculation aborts it and releases its resources
The system SHALL provide a way for a surface to cancel an in-flight calculation; cancelling SHALL abort the underlying routing work, clear the calculation state, start no navigation, and release the location updates the acquisition held.

#### Scenario: Cancel while calculating
- **WHEN** a surface cancels an in-flight calculation
- **THEN** the routing engine's cancellation SHALL be requested
- **AND** the state SHALL report no calculation in progress

#### Scenario: Cancel starts no navigation
- **WHEN** a cancelled calculation would otherwise have produced a route
- **THEN** navigation SHALL NOT start

#### Scenario: Cancel releases location updates
- **WHEN** a calculation that took location updates to obtain its start position is cancelled
- **THEN** those location updates SHALL be released

#### Scenario: Cancel with nothing running
- **WHEN** a surface cancels while no calculation is in progress
- **THEN** the state SHALL remain unchanged

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

### Requirement: Route calculation is measured without coordinates
When a route calculation ends the system SHALL record a diagnostics entry carrying only coordinate-free numbers — the trigger of the calculation, its duration in milliseconds, the number of progress values received, its outcome, and, when it failed, the failure's reason with the numbers that classify it (the distance the search reached, the start-to-target air-line distance, and the distance from a failing end to the nearest road the router could use) — so the notice delay is set from observed durations.

#### Scenario: Entry on a successful calculation
- **WHEN** a calculation ends successfully
- **THEN** a diagnostics entry with its trigger, duration, progress count and `outcome=ok` SHALL be recorded

#### Scenario: Entry on a failed calculation
- **WHEN** a calculation ends with an error
- **THEN** the entry SHALL report `outcome=error`
- **AND** it SHALL report the failure's reason

#### Scenario: The failing reason is identifiable from the entry alone
- **WHEN** a calculation fails because one end cannot be attached to the road network
- **THEN** the entry SHALL identify that end as the failing one
- **AND** it SHALL report how far the search reached

#### Scenario: Entry on a cancelled calculation
- **WHEN** a calculation is cancelled
- **THEN** the entry SHALL report `outcome=cancelled`
- **AND** it SHALL report no failure reason

#### Scenario: No coordinates in the entry
- **WHEN** the entry is recorded
- **THEN** it SHALL NOT contain a position or a coordinate-formatted string

### Requirement: One wait-notice implementation on the car display

The car display SHALL have exactly one transient wait-notice screen, the route-calculation notice
(`RouteCalculatingScreen`). A retired wait screen SHALL leave no class, no caller and no user-facing string
behind, and a comment SHALL describe the mechanism it teaches rather than name the removed class.

#### Scenario: The retired wait screen is not on the classpath

- **WHEN** the `:auto` module's compiled classes are loaded
- **THEN** `com.naviveylin.auto.LoadingScreen` SHALL NOT resolve
- **Case**: `StartupScreensTest.retiredLoadingScreenClassIsGone`

#### Scenario: The retired wait screen's text is gone

- **WHEN** the `:auto` module's default string resources are read
- **THEN** the keys `loading_map_data` and `preparing_navigation` SHALL be absent
- **Case**: `GermanTranslationCompletenessTest.retiredWaitNoticeStringsAreGone`

### Requirement: A notice the navigation view replaces stays visible for a minimum dwell

Once the car calculation notice is shown it SHALL stay visible for a minimum dwell before the navigation view replaces it: a route arriving inside the dwell SHALL replace it only once the dwell has elapsed, and a notice that outlasted it SHALL be replaced at once. The dwell SHALL run from the moment the notice appeared, SHALL NOT delay the removal a cancellation causes or a failure whose error notice stands over no navigation view, and SHALL NOT apply where the notice was never shown.

#### Scenario: A route that arrives inside the dwell keeps the notice up

- **WHEN** the route arrives after the notice has been shown for less than the minimum dwell
- **THEN** the navigation view SHALL NOT replace the notice before the dwell has elapsed
- **AND** it SHALL replace it when it has
- **Case**: `auto: CalculationNoticeDwellTest.aRouteArrivingInsideTheDwellKeepsTheNoticeUp`

#### Scenario: A notice that outlasted its dwell is replaced at once

- **WHEN** the route arrives after the notice has been visible for the whole minimum dwell or longer
- **THEN** the navigation view SHALL replace the notice immediately
- **Case**: `auto: CalculationNoticeDwellTest.aNoticeThatOutlastedItsDwellIsReplacedAtOnce`

#### Scenario: The driver's own answer is never delayed by the dwell

- **WHEN** the calculation is cancelled from the notice, or it fails with no navigation view below the notice
- **THEN** the notice SHALL leave without waiting for the minimum dwell
- **Case**: `auto: CalculationNoticeDwellTest.theDriversOwnAnswerIsNeverDelayedByTheDwell`

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
