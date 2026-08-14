## ADDED Requirements

### Requirement: Touch-based pan

The system SHALL support single-finger drag to pan the map viewport.

- Panning SHALL update the viewport center latitude/longitude in real time
- The map SHALL re-render at the new center when the pan gesture ends
- Pan distance SHALL be converted from screen pixels to geographic delta using the current zoom level's meters-per-pixel ratio
- Pan SHALL feel responsive — gesture tracking SHALL NOT block the UI thread

#### Scenario: Pan map east

- **WHEN** user places one finger on the map and drags left
- **THEN** the viewport center moves east by the corresponding geographic distance
- **WHEN** user lifts finger
- **THEN** the map re-renders at the new center

#### Scenario: Pan map south

- **WHEN** user places one finger on the map and drags up
- **THEN** the viewport center moves south by the corresponding geographic distance
- **WHEN** user lifts finger
- **THEN** the map re-renders at the new center

### Requirement: Pinch-to-zoom

The system SHALL support two-finger pinch to zoom the map viewport.

- Pinch zoom SHALL adjust the magnification level by ±1 step per significant pinch delta
- Zooming in SHALL increase the magnification level; zooming out SHALL decrease it
- The magnification SHALL be clamped to a minimum of 4 and a maximum of 18
- The map SHALL re-render at the new magnification when the pinch gesture ends

#### Scenario: Pinch zoom in

- **WHEN** user places two fingers on the map and spreads them apart
- **THEN** the magnification level increases by 1
- **WHEN** user lifts fingers
- **THEN** the map re-renders at the higher magnification

#### Scenario: Pinch zoom out

- **WHEN** user places two fingers on the map and pinches them together
- **THEN** the magnification level decreases by 1
- **WHEN** user lifts fingers
- **THEN** the map re-renders at the lower magnification

#### Scenario: Zoom clamped at minimum

- **WHEN** magnification is 4 and user pinches to zoom out
- **THEN** the magnification stays at 4
- **THEN** the map does not re-render

#### Scenario: Zoom clamped at maximum

- **WHEN** magnification is 18 and user pinches to zoom in
- **THEN** the magnification stays at 18
- **THEN** the map does not re-render
