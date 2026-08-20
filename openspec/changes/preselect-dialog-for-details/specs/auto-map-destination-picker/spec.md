## ADDED Requirements

### Requirement: Candidate picker on car map selection

The system SHALL show a candidate picker when a location selected on the car map has multiple candidate objects, before showing the details screen.

#### Scenario: Multiple objects at selected location
- **WHEN** the user selects a location on the car map
- **AND** the location has multiple candidate objects
- **THEN** the system SHALL show the candidate picker listing the candidates
- **AND** the details screen SHALL open only after the user selects a candidate

#### Scenario: Single object at selected location
- **WHEN** the user selects a location on the car map
- **AND** the location has exactly one candidate object
- **THEN** the system SHALL open the details screen directly without a picker

#### Scenario: No objects at selected location
- **WHEN** the user selects a location on the car map
- **AND** the location has no candidate objects
- **THEN** the system SHALL open the details screen with coordinates and nearby street name
