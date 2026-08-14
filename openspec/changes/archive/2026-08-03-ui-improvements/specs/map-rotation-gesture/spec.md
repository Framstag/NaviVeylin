## Purpose

Allows users to rotate the map directly via a two-finger rotation gesture on the canvas, providing an intuitive alternative to the orientation mode toggle in menus.

## ADDED Requirements

### Requirement: Two-finger rotation gesture

The system SHALL support a two-finger rotation gesture on the map canvas that rotates the map viewport in real time.

#### Scenario: Two-finger rotate clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them clockwise
- **THEN** the map SHALL rotate clockwise by the same angle
- **AND** the rotation SHALL be smooth and continuous during the gesture

#### Scenario: Two-finger rotate counter-clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them counter-clockwise
- **THEN** the map SHALL rotate counter-clockwise by the same angle
- **AND** the rotation SHALL be smooth and continuous during the gesture

### Requirement: Rotation gesture disengages follow mode

The system SHALL disengage GPS follow mode when the user performs a manual rotation gesture, since the map is no longer tracking the driving direction.

#### Scenario: Manual rotation disables follow mode

- **GIVEN** GPS follow mode is enabled
- **WHEN** the user performs a two-finger rotation gesture
- **THEN** follow mode SHALL be disengaged
- **AND** the map SHALL remain at the manually set rotation angle

### Requirement: Rotation gesture does not conflict with pinch-to-zoom

The system SHALL handle two-finger rotation and pinch-to-zoom simultaneously without gesture conflicts.

#### Scenario: Simultaneous rotate and zoom

- **WHEN** the user places two fingers on the map canvas
- **AND** both rotates and pinches
- **THEN** the map SHALL both rotate and zoom in response to the combined gesture
- **AND** both operations SHALL feel responsive and smooth
