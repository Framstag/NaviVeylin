# Spec Delta

## ADDED Requirements

### Requirement: Reroute keeps the navigation view live under the notice
While a reroute is being calculated the system SHALL keep the navigation view displayed and usable under the calculation notice, with its instruction panel and trip metadata continuing to describe the route the vehicle is still on, and the notice SHALL NOT offer to cancel the reroute while navigation is active.

#### Scenario: Reroute notice over live navigation
- **WHEN** a reroute is still running after the calculation notice delay has elapsed
- **THEN** the calculation notice SHALL be shown over the navigation view
- **AND** the navigation view SHALL remain displayed underneath it
- **AND** the notice SHALL NOT offer to cancel

#### Scenario: Rerouted route arrives
- **WHEN** the rerouted route arrives
- **THEN** the calculation notice SHALL be removed
- **AND** the navigation view SHALL continue with the new route
