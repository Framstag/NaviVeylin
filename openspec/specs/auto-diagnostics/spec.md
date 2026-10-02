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
The system SHALL keep diagnostic log storage bounded in **both size and age**, so long-running use neither exhausts device storage nor accumulates personal data indefinitely.

- Total diagnostic storage (active file + rotated file) SHALL stay within the configured byte cap
- Entries older than a retention window of **7 days** SHALL be removed; the window is a named constant, never a user-facing setting
- The retention pass SHALL run on the logging worker, never on the calling thread — the logging contract ("the caller never touches the filesystem") is unchanged
- The retention pass SHALL cover the active file and the rotated file
- A line whose timestamp cannot be parsed SHALL be removed by the pass as well (it cannot be aged, so it must not defeat the bound) and the removal SHALL be recorded once in the diagnostics
- Removing entries SHALL NOT require a user action; it happens on the first flush of a process and on day rollover while logging continues

#### Scenario: Log size cap enforced
- **WHEN** the log file exceeds the configured size cap
- **THEN** the oldest entries are discarded and writing continues

#### Scenario: Crash log survives without unbounded growth
- **WHEN** the app runs for an extended period with many logged events
- **THEN** total diagnostic log storage stays within the configured cap

#### Scenario: Entries older than the retention window are removed
- **WHEN** the app starts and the log file contains entries older than the 7-day window
- **THEN** those entries are gone from the file once the worker's first flush has completed
- **AND** no entry older than the window remains, in the active file or the rotated one

#### Scenario: A young log is left intact
- **WHEN** every entry in the log is younger than the retention window
- **THEN** no entry is removed and the file content is unchanged apart from the new appends

#### Scenario: Pruning never blocks the caller
- **WHEN** a log or diagnostics call is made while a retention pass is pending
- **THEN** the calling thread performs no filesystem work (bounded in-memory enqueue only)

#### Scenario: An un-ageable line does not survive the bound
- **WHEN** the retention pass encounters a line whose timestamp cannot be parsed
- **THEN** that line is removed
- **AND** the removal is reported once in the diagnostics

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

### Requirement: Diagnostics carry no coordinates
The system SHALL NOT record device-position coordinates — neither in the file-backed diagnostics nor in the logcat mirror.

- No diagnostic or log line may contain a latitude/longitude value or a coordinate-formatted string
- A position-dependent entry SHALL carry precision-free identity instead: map database or map file name, object label and/or object id, magnification, screen pixel, accuracy or bearing
- User-facing coordinate display (labels, sheets, favourites) is explicitly unaffected — this requirement is about the diagnostic stream only
- A build-time gate SHALL fail the build when a log or diagnostics call interpolates a coordinate value
- A log or diagnostics call SHALL NOT interpolate a whole value that may carry a position: a request/location/fix object, an `Intent`'s URI data, or input text the app merely received from another app (a share subject or query, which may itself be a coordinate pair or contain one)
- Where a position-dependent entry names an object, the identity SHALL be the map object's label or id — never input text the app received; for input-driven entries the identity is the input's origin and shape (URI scheme, action, whether a subject/query was present, request kind)
- The build-time gate SHALL flag, besides a coordinate identifier and a coordinate-shaped format, an interpolation of a position-carrying object or of an `Intent`'s data

#### Scenario: Position-dependent entry carries identity, not position
- **WHEN** the app logs a position-dependent event (a fix, a long press, a pan, a reroute, a viewport preparation, a shared location, a favourite or search selection)
- **THEN** the line contains no coordinate value
- **AND** it carries at least one precision-free identity field (magnification, screen pixel, object label/id, database or map file name, accuracy, bearing)

#### Scenario: Long-press mapping evidence stays usable
- **WHEN** the user long-presses the map
- **THEN** the file-backed entry identifies the point by screen pixel, magnification and the resolved object/database identity, so the resolved object can be compared without the coordinate

#### Scenario: Car pan diagnostics stay usable
- **WHEN** the car pan handler logs a scroll
- **THEN** the line carries the scroll deltas, the magnification and the resulting viewport state without logging the start or end coordinates

#### Scenario: Shared location and deep link log identity, not the URI
- **WHEN** the app receives a `geo:` deep link or a location shared from another app
- **THEN** every emitted line names the action, the URI scheme, the request shape and the magnification
- **AND** no line contains a coordinate value or a coordinate-formatted string

#### Scenario: Received share text is not logged verbatim
- **WHEN** a share carries a subject or query that is itself a coordinate pair
- **THEN** the line carries the input's origin and shape instead of the received text

#### Scenario: A new coordinate in a log line fails the build
- **WHEN** a source file adds a log or diagnostics call that interpolates a latitude/longitude value
- **THEN** the build fails naming that file and line

#### Scenario: An interpolated position-carrying object fails the build
- **WHEN** a source file adds a log or diagnostics call that interpolates a request/location/fix object or an `Intent`'s data
- **THEN** the build fails naming that file and line

#### Scenario: User-facing coordinates are unaffected
- **WHEN** a coordinate is shown to the user (favourite row, long-press label, details sheet, car details row)
- **THEN** the coordinate string is still displayed as before

### Requirement: Export and viewers disclose the log contents
The system SHALL state what the diagnostics file may contain and how long it is kept, wherever it is exported or displayed.

- The exported/shared text SHALL begin with the disclosure
- Both viewers (phone diagnostics dialog, car diagnostics screen) SHALL show the same statement
- The disclosure SHALL be a translatable resource, not a hardcoded literal, and SHALL be accurate to the implemented behaviour (no coordinates, entries kept at most 7 days)

#### Scenario: Shared log carries the disclosure
- **WHEN** the user shares the diagnostics log
- **THEN** the shared text begins with the disclosure
- **AND** the disclosure names both what the file may contain and the retention window

#### Scenario: Phone viewer shows the disclosure
- **WHEN** the user opens the diagnostics view in the phone app
- **THEN** the disclosure is visible next to the entries

#### Scenario: Car viewer shows the disclosure
- **WHEN** the user opens the diagnostics screen in the car UI
- **THEN** the same disclosure text is visible

#### Scenario: Disclosure is localized
- **WHEN** the device locale is German
- **THEN** the disclosure is shown in German

### Requirement: Coordinate-carrying entries do not survive the retention pass

The system SHALL remove an existing diagnostic entry that carries a device position during the
retention pass, **regardless of the entry's age**: the retention window SHALL NOT be the only reason a
line is dropped, so a position written before this rule existed does not stay in the file for the rest
of the window.

- An entry carries a device position when it names a coordinate — a latitude/longitude token,
  delimited as a field — together with a number at coordinate precision
- The rule SHALL also recognise a position written without such a name: two coordinate-precision
  numbers written as one comma-separated pair
- An entry that carries precision-free identity instead SHALL be kept: magnification, screen pixel,
  map database or map file name, accuracy, bearing, object label and/or object id
- The removal SHALL cover the active file and the rotated file
- The removal SHALL run inside the retention pass on the logging worker; a log or diagnostics call
  SHALL NOT perform it, or any other filesystem work, on the calling thread
- The removal SHALL be reported once per pass, and the report SHALL NOT name the removed position
- The rule SHALL NOT alter what is logged at runtime (new entries remain the build gate's concern),
  and SHALL NOT affect user-facing coordinate display (labels, sheets, favourites)

#### Scenario: A pre-change coordinate entry is removed on the first pass

- **WHEN** the log file contains a position-carrying entry written by an earlier build, and that entry is younger than the retention window
- **THEN** the entry is gone from the file after the logging worker's first retention pass
- **AND** it is gone from the rotated file as well

#### Scenario: A position without a coordinate field name is removed too

- **WHEN** the log contains an entry whose position is an unnamed comma-separated pair of numbers at coordinate precision (the pre-redaction car render shape)
- **THEN** the entry is removed by the retention pass as well

#### Scenario: Identity entries survive the purge

- **WHEN** the log contains entries that name a magnification, a screen pixel, a map database or map file name, an accuracy, a bearing, or an object label/id
- **THEN** every one of those entries is still present after the retention pass

#### Scenario: The removal is reported without naming the position

- **WHEN** a position-carrying entry is removed
- **THEN** the diagnostics record the removal once for that pass
- **AND** neither the report nor any other new entry contains the removed position

#### Scenario: Purging never blocks the caller

- **WHEN** a log or diagnostics call is made while a coordinate purge is pending
- **THEN** the calling thread performs no filesystem work (bounded in-memory enqueue only)

#### Scenario: Age pruning is unchanged

- **WHEN** the log contains entries older than the retention window and entries that carry no position
- **THEN** the old entries are removed exactly as before, and no coordinate-free entry is removed by the coordinate rule
