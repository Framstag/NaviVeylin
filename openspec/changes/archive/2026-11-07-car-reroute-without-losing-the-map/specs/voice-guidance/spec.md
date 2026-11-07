# Spec Delta

## ADDED Requirements

### Requirement: A departure from the planned route is announced once

While a navigation session is active, the vehicle becomes known to be off the planned route, and the voice switch of the session's surface is on, the engine SHALL announce the off-route state once, in the surface's localized wording. It SHALL NOT repeat the announcement while the state persists, SHALL be re-armed once the vehicle is back on a route, and SHALL stay silent once the session has ended.

#### Scenario: The departure is spoken

- **WHEN** the vehicle becomes known to be off the planned route during an active navigation session with voice on
- **THEN** one off-route announcement SHALL be spoken
- **Case**: `app: OffRouteAnnouncementTest.theDepartureIsSpokenOnce`

#### Scenario: The announcement does not repeat

- **WHEN** the vehicle stays off the planned route across further position updates
- **THEN** no further off-route announcement SHALL be spoken
- **Case**: `app: OffRouteAnnouncementTest.theAnnouncementDoesNotRepeatWhileOffRoute`

#### Scenario: A second departure is announced again

- **WHEN** the vehicle is back on a route and later leaves it again in the same session
- **THEN** the new departure SHALL be announced once
- **Case**: `app: OffRouteAnnouncementTest.aSecondDepartureIsAnnouncedAgain`

#### Scenario: Voice off stays silent

- **WHEN** the departure is detected and the surface's voice switch is off
- **THEN** no off-route announcement SHALL be spoken
- **Case**: `app: OffRouteAnnouncementTest.theAnnouncementHonoursTheVoiceSwitch`

#### Scenario: No announcement outside navigation

- **WHEN** the vehicle is off a route and no navigation session is active
- **THEN** no off-route announcement SHALL be spoken
- **Case**: `app: OffRouteAnnouncementTest.noAnnouncementOutsideASession`

#### Scenario: The wording carries no position

- **WHEN** the off-route announcement is composed
- **THEN** its wording SHALL NOT contain a coordinate or a coordinate-formatted string
- **Case**: `app: OffRouteAnnouncementTest.theWordingCarriesNoPosition`
