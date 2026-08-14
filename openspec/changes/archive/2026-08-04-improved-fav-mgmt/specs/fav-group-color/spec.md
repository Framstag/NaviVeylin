## Purpose

Lets users assign a color to a favorite group for visual identification, with the group card rendered using a tinted background or shading effect.

## ADDED Requirements

### Requirement: Group color can be set via card menu
The system SHALL allow users to assign a color to a favorite group by selecting "Set Color" from the group card's action menu. The color SHALL be persisted in the group's `attributes["color"]` map.

#### Scenario: Open color picker from group menu
- **WHEN** user taps the menu icon on a group card
- **THEN** the dropdown menu SHALL include a "Set Color" option

#### Scenario: Select color from picker
- **WHEN** user selects "Set Color" and picks a color from the color picker
- **THEN** the color SHALL be saved to the group and the group card SHALL update with the new color

#### Scenario: Color persists across app restart
- **WHEN** user assigns a color to a group, closes the app, and reopens
- **THEN** the group card SHALL still display the assigned color

### Requirement: Group card renders with color shading
When a group has an assigned color, the group card SHALL render with a tinted background or shading effect using that color.

#### Scenario: Group card shows color tint
- **GIVEN** a group has an assigned color
- **WHEN** the favorites sheet displays the group grid
- **THEN** the group card SHALL show a tinted background using the assigned color

#### Scenario: Group card without color shows default
- **GIVEN** a group has no assigned color
- **WHEN** the favorites sheet displays the group grid
- **THEN** the group card SHALL render with the default card style (no tint)
