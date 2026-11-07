# Spec Delta

## ADDED Requirements

### Requirement: The car hint names the signposted destination

The car hint's primary text SHALL stay the manoeuvre instruction. Its secondary text — the distance to the
manoeuvre and the arrival time — SHALL carry the signposted destination of a motorway enter or change that
carries one, worded by the same `:core` function that words the phone overlay's second line and the car's own
target street, so the three surfaces cannot drift. A hint for any other manoeuvre, and a hint for a motorway
manoeuvre whose step carries no destination record, SHALL keep exactly the text it shows today.

#### Scenario: A motorway change's secondary text carries the destination

- **WHEN** the manoeuvre ahead is a motorway change onto the A8 whose signposted destination is "München"
- **THEN** the car hint's primary text SHALL remain the manoeuvre alone ("Rechts halten" in German)
- **AND** its secondary text SHALL carry the destination beside the distance and the arrival time
  ("450 m · 14:32 · A8 Richtung München" in German)
- **Case**: `app: CarHintContentTest` "aMotorwayChangesSecondaryTextCarriesTheSignpostedDestination"

#### Scenario: A hint without a destination record keeps today's secondary text

- **WHEN** the manoeuvre ahead is a motorway change carrying no destination record
- **THEN** the car hint's secondary text SHALL be exactly the distance and the arrival time, as it is today
- **Case**: `app: CarHintContentTest` "aMotorwayChangeWithoutADestinationKeepsTodaysSecondaryText"
