## MODIFIED Requirements

### Requirement: Route button in details sheet
The details sheet SHALL display a "Route" button that opens the route panel with the current location prefilled as the start point. This button SHALL be positioned alongside the favorite controls.

#### Scenario: Route button visible
- **WHEN** the details sheet is open
- **THEN** a "Route" button SHALL be visible in the sheet
- **AND** tapping it SHALL dismiss the details sheet and open the route panel with the location prefilled as start

#### Scenario: Route button with favorite controls
- **WHEN** the details sheet is open
- **AND** the location is not a favorite
- **THEN** both the "Add to Favorites" button and the "Route" button SHALL be visible
