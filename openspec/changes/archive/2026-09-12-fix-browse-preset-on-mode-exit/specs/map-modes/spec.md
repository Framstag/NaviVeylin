## MODIFIED Requirements

### Requirement: Map mode model
The system SHALL maintain an explicit map mode with exactly three states: BROWSE, FREE_DRIVE, and NAVIGATION. The mode SHALL be derived from a single source of truth: navigation active → NAVIGATION; otherwise follow mode active → FREE_DRIVE; otherwise BROWSE. The app SHALL always start in BROWSE mode regardless of the mode used in a previous session.

#### Scenario: App starts in browse mode
- **WHEN** the app starts with a map installed
- **THEN** the map mode SHALL be BROWSE
- **AND** the map SHALL show the last persisted viewport with north-up orientation and follow disabled

#### Scenario: Free drive mode active
- **WHEN** the user enters free drive
- **THEN** the map mode SHALL be FREE_DRIVE
- **AND** the map SHALL follow the GPS position with auto-zoom and heading-up orientation

#### Scenario: Navigation overrides the mode
- **WHEN** turn-by-turn navigation is active
- **THEN** the map mode SHALL be NAVIGATION regardless of the follow-mode state

#### Scenario: Navigation end restores prior mode
- **WHEN** navigation stops
- **THEN** the map mode SHALL return to the mode that was active before navigation started (BROWSE or FREE_DRIVE)
- **AND** the representation preset of that restored mode SHALL be applied: north-up orientation for BROWSE (follow disabled, drive suspension cleared), follow enabled for FREE_DRIVE
- **AND** the map viewport SHALL remain at the position and zoom where navigation ended

#### Scenario: Navigation end applies browse orientation on the map
- **WHEN** navigation stops while the map renders heading-up (rotation angle equals the last driving bearing)
- **AND** the mode before navigation started was BROWSE
- **THEN** the map SHALL rotate back to north-up (0° angle)
- **AND** the map SHALL re-render so the north-up orientation is visible immediately
- **AND** the viewport center and zoom SHALL be unchanged by the rotation

#### Scenario: Navigation end to free drive keeps following
- **WHEN** navigation stops
- **AND** the mode before navigation started was FREE_DRIVE
- **THEN** the map SHALL keep following the GPS position (follow mode on)
- **AND** any drive suspension active before navigation started SHALL be restored

#### Scenario: Navigation end keeps the viewport
- **WHEN** navigation stops after the user navigated at a given zoom and position
- **AND** navigation returns the map to BROWSE
- **THEN** the map SHALL stay centered on the position where routing ended
- **AND** the zoom SHALL remain at the zoom where routing ended
- **AND** the map SHALL NOT jump back to a previously persisted viewport
