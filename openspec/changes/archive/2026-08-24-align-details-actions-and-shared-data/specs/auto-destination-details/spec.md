## ADDED Requirements

### Requirement: Favorite management on details screen
The details screen SHALL offer favorite management for the displayed destination, matching the phone details dialog: an "Add to Favorites" action when the destination is not yet a favorite, and a "Remove from Favorites" action when it is. The screen SHALL reflect the current favorite state of the destination.

#### Scenario: Save destination to favorites
- **WHEN** the details screen is open
- **AND** the destination is not a favorite
- **THEN** the screen SHALL show an "Add to Favorites" action
- **AND** activating it SHALL add the destination to the favorites

#### Scenario: Remove destination from favorites
- **WHEN** the details screen is open
- **AND** the destination is already a favorite
- **THEN** the screen SHALL show a "Remove from Favorites" action
- **AND** activating it SHALL remove the destination from the favorites

#### Scenario: Favorite state shown on details screen
- **WHEN** the details screen is open
- **AND** the destination's favorite state changes
- **THEN** the screen SHALL update the shown action accordingly (save ↔ remove)

## MODIFIED Requirements

### Requirement: Details screen shown before navigation starts
The system SHALL show a details screen when the user selects a destination from a search result, a POI result, or a map tap, before navigation starts. The details screen SHALL display the destination's name or address, its coordinates, and its object description when available, overlaid on a map preview that shows the destination position. The screen SHALL show a title derived from the destination identity and SHALL present every attribute as a labeled row.

The address, area, and title SHALL be resolved with the same composition rules as the phone details dialog (which is the lead view): the address SHALL combine street, house number, postal code, and city when available; the area SHALL fall back through the admin region hierarchy, the reverse-lookup region, and the description's admin-level "IsIn" value; and the title SHALL fall back from the object name (description `General/Name`, else the caller-provided name), to the full address, to an address-like search label, to a generic title. The screen SHALL show every attribute returned by the object description API (e.g. opening hours, phone, website) as a labeled row, and SHALL NOT drop attributes: the description entries SHALL be listed completely, in native order, after the coordinates, address, and area rows, and the host SHALL page the list when it exceeds one page. A standalone street row SHALL NOT be shown when the combined address row is shown. Description entries with an empty label or an empty value SHALL be omitted (they carry no information).

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

#### Scenario: Address row combines street, house number, postal code and city
- **WHEN** the details screen is open
- **AND** the object has a street (e.g., "Hauptstraße"), a house number (e.g., "12"), a postal code (e.g., "44339"), and a city (e.g., "Dortmund")
- **THEN** the screen SHALL show an "Address" row with the combined value (e.g., "Hauptstraße 12, 44339 Dortmund")

#### Scenario: Address row with street and house number only
- **WHEN** the details screen is open
- **AND** the object has a street and a house number
- **AND** no postal code or city is available
- **THEN** the screen SHALL show an "Address" row with street and house number (e.g., "Hauptstraße 12")

#### Scenario: Address falls back to reverse lookup street
- **WHEN** the details screen is open
- **AND** the description lacks a street
- **AND** a reverse lookup at the object's position returns a street
- **THEN** the screen SHALL show an "Address" row combining the reverse-lookup street with the house number

#### Scenario: Address falls back to address-like search label
- **WHEN** the details screen is open
- **AND** the object has no description street, no reverse-lookup street, and no house number
- **AND** the caller provided a search label that contains a digit (e.g., "Hauptstraße 12")
- **THEN** the screen SHALL show the search label as the address
- **AND** the label SHALL NOT be shown as the title in its place

#### Scenario: Standalone street row not duplicated
- **WHEN** the details screen shows a combined "Address" row
- **THEN** no separate row for the same street SHALL be shown

#### Scenario: Details screen shows object description
- **WHEN** the details screen is open
- **AND** an object description is available
- **THEN** the screen SHALL show all description entries (label/value pairs)

#### Scenario: All description attributes shown
- **WHEN** the details screen is open
- **AND** the object description contains attributes such as opening hours, phone, or website
- **THEN** the screen SHALL show every such attribute as a labeled row
- **AND** no attribute SHALL be omitted due to a fixed row limit

#### Scenario: Opening hours shown on the details screen
- **WHEN** the details screen is open
- **AND** the object description contains an opening-hours entry
- **THEN** the screen SHALL show the opening-hours label with its value

#### Scenario: Long description paged by host
- **WHEN** the details screen is open
- **AND** the attribute list exceeds one page
- **THEN** the host SHALL page through the remaining attributes
- **AND** every attribute SHALL be reachable

#### Scenario: Description entries keep native order
- **WHEN** the details screen is open
- **AND** an object description is available
- **THEN** the description entries SHALL appear after the coordinates, address, and area rows
- **AND** the entries SHALL appear in native description order

#### Scenario: Empty description entries are dropped
- **WHEN** the details screen is open
- **AND** the description contains an entry with an empty label or an empty value
- **THEN** the screen SHALL NOT show that entry as a row

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

#### Scenario: Area row uses admin region hierarchy
- **WHEN** the details screen is open
- **AND** the object has an admin region hierarchy
- **THEN** the "Area" row SHALL show the hierarchy (e.g., "Eving/Dortmund/Dortmund")

#### Scenario: Area row falls back to reverse lookup region
- **WHEN** the details screen is open
- **AND** the object has no admin region hierarchy
- **AND** a reverse lookup at the object location returns an admin region
- **THEN** the "Area" row SHALL show the reverse-lookup region

#### Scenario: Area row falls back to description IsIn
- **WHEN** the details screen is open
- **AND** the object has no admin region hierarchy and no reverse-lookup region
- **AND** the object description contains an admin-level "IsIn" value
- **THEN** the "Area" row SHALL show the IsIn value

#### Scenario: No area row without area data
- **WHEN** the details screen is open
- **AND** the object has no admin region hierarchy, no reverse-lookup region, no description IsIn value, and no postal area
- **THEN** no "Area" row SHALL be shown

#### Scenario: Details screen title shows destination name
- **WHEN** the details screen is open
- **AND** the object description contains a name
- **THEN** the screen title SHALL show the object's name

#### Scenario: Title shows caller-provided name before address
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** the caller provided a name (e.g. a POI name)
- **AND** a resolved address is available
- **THEN** the screen title SHALL show the caller-provided name
- **AND** the screen SHALL NOT use the address as the title

#### Scenario: Details screen title falls back to full address
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** a resolved address is available
- **THEN** the screen title SHALL show the full address including postal code and city when available

#### Scenario: Details screen title falls back to address
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** a resolved address is available
- **THEN** the screen title SHALL show the resolved address

#### Scenario: Details screen title falls back to label
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** no resolved address is available
- **AND** the caller provided a search label
- **THEN** the screen title SHALL show the search label

#### Scenario: Details screen title falls back to address-like label
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** no resolved address is available
- **AND** the caller provided a search label that contains a digit (e.g., "Hauptstraße 12")
- **THEN** the screen title SHALL show the search label

#### Scenario: Details screen title falls back to generic
- **WHEN** the details screen is open
- **AND** the object description contains no name
- **AND** no resolved address is available
- **AND** the caller provided no address-like search label
- **THEN** the screen title SHALL show a generic location title


## MODIFIED Requirements

### Requirement: Navigation starts from the details screen
The system SHALL start navigation only from the details screen's "Navigate to" action. The details screen SHALL offer a second "Show" action that displays the destination on the browse map without starting navigation.

#### Scenario: Navigate here starts navigation
- **WHEN** the user taps "Navigate to" on the details screen
- **THEN** the system SHALL start navigation to the displayed destination
- **AND** the details screen SHALL be replaced by the navigation template

#### Scenario: Back from details screen does not navigate
- **WHEN** the user goes back from the details screen without tapping "Navigate to"
- **THEN** the system SHALL NOT start navigation
- **AND** the previous screen SHALL be shown again

#### Scenario: Show action displays destination on map
- **WHEN** the user taps "Show" on the details screen
- **THEN** the details screen SHALL close
- **AND** the browse map SHALL be shown centered on the destination
- **AND** navigation SHALL NOT start

### Requirement: Details actions visually marked

The "Navigate to" and "Show" actions on the details screen SHALL be visually marked as actions (e.g. a leading symbol) so they are distinguishable from the labeled attribute rows, and SHALL be positioned before the attribute rows.

#### Scenario: Navigate here marked as action
- **WHEN** the details screen shows the "Navigate to" action
- **THEN** the action SHALL be visually marked (e.g. a leading symbol) distinct from the attribute rows

#### Scenario: Show marked as action
- **WHEN** the details screen shows the "Show" action
- **THEN** the action SHALL be visually marked (e.g. a leading symbol) distinct from the attribute rows
