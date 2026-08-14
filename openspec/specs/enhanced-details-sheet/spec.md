# enhanced-details-sheet Specification

## Purpose

Provides a draggable bottom sheet that displays structured OSM object descriptions with section headers, label/value rows, and favorite management — reused for both search results and long-press.

## Requirements

### Requirement: Draggable bottom sheet
The details sheet SHALL be implemented as a `ModalBottomSheet` that the user can drag down to dismiss. It SHALL NOT use `skipPartiallyExpanded` so the drag gesture is active. The sheet SHALL display a visible drag handle at the top.

#### Scenario: Sheet can be dismissed by dragging down
- **WHEN** the details sheet is open
- **THEN** dragging the sheet downward past a threshold SHALL dismiss it
- **AND** the map SHALL remain visible behind the sheet

#### Scenario: Sheet opens from search result selection
- **WHEN** user taps a search result
- **THEN** the details sheet SHALL open with the selected location's information
- **AND** the search panel SHALL close

#### Scenario: Sheet opens from long-press
- **WHEN** user long-presses on the map
- **AND** a nearby object with description data is found
- **THEN** the details sheet SHALL open with the object's structured description

### Requirement: Structured description display
The sheet SHALL render `ObjectDescription` entries grouped by section. Each section SHALL display as a header (e.g., "General", "Location", "Contact") followed by its label/value rows. Subsections SHALL display as sub-headers indented under their parent section. Repeated subsections (with index) SHALL show the index.

#### Scenario: Sections rendered as headers with label/value rows
- **WHEN** the description contains a "General" section with entries for type and name
- **THEN** the sheet SHALL display "General" as a section header
- **AND** show "Type: restaurant" and "Name: Mario's" as label/value rows below

#### Scenario: Subsections rendered indented
- **WHEN** the description contains a "Location" section with an "Admin Level" subsection
- **THEN** the sheet SHALL display "Location" as a section header
- **AND** "Admin Level" as a subsection header indented below
- **AND** show the admin level label/value rows under the subsection

#### Scenario: Empty description shows no sections
- **WHEN** the `ObjectDescription` has zero entries
- **THEN** the sheet SHALL display only the location name, coordinates, and favorite controls
- **AND** no section headers SHALL be shown

### Requirement: Favorite management in sheet
The details sheet SHALL support adding the current location to favorites and removing it from favorites, matching the existing behavior from search results.

#### Scenario: Add to favorites from details sheet
- **WHEN** the details sheet is open
- **AND** the location is not already a favorite
- **THEN** an "Add to Favorites" button SHALL be visible
- **AND** tapping it SHALL show a group picker and name input
- **AND** confirming SHALL save the favorite

#### Scenario: Remove from favorites from details sheet
- **WHEN** the details sheet is open
- **AND** the location is already a favorite
- **THEN** a "Remove from Favorites" button SHALL be visible
- **AND** tapping it SHALL remove the location from favorites

### Requirement: Coordinates display
The sheet SHALL always display the latitude and longitude of the selected location, formatted to 5 decimal places.

#### Scenario: Coordinates shown in sheet
- **WHEN** the details sheet is open
- **THEN** the coordinates SHALL be displayed as "lat, lon" formatted to 5 decimal places
- **AND** the text SHALL use a subdued color style

### Requirement: Route button in details sheet
The details sheet SHALL display a "Route" button that opens the route panel with the current location prefilled as the start point. This button SHALL be positioned alongside the favorite controls.

#### Scenario: Route button visible
- **WHEN** the details sheet is open
- **THEN** a "Route" button SHALL be visible in the sheet
- **AND** tapping it SHALL dismiss the details sheet and open the route panel with the location prefilled as start

#### Scenario: Route button with favorite controls
- **WHEN** the details sheet is open
- **AND** the location is not a favorite
- **THEN** both the "Add to Favorites" button and the "Route" button SHALL be visible
