## MODIFIED Requirements

### Requirement: Zoom in button

The system SHALL display a zoom in button overlaid on the map.

- The button SHALL be positioned in the bottom-right corner of the screen
- The button SHALL display a "+" icon
- Tapping the button SHALL increase the magnification level by 1
- The button SHALL be disabled when magnification is at the maximum (18)
- The button SHALL use Material 3 `FloatingActionButton` or equivalent small elevated surface
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce)
- The current magnification level SHALL be displayed adjacent to the zoom controls

#### Scenario: Tap zoom in

- **WHEN** user taps the zoom in button
- **THEN** magnification increases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

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
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce)

#### Scenario: Tap zoom out

- **WHEN** user taps the zoom out button
- **THEN** magnification decreases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

#### Scenario: Zoom out button disabled at min

- **WHEN** magnification is 4
- **THEN** the zoom out button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs

### Requirement: Magnification level display

The system SHALL display the current magnification level near the zoom controls.

- The display SHALL show the numeric magnification level (e.g., "12")
- The display SHALL update immediately on zoom change (before the full render completes)
- The display SHALL use Material 3 typography `labelSmall` or equivalent

#### Scenario: Magnification shown after zoom

- **WHEN** user zooms in from level 12 to level 13
- **THEN** the display updates to "13" immediately
- **WHEN** the full render completes
- **THEN** the display remains "13"
