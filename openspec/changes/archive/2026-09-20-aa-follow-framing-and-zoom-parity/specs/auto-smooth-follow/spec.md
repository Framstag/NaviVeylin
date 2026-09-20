# Spec Delta: auto-smooth-follow

## MODIFIED Requirements

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

## ADDED Requirements

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
