# smooth-follow Specification

## Purpose
Smooth follow-mode map scrolling on the phone renderer by extrapolating the displayed viewport between 1 Hz GPS fixes and easing corrections on fix arrival.

## Requirements

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix: `predicted = lastFix + speed * heading * (now - fixTime)`. The map SHALL be scrolled to the predicted position by blitting the front buffer each display frame. The displayed frame's viewport SHALL be centered on the anchor center of the position it was rendered for (see "Anchor-centered follow framing"), so the blitted offset carries the prediction drift only.

The prediction base SHALL be the SAME position source the follow framing renders on: the engine-filtered position while route guidance is active, the raw GPS fix otherwise. The rendered frame's center and the predicted/displayed position SHALL therefore describe the same vehicle location.

#### Scenario: Vehicle moves at constant speed between fixes

- **WHEN** a fix arrives at position P with speed 14 m/s and heading 90°, and 500 ms later no new fix has arrived
- **THEN** the displayed viewport center SHALL be approximately 7 m east of P
- **AND** the map SHALL be scrolled by blitting the front buffer, not by a full render

#### Scenario: No speed or heading available

- **WHEN** the last fix has no speed or heading (speed unknown, bearing < 0)
- **THEN** the displayed viewport SHALL remain at the last fix position until the next fix
- **AND** no extrapolation SHALL be applied

#### Scenario: Default anchors reproduce the pre-anchor framing

- **WHEN** both vehicle anchors are at their `center/center` defaults and the phone map is in follow mode
- **THEN** the displayed frame, the blitted offset and the marker position SHALL be identical to follow mode without anchor presets

#### Scenario: Prediction base matches the rendered position during guidance

- **WHEN** route guidance is active and the navigation engine reports a snapped position several meters from the raw GPS fix (junction snap, GPS jitter)
- **THEN** the prediction SHALL extrapolate from the engine position, not from the raw GPS fix
- **THEN** the rendered frame, the blit offset, and the marker SHALL stay mutually consistent (no lateral correction jump at the snap)

### Requirement: Correction easing

The system SHALL advance the displayed viewport at a bounded rate instead of easing it toward the predicted position with an unbounded gain. The displayed position SHALL advance by the extrapolation's effective speed along the smoothed direction of travel, plus a position correction toward the extrapolated target the prediction publishes — a continuous reference; the raw fix is a step function between arrivals and correcting onto it runs the map a quarter slow. That correction SHALL be clamped to a bounded fraction of the effective speed (nominally a quarter, with a floor for near-zero speeds). The displayed rate SHALL therefore stay inside approximately 0.75× to 1.25× of the effective speed at all times.

The displayed position MAY move backward along the direction of travel while a correction is active. It SHALL NOT hold still instead of correcting, and its backward contribution SHALL never exceed the same bounded fraction. While the reference is stale (no fix within the prediction's extrapolation window) the correction and the snap SHALL be skipped: a lost feed is not a moved vehicle, so the display dead-reckons at the decaying effective speed instead of being dragged back onto a fix the vehicle has long left. A difference larger than the teleport gap SHALL snap instead of being corrected, and the first fresh fix afterwards re-applies both rules. The residual position error SHALL be bounded: corrected at no more than the bounded correction rate, a sustained speed-estimate bias of a few percent leaves a sub-metre error instead of accumulating across fixes.

The earlier monotonic-forward rule (hold at the current position whenever the fix track lay behind the extrapolation) is replaced by this bound: the hold stopped the display for the whole correction and the ease then repaid the gap at up to several times the vehicle speed.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the correction SHALL be applied across display frames at the bounded rate
- **AND** the map SHALL NOT jump to the true fix in a single frame
- **AND** the displayed rate SHALL stay inside the band
- **Case**: `core: FollowDisplayRateTest.cornerOvershootStaysInBand`

#### Scenario: Fix arrival behind the display corrects without a halt

- **WHEN** a fix arrives whose position lies behind the displayed position along the direction of travel
- **THEN** the displayed position SHALL keep advancing at no less than the bounded rate below the effective speed
- **AND** it SHALL NOT hold still
- **AND** its backward contribution SHALL NOT exceed the bounded correction fraction
- **Case**: `core: FollowDisplayRateTest.backwardFixJumpKeepsRateInBand`

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction
- **Case**: `core: FollowDisplayRateTest.subPixelCorrectionIsImperceptible`

#### Scenario: Fix arrives ahead of display

- **WHEN** the true fix is ahead of the displayed position (e.g., after acceleration)
- **THEN** the displayed rate SHALL rise at most to the bounded forward fraction above the effective speed
- **AND** no full render SHALL be triggered solely by the correction while the offset stays within the overrun margin
- **Case**: `core: FollowDisplayRateTest.forwardGapNeverExceedsBound`

#### Scenario: Speed-estimate bias leaves a bounded position error

- **WHEN** the effective speed is 5 % below the vehicle's true speed for a sustained period
- **THEN** the residual position error SHALL stay below one metre
- **AND** it SHALL NOT grow across fixes
- **Case**: `core: FollowDisplayRateTest.speedBiasLeavesBoundedError`

#### Scenario: A lost fix does not reverse the display

- **WHEN** no fix arrives for several seconds while the vehicle keeps moving
- **THEN** the displayed position SHALL keep advancing forward at no less than the bounded rate below the effective speed
- **AND** it SHALL NOT reverse
- **Case**: `core: FollowDisplayRateTest.lostFixKeepsForwardMotion`

#### Scenario: A jittered fix stream never halts the display

- **WHEN** fixes arrive at 1 Hz carrying ±3 m of along-track jitter
- **THEN** no interval longer than 0.15 s SHALL show a rate below a quarter of the effective speed
- **Case**: `core: FollowDisplayRateTest.jitteredStreamHasNoHalt`

### Requirement: Display-only prediction

The system SHALL feed predicted positions only to the map display (viewport blit and marker overlay). The navigation engine SHALL receive only real GPS fixes.

#### Scenario: Predicted position never reaches navigation engine

- **WHEN** the display extrapolates the viewport between fixes
- **THEN** `NavigationController.processLocation()` SHALL NOT be called with a predicted position
- **AND** routing decisions SHALL be based only on real fixes

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only in follow mode while the vehicle is moving. The loop SHALL stop when the vehicle is stationary (speed below a threshold) or follow mode is disengaged.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position
- **AND** the blit offset SHALL be frozen (not zeroed)

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally

#### Scenario: Vehicle resumes after a stop

- **WHEN** the vehicle accelerates again after a brief stop (e.g., a traffic light) and the speed crosses back above the threshold
- **THEN** the display SHALL resume extrapolating from the frozen position
- **AND** the map and marker SHALL NOT snap (no jump at stop/resume)

### Requirement: Prediction state update

The system SHALL update the prediction state (position, speed, heading, fix time) on every GPS fix. A fix SHALL update the state without necessarily triggering a render.

#### Scenario: Fix updates prediction state only

- **WHEN** a new fix arrives while the predicted position is still inside the overrun region
- **THEN** the prediction state SHALL be updated to the new fix
- **AND** no full render SHALL be initiated
- **AND** the displayed viewport SHALL NOT be re-centered on the fix

#### Scenario: Fix arrives during a zoom animation

- **WHEN** a new fix arrives while an auto-zoom animation is playing
- **THEN** the zoom animation SHALL continue from the displayed position (not from a re-centered raw fix)
- **AND** the frame SHALL NOT jump between zoom-only and center re-commits

### Requirement: Anchor-centered follow framing

In phone follow mode the map SHALL render each frame with a viewport centered on the **anchor center** of the position the frame is rendered for: the geographic point that projects the vehicle to the configured anchor screen fraction under the current map rotation. The vehicle's geographic position SHALL therefore project to the anchor inside the rendered frame, and the anchor SHALL be applied exactly once — never again as an additional shift of the blitted frame or of the marker projection.

The active anchor SHALL be the routing anchor while turn-by-turn route guidance is active and the free-driving anchor otherwise; each is one of the 15 presets of the shared vehicle-position grid.

The follow blit offset (the prediction drift) SHALL remain inside the overrun margin of the rendered frame, so no uncovered strip of the surface background is ever visible and no part of the map content is pushed off-screen. Re-centering (the re-center control and follow re-engage) SHALL commit the anchor-centered viewport, so the configured anchor is restored without a snap.

#### Scenario: Vehicle projects to the active anchor

- **WHEN** the phone map is in follow mode with a non-center anchor (for example bottom-center) for the active mode
- **THEN** the vehicle's geographic position SHALL project to that anchor screen fraction in the displayed frame
- **AND** the map content around the vehicle SHALL be visible at the anchor (no grey/black uncovered strip, no content pushed off-screen)

#### Scenario: Every preset stays inside the overrun buffer

- **WHEN** the user selects any of the 15 anchor presets and drives in follow mode, in north-up and in heading-up orientation
- **THEN** the applied blit offset SHALL NOT exceed the overrun margin of the rendered frame
- **AND** the visible map SHALL cover the full surface with map content

#### Scenario: Anchor follows guidance state

- **WHEN** the user configured distinct routing and free-driving anchors
- **AND** route guidance starts or stops
- **THEN** the follow framing SHALL switch between the routing and the free-driving anchor on the next position update

#### Scenario: Anchor restored after manual pan or re-center

- **WHEN** the user pans the map (follow disengaged) and re-engages follow, or activates the re-center control
- **THEN** the next committed frame SHALL be anchor-centered on the current position
- **AND** the vehicle SHALL reappear at the configured anchor without a framing snap

#### Scenario: Anchor held under heading-up rotation

- **WHEN** the map is rotated (heading-up follow or a manual rotation) and the vehicle moves in follow mode
- **THEN** the render target SHALL be re-derived from the rotated viewport so the vehicle keeps projecting to the anchor screen fraction

### Requirement: Vehicle position anchor in follow mode

The phone map SHALL keep the vehicle marker in follow mode at the configured anchor position instead of at the screen center. The active anchor depends on the driving state: the routing anchor while turn-by-turn route guidance is active, the free-driving anchor otherwise. Each anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the screen width, vertical 10/50/90% of the screen height); the default for both is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the current map rotation.

The regions the phone's own chrome covers SHALL be measured from the chrome the surface **currently composes**: the right-side widget column reports its measured width in navigation exactly as it does in browse, including on a surface whose chrome band composes while navigation is already active — the car-session resume composes the band from scratch, with no remembered measurement (`map-canvas-screen` — The phone map canvas is suspended while a car session is active; `guidelines/MapRendering.md`, section 18), and the band publishes its width before the anchor is resolved. A right-edge preset SHALL therefore resolve left of the column in every composition of the band, never only in the ones that passed through browse first (measured on the host, 2026-10-09: the browsing column's width already resolved `MIDDLE_FAR_RIGHT` to `fx=0.7773722627737226`, while the same preset stayed at the raw `fx=0.9` after a car-session resume that composed the band during navigation).

#### Scenario: Default anchors reproduce today's framing

- **GIVEN** both anchors are at their defaults (center/center)
- **WHEN** the phone map is in follow mode
- **THEN** the vehicle marker projects to the center of the map canvas
- **AND** the map framing is identical to follow mode without anchor presets
- **AND** the phone's navigation overlays are measured (next-turn card, routing-status card, right widget column)
- **THEN** the vehicle marker projects to the EXACT center of the canvas — the default preset collides with no overlay region, so it resolves to (50%, 50%) rather than to the center of the reduced visible area

#### Scenario: Routing anchor active during guidance

- **GIVEN** the user configured a distinct routing anchor
- **WHEN** turn-by-turn route guidance is active and the map is in follow mode
- **THEN** the vehicle marker stays at the routing anchor position

#### Scenario: Free-driving anchor active without guidance

- **GIVEN** the user configured a distinct free-driving anchor
- **WHEN** no route guidance is active and the map is in follow mode (browsing/free driving)
- **THEN** the vehicle marker stays at the free-driving anchor position

#### Scenario: Anchor kept under map rotation

- **WHEN** the map is rotated (navigation or free-form orientation) and the vehicle moves in follow mode
- **THEN** the map render target shifts so the vehicle marker keeps projecting to the active anchor position

#### Scenario: Anchor restored after manual pan or recenter

- **WHEN** the user pans the map (follow disengaged) and re-engages follow, or activates the recenter control
- **THEN** the map returns to the anchor-centered framing without a snap

#### Scenario: Bottom anchor stays visible above the routing status card

- **GIVEN** the phone is navigating and the routing-status card covers the bottom of the canvas
- **AND** the routing anchor is bottom-center (50%, 90% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved vertical fraction SHALL be above the routing-status card, clear of the marker footprint and padding
- **AND** the resolved horizontal fraction SHALL remain exactly 50% (the card covers no horizontal position of the marker, so no horizontal move is applied)
- **AND** the vehicle marker SHALL be fully visible, not covered by the card

#### Scenario: Top anchor stays visible below the turn card

- **GIVEN** the phone is navigating and the next-turn card covers the top of the canvas
- **AND** the routing anchor is top-center (50%, 10% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved vertical fraction SHALL be below the turn card, clear of the marker footprint and padding
- **AND** the resolved horizontal fraction SHALL remain exactly 50%
- **AND** the vehicle marker SHALL be fully visible, not covered by the card

#### Scenario: Right anchor stays clear of the widget column only when covered

- **GIVEN** the widget column covers the right edge of the canvas
- **AND** the anchor is at 90% width (its marker would fall inside the column)
- **WHEN** the map is in follow mode
- **THEN** the resolved horizontal fraction SHALL be left of the widget column, clear of the marker footprint and padding
- **AND** the vehicle marker SHALL NOT be covered by the column

#### Scenario: Non-covered preset keeps its exact fraction

- **GIVEN** the widget column covers the right edge of the canvas
- **AND** the anchor is at 70% width (its marker stays clear of the column)
- **WHEN** the map is in follow mode
- **THEN** the resolved horizontal fraction SHALL equal exactly 70% — an uncovered preset is not moved by the mere presence of an overlay
- **AND** the vehicle marker SHALL keep the preset's screen position

#### Scenario: Corner preset moves on both axes

- **GIVEN** the phone is navigating (routing-status card covers the bottom, widget column covers the right edge)
- **AND** the routing anchor is bottom-right (70%, 90% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved fraction SHALL move up above the routing-status card AND left of the widget column
- **AND** the vehicle marker SHALL be fully visible, clear of both the card and the column

#### Scenario: Collision uses the marker footprint and padding

- **GIVEN** a preset whose center still clears an overlay region but whose marker footprint plus padding would overlap it
- **WHEN** the map is in follow mode
- **THEN** the preset SHALL be treated as covered and SHALL move until the footprint and padding are clear of the region
- **AND** the resolved position SHALL leave at least the footprint and padding between the marker and the overlay edge

#### Scenario: No overlay measured means the preset fraction

- **WHEN** no overlay region is measured
- **THEN** every resolved anchor SHALL equal the preset fraction
- **AND** the framing SHALL be identical to a surface without overlay clearance

#### Scenario: Phone anchor value is independent of Android Auto

- **GIVEN** the phone's routing anchor and the car's routing anchor are both configured
- **WHEN** the driver changes the anchor on one surface
- **THEN** only that surface's stored value changes
- **AND** the other surface keeps its own anchor

#### Scenario: Car falls back to the phone anchor until it has its own value

- **GIVEN** settings written before the per-surface split (no car-specific anchor stored)
- **WHEN** Android Auto reads its anchor
- **THEN** it SHALL use the value the phone stored for that mode
- **AND** once an anchor is chosen on the car, the car SHALL keep that value independently

#### Scenario: A band that composes during navigation still measures its column

- **GIVEN** the phone map is suspended by a car session, navigation is started on the car, and the driver asks for the map back on the phone
- **WHEN** the chrome band composes from scratch with navigation active and the map is in follow mode with a right-edge anchor preset
- **THEN** the navigation-time right-side widget column SHALL report its measured width like the browsing one, so the screen publishes a right inset for it
- **AND** the resolved horizontal fraction SHALL stay left of the column instead of resolving to the raw preset
- **AND** the browsing band SHALL keep publishing the column's width unchanged

### Requirement: Single resolved anchor across render, blit and marker

In phone follow mode the follow pipeline SHALL use one anchor value in every stage that positions the vehicle: the frame render target, the follow blit offset, and the marker projection SHALL all use the **resolved** anchor screen fraction (the preset after collision resolution against the surface's own overlays), never the raw preset when the two differ.

- The blit offset (`followOffset`) SHALL be computed against the same resolved fraction the frame was rendered with, so the displayed position's map content lands on the marker at the anchor
- The blit offset SHALL therefore stay a pure prediction drift (inside the overrun margin) while the anchor is applied exactly once, in the render target
- The vehicle marker SHALL stay glued to the map content it represents; the marker and the road SHALL NOT drift apart by the anchor delta while the frame lags behind the prediction (see gps-location-marker — marker shares one projection with the content)
- The re-centering path and follow re-engage SHALL commit the anchor-centered viewport with the same resolved anchor

#### Scenario: Navigation overlays resolve the anchor away from the raw preset

- **WHEN** the user navigates with a routing status card at the bottom, the preset bottom-center resolves from raw `fy = 0.9` to a resolved `fy` inside the visible area above the card, and the vehicle moves in follow mode
- **THEN** the displayed position's map content SHALL project to the resolved anchor fraction
- **AND** the vehicle marker SHALL project to the same resolved fraction, on the map content that is the vehicle's road position
- **AND** the blit offset SHALL NOT exceed the overrun margin while the display and the rendered frame are aligned (no re-render churn)

#### Scenario: Marker stays on the road at a non-resolved preset

- **WHEN** the raw preset differs from the resolved anchor (collision-remapped above an overlay) and the displayed frame lags the prediction by a drift up to the overrun margin
- **THEN** the marker SHALL land on the map content of the displayed position
- **AND** the marker SHALL NOT sit ahead of the road in the driving direction by the anchor delta

### Requirement: Non-negative overrun margin

The overrun margin used to clamp the follow display offset SHALL never be negative, on either axis, in
either orientation, and on either surface.

#### Scenario: Both orientation transitions are safe

- **WHEN** the canvas changes from portrait to landscape and from landscape to portrait while follow mode is active
- **THEN** every offset computation SHALL return a finite offset on both axes and SHALL never throw

#### Scenario: A frame with overrun margin keeps today's clamp

- **WHEN** the displayed frame and the canvas share an orientation and the frame has an overrun margin
- **THEN** the offset SHALL still clamp to that frame's overrun margin exactly as before
- **AND** an in-margin drift SHALL remain unclamped

### Requirement: A frame from the previous orientation yields a zero offset instead of an error

When the rendered frame's bitmap gives no overrun margin on an axis — the state while the displayed
frame still carries the previous orientation and the canvas already has the new one — the follow
display offset on that axis SHALL be zero and SHALL be reported as clamped, so the surface requests a
frame in the current orientation rather than failing. Computing the offset SHALL never fail.

#### Scenario: The phone is rotated while follow mode is active

- **WHEN** the phone is rotated (portrait to landscape or landscape to portrait) during a followed drive
- **AND** the displayed frame still has the previous orientation while the canvas already has the new one
- **THEN** computing the follow display offset SHALL NOT fail
- **AND** the offset on the axis without overrun margin SHALL be exactly zero and SHALL be reported as clamped
- **AND** the surface SHALL request a frame in the current orientation

### Requirement: Follow frame refresh before the overrun margin is exhausted

The system SHALL request the follow-mode re-render when the displayed drift reaches a threshold inside the overrun margin, not only once the drift is already clamped there. The threshold SHALL leave room to absorb one render at the current speed. At most one refresh request SHALL be outstanding at a time, the render debounce SHALL still bound the request rate, and the clamp SHALL remain the last resort.

#### Scenario: Drift threshold requests the frame before the clamp

- **WHEN** the displayed drift reaches the refresh threshold inside the overrun margin (nominally 55 % of the margin)
- **THEN** the follow re-render SHALL be requested while the drift is still inside the margin
- **AND** the displayed offset SHALL remain inside the overrun margin while that render is in flight
- **Case**: `core: FollowRefreshPolicyTest.thresholdRequestsBeforeMargin`

#### Scenario: A steady drive never lets the drift reach the margin

- **WHEN** the phone drives at a steady speed and the render latency is below the margin the threshold leaves
- **THEN** the drift SHALL NOT reach the overrun margin
- **AND** the display SHALL NOT be clamped
- **Case**: `core: FollowRefreshPolicyTest.steadyDriveNeverClamps`

#### Scenario: Refresh requests stay inside the render debounce

- **WHEN** the drift stays beyond the threshold for several seconds
- **THEN** no more than one refresh request SHALL be outstanding
- **AND** the request rate SHALL stay inside the render debounce window
- **Case**: `core: FollowRefreshPolicyTest.singleRequestOutstanding`

#### Scenario: A render slower than the remaining margin still clamps

- **WHEN** the render latency exceeds the margin left at the threshold
- **THEN** the drift SHALL clamp at the overrun margin as before
- **AND** the display SHALL still request the next frame
- **Case**: `core: FollowRefreshPolicyTest.slowRenderClampsAsLastResort`

### Requirement: Stop decision against a current base

The prediction SHALL decide its stopped state against a base position no older than the fix being applied. A base that has not moved because it was never refreshed SHALL NOT declare a stop.

#### Scenario: A stale base does not declare a stop

- **WHEN** consecutive updates carry the same base position because the base source was not refreshed within the hold window
- **THEN** the prediction SHALL NOT report the vehicle as stopped
- **AND** the displayed position SHALL keep advancing
- **Case**: `core: FollowPredictionTest.staleBaseDoesNotDeclareStop`

#### Scenario: A genuine stop still holds the display

- **WHEN** refreshed bases stay within the movement threshold for the hold window while the vehicle is stationary
- **THEN** the prediction SHALL report the vehicle as stopped
- **AND** the display SHALL hold at its current position
- **Case**: `core: FollowPredictionTest.`"holds position when the fix has not moved"
