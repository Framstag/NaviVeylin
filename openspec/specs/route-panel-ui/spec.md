# route-panel-ui Specification

## Purpose

Lets users plan a route between two locations with vehicle choice, view the route polyline on the map, and see turn-by-turn instructions — bridging search results to route calculation.

## Requirements

### Requirement: Route button on location details sheet
The `LocationDetailsSheet` SHALL display a "Route" button that opens the route-planning session with the current location prefilled as the start point.

#### Scenario: Route button visible in details sheet
- **WHEN** the location details sheet is open
- **THEN** a "Route" button SHALL be visible alongside the existing favorite controls
- **AND** tapping it SHALL dismiss the details sheet and open the route-planning session

#### Scenario: Route button prefills destination, start = current location
- **WHEN** user taps "Route" on a details sheet for location "Museum Island, Berlin"
- **AND** GPS location is available
- **THEN** the route-planning session SHALL open with start set to current GPS location
- **AND** destination set to "Museum Island, Berlin" with its lat/lon coordinates

#### Scenario: Route button prefills destination only when GPS unavailable
- **WHEN** user taps "Route" on a details sheet
- **AND** GPS location is not available
- **THEN** the route-planning session SHALL open with destination set to the details sheet location
- **AND** start field SHALL show placeholder text

### Requirement: Route panel with start and destination fields

The route-planning session SHALL present two location fields: start and destination. Each field SHALL be an editable text field that triggers search-on-type as the user types. Each field SHALL support three input methods: search (via text input), favorite picker (via "Select Favorite" list entry), and current location (via "Current Location" list entry). When a location is selected, the field SHALL display the location label and SHALL be read-only until cleared.

#### Scenario: Route panel opens with start prefilled from details sheet

- **WHEN** the route-planning session opens from a details sheet "Route" button
- **THEN** the start field SHALL show "Current Location" (if GPS available)
- **AND** the destination field SHALL show the location label from the details sheet

#### Scenario: Route panel opens empty

- **WHEN** the route-planning session opens from the map screen (not from a details sheet)
- **THEN** both start and destination fields SHALL show placeholder text

#### Scenario: Start field shows search results while typing

- **WHEN** user taps the start field and types a query
- **THEN** the search panel SHALL open inline with search-as-you-type behavior
- **AND** the result entries SHALL be location search results only
- **AND** "Current Location" and "Select Favorite" entries SHALL NOT appear while text is entered
- **AND** selecting a result SHALL set it as the start location and close the results

#### Scenario: Destination field shows search results while typing

- **WHEN** user taps the destination field and types a query
- **THEN** the search panel SHALL open inline with search-as-you-type behavior
- **AND** the result entries SHALL be location search results only
- **AND** "Current Location" and "Select Favorite" entries SHALL NOT appear while text is entered
- **AND** selecting a result SHALL set it as the destination location and close the results

#### Scenario: Convenience entries shown for empty query

- **WHEN** user taps the start or destination field
- **AND** the query is empty
- **THEN** the "Current Location" entry SHALL appear (if GPS available)
- **AND** the "Select Favorite" entry SHALL appear
- **AND** clearing a typed query SHALL immediately restore both entries

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

#### Scenario: Fields stay reachable while the overlay is collapsed

- **WHEN** the session overlay is collapsed to its compact anchor
- **THEN** both location fields SHALL remain visible
- **AND** tapping a field SHALL expand the overlay so its results are readable

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
After successful route calculation, the route panel SHALL show the route summary inline, below the calculate button. The route panel SHALL remain open and SHALL NOT be dismissed. The route panel SHALL NOT show the instruction list separately — the route summary component contains the instructions.

#### Scenario: Route summary dialog triggered after calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary SHALL be shown inline in the route panel below the calculate button
- **AND** the route panel SHALL remain open
- **AND** the instruction list SHALL NOT be shown separately in the route panel

#### Scenario: Route panel re-opens on summary dismiss
- **WHEN** the route summary dialog is dismissed
- **THEN** the route panel SHALL re-open with all previous state intact (start, destination, vehicle, route)

#### Scenario: Instructions scrollable in summary dialog
- **WHEN** the route summary component is displayed in the route panel
- **THEN** the instruction list SHALL be scrollable within the summary component

### Requirement: Start Navigation button in route panel
When a route is calculated and navigation is not active, the session SHALL display a "Start Navigation" button below the calculate button and above the route summary component.

#### Scenario: Start Navigation button visible
- **WHEN** a route is calculated
- **AND** navigation is not active
- **THEN** a "Start Navigation" button SHALL be visible in the session below the calculate button
- **AND** the button SHALL be positioned above the route summary component
- **AND** tapping it SHALL start navigation
- **AND** the session SHALL end

#### Scenario: Start Navigation hidden during active nav
- **WHEN** navigation is active
- **THEN** the "Start Navigation" button SHALL be replaced with a "Stop Navigation" button
- **AND** tapping it SHALL stop navigation

### Requirement: Swap button position
The swap button SHALL be positioned to the right of the start and destination fields, vertically centered between them. It SHALL NOT be placed centered between the two fields.

#### Scenario: Swap button right of the fields
- **WHEN** the route panel is open
- **THEN** the swap button SHALL be visible to the right of the start and destination fields
- **AND** the button SHALL be vertically centered between the two fields

### Requirement: Stop navigation hides route from map
When navigation is stopped, the route polyline and the `_route_start`/`_route_end` markers SHALL be removed from the map once the session's stopped-state grace period ends, while the route data (start, destination, vehicle, route summary, steps) SHALL be preserved during that grace period so navigation can be restarted without recalculating.

#### Scenario: Stop removes route from map
- **WHEN** navigation is active
- **AND** the user stops navigation
- **AND** the grace period elapses without a restart
- **THEN** the route polyline SHALL be removed from the map
- **AND** the `_route_start` and `_route_end` markers SHALL be removed
- **AND** the session SHALL end

#### Scenario: Route panel shows Start Navigation after stop
- **WHEN** navigation has been stopped and the session is in its stopped state
- **THEN** the route summary SHALL still be shown
- **AND** a Restart action SHALL be available that starts navigation again without recalculating
- **AND** an End action SHALL be available that ends the session immediately

#### Scenario: Restarting navigation redraws the route
- **WHEN** the user stops navigation during a session
- **AND** then starts navigation again on the same route within the grace period
- **THEN** the route polyline and markers SHALL be rendered on the map again
- **AND** the route SHALL NOT be recalculated

#### Scenario: Route not redrawn after stop on screen re-entry
- **WHEN** navigation has been stopped and the session has ended
- **AND** the user navigates away from the map screen and back (screen recomposition)
- **THEN** the route polyline SHALL NOT reappear on the map

### Requirement: Session overlay dismissal ends the session

The session overlay SHALL be dismissable, and dismissing it SHALL end the session: the route polyline and the start/target markers SHALL be removed from the map, the session state SHALL be reset, and the overlay SHALL be gone. Collapsing the overlay to the compact anchor SHALL NOT be a dismissal.

#### Scenario: Dismiss removes route

- **WHEN** a route is displayed in the session
- **AND** the user dismisses the overlay without starting navigation
- **THEN** the route polyline and the start/target markers SHALL be removed from the map
- **AND** re-opening the session SHALL show the empty state

#### Scenario: Collapsing is not dismissing

- **WHEN** a route is displayed in the session
- **AND** the user collapses the overlay to the compact anchor
- **THEN** the route polyline and markers SHALL remain visible
- **AND** the session state SHALL be preserved

#### Scenario: The close control dismisses without collapsing

- **WHEN** a route is displayed in the session and the card is in max
- **AND** the user activates the card's close control
- **THEN** the overlay SHALL be dismissed and the session SHALL end
- **AND** the route SHALL be removed from the map

#### Scenario: A dismissed overlay stays dismissed

- **WHEN** the user dismisses the overlay
- **THEN** no part of the session's surface (card, strip or affordance) SHALL remain visible
- **AND** re-opening the session SHALL open it in editing, not in a route-ready state
