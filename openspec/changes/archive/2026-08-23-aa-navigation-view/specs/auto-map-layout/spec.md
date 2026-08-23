# auto-map-layout Delta

## MODIFIED Requirements

### Requirement: Speed-limit indicator during navigation

The system SHALL display the current speed and the current speed limit on the navigation display in the right visualisation region: a speed badge with the current speed, the speed limit as a round sign below the badge, and a warning color on the badge when the current speed exceeds the limit.

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

## REMOVED Requirements

### Requirement: Navigation hints left-oriented

During navigation, the system SHALL display the navigation hints (next-turn instruction, distance, lane guidance) left-aligned on the display, positioned immediately to the right of the action strip, and SHALL NOT let the hints overlap the visualisation strip on the right.

#### Scenario: Hints positioned right of action strip

- **WHEN** navigation is active
- **THEN** the navigation hints appear left-aligned, to the right of the action strip

#### Scenario: Hints do not cover right visualisation strip

- **WHEN** navigation is active and both strips are visible
- **THEN** the navigation hints do not extend into the horizontal region occupied by the right visualisation strip

#### Scenario: Hints update on approach

- **WHEN** the vehicle approaches the next turn
- **THEN** the hint text and distance update in place without changing position

#### Scenario: Hints not clipped by host chrome

- **WHEN** the navigation display is shown on an Android unit with horizontal chrome
- **THEN** the hint panel stays inside the host-reported stable region of the surface

**Reason**: The custom surface-drawn hint panel is replaced by the host-rendered `NavigationTemplate` instruction panel (next-turn maneuver, step list, lane guidance) defined by the `auto/navigation-view` capability. Two instruction sources on the same display would conflict.

**Migration**: Host instruction panel renders next-turn, route-description steps and lane guidance (see `auto/navigation-view`). The compass rose and speed-limit badge requirements above remain unchanged and stay surface-drawn.
