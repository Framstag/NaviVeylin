# Spec Delta — auto-smooth-follow

## MODIFIED Requirements

### Requirement: Single resolved anchor in the AA follow blit

In AA follow mode the follow path SHALL compute one **displayed viewport** per tick — the frame viewport shifted by the clamped overrun offset — and SHALL read it at the blit, the marker's draw offset and the frame's own commit; the resolved anchor fraction SHALL be read in one place rather than by each site agreeing separately.

- `anchorCenterFor` SHALL remain the derivation's input — the frame's anchor-centred commit — and SHALL NOT be a second projection the blit has to agree with
- The blit offset SHALL be computed against the same resolved fraction the frame render target uses, never the raw preset, so the blit stays a pure prediction drift inside the overrun margin
- The AA marker SHALL keep riding the blitted content; the change SHALL NOT alter the marker-to-content glue

#### Scenario: Far-left preset against an LTR host pane

- **WHEN** the user selects a far-left preset (fx 0.1), the host draws its pane on the left (LTR) covering 40% of the surface width, and follow mode drives the map in navigation or free driving
- **THEN** the frame SHALL render anchor-centered on the resolved fraction (moved out of the pane band)
- **AND** the blit offset SHALL be computed against that same resolved fraction
- **AND** the blit offset SHALL NOT be clamped while the display and the rendered frame are aligned (no full-render churn)
- **AND** the vehicle marker SHALL stay on the map content at the resolved screen fraction
- **Case**: none — the shipped scenario is repeated verbatim and its case is the one already in the suite (`AutoMapRendererBlitTest`)

#### Scenario: Far-right preset against an RTL host pane

- **WHEN** the user selects a far-right preset (fx 0.9) and an RTL host draws its pane on the right
- **THEN** the far-right preset SHALL resolve out of the pane band and the blit SHALL use the resolved fraction exactly as in the LTR case
- **AND** presets outside the band (including the default center) SHALL keep their exact fraction and unchanged behavior
- **Case**: none — the shipped scenario is repeated verbatim and its case is the one already in the suite (`AutoMapRendererFollowAnchorTest`)

#### Scenario: The marker's draw offset is the blit offset

- **WHEN** a follow tick blits the overrun frame at a clamped offset and the marker is drawn on the same tick
- **THEN** the marker's draw offset SHALL be the derived displayed viewport's offset, read once
- **AND** the blit, the marker and the frame's commit SHALL NOT each recompute a shift from the resolved fraction
- **Case**: `auto#theCarOverlayOffsetIsTheFramesShift`

#### Scenario: A site handed the raw preset reddens the case

- **WHEN** a call site is handed the raw preset instead of the resolved fraction
- **THEN** the case SHALL fail, because the derived offset is read in one place and the site no longer reads it
- **AND** the failure SHALL name the site, not the value
- **Case**: `auto#theCarOverlayOffsetIsTheFramesShift`
