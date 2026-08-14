# Deep Links (auto-deep-links)

## Purpose

Hand a destination from the phone to the car: a deep link (`geo:`, Google Maps URL, or `text/plain` share) opened on the phone launches the NaviVeylin templated app on Android Auto and starts navigation to the parsed destination.

## ADDED Requirements

### Requirement: Deep-link entry point declared
The system SHALL declare a phone-side deep-link entry point that Android Auto recognizes as a navigation target.

#### Scenario: Car host discovers deep-link target
- **WHEN** the app is installed and Android Auto scans for navigation deep-link targets
- **THEN** the manifest declares an exported `DeepLinkActivity` with an intent filter containing action `android.intent.action.VIEW` and category `androidx.car.app.category.NAVIGATION`

#### Scenario: geo URIs accepted
- **WHEN** the user opens a `geo:` URI
- **THEN** the intent filter matches the deep-link activity

#### Scenario: Google Maps URLs accepted
- **WHEN** the user opens an `https://maps.google.com` URL (also `www.google.com/maps`)
- **THEN** the intent filter matches the deep-link activity

#### Scenario: Shared text accepted
- **WHEN** the user shares a `text/plain` payload to the app from another app
- **THEN** the share intent matches the deep-link activity

### Requirement: Deep link parsed into a destination
The system SHALL parse a deep-link intent into either destination coordinates or a location query.

#### Scenario: geo URI with coordinates
- **WHEN** the intent URI is `geo:48.8566,2.3522`
- **THEN** the parser yields latitude 48.8566, longitude 2.3522, no query

#### Scenario: geo URI with query
- **WHEN** the intent URI is `geo:0,0?q=48.8566,2.3522(Eiffel%20Tower)`
- **THEN** the parser yields the coordinate pair and the query label

#### Scenario: Google Maps URL with q parameter
- **WHEN** the intent URI is `https://maps.google.com/?q=48.8566,2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: Google Maps URL with address query
- **WHEN** the intent URI is `https://maps.google.com/?q=Eiffel+Tower`
- **THEN** the parser yields no coordinates and the query text

#### Scenario: Shared text with coordinates
- **WHEN** the share intent `EXTRA_TEXT` is `48.8566, 2.3522`
- **THEN** the parser yields the coordinate pair

#### Scenario: Unparseable input
- **WHEN** the intent URI or text cannot be parsed
- **THEN** the parser yields no destination and the session surfaces an error

### Requirement: Deep link starts navigation
The system SHALL start navigation to the parsed destination from the car session.

#### Scenario: Coordinate destination
- **WHEN** the deep link yields coordinates
- **THEN** the session calls `NavigationViewModel.navigateTo(lat, lon)`

#### Scenario: Address query destination
- **WHEN** the deep link yields a query without coordinates
- **THEN** the session geocodes the query via `AutoSearchProvider.searchLocations()` and navigates to the first result

#### Scenario: Address query with no matches
- **WHEN** geocoding returns no results
- **THEN** the session surfaces an error message on the car screen

#### Scenario: Deep link while session active
- **WHEN** the car session is already running and receives a new deep link
- **THEN** `Session.onNewIntent()` handles it and starts navigation to the new destination

### Requirement: Deep-link activity forwards to phone app
The system SHALL forward the deep-link intent from `DeepLinkActivity` to `MainActivity` so the phone surface reflects the navigation.

#### Scenario: Phone app opened by deep link
- **WHEN** `DeepLinkActivity` receives a deep link
- **THEN** it starts `MainActivity` with the original intent data and finishes itself
