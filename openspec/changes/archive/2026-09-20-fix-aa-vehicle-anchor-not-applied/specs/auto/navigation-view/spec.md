# Spec Delta

## ADDED Requirements

### Requirement: Routing anchor applies when the setting changes during navigation
The navigation follow-mode map SHALL re-frame at the routing anchor when the setting changes during active navigation: the change SHALL apply without restarting the navigation screen and without waiting for the next maneuver change.

#### Scenario: Anchor change applies during navigation
- **WHEN** the driver changes the routing anchor in the settings dialog during an active navigation session
- **AND** returns to the navigation map
- **THEN** follow mode re-frames the map at the new routing anchor

#### Scenario: Anchor applies on resume without a maneuver change
- **GIVEN** navigation is active and no maneuver change is pending
- **WHEN** the driver returns from the settings dialog with a new routing anchor
- **THEN** the map re-frames at the new anchor without requiring the next instruction change to trigger it
