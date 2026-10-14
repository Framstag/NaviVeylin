# Spec Delta

## MODIFIED Requirements

### Requirement: All native instruction kinds are covered

The localizer SHALL map every instruction kind the JNI bridge emits: start, destination reached, plain turns (sharp/slight/straight, left/right), roundabout enter, roundabout leave (with exit count), motorway enter, motorway change ("Keep ..."), and motorway leave.

#### Scenario: Start instruction

- **WHEN** an instruction has `turnType = START`
- **THEN** the short description SHALL be the localized start string (English "Start", German "Start")

#### Scenario: Destination reached

- **WHEN** an instruction has `turnType = TARGET_REACHED`
- **THEN** the short description SHALL be the localized arrival string (English "Arrive", German "Ankunft")

#### Scenario: Roundabout enter

- **WHEN** an instruction has `turnType = ROUNDABOUT_ENTER`
- **THEN** the short description SHALL be the localized roundabout string (English "Roundabout", German "Kreisverkehr")

#### Scenario: Roundabout leave with exit count

- **WHEN** an instruction has `turnType = ROUNDABOUT_LEAVE` and leaves the roundabout at exit 3
- **THEN** the short description SHALL be the localized exit string with the count (English "Exit 3", German "Ausfahrt 3")
- **Case**: `core: TurnInstructionLocalizerTest` "roundabout leave at exit 3 is localized from the instruction's exit ordinal"

#### Scenario: Roundabout leave with no exit count

- **WHEN** an instruction has `turnType = ROUNDABOUT_LEAVE` and carries no exit ordinal
- **THEN** the short description SHALL still be produced, and SHALL NOT be empty or carry an invented number
- **Case**: `core: TurnInstructionLocalizerTest` "roundabout leave without an exit ordinal is still worded"

#### Scenario: Motorway enter

- **WHEN** an instruction has `turnType = MOTORWAY_ENTER`
- **THEN** the short description SHALL be the localized enter-motorway string (English "Enter motorway", German "Autobahn auffahren")

#### Scenario: Motorway change

- **WHEN** an instruction has a plain move `turnType` (e.g. `LEFT`) and the native short description starts with "Keep "
- **THEN** the short description SHALL be the localized keep string (English "Keep left", German "Links halten")

#### Scenario: Motorway leave

- **WHEN** an instruction has a plain move `turnType` and the native short description is "Leave motorway"
- **THEN** the short description SHALL be the localized leave-motorway string (English "Leave motorway", German "Autobahn verlassen")
