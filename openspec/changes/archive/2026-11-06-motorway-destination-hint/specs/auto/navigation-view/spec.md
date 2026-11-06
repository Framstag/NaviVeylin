# Spec Delta

## ADDED Requirements

### Requirement: The target street carries the signposted destination

The instruction panel's target street SHALL be the thing the manoeuvre leads onto, and for a motorway enter or
change that carries a signposted destination it SHALL include the destination beside the motorway's name or
reference. It SHALL word the manoeuvre exactly as the phone's second line words it (label parity), and SHALL
keep the manoeuvre instruction itself free of the destination.

#### Scenario: The current step's target street carries the destination

- **WHEN** the current step is a motorway change onto the A8 whose signposted destination is "München"
- **THEN** the step's instruction SHALL be the manoeuvre wording alone ("Rechts halten")
- **AND** its target street SHALL read "A8 Richtung München" in German
- **Case**: `auto: NavigationTemplateMapperTest` "the current motorway step's road carries the destination"

#### Scenario: The next step carries its own destination

- **WHEN** the following manoeuvre is a motorway change carrying a different signposted destination
- **THEN** the panel's next step SHALL carry that step's own destination
- **AND** it SHALL NOT repeat the current step's
- **Case**: `auto: NavigationTemplateMapperTest` "the next motorway step carries its own destination"

#### Scenario: A motorway step without a destination record

- **WHEN** a motorway step carries no destination record
- **THEN** its target street SHALL be the thing the manoeuvre leads onto, with no destination clause: the
  motorway it names, else the street it names
- **AND** that is wider than today's panel, which filled the target street from the manoeuvre's own
  `displayName` only and so set no street at all for a step that names a motorway reference
- **Case**: `auto: NavigationTemplateMapperTest` "a motorway step without a destination keeps the bare reference"
