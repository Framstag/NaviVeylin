# group-grid-display Specification

## Purpose

Lets users browse favorite groups in a visual grid layout instead of a flat list, making it easy to find and interact with groups even when many exist.

## Requirements

### Requirement: Groups displayed in grid
The system SHALL display favorite groups in a scrollable grid layout with two or more columns on phone form factors. When a group has an assigned color, the card SHALL render with a tinted background or shading effect using that color.

#### Scenario: Groups shown as grid cards
- **WHEN** user opens the favorites sheet
- **THEN** groups appear as cards in a grid, each card showing the group name and favorite count

#### Scenario: Group card shows color tint
- **GIVEN** a group has an assigned color
- **WHEN** the favorites sheet displays the group grid
- **THEN** the group card SHALL show a tinted background using the assigned color

#### Scenario: Group card without color shows default
- **GIVEN** a group has no assigned color
- **WHEN** the favorites sheet displays the group grid
- **THEN** the group card SHALL render with the default card style (no tint)

### Requirement: Group card has action menu
Each group card SHALL have a dropdown menu with "Rename", "Set Color", and "Delete" actions.

#### Scenario: Open group action menu
- **WHEN** user taps the menu icon on a group card
- **THEN** a dropdown menu appears with "Rename", "Set Color", and "Delete" options

#### Scenario: Delete group from menu
- **WHEN** user selects "Delete" from the group menu
- **THEN** a confirmation dialog appears before deletion

#### Scenario: Set Color from menu
- **WHEN** user selects "Set Color" from the group menu
- **THEN** a color picker dialog appears with predefined color swatches

### Requirement: Click group card to view favorites
Tapping a group card SHALL navigate to a detail view showing that group's favorites list.

#### Scenario: Navigate to group favorites
- **WHEN** user taps a group card
- **THEN** the view transitions to show only favorites in that group, with a back button to return to the grid

### Requirement: Back navigation from group detail
The group detail view SHALL include a back button to return to the group grid.

#### Scenario: Return to grid from group detail
- **WHEN** user taps the back button in the group detail view
- **THEN** the view returns to the group grid
