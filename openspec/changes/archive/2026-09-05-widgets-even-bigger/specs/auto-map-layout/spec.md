## MODIFIED Requirements

### Requirement: Visualisation controls

The system SHALL display the zoom buttons together with Search and Settings in the template action strip (right edge) of the Android Auto map display — host-rendered, always tappable (the AAOS template host does not forward surface gestures, so surface-drawn interactive controls cannot be used). The rotating compass rose appears only during navigation.

#### Scenario: Zoom, search and settings on the right

- **WHEN** the Android Auto map display is visible
- **THEN** the zoom in/out, search and settings actions are shown in the template action strip on the right edge of the display

#### Scenario: Buttons tappable without surface gestures

- **WHEN** the map display is shown on any host (projection or AAOS)
- **THEN** the action buttons are host-rendered and always tappable, independent of map surface gesture delivery

#### Scenario: No compass while browsing

- **WHEN** the browse map is displayed and the user is not navigating
- **THEN** no compass rose is drawn

#### Scenario: Compass rose during navigation

- **WHEN** navigation is active
- **THEN** a compass rose on the navigation display rotates so its north pointer faces true north
- **AND** the rose is right-aligned to the display edge, sized 56 dp

### Requirement: Speed-limit indicator during navigation

The system SHALL display the current speed and the current speed limit on the navigation display in the right visualisation region: a speed badge with the current speed, the speed limit as a round sign below the badge, and a warning color on the badge when the current speed exceeds the limit. The round speed-limit sign SHALL be at least 56dp in diameter with a red ring of at least 7dp and digits of at least 24sp bold.

#### Scenario: Limit badge shown during navigation

- **WHEN** navigation is active and current-speed data is available
- **THEN** the speed badge shows the current speed on the right side of the navigation display inside the stable region

#### Scenario: Speed-limit sign shown

- **WHEN** navigation is active and speed-limit data is available
- **THEN** the current speed limit is shown as a round sign below the speed badge

#### Scenario: Limit exceeded warning

- **WHEN** current speed exceeds the displayed speed limit
- **THEN** the speed badge changes to a warning color

#### Scenario: No sign without a limit

- **WHEN** no speed-limit data is available
- **THEN** no speed-limit sign is drawn

#### Scenario: Sign at least 56dp

- **WHEN** the speed-limit sign is shown
- **THEN** the sign circle is at least 56dp in diameter with a red ring of at least 7dp

#### Scenario: Sign digits at least 24sp

- **WHEN** the speed-limit sign is shown
- **THEN** the digit text uses a font size of 24sp or larger with bold weight
