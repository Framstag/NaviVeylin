# Spec Delta

## MODIFIED Requirements

### Requirement: Browse re-center

In BROWSE mode the system SHALL show the re-center button exactly while the map is not centered on the vehicle position: the button SHALL be visible when a GPS fix is available and the vehicle's projected position is off the center of the visible map area by more than a threshold, and SHALL be hidden otherwise. The condition SHALL be derived from the current viewport and the current position — it SHALL NOT depend on remembered user interaction, and it SHALL be re-evaluated on every position fix and every viewport change. The system SHALL damp the condition so a position wandering within the GPS noise band around the center does not toggle the button: the appear edge SHALL require the off-center state to persist beyond a short dwell, and the hide edge SHALL use a smaller offset than the appear edge. The offset SHALL be a screen distance, so the threshold means the same visible displacement at every zoom level. Pressing the button SHALL set the viewport center to the current GPS position, SHALL keep the mode in BROWSE, and SHALL hide the button. BROWSE framing SHALL NOT apply the vehicle anchor presets ("Vehicle position"): those configure the driving modes only.

The dwell SHALL be completed on elapsed time, not on the next state change: once the off-center state has been beyond the appear threshold and the dwell has elapsed, the button SHALL become visible even though no further position fix, pan, zoom or other state change occurs. A pending dwell SHALL be discarded when the map mode leaves BROWSE or the vehicle returns inside the hide threshold, so it cannot reveal the button later.

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

#### Scenario: Off-center start at standstill reveals the button with no further event (offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent)

- **WHEN** the app is in BROWSE on a persisted viewport whose off-center offset is beyond the appear threshold
- **AND** exactly one GPS fix has been delivered and the fix-quality tier has settled
- **AND** the dwell elapses with no further position fix, no viewport change and no user interaction
- **THEN** the re-center button SHALL be visible

#### Scenario: A single flick pan reveals the button with no further event (aSingleFlickPanRevealsTheButtonWithoutAFollowUpEvent)

- **WHEN** the map mode is BROWSE and one pan gesture leaves the vehicle beyond the appear threshold
- **AND** the gesture ends and the dwell elapses with no further fix, viewport change or interaction
- **THEN** the re-center button SHALL be visible

#### Scenario: The dwell boundary is decided by elapsed time (theDwellBoundaryIsDecidedByElapsedTime)

- **WHEN** the off-center state has been beyond the appear threshold for less than the dwell
- **AND** the dwell then elapses without any other state change
- **THEN** the re-center button SHALL NOT have been visible before the dwell elapsed
- **AND** it SHALL be visible at the dwell boundary

#### Scenario: A centered viewport produces no dwell re-evaluation (noDwellEvaluationWhileTheViewportIsCentered)

- **WHEN** the map mode is BROWSE and the vehicle's offset stays below the hide threshold
- **AND** time passes well beyond the dwell
- **THEN** no dwell re-evaluation SHALL be performed

#### Scenario: Pressing re-center discards a pending dwell (pressingReCenterDiscardsThePendingDwell)

- **WHEN** the map mode is BROWSE and the dwell has elapsed so the button is visible
- **AND** the user presses the re-center button
- **THEN** the button SHALL be hidden
- **AND** no later re-evaluation SHALL reveal it while the viewport stays centered on the vehicle

#### Scenario: Leaving BROWSE discards a pending dwell and returning re-arms it (leavingBrowseDiscardsTheDwellAndReturningReArmsIt)

- **WHEN** the map mode is BROWSE with a dwell pending and the mode changes to FREE_DRIVE before the dwell elapses
- **AND** the dwell elapses while the map is not in BROWSE
- **THEN** the BROWSE re-center button SHALL NOT become visible
- **AND** after returning to BROWSE on an off-center viewport the full dwell SHALL be required again

#### Scenario: A fix that ages out suppresses the pending dwell (agedOutFixSuppressesThePendingDwell)

- **WHEN** the map mode is BROWSE with a dwell pending and the available fix is no longer usable
- **THEN** the dwell elapsing SHALL NOT make the re-center button visible
- **AND** once a usable fix is available again on an off-center viewport the full dwell SHALL be required
