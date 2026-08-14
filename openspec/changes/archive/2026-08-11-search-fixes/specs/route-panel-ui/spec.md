## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Swap button position
The swap button SHALL be positioned to the right of the start and destination fields, vertically centered between them. It SHALL NOT be placed centered between the two fields.

#### Scenario: Swap button right of the fields
- **WHEN** the route panel is open
- **THEN** the swap button SHALL be visible to the right of the start and destination fields
- **AND** the button SHALL be vertically centered between the two fields
