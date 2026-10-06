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

### Requirement: Reusable render target for map frames

The render path SHALL draw a map frame into a render target obtained from a shared, bounded pool
instead of allocating a bitmap per render. A target SHALL be handed out at the requested pixel size
in ARGB_8888 and SHALL be released back to the pool when its frame is dropped; a target SHALL NOT be
recycled by a caller that received it from the pool. When no free target of the requested size is
available, a new one SHALL be allocated. The pool SHALL be bounded: surplus targets released beyond
the bound SHALL be recycled rather than retained. Both surfaces that render through the shared JNI
entry point — the phone map canvas and the car map surface — SHALL use the pool.

#### Scenario: Consecutive renders of the same size reuse one target

- **WHEN** two consecutive renders of the same pixel size complete and the frame of the first has been dropped by the display path
- **THEN** the second render SHALL draw into the same target storage the first one used
- **AND** no new bitmap SHALL have been allocated for the second render

#### Scenario: A frame still in use is not handed out again

- **WHEN** a render is requested while the previous frame is still displayed (phone front buffer or car overrun frame)
- **THEN** the render SHALL use a different target than the one the display path holds
- **AND** the displayed pixels SHALL NOT be overwritten by the in-flight render

#### Scenario: Render size changes

- **WHEN** the requested render size changes (canvas overrun multiplier or surface size)
- **THEN** a target of the new size SHALL be used
- **AND** the previously held target of the other size SHALL be released instead of retained by the caller

#### Scenario: Pool stays bounded

- **WHEN** more targets are released back to the pool than the bound allows
- **THEN** the surplus targets SHALL be recycled
- **AND** the pool SHALL retain no more than the bound

#### Scenario: Reuse is observable

- **WHEN** the pool's allocation counter is enabled and N consecutive same-size renders are served on the same display path
- **THEN** exactly one bitmap allocation SHALL be reported for that sequence

### Requirement: A frame handed to the display layer is never overwritten

A frame that the display layer holds SHALL be an independent copy and SHALL NOT be a pooled render
target, so a later render can never change pixels that are still being displayed or still in flight
to the display. Releasing or reusing a render target SHALL NOT alter the contents of any frame already
emitted for display.

#### Scenario: Emitted frame survives later renders

- **WHEN** a frame has been emitted for display and further renders complete afterwards
- **THEN** the emitted frame's pixels SHALL be unchanged
- **AND** the display SHALL NOT show content of the later render at the emitted frame's crop offset

#### Scenario: Sub-region blit after release

- **WHEN** a sub-region blit is taken from the front buffer while that buffer's render target has already been released back to the pool
- **THEN** the blit SHALL still read valid pixels (the emitted frame is a copy)
- **AND** no "trying to use a recycled bitmap" failure SHALL occur

### Requirement: Pan hot path stays off the frame budget

The single-finger pan path SHALL keep the UI thread free of work proportional to the size of the map UI state: a pan event SHALL only move display-only state and SHALL NOT copy the UI state, recompose the screen, log, or request a render per event.

- A pan event SHALL NOT write the map UI state (`MapCanvasUiState`): the live displayed center SHALL be display-only state read by the draw/overlay layer (`map-pan-zoom` — Touch-based pan)
- A pan event SHALL NOT allocate bitmaps and SHALL NOT copy pixels
- A pan event SHALL NOT emit a log line; renderer diagnostics on the request/debounce path SHALL be gated behind a debug flag that is off in release and in normal debug runs. The only permitted pan-path output is gesture-level: at most one gated line per gesture (window served / not served, committed versus displayed center) plus one gated line per unservable-window rejection (follow-up fix, 2026-09-26)
- Gesture-start side effects (follow-mode disengagement, attribution interaction) SHALL run once per gesture, not once per pan event
- A render request during a saturated pan SHALL be throttled (at most one per debounce interval) so a fast drag cannot queue renders faster than they complete

#### Scenario: 120 Hz drag does not recompose the screen per event

- **WHEN** the user drags one finger across the map at 120 Hz for two seconds, staying inside the overrun margin
- **THEN** the map UI state SHALL be written at most twice (commit on the render request and at gesture end) plus the gesture-start side effects
- **AND** the map content SHALL follow the finger on every display frame

#### Scenario: Long drag beyond the overrun margin

- **WHEN** the user drags continuously beyond the overrun margin for several seconds
- **THEN** render requests SHALL be issued no faster than the render debounce interval
- **AND** the render queue SHALL NOT grow without bound (later requests coalesce)

#### Scenario: Release build is free of pan-path logging

- **WHEN** the user pans with logging at default settings
- **THEN** no per-event log line SHALL be produced by the pan path
- **AND** gesture-level diagnostics (one line per gesture end) MAY still be produced

#### Scenario: A dead pan is diagnosable without per-event output

- **GIVEN** the pan diagnostics are enabled
- **WHEN** a drag gesture ends without the map having moved
- **THEN** the gesture-level lines SHALL state that the window was not served (or that it was served but the offset stayed zero)
- **AND** the render path SHALL state that the request was dropped as covered
- **AND** at most one line per gesture plus one per unservable-window rejection SHALL have been emitted — no per-event logging

### Requirement: A render writes into caller-owned pixel storage

The render path SHALL write a rendered frame into pixel storage the caller supplies, and SHALL NOT
allocate a frame-sized pixel buffer per render. Storage supplied by the caller SHALL be reusable
across renders, and the caller SHALL remain the owner of it: the render path SHALL NOT retain a
reference to it beyond the render call, SHALL NOT recycle it, and SHALL NOT hand the same storage to
two renders at once.

#### Scenario: Consecutive frames allocate no frame-sized pixel buffer

- **WHEN** N consecutive full renders of the same pixel size complete into caller-supplied storage
- **THEN** no frame-sized pixel buffer SHALL have been allocated for any of them beyond the storage the caller supplied
- **AND** the rendered pixels SHALL be written into that storage

#### Scenario: A rendered frame is the content of the caller's storage

- **WHEN** a render completes successfully into caller-supplied storage
- **THEN** that storage SHALL contain the rendered frame
- **AND** the result SHALL be the same pixels an allocating render of the same viewport produces
- **AND** the storage SHALL hold them in the layout the display layer reads them in, so the frame on
  screen SHALL NOT differ in colour from the same viewport rendered on the allocating path

#### Scenario: A buffer-path frame and an allocating-path frame show the same colours

- **WHEN** the same viewport is rendered once into caller-supplied storage and once through the
  allocating entry point
- **THEN** the two frames SHALL show the same colours
- **AND** the comparison SHALL be made in the consumer's layout, because a raw byte comparison of the
  storage cannot see a channel-order mismatch (both destinations are internally consistent, and a
  frame that is byte-identical in the wrong layout is still the wrong colour on screen)

#### Scenario: Storage is not touched after the call returns

- **WHEN** a render into caller-supplied storage has returned
- **THEN** the render path SHALL NOT write into that storage again until the caller supplies it to another render
- **AND** the caller MAY release, reuse, or display it without the render path observing the change

#### Scenario: Storage under concurrent use is never written

- **WHEN** a caller supplies storage that a display path still reads, or supplies the same storage to two renders concurrently
- **THEN** the render path SHALL NOT be the cause of a fault (no crash, no torn frame reported as success)
- **AND** the render path SHALL NOT be assumed to guard the caller's storage against its own misuse

### Requirement: Per-render transient allocation is bounded

A full render SHALL NOT leave frame-sized transient allocations behind: every frame-sized pixel buffer
a render needs SHALL be reused across renders rather than allocated per render. The transient
allocation a render performs SHALL be independent of the frame's pixel count for frame-sized buffers,
and repeated renders SHALL NOT make the process footprint grow with the number of frames rendered.

#### Scenario: Repeated renders do not grow the native allocation for frame-sized buffers

- **WHEN** a display path renders M frames of the same size with the same viewport
- **THEN** the number of frame-sized native allocations SHALL NOT grow with M
- **AND** the rendered result SHALL be unchanged

#### Scenario: A frame larger than the previous one is served without a permanent peak

- **WHEN** a render is requested at a larger pixel size than any previous render in the process
- **THEN** the render SHALL complete
- **AND** storage for the previous, smaller size SHALL be released or reused instead of being retained alongside the new size beyond what the pool's bound allows

### Requirement: The displayed frame is not duplicated for reuse detection

The render path SHALL NOT hold a second frame-sized bitmap solely to decide whether an unchanged frame
can be re-emitted. Reuse of the previous frame SHALL be decided from bookkeeping (the frame's
sequence/epoch and its viewport identity), so no extra frame-sized buffer exists per display path.

#### Scenario: Unchanged frame is re-emitted without a second buffer

- **WHEN** a frame is requested and the previous frame is still current (unchanged sequence and viewport)
- **THEN** the previous frame SHALL be re-emitted
- **AND** no second frame-sized bitmap SHALL be retained for that decision

#### Scenario: A changed frame allocates nothing extra either

- **WHEN** a render produces a new frame after a previous one
- **THEN** the previous frame's storage SHALL follow the pool contract
- **AND** the number of frame-sized bitmaps alive on that display path SHALL NOT grow with the number of frames rendered

### Requirement: Animation frame references are released when the transition completes

A zoom, rotation or follow transition that holds a frame reference for its crossfade or scale SHALL
release that reference when the transition completes (it is abandoned, or the transition is
superseded), so a finished gesture SHALL NOT leave a frame-sized reference or its uploaded graphics
allocation behind for the rest of the session.

#### Scenario: A completed zoom leaves nothing held

- **WHEN** a zoom transition completes and the new frame has landed
- **THEN** the transition's old-frame reference SHALL be released
- **AND** repeating gestures SHALL NOT accumulate frame references or graphics allocations

#### Scenario: A superseded transition releases its frames

- **WHEN** a new gesture starts while a previous transition is still holding an old frame
- **THEN** the superseded transition's reference SHALL be released
- **AND** at most one transition's frames SHALL be held at any time
