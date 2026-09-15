## MODIFIED Requirements

### Requirement: Smooth zoom transitions
The system SHALL commit the fractional magnification target computed from the speed-to-magnification table (and turn/curve/post-turn floors) without rounding it to an integer level, and SHALL move the zoom toward that target with a distance-proportional step — a constant fraction (gain 0.3) of the remaining gap, capped at 0.5 magnification levels per position update — using fractional values for smooth convergence over multiple seconds. Because the step strength depends on the "is → target" distance, the zoom moves quickly when far away and slows down when near; small target jitter from speed noise is damped to sub-threshold motion instead of being chased (no zoom "pumping"). When the difference between the target and the currently displayed magnification is smaller than a small epsilon (≈ 0.05 levels, i.e. the speed is effectively constant), the system SHALL make no zoom change and SHALL NOT trigger a re-render.

#### Scenario: Speed changes abruptly after tunnel
- **GIVEN** the vehicle exits a tunnel and speed jumps from unknown to 60 km/h
- **WHEN** position estimates arrive at ~1/sec
- **THEN** the magnification changes by at most 0.5 levels per update toward the target
- **AND** the transition takes multiple seconds (~4 s for a 2-level difference) instead of happening instantly

#### Scenario: Fractional target is committed, not rounded
- **GIVEN** the vehicle drives steadily at 75 km/h (interpolated target magnification ≈ 14.5)
- **AND** the current magnification is 16.0
- **WHEN** position estimates arrive at ~1/sec
- **THEN** the committed magnification sequence is approximately 15.55, 15.235, 15.0145, … (proportional decay, capped at 0.5 levels per update) and settles inside the epsilon deadband around 14.5
- **AND** no committed magnification SHALL be rounded to a whole level (the display stops near 14.5, not 15.0 or 14.0)
- **AND** the sequence SHALL be monotonic toward the target — no overshoot back and forth (no pumping)

#### Scenario: Constant speed causes no zoom churn
- **GIVEN** the vehicle speed is steady and the target magnification equals the currently displayed fractional magnification (e.g. both 14.5)
- **WHEN** another position estimate arrives
- **THEN** the magnification SHALL NOT change
- **AND** no re-render SHALL be triggered for the zoom
