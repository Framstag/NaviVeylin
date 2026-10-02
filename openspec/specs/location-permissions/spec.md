# location-permissions Specification

## Purpose

Manages Android runtime location permissions so the app can access GPS position updates, with proper rationale and graceful degradation when permission is denied.

## Requirements

### Requirement: Runtime permission request
The system SHALL request `ACCESS_COARSE_LOCATION` **and** `ACCESS_FINE_LOCATION` together in a single runtime request, so the platform may grant either the approximate or the precise scope. The request SHALL be triggered when the map screen is first displayed and no location permission has been granted.

- Both permissions SHALL be requested in one request; a fine-only request is not made (on API 31+ the system ignores it)
- The three outcomes SHALL be handled explicitly: precise granted, approximate granted, denied
- An approximate grant SHALL be a working state, not a failure state
- After a denial the app SHALL NOT request again automatically; the existing rationale/`Don't ask again` path stays
- The granted scope SHALL be re-read whenever the app resumes, because it can change in system settings

#### Scenario: Both scopes requested on first display

- **WHEN** the map screen is displayed for the first time
- **THEN** the system SHALL show the platform permission dialog for the location permission group (approximate and precise as the platform presents them)
- **AND** the request SHALL contain both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`

#### Scenario: Permission granted on first request

- **WHEN** the map screen is displayed for the first time
- **THEN** the system SHALL show the Android system permission dialog for location
- **WHEN** the user taps "Allow"
- **THEN** the system SHALL start receiving GPS location updates

#### Scenario: Precise grant starts precise updates

- **WHEN** the user grants precise location
- **THEN** the system SHALL start receiving GPS location updates at the precise accuracy class

#### Scenario: Approximate grant is a working state

- **WHEN** the user grants approximate location only
- **THEN** the system SHALL start receiving location updates at the approximate accuracy class
- **AND** the map SHALL follow those fixes, showing the fix accuracy
- **AND** no error SHALL be shown for the grant itself

#### Scenario: Upgrading from approximate to precise while running

- **WHEN** the user changes the grant to precise in system settings and returns to the app
- **THEN** the next update cycle SHALL use the precise accuracy class without an app restart

#### Scenario: Permission denied

- **WHEN** the user taps "Deny" on the permission dialog
- **THEN** the system SHALL NOT request permission again automatically
- **THEN** the map SHALL display normally without a location marker
- **THEN** the system SHALL NOT crash or show error dialogs

#### Scenario: Permission denied with "Don't ask again"

- **WHEN** the user denies permission with "Don't ask again" checked
- **THEN** the system SHALL show a rationale dialog explaining why location access is needed and directing the user to Settings

### Requirement: Graceful degradation without permission

When location permission is not granted, the system SHALL continue to function normally. The map SHALL render, search SHALL work, favorites SHALL work, and all other features SHALL be unaffected. Only the GPS location marker SHALL be absent, and starting a route is refused with an explanation (see "Starting navigation requires precise location").

#### Scenario: All features work without location

- **WHEN** location permission is denied
- **THEN** the map SHALL render at the last known or default viewport
- **THEN** search SHALL return results normally
- **THEN** favorites SHALL load and display normally
- **THEN** no error messages related to location SHALL be shown to the user until a location-dependent action is attempted

### Requirement: The granted accuracy class governs provider updates
The system SHALL derive one accuracy class — `PRECISE`, `APPROXIMATE` or `NONE` — from the two runtime grants, and SHALL request updates that match it.

- `PRECISE` SHALL request high-accuracy updates
- `APPROXIMATE` SHALL request a lower-power update configuration rather than a high-accuracy one
- `NONE` SHALL start no provider updates
- On the non-Fused fallback, only the providers the grant allows SHALL be requested (the GPS provider requires the precise grant; network and passive providers do not)
- The class SHALL be one shared rule, so phone and car agree on what a grant allows
- A grant change SHALL take effect on the next update cycle, without a process restart

#### Scenario: Approximate grant uses a lower-power request

- **WHEN** only the approximate grant is held
- **THEN** the update request SHALL use the lower-power configuration, not the high-accuracy one
- **AND** updates SHALL still be delivered

#### Scenario: Fallback path respects the grant

- **WHEN** the app runs without Google Play Services and holds only the approximate grant
- **THEN** the fallback provider requests SHALL NOT include the GPS provider
- **AND** updates from the remaining allowed providers SHALL be delivered and SHALL not raise an unhandled security exception

#### Scenario: No grant starts nothing

- **WHEN** neither grant is held
- **THEN** no provider updates SHALL be started

### Requirement: Starting navigation requires precise location
The system SHALL refuse to start a route when the precise location grant is not held, and SHALL explain why and how to obtain it.

- The refusal SHALL apply to every entry point that starts a route, on both surfaces (phone and car)
- The refusal SHALL NOT affect the map, free driving, search, favourites, map downloads or diagnostics
- The explanation SHALL be actionable on the phone (re-request, or open system settings when the permission is permanently denied)
- On the car the explanation SHALL be a non-blocking message; the car UI SHALL NOT attempt to open a system settings screen (platform constraint)
- Wording SHALL be shared between the surfaces where the platform allows it

#### Scenario: Route request with approximate grant is refused with an explanation

- **WHEN** the precise grant is not held and the user requests a route
- **THEN** no route request SHALL be sent to the routing engine
- **THEN** the system SHALL show an explanation that precise location is required for navigation

#### Scenario: Phone offers the upgrade action

- **WHEN** the refusal is shown on the phone and the permission can still be requested
- **THEN** the explanation SHALL offer an action that re-requests the precise grant
- **AND** when the permission is permanently denied, the action SHALL open the app's system settings

#### Scenario: Car shows the message without launching settings

- **WHEN** the refusal is shown on the car surface
- **THEN** the message SHALL name the missing precision
- **AND** no attempt SHALL be made to start an activity outside the car app's allowed navigation

#### Scenario: Precise grant allows the route

- **WHEN** the precise grant is held and the user requests a route
- **THEN** the route request SHALL proceed as before

#### Scenario: Free driving is unaffected

- **WHEN** only the approximate grant is held
- **THEN** free driving and the map SHALL keep working
