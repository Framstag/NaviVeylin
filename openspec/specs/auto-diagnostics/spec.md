# Android Auto Diagnostics (auto-diagnostics) Specification

## Purpose

Captures crash and Android Auto session diagnostics on-device so failures on real head units can be analyzed without adb access, and makes those logs viewable and exportable from the car screen and the phone app.

## Requirements

### Requirement: Fatal crashes are captured to a log file
The system SHALL write uncaught exception stack traces to a persistent log file in the app's internal storage so crashes on head units are not lost.

#### Scenario: Uncaught exception on any thread
- **WHEN** an uncaught exception occurs in the app process
- **THEN** the stack trace is appended to a crash log file with a timestamp

#### Scenario: Log file survives process death
- **WHEN** the process dies after an uncaught exception
- **THEN** the crash log file remains readable on the next app launch

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

### Requirement: Diagnostics viewable on the car screen
The system SHALL expose a diagnostics screen inside the Android Auto UI that shows captured log entries.

#### Scenario: Diagnostics screen in Android Auto
- **WHEN** the user selects the diagnostics entry on the car screen
- **THEN** the car screen shows recent log entries (crash traces and session events), newest first

### Requirement: Diagnostics viewable and exportable on the phone
The system SHALL expose captured logs in the phone app and allow exporting them for off-device analysis.

#### Scenario: Log viewer in the phone app
- **WHEN** the user opens the diagnostics view in the phone app
- **THEN** the captured crash and session log entries are displayed

#### Scenario: Log export via share sheet
- **WHEN** the user requests to share the diagnostics log
- **THEN** the log file is shared through the Android share sheet (email, file transfer, etc.)

### Requirement: Log storage is bounded
The system SHALL keep diagnostic log storage bounded so long-running use does not exhaust device storage.

#### Scenario: Log size cap enforced
- **WHEN** the log file exceeds the configured size cap
- **THEN** the oldest entries are discarded and writing continues

#### Scenario: Crash log survives without unbounded growth
- **WHEN** the app runs for an extended period with many logged events
- **THEN** total diagnostic log storage stays within the configured cap
