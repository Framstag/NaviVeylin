## MODIFIED Requirements

### Requirement: Follow mode activated

The system SHALL keep the vehicle marker during free driving at the configured free-driving anchor position while follow mode is active, instead of at the screen center. The anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height). The default free-driving anchor is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the heading-up rotation (free driving is always heading-up).

#### Scenario: Map re-centers on GPS position

- **WHEN** the GPS position moves while free driving and the user does not pan the map
- **THEN** the map render target shifts so the map keeps the vehicle marker at the configured anchor position
- **AND** with the default center/center anchor this reproduces re-centering the viewport on the GPS position

#### Scenario: Default anchor reproduces today's framing

- **GIVEN** the free-driving anchor is at its default center/center
- **WHEN** free driving is active in follow mode
- **THEN** the vehicle marker projects to the center of the free-driving surface
- **AND** the map framing is identical to free driving without anchor presets

#### Scenario: Anchor kept under heading-up rotation

- **WHEN** the vehicle bearing changes while free driving
- **THEN** the map rotates heading-up with the bearing
- **AND** the vehicle marker keeps projecting to the chosen anchor position

#### Scenario: Anchor restored after manual pan

- **WHEN** the user pans the map during free driving (follow suspended)
- **AND** the user then stops panning
- **THEN** follow mode re-engages and the vehicle marker returns to the free-driving anchor without a snap

#### Scenario: Auto-zoom keeps the anchored vehicle in view

- **WHEN** speed-driven auto-zoom changes the magnification while free driving
- **THEN** the map zooms around the anchor position
- **AND** the vehicle marker stays at the chosen anchor
