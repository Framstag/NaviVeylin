## MODIFIED Requirements

### Requirement: Current street name shown
The system SHALL display the current street name on the free-driving view, derived from the map data at the GPS position, centered at the bottom of the view, and SHALL keep the label within the area the host guarantees visible.

#### Scenario: Street name displayed while driving
- **WHEN** the free-driving view is visible and the GPS position is on a named street
- **THEN** the view shows that street name centered at the bottom

#### Scenario: Street name updates on street change
- **WHEN** the vehicle moves onto a different named street while free driving
- **THEN** the displayed street name updates to the new street

#### Scenario: No street name when unnamed
- **WHEN** the GPS position is not on a named street while free driving
- **THEN** the view shows no street name (or an empty placeholder) and does not show stale text from a previous street

#### Scenario: Street name stays within host-visible area
- **WHEN** the free-driving view is visible and the host has not delivered a stable area
- **THEN** the street-name label is still drawn within the host's visible area, not at the raw surface bottom
