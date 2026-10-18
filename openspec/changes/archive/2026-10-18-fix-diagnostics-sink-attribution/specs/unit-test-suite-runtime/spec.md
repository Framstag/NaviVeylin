# unit-test-suite-runtime — deltas

## ADDED Requirements

### Requirement: A diagnostics assertion is attributed to the lines its own case wrote

A case that asserts on the diagnostics log SHALL decide its result from the lines its own subject wrote, and
SHALL NOT decide it from the whole contents of the configured file: the target `DiagnosticsLog.initForTest`
installs is process-wide, so work another class left running logs into it while a case holds it, and a count,
a `single()` or a whole-file substring measures those lines too. A case identifies its own lines by the tag or
marker its subject's message carries.

#### Scenario: A foreign line does not decide the fallback case

- **WHEN** a line another class logged is in the file while the unusable-stored-source case asserts the
  fallback line's text and the absence of a coordinate
- **THEN** the case passes on its own line; an own line that carried a coordinate, that was duplicated or
  missing, or that named a value other than the stored one would fail it
- **Case**: `SettingsStorageSourceTest.unknownStoredSourceFallsBackToBuiltIn`

#### Scenario: A foreign line is not counted as a post

- **WHEN** a line another class logged is in the file while the host-send case asserts that one post was
  recorded
- **THEN** exactly the one `HOST NOTIF post` entry is counted
- **Case**: `NotificationHostSendLoggingTest.aPostIsRecordedWithItsContentAndTimestamp`

#### Scenario: A sequence of posts is counted as posts

- **WHEN** a line another class logged is in the file while the case asserts that two posts produced two
  entries
- **THEN** exactly the two `HOST NOTIF post` entries are counted
- **Case**: `NotificationHostSendLoggingTest.everyPostAddsExactlyOneEntry`

#### Scenario: A foreign line is not a fallback line

- **WHEN** a line another class logged is in the file while the repository-without-a-URL case asserts its own
  fallback line
- **THEN** exactly the one `repository source without a base URL` entry is asserted on
- **Case**: `SettingsStorageSourceTest.repositorySourceWithoutAUrlFallsBackToBuiltIn`

#### Scenario: A foreign line is not a fault record

- **WHEN** a line another class logged is in the file while the confined-fault case reads the record it
  expects
- **THEN** the single `ENGINE_FAULT_TAG` record is the one asserted on
- **Case**: `EngineFaultHandlerTest.aConfinedFaultIsRecordedWithOriginAndStackTraceAndPublishedOnce`

#### Scenario: A foreign line is not a second fault record

- **WHEN** a line another class logged is in the file while the case asserts that a throwing publish still
  left exactly one record
- **THEN** exactly the one `ENGINE_FAULT_TAG` record is counted
- **Case**: `EngineFaultHandlerTest.aThrowingPublishCannotEscapeTheHandlerAndTheRecordStillLands`

#### Scenario: A foreign line is not a fault record for the file assertion

- **WHEN** a line another class logged is in the file while the logcat-and-file case asserts the record
  reached the file
- **THEN** exactly the one `ENGINE_FAULT_TAG` entry is counted in the file
- **Case**: `EngineFaultHandlerTest.theHandlerMirrorsToLogcatAndTheRecordReachesTheFile`

#### Scenario: A foreign line is not a surface entry

- **WHEN** a line another class logged is in the file while the surface-lifetime case asserts one adopt and
  one release
- **THEN** exactly the two `HOST surface` entries are counted, in that order
- **Case**: `SessionCarSurfaceHostTest.surfaceLifetimeIsRecordedForDiagnosis`

#### Scenario: A foreign line is not the calculation record

- **WHEN** a line another class logged is in the file while the route-calculation case asserts its own entry
  reached the file
- **THEN** exactly the one `ROUTE calc done:` entry is counted in the file
- **Case**: `NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`
