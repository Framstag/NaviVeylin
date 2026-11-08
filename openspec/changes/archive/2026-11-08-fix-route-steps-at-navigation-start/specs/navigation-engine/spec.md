# Spec Delta — navigation-engine

## MODIFIED Requirements

### Requirement: One navigation state shared by all surfaces

The engine SHALL expose navigation state — active/inactive, current step and instructions, remaining
distance, arrival estimate, current and maximum speed, position and bearing, lane guidance, current
road, route geometry, destination identity, vehicle profile and the in-flight route calculation
including its progress — as a single observable state, so
every surface renders the same navigation session. The state's step list SHALL be part of that session
from its start.

#### Scenario: Position update reaches both surfaces

- **WHEN** a position estimate is delivered by the native engine
- **THEN** the phone map and the car navigation view both receive the updated position, bearing, step index and remaining distance

#### Scenario: Destination and vehicle are part of the state

- **WHEN** navigation is active
- **THEN** the state carries the destination identity and the vehicle profile the route was acquired with

#### Scenario: A field published during a background tick is not lost

- **WHEN** the native listener publishes position, lane guidance, instructions, current speed and
  maximum speed while the engine's staleness tick is evaluating
- **THEN** every published field is present in the observable state afterwards
- **AND** a field whose value is the speed-unknown default (maximum speed) is not reverted to that
  default by the tick

#### Scenario: The started route's steps are in the state before the first position update

- **WHEN** navigation is started on a route whose calculation published steps, and no position estimate has been
  delivered yet
- **THEN** the state's step list SHALL carry those steps in route order
- **AND** the current step index SHALL be 0
- **Case**: `app: NavigationEngineStepIndexTest.startNavigation_publishesTheRoutesStepsBeforeAnyPositionUpdate`

#### Scenario: A start replaces the previous route's steps

- **WHEN** a navigation start follows an earlier session, live step list included
- **THEN** the state's step list SHALL carry the new route's steps and no step of the route before it
- **Case**: `app: NavigationEngineStepIndexTest.startNavigation_clearsStaleStepsFromPreviousRoute`
