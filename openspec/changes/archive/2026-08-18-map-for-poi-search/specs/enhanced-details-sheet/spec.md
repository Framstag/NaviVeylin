## ADDED Requirements

### Requirement: Current position on details mini map
The details dialog's embedded mini map SHALL also show the current location when a GPS fix is available, in addition to the object marker. The current-location marker SHALL use the same distinct style as the mini map's current-position marker.

#### Scenario: Current position shown on details mini map
- **WHEN** the details dialog is open
- **AND** a GPS fix is available
- **THEN** the mini map shows the object marker and the current-position marker

#### Scenario: Details mini map without current position
- **WHEN** the details dialog is open
- **AND** no GPS fix is available
- **THEN** the mini map shows only the object marker, without error or placeholder
