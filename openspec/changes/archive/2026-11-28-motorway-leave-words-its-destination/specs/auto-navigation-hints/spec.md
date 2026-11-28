## MODIFIED Requirements

### Requirement: The car hint names the signposted destination

The car hint's primary text SHALL stay the manoeuvre instruction. Its secondary text — the distance to the
manoeuvre and the arrival time — SHALL carry the signposted destination of a motorway enter, change or leave
that carries one, worded by the same `:core` function that words the overlay's second line and the car's target
street. A hint for any other manoeuvre, and one whose step carries no record, SHALL keep exactly today's text.

#### Scenario: A motorway change's secondary text carries the destination

- **WHEN** the manoeuvre ahead is a motorway change onto the A8 whose signposted destination is "München"
- **THEN** the car hint's primary text SHALL remain the manoeuvre alone ("Rechts halten" in German)
- **AND** its secondary text SHALL carry the destination beside the distance and the arrival time
  ("450 m · 14:32 · A8 Richtung München" in German)
- **Case**: `app: CarHintContentTest` "aMotorwayChangesSecondaryTextCarriesTheSignpostedDestination"

#### Scenario: A motorway leave's secondary text carries the destination

- **WHEN** the manoeuvre ahead leaves a motorway, names no street, and carries the destination "Essen-Zentrum"
- **THEN** the car hint's primary text SHALL remain the manoeuvre alone ("Leave motorway")
- **AND** its secondary text SHALL carry the destination ("450 m · 14:32 · towards Essen-Zentrum")
- **Case**: `app: CarHintContentTest` "aMotorwayLeavesSecondaryTextCarriesTheSignpostedDestination"

#### Scenario: A hint without a destination record keeps today's secondary text

- **WHEN** the manoeuvre ahead is a motorway change carrying no destination record
- **THEN** the car hint's secondary text SHALL be exactly the distance and the arrival time, as it is today
- **Case**: `app: CarHintContentTest` "aMotorwayChangeWithoutADestinationKeepsTodaysSecondaryText"
