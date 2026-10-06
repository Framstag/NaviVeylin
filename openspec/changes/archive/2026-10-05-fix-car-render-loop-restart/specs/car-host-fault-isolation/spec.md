# Spec Delta

## MODIFIED Requirements

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
navigation and free-driving screens. Work that a car map performs as one iteration of a continuing
loop — a rendered frame, a blitted displayed frame, an eased zoom step — SHALL be confined to that
iteration: a fault SHALL skip the iteration, be recorded, and leave the loop running, so no fault may
permanently stop a car map's frame pipeline or detach it from the state it consumes.

#### Scenario: Trip metadata cannot be built

- **WHEN** the trip metadata for the current navigation state cannot be constructed
- **THEN** nothing is published to the host, the failure is logged, and navigation continues

#### Scenario: Host navigation call rejected

- **WHEN** a host navigation-state call is rejected because the host's navigation session is no longer active
- **THEN** the app treats the host navigation session as ended, logs it, and keeps running

#### Scenario: Rendering a car frame fails

- **WHEN** locking or drawing the car surface throws
- **THEN** the failure is logged, the frame is skipped, and the app does not crash

#### Scenario: A fault in one frame keeps the frames coming

- **WHEN** the car renderer's frame work throws for one frame (the native render, the blit, or the overlay draw)
- **THEN** that frame is skipped and recorded with the renderer's diagnostics tag and no coordinates
- **AND** the renderer draws a later frame instead of leaving the surface frozen
- **AND** the renderer still consumes the render requests that arrive after the fault

#### Scenario: A fault in a display tick keeps the follow pipeline running

- **WHEN** the displayed-frame tick (extrapolation + blit) or one zoom-transition step throws
- **THEN** that tick is skipped and recorded, and the next tick runs
- **AND** the displayed frame continues to be updated afterwards

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

## ADDED Requirements

### Requirement: A repeatedly faulting car renderer recovers, then degrades visibly

The system SHALL bound repeated faults in a car map's frame work instead of retrying forever or
freezing silently. After a threshold number of consecutive confined faults within a window, the car
map renderer SHALL be re-created and re-attached to the current car surface, and this recovery SHALL
be attempted at most a capped number of times per started screen period. When the threshold is reached
again after the cap, the renderer SHALL stop retrying and the car screen SHALL state that the map is
unavailable. A successful iteration SHALL reset the consecutive count; a fresh start of the screen
SHALL re-arm the recovery budget. Every skipped iteration, re-creation and degraded transition SHALL
be recorded in diagnostics with identity only — the loop, the throwable class and the attempt count,
never a coordinate.

#### Scenario: Threshold reached re-creates the renderer

- **WHEN** one car map loop faults on the threshold number of consecutive iterations within the window
- **THEN** the renderer is re-created and the re-creation is recorded with its attempt count
- **AND** the car map draws frames again without the driver re-entering the screen

#### Scenario: Re-created renderer draws on the session's surface

- **WHEN** the renderer is re-created while the session holds a surface
- **THEN** the new renderer draws on that surface
- **AND** no component releases the surface, and a surface instance the host already superseded is not adopted

#### Scenario: Threshold reached again after the cap

- **WHEN** the threshold is reached again after the capped number of re-creations for this started screen period
- **THEN** no further renderer is created, the retry stops, and the car screen states that the map is unavailable

#### Scenario: Successful iterations reset the count

- **WHEN** the loop completes an iteration without a fault between two faults
- **THEN** the consecutive-fault count restarts, so isolated faults do not accumulate towards a re-creation

#### Scenario: Fresh screen start re-arms the recovery budget

- **WHEN** the driver leaves the car map screen and it is started again
- **THEN** the recovery budget is available again, and a fault storm of the previous period does not keep the new period degraded from its first frame

#### Scenario: Degraded transition is recorded without coordinates

- **WHEN** the renderer enters the degraded state
- **THEN** one diagnostics entry names the loop, the throwable class and the attempt count
- **AND** the entry carries no position, no message text from the throwable and no surface pixels
