# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
