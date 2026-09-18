# Delta: auto-smooth-follow

## ADDED Requirements

### Requirement: Single resolved anchor in the AA follow blit

In AA follow mode the follow blit offset SHALL be computed against the same **resolved** anchor screen fraction the AA frame render target uses — the `clampAnchorOutOfPane` result against the host pane band (left in LTR, right in RTL) — never the raw preset.

- The blit offset (`FollowPrediction.displayOffsetPx`) SHALL receive the resolved fraction at both blit sites (the extrapolation-loop blit and `renderFrame`'s blit path), so the pane-band presets are held at their resolved screen fraction
- With the frame rendered anchor-centered on the resolved fraction and the blit computed against it, the blit SHALL stay a pure prediction drift inside the overrun margin — never permanently clamped for a pane-band preset (a permanently clamped offset converts every tick into a full native render instead of a sub-region blit)
- The AA marker SHALL keep riding the blitted content (marker projection subtracts the same blit offset); the change SHALL NOT alter the marker-to-content glue

#### Scenario: Far-left preset against an LTR host pane

- **WHEN** the user selects a far-left preset (fx 0.1), the host draws its pane on the left (LTR) covering 40% of the surface width, and follow mode drives the map in navigation or free driving
- **THEN** the frame SHALL render anchor-centered on the resolved fraction (moved out of the pane band)
- **AND** the blit offset SHALL be computed against that same resolved fraction
- **AND** the blit offset SHALL NOT be clamped while the display and the rendered frame are aligned (no full-render churn)
- **AND** the vehicle marker SHALL stay on the map content at the resolved screen fraction

#### Scenario: Far-right preset against an RTL host pane

- **WHEN** the user selects a far-right preset (fx 0.9) and an RTL host draws its pane on the right
- **THEN** the far-right preset SHALL resolve out of the pane band and the blit SHALL use the resolved fraction exactly as in the LTR case
- **AND** presets outside the band (including the default center) SHALL keep their exact fraction and unchanged behavior
