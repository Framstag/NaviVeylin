# Spec Delta

## ADDED Requirements

### Requirement: Compass rose follows the resolved surface presentation

The app-drawn compass rose on an Android Auto map surface SHALL use a day palette while that surface renders the daylight variant and a night palette while it renders the dark variant — the same resolved presentation that drives the surface's stylesheet `daylight` flag (the dark-mode preference multiplied with the host day/night signal), never the system night mode of a phone. The rose SHALL never show the palette of the other presentation than the surface it is drawn on, and its geometry (diameter, position, rotation, north pointer direction) SHALL be identical in both presentations. This applies to every Android Auto surface that draws the rose, navigation and free driving alike.

#### Scenario: Day presentation uses the day palette

- **WHEN** the surface renders the daylight variant while the compass rose is drawn
- **THEN** the rose SHALL be drawn with its day palette
- **AND** the rose SHALL remain legible against the daylight map

#### Scenario: Night presentation uses the night palette

- **WHEN** the surface renders the dark variant while the compass rose is drawn
- **THEN** the rose SHALL be drawn with its night palette
- **AND** the rose SHALL remain legible against the dark map

#### Scenario: Rose and map never disagree

- **WHEN** the resolved presentation changes while the rose is drawn
- **THEN** the rose palette and the map's stylesheet variant SHALL change together
- **AND** no frame SHALL show a rose palette belonging to the other presentation

#### Scenario: Host day/night change re-renders the rose

- **WHEN** the host day/night signal changes while navigation or free driving is active
- **THEN** the compass rose SHALL be re-rendered with the palette of the new state without a session restart
- **AND** host template chrome SHALL be unaffected

#### Scenario: Dark-mode preference applies on the car as on the phone

- **WHEN** the dark-mode preference is On while the host reports day
- **THEN** the surface SHALL render the dark variant and the rose SHALL use the night palette

#### Scenario: Free driving rose follows the same presentation

- **WHEN** the free-driving surface draws the compass rose
- **THEN** the rose SHALL use the palette of the presentation that surface renders

#### Scenario: Rose geometry is presentation-independent

- **WHEN** the compass rose is drawn in either presentation
- **THEN** its diameter, right-edge alignment, rotation and north pointer direction SHALL be identical
