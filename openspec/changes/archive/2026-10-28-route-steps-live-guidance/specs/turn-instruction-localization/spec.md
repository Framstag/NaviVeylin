# turn-instruction-localization

## MODIFIED Requirements

### Requirement: Instruction text is rebuilt from structured fields

The display text for a navigation instruction SHALL be built from the manoeuvre the route step describes — its
kind, its move, its roundabout exit ordinal and circulation direction, its street name or ref, and its way type
— through localized `:core` resources. No native description string SHALL be read, because the native layer no
longer produces guidance text for any manoeuvre.

#### Scenario: Left turn short description
- **WHEN** the step ahead is a left turn
- **THEN** the short description SHALL be the localized "turn left" string (English "Turn left", German "Links abbiegen")

#### Scenario: Turn description includes street
- **WHEN** the step ahead is a left turn onto a street with a name
- **THEN** the full description SHALL be the localized short description joined with the street name (English "Turn left into Hauptstrasse", German "Links abbiegen in Hauptstraße")

#### Scenario: No street name
- **WHEN** the step ahead carries no street name
- **THEN** the full description SHALL equal the localized short description

### Requirement: All native instruction kinds are covered

The localizer SHALL word every manoeuvre kind the route step model carries: the start, the arrival, the seven
turn moves, roundabout enter, roundabout leave with its exit ordinal, motorway enter, motorway change and
motorway leave. A motorway change and a motorway leave SHALL be recognized by the step's kind, never by matching
words in a description, and their wording SHALL follow the move the step reports.

#### Scenario: Start instruction

- **WHEN** the step is the route's start
- **THEN** the short description SHALL be the localized start string (English "Start", German "Start")

#### Scenario: Destination reached

- **WHEN** the step is the route's arrival
- **THEN** the short description SHALL be the localized arrival string (English "Arrive", German "Ankunft")

#### Scenario: Roundabout enter

- **WHEN** the step enters a roundabout
- **THEN** the short description SHALL be the localized roundabout string (English "Roundabout", German "Kreisverkehr")

#### Scenario: Roundabout leave with exit count

- **WHEN** the step leaves the roundabout at exit 3
- **THEN** the short description SHALL be the localized exit string with the count (English "Exit 3", German "Ausfahrt 3")
- **Case**: `core: TurnInstructionLocalizerTest` "roundabout leave at exit 3 is localized from the instruction's exit ordinal"

#### Scenario: Roundabout leave with no exit count

- **WHEN** the step leaves a roundabout and carries no exit ordinal
- **THEN** the short description SHALL still be produced, and SHALL NOT be empty or carry an invented number
- **Case**: `core: TurnInstructionLocalizerTest` "roundabout leave without an exit ordinal is still worded"

#### Scenario: Motorway enter

- **WHEN** the step enters a motorway
- **THEN** the short description SHALL be the localized enter-motorway string (English "Enter motorway", German "Autobahn auffahren")

#### Scenario: Motorway change

- **WHEN** the step keeps left onto another motorway
- **THEN** the short description SHALL be the localized keep string (English "Keep left", German "Links halten")
- **AND** it SHALL be chosen from the step's kind and move, not from any published text
- **Case**: `core: ManoeuvreLabelTest` "a motorway change is worded keep-left from its kind and move"

#### Scenario: Motorway leave

- **WHEN** the step leaves a motorway
- **THEN** the short description SHALL be the localized leave-motorway string (English "Leave motorway", German "Autobahn verlassen")
- **AND** it SHALL be chosen from the step's kind, not from any published text
- **Case**: `core: ManoeuvreLabelTest` "a motorway leave is worded from its kind"

## REMOVED Requirements

### Requirement: Fallback to native text

**Reason**: The native layer no longer produces instruction text for guidance — the step model carries values
only. There is nothing to fall back to, and the fallback is what let an unmapped manoeuvre show English text in
a German app.

**Migration**: A manoeuvre whose kind the localizer has no wording for SHALL render the neutral turn wording
from resources, and a step list without a manoeuvre SHALL render an explicit empty state. The case
`core: ManoeuvreLabelTest` "an unmapped manoeuvre renders the neutral wording" replaces the two former fallback
cases; no surface may display native text, because none is published.
