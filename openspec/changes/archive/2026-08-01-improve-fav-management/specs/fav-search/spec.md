## Purpose

Lets users quickly find a favorite location by typing its name, with results filtered in real time across all groups.

## ADDED Requirements

### Requirement: Search bar on favorites sheet
The system SHALL display a search text field at the top of the favorites sheet, above the group grid.

#### Scenario: Search bar visible
- **WHEN** user opens the favorites sheet
- **THEN** a search bar is visible at the top with placeholder text "Search favorites"

### Requirement: Real-time filter by name
As the user types in the search bar, the system SHALL filter the displayed favorites to only those whose name contains the typed text (case-insensitive).

#### Scenario: Filter favorites by search query
- **WHEN** user types "cafe" in the search bar
- **THEN** only favorites with "cafe" (case-insensitive) in their name are shown

#### Scenario: Clear search shows all
- **WHEN** user clears the search bar text
- **THEN** all favorites are shown again

### Requirement: Search across all groups
The search SHALL match favorites across all groups, not just the currently selected group.

#### Scenario: Cross-group search
- **WHEN** user types a query that matches favorites in multiple groups
- **THEN** results from all matching groups are shown, grouped by their parent group name

### Requirement: Empty state for no matches
When no favorites match the search query, the system SHALL display a clear "No results" message.

#### Scenario: No search results
- **WHEN** user types a query that matches no favorites
- **THEN** a "No favorites match your search" message is displayed
