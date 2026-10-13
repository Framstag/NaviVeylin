# Spec Delta

## MODIFIED Requirements

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival instead of snapping, and the easing SHALL be bounded: the displayed position SHALL advance by the effective speed along the smoothed direction of travel plus a position correction clamped to a bounded fraction of that speed (the shared follow primitive of `smooth-follow` — "Correction easing"). The displayed rate SHALL stay inside approximately 0.75× to 1.25× of the effective speed at all times, and the residual position error SHALL be bounded because it is corrected at no more than the bounded rate.

The correction SHALL NOT be forward-only. When the true fix (or the predicted target derived from it) lies behind the current displayed position the display SHALL keep advancing at the bounded rate below the effective speed and SHALL NOT hold still; its backward contribution SHALL NOT exceed the same bounded fraction and SHALL NOT accumulate across fixes.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix across display frames
- **AND** the map SHALL NOT jump to the true fix in a single frame
- **Case**: `auto: AutoMapRendererTest.cornerOvershootStaysInBand`

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full native render SHALL be triggered solely by the correction
- **Case**: `auto: AutoMapRendererTest.subPixelCorrectionIsImperceptible`

#### Scenario: Fix arrival behind the display holds

- **WHEN** a fix arrives whose position lies behind the current displayed position along the direction of travel (the extrapolated prediction overshot into a curve or a deceleration)
- **THEN** the displayed position SHALL keep advancing at no less than the bounded rate below the effective speed
- **AND** it SHALL NOT hold at its current position
- **Case**: `auto: AutoMapRendererTest.backwardFixDoesNotHaltCarDisplay`

#### Scenario: Forward-only across consecutive fixes

- **WHEN** the vehicle drives a series of curves at highway speed and a fix arrives every second
- **THEN** the displayed rate SHALL stay inside the band on every interval
- **AND** the backward contribution SHALL NOT exceed the bounded correction fraction on any fix
- **Case**: `auto: AutoMapRendererTest.curveSequenceStaysInBand`

## ADDED Requirements

### Requirement: Shared follow bounds on the car surface

The car follow display SHALL obey the same bounded-rate correction and stop-decision rules as the phone, both being properties of the shared follow primitives. The car re-anchors its render target on every fix, so it requires no drift-threshold refresh of its own.

#### Scenario: The car inherits the bounded correction

- **WHEN** the car follow display receives a fix whose position lies behind its displayed position
- **THEN** the correction SHALL be bounded exactly as on the phone
- **AND** no additional car-side bound SHALL be required to hold the rate band
- **Case**: `auto: AutoMapRendererTest.sharedCorrectionBoundAppliesToCar`

#### Scenario: The car inherits the stop-decision guard

- **WHEN** the car's base position has not been refreshed within the hold window
- **THEN** the car display SHALL NOT treat the vehicle as stopped
- **Case**: `auto: AutoMapRendererTest.staleBaseDoesNotStopCarDisplay`
