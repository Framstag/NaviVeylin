# Spec Delta

## ADDED Requirements

### Requirement: The second line carries the signposted destination

The next-turn overlay's second line SHALL show the thing the manoeuvre leads onto. For a motorway enter or
change that carries a signposted destination, that line SHALL include the destination beside the motorway's
name or reference. The line SHALL keep the overlay's existing wrapping and ellipsis rules, and the value SHALL
word the manoeuvre the way the car's instruction text words it.

#### Scenario: A motorway change shows the signposted destination

- **WHEN** the manoeuvre ahead is a motorway change onto the A8 whose signposted destination is "München"
- **THEN** the overlay's second line SHALL read "A8 Richtung München" in German
- **AND** the first line SHALL remain the manoeuvre alone ("Rechts halten")
- **Case**: `app: NextTurnOverlayTest` "a motorway change's second line carries the signposted destination"

#### Scenario: The second line is unchanged without a destination record

- **WHEN** the manoeuvre ahead is a motorway change carrying no destination record
- **THEN** the overlay's second line SHALL read the manoeuvre's target alone, with no destination clause
- **AND** that target SHALL be the motorway the step names, else its street name — the order `leadsOnto`
  uses, swapped from the `displayName ?: motorwayTo` order the line used before, so a step that names both now
  shows the motorway
- **Case**: `app: NextTurnOverlayTest` "a motorway change without a destination keeps the bare reference"

#### Scenario: The longer line stays in the overlay's own budget

- **WHEN** the second line grows by the destination clause
- **THEN** the description SHALL carry the whole clause in its one text, so nothing of the value is dropped
  before the overlay's own wrapping sees it
- **AND** the wrapping and ellipsis the description applies SHALL stay the ones it already has, untouched by
  this change — the rendered frame is what the overlay's device pixel case measures, not this unit case
- **Case**: `app: NextTurnOverlayTest` "the destination clause wraps inside the existing line limit"
