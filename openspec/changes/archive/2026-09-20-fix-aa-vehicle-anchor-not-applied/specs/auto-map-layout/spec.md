# Spec Delta

## ADDED Requirements

### Requirement: Anchor settings reflect the persisted value on re-visibility
The system SHALL display the currently persisted vehicle-anchor values (routing and free driving) in the Android Auto settings dialog whenever the dialog becomes visible again: a value changed in the anchor picker SHALL appear on the settings row when the picker pops back, and the rows SHALL NOT show a snapshot from an earlier visit.

#### Scenario: Picker selection shown on pop-back
- **WHEN** the driver picks a preset in the vehicle anchor picker
- **AND** the picker pops back to the settings dialog
- **THEN** the corresponding vehicle position row displays the newly selected anchor label

#### Scenario: Re-entry shows current values
- **WHEN** the settings dialog is opened again after the vehicle anchors changed on either surface
- **THEN** the vehicle position rows display the currently persisted anchors

### Requirement: Anchor selection survives immediate dismissal
The system SHALL persist a vehicle-anchor selection made in the picker even when the picker closes immediately after the tap — the write SHALL NOT be lost when the picker screen is destroyed on dismissal.

#### Scenario: Selection persisted on immediate pop
- **WHEN** the driver taps a preset in the anchor picker
- **AND** the picker closes right away (pop-back without delay)
- **THEN** a subsequent fresh settings load SHALL return the tapped preset
- **AND** the change SHALL be present after a restart of the car app session
