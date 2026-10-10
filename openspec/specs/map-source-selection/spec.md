# map-source-selection Specification

## Purpose
Make the origin of map data an explicit, user-controlled choice — the built-in karry.cz provider or a libosmscout mapgen repository at a URL the user supplies — and make the consequences of changing that choice (which installed data survives) predictable and visible before anything is deleted.

## Requirements

### Requirement: Map source is a registered, persisted choice

The app SHALL offer at least two map sources: the built-in karry.cz provider and a libosmscout mapgen repository identified by a user-supplied base URL. The active source SHALL persist across app restarts, and an unusable persisted selection SHALL fall back to the built-in provider at startup instead of failing to start.

#### Scenario: Default source on first start

- **WHEN** the app starts with no stored source selection
- **THEN** the built-in karry.cz provider is the active source

#### Scenario: Selection survives a restart

- **WHEN** the user selects the repository source with a base URL and restarts the app
- **THEN** the repository source with that base URL is still active
- **AND** the map manager screen shows it as the selected source

#### Scenario: Unusable stored selection falls back

- **WHEN** the stored source selection names a source the app no longer knows, or names the repository source with an empty base URL
- **THEN** the app starts with the built-in provider active
- **AND** the fallback is recorded in the diagnostics stream with the stored value

### Requirement: Base URL of the repository source is validated before use

The app SHALL provide a test action for the repository source that fetches the source's region index and reports whether it is a usable repository: reachability, a supported index schema version, and the number of regions and leaf entries found. A failed test SHALL report a user-actionable reason and SHALL leave the active source unchanged.

#### Scenario: Test succeeds

- **WHEN** the user enters a base URL that serves a supported region index and runs the test
- **THEN** the app reports success together with the number of regions and of leaf entries in the index
- **AND** the user can select that source

#### Scenario: Test fails on transport or status

- **WHEN** the test request fails to connect, or the server answers with a non-success status
- **THEN** the app reports the failure together with the URL that was requested and the status or error
- **AND** the active source is unchanged

#### Scenario: Test fails on an unsupported index

- **WHEN** the fetched document is not a region index, or its schema version is not supported
- **THEN** the app reports that the URL does not serve a supported region index
- **AND** the active source is unchanged

### Requirement: Installed map data records the source it came from

Every map directory the app installs SHALL contain a marker recording the source it was downloaded from (source identity, the base URL for a repository source, and the installed database version). A map directory that predates this change, and therefore carries no marker, SHALL be treated as coming from the built-in provider.

#### Scenario: Downloaded map carries its source

- **WHEN** a map download completes from any source
- **THEN** the map's directory contains a marker naming that source
- **AND** the marker is readable without contacting the server

#### Scenario: Pre-existing map is attributed to the built-in provider

- **WHEN** the app finds an installed map directory with no marker
- **THEN** the map is attributed to the built-in provider
- **AND** it is not deleted when the built-in provider is already the active source

### Requirement: Switching source is gated by a confirmation naming what will be deleted

Changing the active source SHALL remove the map directories and the basemap that belong to the other source, and SHALL do so only after the user confirms a dialog naming how many maps will be deleted and their total size. Cancelling SHALL change nothing, and a source with no installed data SHALL switch without a dialog.

#### Scenario: Confirmed switch deletes the other source's data

- **WHEN** the user selects a different source
- **WHEN** maps of the previously active source exist
- **THEN** the confirmation dialog names the number of maps and their total size, including the basemap when one is installed
- **WHEN** the user confirms
- **THEN** those map directories and that basemap are deleted

#### Scenario: Cancelled switch changes nothing

- **WHEN** the user cancels the confirmation dialog
- **THEN** the previously active source remains active
- **AND** no map directory and no basemap is deleted

#### Scenario: Switch with nothing to delete is not gated by a dialog

- **WHEN** the user selects a different source
- **WHEN** the other source has no installed maps and no basemap
- **THEN** the source changes without a confirmation dialog

#### Scenario: Partly failed deletion is reported and does not block the switch

- **WHEN** a map directory of the other source cannot be deleted
- **THEN** the app reports which directory could not be deleted
- **AND** the source change is still applied
- **AND** the remaining directories are left attributed to their own source

### Requirement: A source switch unregisters, reloads and records what it deletes

A deleted map directory SHALL be unregistered from the map manager rather than only removed from disk, and deleting the basemap SHALL fire the shared basemap-reload signal so any map view rendering in the process drops it. The switch SHALL be recorded in the diagnostics stream with the source identities, the number of directories deleted and the bytes reclaimed, and without coordinates.

#### Scenario: Deleted map is unregistered

- **WHEN** a source switch deletes an installed map
- **THEN** the map's directory is removed from the map manager's lookup set
- **AND** the installed list no longer contains it after the next refresh

#### Scenario: Deleted basemap stops rendering in the running process

- **WHEN** a source switch deletes the basemap
- **THEN** the basemap-reload signal is fired
- **AND** a map view that was rendering the deleted basemap re-renders without it, without an app restart

#### Scenario: Switch is diagnosable without coordinates

- **WHEN** a source switch completes
- **THEN** the diagnostics stream carries one line naming the two sources, the number of deleted directories and the bytes reclaimed
- **AND** that line carries no coordinates

### Requirement: An unencrypted repository source is marked as such

When the repository source's base URL uses the `http` scheme, the map manager SHALL mark that source as unencrypted, at the source together with the URL it applies to. The mark SHALL be informational only: it SHALL NOT gate selection, testing or downloading, and SHALL NOT ask the user to confirm anything.

#### Scenario: An http base URL is marked unencrypted

- **WHEN** the repository source's base URL scheme is `http`
- **THEN** the map manager shows a notice saying the source is unencrypted
- **AND** the notice names the URL it applies to
- **AND** testing, selecting and downloading that source remain available without further interaction

#### Scenario: An https base URL carries no notice

- **WHEN** the repository source's base URL scheme is `https`
- **THEN** no unencrypted notice is shown for it

#### Scenario: Marking does not replace the test outcome

- **WHEN** the user runs the source test on an `http` base URL
- **THEN** the test's own result is still reported
- **AND** the unencrypted notice is shown independently of the result

### Requirement: A repository base URL is normalised before it is used

The app SHALL remove whitespace from a repository base URL before using it to build a request URL, before validating it, and before persisting it as the source's identity, so a URL an input method padded is still usable and two spellings of one URL name one source.

#### Scenario: A padded URL behaves as the unpadded one

- **WHEN** the user enters a base URL that contains whitespace
- **THEN** the test and the requests use that URL without the whitespace
- **AND** the outcome is the same as for the same URL entered without whitespace

#### Scenario: Whitespace does not fork the stored source

- **WHEN** a base URL containing whitespace is selected as the source
- **THEN** the stored source identity equals that of the same URL without whitespace
- **AND** restarting the app shows that same source as active

### Requirement: The repository URL field is presented as URL input with its format shown

The field for the repository base URL SHALL show the format it expects (scheme, host, optional port) and SHALL be presented as URL input, so the platform's text input does not apply prose conventions — a space inserted after a period, or an auto-capitalised first letter.

#### Scenario: The expected format is shown with the field

- **WHEN** the map manager shows the repository URL field
- **THEN** an example of the accepted format is visible beside it

#### Scenario: URL text is not subject to prose conventions

- **WHEN** the repository URL field is focused and text is entered
- **THEN** the field declares URL input, so no space is inserted after a period and no letter is auto-capitalised
