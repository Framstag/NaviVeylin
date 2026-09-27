# Delta: render-performance

## ADDED Requirements

### Requirement: Pan hot path stays off the frame budget

The single-finger pan path SHALL keep the UI thread free of work proportional to the size of the map UI state: a pan event SHALL only move display-only state and SHALL NOT copy the UI state, recompose the screen, log, or request a render per event.

- A pan event SHALL NOT write the map UI state (`MapCanvasUiState`): the live displayed center SHALL be display-only state read by the draw/overlay layer (`map-pan-zoom` — Touch-based pan)
- A pan event SHALL NOT allocate bitmaps and SHALL NOT copy pixels
- A pan event SHALL NOT emit a log line; renderer diagnostics on the request/debounce path SHALL be gated behind a debug flag that is off in release and in normal debug runs. The only permitted pan-path output is gesture-level: at most one gated line per gesture (window served / not served, committed versus displayed center) plus one gated line per unservable-window rejection (follow-up fix, 2026-09-26)
- Gesture-start side effects (follow-mode disengagement, attribution interaction) SHALL run once per gesture, not once per pan event
- A render request during a saturated pan SHALL be throttled (at most one per debounce interval) so a fast drag cannot queue renders faster than they complete

#### Scenario: 120 Hz drag does not recompose the screen per event

- **WHEN** the user drags one finger across the map at 120 Hz for two seconds, staying inside the overrun margin
- **THEN** the map UI state SHALL be written at most twice (commit on the render request and at gesture end) plus the gesture-start side effects
- **AND** the map content SHALL follow the finger on every display frame

#### Scenario: Long drag beyond the overrun margin

- **WHEN** the user drags continuously beyond the overrun margin for several seconds
- **THEN** render requests SHALL be issued no faster than the render debounce interval
- **AND** the render queue SHALL NOT grow without bound (later requests coalesce)

#### Scenario: Release build is free of pan-path logging

- **WHEN** the user pans with logging at default settings
- **THEN** no per-event log line SHALL be produced by the pan path
- **AND** gesture-level diagnostics (one line per gesture end) MAY still be produced

#### Scenario: A dead pan is diagnosable without per-event output

- **GIVEN** the pan diagnostics are enabled
- **WHEN** a drag gesture ends without the map having moved
- **THEN** the gesture-level lines SHALL state that the window was not served (or that it was served but the offset stayed zero)
- **AND** the render path SHALL state that the request was dropped as covered
- **AND** at most one line per gesture plus one per unservable-window rejection SHALL have been emitted — no per-event logging
