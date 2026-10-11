# Spec Delta — auto-map-renderer

## MODIFIED Requirements

### Requirement: Renderer initialization off the car-app main thread

The system SHALL initialize the car map renderer off the car-app main thread: native client access, the initial-viewport resolution (saved viewport JSON or first installed map database bounding box), and any native database bounding-box queries SHALL run on a background dispatcher, never on the main/host-callback thread. When the map screen starts while the native client is still building, the main thread SHALL NOT block on native client construction or database queries. Resolving a car provider (client, favorites, location, settings) SHALL NOT build the native client on the host thread: a screen constructor or a host callback SHALL only retain what it received, and a provider that needs the native client SHALL resolve it on a background dispatcher. A car screen's observation body SHALL NOT be an exception: it is launched from a lifecycle callback and runs on the main dispatcher, so a provider it resolves before its first emission SHALL be resolved inside a background context — a coroutine dispatching on the main dispatcher is not that context.

#### Scenario: Template delivered while the native client is still building

- **WHEN** the car map screen is constructed while the session's background warmup is still building or opening the native client
- **THEN** the screen constructor returns without touching the native client on the main thread and the map template is delivered without waiting for renderer initialization

#### Scenario: Surface arrives before the renderer is ready

- **WHEN** the host delivers a map surface before the renderer initialization completes
- **THEN** the surface dimensions and DPI are retained and applied to the renderer when it becomes ready, so the first rendered frame uses the delivered surface

#### Scenario: Provider resolution does not build the client

- **WHEN** a car screen resolves its client, favorites, location or settings provider before the native client exists
- **THEN** the native client is not built on the calling (host) thread
- **AND** the client is built on a background dispatcher

#### Scenario: Surface delivery never builds the client

- **WHEN** the host delivers a surface while the native client is still being built
- **THEN** the surface callback returns without building or touching the native client

#### Scenario: A screen observation resolves its provider off the host thread

- **WHEN** a car screen's observation body resolves its favorites provider during a screen start, while the session's warmup is still building the native client
- **THEN** the call SHALL NOT be made on the main dispatcher's thread
- **AND** the observation SHALL reach the provider from a background context and continue from the value it gets there

#### Scenario: The host thread stays free while the observation waits for the client

- **WHEN** the car favorites screen starts while the native client is still being built by the session's warmup
- **THEN** the template request that follows the screen start SHALL be answered without waiting for that build
