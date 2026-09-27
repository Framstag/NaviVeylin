# auto-navigation-hints Specification

## Purpose
Keeps turn-by-turn guidance visible on the car screen when the driver is not looking at
the navigation view: the app participates in the host's turn-by-turn notification
contract so the host can render the current manoeuvre in the rail widget at the bottom
of the car screen, and it publishes trip metadata so the vehicle cluster and heads-up
display can show the same guidance. Covers both Android Auto (projection) and Android
Automotive OS head units.

## Requirements

### Requirement: Turn-by-turn notification contract while navigating

While NAVIGATION mode is active, the ongoing navigation notification SHALL satisfy the
car host's turn-by-turn (TBT) contract: it SHALL be ongoing, SHALL carry the
`Notification.CATEGORY_NAVIGATION` category, and SHALL be extended with
`androidx.car.app.notification.CarAppExtender` — even when the extender overrides no
property — because a connected host only recognizes a TBT notification when it is
extended. The navigation session SHALL have declared itself as the active navigation
app (`NavigationManager.navigationStarted`) before hints are posted.

#### Scenario: Navigation notification is extended

- **WHEN** navigation is active and the ongoing notification is posted or updated
- **THEN** the notification is ongoing, has category `CATEGORY_NAVIGATION` and is
  extended with a `CarAppExtender` (verified through the published notification)

#### Scenario: Active navigation app is declared before hints

- **WHEN** navigation starts and hint data becomes available
- **THEN** the host has already been told the app is navigating, so the TBT
  notification is eligible for car rendering

#### Scenario: No hint contract outside navigation

- **WHEN** navigation is not active (browse mode or free driving)
- **THEN** no TBT notification is offered to the car host

### Requirement: Turn hints in the car rail widget

While navigation is active and the app is not showing routing information in its
navigation template, the host SHALL be able to render the current manoeuvre in the rail
widget at the bottom of the car screen, and the app SHALL keep that content current as
the drive progresses.

#### Scenario: Rail widget shows the current maneuver

- **WHEN** navigation is active and the driver switches to another car app
- **THEN** the rail widget can show the current manoeuvre with its direction and
  distance

#### Scenario: Rail widget content follows the drive

- **WHEN** the distance to the next manoeuvre or the manoeuvre itself changes
- **THEN** the notification is updated so the rail widget shows the new values

#### Scenario: No car hints while the routing card is shown

- **WHEN** the app displays routing information in its navigation view
- **THEN** the host suppresses TBT hints (the navigation view already shows the
  manoeuvre), and the app does not treat the suppressed hint as an error

### Requirement: Car hint content

The car rendering of the hint SHALL show the manoeuvre instruction as the primary text,
the distance to the manoeuvre and the arrival time as secondary text, and the manoeuvre
direction as a large icon. The instruction wording SHALL be the same wording the
on-screen and phone displays use (label parity); the car MAY use different text roles
(instruction first) than the phone notification, which keeps the destination name
first.

#### Scenario: Instruction is the primary car text

- **WHEN** a next manoeuvre is available while navigating
- **THEN** the car hint's primary text is the manoeuvre instruction with the same
  wording as the on-screen next-turn display

#### Scenario: Distance and arrival time as secondary text

- **WHEN** the car hint is rendered while navigating
- **THEN** its secondary text contains the distance to the manoeuvre and the arrival
  time

#### Scenario: Maneuver direction as large icon

- **WHEN** the car hint is rendered while navigating
- **THEN** a large icon shows the manoeuvre direction (turn arrow)

#### Scenario: Neutral hint without a maneuver

- **WHEN** navigation is active but no next manoeuvre is available (for example while
  rerouting or before the first instruction)
- **THEN** the car hint shows neutral navigation text instead of a manoeuvre and does
  not crash

### Requirement: End navigation from the car hint

The car hint SHALL offer an action that ends navigation, identified by a drawable icon,
and invoking it from the rail widget SHALL end the navigation session the same way the
on-screen and phone stop actions do.

#### Scenario: Stop action offered in the car hint

- **WHEN** the car hint is rendered while navigating
- **THEN** it carries an end-navigation action with a valid drawable icon

#### Scenario: Stop action ends navigation

- **WHEN** the driver invokes the end-navigation action on the car hint
- **THEN** navigation ends and the hint is withdrawn

### Requirement: Notification importance per surface

The phone notification channel SHALL keep low importance (silent, no badge, no
heads-up notification). On Android Automotive OS the hint SHALL use a notification
channel of `IMPORTANCE_DEFAULT` or above, because the platform does not represent
foreground-service notifications with importance `LOW` or below at all, regardless of
category. The app SHALL NOT request heads-up notifications for turn hints: hints are
rail-widget content, and repeated heads-up notifications distract the driver.

#### Scenario: Phone stays silent

- **WHEN** the ongoing notification is posted on a phone
- **THEN** its channel importance is low and no sound or badge is produced

#### Scenario: Automotive represents the hint

- **WHEN** the ongoing notification is posted on Android Automotive OS
- **THEN** it uses a channel with importance `IMPORTANCE_DEFAULT` or above, so the car
  surface represents it

#### Scenario: No heads-up notification for turn hints

- **WHEN** the hint is updated during a drive
- **THEN** no heads-up notification is triggered (importance is never raised to
  `IMPORTANCE_HIGH` and updates alert at most once)

### Requirement: No car surface for free driving

Free driving SHALL remain phone-only: the system SHALL NOT expect a rail-widget hint,
a TBT notification, or trip metadata while free driving, because the app is not the
active navigation app and `NavigationManager.updateTrip` rejects calls issued without
`navigationStarted`. This platform constraint SHALL be documented in
`guidelines/UI.md` next to the cross-surface parity rule.

#### Scenario: Free driving produces no car hint

- **WHEN** free driving is active and the driver switches to another car app
- **THEN** no rail-widget hint and no trip metadata are offered to the host

#### Scenario: Deviation is documented

- **WHEN** the change is complete
- **THEN** `guidelines/UI.md` states that the ongoing notification reaches the car only
  as a turn-by-turn hint while navigating, and that free driving stays phone-only

### Requirement: Trip metadata for cluster and heads-up display

While navigating, the app SHALL publish trip metadata to the car host through
`NavigationManager.updateTrip`: the current step (manoeuvre direction, cue, road), the
step's travel estimate (remaining distance, arrival time, remaining time) and the
destination. While the route is recalculating it SHALL publish a loading trip without
step data. It SHALL stop publishing when navigation ends or the host requests
navigation stop, and it SHALL never publish after the navigation session has ended.

#### Scenario: Trip published during navigation

- **WHEN** navigation is active and the host is connected
- **THEN** the host receives a trip whose first step matches the current maneuver with
  the same distance shown on screen

#### Scenario: Trip updates on progress

- **WHEN** the remaining distance, remaining time or current maneuver changes
- **THEN** the published trip is updated with the new values

#### Scenario: Loading trip while recalculating

- **WHEN** the route is recalculated during navigation
- **THEN** the published trip reports a loading state and carries no step data

#### Scenario: Publishing stops with navigation

- **WHEN** navigation ends or the host requests navigation stop
- **THEN** no further trip updates are published

### Requirement: Trip publishing cadence

The app SHALL publish trip metadata only when a displayed value changes (maneuver,
rounded distance, remaining time, or loading state) and SHALL NOT publish on every
position fix, so the host is not flooded at the position update rate.

#### Scenario: Unchanged values are not republished

- **WHEN** a position fix arrives without changing any displayed value
- **THEN** no trip update is sent to the host

#### Scenario: Changed distance is republished

- **WHEN** the rounded distance or remaining time changes
- **THEN** exactly one trip update is sent

### Requirement: Hint teardown without host connection

Cleaning up the car session mid-navigation SHALL NOT throw and SHALL NOT leave hint
publication running: the session SHALL stop trip updates and withdraw hint ownership
best-effort when the host connection is already gone.

#### Scenario: Session destroyed mid-navigation

- **WHEN** the car session is destroyed while navigation is active
- **THEN** cleanup completes without an exception and no further trip updates or hint
  updates are attempted
