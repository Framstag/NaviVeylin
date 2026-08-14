## ADDED Requirements

### Requirement: App scaffold with Gradle build
The system SHALL provide a Gradle-based Android project using Kotlin DSL with AGP, targeting API 34+ with minSdkVersion 26.

#### Scenario: Gradle build succeeds
- **WHEN** developer runs `./gradlew assembleDebug`
- **THEN** build completes successfully and produces a debug APK

### Requirement: Jetpack Compose UI
The system SHALL use Jetpack Compose for all UI rendering, with Material 3 design system.

#### Scenario: Compose renders main screen
- **WHEN** app launches
- **THEN** Compose renders the main navigation screen without XML layout files

### Requirement: Jetpack Navigation
The system SHALL use Jetpack Navigation Compose for screen-to-screen navigation.

#### Scenario: Navigate between screens
- **WHEN** user triggers a navigation action
- **THEN** Navigation Compose transitions to the target screen

### Requirement: ViewModel + State management
The system SHALL use Jetpack ViewModel with Kotlin StateFlow for UI state management.

#### Scenario: ViewModel survives config change
- **WHEN** device rotates
- **THEN** ViewModel retains its state and UI recomposes from existing state

### Requirement: Hilt dependency injection
The system SHALL use Hilt for dependency injection across the app.

#### Scenario: Hilt injects dependencies
- **WHEN** app starts
- **THEN** Hilt injects all declared dependencies (repositories, viewmodels, native bridge)

### Requirement: Room local persistence
The system SHALL use Room for local storage of map metadata, favorites, and search history.

#### Scenario: Room database created
- **WHEN** app first launches
- **THEN** Room creates the database schema with all defined entities

### Requirement: Adaptive layouts for phone, foldable, tablet
The system SHALL support phone, foldable, and tablet form factors using Compose adaptive APIs and WindowSizeClass.

#### Scenario: Tablet layout on large screen
- **WHEN** app runs on a device with screen width >= 840dp
- **THEN** UI renders in tablet-optimized layout (e.g., list-detail pane)

#### Scenario: Foldable layout on unfolded screen
- **WHEN** foldable device is unfolded to medium width (600dp+)
- **THEN** UI adapts to use available space with appropriate layout
