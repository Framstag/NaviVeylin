## ADDED Requirements

### Requirement: Vehicle anchor position controls

The location options bottom sheet SHALL contain a "Vehicle position" control with an entry for routing and one for free driving. Selecting an entry SHALL open a 5×3 position picker (horizontal 10/30/50/70/90% of the screen width, vertical 10/50/90% of the screen height, 15 presets total) with the currently configured anchor marked. The control labels SHALL match the Android Auto settings dialog labels, and changes SHALL persist globally so both surfaces share the same value.

#### Scenario: Anchor rows visible in the sheet

- **WHEN** the location options bottom sheet is open
- **THEN** it shows a "Vehicle position" row for routing and one for free driving
- **AND** each row displays the currently configured anchor

#### Scenario: Picker offers the 5×3 grid

- **WHEN** the user selects a vehicle position row
- **THEN** a position picker opens offering the 15 presets of the 5×3 grid
- **AND** the currently configured anchor is marked

#### Scenario: Selection persists globally

- **WHEN** the user picks an anchor position in the picker
- **THEN** the change persists through the shared settings storage
- **AND** the new anchor applies to vehicle positioning on the phone and on Android Auto

#### Scenario: Picker labels match Android Auto

- **WHEN** the user compares the phone anchor picker with the Android Auto anchor picker
- **THEN** the two pickers use the same position labels and hierarchy
