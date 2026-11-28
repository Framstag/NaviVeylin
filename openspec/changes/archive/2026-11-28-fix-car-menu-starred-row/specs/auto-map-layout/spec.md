## MODIFIED Requirements

### Requirement: Content box acts as the app menu

The system SHALL use the map template's required content slot as the app menu, headed by the app icon and name, with one entry per destination it opens: free driving, all favorites, POI search, search history, diagnostics, and about. The menu SHALL NOT carry a "Starred favorites" entry, because the starred level is reached through the all-favorites group level's first row (requirement "Favorites grouped by category").

#### Scenario: Menu header shows app identity

- **WHEN** the map display is visible
- **THEN** the content menu header shows the app icon and the app name

#### Scenario: Menu entries open their screens

- **WHEN** the user taps "Free driving"
- **THEN** active navigation stops and the map re-centers on the current GPS position
- **WHEN** the user taps "All favorites"
- **THEN** the full favorites screen opens
- **WHEN** the user taps "Search for POIs"
- **THEN** the POI search screen opens
- **WHEN** the user taps "Search history"
- **THEN** the search history screen opens
- **WHEN** the user taps "Diagnostics"
- **THEN** the diagnostics screen opens
- **WHEN** the user taps "About"
- **THEN** the about screen opens

#### Scenario: History entry re-runs the search

- **WHEN** the user taps a search history entry
- **THEN** the search screen opens prefilled with that query and runs the search

#### Scenario: The menu carries one row per destination it opens

- **WHEN** the map menu is built
- **THEN** it carries six clickable rows, in the order free driving, all favorites, POI search, search history, diagnostics, about
- **AND** it carries no "Starred favorites" row and no callback that pushes the starred level
- **Case**: `auto: MapTemplateFactoryTest.theMenuCarriesSixRowsWithNoStarredEntry`

#### Scenario: A degraded menu adds its notice without losing a row

- **WHEN** the map cannot be drawn and the menu is built
- **THEN** the disabled notice is the first row and the six menu rows follow it, each still clickable
- **Case**: `auto: MapTemplateFactoryTest.theDegradedNoticeIsADisabledFirstRow`
