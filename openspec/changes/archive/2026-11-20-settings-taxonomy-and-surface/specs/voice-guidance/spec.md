# Spec Delta — voice-guidance

## MODIFIED Requirements

### Requirement: The voice switch is per surface, persisted and on by default

Each surface SHALL have its own persisted voice switch and SHALL speak according to its own value: a
navigation session on the phone follows the phone's switch, a session on the car follows the car's
switch. Both switches SHALL default to on, and neither SHALL change the other. Each surface SHALL offer
its own switch, and the phone SHALL additionally offer the car's switch, so a car value is reachable
without a car session.

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

- **WHEN** the phone's settings surface is opened, in every map state
- **THEN** it shows a voice-guidance switch and a GPS-announcement switch with their current values,
  and toggling either persists it
- **Case**: none — the announcement rows' cases are written in tasks.md group 5

## ADDED Requirements

### Requirement: The phone offers the car's own voice switch

The phone's settings surface SHALL offer the car's own voice-guidance switch as a row marked for Android
Auto, and changing that row SHALL persist the car's value without a car session. The phone's own switch
SHALL be unaffected by it.

#### Scenario: The car's switch is edited on the phone

- **WHEN** the user switches the car's voice guidance off on the phone
- **THEN** the car's stored value is off
- **AND** the phone's own value is unchanged
- **Case**: none — the announcement rows' cases are written in tasks.md group 5

#### Scenario: The next car session reads the phone's edit

- **WHEN** the car's voice guidance was switched off on the phone and a car navigation session
  announces a turn
- **THEN** the car speaks nothing
- **Case**: none — the announcement rows' cases are written in tasks.md group 5

#### Scenario: A car change is visible on the phone

- **WHEN** the driver changes the car's voice guidance in the car and the phone opens its settings
  surface afterwards
- **THEN** the car's row shows the new value
- **Case**: none — the announcement rows' cases are written in tasks.md group 5
