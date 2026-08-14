## Purpose

Lets users plan a route between two locations with vehicle choice, view the route polyline on the map, and see turn-by-turn instructions — bridging search results to route calculation.

## ADDED Requirements

### Requirement: Route button on location details sheet
The `LocationDetailsSheet` SHALL display a "Route" button that opens the route panel with the current location prefilled as the start point.

#### Scenario: Route button visible in details sheet
- **WHEN** the location details sheet is open
- **THEN** a "Route" button SHALL be visible alongside the existing favorite controls
- **AND** tapping it SHALL dismiss the details sheet and open the route panel

#### Scenario: Route button prefills destination, start = current location
- **WHEN** user taps "Route" on a details sheet for location "Museum Island, Berlin"
- **AND** GPS location is available
- **THEN** the route panel SHALL open with start set to current GPS location
- **AND** destination set to "Museum Island, Berlin" with its lat/lon coordinates

#### Scenario: Route button prefills destination only when GPS unavailable
- **WHEN** user taps "Route" on a details sheet
- **AND** GPS location is not available
- **THEN** the route panel SHALL open with destination set to the details sheet location
- **AND** start field SHALL show placeholder text

### Requirement: Route panel with start and destination fields
The route panel SHALL be a modal bottom sheet with two location fields: start and destination. Each field SHALL be an editable text field that triggers search-on-type as the user types. Each field SHALL support three input methods: search (via text input), favorite picker (via "Select Favorite" list entry), and current location (via "Current Location" list entry). When a location is selected, the field SHALL display the location label and SHALL be read-only until cleared.

#### Scenario: Route panel opens with start prefilled from details sheet
- **WHEN** the route panel opens from a details sheet "Route" button
- **THEN** the start field SHALL show "Current Location" (if GPS available)
- **AND** the destination field SHALL show the location label from the details sheet

#### Scenario: Route panel opens empty
- **WHEN** the route panel opens from the map screen (not from a details sheet)
- **THEN** both start and destination fields SHALL show placeholder text

#### Scenario: Start field shows search results while typing
- **WHEN** user taps the start field and types a query
- **THEN** the search panel SHALL open inline with search-as-you-type behavior
- **AND** the first result entry SHALL be "Current Location" (if GPS available)
- **AND** the second result entry SHALL be "Select Favorite"
- **AND** remaining entries SHALL be location search results
- **AND** selecting a result SHALL set it as the start location and close the results

#### Scenario: Destination field shows search results while typing
- **WHEN** user taps the destination field and types a query
- **THEN** the search panel SHALL open inline with search-as-you-type behavior
- **AND** the first result entry SHALL be "Current Location" (if GPS available)
- **AND** the second result entry SHALL be "Select Favorite"
- **AND** remaining entries SHALL be location search results
- **AND** selecting a result SHALL set it as the destination location and close the results

#### Scenario: Field is read-only when location selected
- **WHEN** a location is selected for a field
- **THEN** the field SHALL display the location label
- **AND** the field SHALL be read-only (not editable)
- **AND** a clear (X) button SHALL be visible to remove the selection

#### Scenario: Clear button resets field
- **WHEN** user taps the clear (X) button on a field with a selected location
- **THEN** the field SHALL be cleared
- **AND** the field SHALL become editable again

#### Scenario: Current location option hidden when GPS unavailable
- **WHEN** GPS location is not available (no fix or permission denied)
- **THEN** the "Current Location" entry SHALL be hidden in both start and destination field search results

### Requirement: Swap start and destination
The route panel SHALL have a swap button that exchanges the start and destination locations.

#### Scenario: Swap exchanges locations
- **WHEN** start is "Museum Island" and destination is "Brandenburg Gate"
- **AND** user taps the swap button
- **THEN** start SHALL become "Brandenburg Gate"
- **AND** destination SHALL become "Museum Island"

### Requirement: Vehicle selector
The route panel SHALL provide a vehicle selector with three options: Car, Bicycle, and Pedestrian. The selected vehicle SHALL be visually highlighted. The default selection SHALL be Car.

#### Scenario: Car selected by default
- **WHEN** the route panel opens
- **THEN** the Car button SHALL be visually highlighted as selected
- **AND** the routing profile SHALL use `Vehicle.CAR`

#### Scenario: Switch to bicycle
- **WHEN** user taps the Bicycle button
- **THEN** the Bicycle button SHALL be visually highlighted
- **AND** the routing profile SHALL use `Vehicle.BICYCLE`

#### Scenario: Switch to pedestrian
- **WHEN** user taps the Pedestrian button
- **THEN** the Pedestrian button SHALL be visually highlighted
- **AND** the routing profile SHALL use `Vehicle.PEDESTRIAN`

### Requirement: Calculate route
When both start and destination are set, the route panel SHALL display a "Calculate" button. Tapping it SHALL call `OSMScoutClient.calculateRouteAsync()` with the selected start/dest coordinates and routing profile.

#### Scenario: Calculate button enabled when both fields set
- **WHEN** both start and destination locations are set
- **THEN** the "Calculate" button SHALL be enabled
- **AND** tapping it SHALL initiate route calculation

#### Scenario: Calculate button disabled when fields missing
- **WHEN** either start or destination is not set
- **THEN** the "Calculate" button SHALL be disabled

#### Scenario: Progress indicator during calculation
- **WHEN** route calculation is in progress
- **THEN** a progress indicator SHALL be shown in the route panel
- **AND** the "Calculate" button SHALL be replaced with a "Cancel" button

#### Scenario: Route polyline rendered on map
- **WHEN** route calculation completes successfully
- **THEN** the route polyline SHALL be rendered on the map via `renderWithRoute()`
- **AND** `_route_start` and `_route_end` markers SHALL appear at the start and destination coordinates

#### Scenario: Route calculation failure
- **WHEN** route calculation fails (no route found, disconnected graph)
- **THEN** an error message SHALL be displayed in the route panel
- **AND** no route polyline SHALL be rendered

### Requirement: Cancel route calculation
During route calculation, the user SHALL be able to cancel the operation.

#### Scenario: Cancel during calculation
- **WHEN** route calculation is in progress
- **AND** user taps the "Cancel" button
- **THEN** `OSMScoutClient.cancelRoute()` SHALL be called
- **AND** the progress indicator SHALL be removed
- **AND** the "Calculate" button SHALL be re-enabled

### Requirement: Clear route
The route panel SHALL have a "Clear" button that removes the current route from the map and resets the panel state.

#### Scenario: Clear removes route from map
- **WHEN** a route is displayed on the map
- **AND** user taps "Clear"
- **THEN** the route polyline SHALL be removed from the map
- **AND** the `_route_start` and `_route_end` markers SHALL be removed
- **AND** the route panel SHALL reset to its initial state

### Requirement: Turn-by-turn instruction list
After successful route calculation, the route panel SHALL display a scrollable list of turn-by-turn instructions below the control area.

#### Scenario: Instructions shown after calculation
- **WHEN** route calculation completes successfully
- **THEN** a list of `RouteInstruction` entries SHALL be displayed in the route panel
- **AND** each instruction SHALL show the primary text (e.g., "Left onto Main Street")
- **AND** each instruction SHALL show the distance to the next turn

#### Scenario: Instructions scrollable
- **WHEN** the instruction list exceeds the visible area
- **THEN** the instruction area SHALL be scrollable

### Requirement: Route panel dismiss
The route panel SHALL be dismissable by dragging down. Dismissing SHALL NOT clear the route — the route polyline and markers SHALL remain on the map.

#### Scenario: Dismiss preserves route
- **WHEN** a route is displayed on the map
- **AND** user dismisses the route panel by dragging down
- **THEN** the route polyline and markers SHALL remain visible on the map
- **AND** re-opening the route panel SHALL show the current route state
