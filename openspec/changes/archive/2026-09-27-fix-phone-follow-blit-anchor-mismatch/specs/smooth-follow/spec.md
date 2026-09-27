# Delta: smooth-follow

## ADDED Requirements

### Requirement: Single resolved anchor across render, blit and marker

In phone follow mode the follow pipeline SHALL use one anchor value in every stage that positions the vehicle: the frame render target, the follow blit offset, and the marker projection SHALL all use the **resolved** anchor screen fraction (the preset after collision resolution against the surface's own overlays), never the raw preset when the two differ.

- The blit offset (`followOffset`) SHALL be computed against the same resolved fraction the frame was rendered with, so the displayed position's map content lands on the marker at the anchor
- The blit offset SHALL therefore stay a pure prediction drift (inside the overrun margin) while the anchor is applied exactly once, in the render target
- The vehicle marker SHALL stay glued to the map content it represents; the marker and the road SHALL NOT drift apart by the anchor delta while the frame lags behind the prediction (see gps-location-marker — marker shares one projection with the content)
- The re-centering path and follow re-engage SHALL commit the anchor-centered viewport with the same resolved anchor

#### Scenario: Navigation overlays resolve the anchor away from the raw preset

- **WHEN** the user navigates with a routing status card at the bottom, the preset bottom-center resolves from raw `fy = 0.9` to a resolved `fy` inside the visible area above the card, and the vehicle moves in follow mode
- **THEN** the displayed position's map content SHALL project to the resolved anchor fraction
- **AND** the vehicle marker SHALL project to the same resolved fraction, on the map content that is the vehicle's road position
- **AND** the blit offset SHALL NOT exceed the overrun margin while the display and the rendered frame are aligned (no re-render churn)

#### Scenario: Marker stays on the road at a non-resolved preset

- **WHEN** the raw preset differs from the resolved anchor (collision-remapped above an overlay) and the displayed frame lags the prediction by a drift up to the overrun margin
- **THEN** the marker SHALL land on the map content of the displayed position
- **AND** the marker SHALL NOT sit ahead of the road in the driving direction by the anchor delta
