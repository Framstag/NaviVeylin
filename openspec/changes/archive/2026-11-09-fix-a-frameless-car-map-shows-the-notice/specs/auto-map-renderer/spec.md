# Spec Delta — auto-map-renderer

## MODIFIED Requirements

### Requirement: A requested car frame that never lands is re-requested

A car frame request SHALL stay outstanding while the frame path cannot draw it. While a surface is
held and no frame was drawn when the frame-wait bound expires, the renderer SHALL request it again,
at most a fixed number of times per stalled wait, and the budget SHALL re-arm when a frame lands.
Each expired wait SHALL be recorded once, identity-only, naming the retry index. Landed frames SHALL
NOT be duplicated; a stopped, paused or shut-down renderer SHALL NOT retry. The bound, the armed wait
and the injected clock SHALL be decided by the renderer's own state, so a case can drive the armed
watchdog without depending on which thread reaches a statement first. The moment the budget is spent,
and the moment a frame lands afterwards, SHALL be reported to the owning screen through one
change-only callback, so the screen can state the map's unavailability where the host draws it without
a frame; a wait that never spent its budget SHALL publish nothing.

#### Scenario: A frame that never lands is requested again

- **GIVEN** a started car screen with a surface held, whose requested frame is never drawn — the frame
  path returns without drawing
- **WHEN** the frame-wait bound expires
- **THEN** the request is still outstanding and the renderer asks for the frame again, without the
  driver re-entering the screen or changing the viewport
- **Case**: `AutoMapRendererFrameWaitTest#aFrameThatNeverLandsIsRequestedAgain`

#### Scenario: The armed watchdog fires and retries the frame

- **GIVEN** a started renderer with a surface held, exactly one armed wait, and a frame-wait bound the
  case lowered, whose requested frame is never drawn
- **WHEN** the armed wait's deadline expires
- **THEN** the armed job itself requests the frame again exactly once for that wait and records one
  coordinate-free line naming the retry index, with the expiry read from the renderer's injected clock,
  so the outcome does not depend on the order in which the test thread advances that clock
- **Case**: `AutoMapRendererFrameWaitTest#theArmedWaitFiresAndRetriesTheFrame`

#### Scenario: Landed frames are not duplicated

- **WHEN** every requested frame is drawn inside the frame-wait bound
- **THEN** the renderer issues exactly the requests the state changes asked for, and the draw
  counters are not exceeded by retry work
- **Case**: `AutoMapRendererFrameWaitTest#landedFramesAreNotDuplicated`

#### Scenario: A stopped renderer does not retry

- **GIVEN** an armed frame wait
- **WHEN** the screen stops, the surface is detached, or the renderer is shut down before the bound
  expires
- **THEN** no frame is requested and no surface is locked by the renderer
- **Case**: `AutoMapRendererFrameWaitTest#aStoppedRendererIssuesNoRetry`

#### Scenario: The retry budget is finite and reported once

- **GIVEN** a started renderer whose requested frames are never drawn
- **WHEN** the retry budget for that stalled wait is exhausted
- **THEN** one coordinate-free diagnostic names the retry count, no further frame is requested for
  that wait, and a frame that later lands re-arms the budget
- **Case**: `AutoMapRendererFrameWaitTest#theExhaustedBudgetIsReportedOnce`

#### Scenario: The spent budget is told to the owner, and a landed frame clears it

- **GIVEN** a started renderer with a surface held whose requested frame is never drawn, and an owner
  listening for the spent-budget state
- **WHEN** the retry budget is exhausted and later a frame lands
- **THEN** the owner is told exactly once that the budget is spent and exactly once that it cleared, no
  other iteration reports anything, and the state the renderer exposes agrees with both reports — so a
  screen can move its notice off a surface that has no frame and back on the frame that lands
- **Case**: `AutoMapRendererFrameWaitTest#theSpentBudgetIsToldToTheOwnerAndClearedByALandedFrame`
