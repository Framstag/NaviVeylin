## RENAMED Requirements

- FROM: `### Requirement: Draggable bottom sheet`
- TO: `### Requirement: Full-screen details dialog`

## MODIFIED Requirements

### Requirement: Full-screen details dialog
The details view SHALL be implemented as a full-screen dialog that covers the entire available screen. It SHALL close on the system back gesture/button (including predictive back on API 33+), returning to the previous view. The dialog SHALL display the object name, an interactive mini map of the object's surroundings, the structured description list, and the action buttons. No map content SHALL remain visible behind the dialog.

#### Scenario: Dialog is full screen
- **WHEN** the details view is open
- **THEN** it SHALL fill the entire available screen
- **AND** the underlying map SHALL NOT be visible

#### Scenario: Sheet can be dismissed by dragging down
- **WHEN** the details view is open
- **AND** the user performs the system back gesture (edge swipe) or presses the back button
- **THEN** the dialog SHALL dismiss
- **AND** the map screen SHALL be shown again

#### Scenario: Predictive back on API 33+
- **WHEN** the details view is open on Android 13 or newer
- **AND** the user starts the back gesture
- **THEN** the system SHALL show the predictive back preview animation
- **AND** completing the gesture SHALL dismiss the dialog

#### Scenario: Sheet opens from search result selection
- **WHEN** user taps a search result
- **THEN** the details dialog SHALL open with the selected location's information
- **AND** the search panel SHALL close

#### Scenario: Sheet opens from long-press
- **WHEN** user long-presses on the map
- **AND** a nearby object with description data is found
- **THEN** the details dialog SHALL open with the object's structured description

#### Scenario: Dialog embeds an interactive mini map
- **WHEN** the details dialog is open
- **THEN** an interactive mini map of the object's surroundings SHALL be displayed below the object name
- **AND** the mini map SHALL show a marker at the object's position

### Requirement: Structured description display
The details dialog SHALL render `ObjectDescription` entries grouped by section. Each section SHALL display as a header (e.g., "General", "Location", "Contact") followed by its label/value rows. Subsections SHALL display as sub-headers indented under their parent section. Repeated subsections (with index) SHALL show the index.

#### Scenario: Sections rendered as headers with label/value rows
- **WHEN** the description contains a "General" section with entries for type and name
- **THEN** the dialog SHALL display "General" as a section header
- **AND** show "Type: restaurant" and "Name: Mario's" as label/value rows below

#### Scenario: Subsections rendered indented
- **WHEN** the description contains a "Location" section with an "Admin Level" subsection
- **THEN** the dialog SHALL display "Location" as a section header
- **AND** "Admin Level" as a subsection header indented below
- **AND** show the admin level label/value rows under the subsection

#### Scenario: Empty description shows no sections
- **WHEN** the `ObjectDescription` has zero entries
- **THEN** the dialog SHALL display only the object name, the mini map, the coordinates, and the favorite controls
- **AND** no section headers SHALL be shown

## ADDED Requirements

### Requirement: Area as list entry
The details dialog SHALL display the object's area as a structured list entry with a label and value, alongside the other description entries. The area SHALL be the object's admin region hierarchy when available, falling back to the description's admin-level "IsIn" value (covers results without a hierarchy, e.g. POI search and long-press).

#### Scenario: Admin region entry shown
- **WHEN** the details dialog is open
- **AND** the object has an admin region hierarchy
- **THEN** the dialog SHALL display an "Area" list entry with the hierarchy as its value (e.g., "Eving/Dortmund/Dortmund")

#### Scenario: Area falls back to description IsIn
- **WHEN** the details dialog is open
- **AND** the object has no admin region hierarchy
- **AND** the object's description contains an admin-level "IsIn" value
- **THEN** the dialog SHALL display an "Area" list entry with the IsIn value

#### Scenario: No admin region entry
- **WHEN** the details dialog is open
- **AND** the object has neither an admin region hierarchy nor a description IsIn value
- **THEN** no area list entry SHALL be shown

### Requirement: Title shows name or address
The details dialog SHALL show the object's name as the title when the object has a name. When the object has no name but has an address, the address SHALL be shown as the title instead. Otherwise the search label SHALL be shown.

#### Scenario: Title shows object name
- **WHEN** the details dialog is open
- **AND** the object has a name
- **THEN** the title SHALL display the object's name

#### Scenario: Title falls back to address
- **WHEN** the details dialog is open
- **AND** the object has no name
- **AND** the object has an address
- **THEN** the title SHALL display the object's address

#### Scenario: Title falls back to label
- **WHEN** the details dialog is open
- **AND** the object has neither a name nor an address
- **THEN** the title SHALL display the search label

### Requirement: Address entry when house number present
The details dialog SHALL display the object's address as a list entry when the object has a house number. The address SHALL combine the street and the house number from the object description's address entries ("Location" = street, "Address" = house number) with the postal code and city; when the description lacks a street, the street SHALL be taken from a reverse lookup of the location index at the object's position (which also supplies the admin region and postal area). The standalone street row SHALL NOT be duplicated when the combined address is shown.

#### Scenario: Address shown for object with house number
- **WHEN** the details dialog is open
- **AND** the object's description contains an address with a house number (e.g., "12")
- **THEN** the dialog SHALL display an "Address" list entry with the house number as its value

#### Scenario: Full address combines street, house number, postal code and city
- **WHEN** the details dialog is open
- **AND** the object's description contains both a street (e.g., "Hauptstraße") and a house number (e.g., "12")
- **AND** the object has a postal code (e.g., "44339") and city (e.g., "Dortmund")
- **THEN** the dialog SHALL display an "Address" list entry with the combined value (e.g., "Hauptstraße 12, 44339 Dortmund")
- **AND** the standalone street row SHALL NOT be shown

#### Scenario: Address includes city without street
- **WHEN** the details dialog is open
- **AND** the object's description contains a house number (e.g., "12") but no street
- **AND** the object has a city
- **THEN** the dialog SHALL display an "Address" list entry with the house number and city (e.g., "12, Dortmund")

#### Scenario: No address entry without house number
- **WHEN** the details dialog is open
- **AND** the object's description contains no house number
- **THEN** no address list entry SHALL be shown
