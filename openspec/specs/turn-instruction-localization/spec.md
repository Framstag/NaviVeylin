# turn-instruction-localization Specification

## Purpose

Localizes turn-by-turn instruction text for the phone UI and Android Auto by building display text in the frontend from the route step model's own values — the manoeuvre's kind, its move, its roundabout exit ordinal and circulation direction, its street name or ref, and its way type — through Android string resources, with English as the default and German fully supported. The native layer publishes values only: it produces no guidance text for any manoeuvre, so there is no description string for a surface to relay (change `retire-instruction-prose`).

## Requirements

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

### Requirement: Shared resources for phone and Auto
The instruction strings SHALL live in the `:core` module resources so the phone app and the Android Auto variant resolve the identical strings for every locale (label parity).

#### Scenario: Same German string on phone and Auto
- **WHEN** the device locale is German
- **THEN** the phone overlay and the Auto host panel SHALL render the same German instruction text for the same instruction

#### Scenario: German translation completeness
- **WHEN** the `:core` German resource set is validated against the English default
- **THEN** every instruction string key present in `values/strings.xml` SHALL also exist in `values-de/strings.xml`

### Requirement: Route analysis rows are localized from the step model

The route analysis rows SHALL resolve their text from the route's step model through `:core` resources, in the
device language, for every manoeuvre kind the step model carries — the start, the arrival, the seven turn
moves, roundabout enter and leave, motorway enter, change and leave, and a street-name change. The rows SHALL
NOT display a native description string, and SHALL NOT decide their icon by matching prose.

#### Scenario: A German row names the manoeuvre in German

- **WHEN** the device locale is German and a route's row describes a left turn into a street
- **THEN** the row SHALL read as the German "links abbiegen" wording plus the street
- **AND** SHALL NOT read as the native English text
- **Case**: `app: RoutePanelLocalizedRowsTest` "a German left turn row is localized"

#### Scenario: A motorway change is worded as one

- **WHEN** a row describes keeping left onto another motorway
- **THEN** the row SHALL read as the localized motorway-change wording
- **AND** SHALL NOT read as a plain left turn
- **Case**: `app: RoutePanelLocalizedRowsTest` "a motorway change row is not a plain turn"

#### Scenario: The row icon follows the manoeuvre, not the text

- **WHEN** a row's icon is chosen
- **THEN** it SHALL be chosen from the step's manoeuvre kind and move
- **AND** SHALL NOT be chosen by matching the row's text
- **Case**: `app: RoutePanelLocalizedRowsTest` "the row icon follows the step kind"
