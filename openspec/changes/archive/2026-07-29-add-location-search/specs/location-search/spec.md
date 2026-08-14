## ADDED Requirements

### Requirement: Search button on map screen
The map screen SHALL display a search button overlay positioned at the top-left area (below status bar padding). Pressing the button SHALL open the search panel.

#### Scenario: Search button visible
- **WHEN** the map screen is displayed
- **THEN** a search button (magnifying glass icon) SHALL be visible in the top-left corner

#### Scenario: Search button opens panel
- **WHEN** user taps the search button
- **THEN** a draggable bottom-sheet search panel SHALL open with the search input auto-focused

### Requirement: Draggable bottom-sheet search panel
The search panel SHALL be implemented as a draggable bottom sheet that covers the lower portion of the screen. User SHALL be able to drag it down to dismiss.

#### Scenario: Bottom sheet can be dismissed by dragging down
- **WHEN** the search panel is open
- **THEN** dragging the sheet downward past a threshold SHALL dismiss the panel and return focus to the map

#### Scenario: Map visible behind sheet
- **WHEN** the search panel is open
- **THEN** the map SHALL remain partially visible behind the sheet

### Requirement: Auto-focused search input
When the search panel opens, the text input field SHALL receive focus automatically and the keyboard SHALL appear.

#### Scenario: Input auto-focus on panel open
- **WHEN** the search panel opens
- **THEN** the text input field SHALL have focus and the soft keyboard SHALL be shown

### Requirement: Suggestions-while-type
As the user types, the system SHALL query libosmscout's location search and display matching results below the input. Queries SHALL be debounced by 300ms from the last keystroke.

#### Scenario: Results appear while typing
- **WHEN** user types "Dort" in the search field
- **AND** the debounce interval of 300ms has elapsed
- **THEN** the system SHALL call `OSMScoutClient.searchLocations("Dort", 20)`
- **AND** matching results SHALL appear in a list below the input

#### Scenario: Empty query clears results
- **WHEN** the search field is empty
- **THEN** the results list SHALL be hidden

#### Scenario: Loading indicator during search
- **WHEN** a search query is in progress
- **THEN** a loading indicator SHALL be shown in the results area

#### Scenario: No results state
- **WHEN** the search returns zero results
- **THEN** the system SHALL display "No results found" in the results area

### Requirement: Clear text button
The search input SHALL have a clear (X) button that removes all text from the field.

#### Scenario: Clear button visible when text present
- **WHEN** the search field contains text
- **THEN** a clear button SHALL be visible at the end of the input field

#### Scenario: Clear button removes text
- **WHEN** user taps the clear button
- **THEN** the search field SHALL be emptied
- **AND** the results list SHALL be hidden
- **AND** the input SHALL retain focus

### Requirement: Result item display
Each search result item SHALL display the location label and its region/admin hierarchy.

#### Scenario: Result shows label and region
- **WHEN** search results are displayed
- **THEN** each result SHALL show the location name (`label`) and the region hierarchy (`adminRegionHierarchy` or `region` array joined)

#### Scenario: Result item is tappable
- **WHEN** user taps a result item
- **THEN** the search panel SHALL dismiss
- **AND** the map SHALL center on the selected location's coordinates
- **AND** a marker SHALL be rendered at the selected location

### Requirement: Map centers on selected location
When a search result is selected, the map viewport SHALL move to center on the selected location's latitude/longitude.

#### Scenario: Viewport updates to selection
- **WHEN** user selects a search result with `lat=51.5136, lon=7.4653`
- **THEN** the map center SHALL update to `lat=51.5136, lon=7.4653`
- **AND** the map SHALL re-render at the current magnification

### Requirement: Marker at selected location
When a search result is selected, a marker SHALL be rendered on the map at the selected location.

#### Scenario: Marker rendered via renderWithRouteAndPois
- **WHEN** a search result is selected
- **THEN** the system SHALL call `renderWithRouteAndPois()` with `searchSelLat` and `searchSelLon` set to the selected coordinates
- **AND** a visible marker SHALL appear at that location on the map

#### Scenario: Marker persists on re-render
- **WHEN** the user pans or zooms the map after selecting a location
- **THEN** the marker SHALL remain visible at the selected coordinates during subsequent renders

#### Scenario: New search clears previous marker
- **WHEN** user opens the search panel and selects a new location
- **THEN** the previous marker SHALL be removed
- **AND** a new marker SHALL appear at the newly selected location
