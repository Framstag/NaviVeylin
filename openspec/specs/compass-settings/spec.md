# compass-settings Specification

## Purpose

Controls map orientation per-mode (free-form and navigation) so users can choose between north-up and direction-follow behavior, with settings persisted across app restarts.

## Requirements

### Requirement: Per-mode orientation setting

The system SHALL provide two orientation modes for each of the two map modes (free-form and navigation): "North always up" and "Follow direction".

- In free-form mode, "North always up" SHALL keep the map at 0° rotation regardless of GPS bearing. "Follow direction" SHALL rotate the map to match the device's GPS bearing when follow mode is active.
- In navigation mode, "North always up" SHALL keep the map at 0° rotation during turn-by-turn guidance. "Follow direction" SHALL rotate the map to match the driving direction (the bearing from the navigation engine).
- "North always up" SHALL hold the map at 0° on **every** map commit, not only at the moment the mode is selected: no previously used rotation angle and no missing/noisy bearing signal SHALL rotate the map while this mode is active.
- The selected orientation for each mode SHALL be independent — changing free-form orientation SHALL NOT affect navigation orientation and vice versa.
- The orientation mode SHALL be togglable from three UI surfaces: the map's quick settings sheet, the settings surface's Karte topic AND the compass button long press. All three SHALL update the same underlying state and SHALL stay in sync.

#### Scenario: Free-form north-up keeps map at 0°

- **WHEN** the user is in free-form mode
- **AND** orientation is set to "North always up"
- **THEN** the map rotation SHALL be 0° regardless of GPS bearing
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Free-form follow direction rotates to bearing

- **WHEN** the user is in free-form mode
- **AND** orientation is set to "Follow direction"
- **AND** follow mode is active
- **WHEN** a GPS location update with bearing is received
- **THEN** the map SHALL rotate to match the GPS bearing
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Navigation north-up overrides driving direction

- **WHEN** navigation is active
- **AND** orientation is set to "North always up"
- **THEN** the map SHALL stay at 0° rotation during guidance
- **AND** the map SHALL NOT rotate to driving direction
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Switching to north-up while driving stops further rotation

- **WHEN** the map is following the driving direction (heading-up)
- **AND** the user selects "North always up" (compass long press, quick settings sheet or settings surface)
- **AND** further GPS fixes arrive, with or without a usable bearing
- **THEN** the map rotation SHALL be 0° for every subsequent commit
- **AND** the map SHALL NOT rotate back to the previously used heading-up angle
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Navigation follow direction rotates to driving bearing

- **WHEN** navigation is active
- **AND** orientation is set to "Follow direction"
- **THEN** the map SHALL rotate to match the driving direction bearing
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Free-form and navigation orientations are independent

- **WHEN** the user sets free-form orientation to "North always up"
- **AND** navigation orientation to "Follow direction"
- **WHEN** navigation starts
- **THEN** the map SHALL rotate to driving direction
- **WHEN** navigation stops
- **THEN** the map SHALL return to 0° rotation
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Compass button toggles orientation

- **WHEN** the user long-presses the compass button
- **THEN** the orientation mode SHALL toggle between "North always up" and "Follow direction"
- **AND** the quick settings sheet SHALL reflect the updated selection
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

#### Scenario: Bottom sheet and compass stay in sync

- **WHEN** the user changes orientation via the quick settings sheet or the settings surface
- **THEN** the compass button SHALL reflect the updated mode
- **WHEN** the user toggles orientation via the compass button long press
- **THEN** both the quick settings sheet and the settings surface SHALL show the updated orientation selection
- **Case**: none — the orientation cases exist for the sheet; the third editor's case is written in tasks.md group 3

### Requirement: Orientation settings persisted

The system SHALL persist the per-mode orientation settings to device storage so they survive app restart.

#### Scenario: Settings survive restart

- **WHEN** the user sets free-form orientation to "Follow direction"
- **AND** navigation orientation to "North always up"
- **WHEN** the app is killed and restarted
- **THEN** free-form orientation SHALL still be "Follow direction"
- **THEN** navigation orientation SHALL still be "North always up"

#### Scenario: Default orientation is north-up

- **WHEN** the app is launched for the first time
- **THEN** free-form orientation SHALL default to "North always up"
- **THEN** navigation orientation SHALL default to "Follow direction"

### Requirement: A user orientation choice outranks an in-flight settings read

The orientation state SHALL carry an orientation the user chose while the persisted settings were still loading:
when that read completes it SHALL NOT restore a persisted value over the user's newer choice, for the navigation
orientation and the free-form orientation alike. An orientation the user did not choose SHALL still be restored
from disk.

#### Scenario: North-up chosen while the read is open survives it

- **WHEN** the map is following the driving direction
- **AND** the user selects "North always up" while the start-up settings read is still open
- **WHEN** that read completes, carrying the persisted follow-direction value
- **THEN** the orientation SHALL still be "North always up"
- **AND** further GPS fixes SHALL NOT rotate the map back to the previously used heading-up angle
- **Case**: `app: NorthUpLateRestoreTest.northUpChosenWhileTheReadIsOpenSurvivesTheRead`

#### Scenario: An orientation the user did not choose is restored

- **WHEN** the persisted settings carry a navigation orientation and a free-form orientation that each differ
  from its default
- **AND** the user changes no orientation while the start-up read is open
- **WHEN** that read completes
- **THEN** each orientation SHALL be the persisted value, read from disk
- **Case**: `app: NorthUpLateRestoreTest.orientationTheUserDidNotChooseIsRestoredFromDisk`
