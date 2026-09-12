## MODIFIED Requirements

### Requirement: Current street name shown during navigation

The system SHALL display the name and ref of the current street on the navigation display while navigating, taken from the route's way at the current point (not an area search), updating when the vehicle changes roads. The label SHALL remain fully visible — not covered by host-rendered UI such as the travel-estimate (ETA) card.

#### Scenario: Street name displayed while driving

- **WHEN** navigation is active and current-road data is available
- **THEN** the current street name is drawn on the map surface

#### Scenario: Ref shown with the street name

- **WHEN** the current street has a ref tag
- **THEN** the label shows the ref together with the name (e.g. "B 1 Hauptstrasse")

#### Scenario: Street name from the route, not an area search

- **WHEN** navigation is active and the vehicle is on the planned route
- **THEN** the street name comes from the route's way at the current point
- **AND** no reverse-geocode or description lookup is performed at the GPS position

#### Scenario: Street name updates on street change

- **WHEN** the vehicle enters a new road during navigation
- **THEN** the displayed street name updates to the new road's name

#### Scenario: No street name when unnamed

- **WHEN** navigation is active but no street name is available
- **THEN** no street-name label is drawn

#### Scenario: Street name not covered by host ETA card

- **WHEN** navigation is active, a travel estimate is shown by the host, and the current street name is displayed
- **THEN** the street-name label is drawn entirely above the host's ETA card region, within the area the host guarantees visible

#### Scenario: Street name stays clear when host geometry is unknown

- **WHEN** navigation is active and the host has not delivered a stable area
- **THEN** the street-name label is still drawn within the host's visible area, not at the raw surface bottom

#### Scenario: Street name in host ETA card when map area is not safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers no stable or visible area that clears the surface bottom (the ETA card may cover the map label)
- **THEN** the street name is rendered inside the host's travel-estimate card via `setTripText`, and no street-name label is drawn on the map surface

#### Scenario: Street name on map when host area is safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers a stable or visible area that clears the surface bottom
- **THEN** the street-name label is drawn on the map surface and the travel-estimate card carries no trip text
