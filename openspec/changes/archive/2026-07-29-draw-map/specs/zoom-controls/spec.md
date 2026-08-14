## ADDED Requirements

### Requirement: Zoom in button

The system SHALL display a zoom in button overlaid on the map.

- The button SHALL be positioned in the bottom-right corner of the screen
- The button SHALL display a "+" icon
- Tapping the button SHALL increase the magnification level by 1
- The button SHALL be disabled when magnification is at the maximum (18)
- The button SHALL use Material 3 `FloatingActionButton` or equivalent small elevated surface

#### Scenario: Tap zoom in

- **WHEN** user taps the zoom in button
- **THEN** magnification increases by 1
- **THEN** the map re-renders at the higher magnification

#### Scenario: Zoom in button disabled at max

- **WHEN** magnification is 18
- **THEN** the zoom in button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs

### Requirement: Zoom out button

The system SHALL display a zoom out button overlaid on the map.

- The button SHALL be positioned below the zoom in button in the bottom-right corner
- The button SHALL display a "−" icon
- Tapping the button SHALL decrease the magnification level by 1
- The button SHALL be disabled when magnification is at the minimum (4)

#### Scenario: Tap zoom out

- **WHEN** user taps the zoom out button
- **THEN** magnification decreases by 1
- **THEN** the map re-renders at the lower magnification

#### Scenario: Zoom out button disabled at min

- **WHEN** magnification is 4
- **THEN** the zoom out button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs
