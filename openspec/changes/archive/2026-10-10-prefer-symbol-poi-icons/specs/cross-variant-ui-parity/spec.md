# Cross-variant UI parity — delta

## ADDED Requirements

### Requirement: A phone-only control whose effect reaches the car

A settings control MAY exist only in the phone variant when the car variant's surface cannot carry
it, provided the car's rendering still follows the persisted value the phone wrote. Such an absence
is an accepted deviation and SHALL be recorded with the setting it belongs to, not treated as a
platform-constrained difference that needs a relabeled or reduced car element.

#### Scenario: Effect reaches the car without a car control

- **WHEN** a setting has a phone-only control and the phone changes the persisted value
- **THEN** the car variant's rendering follows the new value without offering that control

#### Scenario: Absence is not relabeled

- **WHEN** the car variant offers no control for such a setting
- **THEN** no placeholder, disabled or differently labeled car element is added for it
