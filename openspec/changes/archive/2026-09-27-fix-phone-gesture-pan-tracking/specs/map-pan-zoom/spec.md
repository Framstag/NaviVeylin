# Delta: map-pan-zoom

## MODIFIED Requirements

### Requirement: Touch-based pan

The system SHALL support single-finger drag to pan the map, with the displayed map content tracking the finger on every display frame.

- The displayed center SHALL track the finger in real time: each pan event moves the displayed window by the rotated screen delta converted with `ProjectionUtils.dragDeltaToNewCenterRotated()` using the current displayed center, the viewport rotation angle, the magnification, the viewport dimensions and the display DPI
- The conversion SHALL reduce to the north-up `dragDeltaToNewCenter()` behavior when the viewport angle is zero
- The pan SHALL be served by shifting the displayed window inside the overrun frame (see `canvas-overrun` — Overrun window shift for pan): the frame in hand SHALL be drawn at the clamped display offset, so the content follows the finger without a native render
- A pan whose window stays inside the overrun margin SHALL NOT trigger a native render, at any point of the gesture or at its end
- When the display offset reaches the overrun margin the window SHALL saturate at the margin and the system SHALL request a re-render centered on the displayed center; the displayed content SHALL resume tracking the finger once that frame is displayed
- The committed viewport center SHALL be updated from the displayed center when a render is requested and at gesture end — not on every pan event (the live center is display-only state, `map-render` — Pan hot path stays off the frame budget)
- A pan SHALL never end without committing the displayed center: the committed viewport and the displayed frame SHALL NOT disagree after the finger is lifted
- On finger lift the viewport SHALL be persisted (`viewport-persist`), independently of whether a render was requested
- Pan SHALL feel responsive: no per-event work that is proportional to the size of the UI state (no UI-state copy, no full-screen recomposition, no per-event logging) SHALL run in the pan path
- A pan SHALL never be invisible: the displayed content SHALL track the finger on every display frame, including when the frame in hand cannot serve the window — in that case the system SHALL commit the displayed center and request a render at the throttled pan cadence so the content resumes tracking the finger once that frame is displayed (follow-up fix, 2026-09-26)
- The pan path SHALL be diagnosable without per-event output: at most one gated diagnostic line per gesture (whether the window was served, and the committed versus displayed center at lift) plus one line per unservable-window rejection (follow-up fix, 2026-09-26)

#### Scenario: Pan map east

- **WHEN** user places one finger on the map and drags left
- **THEN** the displayed center moves east by the geographic distance computed via Mercator projection
- **AND** the displayed content SHALL follow the finger on every display frame while the window stays inside the overrun margin
- **AND** no native render SHALL be requested for that pan
- **WHEN** user lifts finger
- **THEN** the committed viewport center SHALL equal the displayed center
- **AND** the viewport SHALL be persisted

#### Scenario: Small pan inside the overrun margin never renders

- **WHEN** user pans 50 px right on a 1080×1920 screen with 1.2× overrun
- **THEN** the displayed content SHALL move by 50 px
- **AND** no native render call SHALL be made during or after the gesture
- **AND** the frame displayed at the end of the gesture SHALL be the overrun frame shifted by 50 px, not a scaled or re-rendered frame

#### Scenario: Pan map east on a rotated viewport

- **GIVEN** the viewport is rotated by 90 degrees clockwise
- **WHEN** user places one finger on the map and drags left
- **THEN** the viewport center moves south (the drag delta is converted using the viewport rotation angle, not north-up)
- **AND** the map follows the finger exactly as it does on a north-up viewport
- **AND** the applied display offset is rotated with the viewport (no lateral drift)

#### Scenario: Pan map south

- **WHEN** user places one finger on the map and drags up
- **THEN** the viewport center moves south by the geographic distance computed via Mercator projection
- **WHEN** the new viewport is within the overrun buffer
- **THEN** the visible sub-region is served from the overrun buffer without a native re-render

#### Scenario: Pan beyond overrun buffer triggers re-render

- **WHEN** the pan moves the displayed center beyond the overrun margin
- **THEN** the displayed content SHALL stop at the margin until the re-render lands (no empty strips, no surface-color band)
- **AND** a full native render SHALL be requested at the displayed center
- **AND** the frame from that render SHALL replace the saturated window and the content SHALL resume tracking the finger
- **AND** the displayed content SHALL NOT jump at the swap (the re-render is centered on the position the saturated window already displayed)

#### Scenario: A small pan is never invisible

- **WHEN** user pans a distance that stays inside the overrun margin and lifts the finger
- **THEN** the map content SHALL be at the panned position visually
- **AND** the committed viewport center SHALL match the displayed content
- **AND** the next render (zoom, follow re-engage, forced overlay render) SHALL NOT show a jump by the accumulated pan distance

#### Scenario: Pan during a follow-mode session

- **GIVEN** follow mode is engaged and the vehicle is moving
- **WHEN** user starts a single-finger pan
- **THEN** follow mode SHALL be disengaged once, using the displayed frame as the new framing base
- **AND** the further pan SHALL be tracked by the display window as described above
- **AND** re-engaging follow mode SHALL recenter on the vehicle with no residual display offset

#### Scenario: A drag moves the displayed content (screen level)

- **GIVEN** the map screen shows a frame with an overrun margin and no multi-touch gesture is active
- **WHEN** a single-finger drag of N pixels is delivered to the map canvas
- **THEN** the display offset derived for the drawn frame SHALL be non-zero and directed against the drag
- **AND** the frame SHALL be drawn at that offset (the displayed content moves by the drag distance)
- **AND** no native render SHALL be required for that movement

#### Scenario: A pan with no usable window still moves the map

- **GIVEN** the frame on screen cannot serve a pan window
- **WHEN** the user drags
- **THEN** the displayed center SHALL still track the finger
- **AND** a render SHALL be requested at the throttled cadence
- **AND** the map SHALL NOT remain visually frozen for the duration of the gesture

#### Scenario: A dead pan is diagnosable from the log

- **WHEN** a pan gesture produces no visible map movement
- **THEN** the gated pan diagnostics SHALL state whether the window was served and what the committed and displayed centers were
- **AND** the renderer's diagnostics SHALL show whether the render request was dropped as covered
