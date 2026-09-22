# car-host-fault-isolation Specification

## Purpose
Keeps the car host alive and responsive while the NaviVeylin car session runs — foreground or
backgrounded: the app answers host callbacks promptly, never lets a fault escape into the host path,
owns exactly one car surface at a time, and bounds the traffic it sends the host.

## Requirements

### Requirement: Host callbacks answer promptly

The system SHALL answer every car-app host callback (`onSurfaceAvailable`, `onSurfaceChanged`,
`onSurfaceDestroyed`, the gesture callbacks including the surface click, the stable/visible-area
callbacks and template requests) without blocking on native client construction, native database
queries, a native stylesheet change or file I/O. Work that needs the native client SHALL be deferred
to a background dispatcher and applied when it completes, and a host callback SHALL only retain the
state it received. A control that leads to a screen change (a template row action, a search result, a
map candidate) SHALL likewise not construct a car screen, resolve the native client, mutate the screen
stack or do file I/O on the host's answering path: the resulting navigation SHALL be performed on the
main thread outside the callback.

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

#### Scenario: Surface delivered with a day/night state

- **WHEN** the host delivers a car surface and the resolved day/night state has to be applied to the native style sheet
- **THEN** the delivery callback returns without changing a native style sheet flag
- **AND** the flag is published as state and applied by a background collector, so the callback does no native work

#### Scenario: Surface delivered while the client is initialising

- **WHEN** the host delivers a surface at the moment the native client or its database is still initialising
- **THEN** the delivery callback returns without waiting for that initialisation
- **AND** the day/night flag is applied once the client is usable, without a later delivery being required

#### Scenario: Template row action opens a screen

- **WHEN** a host click action on a car screen leads to a screen push (search result, POI result, address book entry, details "Show on map", candidate pick)
- **THEN** the click callback returns without constructing the target screen and without pushing or popping on the host thread
- **AND** the push happens on the main thread afterwards, and a rejected push degrades to a logged no-op

### Requirement: No fault escapes into the host path

The system SHALL confine a fault in car-facing work to that piece of work: building or publishing
trip metadata, updating the host navigation state, building and posting the ongoing notification,
building a template, drawing a car frame, and **every mutation of host state — pushing or popping a
car screen, changing the host navigation state, registering or clearing the surface callback, posting
a notification** SHALL each degrade to a logged no-op on failure. No exception from those paths SHALL
reach the process's main thread or a coroutine that owns the car session. This SHALL hold **wherever
the work runs**: in a host callback, in a screen's own coroutine, in a coroutine that owns the car
session, and in the session's lifecycle callbacks; the scopes that own host-mutating work SHALL carry
a fault handler, so a fault ends that piece of work instead of the app process. This SHALL hold for
the template build of **every** car screen — including the search, favorites, address-book, settings,
picker, diagnostics, root and about screens and the session's error screen — not only for the map,
navigation and free-driving screens.

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

#### Scenario: Screen push from a click action throws

- **WHEN** pushing a car screen from a host click action throws (rejected by the host, invalidated session, screen construction failure)
- **THEN** the failure is logged, the click action completes without navigating, and the process keeps running

#### Scenario: Screen mutation from a session observer throws

- **WHEN** a push or pop performed by a session observer (navigation-state, error, free-driving restore, background sync) throws
- **THEN** the failure is logged, that observation keeps running or ends on its own, and the process keeps running

#### Scenario: Session lifecycle callback throws

- **WHEN** a fault occurs in the session's own start, stop or destroy handling (surface registration, host state sync, cleanup)
- **THEN** the process keeps running and the remaining cleanup still runs

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

The system SHALL keep exactly one owner for each host-delivered car surface: the car session, which
registers as the surface callback once per session and is the only component that releases a
surface. A screen or its renderer SHALL NOT release a surface; it SHALL attach to the session as the
drawing owner while it is started and detach on stop, and a detached screen SHALL NOT hold a
reference to, lock or draw the session's surface. After a release the system SHALL NOT lock or draw
the released surface, and it SHALL NOT adopt an already released instance for drawing.

Surface lifetime SHALL be tracked per **delivery**: each delivered instance SHALL be released exactly
once, a repeated destroy signal for the same instance SHALL NOT release it a second time, and an
instance delivered again after a destroy SHALL be treated as a new delivery — it SHALL be released
again when it is superseded or destroyed, and it SHALL be drawable until then. A host destroy
notification SHALL be scoped to the surface instance it names: it SHALL NOT clear, detach or release
a surface instance other than the one reported destroyed.

#### Scenario: Screen pushed or popped

- **WHEN** one car screen is pushed over another, or the stack pops back, while the session keeps one surface
- **THEN** the outgoing screen's stop does not release the surface the incoming screen is drawing through
- **AND** the incoming screen renders normally after the transition

#### Scenario: Incoming screen starts before the outgoing screen stops

- **WHEN** the host starts an incoming car screen while the outgoing screen is still started (the car-app contract), and both have a renderer
- **THEN** at most one of the two renderers locks or draws the session surface at any moment
- **AND** the outgoing screen stops drawing before the incoming screen's first frame, and no frame is drawn by a screen that is no longer the owner

#### Scenario: App backgrounded and returned

- **WHEN** the car app is backgrounded and returns without the host having reported the surface destroyed
- **THEN** no screen released that surface in the meantime
- **AND** the resumed renderer can lock and draw it again, after a full re-render of its frame

#### Scenario: Host destroys the surface

- **WHEN** the host reports the surface destroyed
- **THEN** the session releases the held instance exactly once
- **AND** no later frame locks or draws it

#### Scenario: Surface instance delivered again after its destroy

- **WHEN** the host delivers a surface instance that the session already released
- **THEN** the instance is treated as a new delivery and may be drawn on
- **AND** it is released exactly once when it is superseded or destroyed afterwards

#### Scenario: Repeated destroy signal for one instance

- **WHEN** the host reports the destroy of the same surface instance more than once
- **THEN** it is released once in total, and the duplicate signal releases nothing else

#### Scenario: Destroy reported for a superseded surface instance

- **WHEN** the host delivers a new surface and afterwards reports the destroy of the surface it delivered before
- **THEN** the session keeps the newer surface as its active one and the screens keep drawing through it
- **AND** only the superseded instance is released, exactly once
- **AND** no released surface is locked or drawn afterwards

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
continue while navigating. Independently of visibility, a template SHALL only be rebuilt when its
displayed content changes materially — the current step or manoeuvre, the street name, the lane
state, or a distance bucket — and a template asset that the host receives per rebuild (the
lane-guidance image) SHALL be reused while the state it renders is unchanged. A host mutation that was
scheduled while the session was started SHALL be discarded when it would run after the session stopped
or ended, instead of being applied to a host connection that no longer carries this session.

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

#### Scenario: Deferred mutation after the session ended

- **WHEN** a host mutation was scheduled while the session was started (for example an error overlay's delayed auto-dismiss) and the session stops, is destroyed, or loses its host connection before it runs
- **THEN** no host call is made for that mutation
- **AND** the local state it would have cleared is cleared without touching the host

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

### Requirement: Session registration follows the host session

The system SHALL register the car surface callback with the car context of the session that is
starting, so that a session which begins after the previous host connection ended (a dropped
connection, a host restart) registers its own callback and never calls host APIs through the context
of a session that has ended. A session that starts with a context different from the registered one
SHALL replace the registration; ending a session SHALL clear it.

#### Scenario: Session starts after a dropped connection

- **WHEN** the host drops the connection so that the previous session never ran its end and the host then starts a new session
- **THEN** the new session's surface callback is registered with the new car context
- **AND** the host's surface delivery for the new session reaches the app, so the map draws again

#### Scenario: Session registration is not duplicated within one session

- **WHEN** one session starts more than once (lifecycle re-entry) with the same car context
- **THEN** the surface callback is registered once and no second registration is made

#### Scenario: Session ends

- **WHEN** a car session ends
- **THEN** its surface callback registration is cleared, and a later session registers with its own context

### Requirement: Host screen-stack mutations are balanced

The system SHALL keep its screen-stack bookkeeping consistent with the stack it actually created: the
car session SHALL pop only screens it pushed, SHALL NOT remove a screen from the stack whose state
the session still relies on, and a transient overlay SHALL pop only itself. Screen-stack bookkeeping
SHALL be updated from the mutation that succeeded, never in advance of it.

#### Scenario: Error raised while navigating

- **WHEN** the app raises an error notice while navigation is active
- **THEN** the notice is shown over the navigation view
- **AND** the navigation view remains on the stack when the notice is dismissed, and guidance stays visible

#### Scenario: Error notice auto-dismissed

- **WHEN** an error notice is dismissed
- **THEN** only the notice is removed from the stack
- **AND** the screen that was underneath it is unchanged and still current

#### Scenario: Repeated errors

- **WHEN** errors are raised repeatedly in one session
- **THEN** the stack does not grow unboundedly and the screen the errors are shown over is unchanged

#### Scenario: Rejected screen push

- **WHEN** a screen push is rejected by the host
- **THEN** the app does not treat that screen as present
- **AND** a later request for the same screen pushes it again
