# Spec Delta

## MODIFIED Requirements

### Requirement: Follow-mode render throttling

The system SHALL trigger a follow-mode map render when the displayed drift reaches the refresh threshold inside the overrun region, and it SHALL have requested a frame before the predicted position would leave the overrun region of the current front buffer — not on every GPS fix. Renders SHALL still be coalesced by the render debounce so no more than one render is initiated per debounce window.

#### Scenario: Multiple GPS ticks arrive within 200 ms

- **WHEN** two or more distinct GPS fixes arrive within 200 ms
- **THEN** only one follow-mode render is initiated
- **AND** the navigation engine still receives every fix
- **Case**: `app: MapCanvasViewModelFollowModeTest.theFollowRenderThrottleBoundaryIsDecidedByInjectedTime`

#### Scenario: Prediction stays inside overrun region

- **WHEN** the displayed drift stays below the refresh threshold of the overrun margin
- **THEN** no full render is initiated
- **AND** the map is scrolled by blitting the front buffer
- **Case**: `app: MapFollowRefreshTest.belowThresholdRendersNothing`

#### Scenario: Drift at the refresh threshold requests a frame

- **WHEN** the displayed drift reaches the refresh threshold
- **THEN** a follow-mode render is requested while the drift is still inside the overrun margin
- **AND** the map keeps scrolling by blitting the frame in hand until that render lands
- **Case**: `app: MapFollowRefreshTest.thresholdRequestsFrame`
