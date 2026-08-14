# Zoom Controls Specification

## Purpose

Provide zoom in/out buttons overlaid on the map display for one-step magnification adjustment.
## Requirements
### Requirement: Zoom in button

The system SHALL display a zoom in button overlaid on the map.

- In portrait orientation, the button SHALL be positioned in the bottom-right corner of the screen as part of a vertical column
- In landscape orientation, the button SHALL be positioned as part of a horizontal row at the bottom-right of the screen
- The button SHALL display a "+" icon
- Tapping the button SHALL increase the magnification level by 1
- The button SHALL be disabled when magnification is at the maximum (20)
- The button SHALL use Material 3 `FilledIconButton` or equivalent small elevated surface
- Tapping the button SHALL trigger the debounced render pipeline (200ms zoom debounce)
- The current magnification level SHALL be displayed adjacent to the zoom controls

#### Scenario: Tap zoom in (portrait)

- **WHEN** user taps the zoom in button in portrait orientation
- **THEN** magnification increases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

#### Scenario: Tap zoom in (landscape)

- **WHEN** user taps the zoom in button in landscape orientation
- **THEN** magnification increases by 1
- **THEN** a scaled placeholder is displayed immediately
- **THEN** after the 200ms debounce, a full native render is triggered
- **THEN** the magnification level display updates

#### Scenario: Zoom in button disabled at max

- **WHEN** magnification is 20
- **THEN** the zoom in button is visually disabled
- **WHEN** user taps the disabled button
- **THEN** no action occurs

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

### Requirement: Magnification level display

The system SHALL display the current magnification level near the zoom controls.

- In portrait orientation, the display SHALL appear between the zoom in and zoom out buttons in a vertical arrangement
- In landscape orientation, the display SHALL appear between the zoom in and zoom out buttons in a horizontal arrangement
- The display SHALL show the numeric magnification level (e.g., "12")
- The display SHALL update immediately on zoom change (before the full render completes)
- The display SHALL use Material 3 typography `labelSmall` or equivalent

#### Scenario: Magnification shown after zoom (portrait)

- **WHEN** user zooms in from level 12 to level 13 in portrait orientation
- **THEN** the display updates to "13" immediately
- **WHEN** the full render completes
- **THEN** the display remains "13"

#### Scenario: Magnification shown after zoom (landscape)

- **WHEN** user zooms in from level 12 to level 13 in landscape orientation
- **THEN** the display updates to "13" immediately
- **WHEN** the full render completes
- **THEN** the display remains "13"

