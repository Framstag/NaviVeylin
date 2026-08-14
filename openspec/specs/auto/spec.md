# Android Auto (auto)

## Purpose

Android Auto support module providing turn-by-turn navigation display on car screens via `NavigationTemplate`.

## Requirements

### Requirement: Placeholder Android Auto module
The system SHALL include an `:auto` Gradle module with a `CarAppService` that provides turn-by-turn navigation display on Android Auto.

#### Scenario: Auto module compiles
- **WHEN** developer runs `./gradlew :auto:assembleDebug`
- **THEN** module compiles successfully

#### Scenario: CarAppService returns real Session
- **WHEN** Android Auto host binds to the service
- **THEN** `onCreateSession()` returns a non-null `Session` instance

### Requirement: Android Auto manifest declarations
The system SHALL declare NaviVeylin as a native Android Auto navigation app using the current Car App Library 1.7.0 conventions.

#### Scenario: Car App Service is discoverable
- **WHEN** Android Auto or Android Automotive OS scans for car apps
- **THEN** it finds `com.naviveylin.NaviVeylinCarAppService` with action `androidx.car.app.CarAppService` and category `androidx.car.app.category.NAVIGATION`

#### Scenario: Automotive metadata is declared
- **WHEN** the host validates the app manifest
- **THEN** the `com.google.android.gms.car.application` metadata references `automotive_app_desc.xml` containing `<uses name="template" />`

#### Scenario: Host is queryable
- **WHEN** the app is installed
- **THEN** the manifest contains a `<queries>` element exposing `androidx.car.app.CarAppService`, `com.google.android.projection.gearhead`, and `com.google.android.apps.automotive.templates.host`

### Requirement: NavigationTemplate displays turn-by-turn guidance
The system SHALL display a `NavigationTemplate` on the Android Auto screen when the user is actively navigating on the phone.

#### Scenario: NavigationTemplate shown during active navigation
- **WHEN** user is navigating on the phone and Android Auto is connected
- **THEN** the car screen shows a `NavigationTemplate` with next turn instruction and distance

#### Scenario: NavigationTemplate hidden when not navigating
- **WHEN** user stops navigation on the phone
- **THEN** the car screen exits the `NavigationTemplate`

### Requirement: Next turn instruction updates in real-time
The system SHALL update the next turn instruction and distance on the `NavigationTemplate` as the navigation progresses.

#### Scenario: Next turn updates on approach
- **WHEN** the vehicle approaches the next turn
- **THEN** the turn instruction and distance update in real-time on the car screen

### Requirement: ETA and remaining distance displayed
The system SHALL display estimated time of arrival, remaining distance, and remaining time on the `NavigationTemplate`.

#### Scenario: ETA and distance shown
- **WHEN** navigation is active
- **THEN** the car screen shows ETA, remaining distance, and remaining time

### Requirement: Current speed displayed
The system SHALL display the current vehicle speed on the `NavigationTemplate`.

#### Scenario: Speed shown during navigation
- **WHEN** navigation is active and GPS speed data is available
- **THEN** the car screen shows current speed

### Requirement: Lane guidance displayed
The system SHALL display lane guidance on the `NavigationTemplate` when lane data is available from the navigation engine.

#### Scenario: Lane guidance shown
- **WHEN** lane guidance data is available from the navigation engine
- **THEN** the car screen shows lane guidance on the `NavigationTemplate`

### Requirement: Trip progress bar
The system SHALL display a trip progress bar on the `NavigationTemplate` showing the fraction of the route completed.

#### Scenario: Progress bar updates
- **WHEN** navigation is active
- **THEN** the progress bar updates as the vehicle progresses along the route

### Requirement: Rerouting indicator
The system SHALL display a rerouting indicator on the `NavigationTemplate` when the navigation engine detects the vehicle is off-route and recalculating.

#### Scenario: Rerouting shown
- **WHEN** the navigation engine triggers a reroute
- **THEN** the car screen shows a rerouting indicator

### Requirement: Stop navigation action
The system SHALL provide a stop navigation action on the `NavigationTemplate` that ends the active navigation.

#### Scenario: Stop navigation from car
- **WHEN** user taps the stop navigation action on the car screen
- **THEN** navigation stops on both the car screen and the phone

### Requirement: Lifecycle-aware state observation
The system SHALL observe navigation state only while the Auto screen is visible, and clean up observers when the screen is destroyed.

#### Scenario: No leaks on disconnect
- **WHEN** Android Auto disconnects mid-navigation
- **THEN** all state observers are cleaned up and no memory leak occurs

### Requirement: Map browsing screen on Android Auto
The system SHALL provide a map browsing screen on Android Auto that renders libosmscout map tiles, accessible from the root screen when not navigating.

#### Scenario: Map screen accessible from root
- **WHEN** the user is on the root screen and not navigating
- **THEN** a "Map" option is available alongside Search and Favorites

#### Scenario: Map screen shows rendered map
- **WHEN** the user selects "Map" from the root screen
- **THEN** a `MapWithContentTemplate` with a rendered libosmscout map is displayed

#### Scenario: Map screen transitions to navigation
- **WHEN** the user selects a destination on the map and starts navigation
- **THEN** the car screen transitions from the map screen to the `NavigationTemplate`

### Requirement: Screen stack includes map screen
The system SHALL manage the map screen in the Android Auto screen stack alongside existing search, favorites, and navigation screens.

#### Scenario: Back from map to root
- **WHEN** the user presses back on the map screen
- **THEN** the car screen returns to the root screen

#### Scenario: Navigation replaces map
- **WHEN** navigation starts from the map screen
- **THEN** the map screen is replaced by the navigation screen (not stacked on top)
