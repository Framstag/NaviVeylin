# Spec Delta

## ADDED Requirements

### Requirement: A stopped renderer holds no surface or frame buffer

A car map renderer whose screen is not started SHALL hold no reference to the car surface and no
rendered frame buffer: it SHALL release both when its screen stops, and it SHALL re-acquire the
session's surface and render a full frame before its first frame after a start. Releasing the surface
reference SHALL NOT release the surface itself, which the session owns.

#### Scenario: Screen stops

- **WHEN** a car screen with a renderer stops (backgrounded, or covered by a pushed screen)
- **THEN** its renderer holds no car surface reference and no overrun frame buffer
- **AND** a later frame of that renderer cannot lock or draw a surface while the screen is stopped

#### Scenario: Screen starts again with the session's surface held

- **WHEN** a stopped car screen starts again while the session still holds its surface
- **THEN** the renderer re-acquires that surface and renders a full frame before its first frame is drawn
- **AND** the first frame after the start is not blitted from a buffer that predates the stop

#### Scenario: Stopped screen after a surface transition

- **WHEN** the host delivers a new surface while a screen with a renderer is stopped
- **THEN** the stopped renderer does not hold the destroyed surface, and it uses the current one after its next start

#### Scenario: Stopped renderer reports no failure

- **WHEN** a screen with a renderer stops while its renderer had reported a surface failure
- **THEN** the stop clears that failure state, so the next start is not treated as a failed surface and no host template refresh is requested for it
