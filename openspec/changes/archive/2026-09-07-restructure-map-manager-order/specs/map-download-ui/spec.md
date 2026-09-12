## MODIFIED Requirements

### Requirement: Active downloads section
The system SHALL display a collapsible "Active Downloads" section at the top of the content area when downloads are in progress, including basemap downloads. The section SHALL be positioned below the provider row and search field and above all map sections. This section SHALL be hidden when no downloads are active.

#### Scenario: Active downloads section appears during download
- **WHEN** a download starts
- **THEN** an "Active Downloads" section appears at the top of the content area with the current download(s) and progress
- **AND** the section is collapsible

#### Scenario: Basemap download shown with progress
- **WHEN** a basemap download is in progress
- **THEN** the basemap entry appears in the active downloads section with progress
- **AND** a cancel control is available

#### Scenario: Active downloads section hides when empty
- **WHEN** all downloads complete or are cancelled
- **THEN** the "Active Downloads" section disappears

### Requirement: Search/filter available maps
The system SHALL provide a text search field that filters the available maps tree by name or region path. The search field SHALL be positioned at the top of the content area, directly below the provider row. While a search query is non-blank, the installed-maps section SHALL be hidden so search results remain contiguous.

#### Scenario: Search filters tree
- **WHEN** user types in the search field
- **THEN** the tree view filters to show only entries whose name or path matches the query
- **AND** non-matching entries are hidden

#### Scenario: Installed section hidden while searching
- **WHEN** user types a non-blank query in the search field
- **THEN** the installed-maps section is hidden
- **AND** only matching available-map entries are shown

#### Scenario: Search cleared restores installed section
- **WHEN** user clears the search field
- **THEN** the installed-maps section reappears

## ADDED Requirements

### Requirement: Section ordering
The system SHALL render the map manager screen sections in a fixed order: provider row, search field, error banner, active downloads, basemap section, installed maps, available maps. Sections with no content SHALL be omitted without leaving gaps.

#### Scenario: Sections render in fixed order
- **WHEN** user opens the map manager screen with downloads active, maps installed, and maps available
- **THEN** the sections appear in the order: provider row, search field, active downloads, basemap section, installed maps, available maps

#### Scenario: Empty sections omitted
- **WHEN** no downloads are active and no maps are installed
- **THEN** the active-downloads and installed-maps sections are not shown
- **AND** the remaining sections keep their relative order

### Requirement: Loading indicator placement
The system SHALL show the loading indicator at the top of the content area while the available-maps list is being fetched, not between content sections. When loading with no entries yet, the system SHALL show a centered loading indicator instead of scattered sections.

#### Scenario: Loading indicator at top
- **WHEN** the user taps [Refresh] and the fetch is in progress
- **THEN** the loading indicator appears at the top of the content area, below the provider row

#### Scenario: Centered loading on first fetch
- **WHEN** the screen is loading and no maps are installed or available yet
- **THEN** a centered loading indicator is shown
- **AND** no partially loaded map-list sections (installed/available) are rendered

### Requirement: Error banner placement
The system SHALL show the error banner directly below the search field, above all other content, when a fetch, delete, or refresh operation fails.

#### Scenario: Error shown at top
- **WHEN** a fetch or delete operation fails
- **THEN** the error message appears directly below the search field
- **AND** the error is the first content element below the search field
