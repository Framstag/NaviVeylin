# voice-guidance Specification

## Purpose
Defines how turn-by-turn navigation turns the native voice sample sequence into one localized
spoken instruction on the device's text-to-speech engine, and when that speech is silent.

## Requirements

### Requirement: Navigation speaks the native voice instruction

While a navigation session is active and the voice switch of its surface is on, the engine SHALL
announce each recognised native voice instruction through the device's text-to-speech output,
replacing any utterance in progress. It SHALL compose the sample identifiers into one localized
instruction sentence in the order the native agent emitted them, SHALL stay silent when a sequence
carries no recognised instruction, and SHALL NOT speak when no navigation session is active.

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

#### Scenario: A turn on a surface whose voice switch is off is silent

- **WHEN** the session's surface has its voice switch off and the listener reports the sample sequence
  for a turn
- **THEN** the engine speaks nothing, and the navigation state is otherwise unchanged
- **Case**: `NavigationEngineVoiceGuidanceTest#voiceSwitchOffKeepsATurnSilent`

### Requirement: The voice switch is per surface, persisted and on by default

Each surface SHALL have its own persisted voice switch and SHALL speak according to its own value: a
navigation session on the phone follows the phone's switch, a session on the car follows the car's
switch. Both switches SHALL default to on, and neither SHALL change the other. Both surfaces offer the
switch under the same rule; only each surface's own label set differs.

#### Scenario: A fresh install speaks on both surfaces

- **WHEN** the app runs with settings written before the switches existed
- **THEN** the phone's voice switch and the car's voice switch both read on
- **Case**: `SettingsStorageVoiceSwitchTest#freshSettingsHaveBothVoiceSwitchesOn`

#### Scenario: The phone's switch governs a phone session

- **WHEN** a navigation session started on the phone announces a turn
- **THEN** it speaks according to the phone's switch, and the car's value changes nothing
- **Case**: `NavigationEngineVoiceGuidanceTest#thePhoneSessionReadsThePhoneSwitch`

#### Scenario: The car's switch governs a car session

- **WHEN** the phone's voice switch is off and a navigation session started on the car announces a
  turn
- **THEN** the car speaks the sentence, and the phone's stored value is unchanged
- **Case**: `NavigationEngineVoiceGuidanceTest#theCarSessionReadsTheCarSwitch`

#### Scenario: A change applies to the next instruction

- **WHEN** the switch of the session's surface is turned off during an active navigation session and
  the next instruction arrives
- **THEN** that instruction is not spoken
- **Case**: `NavigationEngineVoiceGuidanceTest#voiceSwitchChangeAppliesToTheNextInstruction`

#### Scenario: The phone offers both switches in its settings sheet

- **WHEN** the phone's settings bottom sheet is open during navigation
- **THEN** it shows a voice-guidance switch and a GPS-announcement switch with their current values,
  and toggling either persists it
- **Case**: `LocationOptionsVoiceSwitchesTest#bothSwitchesAreShownAndPersisted`

### Requirement: GPS announcements are one shared switch, off by default

GPS state announcements SHALL be governed by one persisted switch shared by both surfaces, defaulting
to off. Either surface SHALL be able to change it and the other SHALL read the changed value; while it
is off, no GPS state is spoken on either surface.

#### Scenario: GPS is silent by default

- **WHEN** the native listener reports a GPS-lost or GPS-found sample with the switch at its default
- **THEN** the engine speaks nothing, and navigation continues
- **Case**: `NavigationEngineVoiceGuidanceTest#gpsAnnouncementIsSilentByDefault`

#### Scenario: A car change is the value the phone reads

- **WHEN** the driver turns the shared switch on in the car preferences and the phone reads its
  settings afterwards
- **THEN** the phone sees the switch on, because it is one value and not two
- **Case**: `SettingsStorageVoiceSwitchTest#theSharedGpsValueIsOneField`

### Requirement: A GPS announcement is spoken only after the state held

When the shared switch is on, an announced GPS state SHALL be the state that survived a hold window: a
state an opposite sample replaces inside that window SHALL NOT be spoken, and the end of the session
SHALL cancel a pending announcement.

#### Scenario: A flapping signal speaks only the state that held

- **WHEN** the switch is on and a GPS-lost sample is replaced by a GPS-found sample inside the hold
  window
- **THEN** the loss is not spoken, and the recovery is spoken once it has held the window itself
- **Case**: `NavigationEngineVoiceGuidanceTest#gpsFlapSpeaksOnlyTheStateThatHeld`

#### Scenario: A state that holds is announced once

- **WHEN** the switch is on and the GPS-lost state survives the hold window
- **THEN** the loss is spoken exactly once, and a repeated sample of the same state adds no further
  utterance
- **Case**: `NavigationEngineVoiceGuidanceTest#gpsStateThatHoldsIsAnnouncedOnce`

#### Scenario: Stopping navigation cancels a pending announcement

- **WHEN** a GPS state is inside its hold window and navigation stops
- **THEN** nothing is spoken and the pending announcement does not outlive the session
- **Case**: `NavigationEngineVoiceGuidanceTest#stopCancelsAPendingGpsAnnouncement`

### Requirement: The spoken GPS wording follows the same rule on both surfaces

Because the switch is shared, a spoken GPS state SHALL use the same rule on the phone and on the car:
the surface's voice switch does not affect it, and the session's surface does not change whether it is
spoken. Only the shared switch and the hold window decide.

#### Scenario: A car session with its voice switch off still speaks a held GPS state

- **WHEN** the car's voice switch is off, the shared GPS switch is on, and a GPS state survives the
  hold window while a car session navigates
- **THEN** the state is spoken
- **Case**: `NavigationEngineVoiceGuidanceTest#gpsIgnoresTheSurfaceVoiceSwitch`
