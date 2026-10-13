# Spec Delta

## ADDED Requirements

### Requirement: Auto-zoom speed source per surface

Each surface SHALL feed the speed-to-magnification table a speed it actually knows: the phone its own GPS fix speed while free driving and the navigation engine's speed while route guidance is active, the car the navigation engine's speed. On the phone, a speed that is unknown SHALL NOT be replaced by a remembered value — the table owns what an unknown input means.

#### Scenario: Free drive uses the phone's own speed

- **WHEN** the phone free-drives at 90 km/h or more with no route active
- **THEN** the committed magnification target SHALL be the table's highway value for that speed
- **AND** it SHALL NOT be the value for the speed seed
- **Case**: `app: AutoZoomSpeedSourceTest.freeDriveUsesOwnSpeed`

#### Scenario: Navigation keeps using the engine speed

- **WHEN** route guidance is active
- **THEN** the committed magnification target SHALL be computed from the navigation engine's reported speed
- **Case**: `app: AutoZoomSpeedSourceTest.navigationUsesEngineSpeed`

#### Scenario: An unknown speed follows the table, not a remembered value

- **WHEN** no speed is known for the active surface
- **THEN** the target SHALL be the table's own result for an unknown input
- **AND** no previously remembered speed SHALL drive the zoom
- **Case**: `app: AutoZoomSpeedSourceTest.unknownSpeedIsNotRemembered`

#### Scenario: The car feeds the engine speed

- **WHEN** the car surface auto-zooms
- **THEN** the target SHALL be computed from the navigation engine's speed
- **Case**: `auto: AutoZoomControllerTest.firstSpeedCommitJumpsDirectlyToTarget`
