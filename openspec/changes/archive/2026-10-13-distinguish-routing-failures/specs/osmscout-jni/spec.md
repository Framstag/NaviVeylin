# Spec Delta

## ADDED Requirements

### Requirement: A failed routing call reports a reason

The routing entry point SHALL report a failed calculation with a reason from a closed set, in addition to
the failure callback it already invokes. A caller that reads only the existing failure callback SHALL
observe the behaviour it observes today, and a cancelled calculation SHALL NOT be reported as a failure
with a reason.

#### Scenario: The reason reaches the caller
- **WHEN** the routing entry point fails a calculation
- **THEN** the caller SHALL receive a reason from the closed set for that failure

#### Scenario: An existing caller keeps its behaviour
- **WHEN** a caller reads only the failure callback the entry point invoked before this contract existed
- **THEN** it SHALL still receive a failure notification for every failed calculation
- **AND** it SHALL NOT need to change to keep working

#### Scenario: Cancellation carries no failure reason
- **WHEN** a calculation is cancelled through the routing entry point
- **THEN** the caller SHALL receive the cancellation it receives today
- **AND** no failure reason SHALL be reported for it

#### Scenario: A thrown native fault is reported as one reason
- **WHEN** the native routing work throws instead of returning a result
- **THEN** the failure SHALL reach the caller as a reason and SHALL NOT fault the process

### Requirement: An endpoint beside a direction-restricted road is still attached

When the nearest routable way of the start or the target cannot carry that endpoint in a permitted
direction, the calculation SHALL attach the endpoint to the nearest routable way that can, and SHALL
calculate the route. A calculation SHALL NOT fail because the nearest routable way is
direction-restricted.

#### Scenario: Destination beside a one-way road
- **WHEN** the destination's nearest routable way is one-way and its route node lies behind the point
  where the destination attaches
- **AND** another routable way that can carry the destination lies within the search radius
- **THEN** the route SHALL be calculated
- **AND** the destination SHALL be attached to the nearest routable way that can carry it

#### Scenario: Start beside a direction-restricted road
- **WHEN** the start's nearest routable way is direction-restricted in the direction its route node is
  reachable from
- **AND** another routable way that can carry the start lies within the search radius
- **THEN** the route SHALL be calculated

#### Scenario: Nothing attachable within the search radius
- **WHEN** no routable way within the search radius can carry the endpoint
- **THEN** the calculation SHALL fail with the reason for that end
- **AND** the failure SHALL carry the distance from that end to the nearest road the router could use

#### Scenario: An attachable nearest way is unchanged
- **WHEN** the nearest routable way can carry the endpoint in a permitted direction
- **THEN** the endpoint SHALL be attached to that way, as before
