## MODIFIED Requirements

### Requirement: Full-screen details dialog
The details view SHALL be implemented as a full-screen dialog that covers the entire available screen. It SHALL close on the system back gesture/button (including predictive back on API 33+), returning to the previous view. The dialog SHALL display the object name, an interactive mini map of the object's surroundings, the structured description list, and the action buttons. No map content SHALL remain visible behind the dialog. The dialog SHALL always offer a "Show on map" action that closes the dialog and centers the map on the object, regardless of how the dialog was opened.

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

#### Scenario: Show on map always available
- **WHEN** the details dialog is open
- **AND** the dialog was opened from a long-press or a search result
- **THEN** a "Show on map" action SHALL be visible
- **AND** activating it SHALL close the dialog and center the map on the object

### Requirement: Title shows name or address
The details dialog SHALL show the object's name as the title when the object has a name. When the object has no name but has an address, the address SHALL be shown as the title instead. Otherwise the search label SHALL be shown, unless the label is a coordinate pair, in which case a generic "Location" title SHALL be shown.

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
- **AND** the search label is not a coordinate pair
- **THEN** the title SHALL display the search label

#### Scenario: Coordinate label falls back to generic title
- **WHEN** the details dialog is open
- **AND** the object has neither a name nor an address
- **AND** the search label is a coordinate pair (e.g. "51.50000, 7.40000")
- **THEN** the title SHALL display a generic "Location" title

## RENAMED Requirements

- FROM: `### Requirement: Route button in details sheet`
- TO: `### Requirement: Navigate to button in details sheet`

## MODIFIED Requirements

### Requirement: Navigate to button in details sheet
The details sheet SHALL display a "Navigate to" button that opens the route panel with the current location prefilled as the start point. This button SHALL be positioned alongside the favorite controls.

#### Scenario: Route button visible
- **WHEN** the details sheet is open
- **THEN** a "Navigate to" button SHALL be visible in the sheet
- **AND** tapping it SHALL dismiss the details sheet and open the route panel with the location prefilled as start

#### Scenario: Route button with favorite controls
- **WHEN** the details sheet is open
- **AND** the location is not a favorite
- **THEN** both the "Add to Favorites" button and the "Navigate to" button SHALL be visible
