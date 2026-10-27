# Spec Delta

## ADDED Requirements

### Requirement: Navigation speaks the native voice instruction

While a navigation session is active, the engine SHALL announce every non-empty native voice sample
sequence through the device's text-to-speech output. It SHALL compose the sample identifiers into one
localized instruction sentence in the order the native agent emitted them, and it SHALL NOT speak when
no navigation session is active.

#### Scenario: A native voice sequence is spoken while navigating

- **WHEN** the native navigation listener reports the sample sequence for a turn after a distance
  while a session is active
- **THEN** the engine speaks the localized sentence for that sequence
- **AND** the spoken sentence names the distance and the manoeuvre in the emitted order
- **Case**: `NavigationEngineVoiceGuidanceTest#voiceSampleSequenceIsSpokenWhileNavigating`

#### Scenario: A voice instruction after navigation stops is silent

- **WHEN** a native voice sample sequence arrives after navigation has stopped
- **THEN** the engine speaks nothing
- **AND** stopping navigation stops the current utterance
- **Case**: `NavigationEngineVoiceGuidanceTest#voiceInstructionAfterStopIsSilent`
