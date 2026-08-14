## ADDED Requirements

### Requirement: Group card menu has "Set Color" option
The group card's action menu SHALL include a "Set Color" option that opens a color picker dialog.

#### Scenario: Set Color in group menu
- **WHEN** user taps the menu icon on a group card
- **THEN** the dropdown menu SHALL include "Set Color" alongside existing "Rename" and "Delete" options

#### Scenario: Color picker dialog appears
- **WHEN** user selects "Set Color" from the group menu
- **THEN** a color picker dialog SHALL appear with a set of predefined color swatches

#### Scenario: Color applied and card updates
- **WHEN** user selects a color swatch and confirms
- **THEN** the group color SHALL be saved and the group card SHALL immediately update with the new color tint

### Requirement: Favorite item has star toggle button
Each favorite item row SHALL include a star icon button that toggles the starred state.

#### Scenario: Star button on favorite row
- **WHEN** a favorite is displayed in a list
- **THEN** a star icon button SHALL be shown on the right side of the row, alongside rename and delete buttons

#### Scenario: Toggle star from favorite row
- **WHEN** user taps the star icon on a favorite row
- **THEN** the starred state SHALL toggle and the icon SHALL update immediately

### Requirement: Starred chip bar at top of main view
The favorites sheet main view SHALL display a horizontal scrollable chip bar above the group grid showing all starred favorites.

#### Scenario: Chip bar above group grid
- **WHEN** the favorites sheet main view is displayed and starred favorites exist
- **THEN** a chip bar SHALL appear above the group grid, before the search bar

#### Scenario: Chip click opens route panel
- **WHEN** user taps a starred favorite chip
- **THEN** the favorites sheet SHALL close
- **AND** the route panel SHALL open with current location as start and the tapped favorite as destination
