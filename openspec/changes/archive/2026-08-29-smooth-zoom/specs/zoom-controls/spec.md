# zoom-controls

## MODIFIED Requirements

### Requirement: Zoom in button

The system SHALL display a zoom in button overlaid on the map.

- In portrait orientation, the button SHALL be positioned in the bottom-right corner of the screen as part of a vertical column
- In landscape orientation, the button SHALL be positioned as part of a horizontal row at the bottom-right of the screen
- The button SHALL display a "+" icon
- Tapping the button SHALL increase the magnification level by 1
- The button SHALL be disabled when magnification is at the maximum (20)
- The button SHALL use Material 3 `FilledIconButton` or equivalent small elevated surface
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce) and SHALL start an eased zoom animation toward the target magnification (smooth-zoom)
- The current magnification level SHALL be displayed adjacent to the zoom controls

#### Scenario: Tap zoom in (portrait)

- **WHEN** user taps the zoom in button in portrait orientation
- **THEN** magnification increases by 1
- **THEN** an eased zoom animation plays from the current scale toward the target magnification while the debounced render runs
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates immediately to the target level

#### Scenario: Tap zoom in (landscape)

- **WHEN** user taps the zoom in button in landscape orientation
- **THEN** magnification increases by 1
- **THEN** an eased zoom animation plays from the current scale toward the target magnification while the debounced render runs
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates immediately to the target level

#### Scenario: Zoom in button disabled at max

- **WHEN** magnification is 20
- **THEN** the zoom in button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs

#### Scenario: Zoom in tapped while zoom animation runs

- **WHEN** user taps the zoom in button while a previous zoom animation is still running
- **THEN** the running animation SHALL retrack smoothly toward the new target magnification without snapping back

### Requirement: Zoom out button

The system SHALL display a zoom out button overlaid on the map.

- In portrait orientation, the button SHALL be positioned below the zoom in button in the bottom-right corner
- In landscape orientation, the button SHALL be positioned to the right of the zoom in button in a horizontal row
- The button SHALL display a "−" icon
- Tapping the button SHALL decrease the magnification level by 1
- The button SHALL be disabled when magnification is at the minimum (4)
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce) and SHALL start an eased zoom animation toward the target magnification (smooth-zoom)

#### Scenario: Tap zoom out (portrait)

- **WHEN** user taps the zoom out button in portrait orientation
- **THEN** magnification decreases by 1
- **THEN** an eased zoom animation plays from the current scale toward the target magnification while the debounced render runs
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates immediately to the target level

#### Scenario: Tap zoom out (landscape)

- **WHEN** user taps the zoom out button in landscape orientation
- **THEN** magnification decreases by 1
- **THEN** an eased zoom animation plays from the current scale toward the target magnification while the debounced render runs
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates immediately to the target level

#### Scenario: Zoom out button disabled at min

- **WHEN** magnification is 4
- **THEN** the zoom out button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs

#### Scenario: Zoom out tapped while zoom animation runs

- **WHEN** user taps the zoom out button while a previous zoom animation is still running
- **THEN** the running animation SHALL retrack smoothly toward the new target magnification without snapping back