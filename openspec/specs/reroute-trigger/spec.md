# Reroute Trigger Specification

## Purpose

Defines when the system triggers a reroute after the vehicle leaves the planned route: fast first trigger, a distance-based fast path for clear deviations, a post-reroute cooldown to prevent cascades, and preserved noise guards.

## Requirements

### Requirement: Fast first reroute trigger
When the vehicle leaves the planned route, the system SHALL confirm the deviation and start a reroute within 10 seconds of the first off-route detection, provided GPS accuracy is acceptable and no tunnel/no-signal guard is active.

#### Scenario: Consistent deviation triggers fast reroute
- **WHEN** the vehicle is off-route and the navigation engine reports off-route state for at least 10 seconds
- **THEN** the system SHALL start a reroute calculation from the current position to the destination

#### Scenario: Transient deviation does not trigger reroute
- **WHEN** the vehicle is off-route for less than 10 seconds and then returns to the route
- **THEN** the system SHALL NOT start a reroute

### Requirement: Distance-based fast path
When the vehicle's distance from the active route polyline exceeds 50 meters, the system SHALL confirm the deviation on the first off-route report and start the reroute within 5 seconds of first detection.

#### Scenario: Large deviation triggers immediate reroute
- **WHEN** the vehicle is more than 50 meters from the active route polyline and the navigation engine reports off-route
- **THEN** the system SHALL start a reroute calculation within 5 seconds of the first off-route report

#### Scenario: Marginal deviation uses normal confirmation
- **WHEN** the vehicle is between 20 and 50 meters from the active route polyline
- **THEN** the system SHALL apply the standard confirmation (10 seconds) rather than the fast path

### Requirement: Post-reroute cooldown
After a confirmed reroute, the system SHALL NOT confirm another reroute within 25 seconds, even if the vehicle remains off-route, to prevent cascading recalculations.

#### Scenario: No cascade after reroute
- **WHEN** a reroute has been confirmed and the vehicle is still off-route within 25 seconds
- **THEN** the system SHALL NOT start another reroute calculation

#### Scenario: Cooldown expires
- **WHEN** more than 25 seconds have passed since the last confirmed reroute and the vehicle is still off-route
- **THEN** the system SHALL allow a new reroute confirmation

### Requirement: Noise guards preserved
The reroute trigger SHALL keep the existing noise guards: reroute requests with GPS accuracy worse than 100 meters SHALL be ignored, and reroute requests within 30 seconds after a tunnel or no-GPS-signal state SHALL be ignored.

#### Scenario: Poor accuracy blocks reroute
- **WHEN** GPS accuracy is worse than 100 meters
- **THEN** the system SHALL ignore off-route reports and SHALL NOT start a reroute

#### Scenario: Tunnel exit blocks reroute
- **WHEN** the vehicle was in a tunnel or no-GPS-signal state within the last 30 seconds
- **THEN** the system SHALL ignore off-route reports and SHALL NOT start a reroute

### Requirement: Phone and Android Auto parity
Reroute trigger timing SHALL behave identically on the phone UI and on Android Auto / Android Automotive OS, since both consume the same navigation state.

#### Scenario: Same trigger on both surfaces
- **WHEN** the vehicle deviates from the route while navigation is active on either the phone or the car screen
- **THEN** the reroute SHALL be triggered with the same timing on both surfaces
