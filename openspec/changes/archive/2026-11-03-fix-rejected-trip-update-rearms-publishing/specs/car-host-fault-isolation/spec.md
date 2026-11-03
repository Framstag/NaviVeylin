# Spec Delta

## ADDED Requirements

### Requirement: A rejected trip update re-arms publishing within a retry budget

A rejected `NavigationManager.updateTrip` SHALL NOT by itself end trip publishing for the rest of the
navigation period: the controller SHALL re-arm the send so the next changed trip content is sent again. A
navigation period SHALL spend at most three rejected trip updates before it stops publishing until the next
navigation start, and a rejected update SHALL NOT be resent for trip content the host already refused.

#### Scenario: A transient rejection re-arms on the next changed trip

- **WHEN** a trip update is rejected and the next changed trip content arrives in the same navigation period
- **THEN** that content is sent to the host, so guidance resumes for a host that accepts again
- **Case**: `auto: NavigationManagerControllerTest.aTransientTripRejectionRearmsPublishing`

#### Scenario: The rejected content is not resent unchanged

- **WHEN** the trip content after a rejected update has not changed
- **THEN** no trip update is sent, so a re-arm does not poll the host at the position update rate
- **Case**: `auto: NavigationManagerControllerTest.aRejectedTripIsNotResentUntilItsContentChanges`

#### Scenario: The retry budget bounds a host that keeps rejecting

- **WHEN** three trip updates in one navigation period have been rejected
- **THEN** no further trip update is sent until the next navigation start
- **Case**: `auto: NavigationManagerControllerTest.aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget`

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
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Host navigation call rejected

- **WHEN** a host navigation-state call is rejected because the host's navigation session is no longer active
- **THEN** the app logs it and keeps running, and a rejected trip update re-arms publishing under
  the capability's "A rejected trip update re-arms publishing within a retry budget" requirement
- **Case**: `auto: NavigationManagerControllerTest.aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget`

#### Scenario: Rendering a car frame fails

- **WHEN** locking or drawing the car surface throws
- **THEN** the failure is logged, the frame is skipped, and the app does not crash
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: A fault in one frame keeps the frames coming

- **WHEN** the car renderer's frame work throws for one frame (the native render, the blit, or the overlay draw)
- **THEN** that frame is skipped and recorded with the renderer's diagnostics tag and no coordinates
- **AND** the renderer draws a later frame instead of leaving the surface frozen
- **AND** the renderer still consumes the render requests that arrive after the fault
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: A fault in a display tick keeps the follow pipeline running

- **WHEN** the displayed-frame tick (extrapolation + blit) or one zoom-transition step throws
- **THEN** that tick is skipped and recorded, and the next tick runs
- **AND** the displayed frame continues to be updated afterwards
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Template build fails

- **WHEN** building a car template throws
- **THEN** the host receives an error template instead of the app dying
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Template build fails on a list, search or settings screen

- **WHEN** the template build of a car screen other than the map, navigation or free-driving view throws
- **THEN** the host receives an error template, the failure is logged with the template tag, and the process keeps running
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Error screen build fails

- **WHEN** the session's error screen fails to build its template
- **THEN** the host still receives a usable template and the process keeps running
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Screen push from a click action throws

- **WHEN** pushing a car screen from a host click action throws (rejected by the host, invalidated session, screen construction failure)
- **THEN** the failure is logged, the click action completes without navigating, and the process keeps running
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Screen mutation from a session observer throws

- **WHEN** a push or pop performed by a session observer (navigation-state, error, free-driving restore, background sync) throws
- **THEN** the failure is logged, that observation keeps running or ends on its own, and the process keeps running
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change

#### Scenario: Session lifecycle callback throws

- **WHEN** a fault occurs in the session's own start, stop or destroy handling (surface registration, host state sync, cleanup)
- **THEN** the process keeps running and the remaining cleanup still runs
- **Case**: none — the shipped scenario is repeated verbatim because this change modifies its
  requirement; the case that exercises it is unchanged by this change
