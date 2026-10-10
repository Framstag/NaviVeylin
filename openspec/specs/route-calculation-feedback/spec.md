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
While a calculation is in progress and it outlasts a short configured delay, the car session SHALL show a notice that a route is being calculated, carrying the destination when known and the percentage when known; the notice SHALL NOT appear before the delay has elapsed.

#### Scenario: Notice appears only after the delay
- **WHEN** a calculation is still running after the configured delay has elapsed
- **THEN** the car session SHALL show the calculation notice

#### Scenario: Fast calculation shows no notice
- **WHEN** a calculation completes before the configured delay elapses
- **THEN** no calculation notice SHALL be shown

#### Scenario: Notice carries the percentage
- **WHEN** the notice is shown and a percentage is known
- **THEN** the notice SHALL display the percentage
- **AND** it SHALL display an updated percentage as the calculation progresses

#### Scenario: Notice offers a way out when not navigating
- **WHEN** the notice is shown and navigation is not active
- **THEN** it SHALL offer to cancel the calculation

### Requirement: The car notice never outlives its calculation
The car session SHALL keep the calculation notice consistent with the live calculation: replaced by the navigation view when the route arrives and navigation starts, replaced by the error notice when the calculation fails, and removed when the calculation is cancelled; at most one calculation notice SHALL exist at a time.

#### Scenario: Route arrives
- **WHEN** the route arrives and navigation starts
- **THEN** the navigation view SHALL replace the calculation notice

#### Scenario: Calculation fails
- **WHEN** the calculation fails
- **THEN** the error notice SHALL replace the calculation notice

#### Scenario: Calculation cancelled from the notice
- **WHEN** the driver cancels from the calculation notice
- **THEN** the notice SHALL be removed
- **AND** the screen the driver came from SHALL be shown

#### Scenario: One notice only
- **WHEN** a second calculation starts while the notice is displayed
- **THEN** the existing notice SHALL be updated
- **AND** no second calculation notice SHALL be pushed

#### Scenario: Calculation ended while the session was not visible
- **WHEN** a calculation ends while the car session is not started
- **THEN** no calculation notice SHALL be shown when the session starts again

### Requirement: Route calculation is measured without coordinates
When a route calculation ends the system SHALL record a diagnostics entry carrying only coordinate-free numbers — the trigger of the calculation, its duration in milliseconds, the number of progress values received, and its outcome — so the notice delay is set from observed durations.

#### Scenario: Entry on a successful calculation
- **WHEN** a calculation ends successfully
- **THEN** a diagnostics entry with its trigger, duration, progress count and `outcome=ok` SHALL be recorded

#### Scenario: Entry on a failed calculation
- **WHEN** a calculation ends with an error
- **THEN** the entry SHALL report `outcome=error`

#### Scenario: Entry on a cancelled calculation
- **WHEN** a calculation is cancelled
- **THEN** the entry SHALL report `outcome=cancelled`

#### Scenario: No coordinates in the entry
- **WHEN** the entry is recorded
- **THEN** it SHALL NOT contain a position or a coordinate-formatted string
