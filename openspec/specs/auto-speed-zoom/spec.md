# auto-speed-zoom Specification

## Purpose

Adjusts map magnification based on current vehicle speed during navigation so the visible area matches what the driver needs at that speed — zoomed out at highway speed, zoomed in at walking speed.

## Requirements

### Requirement: Map magnification adjusts automatically based on navigation speed
The system SHALL adjust the map magnification level based on the current vehicle speed when follow mode and auto-zoom are both active.

#### Scenario: Speed increases during navigation
- **GIVEN** an active navigation session with follow mode and auto-zoom enabled
- **WHEN** the vehicle speed increases (e.g. from city driving to highway)
- **THEN** the map magnification decreases (zooms out) according to the speed-to-magnification mapping
- **AND** the map remains centered on the vehicle position

#### Scenario: Speed decreases during navigation
- **GIVEN** an active navigation session with follow mode and auto-zoom enabled
- **WHEN** the vehicle speed decreases (e.g. from highway to city driving)
- **THEN** the map magnification increases (zooms in) according to the speed-to-magnification mapping
- **AND** the map remains centered on the vehicle position

### Requirement: Auto-zoom uses linear interpolation between speed breakpoints
The system SHALL use a configurable lookup table of speed-to-magnification pairs with linear interpolation between entries to produce smooth zoom transitions.

#### Scenario: Speed between breakpoints
- **GIVEN** a speed of 75 km/h
- **WHEN** the breakpoints are (60 km/h → mag 16) and (90 km/h → mag 13)
- **THEN** the computed magnification is approximately 14.5 (linearly interpolated)

### Requirement: Manual zoom temporarily suspends auto-zoom
The system SHALL suspend auto-zoom when the user manually changes the zoom level, and re-engage it when the speed crosses a threshold boundary.

#### Scenario: User zooms in manually
- **GIVEN** auto-zoom is active at mag 14 (highway speed)
- **WHEN** the user zooms in to mag 16 to inspect a junction
- **THEN** auto-zoom is suspended
- **AND** the map stays at mag 16 even as speed changes

#### Scenario: Speed crosses threshold boundary
- **GIVEN** auto-zoom is suspended after a manual zoom
- **WHEN** the speed changes from highway (90 km/h) to city (30 km/h), crossing a table boundary
- **THEN** auto-zoom re-engages
- **AND** the magnification adjusts to the speed-appropriate level

### Requirement: Auto-zoom can be toggled on and off
The system SHALL provide a UI control to enable or disable auto-zoom independently of follow mode.

#### Scenario: Auto-zoom disabled
- **GIVEN** follow mode is active
- **WHEN** the user disables auto-zoom
- **THEN** the map magnification stays at the current level regardless of speed changes
- **AND** follow mode continues to re-center on the vehicle position

#### Scenario: Auto-zoom re-enabled
- **GIVEN** auto-zoom is disabled
- **WHEN** the user re-enables auto-zoom
- **THEN** the magnification immediately adjusts to the speed-appropriate level
- **AND** auto-zoom suspension state is reset

### Requirement: Auto-zoom uses the navigation engine's reported speed
The system SHALL use the speed value from the `onCurrentSpeed(double speedKmH)` callback as the input for zoom calculation, not raw GPS position deltas.

#### Scenario: Speed unknown
- **GIVEN** the navigation engine has not yet reported a speed (speed is negative)
- **WHEN** a position estimate arrives
- **THEN** auto-zoom uses a default speed of 20 km/h to compute a reasonable initial zoom
- **AND** the magnification jumps directly to the target instead of smoothing from the default map zoom

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

### Requirement: Initial zoom uses routing-sensible default
The system SHALL initialize the current magnification to 15.0 instead of the default map magnification (5) when navigation is active.

#### Scenario: Navigation starts
- **GIVEN** the user starts navigation
- **WHEN** the first position estimate arrives
- **THEN** the initial zoom SHALL be approximately 15.0 (routing-sensible)
- **AND** the map SHALL NOT start at zoom level 5 (very zoomed out)

### Requirement: SPEED_ZOOM_TABLE with narrowed range
The system SHALL use a speed-to-magnification table with a range of 18→12 (6 levels), with linear interpolation between breakpoints. Walking speeds (≤6 km/h) SHALL target magnification 18–17.5, and speeds up to 60 km/h SHALL target magnification at least 16 so building names and numbers are rendered.

#### Scenario: Speed of 5 km/h
- **GIVEN** the vehicle is walking at 5 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be approximately 17.6

#### Scenario: City speed of 30 km/h
- **GIVEN** the vehicle is driving at 30 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be 16.0

#### Scenario: Suburban speed of 60 km/h
- **GIVEN** the vehicle is driving at 60 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be 16.0
- **AND** building names and numbers SHALL be rendered (magnification ≥ 16)

#### Scenario: Speed of 100 km/h
- **GIVEN** the vehicle is driving at 100 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be approximately 12.75
