# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
