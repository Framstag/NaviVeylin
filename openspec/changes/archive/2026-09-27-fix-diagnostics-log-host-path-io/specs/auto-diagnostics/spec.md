# Spec Delta

## ADDED Requirements

### Requirement: Diagnostic logging never blocks the caller

The system SHALL capture a diagnostic entry without performing filesystem access, blocking lock
acquisition or any other blocking work on the thread that logs it: the entry is handed to an
in-memory buffer and written by a separate logging worker. This applies to every logging caller,
including the Android Auto host-facing paths (template builds, surface callbacks, host navigation
calls, notification posts).

#### Scenario: A log call from the main thread performs no file access

- **WHEN** a diagnostic entry is logged from the app's main thread
- **THEN** no filesystem operation is performed on that thread to capture it

#### Scenario: The logging worker owns the file

- **WHEN** the logging worker flushes buffered entries
- **THEN** the file operations (size check, append, rotation) run on the worker's own thread, not on
  the thread that logged the entry

#### Scenario: A host callback that logs still answers promptly

- **WHEN** a car host callback (template build or surface callback) logs a diagnostic entry while it
  runs
- **THEN** the callback returns without waiting for the entry to reach the file, and the entry is
  preserved in the log

### Requirement: The in-memory diagnostic buffer is bounded

The system SHALL keep the memory used by pending diagnostic entries bounded, so a fast logging rate
cannot grow the app's heap without limit.

#### Scenario: Logging outpaces the worker

- **WHEN** entries are logged faster than the worker flushes them
- **THEN** memory stays within the configured bound and the oldest pending entries are dropped while
  the newest are kept

### Requirement: Buffered entries reach the file within a bounded delay

The system SHALL flush buffered diagnostic entries to the log file within a configured bound, so a
process that is killed rather than crashing loses at most the tail of that bound.

#### Scenario: Flush bound respected while the app runs

- **WHEN** a diagnostic entry is captured and the process keeps running
- **THEN** the entry is present in the log file no later than the configured flush bound after it was
  logged

### Requirement: Crash capture does not depend on the logging worker

The system SHALL record an uncaught exception's stack trace on disk before the process dies, without
depending on the asynchronous logging path — the crash trace is written on the thread that handles the
exception, and the previous handler is still invoked so the platform's own crash behaviour is
preserved.

#### Scenario: Uncaught exception on any thread

- **WHEN** an uncaught exception occurs in the app process
- **THEN** its stack trace is written to the log file before the process terminates

#### Scenario: Trace survives a process death

- **WHEN** the process dies immediately after an uncaught exception
- **THEN** the trace is readable on the next app launch

### Requirement: Reading diagnostics does not block the UI

The system SHALL read the diagnostic log for display or export off the thread that renders the UI, so
opening the diagnostics screen or sharing the log cannot block it.

#### Scenario: Diagnostics screen opens without a main-thread read

- **WHEN** the user opens the diagnostics view (phone or car) or exports the log
- **THEN** the log file is read on a background dispatcher and the UI stays responsive
