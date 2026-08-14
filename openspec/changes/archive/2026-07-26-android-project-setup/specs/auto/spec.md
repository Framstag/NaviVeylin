## ADDED Requirements

### Requirement: Placeholder Android Auto module
The system SHALL include an `:auto` Gradle module with a minimal `CarAppService` stub and Android Auto manifest declarations.

#### Scenario: Auto module compiles
- **WHEN** developer runs `./gradlew :auto:assembleDebug`
- **THEN** module compiles successfully with no functional code

### Requirement: Android Auto manifest declarations
The system SHALL declare Android Auto support in the `:auto` module manifest, including the `<action android:name="android.car.app.CarAppService"/>` intent filter.

#### Scenario: Manifest declares Auto capability
- **WHEN** Android Auto host queries the app
- **THEN** the manifest correctly advertises CarAppService support

### Requirement: No functional Auto code
The system SHALL NOT include any functional Android Auto screens, templates, or navigation logic in this change.

#### Scenario: Empty service stub
- **WHEN** `CarAppService` is inspected
- **THEN** it contains only the required override stubs with no implementation
