# POI Search

## Purpose

Lets users search for points of interest (hotels, restaurants, grocery stores) around the current map center by category and radius, browse results, inspect details, and hand off to routing or map display.

## ADDED Requirements

### Requirement: POI search accessible from the map menu
The app SHALL provide a "Search POIs" entry in the map screen menu that opens the POI search sheet.

#### Scenario: Open POI search from menu
- **WHEN** the user opens the map screen menu and selects "Search POIs"
- **THEN** the POI search sheet opens over the map

### Requirement: No category preselected and no preloaded results
The POI search sheet SHALL open with no category selected and SHALL NOT run a search or load results until the user explicitly selects a category and triggers the search.

#### Scenario: Opening the sheet shows no results
- **WHEN** the POI search sheet opens
- **THEN** no category is preselected, no search runs automatically, and the result list is empty

#### Scenario: Search disabled without a category
- **WHEN** the user has not selected a category
- **THEN** the search trigger is disabled

### Requirement: Category and radius selection
The POI search sheet SHALL let the user pick one POI category from the supported set (hotels, restaurants, grocery) and choose a search radius, then trigger a search around the current map center.

#### Scenario: Search with selected category and radius
- **WHEN** the user selects a category and a radius and triggers the search
- **THEN** the app searches for POIs of that category within the chosen radius around the current map center

#### Scenario: Changing category or radius before searching
- **WHEN** the user changes the selected category or radius before triggering a search
- **THEN** the previous result list is not reused for the new selection; a new search is required

### Requirement: POI results list
The app SHALL display POI search results in a list showing the POI label, its object type, and its distance from the search center, styled consistently with the app's other result lists.

#### Scenario: Results displayed
- **WHEN** a POI search returns entries
- **THEN** each entry is shown with its label, object type, and distance from the search center

#### Scenario: Empty results
- **WHEN** a POI search returns no entries
- **THEN** the app shows an empty-results state instead of a blank list

#### Scenario: Search failure
- **WHEN** a POI search fails
- **THEN** the app keeps the sheet usable, shows an error, and does not crash

### Requirement: Search radius up to 100 km
The POI search sheet SHALL offer a search radius of up to 100 km, with the 500 m … 100 km range selectable in steps.

#### Scenario: Maximum radius selectable
- **WHEN** the user sets the search radius to 100 km and triggers a search
- **THEN** the search runs with the 100 km radius and returns POIs within it

### Requirement: Details via single click
Selecting a POI result with a single click SHALL open the location details dialog for that POI, center the map on the POI, and zoom so that the current location and the selected POI are both visible, with visual markers shown at both the current location and the POI.

#### Scenario: Single click opens details and centers the map
- **WHEN** the user clicks a POI result
- **THEN** the location details dialog opens and the map centers on the POI

#### Scenario: Zoom fits current location and POI with markers
- **WHEN** the user clicks a POI result and a current location is available
- **THEN** the zoom level is adjusted so both the current location and the POI are visible, and visual markers are shown at both positions

#### Scenario: No current location available
- **WHEN** the user clicks a POI result and no current location is available
- **THEN** the map centers on the POI with the zoom level unchanged, and a marker is shown at the POI

#### Scenario: Details without description
- **WHEN** the user clicks a POI result and no object description is available
- **THEN** the details dialog opens without a description section and without an error

### Requirement: Viewport restored when POI search closes
Closing the POI search sheet SHALL restore the map center and zoom level to the values they had when the POI search was opened, and SHALL remove the POI marker.

#### Scenario: Close restores viewport
- **WHEN** the user opens POI search, the map moves (centering on a selected POI), and the user then closes the POI search sheet
- **THEN** the map center and zoom level return to the pre-search values

#### Scenario: POI marker removed on close
- **WHEN** the user closes the POI search sheet
- **THEN** the POI marker is removed while the current-location marker behavior is unchanged

### Requirement: Selective action closes both dialogs
When the location details dialog is closed via a selective action (route to location or show on map), the app SHALL close the details dialog AND the POI search sheet.

#### Scenario: Route action closes both
- **WHEN** the user chooses "Route" in the details dialog
- **THEN** the details dialog closes, the route flow starts, and the POI search sheet closes

#### Scenario: Show on map action closes both
- **WHEN** the user chooses "Show" in the details dialog
- **THEN** the details dialog closes, the map centers on the POI, and the POI search sheet closes

#### Scenario: Plain dismiss keeps POI search open
- **WHEN** the user dismisses the details dialog without choosing a selective action
- **THEN** the POI search sheet remains open with its results
