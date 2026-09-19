## Purpose

Keeps the Android Auto favorites place list aligned with the order the user set on the phone, without adding a reorder interaction the Car App Library templates cannot express.

## ADDED Requirements

### Requirement: AA place list reflects the stored favorite order

The Android Auto favorites `PlaceListTemplate` SHALL list the favorites of each group in the stored favorite order, read-only, and SHALL update when the stored order changes while the screen is open.

#### Scenario: AA list follows a phone reorder

- **GIVEN** the user reordered the favorites of a group on the phone
- **WHEN** the favorites place list is shown on the car screen
- **THEN** the group's rows SHALL appear in the reordered sequence

#### Scenario: AA list updates in place

- **GIVEN** the favorites place list is open on the car screen
- **WHEN** the stored order changes
- **THEN** the list SHALL update in place with the new order, without leaving and re-entering the screen

#### Scenario: No reorder affordance on the car screen

- **WHEN** the favorites place list is displayed
- **THEN** it SHALL offer no drag or move action for individual favorites
- **AND** selecting a favorite SHALL still start the destination-picker navigation flow
