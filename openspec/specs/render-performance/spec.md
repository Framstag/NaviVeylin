# Render Performance Specification

## Purpose

Reduce the native map render cost and the per-frame buffer copy overhead so follow-mode map updates keep up with the GPS marker cadence.

## Requirements

### Requirement: Native way node optimization

The system SHALL enable `TransPolygon::fast` node optimization for ways in the JNI render path so intermediate nodes that are not needed for rendering are dropped before Cairo draws them.

#### Scenario: Follow-mode render on a dense road network

- **WHEN** `MapPainterCairo::DrawMap` renders a viewport containing ways with many intermediate nodes
- **THEN** the way geometry is simplified with `TransPolygon::fast` before drawing
- **AND** the rendered map is visually equivalent at typical navigation zoom levels

### Requirement: Native area node optimization

The system SHALL enable `TransPolygon::fast` node optimization for areas in the JNI render path so area outlines with redundant nodes are simplified before Cairo fills them.

#### Scenario: Render with large building/landuse areas

- **WHEN** the viewport contains areas with long outlines
- **THEN** the area geometry is simplified with `TransPolygon::fast` before filling
- **AND** the fill result is visually equivalent at typical navigation zoom levels

### Requirement: Node reduction error tolerance

The system SHALL set a small error tolerance for node reduction via `SetOptimizeErrorToleranceMm` so the optimization is effective without visible geometry loss.

#### Scenario: Tolerance applied to way simplification

- **WHEN** the renderer simplifies a way with the configured error tolerance
- **THEN** the deviation of the simplified geometry stays within the tolerance
- **AND** the tolerance value is small enough that no visible kinks appear at navigation zoom levels

### Requirement: Multithreaded tile data loading

The system SHALL enable multithreaded tile data loading in the JNI render path via `AreaSearchParameter::SetUseMultithreading(true)` so `LoadMissingTileData` parallelizes across CPU cores.

#### Scenario: Cold render with many missing tiles

- **WHEN** a render requires loading tile data that is not yet cached
- **THEN** the tile data loading uses multiple threads
- **AND** the render completes faster than with single-threaded loading

### Requirement: Double-buffer swap without pixel copies

The system SHALL swap the rendered bitmap into the back buffer with `Canvas.drawBitmap` instead of `getPixels`/`setPixels` full-buffer copies.

#### Scenario: Rotated render completes

- **WHEN** a native render produces a bitmap for the back buffer
- **THEN** the bitmap is drawn into the back buffer via `Canvas.drawBitmap`
- **AND** no full-buffer `getPixels`/`setPixels` round-trip occurs

### Requirement: Frame emission only on change

The system SHALL emit the `_frameFlow` bitmap copy only when the frame actually changed; unchanged frames SHALL NOT allocate a new bitmap copy.

#### Scenario: Repeated emission of the same frame

- **WHEN** a frame is emitted and no new render has changed the front buffer
- **THEN** no new `Bitmap.createBitmap` copy is allocated for the unchanged frame
- **AND** the previously emitted frame is reused
