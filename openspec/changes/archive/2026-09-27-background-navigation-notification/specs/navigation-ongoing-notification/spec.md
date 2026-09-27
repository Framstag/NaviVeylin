## Purpose

Keeps navigation guidance visible and the process alive while NaviVeylin drives in the background: an ongoing, silent notification shows live turn-by-turn (NAVIGATION) or current-street/speed (FREE_DRIVE) content on the phone, on Android Auto projection, and on Android Automotive OS head units.

## ADDED Requirements

### Requirement: Active-driving notification
The system SHALL show an ongoing, silent notification whenever the map mode is NAVIGATION or FREE_DRIVE (as defined by the map-modes capability), whether the app is in the foreground or background, on the phone and in the car. The notification SHALL NOT be shown in BROWSE mode.

#### Scenario: Navigation started, app backgrounded on phone
- **WHEN** the user starts navigation and moves the app to the background (another app foregrounded)
- **THEN** an ongoing notification remains visible showing live navigation guidance

#### Scenario: Free driving active, app backgrounded on the car
- **WHEN** free driving is active on the head unit and the user switches to another car app
- **THEN** an ongoing notification remains visible in the car notification shade showing live free-driving content

#### Scenario: Notification hidden when driving ends
- **WHEN** the user stops navigation or exits free-driving mode (map returns to BROWSE)
- **THEN** the notification is removed

#### Scenario: No notification while browsing
- **WHEN** the map is in BROWSE mode and the app goes to the background
- **THEN** no navigation notification is shown

### Requirement: Process survival during background driving
While NAVIGATION or FREE_DRIVE is active, the system SHALL keep the process running in the background so GPS updates and guidance continue; the ongoing notification is the carrier of that protection. The system SHALL NOT resurrect a dead driving session: if the process is killed, nothing SHALL claim navigation is still running.

#### Scenario: Process protected while driving in background
- **WHEN** the app drives in the background in NAVIGATION or FREE_DRIVE mode
- **THEN** the operating system does not reap the process, and GPS/guidance state keeps updating until the mode ends

#### Scenario: No lying notification after a kill
- **WHEN** the process was killed while in the background and the Android system restarts a service
- **THEN** no notification is posted that shows guidance, because the in-process navigation state is gone

### Requirement: Navigation guidance content
While NAVIGATION is active and driving continues, the notification SHALL show, updated live: the next manoeuvre instruction with the street/towards name (same wording as the on-screen next-turn display), the distance to the manoeuvre, the arrival time or remaining time, the remaining route distance, and the destination when known. If the destination is unknown (route via coordinates only), a neutral "Navigation active" title SHALL be used.

#### Scenario: Turn-by-turn guidance in the shade
- **WHEN** navigation is active and the app is not visible
- **THEN** the notification shows the current instruction (e.g. "Turn left into Hauptstraße"), the distance to the turn, an arrival/remaining-time value, the remaining distance, and the destination name

#### Scenario: Guidance updates while driving
- **WHEN** the next manoeuvre changes (turn completed) or the distance-to-turn value passes a reporting threshold
- **THEN** the notification content is refreshed without user interaction and without sound

### Requirement: Stop action for navigation
While NAVIGATION is active, the notification SHALL offer an action that ends the navigation (route guidance stopped, map returns to BROWSE, notification removed).

#### Scenario: Stopping navigation from the shade
- **WHEN** the user triggers the stop action on the notification
- **THEN** the active navigation ends, the notification disappears, and the map returns to BROWSE

### Requirement: Free-driving content
While FREE_DRIVE is active, the notification SHALL show, updated live, the current street name/ref (the road the vehicle is driving on) and the current speed. It SHALL NOT show any destination-dependent guidance or an arrival time. Free driving SHALL remain phone-only: the app is not the active navigation app while free driving, so no car turn-by-turn hint and no trip metadata are offered to the car host (documented platform deviation; the car surfaces are specified by the `auto-navigation-hints` capability in the change `car-turn-by-turn-rail-widget`).

#### Scenario: Free-driving street and speed in the shade
- **WHEN** free driving is active and the app is backgrounded
- **THEN** the notification shows the current street/ref and current speed, refreshed as the vehicle moves through the road network

#### Scenario: Free driving has no car surface
- **WHEN** free driving is active and the driver switches to another car app
- **THEN** no car turn-by-turn hint and no trip metadata are published for the free-driving session

### Requirement: Return to the app from the notification
Tapping the notification SHALL bring the app (phone UI, or the car navigation view on an automotive device) back to the front and restore the active driving context.

#### Scenario: Tap restores the driving app
- **WHEN** the user taps the notification while the app is backgrounded
- **THEN** the app returns to the foreground showing the active navigation or free-driving view
- **AND** guidance continues / resumes without restarting

### Requirement: Notification permission degradation
The notification system SHALL degrade gracefully when the user has denied notification permission (API 33+): driving continues (guidance, process protection) without crash; the notification may be hidden from the shade. The permission state SHALL be handled through the same notification-permission flow as other notifications.

#### Scenario: Denied notification permission
- **WHEN** the user denied the notification-permission request and then starts navigation
- **THEN** navigation and process protection still function, and no crash or error occurs

#### Scenario: Granted notification permission
- **WHEN** the user granted the notification-permission request
- **THEN** the active-driving notification appears in the shade under the navigation channel settings

### Requirement: Surface parity
Label and guidance-content parity SHALL hold across the phone shade and the on-screen guidance: the same destination/instruction wording, the same distance and arrival labels. The car surfaces SHALL be the rail widget at the bottom of the car screen (rendered by the host from the turn-by-turn notification the app extends with a `CarAppExtender`) and an optional heads-up notification — NOT the car Notification Center, which by design never shows turn-by-turn navigation notifications. Car rendering MAY use car-specific text roles (instruction first) through `CarAppExtender` content. Free driving SHALL remain phone-only. Platform constraints that force deviation SHALL be documented (`guidelines/UI.md`).

#### Scenario: Same guidance wording on phone and car
- **WHEN** the same navigation session is shown in the phone shade and as a car hint
- **THEN** the manoeuvre instruction wording is the same on both, while the car hint shows it as its primary text and the phone notification keeps the destination first

#### Scenario: Car surfaces are the rail widget and the heads-up notification
- **WHEN** navigation is active and the driver leaves the navigation view
- **THEN** the host renders the hint in the rail widget, and a heads-up notification is not expected (turn hints are rail-only)
