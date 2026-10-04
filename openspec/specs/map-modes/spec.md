# map-modes Specification

## Purpose

Defines the explicit map mode model (Browse / Free drive / Navigation) on the phone: three derived states with per-mode configuration presets, mode-toggle control, drive-suspension and the browse re-center rule, and the preset applied when navigation ends.

## Requirements

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

### Requirement: Browse re-center

In BROWSE mode the system SHALL show the re-center button exactly while the map is not centered on the vehicle position: the button SHALL be visible when a GPS fix is available and the vehicle's projected position is off the center of the visible map area by more than a threshold, and SHALL be hidden otherwise. The condition SHALL be derived from the current viewport and the current position — it SHALL NOT depend on remembered user interaction, and it SHALL be re-evaluated on every position fix and every viewport change. The system SHALL damp the condition so a position wandering within the GPS noise band around the center does not toggle the button: the appear edge SHALL require the off-center state to persist beyond a short dwell, and the hide edge SHALL use a smaller offset than the appear edge. The offset SHALL be a screen distance, so the threshold means the same visible displacement at every zoom level. Pressing the button SHALL set the viewport center to the current GPS position, SHALL keep the mode in BROWSE, and SHALL hide the button. BROWSE framing SHALL NOT apply the vehicle anchor presets ("Vehicle position"): those configure the driving modes only.

In BROWSE mode, the re-center button SHALL appear only when the viewport has drifted from the GPS position (the user panned or zoomed away). Pressing it SHALL center the map on the current GPS position, keep the mode in BROWSE, and hide the button.

#### Scenario: Off-center viewport at start shows the button

- **WHEN** the app starts in BROWSE with a persisted viewport that is not centered on the vehicle position
- **AND** a GPS fix is available
- **THEN** the re-center button SHALL be visible without any user interaction with the map

#### Scenario: Vehicle moves away from the center

- **WHEN** the map mode is BROWSE and the viewport is centered on the vehicle position
- **AND** the vehicle moves until its projected offset from the center exceeds the appear threshold and stays beyond it for longer than the dwell
- **THEN** the re-center button SHALL appear

#### Scenario: Map moved away from the vehicle

- **WHEN** the map mode is BROWSE
- **AND** the user pans the map, or zooms it about a point other than the center, until the vehicle's projected offset exceeds the appear threshold
- **THEN** the re-center button SHALL appear

#### Scenario: Rotating the map does not change visibility

- **WHEN** the map mode is BROWSE and the map is rotated about the viewport center
- **THEN** the vehicle's offset from the center SHALL be unchanged
- **AND** the visibility of the re-center button SHALL be unchanged

#### Scenario: Centered vehicle hides the button

- **WHEN** the map mode is BROWSE
- **AND** the vehicle's projected offset from the center is below the hide threshold
- **THEN** the re-center button SHALL NOT be visible

#### Scenario: Stationary vehicle with a noisy position does not toggle the button

- **WHEN** the map mode is BROWSE and the viewport is centered on the vehicle position
- **AND** the reported position wanders within the GPS noise band around the center over a sequence of fixes
- **THEN** the re-center button SHALL NOT become visible
- **AND** it SHALL NOT toggle while the wander stays within that band

#### Scenario: No GPS fix hides the button

- **WHEN** the map mode is BROWSE and no GPS fix is available
- **THEN** the re-center button SHALL NOT be visible
- **AND** no other BROWSE behavior SHALL change

#### Scenario: Re-center keeps browse mode

- **WHEN** the map mode is BROWSE, the re-center button is visible, and the user presses it
- **THEN** the viewport center SHALL become the current GPS position
- **AND** the mode SHALL remain BROWSE
- **AND** the re-center button SHALL disappear
- **AND** the user presses it

#### Scenario: Re-center ignores the vehicle anchor preset

- **WHEN** the map mode is BROWSE and the configured vehicle anchor preset is not the center (e.g. "Bottom center")
- **AND** the user presses the re-center button
- **THEN** the vehicle SHALL be centered at the center of the visible map area
- **AND** the anchor preset SHALL NOT be applied
- **AND** the re-center button SHALL disappear

#### Scenario: Drift shows re-center in browse

- **WHEN** the map mode is BROWSE
- **AND** the user pans or zooms away from the GPS position
- **THEN** the re-center button SHALL appear

#### Scenario: No drift, no re-center button

- **WHEN** the map mode is BROWSE
- **AND** the viewport has not drifted from the GPS position
- **THEN** the re-center button SHALL NOT be visible

### Requirement: Mode toggle button
The system SHALL provide a single mode toggle button in the right-side widget column that switches between BROWSE and FREE_DRIVE. The button SHALL be a dedicated Drive control (car icon in BROWSE, exit icon in FREE_DRIVE) — NOT the compass button, which keeps its orientation/re-center role. In BROWSE, tapping it SHALL enter FREE_DRIVE. In FREE_DRIVE, tapping it SHALL exit to BROWSE. The button SHALL be hidden during NAVIGATION.

#### Scenario: Enter free drive from browse
- **WHEN** the map mode is BROWSE
- **AND** the user taps the mode toggle button
- **THEN** the map mode SHALL become FREE_DRIVE
- **AND** the map SHALL immediately center on the current GPS position

#### Scenario: Exit free drive to browse
- **WHEN** the map mode is FREE_DRIVE
- **AND** the user taps the mode toggle button
- **THEN** the map mode SHALL become BROWSE
- **AND** the map SHALL stop following, switch to north-up orientation, and stay at the current position

#### Scenario: Mode toggle hidden during navigation
- **WHEN** the map mode is NAVIGATION
- **THEN** the mode toggle button SHALL NOT be visible

### Requirement: Mode presets
Each mode SHALL apply a configuration preset. The BROWSE preset SHALL be: follow off, auto-zoom off, north-up orientation, last persisted viewport. The FREE_DRIVE preset SHALL be: follow on, auto-zoom on, heading-up orientation, speed-based driving zoom. Entering a mode SHALL apply its preset immediately.

#### Scenario: Browse preset applied on start
- **WHEN** the app starts in BROWSE mode
- **THEN** follow SHALL be off, auto-zoom SHALL be off, and orientation SHALL be north-up

#### Scenario: Free drive preset applied on entry
- **WHEN** the user enters FREE_DRIVE
- **THEN** follow SHALL be on, auto-zoom SHALL be on, and orientation SHALL be heading-up

### Requirement: Drive suspension and reset
In FREE_DRIVE mode, any manual map interaction (pan, zoom, or rotate) SHALL suspend the drive preset: the map stops following and auto-zooming, and the re-center button SHALL appear. Pressing the re-center button SHALL restore the standard FREE_DRIVE values (follow on, auto-zoom on, heading-up, driving zoom) and SHALL hide the button.

#### Scenario: Manual pan suspends free drive
- **WHEN** the map mode is FREE_DRIVE
- **AND** the user manually pans the map
- **THEN** the drive preset SHALL be suspended (follow and auto-zoom off)
- **AND** the re-center button SHALL appear

#### Scenario: Manual zoom suspends free drive
- **WHEN** the map mode is FREE_DRIVE
- **AND** the user manually zooms the map
- **THEN** the drive preset SHALL be suspended
- **AND** the re-center button SHALL appear

#### Scenario: Manual rotate suspends free drive
- **WHEN** the map mode is FREE_DRIVE
- **AND** the user manually rotates the map
- **THEN** the drive preset SHALL be suspended
- **AND** the re-center button SHALL appear

#### Scenario: Re-center restores standard drive values
- **WHEN** the drive preset is suspended in FREE_DRIVE mode
- **AND** the user presses the re-center button
- **THEN** follow SHALL be re-enabled, auto-zoom SHALL be re-enabled, orientation SHALL return to heading-up, and the zoom SHALL return to the speed-based driving zoom
- **AND** the re-center button SHALL disappear

### Requirement: Planning session suspends the drive preset
While a route-planning session is active and the map mode is FREE_DRIVE, the drive preset SHALL be suspended exactly as a manual map interaction suspends it: follow and auto-zoom SHALL be off, and no follow-triggered camera move SHALL occur during the session. The session SHALL NOT introduce a fourth map mode — the mode SHALL remain BROWSE, FREE_DRIVE or NAVIGATION as derived. Ending the session SHALL leave the preset suspended, with the re-center affordance available to restore it.

#### Scenario: Session suspends free drive

- **WHEN** the map mode is FREE_DRIVE and the user opens a route-planning session
- **THEN** the drive preset SHALL be suspended (follow and auto-zoom off)
- **AND** the session's own camera fit SHALL be the only camera move during the session

#### Scenario: Session is not a map mode

- **WHEN** a route-planning session is active and navigation is not
- **THEN** the reported map mode SHALL still be BROWSE or FREE_DRIVE according to the follow state
- **AND** no fourth mode SHALL be reported

#### Scenario: Ending the session offers re-center

- **WHEN** a route-planning session opened from FREE_DRIVE ends
- **THEN** follow SHALL remain off
- **AND** the re-center button SHALL be visible so the driver can restore the standard drive values
