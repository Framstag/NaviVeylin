# Delta: canvas-overrun

## MODIFIED Requirements

### Requirement: Sub-region blit for pan

When the displayed center moves within the overrun buffer bounds, the system SHALL position the displayed overrun frame so that the requested window is visible, instead of copying a sub-region bitmap or triggering a native re-render.

The requirement keeps its historical name: "serving a pan from the overrun buffer" is now a display-window shift, not a per-event bitmap copy.

- The display offset SHALL be the screen-space delta from the frame's own viewport center to the displayed center, rotated by the viewport angle (the same rotation the previous sub-region blit applied), and clamped to the overrun margin
- The offset SHALL be derived from the frame currently displayed, so the offset and the bitmap it describes can never come from different frames
- The frame delivered to the UI for a pan SHALL be the overrun frame itself together with its display offset — no per-event sub-region bitmap SHALL be created and no pixels SHALL be copied per pan event
- If the requested window stays inside the overrun buffer (minus the display slack), the system SHALL NOT request a native re-render
- If the requested window extends beyond the overrun buffer, the displayed window SHALL saturate at the margin and the system SHALL trigger a re-render centered on the displayed center
- Serving a pan from the overrun frame SHALL complete within a single frame (no async wait, no bitmap work proportional to the canvas size)
- The applied display offset and the renderer's decision whether the frame can serve a window SHALL be derived from the same frame: the same dimensions **and** the same viewport, as the display holds them. The renderer SHALL NOT skip a render for a window the display cannot serve — an unservable window SHALL count as not covered (follow-up fix, 2026-09-26)
- When the frame in hand cannot serve the requested window (no frame yet, a frame without an overrun margin, a viewport or magnification mismatch), the system SHALL request a render instead of relying on the display shift, and the pan SHALL continue to track the finger from the displayed center (follow-up fix, 2026-09-26)

#### Scenario: Small pan uses sub-region blit

- **WHEN** user pans 50 pixels right on a 1080×1920 screen with 1.2× overrun
- **THEN** the displayed window is offset by 50 pixels inside the 1296×2304 overrun frame
- **AND** no sub-region bitmap is allocated and no pixels are copied for the pan event
- **AND** no native render call is made

#### Scenario: Large pan triggers full re-render

- **WHEN** user pans 200 pixels right on a 1080×1920 screen with 1.2× overrun
- **THEN** the displayed window saturates at the overrun margin
- **AND** the system triggers a full native render centered on the displayed center
- **AND** a new overrun buffer is created at the new center

#### Scenario: Offset and frame stay consistent across a concurrent render

- **WHEN** a native render lands while a pan offset is applied
- **THEN** the offset applied to the draw and to the overlays SHALL be the one derived from the frame that is displayed
- **AND** the displayed content SHALL NOT jump when the new frame replaces the old one at a different center

#### Scenario: Rotated viewport pan

- **GIVEN** the viewport is rotated
- **WHEN** the displayed center moves
- **THEN** the applied display offset SHALL be the rotated delta (north-up delta rotated by the viewport angle)
- **AND** the content SHALL follow the finger along the drag direction, not a rotated-away direction

#### Scenario: Frame in hand cannot serve the window

- **GIVEN** the frame on screen has no overrun margin (or is missing, or was rendered at another magnification)
- **WHEN** the user pans
- **THEN** the window SHALL be treated as not covered and a native render SHALL be requested at the throttled cadence
- **AND** the map content SHALL NOT stay frozen while the finger moves
- **AND** once a frame with an overrun margin is displayed, the pan SHALL be served from it without further renders inside the margin

#### Scenario: Coverage and display never disagree into a freeze

- **WHEN** a pan window is evaluated for coverage by the renderer and for application by the display
- **THEN** both evaluations SHALL refer to the frame the user is looking at (its dimensions and its viewport)
- **AND** a request the renderer drops as covered SHALL be a request the display can shift (non-zero applicable offset)
- **AND** a request the display cannot shift SHALL NOT be dropped by the renderer
