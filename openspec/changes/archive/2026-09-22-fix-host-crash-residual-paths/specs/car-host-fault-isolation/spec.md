# Spec Delta

## MODIFIED Requirements

### Requirement: Host callbacks answer promptly

The system SHALL answer every car-app host callback (`onSurfaceAvailable`, `onSurfaceChanged`,
`onSurfaceDestroyed`, the gesture callbacks including the surface click, the stable/visible-area
callbacks and template requests) without blocking on native client construction, native database
queries or file I/O. Work that needs the native client SHALL be deferred to a background dispatcher
and applied when it completes, and a host callback SHALL only retain the state it received.

#### Scenario: Surface delivered before the native client exists

- **WHEN** the host delivers a car surface while the native client is still being built
- **THEN** the callback returns without constructing the native client
- **AND** the delivered surface attributes are retained and applied to the renderer when it is ready

#### Scenario: Host callback during a long native operation

- **WHEN** a host callback arrives while a native render or a database open is in progress
- **THEN** the callback completes without waiting for that operation to finish

#### Scenario: Provider resolution during screen construction

- **WHEN** a car screen is constructed while the native client does not exist yet
- **THEN** the constructor returns without building the native client, and the client is built on a background dispatcher

#### Scenario: Surface tap selects a location

- **WHEN** the host forwards a click on the map surface and answering it needs the native client (candidate lookup, reverse geocoding)
- **THEN** the click callback returns without resolving the native client on the host thread
- **AND** the client is resolved inside the background work, and the host navigation (picker/details) happens on the main thread afterwards

### Requirement: No fault escapes into the host path

The system SHALL confine a fault in car-facing work to that piece of work: building or publishing
trip metadata, updating the host navigation state, building and posting the ongoing notification,
building a template, and drawing a car frame SHALL each degrade to a logged no-op on failure. No
exception from those paths SHALL reach the process's main thread or a coroutine that owns the car
session. This SHALL hold for the template build of **every** car screen — including the search,
favorites, address-book, settings, picker, diagnostics, root and about screens and the session's
error screen — not only for the map, navigation and free-driving screens.

#### Scenario: Trip metadata cannot be built

- **WHEN** the trip metadata for the current navigation state cannot be constructed
- **THEN** nothing is published to the host, the failure is logged, and navigation continues

#### Scenario: Host navigation call rejected

- **WHEN** a host navigation-state call is rejected because the host's navigation session is no longer active
- **THEN** the app treats the host navigation session as ended, logs it, and keeps running

#### Scenario: Rendering a car frame fails

- **WHEN** locking or drawing the car surface throws
- **THEN** the failure is logged, the frame is skipped, and the app does not crash

#### Scenario: Template build fails

- **WHEN** building a car template throws
- **THEN** the host receives an error template instead of the app dying

#### Scenario: Template build fails on a list, search or settings screen

- **WHEN** the template build of a car screen other than the map, navigation or free-driving view throws
- **THEN** the host receives an error template, the failure is logged with the template tag, and the process keeps running

#### Scenario: Error screen build fails

- **WHEN** the session's error screen fails to build its template
- **THEN** the host still receives a usable template and the process keeps running

### Requirement: Single-owner car surface

The system SHALL keep exactly one owner for each host-delivered car surface. A screen SHALL hold and
release only a surface delivered to its own registration; it SHALL release that surface when the host
reports it destroyed or when the session owning it ends, and SHALL NOT release a surface that another
registration currently owns. After release the system SHALL NOT lock or draw the released surface.
A host destroy notification SHALL be scoped to the surface instance it names: it SHALL NOT clear,
detach or release a surface instance other than the one reported destroyed.

#### Scenario: Screen pushed or popped

- **WHEN** one car screen is pushed over another, or the stack pops back, while the session keeps one surface
- **THEN** the outgoing screen's stop does not release the surface the incoming screen is drawing through
- **AND** the incoming screen renders normally after the transition

#### Scenario: App backgrounded and returned

- **WHEN** the car app is backgrounded and returns without the host having reported the surface destroyed
- **THEN** no screen released that surface in the meantime
- **AND** the resumed renderer can lock and draw it again

#### Scenario: Host destroys the surface

- **WHEN** the host reports the surface destroyed
- **THEN** the owning screen releases it exactly once
- **AND** no later frame locks or draws it

#### Scenario: Registration superseded

- **WHEN** a screen's surface registration has been superseded by a newer one
- **THEN** a stop or destroy of the superseded screen SHALL NOT clear or release the newer registration's surface

#### Scenario: Destroy reported for a superseded surface instance

- **WHEN** the host delivers a new surface and afterwards reports the destroy of the surface it delivered before
- **THEN** the session keeps the newer surface as its active one and the screens keep drawing through it
- **AND** only the superseded instance is released, exactly once
- **AND** no released surface is locked or drawn afterwards

#### Scenario: Session ends with a surface held

- **WHEN** the car session is destroyed while a surface is still held
- **THEN** the session releases it

### Requirement: Bounded host-facing traffic while not visible

While the car app is not the visible car app the system SHALL bound what it sends the host: the
ongoing notification SHALL be re-posted only when its host-visible content changed, trip metadata
SHALL stay content-deduplicated, and a session that is not started SHALL NOT push or pop car screens,
request template refreshes, or change the host navigation state. Guidance updates themselves SHALL
continue while navigating. Independently of visibility, a template SHALL only be rebuilt when its
displayed content changes materially — the current step or manoeuvre, the street name, the lane
state, or a distance bucket — and a template asset that the host receives per rebuild (the
lane-guidance image) SHALL be reused while the state it renders is unchanged.

#### Scenario: No content change while backgrounded

- **WHEN** the app is backgrounded with navigation active and the manoeuvre, the distance bucket, the current road, the remaining distance and the arrival time are unchanged
- **THEN** no notification is re-posted
- **AND** no trip metadata is published

#### Scenario: Guidance progresses while backgrounded

- **WHEN** the distance to the next manoeuvre crosses a reporting threshold, or the manoeuvre changes, while the app is backgrounded
- **THEN** the notification content and the trip metadata are updated for the new guidance

#### Scenario: Stopped session mutates no host state

- **WHEN** the car session is stopped (app backgrounded) and the navigation state changes
- **THEN** no screen is pushed or popped, no template refresh is requested, and no host navigation-state call is made by the session

#### Scenario: Driving continues in the background

- **WHEN** the app is backgrounded with navigation or free driving active
- **THEN** position, guidance and the notification content keep updating until the mode ends

#### Scenario: Guidance update within the same displayed bucket

- **WHEN** a position update arrives while navigating and the manoeuvre, the street name, the lane state and the displayed distance bucket are unchanged
- **THEN** no template is rebuilt and no new template asset is sent to the host

#### Scenario: Lane guidance with unchanged lanes

- **WHEN** a template is rebuilt while the suggested lanes and the lane list are unchanged
- **THEN** the lane-guidance image sent for the step is the one already built for that lane state
- **AND** a change of the lane state produces an image matching the new state

#### Scenario: Lane guidance state changes

- **WHEN** the lane list or the suggested lanes change while lane hints are active
- **THEN** the host instruction panel shows an image matching the new lane state
