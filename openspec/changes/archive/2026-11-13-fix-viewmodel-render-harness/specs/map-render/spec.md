# Spec Delta — map-render

## ADDED Requirements

### Requirement: A rendered frame reaches the UI state through the production collector

A frame SHALL reach `MapCanvasUiState` only through the collector that consumes `MapRenderer.frameFlow` and
routes it to `applyRenderedFrame`, so the bitmap, the viewport it was rendered with and the walk step it
advances are published together. A test that needs a landed frame SHALL drive that path and SHALL NOT publish a
frame or a viewport through a test-only setter.

#### Scenario: A frame lands through the collector

- **WHEN** a ViewModel is initialised, given a surface size and asked to render
- **THEN** `renderViewport` carries the viewport of the frame the renderer emitted, and `renderedBitmap` is
  the bitmap that frame carried, and both were published by the collector and not by a test hook
- **Case**: `MapCanvasViewModelFrameHarnessTest#aFrameLandsThroughTheFrameCollector`

#### Scenario: A walked step is the magnification the renderer rendered

- **WHEN** a magnification change larger than the serviceable window is committed and each landed frame
  advances the walk
- **THEN** every walk step equals the magnification of the frame that produced it, every step is within the
  window of the frame before it, and the last step lands exactly on the recorded target
- **Case**: `MapCanvasViewModelZoomWalkTest#aLargeChangeWalksAndLandsExactlyOnTheTarget`