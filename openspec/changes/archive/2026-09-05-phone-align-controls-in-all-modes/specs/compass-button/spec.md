## MODIFIED Requirements

### Requirement: Compass positioned at top of right view column

The system SHALL position the compass button at the top of the right view column. The right view column SHALL be bottom-anchored: at the bottom-right of the screen in the standard (free-form) view, and above the routing status bar during navigation. The menu, search, and favorites buttons move to the left action column (see `map-canvas-screen`), so the compass is no longer between the menu and search buttons.

#### Scenario: Compass at top of right view column

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be visible at the top of the right view column
- **AND** the right view column SHALL be bottom-anchored
- **AND** the menu (toaster) button SHALL be on the left side of the screen
- **AND** it SHALL appear above the search (🔍) button
