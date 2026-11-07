# Spec Delta

## ADDED Requirements

### Requirement: A motorway manoeuvre names the signposted destination

For a manoeuvre that enters or changes a motorway, the wording of the way it leads onto SHALL carry the step's
signposted destination beside the motorway's name or reference — "A8 Richtung München" / "A8 towards München" —
resolved from `:core` resources like every other word. A step that carries no destination record SHALL keep
exactly the wording it has today.

#### Scenario: A motorway change carries the signposted destination

- **WHEN** the step ahead is a motorway change onto the A8 whose signposted destination is "München"
- **THEN** the wording of the way it leads onto SHALL be the motorway's reference and the destination
  ("A8 Richtung München" in German, "A8 towards München" in English)
- **AND** the destination SHALL be read from the step's own destination record, never parsed out of other text
- **Case**: `core: ManeuverLabelTest` "a motorway change carries the signposted destination"

#### Scenario: A motorway entry carries the signposted destination

- **WHEN** the step ahead enters a motorway onto the A8 whose signposted destination is "München"
- **THEN** the wording of the way it leads onto SHALL carry the reference and the destination
- **Case**: `core: ManeuverLabelTest` "a motorway entry carries the signposted destination"

#### Scenario: A motorway manoeuvre without a destination record

- **WHEN** the step ahead enters or changes a motorway and carries no destination record
- **THEN** the wording SHALL be exactly the wording the step produces today, with no placeholder, no empty
  "towards" clause and no invented destination
- **Case**: `core: ManeuverLabelTest` "a motorway change without a destination keeps today's wording"

#### Scenario: A destination with several signposted values

- **WHEN** the step's destination record reads "München;Garmisch-Partenkirchen"
- **THEN** the manoeuvre SHALL carry the first signposted value only
- **AND** the second value SHALL NOT be truncated into a partial word
- **Case**: `core: ManeuverFromStepTest` "the manoeuvre carries the first segment of a multi-value destination"

#### Scenario: A destination record on a manoeuvre that is not a motorway manoeuvre

- **WHEN** a plain turn's step carries a destination record
- **THEN** the turn's wording SHALL NOT gain a destination clause
- **AND** the value SHALL remain on the manoeuvre, unrendered
- **Case**: `core: ManeuverLabelTest` "a plain turn does not show a destination record it carries"
