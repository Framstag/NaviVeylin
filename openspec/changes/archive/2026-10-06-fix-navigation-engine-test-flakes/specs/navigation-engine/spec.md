# Spec Delta

## MODIFIED Requirements

### Requirement: One navigation state shared by all surfaces

The engine SHALL expose navigation state — active/inactive, current step and instructions, remaining
distance, arrival estimate, current and maximum speed, position and bearing, lane guidance, current
road, route geometry, destination identity and vehicle profile — as a single observable state, so
every surface renders the same navigation session.

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

### Requirement: Engine lifecycle and threading

The engine SHALL live for the process lifetime with a single long-lived scope, SHALL release the native
navigation controller when navigation stops, and SHALL NOT retain location updates while not
navigating. Native navigation calls (start, stop, route calculation, road lookup) SHALL run off the
main thread; navigation state publication SHALL be main-thread confined and SHALL be applied as an
atomic update, so a publisher can never revert a field another publisher set concurrently. The
staleness tick's decision SHALL be taken from the engine's injected time source, not from the system
clock read at the tick site.

#### Scenario: Controller released on stop

- **WHEN** navigation stops
- **THEN** the native navigation controller is released and no further listener callbacks are processed

#### Scenario: No location retention while idle

- **WHEN** the engine is not navigating and no surface holds a location subscription
- **THEN** no location updates are requested on the engine's behalf

#### Scenario: Native call off the main thread

- **WHEN** a route is calculated or a navigation is started
- **THEN** the native call is performed off the main thread
- **AND** the resulting state update is published on the main thread

#### Scenario: A background writer cannot revert a concurrent publication

- **WHEN** one publisher has set a field and a second publisher that started from an earlier state
  snapshot applies its own update
- **THEN** the first publisher's field keeps its published value
- **AND** the second publisher's own field is changed as intended

#### Scenario: The staleness tick reads the injected time source

- **WHEN** the engine's time source reports a moment beyond the staleness window after the last fix
- **THEN** the tick decays the displayed speed to zero
- **AND** when the time source reports the fix as fresh, the tick leaves the displayed speed unchanged

#### Scenario: A native lookup result is published on the main thread

- **WHEN** the road lookup answers on a background thread
- **THEN** the resulting road information is published on the main thread
- **AND** publishing it leaves a field another publisher set meanwhile unchanged
