# Spec Delta

## ADDED Requirements

### Requirement: Render entry point writing into a caller-supplied pixel buffer

The JNI bridge SHALL provide a render entry point that renders a map frame into pixel storage the
caller supplies, and SHALL NOT allocate frame-sized pixel storage per call for it. The entry point
SHALL accept the frame's pixel size, the viewport parameters the existing render entry point takes,
and the caller's storage; on success the storage SHALL contain the frame in the pixel format and
stride the entry point's declaration states, and that format SHALL be the format the caller consumes
the storage as. A failure SHALL be reported to the caller and SHALL NOT fault the process (no SIGSEGV,
no abort, no JNI abort), and a failed render SHALL NOT report success.

#### Scenario: Frame rendered into the caller's buffer

- **WHEN** Java calls the buffer-taking render entry point with a valid buffer and viewport
- **THEN** the buffer SHALL contain the rendered frame in the pixel format the bridge documents
- **AND** the returned status SHALL report success

#### Scenario: The buffer's layout is the consumer's, not the allocating path's

- **WHEN** a caller passes storage it hands to the display layer without a conversion step
- **THEN** the bytes in that storage SHALL be in the display layer's own pixel layout (its channel
  order and its stride), byte for byte
- **AND** the entry point's declaration SHALL state that layout (pixel size, channel order, stride)
- **AND** the layout SHALL NOT be assumed to be the allocating entry point's `int[]` element order:
  the same frame reaches the two destinations in two different layouts, and writing one layout into
  the other destination is a channel swap, not a rounding difference

#### Scenario: No frame-sized allocation per call

- **WHEN** the entry point is called repeatedly with the same pixel size
- **THEN** no frame-sized pixel buffer SHALL be allocated per call by the bridge
- **AND** the bridge SHALL NOT hand the same buffer to two renders at once

#### Scenario: Failure is reported, not fatal

- **WHEN** the render cannot be completed (no open database, a rejected viewport, a full or unusable buffer)
- **THEN** the call SHALL return a failure status
- **AND** the process SHALL NOT fault and the caller's buffer SHALL remain a valid, owned buffer

### Requirement: The allocating render entry point remains available and unchanged

The existing render entry point that allocates its result and returns it to Java SHALL keep its
behaviour, its signature, and its error semantics, so callers and consumers outside this app continue
to work.

#### Scenario: Legacy entry point still returns a frame

- **WHEN** Java calls the allocating render entry point
- **THEN** the result SHALL be the rendered frame as before this change
- **AND** its failure behaviour SHALL be unchanged

#### Scenario: Declared native signatures stay consistent with the implementation

- **WHEN** the native library and the Java declarations are checked against each other
- **THEN** every declared native method SHALL match the implemented signature
- **AND** the check SHALL pass with the new entry point present
