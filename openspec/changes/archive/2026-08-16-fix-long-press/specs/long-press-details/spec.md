## MODIFIED Requirements

### Requirement: Long-press gesture detection
The map canvas SHALL detect a long-press gesture: user presses and holds for 500ms without dragging. On detection, the screen coordinates SHALL be converted to geographic coordinates (latitude, longitude) using the current map projection and viewport, including the viewport rotation angle.

#### Scenario: Long press fires after 500ms hold
- **WHEN** user presses on the map canvas and holds for 500ms without moving
- **THEN** the system SHALL fire a long-press callback with the geographic coordinates of the press point

#### Scenario: Long press in rotated viewport
- **WHEN** user long-presses on the map canvas while the viewport is rotated by a non-zero angle
- **THEN** the system SHALL convert the press point to geographic coordinates using the angle-aware projection
- **AND** the resolved object SHALL be the one under the press point

#### Scenario: Long press in north-up viewport
- **WHEN** user long-presses on the map canvas while the viewport angle is zero
- **THEN** the system SHALL convert the press point to geographic coordinates using the standard north-up projection

#### Scenario: Drag cancels long press
- **WHEN** user presses on the map canvas
- **AND** moves the pointer more than 3px before 500ms elapses
- **THEN** the long-press timer SHALL be cancelled
- **AND** no long-press callback SHALL fire

#### Scenario: Release before timeout cancels long press
- **WHEN** user presses on the map canvas
- **AND** releases before 500ms elapses
- **THEN** the long-press timer SHALL be cancelled
- **AND** no long-press callback SHALL fire
