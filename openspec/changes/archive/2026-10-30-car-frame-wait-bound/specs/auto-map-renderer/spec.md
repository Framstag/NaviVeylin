# Spec Delta

## ADDED Requirements

### Requirement: A requested car frame that never lands is re-requested

A car frame request SHALL stay outstanding while the frame path cannot draw it. While a surface is
held and no frame was drawn when the frame-wait bound expires, the renderer SHALL request it again,
at most a fixed number of times per stalled wait, and the budget SHALL re-arm when a frame lands.
Each expired wait SHALL be recorded once, identity-only, naming the retry index. Landed frames SHALL
NOT be duplicated; a stopped, paused or shut-down renderer SHALL NOT retry.

#### Scenario: A frame that never lands is requested again

- **GIVEN** a started car screen with a surface held, whose requested frame is never drawn — the frame
  path returns without drawing
- **WHEN** the frame-wait bound expires
- **THEN** the request is still outstanding and the renderer asks for the frame again, without the
  driver re-entering the screen or changing the viewport (case
  `AutoMapRendererFrameWaitTest.aFrameThatNeverLandsIsRequestedAgain`)

#### Scenario: Landed frames are not duplicated

- **WHEN** every requested frame is drawn inside the frame-wait bound
- **THEN** the renderer issues exactly the requests the state changes asked for, and the draw
  counters are not exceeded by retry work (case
  `AutoMapRendererFrameWaitTest.landedFramesAreNotDuplicated`)

#### Scenario: A stopped renderer does not retry

- **GIVEN** an armed frame wait
- **WHEN** the screen stops, the surface is detached, or the renderer is shut down before the bound
  expires
- **THEN** no frame is requested and no surface is locked by the renderer (case
  `AutoMapRendererFrameWaitTest.aStoppedRendererIssuesNoRetry`)

#### Scenario: The retry budget is finite and reported once

- **GIVEN** a started renderer whose requested frames are never drawn
- **WHEN** the retry budget for that stalled wait is exhausted
- **THEN** one coordinate-free diagnostic names the retry count, no further frame is requested for
  that wait, and a frame that later lands re-arms the budget (case
  `AutoMapRendererFrameWaitTest.theExhaustedBudgetIsReportedOnce`)
