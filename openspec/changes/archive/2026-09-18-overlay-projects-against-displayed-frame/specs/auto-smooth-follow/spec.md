# Spec Delta: auto-smooth-follow

## MODIFIED Requirements

### Requirement: Sub-region blit on viewport change

The system SHALL serve a viewport change within the overrun region by drawing the shifted overrun buffer to the surface instead of performing a full native render. This SHALL hold for every vehicle anchor preset: the blit offset SHALL be derived from the displayed vehicle position within the frame, never from the frame center, since the overrun buffer is rendered anchor-centered and a frame center is not a point of the rendered bitmap.

#### Scenario: Small GPS move served by blit

- **WHEN** the viewport center moves by a delta that stays within the overrun region
- **THEN** the surface SHALL be updated by drawing the overrun buffer shifted by the delta
- **AND** no full native render SHALL be initiated

#### Scenario: Viewport change exits overrun region

- **WHEN** the viewport center moves beyond the overrun region
- **THEN** a full native render SHALL be initiated at the new center
- **AND** the overrun buffer SHALL be refreshed

#### Scenario: Follow-mode fix re-anchor served by blit

- **WHEN** a GPS fix re-anchors the follow frame and the new frame center stays within the overrun region
- **THEN** the surface SHALL be updated by blitting the overrun buffer
- **AND** no full native render SHALL be initiated by that fix
- **AND** the frame SHALL hold the vehicle at the resolved anchor fraction

#### Scenario: Non-center anchor preset does not force a full render

- **WHEN** the follow anchor resolves away from the surface center (e.g. a bottom-row preset)
- **AND** the frame center moves by a delta that stays within the overrun region
- **THEN** the blit offset SHALL remain inside the overrun margin
- **AND** the frame SHALL be served by a blit instead of a full native render
