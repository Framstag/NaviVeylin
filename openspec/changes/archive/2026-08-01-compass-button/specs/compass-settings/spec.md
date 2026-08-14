## MODIFIED Requirements

### Requirement: Per-mode orientation setting

The system SHALL provide two orientation modes for each of the two map modes (free-form and navigation): "North always up" and "Follow direction".

- In free-form mode, "North always up" SHALL keep the map at 0° rotation regardless of GPS bearing. "Follow direction" SHALL rotate the map to match the device's GPS bearing when follow mode is active.
- In navigation mode, "North always up" SHALL keep the map at 0° rotation during turn-by-turn guidance. "Follow direction" SHALL rotate the map to match the driving direction (the bearing from the navigation engine).
- The selected orientation for each mode SHALL be independent — changing free-form orientation SHALL NOT affect navigation orientation and vice versa.
- The orientation mode SHALL be togglable from two UI surfaces: the location options bottom sheet AND the compass button long press. Both SHALL update the same underlying state and SHALL stay in sync.

#### Scenario: Free-form north-up keeps map at 0°

- **WHEN** the user is in free-form mode
- **AND** orientation is set to "North always up"
- **THEN** the map rotation SHALL be 0° regardless of GPS bearing

#### Scenario: Free-form follow direction rotates to bearing

- **WHEN** the user is in free-form mode
- **AND** orientation is set to "Follow direction"
- **AND** follow mode is active
- **WHEN** a GPS location update with bearing is received
- **THEN** the map SHALL rotate to match the GPS bearing

#### Scenario: Navigation north-up overrides driving direction

- **WHEN** navigation is active
- **AND** orientation is set to "North always up"
- **THEN** the map SHALL stay at 0° rotation during guidance
- **AND** the map SHALL NOT rotate to driving direction

#### Scenario: Navigation follow direction rotates to driving bearing

- **WHEN** navigation is active
- **AND** orientation is set to "Follow direction"
- **THEN** the map SHALL rotate to match the driving direction bearing

#### Scenario: Free-form and navigation orientations are independent

- **WHEN** the user sets free-form orientation to "North always up"
- **AND** navigation orientation to "Follow direction"
- **WHEN** navigation starts
- **THEN** the map SHALL rotate to driving direction
- **WHEN** navigation stops
- **THEN** the map SHALL return to 0° rotation

#### Scenario: Compass button toggles orientation

- **WHEN** the user long-presses the compass button
- **THEN** the orientation mode SHALL toggle between "North always up" and "Follow direction"
- **AND** the location options bottom sheet SHALL reflect the updated selection

#### Scenario: Bottom sheet and compass stay in sync

- **WHEN** the user changes orientation via the location options bottom sheet
- **THEN** the compass button SHALL reflect the updated mode
- **WHEN** the user toggles orientation via the compass button long press
- **THEN** the location options bottom sheet SHALL show the updated orientation selection
