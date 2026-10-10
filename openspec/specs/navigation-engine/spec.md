# navigation-engine Specification

## Purpose
Defines the process-scoped navigation engine: exactly one native navigation controller per process,
one source of navigation truth shared by every surface that draws it, route acquisition that works
with or without a surface UI, and a strict separation between navigation state (engine) and view
state (surface).

## Requirements

### Requirement: Exactly one navigation engine per process

The system SHALL run at most one navigation engine per app process, and that engine SHALL own the
single native navigation controller. No surface SHALL create, own or dispose a navigation controller
of its own.

#### Scenario: Car session joins active phone navigation

- **WHEN** navigation is already active and an Android Auto session starts in the same process
- **THEN** the session observes the existing engine's navigation state
- **AND** no second native navigation controller is started

#### Scenario: Navigation starts from the car with no phone UI

- **WHEN** a car-only destination selection starts navigation and no phone screen has been opened
- **THEN** the engine starts the navigation and the car renders it

#### Scenario: Stop from any surface ends navigation everywhere

- **WHEN** navigation is stopped on one surface
- **THEN** the engine's state leaves the navigating state
- **AND** every other surface observes the stopped state and leaves its navigation presentation

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

#### Scenario: A field published during a background tick is not lost

- **WHEN** the native listener publishes position, lane guidance, instructions, current speed and
  maximum speed while the engine's staleness tick is evaluating
- **THEN** every published field is present in the observable state afterwards
- **AND** a field whose value is the speed-unknown default (maximum speed) is not reverted to that
  default by the tick

### Requirement: Route acquisition independent of a surface UI

The engine SHALL acquire a route on request, both when a surface UI drives the acquisition (vehicle
profile, alternatives) and when no surface UI is available, and SHALL re-acquire on reroute with the
vehicle profile the active navigation started with. An acquisition whose result carries no usable
polyline coordinates SHALL NOT fail the surface that reflects it: the surface's route views SHALL be
left without geometry while navigation continues.

#### Scenario: Reroute without a surface UI

- **WHEN** the engine detects a confirmed off-route condition in a process with no phone screen present
- **THEN** the engine re-acquires a route to the retained destination with the retained vehicle profile
- **AND** navigation continues on the new route without any surface action

#### Scenario: Surface-driven acquisition

- **WHEN** a surface acquires a route with a chosen vehicle profile
- **THEN** the engine starts navigation with that route and profile
- **AND** the surface's route views reflect the acquired route

#### Scenario: Acquisition without usable polyline geometry

- **WHEN** the engine acquires a route whose polyline coordinates are absent
- **THEN** navigation SHALL continue on that route with its destination and vehicle profile retained
- **AND** an observing surface SHALL adopt the acquisition without raising an exception
- **AND** that surface SHALL publish no route geometry for drawing

### Requirement: Errors carry the surface that caused them

An error raised on behalf of a surface SHALL name that surface, and a surface SHALL present only errors
originating from itself or from the engine as a whole — never an error another surface caused.

#### Scenario: Car deep-link failure does not appear on the phone

- **WHEN** a car-only destination request fails to resolve and raises an error
- **THEN** the car presents the error
- **AND** the phone navigation UI does not show that error

#### Scenario: Engine-wide error reaches every surface

- **WHEN** the engine raises an error that is not tied to one surface (e.g. route calculation failed)
- **THEN** every surface that is presenting navigation shows the error

### Requirement: Per-surface view state never moves into the engine

Follow mode, free driving, map viewport, zoom, map rotation, vehicle anchor preset, overlay layout and
render state SHALL remain owned by the surface that displays them. Starting, changing or stopping
navigation on one surface SHALL NOT move another surface's viewport, zoom, rotation or anchor.

#### Scenario: Car navigation does not move the phone viewport

- **WHEN** navigation runs on the car surface while the phone map is in browse mode
- **THEN** the phone viewport, zoom and rotation stay as the user left them
- **AND** the phone anchor preset is unchanged

#### Scenario: Phone free driving is independent of the car

- **WHEN** the car surface is navigating and the phone map is free driving
- **THEN** the phone's follow presentation follows the phone's own free-driving state

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

### Requirement: A failed route attempt releases only the lease it took

The engine SHALL release a location lease for a route attempt only when that attempt took the lease itself. A
route attempt that fails while navigation is already active and that reused the running navigation's lease
SHALL NOT release it, so guidance keeps receiving position updates; the failure SHALL still be published to the
surfaces. An attempt that took the lease for its own start position (a surface-less acquisition) SHALL release
it on failure, as it already does on cancellation.

#### Scenario: Failed reroute keeps the running navigation's lease

- **WHEN** navigation is active and the route calculation of a reroute fails
- **THEN** the running navigation's location lease SHALL stay held
- **AND** position updates SHALL keep reaching the guidance
- **AND** the failure SHALL still be published as an engine error

#### Scenario: Failed surface-less acquisition releases its own lease

- **WHEN** a route attempt that took the location lease to obtain its start position fails while no navigation
  is active
- **THEN** the lease taken for that attempt SHALL be released

### Requirement: Arrival is part of the shared navigation state

The shared navigation state SHALL report whether the running navigation reached its destination. The engine SHALL set that fact when the native navigation engine reports the target reached, SHALL keep it while a reroute replaces the route, and SHALL clear it when a new navigation starts and when navigation stops.

#### Scenario: Destination reached is reported in the shared state

- **WHEN** the native navigation engine reports the target reached while navigation is active
- **THEN** the shared navigation state SHALL report the destination as reached
- **AND** guidance SHALL otherwise be unchanged: navigation stays active, the step list, route geometry and position updates are unaffected

#### Scenario: Arrival is observable without a surface

- **WHEN** the destination was reached and no surface is displaying the navigation
- **THEN** the arrival fact SHALL still be readable from the shared state until it is cleared

#### Scenario: Arrival survives a reroute

- **WHEN** a reroute replaces the route after the destination was reached
- **THEN** the shared state SHALL still report the destination as reached while the new route runs

#### Scenario: Arrival cleared on a new navigation

- **WHEN** navigation starts on a newly acquired route that is not a reroute of the running session
- **THEN** the shared state SHALL NOT report the destination as reached

#### Scenario: Arrival cleared on navigation stop

- **WHEN** navigation stops, whether the user stopped it or a surface ended it
- **THEN** the shared state SHALL NOT report the destination as reached

#### Scenario: Late arrival report after a stop

- **WHEN** the native navigation engine reports the target reached for a session that was already stopped
- **THEN** the shared state SHALL NOT report the destination as reached

### Requirement: Surface-less route acquisition is cancellable
The engine SHALL offer cancellation of an in-flight route acquisition whether or not a surface UI is present, and SHALL release the location lease a surface-less acquisition took whenever that acquisition ends without starting navigation.

#### Scenario: Cancel without a surface UI
- **WHEN** an acquisition that no surface UI drives is cancelled
- **THEN** the engine SHALL request cancellation from the routing engine
- **AND** the acquisition SHALL NOT start navigation

#### Scenario: Aborted acquisition releases the location lease
- **WHEN** a surface-less acquisition is cancelled, fails or reports no GPS position
- **THEN** the location lease taken for that acquisition SHALL be released
