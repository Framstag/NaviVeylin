# Spec Delta

## MODIFIED Requirements

### Requirement: Noise guards preserved
The reroute trigger SHALL keep the existing noise guards for every surface: reroute requests with GPS accuracy worse than 100 meters SHALL be ignored, and reroute requests within 30 seconds after a tunnel or no-GPS-signal state SHALL be ignored. These guards SHALL be applied by the single navigation engine, not per surface.

#### Scenario: Poor accuracy blocks reroute
- **WHEN** GPS accuracy is worse than 100 meters
- **THEN** the system SHALL ignore off-route reports and SHALL NOT start a reroute

#### Scenario: Tunnel exit blocks reroute
- **WHEN** the vehicle was in a tunnel or no-GPS-signal state within the last 30 seconds
- **THEN** the system SHALL ignore off-route reports and SHALL NOT start a reroute

#### Scenario: Same guards while driving on the car surface
- **WHEN** navigation runs on Android Auto or Android Automotive OS and GPS accuracy is worse than 100 meters
- **THEN** the off-route report is ignored under the same rule as on the phone

### Requirement: Phone and Android Auto parity
Reroute trigger timing SHALL behave identically on the phone UI and on Android Auto / Android Automotive OS, because exactly one navigation engine applies one policy to the session that every surface renders. No surface SHALL apply its own thresholds, interval gate or confirmation rule.

#### Scenario: Same trigger on both surfaces
- **WHEN** the vehicle deviates from the route while navigation is active on either the phone or the car screen
- **THEN** the reroute SHALL be triggered with the same timing on both surfaces

#### Scenario: One policy for the session, not one per surface
- **WHEN** a navigation session is displayed on both surfaces at the same time
- **THEN** the same single reroute policy decides whether and when a reroute starts
- **AND** no second confirmation or interval rule exists for either surface

#### Scenario: Confirmed deviation reroutes on the car surface
- **WHEN** navigation runs on the car surface and the deviation is confirmed by the engine policy
- **THEN** the reroute starts with the same confirmation timing as on the phone
