# Spec Delta

## ADDED Requirements

### Requirement: AA place list follows the stored group order

The Android Auto favorites list SHALL present the group headers in the stored group order, read-only, and SHALL update in place when the stored order changes while the screen is open. The order SHALL be taken from the provider's group order flow rather than from the iteration order of the group map, so a change that alters only the sequence is rendered.

#### Scenario: AA headers follow a phone reorder

- **GIVEN** the user moved a group to the first position on the phone
- **WHEN** the favorites list is shown on the car screen
- **THEN** that group's header SHALL appear before the other group headers

#### Scenario: AA headers update in place

- **GIVEN** the favorites list is open on the car screen
- **WHEN** the stored group order changes
- **THEN** the header order SHALL update in place, without leaving and re-entering the screen

#### Scenario: An order-only change reaches the car

- **GIVEN** the car screen shows several groups whose contents do not change
- **WHEN** the stored group order changes
- **THEN** the header sequence SHALL follow the new order

#### Scenario: A group the order does not name yet still appears

- **GIVEN** the stored order does not name a group the store holds
- **WHEN** the car screen renders its headers
- **THEN** that group SHALL still be listed, after the named ones

#### Scenario: No reorder affordance for groups on the car screen

- **WHEN** the favorites list is displayed
- **THEN** it SHALL offer no drag or move action for group headers
- **AND** selecting a favorite SHALL still start the destination-picker navigation flow

#### Scenario: Group order change does not disturb the favorite order

- **GIVEN** a group's favorites are listed on the car screen
- **WHEN** the group order changes
- **THEN** each group's rows SHALL keep their stored favorite order
