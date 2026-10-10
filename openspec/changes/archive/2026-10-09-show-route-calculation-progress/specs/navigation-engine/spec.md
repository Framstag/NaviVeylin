# Spec Delta

## MODIFIED Requirements

### Requirement: One navigation state shared by all surfaces
The engine SHALL expose navigation state — active/inactive, current step and instructions, remaining
distance, arrival estimate, current and maximum speed, position and bearing, lane guidance, current
road, route geometry, destination identity, vehicle profile and the in-flight route calculation
including its progress — as a single observable state, so
every surface renders the same navigation session.

#### Scenario: Position update reaches both surfaces
- **WHEN** a position estimate is delivered by the native engine
- **THEN** the phone map and the car navigation view both receive the updated position, bearing, step index and remaining distance

#### Scenario: Destination and vehicle are part of the state
- **WHEN** navigation is active
- **THEN** the state carries the destination identity and the vehicle profile the route was acquired with

## ADDED Requirements

### Requirement: Surface-less route acquisition is cancellable
The engine SHALL offer cancellation of an in-flight route acquisition whether or not a surface UI is present, and SHALL release the location lease a surface-less acquisition took whenever that acquisition ends without starting navigation.

#### Scenario: Cancel without a surface UI
- **WHEN** an acquisition that no surface UI drives is cancelled
- **THEN** the engine SHALL request cancellation from the routing engine
- **AND** the acquisition SHALL NOT start navigation

#### Scenario: Aborted acquisition releases the location lease
- **WHEN** a surface-less acquisition is cancelled, fails or reports no GPS position
- **THEN** the location lease taken for that acquisition SHALL be released
