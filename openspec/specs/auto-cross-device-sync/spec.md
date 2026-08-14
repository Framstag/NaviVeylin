# auto-cross-device-sync Specification

## Purpose
Keep navigation state consistent between phone and car regardless of which surface started or stopped it: connecting mid-navigation shows the current route immediately, phone-initiated start/stop is reflected on the car, and navigation can start from the car without the phone UI being open.
## Requirements
### Requirement: Car-only navigation start
The system SHALL start navigation from the car even when the phone UI has not been opened in this process.

#### Scenario: Deep link with phone UI closed
- **WHEN** a deep link or car action triggers `navigateTo()` and the phone `RoutePanelViewModel` is not available
- **THEN** the system calculates the route directly via the JNI client and starts navigation

#### Scenario: GPS position available
- **WHEN** a car-only navigation start occurs
- **THEN** the start position is taken from the current navigation position or `LocationService`, and GPS updates are started on `NavigationViewModel` init

#### Scenario: No GPS fix
- **WHEN** car-only navigation start occurs without any GPS position
- **THEN** the system surfaces a "GPS signal required" error on the car screen instead of failing silently

### Requirement: Connect mid-navigation shows active route
The system SHALL show the `NavigationTemplate` immediately when the car connects while navigation is already active.

#### Scenario: Car connects during active navigation
- **WHEN** the car session is created while `NavigationState.isNavigating == true`
- **THEN** `onCreateScreen()` returns `NavigationScreen` directly without flashing the root screen

### Requirement: Phone stop navigation reflected on car
The system SHALL return the car screen to the root screen when navigation stops on the phone.

#### Scenario: Navigation stopped from phone
- **WHEN** the user stops navigation on the phone while the car is connected
- **THEN** the car session observes `isNavigating == false` and pops back to the root screen

### Requirement: Car stop navigation reflected on phone
The system SHALL stop navigation on the phone when stopped from the car (existing stop action contract).

#### Scenario: Navigation stopped from car
- **WHEN** the user taps Stop on the car `NavigationTemplate`
- **THEN** `NavigationViewModel.stopNavigation()` runs and state resets on both surfaces

### Requirement: Session lifecycle cleanup
The system SHALL release all session resources when the car disconnects.

#### Scenario: No leaks on disconnect
- **WHEN** the car session is destroyed
- **THEN** the session cancels its coroutine scope and stops observing navigation state

