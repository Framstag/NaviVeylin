# turn-instruction-localization Specification

## Purpose

Localizes turn-by-turn instruction text for the phone UI and Android Auto by rebuilding display text from the structured fields the native JNI bridge exposes (`turnType`, `streetName`), using Android string resources with English as the default and German fully supported. The native layer stays unchanged.

## Requirements

### Requirement: Instruction text is rebuilt from structured fields
The display text for a route instruction SHALL be derived from the instruction's `turnType` and `streetName` via localized string resources, not from the native English description. The native English text SHALL be used only as a fallback for unrecognized turn types or patterns.

#### Scenario: Left turn short description
- **WHEN** an instruction has `turnType = LEFT`
- **THEN** the short description SHALL be the localized "turn left" string (English "Turn left", German "Links abbiegen")

#### Scenario: Turn description includes street
- **WHEN** an instruction has `turnType = LEFT` and a non-blank `streetName`
- **THEN** the full description SHALL be the localized short description joined with the street name (English "Turn left into Hauptstrasse", German "Links abbiegen in Hauptstraße")

#### Scenario: No street name
- **WHEN** an instruction has no street name
- **THEN** the full description SHALL equal the localized short description

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
- **WHEN** an instruction has `turnType = ROUNDABOUT_LEAVE` and the native short description is "Exit 3"
- **THEN** the short description SHALL be the localized exit string with the count (English "Exit 3", German "Ausfahrt 3")

#### Scenario: Motorway enter
- **WHEN** an instruction has `turnType = MOTORWAY_ENTER`
- **THEN** the short description SHALL be the localized enter-motorway string (English "Enter motorway", German "Autobahn auffahren")

#### Scenario: Motorway change
- **WHEN** an instruction has a plain move `turnType` (e.g. `LEFT`) and the native short description starts with "Keep "
- **THEN** the short description SHALL be the localized keep string (English "Keep left", German "Links halten")

#### Scenario: Motorway leave
- **WHEN** an instruction has a plain move `turnType` and the native short description is "Leave motorway"
- **THEN** the short description SHALL be the localized leave-motorway string (English "Leave motorway", German "Autobahn verlassen")

### Requirement: Fallback to native text
When the localizer cannot map a turn type or native text pattern, it SHALL return the native English text so the UI never shows an empty or incorrect string.

#### Scenario: Unknown turn type
- **WHEN** an instruction has a turn type with no localized mapping
- **THEN** the short description SHALL be the native English short description

### Requirement: Shared resources for phone and Auto
The instruction strings SHALL live in the `:core` module resources so the phone app and the Android Auto variant resolve the identical strings for every locale (label parity).

#### Scenario: Same German string on phone and Auto
- **WHEN** the device locale is German
- **THEN** the phone overlay and the Auto host panel SHALL render the same German instruction text for the same instruction

#### Scenario: German translation completeness
- **WHEN** the `:core` German resource set is validated against the English default
- **THEN** every instruction string key present in `values/strings.xml` SHALL also exist in `values-de/strings.xml`
