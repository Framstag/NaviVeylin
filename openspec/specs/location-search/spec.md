# Location Search

## Purpose

Allow users to search for locations, addresses, and points of interest via free-text query against the offline libosmscout database. Results are displayed in a draggable bottom-sheet overlay; selecting a result centers the map and renders a marker.

## Requirements

### Requirement: Search button on map screen
The map screen SHALL display a search button overlay positioned at the top-left area (below status bar padding). Pressing the button SHALL open the search panel.

#### Scenario: Search button visible
- **WHEN** the map screen is displayed
- **THEN** a search button (magnifying glass icon) SHALL be visible in the top-left corner

#### Scenario: Search button opens panel
- **WHEN** user taps the search button
- **THEN** a draggable bottom-sheet search panel SHALL open with the search input auto-focused

### Requirement: Stable sheet height
The search bottom sheet SHALL maintain a fixed minimum height from open to dismiss. Sheet height SHALL NOT change when results load, update, or clear. Content exceeding the allocated space SHALL scroll internally.

#### Scenario: Sheet height stable during search lifecycle
- **WHEN** the search panel opens
- **THEN** the sheet SHALL display at its minimum height immediately
- **AND** the height SHALL remain constant while the user types, results load, results display, or results clear
- **AND** the sheet SHALL NOT resize when transitioning between empty, loading, results, and no-results states

#### Scenario: Many results scroll internally
- **WHEN** search returns more results than fit in the allocated space
- **THEN** the result list SHALL scroll within the sheet
- **AND** the sheet SHALL NOT expand to show additional items

#### Scenario: Map visible behind sheet
- **WHEN** the search panel is open
- **THEN** the map SHALL remain partially visible behind the sheet
- **AND** the sheet height SHALL leave at least 30% of the screen visible for the map

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
Each search result item SHALL display the location label and its region/admin hierarchy. When multiple results share the same label, each item SHALL additionally show distinguishing fields (`objectTypeName`, `postalArea`, region tail) to help users differentiate.

#### Scenario: Result shows label and region
- **WHEN** search results are displayed
- **THEN** each result SHALL show the location name (`label`) and the region hierarchy (`adminRegionHierarchy` or `region` array joined)

#### Scenario: Duplicate results show disambiguation fields
- **WHEN** search results are displayed
- **AND** two or more results share the same `label`
- **THEN** each result in the duplicate group SHALL additionally show `objectTypeName`, `postalArea`, and the most specific `region` component
- **AND** these fields SHALL be formatted as a single detail line (e.g., "restaurant · 44139 · Dortmund")

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

### Requirement: Search results reusable for route location picking
The search panel SHALL be reusable for picking start and destination locations in the route panel. When opened from the route panel, the search panel SHALL behave identically to the map screen search, but selecting a result SHALL set the chosen route field instead of centering the map and opening the details sheet.

#### Scenario: Search opens from route start field
- **WHEN** user taps the search icon on the route panel start field
- **THEN** the search panel SHALL open with the existing search-as-you-type behavior
- **AND** selecting a result SHALL set it as the route start location
- **AND** the search panel SHALL close

#### Scenario: Search opens from route destination field
- **WHEN** user taps the search icon on the route panel destination field
- **THEN** the search panel SHALL open with the existing search-as-you-type behavior
- **AND** selecting a result SHALL set it as the route destination location
- **AND** the search panel SHALL close

#### Scenario: Search panel dismiss without selection
- **WHEN** the search panel is open from the route panel
- **AND** user dismisses the search panel without selecting a result
- **THEN** the route field SHALL remain unchanged

### Requirement: Search resilient to inconsistent map data
The system SHALL NOT crash when a map's search index references objects that cannot be read from the map data (e.g., stale or mismatched index entries after a partial download or a dataset/version update). Entries whose object cannot be resolved SHALL be omitted from the results, the remaining entries SHALL be returned normally, and the app SHALL continue running.

#### Scenario: Unreadable result entry is skipped
- **WHEN** a search query returns an entry whose object cannot be read from the map database
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining results SHALL be returned normally

#### Scenario: No crash on corrupt index data
- **WHEN** a search query runs against a map whose search index references an object type outside the database's type configuration
- **THEN** the app SHALL NOT terminate or crash
- **AND** the search SHALL complete with whatever valid results are available

### Requirement: Follow mode deactivated on search result selection
When the user selects a search result while follow-location mode is active, the system SHALL deactivate follow mode before centering the map, so the selected location stays visible and is not overridden by subsequent GPS position updates.

#### Scenario: Follow mode off when result selected
- **WHEN** follow mode is active
- **AND** user selects a search result
- **THEN** follow mode SHALL be deactivated
- **AND** the map SHALL center on the selected location
- **AND** subsequent GPS position updates SHALL NOT re-center the map on the current position

### Requirement: Convenience entries on empty query
When the search field is empty, the search panel SHALL show "Current Location" (if GPS is available) and "Select Favorite" entries above the results area. Typing a query SHALL hide both entries and show location search results only; clearing the field SHALL restore both entries immediately.

#### Scenario: Empty query shows convenience entries
- **WHEN** the search panel opens with an empty query
- **THEN** a "Current Location" entry SHALL be visible (if GPS is available)
- **AND** a "Select Favorite" entry SHALL be visible

#### Scenario: Typing hides convenience entries
- **WHEN** the user types a query
- **THEN** the "Current Location" and "Select Favorite" entries SHALL be hidden
- **AND** only location search results SHALL be listed

#### Scenario: Clearing restores convenience entries
- **WHEN** the user clears the query
- **THEN** the "Current Location" and "Select Favorite" entries SHALL reappear immediately

#### Scenario: Current location entry hidden without GPS
- **WHEN** GPS location is not available
- **THEN** the "Current Location" entry SHALL be hidden
- **AND** the "Select Favorite" entry SHALL remain visible

### Requirement: Search scoped by current admin region
When a usable GPS fix is available, the map screen search panel SHALL resolve the admin region containing the current position and pass it as the default admin region to the native search call. Addresses and POIs SHALL then match when the user omits the region qualifier. The search SHALL still match fully qualified queries regardless of the default region. Without a usable GPS fix (no fix, stale fix, or poor accuracy), the search SHALL run unconstrained, exactly as before this change. Search initiated from the route panel SHALL remain unconstrained.

#### Scenario: Incomplete address matches with GPS fix
- **WHEN** the user has a usable GPS fix inside a known admin region
- **AND** the user types an address or POI name without the region qualifier (e.g. "Hauptstraße 12" while located in Dortmund)
- **THEN** the search results SHALL include matches from the current admin region

#### Scenario: Fully qualified query still matches
- **WHEN** the user types a fully qualified query (e.g. "Hauptstraße 12 Dortmund")
- **THEN** the results SHALL match as before, independent of the current admin region

#### Scenario: No GPS fix falls back to unconstrained search
- **WHEN** the user has no GPS fix, a stale fix (older than the freshness threshold), or a fix with accuracy worse than the accuracy threshold
- **THEN** the search SHALL run without a default admin region
- **AND** the results SHALL be identical to the pre-change behavior

#### Scenario: Default region is fallback only
- **WHEN** the query contains an explicit admin region that differs from the current position's region
- **THEN** the explicit region in the query SHALL take precedence over the default region

### Requirement: Admin region follows user movement
The resolved admin region used for search SHALL track the user's position. Once resolved, the region SHALL be reused for subsequent queries without re-resolution. The system SHALL re-resolve when the position has moved beyond a movement threshold since the last resolution.

#### Scenario: Region re-resolved after significant movement
- **WHEN** the user has moved more than the movement threshold since the last admin region resolution
- **AND** a new search query is submitted
- **THEN** the system SHALL resolve the admin region at the new position and use it for that query

#### Scenario: No re-resolution on minor movement
- **WHEN** the user's position changes by less than the movement threshold since the last resolution
- **AND** a new search query is submitted
- **THEN** the system SHALL reuse the previously resolved admin region without re-resolving

#### Scenario: Stable region during a typing session
- **WHEN** the user is typing a query and results are refreshing per keystroke
- **THEN** the admin region SHALL remain constant for all queries of that session unless the position moved beyond the movement threshold

### Requirement: Resolved region name shown in search panel
When an admin region has been resolved for the current GPS position, the search panel SHALL display the region's name above the search input. The displayed name SHALL follow the currently resolved region: it SHALL appear when resolution succeeds, update when the region is re-resolved after movement, and disappear when no usable GPS fix exists or resolution fails.

#### Scenario: Region name shown above search field
- **WHEN** an admin region is resolved for the current position
- **AND** the search panel is open
- **THEN** the region's name SHALL be displayed above the search input field

#### Scenario: No name without resolved region
- **WHEN** no usable GPS fix exists or region resolution failed
- **AND** the search panel is open
- **THEN** no region name SHALL be displayed above the search input field

#### Scenario: Name follows re-resolution
- **WHEN** the user moves beyond the movement threshold
- **AND** a new admin region is resolved
- **THEN** the displayed name SHALL update to the newly resolved region's name

### Requirement: Free-text matches in search suggestions
The search panel SHALL surface free-text matches (e.g., POI or business names) in addition to structured location results, so queries that do not match an address or location name still find named places.

#### Scenario: POI found while typing
- **WHEN** the user types "cafe central" in the search field
- **AND** the debounce interval of 300ms has elapsed
- **THEN** the results list contains the POI "Café Central" found via free-text search

#### Scenario: Suggestions without text index unchanged
- **WHEN** the current map database has no text index
- **AND** the user types a query
- **THEN** the results list contains only structured location results
- **AND** the app continues to function normally

### Requirement: Result distance display
Each search result entry SHALL display the straight-line (haversine) distance from the current map center to the result location, in kilometers, right-aligned in a smaller font than the entry's primary label text.

#### Scenario: Distance shown for each result
- **WHEN** search results are displayed in the result list
- **THEN** each entry SHALL show the distance from the current map center to the result location in kilometers
- **AND** the distance SHALL be right-aligned within the entry
- **AND** the distance SHALL be rendered in a smaller font than the entry's primary label text

#### Scenario: Distance shown in route panel search
- **WHEN** search results are displayed in the route panel start or destination search
- **THEN** each entry SHALL show the distance from the current map center, formatted and positioned identically to the map search panel

#### Scenario: Distance formatting
- **WHEN** a result is less than 10 km from the map center
- **THEN** the distance SHALL be shown with one decimal place (e.g. "0.5 km")
- **AND** the unit "km" SHALL be included in the displayed value
- **AND** when a result is 10 km or farther, the distance SHALL be shown as whole kilometers (e.g. "12 km")

#### Scenario: Distance follows current map center
- **WHEN** the map is panned or recentered before or during a search
- **THEN** the displayed distances SHALL be computed against the current map center at display time
