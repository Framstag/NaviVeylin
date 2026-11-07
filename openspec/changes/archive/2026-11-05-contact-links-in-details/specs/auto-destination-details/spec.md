## ADDED Requirements

### Requirement: Contact rows on the car stay non-actionable
The car details screen SHALL list `Contact/Phone` and `Contact/Website` values as plain text rows and SHALL NOT start any intent for them, because a Car App Library template cannot start an activity outside the car app. The rows SHALL carry the same label key and value text as the phone dialog, so only the interaction and the phone's `label:` colon presentation differ between the surfaces.

#### Scenario: A contact row carries its value and starts no intent
- **WHEN** the car details screen lists an object whose description carries a `Contact` entry labelled `Website` with the value `https://cafe.de`
- **THEN** the row SHALL show the label `Website` and the value `https://cafe.de`
- **Case**: `DetailsScreenTest#contactRowsCarryTheirValueAndStartNoIntent`
