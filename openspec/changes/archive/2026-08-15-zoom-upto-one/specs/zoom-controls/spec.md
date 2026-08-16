## MODIFIED Requirements

### Requirement: Zoom out button

The system SHALL display a zoom out button overlaid on the map.

- In portrait orientation, the button SHALL be positioned below the zoom in button in the bottom-right corner
- In landscape orientation, the button SHALL be positioned to the right of the zoom in button in a horizontal row
- The button SHALL display a "−" icon
- Tapping the button SHALL decrease the magnification level by 1
- The button SHALL be disabled when magnification is at the minimum (4)
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce)

#### Scenario: Tap zoom out (portrait)

- **WHEN** user taps the zoom out button in portrait orientation
- **THEN** magnification decreases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

#### Scenario: Tap zoom out (landscape)

- **WHEN** user taps the zoom out button in landscape orientation
- **THEN** magnification decreases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

#### Scenario: Zoom out button disabled at min

- **WHEN** magnification is 4
- **THEN** the zoom out button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs
