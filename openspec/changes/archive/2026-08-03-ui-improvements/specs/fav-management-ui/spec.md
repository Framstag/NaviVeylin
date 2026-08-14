## ADDED Requirements

### Requirement: Favorites sheet resets to main screen on open

The favorites sheet SHALL always start on the main group grid screen when opened, regardless of which sub-screen was last viewed.

#### Scenario: Sheet opens on main screen

- **WHEN** the user opens the favorites sheet
- **THEN** the sheet SHALL display the main group grid
- **AND** SHALL NOT show a previously viewed sub-screen (e.g., group detail or favorite edit)

#### Scenario: Sheet re-opens after closing from sub-screen

- **GIVEN** the user navigated to a group detail sub-screen
- **WHEN** the user closes the favorites sheet
- **AND** re-opens it
- **THEN** the sheet SHALL display the main group grid, not the group detail sub-screen
