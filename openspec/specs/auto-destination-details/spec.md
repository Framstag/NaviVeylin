# Auto Destination Details (auto-destination-details)

## Purpose

Shows a destination details screen on the Android Auto car display before navigation starts — reachable from search results, POI results, and map taps — and carries the destination identity into the active navigation context.

## Requirements

### Requirement: Details screen shown before navigation starts
The system SHALL show a details screen when the user selects a destination from a search result, a POI result, or a map tap, before navigation starts. The details screen SHALL display the destination's name or address, its coordinates, and its object description when available, overlaid on a map preview that shows the destination position. The screen SHALL show a title derived from the destination identity and SHALL present every attribute as a labeled row.

#### Scenario: Search result opens details screen
- **WHEN** the user taps a search result on the car screen
- **THEN** the details screen SHALL open for that location
- **AND** navigation SHALL NOT start yet

#### Scenario: POI result opens details screen
- **WHEN** the user taps a POI result on the car screen
- **THEN** the details screen SHALL open for that POI
- **AND** navigation SHALL NOT start yet

#### Scenario: Map tap opens details screen
- **WHEN** the user taps a location on the car map
- **AND** the location has exactly one candidate object
- **THEN** the details screen SHALL open directly for that object

#### Scenario: Details screen shows address and coordinates
- **WHEN** the details screen is open
- **AND** a reverse-geocoded address is available
- **THEN** the screen SHALL show the address and the coordinates formatted to 5 decimal places

#### Scenario: Details screen shows object description
- **WHEN** the details screen is open
- **AND** an object description is available
- **THEN** the screen SHALL show the description entries (label/value pairs)

#### Scenario: Details screen without description
- **WHEN** the details screen is open
- **AND** no object description is available
- **THEN** the screen SHALL show the address or coordinates without a description section and without an error

#### Scenario: Details screen shows map preview
- **WHEN** the details screen is open
- **THEN** the screen SHALL show a map preview of the destination's surroundings
- **AND** the map preview SHALL show a destination marker at the destination position
- **AND** the destination SHALL be centered in the visible map area (the part not covered by the details panel)

#### Scenario: Details screen shows favorites
- **WHEN** the details screen is open
- **AND** favorites exist
- **THEN** the map preview SHALL show the favorite markers

#### Scenario: Details screen shows current position
- **WHEN** the details screen is open
- **AND** a GPS fix is available
- **THEN** the map preview SHALL show a current-position marker
- **AND** the preview SHALL be zoomed so that both the destination and the current position are visible

#### Scenario: Details screen without current position
- **WHEN** the details screen is open
- **AND** no GPS fix is available
- **THEN** the map preview SHALL show only the destination marker, centered on the destination, without error or placeholder

#### Scenario: Details screen shows labeled attribute rows
- **WHEN** the details screen is open
- **THEN** every attribute row SHALL show a label with its value
- **AND** no attribute row SHALL be shown without a label

#### Scenario: Details screen title shows destination name
- **WHEN** the details screen is open
- **AND** the object description contains a name
- **THEN** the screen title SHALL show the object's name

#### Scenario: Details screen title falls back to address
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** a reverse-geocoded address is available
- **THEN** the screen title SHALL show the address

#### Scenario: Details screen title falls back to label
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** no reverse-geocoded address is available
- **AND** the caller provided a search label
- **THEN** the screen title SHALL show the search label

#### Scenario: Details screen title falls back to generic
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** no reverse-geocoded address is available
- **AND** the caller provided no search label
- **THEN** the screen title SHALL show a generic location title

### Requirement: Navigation starts from the details screen
The system SHALL start navigation only from the details screen's "Navigate here" action. The details screen SHALL offer a second "Show" action that displays the destination on the browse map without starting navigation.

#### Scenario: Navigate here starts navigation
- **WHEN** the user taps "Navigate here" on the details screen
- **THEN** the system SHALL start navigation to the displayed destination
- **AND** the details screen SHALL be replaced by the navigation template

#### Scenario: Back from details screen does not navigate
- **WHEN** the user goes back from the details screen without tapping "Navigate here"
- **THEN** the system SHALL NOT start navigation
- **AND** the previous screen SHALL be shown again

#### Scenario: Show action displays destination on map
- **WHEN** the user taps "Show" on the details screen
- **THEN** the details screen SHALL close
- **AND** the browse map SHALL be shown centered on the destination
- **AND** navigation SHALL NOT start

### Requirement: Destination identity retained during navigation
The system SHALL retain the destination identity (name or address when known, otherwise coordinates) after navigation starts, and SHALL display it on the navigation template during active navigation.

#### Scenario: Destination name shown during navigation
- **WHEN** navigation is active
- **AND** the destination has a known name or address
- **THEN** the navigation template SHALL display the destination name or address alongside the travel estimate

#### Scenario: Unnamed destination shows marker without label
- **WHEN** navigation is active
- **AND** the destination has no known name or address
- **THEN** the navigation template SHALL show the destination marker at the destination position
- **AND** no name label SHALL be drawn (the marker position itself carries the coordinates)

#### Scenario: Destination marker on the navigation map
- **WHEN** navigation is active
- **THEN** the navigation template SHALL show a destination marker at the destination position
