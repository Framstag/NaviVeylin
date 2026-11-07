# Spec Delta

## MODIFIED Requirements

### Requirement: The follow blit offset is computed by one seam that owns the anchor

In phone follow mode the blit offset SHALL be produced by one seam, `FollowBlitRules.offsetFor`, that takes
the map state and reads `MapCanvasUiState.resolvedAnchor` itself. The anchor fraction SHALL NOT be a
parameter a caller supplies; the screen never computes the offset inline and never rebuilds the map state
around the raw preset on this path.

#### Scenario: The screen takes the offset from the seam

- **WHEN** the phone map screen's follow display block needs the display offset for the frame in hand
- **THEN** it calls `FollowBlitRules.offsetFor` exactly once, calls `OverrunWindow.shiftPx`
  nowhere and does not reconstruct the state with `activeFollowAnchor`, so the screen cannot choose the
  anchor — exercised by
  `FollowBlitCallSiteTest.the follow blit offset is computed by the seam and not inline in the frame loop`

#### Scenario: The seam consumes the resolved anchor with measured overlays

- **WHEN** the preset is `BOTTOM_CENTER` (raw `fy = 0.9`), the navigation overlays are measured
  (`setMapOverlayInsets(top = 400, bottom = 400, right = 80)`), the persisted preset is asserted (not the
  `0.5` fallback), the frame is the ViewModel's `followRenderTarget(...)` — anchor-centered on the resolved
  fraction — and the display has drifted 40 m north of it
- **THEN** the seam's offset is unclamped and equals the resolved-anchor computation, while the same
  geometry computed with the raw preset is clamped at the overrun margin — exercised by
  `MapCanvasViewModelVehicleAnchorTest.followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset`

#### Scenario: A seam that reads the raw preset is refused

- **WHEN** the follow offset path's seam reads `activeFollowAnchor` instead of the resolved anchor
- **THEN** the source guard fails and names that read — exercised by
  `FollowBlitCallSiteTest.the follow offset path cannot read the raw preset`
