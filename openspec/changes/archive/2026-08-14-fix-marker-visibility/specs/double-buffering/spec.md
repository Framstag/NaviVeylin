# double-buffering Specification (Delta)

## ADDED Requirements

### Requirement: Buffers hold only rendered map content

The back buffer and front buffer SHALL contain only the output of the map render. Temporary overlays (e.g., the GPS location marker) SHALL be drawn by the UI layer on top of the front buffer and SHALL never be written into either buffer.

- The front buffer SHALL be reusable for sub-region blits without carrying stale overlay pixels
- An overlay redraw SHALL NOT trigger a buffer swap or modify buffer contents
- The displayed frame SHALL be the composition of the front buffer (map content) plus per-frame overlays drawn on top

#### Scenario: Overlay redraw leaves buffers untouched

- **WHEN** the marker moves or hides while the map content is unchanged
- **THEN** the back buffer and front buffer SHALL remain unchanged
- **THEN** only the overlay layer SHALL change

#### Scenario: Sub-region blit after marker move

- **WHEN** a small pan is served by a sub-region blit after the marker has moved
- **THEN** the blitted content SHALL contain no marker pixels from the marker's previous position
