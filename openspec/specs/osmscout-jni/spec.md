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

### Requirement: The published route length is a length of that route

The length a calculated route publishes SHALL be derived from the route's own geometry or from its
description. It SHALL NOT be the straight-line (air-line) distance between the route's start and target.
Where both are available they SHALL agree, since one route has one length of road.

#### Scenario: A published length tracks the route that was drawn

- **WHEN** a calculated route's published total distance and its published polyline are read
- **THEN** the total SHALL agree with the great-circle length of that polyline within the description's
  own agreement with it (measured 2026-10-05: description over polyline 1.0014 / 1.0021 / 0.9964)
- **AND** the total SHALL NOT be the start-to-target air-line estimate (measured 2026-10-05: 0.748 /
  0.795 / 0.552 of the polyline, deficits 24.5 km / 4.3 km / 0.7 km)

#### Scenario: A long intercity route carries a real length

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the published total SHALL lie within a few percent of the drawn route's length
- **AND** it SHALL NOT be closer to the great-circle distance between its endpoints than to the drawn
  length (the ~70 km case: 72 771 m published against 97 283 m drawn and ~66 km air-line)

#### Scenario: A route without a description still carries a route length

- **WHEN** a calculated route exposes no per-step description, so the per-step sum cannot state its length
- **THEN** the route's published total SHALL still be a length of the route it publishes, not the
  start-to-target estimate
- **AND** a client that falls back to that total SHALL obtain a route length

### Requirement: The start/target estimate is not exposed as a route length

The router's air-line estimate MAY remain available where the router itself needs it — the cost limit and
the progress denominator — but no client of the route result SHALL receive it as the route's length, and a
field that carries it SHALL be named or documented as an estimate.

#### Scenario: No route field states the estimate as a distance

- **WHEN** the fields of a calculated route result are inspected
- **THEN** no field documented or named as the route's distance or length SHALL hold the start-to-target
  estimate
- **AND** a field that does hold it SHALL say that it is an estimate

#### Scenario: A consumer that reads the length gets a length

- **WHEN** a client reads the route's total distance — the card statistic, the step list's sum, the
  progress denominator, or a fallback for a route without steps
- **THEN** it SHALL obtain a length of that route, on every code path that publishes one

### Requirement: Per-step leg values on a calculated route

A calculated route SHALL expose one distance (in metres) and one duration (in seconds) per instruction line,
each describing the leg that **ends at that line's manoeuvre** — never a value measured between two
consecutive route nodes that carry no instruction line. Index alignment SHALL hold with the route's
instruction lines and with the per-step positions, and the legs SHALL cover the route once: their sum SHALL
be the route's own length as the description measures it, and SHALL NOT be one geometry edge per step.
(The router's overall distance is a separate native number; the two may disagree — TODO.md §126.) Index
alignment SHALL hold with the route's instruction lines and with the per-step positions. When the values
cannot be aligned one-to-one with the instruction lines, both SHALL be absent for that route rather than
shifted.

#### Scenario: Legs sum to the route's total

- **WHEN** a route of 17.3 km with 19 instruction lines is calculated
- **THEN** the per-step distances SHALL cover the route once, i.e. sum to the route's own total as the description measures it (the router's overall distance is a second, independent number - measured on device 2026-10-05 as 1.34× on a 70 km route, TODO.md §126)
- **AND** the sum SHALL NOT be the length of a single geometry edge per step (measured before this change: a few hundred metres for a 17,3 km route)

#### Scenario: A step's values belong to its own leg

- **WHEN** a step's values are inspected
- **THEN** its distance SHALL be the route distance from the previous instruction's manoeuvre to this one
- **AND** its duration SHALL be the travel time of that same leg
- **AND** the value SHALL NOT be the distance or time of the last geometry edge before the manoeuvre

#### Scenario: Unaligned values are absent, not shifted

- **WHEN** the per-step values cannot be aligned one-to-one with the instruction lines
- **THEN** no per-step distance and no per-step duration SHALL be published for the route
- **AND** the instruction lines SHALL remain valid and complete

#### Scenario: Start line owns no leg

- **WHEN** the route's first instruction line is the start line
- **THEN** its distance SHALL be zero and it SHALL carry no duration

### Requirement: Instruction segment time is the leg's travel time

The time a route exposes for an instruction SHALL be the travel time of the leg that ends at that
instruction's manoeuvre, so that a client can tell how long driving that step takes. It SHALL NOT be the
travel time between two consecutive route nodes, and it SHALL agree with the per-step duration the same
route exposes for that step.

#### Scenario: A step's time covers its whole leg, not one of its edges

- **WHEN** a step's leg spans several hundred metres between two manoeuvres
- **THEN** its reported time SHALL be the travel time of that whole leg, i.e. at least tens of seconds
- **AND** SHALL NOT be the sub-second to few-second time of a single geometry edge inside the leg

#### Scenario: A city step is reported in minutes, not in seconds

- **WHEN** a step's leg covers several hundred metres of a city route
- **THEN** its reported time SHALL be on the order of the driving time of that leg
- **AND** SHALL NOT be the sub-second to few-second time of a single geometry edge

#### Scenario: The two step lists of one route agree

- **WHEN** a route exposes per-step durations and an instruction list built from the same route
- **THEN** the time of an instruction SHALL equal the per-step duration of the same step

### Requirement: A next instruction's time is the remaining time of its leg

For the next instruction — the one the instruction list is asked for ahead of the current position — the
time SHALL be the **remaining** travel time of that instruction's leg, consistent with the remaining
distance the same instruction reports, and it SHALL reach zero as the manoeuvre is reached.

#### Scenario: The arrival estimate shrinks while the leg is driven

- **WHEN** the next instruction is reported, then reported again after the driver has covered most of its leg
- **THEN** the second remaining time SHALL be smaller than the first
- **AND** the estimate of arriving at that manoeuvre SHALL NOT stay at its initial value while the manoeuvre is approached

#### Scenario: Arrival estimate is not seconds away for a long leg

- **WHEN** the next manoeuvre's leg is 900 m in city traffic
- **THEN** the remaining time reported for it SHALL be on the order of a minute
- **AND** an arrival time derived from it SHALL NOT land within the next few seconds

### Requirement: Render entry points take the projection DPI

The JNI render entry points SHALL accept the physical DPI to project with as an explicit parameter of the render request.

- Every render entry point that produces map pixels SHALL accept the DPI parameter
- The render SHALL project with the passed value, not with a value stored in client settings
- A render request with an unusable DPI (zero or negative) SHALL NOT produce a frame; the failure SHALL surface to the caller like any other render failure

#### Scenario: Render projects with the passed DPI

- **WHEN** Java issues a render request carrying DPI X
- **THEN** the produced pixels are projected at X
- **THEN** the geographic extent covered by the pixel buffer corresponds to X

#### Scenario: Two renders with different DPI in one client

- **WHEN** two render requests with different DPI values are issued against the same client instance
- **THEN** each frame is projected with its own request's value, independent of the order in which they are issued

### Requirement: No client-wide render DPI setter

The JNI bridge SHALL expose no API that changes the projection DPI used by renders, and no render SHALL depend on a previously configured client-wide value.

- A client-wide DPI setter SHALL NOT be part of the Java bridge API
- Calls that configure other client-wide native state (style sheet, tile data cache capacity) SHALL NOT imply or alter the projection DPI

#### Scenario: No bridge method changes the projection DPI

- **WHEN** the Java bridge API is inspected by a caller
- **THEN** no method changes a client-wide render DPI
- **THEN** omitting any such call leaves every render correctly projected, because each request carries its own DPI

#### Scenario: Other client-wide configuration does not affect the projection DPI

- **WHEN** the style sheet or the native tile data cache capacity is configured on the client
- **THEN** subsequent renders are still projected with the DPI of their own request
