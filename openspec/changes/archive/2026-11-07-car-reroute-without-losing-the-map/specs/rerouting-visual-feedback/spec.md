# Spec Delta

## MODIFIED Requirements

### Requirement: Reroute calculation progress is exposed

While a reroute is in progress the system SHALL expose the reroute calculation's progress as the same percentage an initial acquisition exposes, and SHALL present the reroute as visible progress on the car's map surface rather than as silence or as a notice that covers the map.

#### Scenario: Progress during a reroute

- **WHEN** the routing engine reports progress while a reroute is being calculated
- **THEN** the navigation state SHALL carry that percentage
- **AND** the reroute SHALL still be reported as in progress
- **Case**: `auto: CalculationStripTest.theStripShowsTheReportedPercent`

#### Scenario: Reroute progress notice

- **WHEN** a reroute is still running
- **THEN** the car map surface SHALL state the wait and show the percentage when one is known (see `route-calculation-feedback`)
- **AND** no screen SHALL be pushed over the navigation view
- **Case**: `auto: CalculationNoticeSessionTest.noScreenForARerouteHoweverLongItRuns`

#### Scenario: Reroute state clears the progress

- **WHEN** the reroute completes or navigation is stopped while it runs
- **THEN** `isRerouting` SHALL be `false`
- **AND** the state SHALL report no calculation in progress
- **AND** the surface's wait SHALL end
- **Case**: `auto: CalculationStripTest.theWaitEndsWithTheCalculation`
