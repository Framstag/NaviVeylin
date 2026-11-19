# Spec Delta — always-on-display

## MODIFIED Requirements

### Requirement: Keep screen on toggle in location options

The settings surface SHALL display a "Keep screen on" toggle switch at all times.

#### Scenario: Toggle always visible

- **WHEN** the user opens the settings surface
- **THEN** a "Keep screen on" toggle SHALL be visible in the Karte topic
- **Case**: none — the toggle keeps its behaviour; its case on the settings surface is written in tasks.md group 3

#### Scenario: Toggle reflects current state

- **WHEN** the user opens the settings surface
- **THEN** the "Keep screen on" toggle SHALL reflect the current setting state
- **WHEN** the user toggles the setting
- **WHEN** the user closes and re-opens the settings surface
- **THEN** the toggle SHALL reflect the updated state
- **Case**: none — the toggle keeps its behaviour; its case on the settings surface is written in tasks.md group 3
