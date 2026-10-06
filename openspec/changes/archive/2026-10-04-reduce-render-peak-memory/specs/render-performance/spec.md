# Spec Delta

## ADDED Requirements

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

The render path SHALL NOT hold a second frame-sized bitmap solely to decide whether an unchanged frame can be re-emitted. Reuse of the previous frame SHALL be decided from bookkeeping (the frame's sequence/epoch and its viewport identity), so no extra frame-sized buffer exists per display path.

#### Scenario: Unchanged frame is re-emitted without a second buffer

- **WHEN** a frame is requested and the previous frame is still current (unchanged sequence and viewport)
- **THEN** the previous frame SHALL be re-emitted
- **AND** no second frame-sized bitmap SHALL be retained for that decision

#### Scenario: A changed frame allocates nothing extra either

- **WHEN** a render produces a new frame after a previous one
- **THEN** the previous frame's storage SHALL follow the pool contract
- **AND** the number of frame-sized bitmaps alive on that display path SHALL NOT grow with the number of frames rendered

### Requirement: Animation frame references are released when the transition completes

A zoom, rotation or follow transition that holds a frame reference for its crossfade or scale SHALL release that reference when the transition completes (it is abandoned, or the transition is superseded), so a finished gesture SHALL NOT leave a frame-sized reference or its uploaded graphics allocation behind for the rest of the session.

#### Scenario: A completed zoom leaves nothing held

- **WHEN** a zoom transition completes and the new frame has landed
- **THEN** the transition's old-frame reference SHALL be released
- **AND** repeating gestures SHALL NOT accumulate frame references or graphics allocations

#### Scenario: A superseded transition releases its frames

- **WHEN** a new gesture starts while a previous transition is still holding an old frame
- **THEN** the superseded transition's reference SHALL be released
- **AND** at most one transition's frames SHALL be held at any time
