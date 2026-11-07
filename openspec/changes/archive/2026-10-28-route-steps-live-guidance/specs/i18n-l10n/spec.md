# i18n-l10n

## MODIFIED Requirements

### Requirement: Native-generated navigation text is localized at the frontend

Turn-by-turn guidance text SHALL be produced by the frontend from the route step model — the manoeuvre's kind,
move, roundabout exit ordinal and circulation direction, street name or ref, and way type — and resolved from
localized `:core` resources in the device language. The native layer SHALL NOT produce guidance text: it
publishes values only. No surface SHALL depend on an English string for a manoeuvre, and no native fallback
SHALL exist.

#### Scenario: German turn instruction on Android Auto
- **WHEN** the device locale is German and the next instruction is a left turn
- **THEN** the Android Auto host instruction panel SHALL show the German cue (e.g. "Links abbiegen"), not "Turn left"

#### Scenario: German turn instruction on phone
- **WHEN** the device locale is German and the next instruction is a left turn
- **THEN** the phone next-turn overlay SHALL show the German instruction (e.g. "Links abbiegen"), not "Turn left"

#### Scenario: German roundabout exit
- **WHEN** the device locale is German and the next instruction leaves a roundabout at exit 3
- **THEN** the instruction SHALL render the exit count in German (e.g. "Ausfahrt 3"), not "Exit 3"

#### Scenario: German motorway maneuver
- **WHEN** the device locale is German and the next instruction is a motorway change or leave
- **THEN** the instruction SHALL render in German (e.g. "Links halten", "Autobahn verlassen"), not "Keep left" / "Leave motorway"

#### Scenario: Unknown turn type falls back to native text
- **WHEN** the step ahead carries a manoeuvre the localizer has no wording for
- **THEN** the app SHALL display the localized neutral turn wording rather than an empty string
- **AND** SHALL NOT display a native English text, because the native layer publishes none

#### Scenario: English device locale
- **WHEN** the device locale is English
- **THEN** turn instructions SHALL render in English (unchanged behavior)
