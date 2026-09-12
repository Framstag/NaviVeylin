## Purpose

Makes every user-facing string in NaviVeylin translatable through the Android resource system, with English as the default locale and German fully supported, so the phone and Android Auto variants render in the device language with locale-correct number and unit formatting.

## ADDED Requirements

### Requirement: Native-generated navigation text is localized at the frontend
Turn-by-turn instruction text produced by the native JNI bridge (hardcoded English in `OSMScoutClient.cpp`, e.g. "Turn left", "Exit 3", "Enter motorway", "Keep left") SHALL be rebuilt from the structured instruction fields (`turnType`, `streetName`) into localized resource strings in the frontend. The native layer SHALL remain unchanged. When a turn type or native text pattern has no localized mapping, the app SHALL fall back to the native English text.

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
- **WHEN** the native layer returns a turn type or text pattern with no localized mapping
- **THEN** the app SHALL display the native English text rather than an empty or wrong string

#### Scenario: English device locale
- **WHEN** the device locale is English
- **THEN** turn instructions SHALL render in English (unchanged behavior)
