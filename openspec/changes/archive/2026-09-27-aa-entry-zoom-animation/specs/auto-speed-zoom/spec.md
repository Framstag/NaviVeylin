# Spec Delta: auto-speed-zoom

## MODIFIED Requirements

### Requirement: Auto-zoom uses the navigation engine's reported speed
The system SHALL use the speed value from the `onCurrentSpeed(double speedKmH)` callback as the input for zoom calculation, not raw GPS position deltas. The reported speed SHALL determine the target magnification only; it SHALL NOT determine the magnification the transition starts from. The system SHALL jump directly to the target — instead of smoothing from the default map zoom — only while no magnification is displayed yet (before the first map frame on the surface); whenever a magnification is displayed, the requirement "Auto-zoom entry transition starts from the displayed magnification" governs.

#### Scenario: Speed unknown
- **GIVEN** the navigation engine has not yet reported a speed (speed is negative)
- **AND** no map frame has been displayed on the surface yet
- **WHEN** a position estimate arrives
- **THEN** auto-zoom uses a default speed of 20 km/h to compute a reasonable initial zoom
- **AND** the magnification jumps directly to the target instead of smoothing from the default map zoom

#### Scenario: Speed unknown while a magnification is displayed
- **GIVEN** the navigation engine has not yet reported a speed (speed is negative)
- **AND** the surface displays a magnification already (e.g. the routing-sensible 15.0 the navigation view starts at)
- **WHEN** a position estimate arrives
- **THEN** the target SHALL be computed from the default speed of 20 km/h
- **AND** the displayed magnification SHALL move to that target across display frames, not in a single frame

## ADDED Requirements

### Requirement: Auto-zoom entry transition starts from the displayed magnification
When auto-zoom starts driving the viewport of a follow surface (free-driving entry, navigation start, or auto-zoom being re-enabled) while a magnification is already displayed, the system SHALL start the zoom at that displayed fractional magnification and SHALL move the displayed map toward the speed-appropriate target across display frames. The displayed magnification SHALL NOT change by the entire remaining difference in a single frame, the sequence SHALL be monotonic toward the target without overshoot, the change SHALL be applied about the follow anchor so the vehicle stays on its screen position, and the transition SHALL end on a frame at the exact (fractional) target magnification. A transition whose difference is already small enough for the overrun margin SHALL complete without initiating a full native render.

Parity: the observable behaviour is the car-side counterpart of the phone's animated zoom transitions (`smooth-zoom`); the mechanisms differ (the car surface eases the displayed magnification on the render frame, the phone in the Compose overlay), and the phone path SHALL NOT change.

#### Scenario: Entering free driving from a browse magnification
- **GIVEN** the car surface displays magnification 13.0 (the browse viewport)
- **AND** the vehicle drives at a speed whose auto-zoom target is 17.0
- **WHEN** free driving starts and auto-zoom takes over the viewport
- **THEN** no display frame SHALL change the displayed magnification by the whole 4-level difference
- **AND** the displayed magnification SHALL approach 17.0 monotonically across the frames of the transition
- **AND** the transition SHALL end on a frame rendered at exactly 17.0
- **AND** the vehicle marker SHALL stay on the resolved follow anchor screen fraction throughout

#### Scenario: Cold start without a displayed magnification
- **GIVEN** no map frame has been displayed on the surface yet
- **WHEN** the first position estimate with a speed arrives
- **THEN** the magnification SHALL jump directly to the target, because there is no displayed magnification to transition from
- **AND** the jump SHALL NOT be re-applied on later commits of the same session

#### Scenario: Auto-zoom re-enabled after a manual zoom
- **GIVEN** auto-zoom is disabled and the vehicle was manually zoomed to 16.0
- **WHEN** the driver re-enables auto-zoom and the speed-appropriate target is 13.0
- **THEN** the displayed magnification SHALL move from 16.0 toward 13.0 across display frames
- **AND** the transition SHALL begin without waiting for the next GPS fix
- **AND** the render at the target magnification SHALL be requested while the transition is still playing

#### Scenario: Entry difference inside the overrun margin
- **GIVEN** the surface displays magnification 15.0
- **AND** the speed-appropriate target is 15.2
- **WHEN** auto-zoom takes over the viewport
- **THEN** the displayed magnification SHALL end at 15.2
- **AND** no full native render SHALL be initiated for the transition

### Requirement: Entry transition is bounded in time and render requests
The auto-zoom entry transition SHALL complete within a bounded time and SHALL NOT request a full native render per display frame: its render count SHALL be bounded by the number of steps needed to cross the magnification difference. The follow display loop SHALL keep running while the transition plays, and a speed change during the transition SHALL re-target it without overshoot.

#### Scenario: Four-level entry on the car surface
- **GIVEN** a transition from displayed magnification 13.0 to 17.0
- **WHEN** the transition runs to completion
- **THEN** it SHALL complete within approximately 4 s
- **AND** the number of full native renders it causes SHALL be bounded by the number of steps that cross the difference, well below one render per display frame
- **AND** the displayed position SHALL keep gliding (the follow loop SHALL NOT stall)

#### Scenario: Speed changes while the transition plays
- **GIVEN** a transition from 13.0 toward 17.0 is in progress with the displayed magnification at 15.5
- **WHEN** the vehicle slows down and the auto-zoom convergence requests a smaller magnification (16.5, on its way to 15.0)
- **THEN** the transition SHALL take the newest requested magnification from the current displayed value
- **AND** it SHALL NOT pass the requested magnification (no overshoot of a request)
- **AND** it SHALL end on a frame at exactly the last requested magnification
