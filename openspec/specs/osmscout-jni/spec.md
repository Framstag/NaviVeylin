# JNI Bridge (osmscout-jni)

## Purpose

Java/JNI wrapper library providing Java-accessible APIs over the native C++ layer. Implemented via upstream `libosmscout-client-java` submodule (not a separate AAR module).

## Requirements

### Requirement: JNI bridge to native library
The system SHALL expose a Java API that wraps `libosmscout-client` C++ functions via JNI, covering map loading, coordinate queries, and routing.

#### Scenario: Native function called from Java
- **WHEN** Java code calls a JNI bridge method
- **THEN** the corresponding C++ function in `libosmscout-client` executes and returns results to Java

### Requirement: Native library loading
The system SHALL load the native `.so` library before any JNI calls are made.

#### Scenario: Library loads on app start
- **WHEN** app starts
- **THEN** `System.loadLibrary("osmscout_client_java")` succeeds and JNI functions are available

### Requirement: Error handling across JNI boundary
The system SHALL handle C++ exceptions in the JNI layer and convert them to Java exceptions.

#### Scenario: C++ exception becomes Java exception
- **WHEN** a C++ function throws an exception
- **THEN** the JNI bridge catches it and throws a corresponding Java exception

### Requirement: Search result region hierarchy scoped to originating database
When a search returns results while more than one map database is loaded, the admin region hierarchy of every result SHALL be resolved exclusively within the database that produced the result. Hierarchy levels SHALL NEVER be resolved from another loaded database, even when file offsets coincide between databases.

#### Scenario: Multi-map install resolves hierarchy from the correct map
- **WHEN** map databases for Iceland and Germany/Nordrhein-Westfalen are loaded
- **AND** a search returns an address in Bergkamen, Germany (e.g. "Rosenweg 20 Bergkamen")
- **THEN** the result's `adminRegionHierarchy` SHALL contain only German region names (e.g. "Bergkamen/Kreis Unna/Arnsberg/Nordrhein-Westfalen/Deutschland")
- **AND** SHALL NOT contain any region name from the Iceland database

#### Scenario: Coinciding file offsets do not poison the hierarchy
- **WHEN** the file offset of a region in database A numerically equals the file offset of a region in database B
- **AND** a search result from database A references its parent region at that offset
- **THEN** the resolved parent SHALL be database A's region
- **AND** the hierarchy path SHALL remain entirely consistent within database A

#### Scenario: Single-database behavior unchanged
- **WHEN** exactly one map database is loaded
- **AND** a search returns an address
- **THEN** the result's `adminRegionHierarchy` SHALL resolve exactly as before this change

#### Scenario: Hierarchy falls back when the chain cannot be resolved
- **WHEN** the originating database cannot resolve the full parent chain of a result's admin region
- **THEN** the hierarchy SHALL contain the resolvable prefix of the chain only
- **AND** the result SHALL still be returned with its remaining fields intact

### Requirement: Search result object identity scoped to originating database
When a search returns results while more than one map database is loaded, each result's object reference (coordinates, object name, object type) SHALL be resolved exclusively against the database that produced the result. Resolving an object reference from another loaded database SHALL NOT override the originating database's resolution, even when the object file offset coincides with an object in another database.

#### Scenario: Coordinates resolved from the originating database
- **WHEN** a search result's object file offset numerically coincides with an object in another loaded database
- **THEN** the result's coordinates and name SHALL come from the originating database's object
- **AND** the other database's coinciding object SHALL NOT be used

#### Scenario: Unreadable object still drops the entry
- **WHEN** the originating database cannot read the result's object reference (stale or corrupt index entry)
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining entries SHALL be returned normally (unchanged resilience behavior)

### Requirement: Free-text dedup scoped to originating database
Free-text search hit deduplication SHALL compare object file offsets only within the same database. Two hits from different databases SHALL NOT be treated as the same object merely because their file offsets coincide.

#### Scenario: Coinciding offsets across databases both returned
- **WHEN** a free-text search finds a hit in database A and a hit in database B at numerically equal file offsets
- **THEN** both hits SHALL be returned
- **AND** neither SHALL be dropped as a duplicate of the other

#### Scenario: Duplicate within one database still deduplicated
- **WHEN** the same object file offset appears twice in one database's free-text results
- **THEN** the duplicate SHALL be dropped and the object SHALL appear once

### Requirement: Favorite calls are safe while the favorite store is replaced

The JNI bridge SHALL replace the native favorite store atomically with respect to favorite calls. The store is replaced whenever the favorites file is loaded or saved, which today recreates the underlying service instance. A favorite call SHALL NEVER run against a service instance that has been destroyed or replaced, and replacing the store SHALL NEVER take place while a favorite call is in flight on it. Concurrent favorite calls SHALL be serialised. Under concurrency the bridge SHALL NOT fault.

#### Scenario: Store replacement during an in-flight favorite call does not fault

- **WHEN** the favorites file is loaded or saved while another thread is inside a favorite call
- **THEN** no favorite call SHALL operate on a destroyed service instance
- **AND** no native fault (SIGSEGV, abort, use-after-free) SHALL occur

#### Scenario: A store replacement is never observed half-applied

- **WHEN** a favorite read runs while the store is being replaced (loaded or rebuilt from the caller's data)
- **THEN** the read SHALL return either the state before the replacement or the fully replaced store
- **AND** SHALL NOT return an empty or partially rebuilt store

#### Scenario: Concurrent mutation and persist do not discard each other

- **WHEN** a persist that rebuilds the store from caller-supplied data runs concurrently with a favorite mutation
- **THEN** the bridge SHALL NOT fault and SHALL NOT leave the store partially rebuilt
- **AND** a mutation that completed before the persist took its input data SHALL still be present afterwards

#### Scenario: Sequential favorite calls are unchanged

- **WHEN** favorite calls are issued one after another without overlapping
- **THEN** they SHALL behave exactly as before this change
- **AND** the favorites file SHALL contain the same content as before

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

### Requirement: One route length for a calculated route
A calculated route SHALL expose exactly one length. The per-step legs the same route exposes SHALL sum to
that length, so a client reading the route's total and a client summing its step legs obtain the same
number. A route SHALL NOT carry two different totals for one length of road.

#### Scenario: Total equals the sum of the step legs

- **WHEN** a calculated route exposes a total distance and a per-step distance for each of its instruction lines
- **THEN** the sum of the per-step distances SHALL equal the route's total distance
- **AND** the difference SHALL be no more than the rounding of the per-step values

#### Scenario: A long intercity route has one length

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the route's total distance and the sum of its step legs SHALL agree within rounding
- **AND** the total SHALL NOT disagree with that sum by a ratio (measured 2026-10-05: total 72 771 m vs legs 97 416 m, ratio 1.34, on a ~70 km route)

#### Scenario: A short town route has one length

- **WHEN** a short town route (a few kilometres) is calculated
- **THEN** the route's total distance and the sum of its step legs SHALL agree within rounding
- **AND** the total SHALL NOT disagree with that sum by a ratio larger than on a long route

#### Scenario: The total survives the route's geometry being described in steps

- **WHEN** a client obtains only the route's step list for a calculated route
- **THEN** the steps SHALL be sufficient to state that route's total length
- **AND** the client SHALL NOT need a second, independently computed total to report it
