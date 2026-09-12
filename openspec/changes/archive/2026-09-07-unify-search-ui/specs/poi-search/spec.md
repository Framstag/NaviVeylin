# poi-search Delta

## MODIFIED Requirements

### Requirement: POI search accessible from the map menu

The app SHALL provide POI search as the POIs mode of the unified search dialog, reachable from the map screen search button, the "Search" menu entry, and the `/` key.

#### Scenario: Open POI search from menu

- **WHEN** the user opens the map screen menu and selects "Search"
- **THEN** the unified search dialog SHALL open
- **AND** the POIs mode SHALL be selectable

#### Scenario: Open POI search from unified dialog

- **WHEN** the user opens the unified search dialog and selects the POIs mode
- **THEN** the POI search UI SHALL be shown within the dialog

### Requirement: Category and radius selection

The POI search UI SHALL let the user pick one POI category from a searchable dropdown listing the supported set and choose a search radius, then trigger a search around the current map center with an explicit search button. The dropdown SHALL accommodate any number of supported categories and SHALL let the user filter the category list by typing.

#### Scenario: Search with selected category and radius

- **WHEN** the user selects a category and a radius and triggers the search
- **THEN** the app searches for POIs of that category within the chosen radius around the current map center

#### Scenario: Changing category or radius before searching

- **WHEN** the user changes the selected category or radius before triggering a search
- **THEN** the previous result list is not reused for the new selection; a new search is required

#### Scenario: Dropdown lists all supported categories

- **WHEN** the user opens the category dropdown without typing a filter
- **THEN** the dropdown lists every supported category

#### Scenario: Category chosen from searchable dropdown

- **WHEN** the user opens the category dropdown, types text that matches a category, and selects it
- **THEN** that category becomes the selected category

#### Scenario: Typing filters the category list

- **WHEN** the user types text into the category filter field
- **THEN** the dropdown shows only categories whose names match the typed text

#### Scenario: No category matches the filter

- **WHEN** the user types text that matches no category
- **THEN** the dropdown shows no selectable categories and no category is selected

#### Scenario: Selecting the chosen category clears it

- **WHEN** the user selects the category that is already selected
- **THEN** the selection is cleared and the search trigger is disabled

### Requirement: POI results map embedded in the search sheet

The POI search UI SHALL embed an interactive map that shows the location of every search result and the current position when a GPS fix is available. The map SHALL be shown above the result list on portrait screens and to the left of the result list on landscape screens. The embedded map SHALL be independent of the main map's viewport (panning/zooming it SHALL NOT move the main map).

#### Scenario: Map above results in portrait

- **WHEN** the POI search UI shows results on a portrait-oriented screen
- **THEN** the embedded map is displayed above the result list

#### Scenario: Map left of results in landscape

- **WHEN** the POI search UI shows results on a landscape-oriented screen
- **THEN** the embedded map is displayed to the left of the result list

#### Scenario: All results marked on the map

- **WHEN** a POI search returns entries
- **THEN** the embedded map shows a marker at the location of each result

#### Scenario: Current position shown when available

- **WHEN** the POI search UI shows results and a GPS fix is available
- **THEN** the embedded map also shows a current-position marker

#### Scenario: No current position

- **WHEN** the POI search UI shows results and no GPS fix is available
- **THEN** the embedded map shows only the result markers, without error or placeholder

#### Scenario: Embedded map interaction does not move the main map

- **WHEN** the user pans or zooms the embedded map inside the POI search UI
- **THEN** the main map's viewport, center, and magnification SHALL remain unchanged
