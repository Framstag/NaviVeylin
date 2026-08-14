## MODIFIED Requirements

### Requirement: Android Auto session lifecycle is logged
The system SHALL record structured log entries for Android Auto session events and startup steps so startup failures — including native (C++) crashes invisible to the Java crash handler — can be traced after the fact.

#### Scenario: Session lifecycle events recorded
- **WHEN** the app binds, creates a session, receives `onCreateScreen`, switches screens, or destroys the session
- **THEN** each event is appended to the log file with a timestamp and relevant details (intent action/data, screen name, timing)

#### Scenario: Session creation request recorded
- **WHEN** the Android Auto host requests a session via `CarAppService.onCreateSession`
- **THEN** the request is appended to the log file with the session display type

#### Scenario: Warmup steps recorded
- **WHEN** the session runs background startup warmup (Hilt entry-point resolution, native map client build)
- **THEN** each step is appended to the log file with a start and completion marker, so a missing completion marker localizes a crash to that step

#### Scenario: App startup timing recorded
- **WHEN** the app process starts
- **THEN** the duration of `Application.onCreate` is appended to the log file

#### Scenario: Template errors recorded
- **WHEN** building a car screen template throws an exception
- **THEN** the exception and screen context are appended to the log file
