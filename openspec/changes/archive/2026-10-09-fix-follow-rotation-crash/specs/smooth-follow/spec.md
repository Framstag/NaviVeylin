# Spec Delta

## ADDED Requirements

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
