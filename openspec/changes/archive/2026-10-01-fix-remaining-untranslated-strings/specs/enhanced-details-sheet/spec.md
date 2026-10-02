# Spec Delta — enhanced-details-sheet

## MODIFIED Requirements

### Requirement: Title shows name or address
The details dialog SHALL show the object's name as the title when the object has a name. When the object has no name but has an address, the address SHALL be shown as the title instead. Otherwise the search label SHALL be shown, unless the label is a coordinate pair, in which case a generic location title SHALL be shown, taken from a localized string resource supplied by the surface that renders the dialog (so it renders in the device language and matches the title the Android Auto details screen shows for the same destination).

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
- **THEN** the title SHALL display the surface's generic location title

#### Scenario: Generic title renders in the device language
- **WHEN** the device locale is German
- **AND** the details dialog is open on a coordinate label with no name and no address
- **THEN** the title SHALL render the German generic location title and SHALL NOT render the English word "Location"

#### Scenario: Generic title matches on both surfaces
- **WHEN** the device locale is German
- **AND** the same coordinate destination is opened on the phone details dialog and on the Android Auto details screen
- **THEN** both titles SHALL show the same German generic location title
