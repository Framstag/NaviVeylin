# map-rotation-gesture Specification

## Purpose

Allows users to rotate the map directly via a two-finger rotation gesture on the canvas, providing an intuitive alternative to the orientation mode toggle in menus.

## MODIFIED Requirements

### Requirement: Two-finger rotation gesture

The system SHALL support a two-finger rotation gesture on the map canvas that rotates the map viewport by the angle traced by the fingers, applied live to the currently displayed map without triggering pan or zoom as a side effect of the rotation motion.

The rotation angle SHALL be normalized to the range `[-π, π]` radians after each gesture, so repeated rotations cannot grow the angle unbounded.

The gesture SHALL respond to slow rotations whose per-event angle deltas are small: the handler SHALL accumulate raw angle deltas across events and report the accumulated rotation once it exceeds a small threshold, so high-refresh-rate touch sampling (which delivers ~0.01 rad per event during a normal rotation) cannot swallow the gesture.

#### Scenario: Two-finger rotate clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them clockwise
- **THEN** the map SHALL rotate clockwise by the same angle
- **AND** the rotation SHALL be applied to the currently displayed map without any render call during the gesture
- **AND** the map center SHALL NOT drift while the fingers rotate around a fixed midpoint

#### Scenario: Two-finger rotate counter-clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them counter-clockwise
- **THEN** the map SHALL rotate counter-clockwise by the same angle
- **AND** the rotation SHALL be applied to the currently displayed map without any render call during the gesture
- **AND** the map center SHALL NOT drift while the fingers rotate around a fixed midpoint

#### Scenario: Slow rotation with small per-event deltas

- **WHEN** the user rotates two fingers slowly so that each touch event carries an angle delta below the old per-event threshold (e.g. 0.9° per event at high sampling rate)
- **THEN** the map SHALL still rotate by the total angle traced by the fingers
- **AND** the total reported rotation SHALL match the fingers' total rotation within 0.3 rad

#### Scenario: Map stays visible during rotation

- **WHEN** the user rotates two fingers to any angle, including near 180°
- **THEN** the map SHALL remain covering the canvas (no empty regions)
- **AND** the rotation SHALL be applied around the screen center so the map does not swing off-screen
- **AND** the rotation pivot SHALL NOT drift with the fingers' midpoint

#### Scenario: Zoom preview does not exceed the commit range at the limits

- **WHEN** the user pinches to zoom in while the magnification is at the maximum (or zoom out at the minimum)
- **THEN** the live visual zoom SHALL be clamped to the headroom the gesture-end commit can deliver
- **AND** the map SHALL NOT zoom in visually and then snap back on gesture end

#### Scenario: Rotation is re-rendered on gesture end

- **WHEN** the user finishes a two-finger rotation gesture (lifts at least one finger)
- **THEN** the map SHALL be re-rendered exactly once with the final rotation angle
- **AND** labels on the re-rendered map SHALL be drawn in the correct direction (point labels upright, path labels along the rotated roads)

#### Scenario: Rotation does not zoom

- **WHEN** the user rotates two fingers while keeping the distance between them constant
- **THEN** the map magnification SHALL remain unchanged
- **AND** a distance change of less than 20% from the gesture start SHALL NOT change the magnification

#### Scenario: Rotation angle stays bounded

- **WHEN** the user rotates the map repeatedly across multiple gestures
- **THEN** the accumulated rotation angle SHALL remain within `[-π, π]` radians

### Requirement: Rotation gesture disengages follow mode and north-up

The system SHALL disengage GPS follow mode AND the active "always north" orientation when the user performs a manual rotation gesture, since the map is no longer tracking the driving direction or locked to north-up.

#### Scenario: Manual rotation disables follow mode

- **GIVEN** GPS follow mode is enabled
- **WHEN** the user performs a two-finger rotation gesture
- **THEN** follow mode SHALL be disengaged
- **AND** the map SHALL remain at the manually set rotation angle

#### Scenario: Manual rotation disables always-north

- **GIVEN** "always north" orientation is enabled (free-form or navigation mode)
- **WHEN** the user performs a two-finger rotation gesture
- **THEN** the active north-up flag SHALL be cleared
- **AND** the map SHALL remain at the manually set rotation angle
- **AND** re-engaging follow mode SHALL NOT snap the map back to north-up

### Requirement: Rotation gesture does not conflict with pinch-to-zoom

The system SHALL handle two-finger rotation and pinch-to-zoom simultaneously without gesture conflicts. Zoom SHALL be derived from the finger-distance ratio relative to the distance at the moment the second finger touches down, and the magnification SHALL change only when the gesture ends.

#### Scenario: Simultaneous rotate and zoom

- **WHEN** the user places two fingers on the map canvas
- **AND** both rotates and pinches
- **THEN** the map SHALL both rotate and zoom in response to the combined gesture
- **AND** both operations SHALL be applied live to the currently displayed map without any render call during the gesture
- **AND** both the rotation and the magnification change SHALL be committed with a single re-render on gesture end

#### Scenario: Pinch without rotation

- **WHEN** the user pinches two fingers without rotating them
- **THEN** the map SHALL zoom without changing its rotation angle

#### Scenario: Zoom steps on gesture end with limits

- **WHEN** the user pinches two fingers and lifts them
- **THEN** the magnification SHALL change by the nearest whole magnification level implied by the total distance ratio (rounded via `round(log2(ratio))`)
- **AND** the change SHALL be bounded to ±2 magnification levels per gesture
- **AND** the resulting magnification SHALL be clamped to the application limits (4–20)
