# Spec Delta

## Purpose

Defines the process-scoped navigation engine: exactly one native navigation controller per process,
one source of navigation truth shared by every surface that draws it, route acquisition that works
with or without a surface UI, and a strict separation between navigation state (engine) and view
state (surface).

## ADDED Requirements

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
road, route geometry, destination identity and vehicle profile — as a single observable state, so
every surface renders the same navigation session.

#### Scenario: Position update reaches both surfaces

- **WHEN** a position estimate is delivered by the native engine
- **THEN** the phone map and the car navigation view both receive the updated position, bearing, step index and remaining distance

#### Scenario: Destination and vehicle are part of the state

- **WHEN** navigation is active
- **THEN** the state carries the destination identity and the vehicle profile the route was acquired with

### Requirement: Route acquisition independent of a surface UI

The engine SHALL acquire a route on request, both when a surface UI drives the acquisition (vehicle
profile, alternatives) and when no surface UI is available, and SHALL re-acquire on reroute with the
vehicle profile the active navigation started with.

#### Scenario: Reroute without a surface UI

- **WHEN** the engine detects a confirmed off-route condition in a process with no phone screen present
- **THEN** the engine re-acquires a route to the retained destination with the retained vehicle profile
- **AND** navigation continues on the new route without any surface action

#### Scenario: Surface-driven acquisition

- **WHEN** a surface acquires a route with a chosen vehicle profile
- **THEN** the engine starts navigation with that route and profile
- **AND** the surface's route views reflect the acquired route

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
main thread; navigation state publication SHALL be main-thread confined.

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
