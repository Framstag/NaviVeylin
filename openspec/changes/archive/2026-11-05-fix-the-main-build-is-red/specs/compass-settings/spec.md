## ADDED Requirements

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
