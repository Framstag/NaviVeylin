# Spec Delta — current-road-info

## ADDED Requirements

### Requirement: One free-driving road/speed status feeds every surface and the notification
The system SHALL resolve the free-driving road (ref, name) and the current speed once per fix and publish it as one free-driving status that the on-screen label, the car's free-driving label and the ongoing notification all read.

#### Scenario: Phone label and shade show the same road
- **WHEN** free driving is active on the phone and a fix resolves a road
- **THEN** the phone's street label and the ongoing notification both show that road's ref and name

#### Scenario: Car publishes its own resolution into the same status
- **WHEN** free driving is active on the car with no phone surface in the mode
- **THEN** the car's resolved road and speed are the ones the notification shows

#### Scenario: One lookup per fix, not one per consumer
- **WHEN** a fix arrives while free driving is active
- **THEN** the road at that position is resolved once for the mode
- **AND** the notification does not trigger a road lookup of its own

#### Scenario: Status is empty before the first fix
- **WHEN** free driving is entered and no fix has been processed
- **THEN** the status carries no road and no speed
- **AND** neither the label nor the notification shows a road from an earlier session

#### Scenario: Status clears when the road is lost
- **WHEN** a fix resolves no road at the current position
- **THEN** the status carries no road
- **AND** the label disappears instead of keeping the previous street
