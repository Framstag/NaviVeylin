# auto-smooth-follow Specification

## Purpose
Smooth follow-mode map scrolling on the Android Auto map renderer by adding an overrun buffer with sub-region blit, extrapolating the displayed viewport between 1 Hz GPS fixes, and easing corrections on fix arrival.

## Requirements

### Requirement: Overrun render buffer

The system SHALL render the AA map at a size larger than the car surface (approximately 1.2x) and extract the visible region for display, so viewport changes within the overrun region can be served without a full native render.

#### Scenario: Follow-mode render uses overrun size

- **WHEN** the AA renderer performs a full render in follow mode
- **THEN** the render SHALL be at overrun size (approximately 1.2x the surface size)
- **AND** the visible region SHALL be extracted for display

### Requirement: Sub-region blit on viewport change

The system SHALL serve a viewport change within the overrun region by drawing the shifted overrun buffer to the surface instead of performing a full native render. This SHALL hold for every vehicle anchor preset: the blit offset SHALL be derived from the displayed vehicle position within the frame, never from the frame center, since the overrun buffer is rendered anchor-centered and a frame center is not a point of the rendered bitmap.

#### Scenario: Small GPS move served by blit

- **WHEN** the viewport center moves by a delta that stays within the overrun region
- **THEN** the surface SHALL be updated by drawing the overrun buffer shifted by the delta
- **AND** no full native render SHALL be initiated

#### Scenario: Viewport change exits overrun region

- **WHEN** the viewport center moves beyond the overrun region
- **THEN** a full native render SHALL be initiated at the new center
- **AND** the overrun buffer SHALL be refreshed

#### Scenario: Follow-mode fix re-anchor served by blit

- **WHEN** a GPS fix re-anchors the follow frame and the new frame center stays within the overrun region
- **THEN** the surface SHALL be updated by blitting the overrun buffer
- **AND** no full native render SHALL be initiated by that fix
- **AND** the frame SHALL hold the vehicle at the resolved anchor fraction

#### Scenario: Non-center anchor preset does not force a full render

- **WHEN** the follow anchor resolves away from the surface center (e.g. a bottom-row preset)
- **AND** the frame center moves by a delta that stays within the overrun region
- **THEN** the blit offset SHALL remain inside the overrun margin
- **AND** the frame SHALL be served by a blit instead of a full native render

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix. The map SHALL be scrolled to the predicted position by blitting the overrun buffer each display frame. The follow render target (the anchor center a commit frames the map on) SHALL be the anchor center of the DISPLAYED position — the same point the extrapolation loop advances — and never the raw fix: a GPS fix SHALL update the prediction state only and SHALL NOT move the render target, so a fix commits a center-only change that the overrun blit can serve. The raw fix SHALL be the fallback only while no displayed position exists yet.

#### Scenario: Vehicle moves at constant speed between fixes

- **WHEN** a fix arrives at position P with speed 14 m/s and heading 90°, and 500 ms later no new fix has arrived
- **THEN** the displayed viewport center SHALL be approximately 7 m east of P
- **AND** the map SHALL be scrolled by blitting the overrun buffer, not by a full render

#### Scenario: No speed or heading available

- **WHEN** the last fix has no speed or heading (speed unknown, bearing < 0)
- **THEN** the displayed viewport SHALL remain at the last fix position until the next fix
- **AND** no extrapolation SHALL be applied

#### Scenario: Fix arrival does not step the map

- **WHEN** a GPS fix arrives while the displayed position is ahead of it by the extrapolation lead
- **AND** the follow render target is re-anchored on the fix
- **THEN** the map content SHALL NOT jump by the lead when the re-anchored frame is committed
- **AND** the render target SHALL be the anchor center of the displayed position, so the commit changes the center by the display's own advance only

#### Scenario: Re-anchor below the heading deadband is served by a blit

- **WHEN** a GPS fix arrives and the heading changed by less than the heading deadband
- **THEN** the rotation SHALL NOT be re-committed
- **AND** the frame SHALL be served by an overrun blit instead of a full native render

#### Scenario: Every drawn frame places the display on the anchor

- **WHEN** a frame is drawn, whether served by an overrun blit or freshly rendered
- **THEN** its placement SHALL put the current displayed position on the resolved anchor fraction
- **AND** a freshly rendered frame SHALL NOT be drawn unshifted while the display has advanced past the frame's own anchor position (the native render takes 25-300 ms, during which the display keeps moving) — that leaves the whole scene, map and marker, the advance away from the previous frame and the next blit tick moves it back
- **AND** the content's placement SHALL use the same (rounded, not truncated) offset the overlays use

### Requirement: Heading commit deadband

The system SHALL re-commit the follow-mode viewport rotation only when the smoothed heading changed by more than a small deadband since the committed rotation. Below the deadband the previous rotation SHALL stand and the fix SHALL commit a center-only change, so the frame can be served by an overrun blit. Above it the rotation SHALL be committed with the fix as today. The deadband SHALL bound the heading lag to at most its own value and SHALL NOT change the heading-up semantics (the map still rotates to the direction of travel).

#### Scenario: Small heading change keeps the committed rotation

- **WHEN** a fix arrives whose smoothed heading differs from the committed rotation by less than the deadband
- **THEN** the viewport rotation SHALL remain unchanged
- **AND** the fix SHALL NOT force a full native render

#### Scenario: Heading change beyond the deadband commits the rotation

- **WHEN** the smoothed heading differs from the committed rotation by more than the deadband
- **THEN** the rotation SHALL be committed with the fix
- **AND** the map SHALL be rendered at the new rotation

#### Scenario: Heading-up semantics unchanged

- **WHEN** the vehicle turns and the heading keeps changing beyond the deadband
- **THEN** the map SHALL follow the direction of travel as before
- **AND** the heading lag SHALL stay within the deadband

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival over approximately 200-300 ms instead of snapping. The easing SHALL absorb extrapolation drift caused by curves and acceleration.

The correction SHALL be forward-only: the displayed position SHALL NEVER move backward along the direction of travel on fix arrival. When the true fix (or the predicted target derived from it) lies behind the current displayed position, the display SHALL hold at its current position until the extrapolation from the new fix advances beyond it. The residual forward lead SHALL be bounded (at most the eased lead of one fix interval) and SHALL NOT accumulate across fixes.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix over ~200-300 ms
- **AND** the map SHALL NOT jump to the true fix in a single frame

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction

#### Scenario: Fix arrival behind the display holds

- **WHEN** a fix arrives whose position lies behind the current displayed position along the direction of travel (the extrapolated prediction overshot into a curve or a deceleration)
- **THEN** the displayed position SHALL hold at its current position
- **AND** the display SHALL resume advancing once the extrapolation from the new fix passes the held position

#### Scenario: Forward-only across consecutive fixes

- **WHEN** the vehicle drives a series of curves at highway speed and a fix arrives every second
- **THEN** the displayed position SHALL never decrease its progress along the direction of travel between fixes
- **AND** the per-fix backward correction slide SHALL NOT occur (no "moved then jumped back" sawtooth)

### Requirement: Display-only prediction

The system SHALL feed predicted positions only to the map display (viewport blit and marker overlay). The navigation engine SHALL receive only real GPS fixes.

#### Scenario: Predicted position never reaches navigation engine

- **WHEN** the display extrapolates the viewport between fixes
- **THEN** the navigation engine SHALL NOT be called with a predicted position
- **AND** routing decisions SHALL be based only on real fixes

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only when the renderer is resumed, follow mode is active, and the vehicle is moving. The loop SHALL respect the surface lifecycle (pause, surface destroyed, surface failure) and the shared surface lock across renderers.

While the gate is closed (vehicle stopped), the displayed position and the vehicle marker SHALL remain frozen at the last displayed position — the renderer SHALL NOT reset the displayed position to the last raw fix and SHALL NOT re-anchor the viewport on stationary fixes. A stationary fix may re-seed the displayed position to the fix at most once per moving-to-stopped transition, after which the display SHALL stay frozen until movement resumes and the loop advances from the frozen position.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position
- **AND** the vehicle marker SHALL remain at the displayed position (SHALL NOT fall back to the last raw fix)
- **AND** the map content SHALL NOT re-frame on stationary GPS-jitter fixes

#### Scenario: Vehicle resumes after a stop

- **WHEN** the vehicle starts moving again after a stop
- **THEN** the display SHALL continue advancing from the frozen position toward the prediction
- **AND** the map SHALL NOT snap to the new fix or to a re-initialized display position

#### Scenario: Screen paused or surface destroyed

- **WHEN** the owning screen is stopped or the surface is destroyed
- **THEN** the extrapolation loop SHALL stop
- **AND** no surface lock SHALL be attempted

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally

### Requirement: Fix feed from follow-mode screens

The system SHALL feed every GPS fix's speed, heading and timestamp to the map renderer from every follow-mode car screen (browse map, free driving, navigation view) so the extrapolation display loop can run between fixes. When the fix carries no GPS speed or bearing, the screen SHALL derive them from the movement between consecutive fixes before feeding the renderer.

#### Scenario: Routing view feeds speed to the renderer

- **WHEN** a GPS fix arrives while the navigation view is active in follow mode
- **THEN** the screen passes the fix speed, heading and receipt time to the renderer's fix API
- **AND** the extrapolation loop runs and the map glides toward the predicted position between fixes

#### Scenario: Speed derived from movement when GPS speed is missing

- **WHEN** a fix arrives with no GPS speed (speed unknown) while a follow-mode screen is active
- **THEN** the screen computes the speed from the distance travelled since the previous fix over the fix interval
- **AND** passes that derived speed to the renderer so the extrapolation loop keeps running

#### Scenario: Bearing derived from movement when GPS bearing is missing

- **WHEN** a fix arrives with no GPS bearing while a follow-mode screen is active
- **THEN** the screen uses the movement direction between the consecutive fixes as the heading for the prediction
- **AND** keeps the last effective bearing when the fix moved too little to yield a direction

### Requirement: Single resolved anchor in the AA follow blit

In AA follow mode the follow blit offset SHALL be computed against the same **resolved** anchor screen fraction the AA frame render target uses — the `clampAnchorOutOfPane` result against the host pane band (left in LTR, right in RTL) — never the raw preset.

- The blit offset (`FollowPrediction.displayOffsetPx`) SHALL receive the resolved fraction at both blit sites (the extrapolation-loop blit and `renderFrame`'s blit path), so the pane-band presets are held at their resolved screen fraction
- With the frame rendered anchor-centered on the resolved fraction and the blit computed against it, the blit SHALL stay a pure prediction drift inside the overrun margin — never permanently clamped for a pane-band preset (a permanently clamped offset converts every tick into a full native render instead of a sub-region blit)
- The AA marker SHALL keep riding the blitted content (marker projection subtracts the same blit offset); the change SHALL NOT alter the marker-to-content glue

#### Scenario: Far-left preset against an LTR host pane

- **WHEN** the user selects a far-left preset (fx 0.1), the host draws its pane on the left (LTR) covering 40% of the surface width, and follow mode drives the map in navigation or free driving
- **THEN** the frame SHALL render anchor-centered on the resolved fraction (moved out of the pane band)
- **AND** the blit offset SHALL be computed against that same resolved fraction
- **AND** the blit offset SHALL NOT be clamped while the display and the rendered frame are aligned (no full-render churn)
- **AND** the vehicle marker SHALL stay on the map content at the resolved screen fraction

#### Scenario: Far-right preset against an RTL host pane

- **WHEN** the user selects a far-right preset (fx 0.9) and an RTL host draws its pane on the right
- **THEN** the far-right preset SHALL resolve out of the pane band and the blit SHALL use the resolved fraction exactly as in the LTR case
- **AND** presets outside the band (including the default center) SHALL keep their exact fraction and unchanged behavior
