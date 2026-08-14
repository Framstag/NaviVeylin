## ADDED Requirements

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

### Requirement: Screen stack includes map screen
The system SHALL manage the map screen in the Android Auto screen stack alongside existing search, favorites, and navigation screens.

#### Scenario: Back from map to root
- **WHEN** the user presses back on the map screen
- **THEN** the car screen returns to the root screen

#### Scenario: Navigation replaces map
- **WHEN** navigation starts from the map screen
- **THEN** the map screen is replaced by the navigation screen (not stacked on top)
