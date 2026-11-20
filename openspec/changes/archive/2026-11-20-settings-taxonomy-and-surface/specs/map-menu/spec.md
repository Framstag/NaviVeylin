# Spec Delta — map-menu

## MODIFIED Requirements

### Requirement: Menu entries with leading icons
The menu SHALL contain the entries Download Maps, Favorites, Search, Settings, and About, each with a leading Material icon. Selecting an entry SHALL dismiss the menu and trigger the same action as the previous overflow menu. The menu SHALL NOT contain a separate POI search or Address book entry.

#### Scenario: Download Maps entry
- **WHEN** the user taps the "Download Maps" entry
- **THEN** the menu SHALL dismiss
- **AND** the app SHALL navigate to the map manager screen
- **Case**: none — the Settings entry's cases are written in tasks.md group 2

#### Scenario: Favorites entry
- **WHEN** the user taps the "Favorites" entry
- **THEN** the menu SHALL dismiss
- **AND** the favorites sheet SHALL open
- **Case**: none — the Settings entry's cases are written in tasks.md group 2

#### Scenario: Search POIs entry
- **WHEN** the user taps the "Search" entry
- **THEN** the menu SHALL dismiss
- **AND** the unified search dialog SHALL open
- **Case**: none — the Settings entry's cases are written in tasks.md group 2

#### Scenario: About entry
- **WHEN** the user taps the "About" entry
- **THEN** the menu SHALL dismiss
- **AND** the about dialog SHALL show
- **Case**: none — the Settings entry's cases are written in tasks.md group 2

#### Scenario: Settings entry
- **WHEN** the user taps the "Settings" entry
- **THEN** the menu SHALL dismiss
- **AND** the settings surface SHALL open at its root
- **Case**: none — the Settings entry's cases are written in tasks.md group 2

#### Scenario: No POI search entry
- **WHEN** the user opens the map screen menu
- **THEN** no "Search POIs" entry SHALL be present
- **AND** no "Address book" entry SHALL be present
- **Case**: none — the Settings entry's cases are written in tasks.md group 2
