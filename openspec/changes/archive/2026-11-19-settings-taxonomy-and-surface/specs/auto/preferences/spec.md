# Spec Delta — auto/preferences

## MODIFIED Requirements

### Requirement: Preferences editable from car
The system SHALL let the driver change a preference from the car and persist the change: a preference with
a finite value set SHALL present its options and mark the current one, and a boolean preference SHALL flip
on selection.

#### Scenario: Toggle updates preference
- **WHEN** the driver selects a boolean preference row
- **THEN** the preference value flips and the updated value is saved to shared settings storage
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

#### Scenario: Screen reflects saved value
- **WHEN** a preference is changed
- **THEN** the Preferences screen shows the new value
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

#### Scenario: A value is chosen, never only cycled
- **WHEN** the driver selects a preference row whose value set is finite and larger than one
- **THEN** a screen opens listing every permitted value with the current one marked
- **AND** the driver picks a value from that list instead of tapping repeatedly
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

## ADDED Requirements

### Requirement: The car's preferences are grouped into sub-screens

The car's Preferences screen SHALL present topic entries that open the rows of that topic, using the same
topic names as the phone, and SHALL limit itself to one row list per screen so a driver never scrolls a long
mixed list.

#### Scenario: The root lists topics

- **WHEN** the driver opens Preferences
- **THEN** the screen lists the topics that hold a car-usable setting
- **AND** no individual setting row appears on that screen
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

#### Scenario: A topic opens its rows

- **WHEN** the driver selects a topic
- **THEN** a screen lists that topic's car-usable rows with their current values
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

#### Scenario: Back leaves one level

- **WHEN** a topic screen is shown and the driver presses back
- **THEN** the topic list is shown rather than the map
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7

#### Scenario: Phone-only rows stay absent

- **WHEN** any car topic screen is enumerated
- **THEN** no phone-only row appears on it
- **Case**: none — the grouped car screens' cases are written in tasks.md group 7
