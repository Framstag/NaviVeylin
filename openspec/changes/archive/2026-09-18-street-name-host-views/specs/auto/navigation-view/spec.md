## MODIFIED Requirements

### Requirement: Current street name shown during navigation

The system SHALL display the name and ref of the current street on the navigation display while navigating, taken from the route's way at the current point (not an area search) when on route, updating when the vehicle changes roads. The street name SHALL be rendered as an element of the host's routing status view — inside the travel-estimate (ETA) card via `TravelEstimate.setTripText` — and SHALL never be drawn on the map surface, regardless of the host's delivered stable/visible areas. When navigation is active but no road info is in state (off route), the street name SHALL come from the throttled bearing-aware road lookup at the GPS position.

#### Scenario: Street name displayed while driving

- **WHEN** navigation is active and current-road data is available
- **THEN** the host travel-estimate card shows the street name via `setTripText`

#### Scenario: Ref shown with the street name

- **WHEN** the current street has a ref tag
- **THEN** the ETA card shows the ref together with the name (e.g. "B 1 Hauptstrasse")

#### Scenario: Street name from the route, not an area search

- **WHEN** navigation is active and the vehicle is on the planned route
- **THEN** the street name comes from the route's way at the current point
- **AND** no reverse-geocode or description lookup is performed at the GPS position

#### Scenario: Street name updates on street change

- **WHEN** the vehicle enters a new road during navigation
- **THEN** the ETA-card trip text updates to the new road's name

#### Scenario: No street name when unnamed

- **WHEN** navigation is active but no street name is available
- **THEN** no trip text is set on the travel estimate

#### Scenario: Street name not covered by host ETA card

- **WHEN** navigation is active, a travel estimate is shown by the host, and the current street name is displayed
- **THEN** the street name is an element of the travel-estimate card itself, so no host-rendered UI can cover it

#### Scenario: Street name stays clear when host geometry is unknown

- **WHEN** navigation is active and the host has not delivered a stable area
- **THEN** the street name is still shown in the travel-estimate card, positioned by the host, with no dependence on the surface-rect geometry

#### Scenario: Street name in host ETA card when map area is not safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers no stable or visible area that clears the surface bottom
- **THEN** the street name is rendered inside the host's travel-estimate card via `setTripText`, and no street-name label is drawn on the map surface

#### Scenario: Street name on map when host area is safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers a stable or visible area that clears the surface bottom
- **THEN** the street name is still rendered in the travel-estimate card via `setTripText` and never on the map surface (a bottom-clear area does not move the street name off the card)

#### Scenario: No street-name label on the map surface

- **WHEN** navigation is active
- **THEN** no street-name text is drawn on the map surface (the map surface carries only the compass rose, speed badge and attribution)

#### Scenario: Off-route street name from GPS lookup

- **WHEN** navigation is active and the vehicle is off the planned route (no road info in the navigation state)
- **THEN** the street name comes from the throttled bearing-aware road lookup at the GPS position and updates the ETA-card trip text
