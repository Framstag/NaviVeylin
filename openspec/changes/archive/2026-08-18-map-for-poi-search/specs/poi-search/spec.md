## ADDED Requirements

### Requirement: POI results map embedded in the search sheet
The POI search sheet SHALL embed an interactive map that shows the location of every search result and the current position when a GPS fix is available. The map SHALL be shown above the result list on portrait screens and to the left of the result list on landscape screens. The embedded map SHALL be independent of the main map's viewport (panning/zooming it SHALL NOT move the main map).

#### Scenario: Map above results in portrait
- **WHEN** the POI search sheet shows results on a portrait-oriented screen
- **THEN** the embedded map is displayed above the result list

#### Scenario: Map left of results in landscape
- **WHEN** the POI search sheet shows results on a landscape-oriented screen
- **THEN** the embedded map is displayed to the left of the result list

#### Scenario: All results marked on the map
- **WHEN** a POI search returns entries
- **THEN** the embedded map shows a marker at the location of each result

#### Scenario: Current position shown when available
- **WHEN** the POI search sheet shows results and a GPS fix is available
- **THEN** the embedded map also shows a current-position marker

#### Scenario: No current position
- **WHEN** the POI search sheet shows results and no GPS fix is available
- **THEN** the embedded map shows only the result markers, without error or placeholder

#### Scenario: Embedded map interaction does not move the main map
- **WHEN** the user pans or zooms the embedded map inside the POI search sheet
- **THEN** the main map's viewport, center, and magnification SHALL remain unchanged

### Requirement: Selection changes the maps
Selecting a POI result SHALL update both the embedded map and the main map: the embedded map SHALL highlight the selected result's marker, and the main map SHALL center on the selected POI (existing single-click behavior: details dialog opens, map centers on the POI).

#### Scenario: Selection highlights the result on the embedded map
- **WHEN** the user taps a result in the list
- **THEN** the selected result's marker is visually distinguished from the other result markers on the embedded map

#### Scenario: Main map centers on the selected result
- **WHEN** the user taps a result in the list
- **THEN** the main map centers on the selected POI (existing "Details via single click" behavior is unchanged)
