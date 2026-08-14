## MODIFIED Requirements

### Requirement: Zoom in button

The system SHALL display a zoom in button overlaid on the map.

- The button SHALL be positioned in the bottom-right corner of the screen
- The button SHALL display a "+" icon
- Tapping the button SHALL increase the magnification level by 1
- The button SHALL be disabled when magnification is at the maximum (20)
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

- **WHEN** magnification is 20
- **THEN** the zoom in button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs
