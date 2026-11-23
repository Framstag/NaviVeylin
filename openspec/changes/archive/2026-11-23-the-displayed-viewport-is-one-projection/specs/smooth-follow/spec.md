# Spec Delta — smooth-follow

## MODIFIED Requirements

### Requirement: Single resolved anchor across render, blit and marker

In phone follow mode the follow pipeline SHALL compute one **displayed viewport** per frame — the frame viewport shifted by the clamped overrun offset — and SHALL hand that one value to every map-anchored overlay in both modes; the mode SHALL select the derivation's input, never between two projections, and no overlay SHALL carry a `graphicsLayer` shift of its own.

- The follow pipeline SHALL keep one resolved anchor: the blit offset SHALL be computed against the same resolved screen fraction the frame render target uses, never the raw preset, so the blit stays a pure prediction drift inside the overrun margin
- The pan path SHALL read the same derived viewport, so a pan offset needs no separate translate on the overlay layer
- The vehicle marker SHALL stay glued to the map content at the resolved anchor in both modes, and the route-segment highlight SHALL ride the same content
- The derivation SHALL be one function, so the rule "the overlay's shift is the frame's shift" is asserted once instead of being carried by a mode test

#### Scenario: Navigation overlays resolve the anchor away from the raw preset

- **WHEN** the user navigates with a routing status card at the bottom, the preset bottom-center resolves from raw `fy = 0.9` to a resolved `fy` inside the visible area above the card, and the vehicle moves in follow mode
- **THEN** the displayed position's map content SHALL project to the resolved anchor fraction
- **AND** the vehicle marker SHALL project to the same resolved fraction, on the map content that is the vehicle's road position
- **AND** the blit offset SHALL NOT exceed the overrun margin while the display and the rendered frame are aligned (no re-render churn)
- **Case**: none — the shipped scenario is repeated verbatim and its case is the one already in the suite (`FollowAnchorFramingTest`)

#### Scenario: Marker stays on the road at a non-resolved preset

- **WHEN** the raw preset differs from the resolved anchor (collision-remapped above an overlay) and the displayed frame lags the prediction by a drift up to the overrun margin
- **THEN** the marker SHALL land on the map content of the displayed position
- **AND** the marker SHALL NOT sit ahead of the road in the driving direction by the anchor delta
- **Case**: none — the shipped scenario is repeated verbatim and its case is the one already in the suite (`FollowAnchorFramingTest`)

#### Scenario: A pan offset lands the overlay on the frame's own shift

- **WHEN** the drawn frame is shifted inside the overrun window by a non-zero clamped offset and an overlay is drawn on the pan path
- **THEN** the overlay SHALL project against the derived displayed viewport and SHALL NOT be translated by a `graphicsLayer` shift
- **AND** the marker SHALL sit on the map content it rides, exactly as it does in follow mode
- **Case**: `app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift`

#### Scenario: The derivation does not branch on the mode

- **WHEN** the same clamped offset is applied with follow active and with follow disengaged
- **THEN** the two overlay positions SHALL differ only by the input viewport, so the offset is applied once in both cases
- **AND** the mode SHALL select the derivation's input, never between two projections
- **Case**: `app: MarkerDisplayShiftTest.theDerivationDoesNotBranchOnTheMode`
