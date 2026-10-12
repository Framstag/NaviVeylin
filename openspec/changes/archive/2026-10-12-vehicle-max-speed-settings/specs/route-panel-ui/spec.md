# Spec Delta

## MODIFIED Requirements

### Requirement: Vehicle selector
The route panel SHALL provide a vehicle selector with three options: Car, Bicycle, and Pedestrian. The selected vehicle SHALL be visually highlighted. The default selection SHALL be Car.

#### Scenario: Car selected by default
- **WHEN** the route panel opens
- **THEN** the Car button SHALL be visually highlighted as selected
- **AND** the routing profile SHALL use `Vehicle.CAR`

#### Scenario: Switch to bicycle
- **WHEN** user taps the Bicycle button
- **THEN** the Bicycle button SHALL be visually highlighted
- **AND** the routing profile SHALL use `Vehicle.BICYCLE`

#### Scenario: Switch to pedestrian
- **WHEN** user taps the Pedestrian button
- **THEN** the Pedestrian button SHALL be visually highlighted
- **AND** the routing profile SHALL use `Vehicle.PEDESTRIAN`

#### Scenario: The selected vehicle's stored maximum speed reaches the profile
- **WHEN** the stored bicycle maximum speed is 20 km/h
- **AND** the user selects Bicycle and starts a calculation
- **THEN** the routing profile carries `Vehicle.BICYCLE` and a maximum speed of 20 km/h

#### Scenario: Each vehicle reads its own stored value
- **WHEN** the stored values are 90 km/h (car), 20 km/h (bicycle) and 4 km/h (pedestrian)
- **AND** the user runs one calculation per vehicle selection
- **THEN** each calculation carries its own vehicle's value and not another vehicle's
