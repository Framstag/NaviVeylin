# Spec Delta

## MODIFIED Requirements

### Requirement: Host callbacks answer promptly

The system SHALL answer every car-app host callback (`onSurfaceAvailable`, `onSurfaceChanged`,
`onSurfaceDestroyed`, the gesture callbacks including the surface click, the stable/visible-area
callbacks and template requests) without blocking on native client construction, native database
queries, a native stylesheet change or file I/O. Work that needs the native client SHALL be deferred
to a background dispatcher and applied when it completes, and a host callback SHALL only retain the
state it received.

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

## ADDED Requirements

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
