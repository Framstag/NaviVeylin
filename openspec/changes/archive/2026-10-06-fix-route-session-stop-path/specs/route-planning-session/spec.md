# Spec Delta

## MODIFIED Requirements

### Requirement: Reviewing a route during navigation is read-only

While turn-by-turn navigation is active, a session opened on the map SHALL be a read-only view of the route: the start and destination fields SHALL NOT be editable, and the session SHALL NOT offer to calculate or clear the route. It SHALL offer the analysis of a step through whichever selector the layout provides — the step navigator on the phone, the step list in a docked wide layout — together with the route summary and the Stop Navigation action. Whenever the session carries no route of its own, the review SHALL present the route navigation is running on: its summary and its steps SHALL be shown, so the read-only review is never an empty panel with editable-looking fields.

#### Scenario: Fields are not editable during navigation

- **WHEN** turn-by-turn navigation is active and the user opens the session
- **THEN** the start and destination fields SHALL NOT accept input
- **AND** the calculate and clear actions SHALL NOT be offered

#### Scenario: Analysis stays available during navigation

- **WHEN** turn-by-turn navigation is active and the session is open
- **THEN** the step navigator SHALL be shown (a docked wide layout shows the step list) and a step SHALL be analysable
- **AND** Stop Navigation SHALL be offered

#### Scenario: The review shows the route navigation is running

- **WHEN** turn-by-turn navigation is active
- **AND** the user opens a session that carries no route of its own
- **THEN** the review SHALL show the route navigation is running
- **AND** its route summary (the route's distance and duration) SHALL be shown
- **AND** its steps SHALL be listed in the step navigator with the current navigation step marked

#### Scenario: The review's Stop Navigation is reachable

- **WHEN** turn-by-turn navigation is active and the session is open
- **THEN** a Stop Navigation action SHALL be part of the session's actions
- **AND** activating it SHALL stop navigation

#### Scenario: The review is the surface while it is open

- **WHEN** turn-by-turn navigation is active and the user opens the session — in whichever anchor the
  session opens
- **THEN** the review SHALL be visible on the phone's bottom band, with its own actions (the step
  navigator's controls, Stop Navigation) drawn and tappable
- **AND** the routing status card SHALL NOT occupy that band while the session's card is shown, and
  SHALL return when the session's surface closes

### Requirement: Grace period after navigation is stopped

When the user stops navigation during a session the session SHALL enter a stopped state: the route SHALL stay drawn, the camera SHALL stay free, and the overlay SHALL offer Restart and End. The stopped state SHALL end within a bounded grace period and SHALL end immediately on Restart or End. When the grace period expires the session SHALL end and the route SHALL be cleared. Every phone stop control SHALL enter this state while a session is open — the routing status card's stop control and the review's Stop Navigation included. Stopping navigation while no session is open SHALL end navigation and clear the route without a stopped state.

#### Scenario: Stop shows Restart and End

- **WHEN** the user taps Stop Navigation during a session
- **THEN** the session SHALL enter the stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: The status card's stop enters the stopped state

- **WHEN** a session is open during active navigation
- **AND** the user taps the routing status card's stop control
- **THEN** the session SHALL enter the stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: Stopping with no session open has no grace

- **WHEN** no session is open and the user stops navigation from the routing status card
- **THEN** navigation SHALL end and the route SHALL be cleared
- **AND** no stopped state and no grace period SHALL be involved

#### Scenario: Restart resumes navigation

- **WHEN** the session is in the stopped state
- **AND** the user taps Restart
- **THEN** navigation SHALL start again on the same route
- **AND** the route SHALL NOT need recalculation

#### Scenario: Grace expiry clears the route

- **WHEN** the session is in the stopped state
- **AND** the grace period elapses without Restart or End
- **THEN** the session SHALL end
- **AND** the route polyline and markers SHALL be removed from the map

#### Scenario: End now clears immediately

- **WHEN** the session is in the stopped state
- **AND** the user taps End
- **THEN** the session SHALL end and the route SHALL be cleared without waiting for the grace period

#### Scenario: Grace state does not survive process death

- **WHEN** the app is killed and restarted while a session is in the stopped state
- **THEN** no session SHALL be restored
- **AND** no route polyline or markers SHALL be drawn
