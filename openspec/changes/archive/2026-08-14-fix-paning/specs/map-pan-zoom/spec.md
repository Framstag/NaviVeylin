## MODIFIED Requirements

### Requirement: Touch-based pan

The system SHALL support single-finger drag to pan the map viewport.

- Panning SHALL update the viewport center latitude/longitude in real time using Mercator projection
- Pan distance SHALL be converted from screen pixels to geographic delta using `ProjectionUtils.dragDeltaToNewCenterRotated()` with the current viewport rotation angle, magnification, viewport dimensions, and display DPI
- The conversion SHALL reduce to the north-up `dragDeltaToNewCenter()` behavior when the viewport angle is zero
- During a pan gesture, the system SHALL use sub-region blit from the overrun buffer when the new viewport is within bounds
- When the pan extends beyond the overrun buffer, the system SHALL trigger a full re-render at the new center
- Pan gesture events SHALL be debounced at 50ms before triggering a full re-render
- Pan SHALL feel responsive — gesture tracking SHALL NOT block the UI thread

#### Scenario: Pan map east

- **WHEN** user places one finger on the map and drags left
- **THEN** the viewport center moves east by the geographic distance computed via Mercator projection
- **WHEN** the new viewport is within the overrun buffer
- **THEN** the visible sub-region is blitted from the overrun buffer without a native re-render

#### Scenario: Pan map east on a rotated viewport

- **GIVEN** the viewport is rotated by 90 degrees clockwise
- **WHEN** user places one finger on the map and drags left
- **THEN** the viewport center moves south (the drag delta is converted using the viewport rotation angle, not north-up)
- **AND** the map follows the finger exactly as it does on a north-up viewport

#### Scenario: Pan map south

- **WHEN** user places one finger on the map and drags up
- **THEN** the viewport center moves south by the geographic distance computed via Mercator projection
- **WHEN** the new viewport is within the overrun buffer
- **THEN** the visible sub-region is blitted from the overrun buffer without a native re-render

#### Scenario: Pan beyond overrun buffer triggers re-render

- **WHEN** user pans far enough that the viewport extends beyond the overrun buffer
- **THEN** a full native render is triggered at the new center
- **THEN** a new overrun buffer is created
