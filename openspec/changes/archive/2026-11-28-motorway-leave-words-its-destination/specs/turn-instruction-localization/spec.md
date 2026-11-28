## MODIFIED Requirements

### Requirement: A motorway manoeuvre names the signposted destination

For a manoeuvre that enters, changes or leaves a motorway, the wording of the way it leads onto SHALL carry the
step's signposted destination beside the motorway's name or reference. A manoeuvre that leads onto nothing but
its sign SHALL word `towards <destination>`. A record that repeats what the manoeuvre leads onto SHALL add no
clause. A step with no destination record SHALL keep today's wording.

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

#### Scenario: A motorway leave carries the signposted destination beside the street it leads onto

- **WHEN** the step ahead leaves a motorway onto a street whose signposted destination is "Essen-Zentrum"
- **THEN** the wording of the way it leads onto SHALL carry the street and the destination
  ("Kruppstraße towards Essen-Zentrum")
- **AND** the manoeuvre's own instruction SHALL stay the manoeuvre alone ("Leave motorway")
- **Case**: `core: ManeuverLabelTest` "a motorway leave carries the signposted destination beside the street it
  leads onto"

#### Scenario: A motorway leave that names nothing but its sign

- **WHEN** the step ahead leaves a motorway, names no street, and carries the signposted destination
  "Dortmund-Schüren"
- **THEN** the wording of the way it leads onto SHALL be "towards Dortmund-Schüren", never the bare place name
- **AND** the instruction SHALL read "Leave motorway towards Dortmund-Schüren"
  ("Autobahn verlassen Richtung Dortmund-Schüren" in German), never "onto towards"
- **Case**: `core: ManeuverLabelTest` "a motorway leave that names nothing but its sign words the sign"

#### Scenario: A destination that repeats the target adds no clause

- **WHEN** a motorway manoeuvre's destination record equals the reference it leads onto ("A 45")
- **THEN** the wording SHALL be that reference alone, never "A 45 towards A 45"
- **Case**: `core: ManeuverLabelTest` "a destination that repeats the target adds no towards clause"

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
