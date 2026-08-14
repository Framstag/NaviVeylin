## MODIFIED Requirements

### Requirement: Placeholder Android Auto module
The system SHALL include an `:auto` Gradle module with a `CarAppService` that provides turn-by-turn navigation display on Android Auto.

#### Scenario: Auto module compiles
- **WHEN** developer runs `./gradlew :auto:assembleDebug`
- **THEN** module compiles successfully

#### Scenario: CarAppService returns real Session
- **WHEN** Android Auto host binds to the service
- **THEN** `onCreateSession()` returns a non-null `Session` instance

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: No functional Auto code
**Reason**: Replaced by functional NavigationTemplate implementation
**Migration**: The `CarAppService` stub is replaced with a real `Session` that returns `NavigationTemplate` during active navigation
