## MODIFIED Requirements

### Requirement: Speed-limit indicator during navigation

The system SHALL display the current speed and the current speed limit on the navigation display in the right visualisation region: a speed badge with the current speed, the speed limit as a round sign below the badge, and — when the current speed exceeds the limit — a warning state consisting of a red badge background with white text. The round speed-limit sign SHALL be at least 56dp in diameter with a red ring of at least 7dp and digits of at least 24sp bold.

#### Scenario: Limit badge shown during navigation

- **WHEN** navigation is active and current-speed data is available
- **THEN** the speed badge shows the current speed on the right side of the navigation display inside the stable region

#### Scenario: Speed-limit sign shown

- **WHEN** navigation is active and speed-limit data is available
- **THEN** the current speed limit is shown as a round sign below the speed badge

#### Scenario: Limit exceeded warning

- **WHEN** current speed exceeds the displayed speed limit
- **THEN** the speed badge shows a red background with white text (warning state)

#### Scenario: Warning background keeps semi-transparency

- **WHEN** the speed badge is in the warning state
- **THEN** the red background retains the same semi-transparent alpha as the normal badge background

#### Scenario: No sign without a limit

- **WHEN** no speed-limit data is available
- **THEN** no speed-limit sign is drawn

#### Scenario: Sign at least 56dp

- **WHEN** the speed-limit sign is shown
- **THEN** the sign circle is at least 56dp in diameter with a red ring of at least 7dp

#### Scenario: Sign digits at least 24sp

- **WHEN** the speed-limit sign is shown
- **THEN** the digit text uses a font size of 24sp or larger with bold weight
