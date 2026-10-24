# Delta — route-calculation-feedback

## ADDED Requirements

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
