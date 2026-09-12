# map-menu Delta

## MODIFIED Requirements

### Requirement: Menu entries with leading icons

The menu SHALL contain the entries Download Maps, Favorites, Search, and About, each with a leading Material icon. Selecting an entry SHALL dismiss the menu and trigger the same action as the previous overflow menu. The menu SHALL NOT contain a separate POI search or Address book entry.

#### Scenario: Download Maps entry

- **WHEN** the user taps the "Download Maps" entry
- **THEN** the menu SHALL dismiss
- **AND** the app SHALL navigate to the map manager screen

#### Scenario: Favorites entry

- **WHEN** the user taps the "Favorites" entry
- **THEN** the menu SHALL dismiss
- **AND** the favorites sheet SHALL open

#### Scenario: Search POIs entry

- **WHEN** the user taps the "Search" entry
- **THEN** the menu SHALL dismiss
- **AND** the unified search dialog SHALL open

#### Scenario: About entry

- **WHEN** the user taps the "About" entry
- **THEN** the menu SHALL dismiss
- **AND** the about dialog SHALL show

#### Scenario: No POI search entry

- **WHEN** the user opens the map screen menu
- **THEN** no "Search POIs" entry SHALL be present
- **AND** no "Address book" entry SHALL be present
