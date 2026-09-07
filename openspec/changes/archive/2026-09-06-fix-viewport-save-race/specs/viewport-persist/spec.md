## ADDED Requirements

### Requirement: Reject invalid viewport states on save

The system SHALL NOT persist a viewport state whose coordinates or magnification are invalid (NaN, infinity, latitude outside [-90, 90], longitude outside [-180, 180], non-positive or NaN magnification, NaN angle), so an uninitialized viewport can never overwrite a previously restored one on disk.

#### Scenario: NaN center is not saved

- **WHEN** a view-change event or lifecycle save carries a viewport with NaN or infinite coordinates
- **THEN** the viewport file is not written or overwritten
- **THEN** a warning is logged

#### Scenario: Out-of-range coordinates are not saved

- **WHEN** a view-change event or lifecycle save carries a latitude outside [-90, 90] or a longitude outside [-180, 180]
- **THEN** the viewport file is not written or overwritten

#### Scenario: Invalid save does not clobber a valid file

- **WHEN** a valid viewport file exists on disk and an invalid viewport state is saved
- **THEN** the existing file keeps its previous valid content

### Requirement: Apply restored viewport before wiring the view-change listener

The system SHALL apply the restored viewport to the map state before the view-change listener (which persists every completed render) is wired, so no render triggered during map initialization can persist an uninitialized viewport over the restored one.

#### Scenario: Restore survives an early render

- **WHEN** the map screen starts and a render completes before the restored viewport is applied
- **THEN** the persisted viewport file still contains the restored center and magnification

#### Scenario: Restore survives an early screen-size render

- **WHEN** the map screen starts and the composable reports its size while the viewport restore is still in progress
- **THEN** no render with the default viewport is submitted
- **THEN** the persisted viewport file still contains the restored center and magnification
