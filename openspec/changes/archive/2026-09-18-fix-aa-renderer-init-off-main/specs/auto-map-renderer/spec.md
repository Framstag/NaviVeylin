## ADDED Requirements

### Requirement: Renderer initialization off the car-app main thread

The system SHALL initialize the car map renderer off the car-app main thread: native client access, the initial-viewport resolution (saved viewport JSON or first installed map database bounding box), and any native database bounding-box queries SHALL run on a background dispatcher, never on the main/host-callback thread. When the map screen starts while the native client is still building, the main thread SHALL NOT block on native client construction or database queries.

#### Scenario: Template delivered while the native client is still building

- **WHEN** the car map screen is constructed while the session's background warmup is still building or opening the native client
- **THEN** the screen constructor returns without touching the native client on the main thread and the map template is delivered without waiting for renderer initialization

#### Scenario: Surface arrives before the renderer is ready

- **WHEN** the host delivers a map surface before the renderer initialization completes
- **THEN** the surface dimensions and DPI are retained and applied to the renderer when it becomes ready, so the first rendered frame uses the delivered surface

### Requirement: No map state lost during renderer initialization

The system SHALL preserve map state that arrives between screen start and renderer readiness: surface delivery, host day/night state, follow-mode re-centering, north-up/angle changes, GPS position updates, and settings-driven viewport changes that occur before the renderer is ready SHALL be applied to the renderer once it becomes available, with the most recent value of each state winning.

#### Scenario: Dark-mode push during initialization

- **WHEN** the host day/night state changes while the renderer is still initializing
- **THEN** the renderer applies the current dark presentation on readiness and re-renders with the correct style variant

#### Scenario: Follow re-center during initialization

- **WHEN** the map screen starts in follow mode and a GPS fix arrives before the renderer is ready
- **THEN** the renderer centers on the latest fix and shows the GPS marker at the current position once ready, without an intermediate stale viewport

#### Scenario: Renderer still initializing when the screen stops

- **WHEN** the map screen is stopped or destroyed while renderer initialization is still in flight
- **THEN** the pending initialization is cancelled and no renderer work continues after the screen is destroyed
