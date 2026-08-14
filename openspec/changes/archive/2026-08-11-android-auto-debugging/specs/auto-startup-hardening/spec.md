## Purpose

Ensures the Android Auto session starts reliably on real head units: no process death during startup, heavy initialization kept off the screen-critical path, and a safe fallback screen when setup fails.

## ADDED Requirements

### Requirement: Session startup never crashes the process
The system SHALL ensure that starting the Android Auto session — from `CarAppService` binding through first template render — cannot kill the app process, even when initialization fails.

#### Scenario: Host launches the app on a head unit
- **WHEN** the Android Auto host binds to `NaviVeylinCarAppService` and requests a session
- **THEN** a session is created and a first screen is presented without the process dying

#### Scenario: Exception during session creation
- **WHEN** an exception is thrown while creating the session or its first screen
- **THEN** the process survives and a fallback error screen is presented instead

#### Scenario: Exception during first template render
- **WHEN** the first screen's template throws while being built
- **THEN** the process survives and an error state is shown rather than a crash

### Requirement: Heavy initialization does not block the first screen
The system SHALL keep slow one-time work (native client construction, stylesheet setup, map database access, location service start) off the main thread during session startup, so the host receives the first template without timing out.

#### Scenario: Cold start from the car without the phone UI open
- **WHEN** the app process is started by the Android Auto host and no map client exists yet
- **THEN** the first screen is returned within the host's response window, with heavyweight initialization continuing in the background

#### Scenario: Slow or failing background initialization
- **WHEN** background initialization is still in progress or fails while the first screen is already shown
- **THEN** the screen remains responsive and shows a loading or error state rather than blocking or crashing

### Requirement: Startup failure surfaces a readable error
The system SHALL present a human-readable error screen inside Android Auto when startup cannot complete, instead of failing silently or crashing.

#### Scenario: Native client cannot initialize
- **WHEN** the map client fails to initialize (missing map data, native error)
- **THEN** the car screen shows an error message explaining what went wrong and that the process remains alive

#### Scenario: Recovery after failure
- **WHEN** a startup failure has been shown and the user relaunches the session
- **THEN** the session retries initialization instead of permanently failing
