# Spec Delta

## Purpose

Keeps the car host alive and responsive while the NaviVeylin car session runs — foreground or
backgrounded: the app answers host callbacks promptly, never lets a fault escape into the host path,
owns exactly one car surface at a time, and bounds the traffic it sends the host.

## ADDED Requirements

### Requirement: Host callbacks answer promptly

The system SHALL answer every car-app host callback (`onSurfaceAvailable`, `onSurfaceChanged`,
`onSurfaceDestroyed`, the gesture callbacks, the stable/visible-area callbacks and template requests)
without blocking on native client construction, native database queries or file I/O. Work that needs
the native client SHALL be deferred to a background dispatcher and applied when it completes, and a
host callback SHALL only retain the state it received.

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

### Requirement: No fault escapes into the host path

The system SHALL confine a fault in car-facing work to that piece of work: building or publishing
trip metadata, updating the host navigation state, building and posting the ongoing notification,
building a template, and drawing a car frame SHALL each degrade to a logged no-op on failure. No
exception from those paths SHALL reach the process's main thread or a coroutine that owns the car
session.

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

### Requirement: Template invalidation is main-thread only

The system SHALL request a car template refresh (`Screen.invalidate`) only from the main thread,
whatever thread observed the condition that needs the refresh.

#### Scenario: Renderer reports a surface failure

- **WHEN** the renderer cannot lock the surface from its render thread
- **THEN** the template refresh is requested on the main thread

#### Scenario: Background component needs a refresh

- **WHEN** a background component (settings load, warmup, notification controller) wants the current template refreshed
- **THEN** the refresh is posted to the main thread

### Requirement: Single-owner car surface

The system SHALL keep exactly one owner for each host-delivered car surface. A screen SHALL hold and
release only a surface delivered to its own registration; it SHALL release that surface when the host
reports it destroyed or when the session owning it ends, and SHALL NOT release a surface that another
registration currently owns. After release the system SHALL NOT lock or draw the released surface.

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

#### Scenario: Session ends with a surface held

- **WHEN** the car session is destroyed while a surface is still held
- **THEN** the session releases it

### Requirement: Bounded host-facing traffic while not visible

While the car app is not the visible car app the system SHALL bound what it sends the host: the
ongoing notification SHALL be re-posted only when its host-visible content changed, trip metadata
SHALL stay content-deduplicated, and a session that is not started SHALL NOT push or pop car screens,
request template refreshes, or change the host navigation state. Guidance updates themselves SHALL
continue while navigating.

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

### Requirement: Bounded periodic render work

The system SHALL NOT drive a stylesheet reload or a full native map render from a fixed short
interval that is independent of visible change: a style flag SHALL be pushed only when the resolved
value changed, and in follow mode a further full render SHALL NOT be requested before the previous
one has landed when a full render takes longer than the request interval.

#### Scenario: Settings re-read with an unchanged style flag

- **WHEN** the shared settings are re-read and the resolved day/night value is unchanged
- **THEN** no stylesheet flag is pushed and no full render is forced

#### Scenario: Style flag changed

- **WHEN** the resolved day/night value changes
- **THEN** the flag is pushed once and the map re-renders with the new variant

#### Scenario: Render slower than the request interval

- **WHEN** a full native render takes longer than the full-render request interval
- **THEN** the loop does not queue another full render before the previous one landed

### Requirement: Host interaction is diagnosable

The system SHALL record, with timestamps, what the car session sent the host — notification posts,
trip updates, host navigation-state calls, screen pushes and pops, and car surface acquire and
release — and the duration and the thread of the native client build, so a host failure can be
correlated from the app's own diagnostics without a pre-captured log.

#### Scenario: Host-facing send is recorded

- **WHEN** the app posts the ongoing notification or publishes trip metadata
- **THEN** a diagnostics entry with the timestamp and the content that changed is written

#### Scenario: Surface lifetime is recorded

- **WHEN** a car surface is delivered, adopted, or released
- **THEN** the diagnostics record names the screen and the surface that changed

#### Scenario: Client build is recorded

- **WHEN** the native client is built
- **THEN** its duration and the thread it ran on are recorded
