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

A calculated route SHALL expose, on every step it publishes, one distance (in metres) and one duration (in
seconds), each describing the leg that **ends at that step's manoeuvre** — never a value measured between two
consecutive route nodes that carry no step. The legs SHALL cover the route once: their sum SHALL be the route's
own length as the description measures it, and SHALL NOT be one geometry edge per step. (The router's overall
distance is a separate native number; the two may disagree — TODO.md §126.) A route that yields no step SHALL
publish no per-step values, so a client never reads a value that belongs to no manoeuvre.

#### Scenario: Legs sum to the route's total

- **WHEN** a route of 17.3 km with 19 steps is calculated
- **THEN** the per-step distances SHALL cover the route once, i.e. sum to the route's own total as the description measures it (the router's overall distance is a second, independent number - measured on device 2026-10-05 as 1.34× on a 70 km route, TODO.md §126)
- **AND** the sum SHALL NOT be the length of a single geometry edge per step (measured before this change: a few hundred metres for a 17,3 km route)

#### Scenario: A step's values belong to its own leg

- **WHEN** a step's values are inspected
- **THEN** its distance SHALL be the route distance from the previous step's manoeuvre to this one
- **AND** its duration SHALL be the travel time of that same leg
- **AND** the value SHALL NOT be the distance or time of the last geometry edge before the manoeuvre

#### Scenario: Unaligned values are absent, not shifted

- **WHEN** a route yields no described node at all
- **THEN** the route SHALL publish no step and therefore no per-step distance and no per-step duration
- **AND** the route's own geometry and totals SHALL remain valid and complete

#### Scenario: Start line owns no leg

- **WHEN** the route's first step is the start step
- **THEN** its distance SHALL be zero and it SHALL carry no duration


### Requirement: A next instruction's time is the remaining time of its leg

For the step ahead — the one published for the current position — the remaining time SHALL be the **remaining**
travel time of that step's leg, consistent with the remaining distance the same step reports, and it SHALL reach
zero as the manoeuvre is reached.

#### Scenario: The arrival estimate shrinks while the leg is driven

- **WHEN** the step ahead is published, then published again after the driver has covered most of its leg
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

### Requirement: The Android override declares the submodule's favorite file-format natives

The Android bridge override of `OSMScoutClient` SHALL declare the favorite file-format natives that the
submodule's `OSMScoutClient` source declares and the linked JNI implements, with matching signatures, so a
caller on Android reaches the same favorites API as the submodule. Without the declaration the natives are
unreachable through the class the app compiles against, because the override shadows the submodule source.

#### Scenario: The favorite file-format natives are declared on the override

- **WHEN** the Android bridge's `OSMScoutClient` class is inspected for its favorite file-format natives
- **THEN** `getFavoriteFileFormatVersion` SHALL be declared as a native method taking no arguments and
  returning an `int`
- **AND** `isFavoriteFileFormatSupported` SHALL be declared as a native method taking no arguments and
  returning a `boolean`

### Requirement: A failed routing call reports a reason

The routing entry point SHALL report a failed calculation with a reason from a closed set, in addition to
the failure callback it already invokes. A caller that reads only the existing failure callback SHALL
observe the behaviour it observes today, and a cancelled calculation SHALL NOT be reported as a failure
with a reason.

#### Scenario: The reason reaches the caller
- **WHEN** the routing entry point fails a calculation
- **THEN** the caller SHALL receive a reason from the closed set for that failure

#### Scenario: An existing caller keeps its behaviour
- **WHEN** a caller reads only the failure callback the entry point invoked before this contract existed
- **THEN** it SHALL still receive a failure notification for every failed calculation
- **AND** it SHALL NOT need to change to keep working

#### Scenario: Cancellation carries no failure reason
- **WHEN** a calculation is cancelled through the routing entry point
- **THEN** the caller SHALL receive the cancellation it receives today
- **AND** no failure reason SHALL be reported for it

#### Scenario: A thrown native fault is reported as one reason
- **WHEN** the native routing work throws instead of returning a result
- **THEN** the failure SHALL reach the caller as a reason and SHALL NOT fault the process

### Requirement: An endpoint beside a direction-restricted road is still attached

When the nearest routable way of the start or the target cannot carry that endpoint in a permitted
direction, the calculation SHALL attach the endpoint to the nearest routable way that can, and SHALL
calculate the route. A calculation SHALL NOT fail because the nearest routable way is
direction-restricted.

#### Scenario: Destination beside a one-way road
- **WHEN** the destination's nearest routable way is one-way and its route node lies behind the point
  where the destination attaches
- **AND** another routable way that can carry the destination lies within the search radius
- **THEN** the route SHALL be calculated
- **AND** the destination SHALL be attached to the nearest routable way that can carry it

#### Scenario: Start beside a direction-restricted road
- **WHEN** the start's nearest routable way is direction-restricted in the direction its route node is
  reachable from
- **AND** another routable way that can carry the start lies within the search radius
- **THEN** the route SHALL be calculated

#### Scenario: Nothing attachable within the search radius
- **WHEN** no routable way within the search radius can carry the endpoint
- **THEN** the calculation SHALL fail with the reason for that end
- **AND** the failure SHALL carry the distance from that end to the nearest road the router could use

#### Scenario: An attachable nearest way is unchanged
- **WHEN** the nearest routable way can carry the endpoint in a permitted direction
- **THEN** the endpoint SHALL be attached to that way, as before

### Requirement: A route step carries the roundabout exit ordinal

The navigation bridge SHALL expose, on every published route step that leaves a roundabout, the ordinal of the
exit the route takes: its 1-based position counted in circulation order. It SHALL be readable without parsing
any text, because no text is published. A step that is not a roundabout manoeuvre SHALL NOT carry an ordinal.

#### Scenario: A roundabout exit step carries its ordinal

- **WHEN** a route leaves a roundabout at the third exit
- **THEN** the step for that manoeuvre SHALL carry exit ordinal 3
- **AND** the ordinal SHALL NOT have to be recovered from any description text
- **Case**: `app(device): RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

#### Scenario: A non-roundabout step carries no exit ordinal

- **WHEN** the step is a plain turn, a motorway manoeuvre, the start or the arrival
- **THEN** its exit ordinal SHALL be absent
- **AND** a surface reading the ordinal SHALL be able to tell "no exit" from "exit 1"
- **Case**: `osmscout-client-java: RouteStepFieldsTest` "a plain turn carries no exit ordinal and no roundabout record"

#### Scenario: No description text accompanies the step

- **WHEN** a roundabout exit step carries its ordinal
- **THEN** the bridge SHALL publish no description or short description for that manoeuvre
- **AND** the ordinal SHALL be the only thing a client reads it from
- **Case**: `osmscout-client-java: RouteStepFieldsTest` "a published step carries no guidance text"

### Requirement: A route step carries the roundabout circulation direction

The navigation bridge SHALL expose whether the roundabout's traffic circulates clockwise, on every published
route step that leaves a roundabout, and SHALL NOT leave it to be inferred from text.

#### Scenario: The circulation direction is carried

- **WHEN** a route leaves a roundabout whose traffic circulates counter-clockwise
- **THEN** the step SHALL report the circulation direction as counter-clockwise
- **Case**: `osmscout-client-java: RouteStepFieldsTest` "a counter-clockwise roundabout reports its direction"

#### Scenario: A counter-clockwise roundabout is not reported as clockwise

- **WHEN** a route leaves a clockwise roundabout and another route leaves a counter-clockwise one
- **THEN** the two steps SHALL report different circulation directions
- **Case**: `app(device): RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

### Requirement: The next-next hint carries its own exit ordinal

The bridge SHALL expose the roundabout exit ordinal of the manoeuvre the "next next" hint describes inside the
nested step that carries the hint, separately from the enclosing step's own exit ordinal, so two roundabouts in a
row can be told apart.

#### Scenario: The hint's ordinal is its own

- **WHEN** a step leaves a roundabout at exit 3 and its next-next hint is a roundabout exit taken at
  exit 1
- **THEN** the enclosing step's own exit ordinal SHALL be 3
- **AND** the nested hint step's exit ordinal SHALL be 1, not the enclosing step's
- **Case**: `osmscout-client-java: RouteStepFieldsTest` "the next-next hint carries its own exit ordinal"

#### Scenario: No hint means no hint ordinal

- **WHEN** a step carries no next-next hint
- **THEN** no nested step SHALL be published for it
- **AND** its own exit ordinal SHALL be distinguishable from exit 1
- **Case**: `osmscout-client-java: RouteStepFieldsTest` "no exit is distinguishable from exit one"

### Requirement: An unbuildable step never reaches a listener as null

The navigation bridge SHALL deliver only steps it built: a delivered step list SHALL contain no null element, and
when it could build none of a list's steps it SHALL deliver no list at all. A listener SHALL NOT have to defend
against a null element, because one surfaces far from the bridge where its cause is no longer visible.

#### Scenario: Every delivered step is non-null

- **WHEN** a route is calculated and navigation starts, including when the bridge cannot build a step
- **THEN** every step the listener receives SHALL be non-null
- **AND** the bridge SHALL deliver no step list at all rather than a list whose elements are null
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "a route change publishes the full step list"

#### Scenario: A method lookup that fails degrades instead of aborting

- **WHEN** a class or method the bridge looks up cannot be resolved, including a descriptor that matches no
  constructor of the Java class it names
- **THEN** the lookup SHALL leave a null method id and SHALL NOT leave an exception pending
- **AND** the bridge SHALL degrade to delivering no step list rather than aborting the process, because
  a pending lookup exception aborts on the *next* JNI call and so reaches the guard before it can run
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "a route change publishes the full step list"

### Requirement: A search request carries a generation

The bridge SHALL let a caller begin a search for a named slot, obtain that request's generation, run the search
with it, and cancel it by generation. Cancelling a generation that is not current SHALL report that it aborted
nothing. An aborted search SHALL return an empty result array rather than its partial result.

#### Scenario: Beginning a search returns its own generation

- **WHEN** two searches are begun for the same slot
- **THEN** the second SHALL carry a generation different from the first
- **AND** the first SHALL be aborted

#### Scenario: Cancelling the current generation aborts it

- **WHEN** the caller cancels a search by the generation that is current for its slot
- **THEN** that search SHALL be aborted at its next abort check
- **AND** the cancellation SHALL report that it aborted one search

#### Scenario: Cancelling a stale generation aborts nothing

- **WHEN** the caller cancels a generation that a newer request of the same slot has already replaced
- **THEN** the newer search SHALL keep running
- **AND** the cancellation SHALL report that it aborted nothing

#### Scenario: An aborted search returns no entries

- **WHEN** a search is aborted by a newer request of its slot
- **THEN** the bridge SHALL return an empty array to the aborted caller

#### Scenario: One slot's search does not abort another slot's

- **WHEN** a search is begun for one slot while a search of another slot is running
- **THEN** the running search of the other slot SHALL keep running

#### Scenario: The structured form search is abortable

- **WHEN** a structured form search is aborted while it walks the installed databases
- **THEN** it SHALL stop at its next abort check
- **AND** the databases not yet visited SHALL NOT be searched

#### Scenario: The free-text pass is abortable

- **WHEN** a free-text pass is aborted between two databases
- **THEN** the remaining databases' free-text passes SHALL NOT run

### Requirement: The override mirrors the submodule's natives, except the recorded deviations

The Android override of each of the five overridden bridge files SHALL declare a native with the same name and
parameter types as every native the submodule's copy declares, except a divergence recorded in
`osmscout-client-java/native-parity.txt` with its file, direction and reason. The two sides SHALL differ by
exactly that record — in both directions — and a recording the trees no longer need SHALL fail as well. The
build gate `:app:checkNativeParity`, wired into `preBuild`, SHALL assert it.

#### Scenario: An unrecorded submodule native is refused

- **WHEN** the submodule declares a native (e.g. `setGpsMarker(double,double,double,double)`) that the override
  does not declare, and `osmscout-client-java/native-parity.txt` carries no line for it
- **THEN** the scan reports one finding naming the native, the override file that lacks it and the exact list
  line that would record it, and the same fixture with that line recorded reports none
- **Case**: `buildSrc: NativeParityScannerTest` "a new submodule native without a record is a finding"

#### Scenario: An unrecorded Android addition is refused

- **WHEN** the override declares a native (e.g. `getMaxSpeedAt(double,double)`) that the submodule does not, and
  the list carries no line for it
- **THEN** the scan reports one finding naming the added native and the submodule file that would lose it on the
  next sync
- **Case**: `buildSrc: NativeParityScannerTest` "a new override native without a record is a finding"

#### Scenario: A recording the trees no longer need is refused

- **WHEN** the list records a divergence (e.g. `searchLocations(String,int,String,long)`) that both sides now
  agree on
- **THEN** the scan reports one finding naming the stale line and saying to remove it
- **Case**: `buildSrc: NativeParityScannerTest` "a recorded deviation that no longer deviates is a finding"

#### Scenario: A malformed recording is refused

- **WHEN** a list line has three fields, an unknown direction, a file outside the five overridden ones, no
  parenthesised signature, or no reason
- **THEN** the scan reports one finding per malformed line, naming the list and the line number, so no
  allowance drops silently
- **Case**: `buildSrc: NativeParityScannerTest` "a malformed deviation line is a finding"

#### Scenario: A package qualifier and prose are not divergences

- **WHEN** the same declaration reads `java.nio.ByteBuffer` on one side and `ByteBuffer` on the other, or a
  javadoc or a `//` comment *shows* a signature
- **THEN** the two sides compare equal and the prose declares nothing
- **Case**: `buildSrc: NativeParityScannerTest` "package qualifiers and prose are not divergences"

#### Scenario: The app build registers the parity gate and wires it into preBuild

- **WHEN** the app build script is read as the project's own file
- **THEN** it registers `checkNativeParity` over the two Java sides and the recorded list, and makes `preBuild`
  depend on it, so the scan runs in every app build instead of waiting for the call site's `NoSuchMethodError`
- **Case**: `app: NativeOverrideParityGuardTest` "the app build registers the parity gate and wires it into preBuild"

#### Scenario: The recorded deviations are the tree's own

- **WHEN** the recorded list is read as the project's own file
- **THEN** every line carries four fields with a reason and one of the five overridden files, and the list
  holds exactly the divergences the two trees carry — five submodule-only, five override-only
- **Case**: `app: NativeOverrideParityGuardTest` "the recorded deviations are the tree's own"

### Requirement: A calculated route publishes structured route steps

A calculated route SHALL publish one step per described route node, in route order, each carrying that node's
manoeuvre records, its node identity, its position, its distance and time from the route start, and the legs of
its own step — all as values, none as prose. A step SHALL state which manoeuvre it is, so a client can tell a
roundabout leave from a motorway change without reading text. A node with no description SHALL produce no step.

#### Scenario: Steps arrive in route order with their manoeuvre

- **WHEN** a route of 19 manoeuvres is calculated
- **THEN** the route SHALL publish one step per described node, in route order
- **AND** each step SHALL name its manoeuvre kind and carry the records of that manoeuvre
- **Case**: `app: RouteStepModelBridgeTest` "a calculated route publishes steps in route order"

#### Scenario: A roundabout leave states its exit ordinal and direction

- **WHEN** a route leaves a roundabout at the third exit, counter-clockwise
- **THEN** that step SHALL carry exit ordinal 3 and the counter-clockwise direction
- **AND** neither value SHALL have to be recovered from text
- **Case**: `app: RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

#### Scenario: A motorway manoeuvre is distinguishable from a plain turn

- **WHEN** a route keeps left onto another motorway
- **THEN** the step SHALL report a motorway change with its move
- **AND** a client SHALL NOT have to tell it apart from a plain left turn by its text
- **Case**: `app: RouteStepModelBridgeTest` "a motorway change is not a plain turn"

#### Scenario: Each step carries the leg that ends at it

- **WHEN** a step's leg values are inspected
- **THEN** its leg distance and leg time SHALL describe the run from the preceding step's manoeuvre to its own
- **AND** the legs of all steps SHALL sum to the route's length
- **Case**: `app: RouteStepModelBridgeTest` "step legs sum to the route length"

#### Scenario: A step names the database its node belongs to

- **WHEN** a route crosses two map regions
- **THEN** each step SHALL carry its database identity
- **AND** the route SHALL publish the mapping from that identity to the map directory
- **Case**: `app: RouteStepModelBridgeTest` "steps of a cross-database route resolve their database"

### Requirement: Live guidance publishes route steps

Live guidance SHALL publish, on every position update, the step of the manoeuvre ahead as the same structured
record a calculated route publishes, extended with the distance and time remaining to that manoeuvre and, when
the following manoeuvre is within the close-manoeuvre window, that manoeuvre as a nested step. It SHALL publish
no guidance text.

#### Scenario: The step ahead carries its manoeuvre and remaining distance

- **WHEN** the vehicle is 300 m before a roundabout exit while navigating
- **THEN** the published next step SHALL describe that roundabout leave with its exit ordinal
- **AND** SHALL report 300 m, within the tolerance of the position's progress, as remaining distance
- **Case**: `app(device): RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

#### Scenario: A published step carries no guidance text

- **WHEN** any step is published to a listener
- **THEN** it SHALL carry no description string for its manoeuvre
- **AND** a client SHALL be able to word it from the values alone
- **Case**: `osmscout-client-java: RouteStepModelBridgeTest` "a published step carries no guidance text"

#### Scenario: The next-next hint is a nested step

- **WHEN** two manoeuvres are 120 m apart and the first is ahead of the vehicle
- **THEN** the published step ahead SHALL carry the second manoeuvre as a nested step
- **AND** that nested step SHALL carry its own kind, exit ordinal and circulation direction
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "the next-next hint is a nested step"

#### Scenario: A route change publishes the full step list

- **WHEN** navigation starts on a calculated route, and again when a reroute replaces it
- **THEN** the listener SHALL receive that route's steps in route order
- **AND** the list SHALL NOT contain an element that is null
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "a route change publishes the full step list"

#### Scenario: The published list follows the position

- **WHEN** the vehicle passes the manoeuvre the published list begins with
- **THEN** a later publication SHALL begin with the next manoeuvre
- **AND** the passed manoeuvre SHALL no longer be published as the next step
- **Case**: `app: NavigationEngineStepIndexTest` "the published step advances with the position"
