# Spec Delta

## MODIFIED Requirements

### Requirement: Renderer initialization off the car-app main thread

The system SHALL initialize the car map renderer off the car-app main thread: native client access, the initial-viewport resolution (saved viewport JSON or first installed map database bounding box), and any native database bounding-box queries SHALL run on a background dispatcher, never on the main/host-callback thread. When the map screen starts while the native client is still building, the main thread SHALL NOT block on native client construction or database queries. Resolving a car provider (client, favorites, location, settings) SHALL NOT build the native client on the host thread: a screen constructor or a host callback SHALL only retain what it received, and a provider that needs the native client SHALL resolve it on a background dispatcher.

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

### Requirement: No map state lost during renderer initialization

The system SHALL preserve map state that arrives between screen start and renderer readiness: surface delivery, host day/night state, follow-mode re-centering, north-up/angle changes, GPS position updates, and settings-driven viewport changes that occur before the renderer is ready SHALL be applied to the renderer once it becomes available, with the most recent value of each state winning. A renderer that was constructed but not yet handed to the screen SHALL be shut down when the screen is destroyed during initialization, so no background render or display loop outlives its screen.

#### Scenario: Dark-mode push during initialization

- **WHEN** the host day/night state changes while the renderer is still initializing
- **THEN** the renderer applies the current dark presentation on readiness and re-renders with the correct style variant

#### Scenario: Follow re-center during initialization

- **WHEN** the map screen starts in follow mode and a GPS fix arrives before the renderer is ready
- **THEN** the renderer centers on the latest fix and shows the GPS marker at the current position once ready, without an intermediate stale viewport

#### Scenario: Renderer still initializing when the screen stops

- **WHEN** the map screen is stopped or destroyed while renderer initialization is still in flight
- **THEN** the pending initialization is cancelled and no renderer work continues after the screen is destroyed

#### Scenario: Renderer constructed while the screen is being destroyed

- **WHEN** a renderer instance is created and the screen is destroyed before that instance is handed to it
- **THEN** the instance is shut down, and none of its background work (render loop, display-extrapolation loop, zoom-walk loop) stays alive
