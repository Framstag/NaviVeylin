# Spec Delta

## MODIFIED Requirements

### Requirement: Fast first reroute trigger
When the vehicle leaves the planned route, the system SHALL confirm the deviation and start a reroute within 10 seconds of the first off-route detection, provided GPS accuracy is acceptable and no tunnel/no-signal guard is active. The engine's own confirmation floor SHALL be 4 seconds of continuous off-route reporting, so that the native engine's 5-second off-route hold plus that floor stays inside the 10-second bound.

#### Scenario: Consistent deviation triggers fast reroute
- **WHEN** the vehicle is off-route and the navigation engine has reported off-route state for at least 4 seconds
- **THEN** the system SHALL start a reroute calculation from the current position to the destination

#### Scenario: Transient deviation does not trigger reroute
- **WHEN** the vehicle is off-route for less than 4 seconds and then returns to the route
- **THEN** the system SHALL NOT start a reroute

#### Scenario: End-to-end latency bound on either surface
- **WHEN** the vehicle leaves the planned route while navigation is active on the phone screen or on the car screen
- **THEN** the system SHALL start a reroute calculation within 10 seconds of the off-route state beginning

### Requirement: Distance-based fast path
When the vehicle's distance from the active route polyline exceeds 30 meters, the system SHALL confirm the deviation on the first off-route report and start the reroute within 5 seconds of first detection.

#### Scenario: Large deviation triggers immediate reroute
- **WHEN** the vehicle is more than 30 meters from the active route polyline and the navigation engine reports off-route
- **THEN** the system SHALL start a reroute calculation within 5 seconds of the first off-route report

#### Scenario: Marginal deviation uses normal confirmation
- **WHEN** the vehicle is between 20 and 30 meters from the active route polyline
- **THEN** the system SHALL apply the standard confirmation (4 seconds) rather than the fast path
