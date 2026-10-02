# Spec Delta — auto-diagnostics

## MODIFIED Requirements

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

## ADDED Requirements

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
