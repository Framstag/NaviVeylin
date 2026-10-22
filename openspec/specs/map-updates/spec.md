# map-updates Specification

## Purpose

Keeps an installed map database or basemap from a libosmscout mapgen repository in step with the
repository it came from: the app notices that a newer version is published, shows the user what can be
updated, and replaces an installation only when the replacement is complete and verified.

## Requirements

### Requirement: The app checks for newer repository map data once a day

The app SHALL compare its installed repository map data with the repository at most once in any 24 hours,
on the app-start path and without the user opening a screen. A check that did not complete SHALL NOT count
as the day's check, so the next app start retries it. A check SHALL NOT be started while another check is
running.

#### Scenario: First app start of the day runs the check

- **WHEN** the app starts and the last completed check is more than 24 hours old or none has completed
- **THEN** the app compares its installed repository map data with the repository in the background

#### Scenario: A second start in the same day runs no check

- **WHEN** the app starts within 24 hours of a completed check
- **THEN** no comparison request is issued by the app start
- **AND** the state the completed check produced is shown unchanged

#### Scenario: An unfinished check is retried at the next start

- **WHEN** a check could not complete (the repository was unreachable, or the app ended while it ran)
- **THEN** the check is not recorded as the day's check
- **AND** the next app start runs the check again

#### Scenario: A check never runs twice at once

- **WHEN** a check is running and the app start path reaches the check again
- **THEN** no second check is started
- **AND** the running check finishes and publishes its result

### Requirement: The user can run the check on demand

The map manager screen SHALL offer a control that runs the same comparison the daily check runs. The
control SHALL report when the last completed check ran and what it found, and that report SHALL stay
visible until the next check. A completed manual check SHALL count as the day's check.

#### Scenario: The control reports what the check found

- **WHEN** the user runs the check and it completes
- **THEN** the control reports when the check completed and how many updates it found or that none were found
- **AND** the update list reflects the same result

#### Scenario: The report stays until the next check

- **WHEN** a check has reported its outcome and the user interacts with the screen without checking again
- **THEN** the report is still shown

#### Scenario: A manual check defers the next automatic one

- **WHEN** the user runs a manual check and the app starts within 24 hours of it
- **THEN** the app start issues no comparison request

#### Scenario: A manual check that cannot complete is reported as such

- **WHEN** the repository is unreachable during a manual check
- **THEN** the control reports that the check did not complete and why
- **AND** the previously shown update state is not replaced by an update

### Requirement: The check covers the active repository source only

The daily and the manual check SHALL cover map data installed from a repository source. Map data
installed from the built-in provider SHALL report no update state and SHALL NOT be compared, because the
app records no version that would be comparable for it. A check SHALL compare only data installed from the
source it probes.

#### Scenario: A provider-installed map reports no update state

- **WHEN** a map was installed from the built-in provider
- **THEN** the check reports no update state for it
- **AND** no request is issued whose only purpose is comparing it with a server

#### Scenario: Installed data of another repository is not compared

- **WHEN** map data installed from one repository base URL is installed beside data of another
- **THEN** a check of the active source compares only the data that source installed

### Requirement: An installed repository database is compared with its own version slot

For each installed repository database the app SHALL request the metadata of that database's own version
slot, the version this client reads and no other. A slot whose creation time is newer than the creation
time of the metadata the app stored with the installation SHALL be reported as an update. A slot that is
not published SHALL leave the installation in place and be reported as no longer offered. A comparison
that cannot be made SHALL be reported as unknown, never as an update.

#### Scenario: A newer database is published for the client's version

- **WHEN** the repository publishes a metadata creation time newer than the installed one for the client's version slot
- **THEN** the database is reported as having an update available
- **AND** the reported download size is that of the published version's files

#### Scenario: The published database is not newer

- **WHEN** the published metadata's creation time is not newer than the installed one
- **THEN** the database is reported as up to date

#### Scenario: The client's version slot is no longer published

- **WHEN** the repository answers that the client's version slot does not exist
- **THEN** the installed database is kept and stays usable
- **AND** it is reported as no longer offered, not as an error and not as an update

#### Scenario: The installed metadata cannot be read

- **WHEN** the metadata stored with an installation is missing or unreadable
- **THEN** that installation is reported as unknown
- **AND** it is not reported as having an update available

#### Scenario: A newer version of the database format is published

- **WHEN** the repository publishes the database under a newer version slot than the one this client reads
- **THEN** no update is reported for that newer slot

### Requirement: Each compared item carries one update state

Every compared installation SHALL be in exactly one of these states: up to date, update available, no
longer offered, or unknown. A comparison that fails for one installation SHALL leave the other
installations' states as their own comparisons determined.

#### Scenario: One unreachable item does not hide the others

- **WHEN** the comparison fails for one installed database and succeeds for another
- **THEN** the failure is reported for that database as unknown
- **AND** the other database reports the state its comparison determined

#### Scenario: Only update-available items are actionable

- **WHEN** an installation is up to date, no longer offered, or unknown
- **THEN** no update action is offered for it

### Requirement: The updates section belongs to the repository source

The map manager screen SHALL show an updates section when the active source is a repository, and SHALL NOT
show it for any other source. The section SHALL state when the last completed check ran, or that no check
has run, and SHALL carry the control that runs a check. Its position among the screen's sections is fixed
by the `map-download-ui` capability.

#### Scenario: The section is shown for a repository source

- **WHEN** the user opens the map manager screen with a repository source active
- **THEN** the updates section is shown
- **AND** it states when the last completed check ran or that none has run yet
- **AND** it offers the control that runs a check

#### Scenario: The section is absent for the built-in provider

- **WHEN** the active source is the built-in provider
- **THEN** no updates section is shown
- **AND** no check control is offered

#### Scenario: Nothing to update is stated, not left blank

- **WHEN** the last completed check found no updates
- **THEN** the section states that no updates are available
- **AND** it lists no item

#### Scenario: No check has run yet is stated

- **WHEN** no check has completed for the active repository source
- **THEN** the section states that no check has run yet
- **AND** it offers the control that runs a check

### Requirement: The updates section lists what can be updated

The section SHALL list every item that has an update available, SHALL state how many such items there are,
and SHALL give each listed item at least its name, the size of the download and an action that updates that
item. It SHALL carry one action that updates every listed item. It SHALL NOT list an item whose state is
not update available.

#### Scenario: Only updatable items are listed

- **WHEN** one installed database is up to date and another has an update available
- **THEN** only the latter is listed
- **AND** the former is not offered an update action anywhere

#### Scenario: Every updatable item is listed

- **WHEN** one regional database and the basemap have an update available
- **THEN** both are listed in the section
- **AND** its heading reports two items

#### Scenario: Update one item

- **WHEN** the user starts the update of one listed item
- **THEN** that update runs
- **AND** the other listed items stay listed and unchanged

#### Scenario: Update every listed item

- **WHEN** the user starts the update of every listed item
- **THEN** the updates of all listed items are queued

### Requirement: The check result survives a restart

The state a check produced and the time of the last completed check SHALL persist across app restarts. An
app start SHALL show the state the last completed check produced without probing again on the user's
behalf.

#### Scenario: The list is the same after a cold start

- **WHEN** a check found an update and the app is restarted within 24 hours
- **THEN** the updates section lists that update again
- **AND** no comparison request is issued by the start

#### Scenario: A stale result is replaced, not merged

- **WHEN** a new check completes
- **THEN** the state it produced replaces the persisted state entirely
- **AND** an item the new check did not find updatable is no longer listed

### Requirement: Updating every listed item is a queue

The action that updates all listed items SHALL update them one at a time. Each update SHALL be cancellable
on its own, and a failure of one SHALL NOT prevent the remaining items from being updated. The update of
an item SHALL use the download lifecycle the app already uses for map downloads, so the transfer runs in
the foreground service and is not lost when the app is not in the foreground.

#### Scenario: The queue runs the updates one at a time

- **WHEN** the user starts the update of every listed item
- **THEN** one transfer runs at a time
- **AND** the items still waiting keep their update-available state

#### Scenario: A failure does not stop the queue

- **WHEN** the update of one item fails
- **THEN** that failure is reported for that item
- **AND** the remaining items are still updated

#### Scenario: An item is cancelled on its own

- **WHEN** the user cancels the update of one item while the queue runs
- **THEN** that transfer stops and its partial data is removed
- **AND** the remaining items are still updated

#### Scenario: The queue is visible as a download

- **WHEN** an update transfer runs
- **THEN** it appears in the active downloads of the map manager screen with progress and a cancel action
- **AND** the foreground service and its wake lock follow the existing download lifecycle

### Requirement: An update replaces the installation only when it is complete

An update SHALL be transferred into storage separate from the installed data, SHALL be verified against
the metadata it was transferred from, and SHALL replace the installed data only after the transfer
completed and verified. Until then the installed map SHALL stay installed and usable, and an interrupted,
cancelled or failed update SHALL leave the installed data usable and leave no partial data behind.

#### Scenario: The installed map stays usable while the update transfers

- **WHEN** an update of an installed map is running
- **THEN** the installed map is still listed as installed and still renders

#### Scenario: An interrupted update leaves the installed map intact

- **WHEN** an update does not complete
- **THEN** the installed map is still installed and usable
- **AND** the data the incomplete update transferred is removed

#### Scenario: A failed verification installs nothing

- **WHEN** a transferred file does not match the size or checksum the metadata declares
- **THEN** the update fails with a message naming that file
- **AND** the installed map remains installed and usable

### Requirement: An installed update is rendered without an app restart

After an update replaces an installation, the running app SHALL render the updated data and SHALL keep
the map listed with its viewport and its source. An app restart SHALL NOT be required for either.

#### Scenario: The updated data is rendered

- **WHEN** an update has replaced an installed database while the app runs
- **THEN** the map view renders from the updated data without an app restart

#### Scenario: The map keeps its identity

- **WHEN** an update has replaced an installed database
- **THEN** the map is still listed with the same source label
- **AND** its saved viewport still applies

#### Scenario: An updated basemap is rendered

- **WHEN** an update has replaced the installed basemap while the app runs
- **THEN** the map view renders the updated basemap overlay without an app restart

### Requirement: The updates surface is the phone's

The update state SHALL be determined for the whole process, so a session without the map manager screen
sees the same state. The updates section and the check control SHALL be offered in the phone variant only,
because the car surface carries no map-manager screen; the car map SHALL keep rendering whatever is
installed without requiring an update.

#### Scenario: A car-only session keeps rendering

- **WHEN** the app runs with a car session and no phone map-manager screen
- **THEN** the car map renders the installed map data
- **AND** no update is required of the user before it renders

#### Scenario: The car does not offer the controls

- **WHEN** the car session shows its screens
- **THEN** neither the updates section nor the check control is offered there

### Requirement: Data left by an interrupted update is recognised, not mistaken for a map

The app SHALL recognise a directory an earlier update attempt left in the maps tree — the staging directory of a transfer, and the previous installation kept for the duration of a swap — as exactly that. The check SHALL NOT compare such a directory with the repository, SHALL NOT offer it as an update, and SHALL name it in the diagnostics together with the installation it belongs to.

#### Scenario: A leftover is recognised, not compared

- **WHEN** the app finds a directory under the maps tree that an earlier update attempt left behind
- **THEN** it is not compared with the repository
- **AND** it is not offered as an update
- **AND** the diagnostics name it and the installation it belongs to

### Requirement: Leftover staging data is removed on the app-start path only

The app-start path SHALL remove the staging directories an earlier attempt left in the maps tree. A check the user runs on demand SHALL leave them alone, because a transfer of the running process may be writing them.

#### Scenario: The app start removes a leftover

- **WHEN** the app starts and such a leftover is present
- **THEN** the leftover is removed
- **AND** the installation it belonged to is unchanged and still usable

#### Scenario: A check on demand leaves a leftover alone

- **WHEN** the user runs the check on demand
- **THEN** no staging data is removed
- **AND** an update that is transferring at that moment is not disturbed

### Requirement: An update that cannot be installed is reported for its own item

An update that does not complete SHALL be reported on that installation's own row, as a failure carrying the text
for the reason it did not complete, and SHALL leave the installation exactly as it was. A one-item update that
completes SHALL leave no failed row for its item. No listed item's row SHALL carry another item's failure — so
"the update failed" and "the update had not finished" are two statements a surface, and a test, can tell apart.

#### Scenario: A swap that cannot complete is reported, and the installation is unchanged

- **WHEN** a one-item update's transfer has completed and verified but the swap of that slot into the installation
  cannot be made
- **THEN** that item's row reports a failure carrying the text for an installation that could not be made
- **AND** the installation is the one the app had
- **AND** no other listed item's row carries a failure
- **Case**: `app: MapManagerUpdatesQueueTest` "aFailedSwapIsReportedAndKeepsTheInstallation"

#### Scenario: A one-item update that finishes leaves no failure row

- **WHEN** a one-item update completes
- **THEN** its row carries no failure, so an assertion on the installed data that follows cannot be read as a
  failed transfer without the failure having been named
- **Case**: `app: MapManagerUpdatesQueueTest` "updateOneItemRunsOnlyThatOne"

### Requirement: Staging data is never opened or listed as a map

Every consumer that reads the maps tree to open, list, centre on or record a database SHALL apply the
classification `SlotInstall` defines, so the staging directory of a transfer and the previous installation
kept for a swap are never opened or listed as a map. Such a directory SHALL still be named in the diagnostics
together with the installation it belongs to, and the installations beside it SHALL remain discovered.

#### Scenario: A leftover is not the start map

- **WHEN** the phone resolves its start map in a maps tree where `.nordrhein-westfalen.backup` lies beside
  `nordrhein-westfalen` and both carry a `types.dat`
- **THEN** the resolved start map is the installation, never the leftover
- **Case**: `StartMapResolverTest.a leftover staging directory is never the start map`

#### Scenario: A leftover is not an installed map row

- **WHEN** the map manager refreshes its installed list from a scan that reports `.berlin.backup` beside
  `berlin`
- **THEN** the leftover is not in the installed paths
- **AND** it has no row in the installed list
- **AND** the installation beside it stays installed
- **Case**: `MapManagerSourceLabelTest.leftoverStagingIsNotAnInstalledMap`

#### Scenario: The installations beside a leftover are still discovered

- **WHEN** a scan of the maps tree finds installations at the top level and a level below, a `.backup`, an
  `.incoming`, and a dot directory with no staging suffix
- **THEN** the installations — the dot directory included — are the set the app opens or lists
- **AND** the `.backup` and the `.incoming` are the staging data
- **Case**: `MapTreeDiscoveryTest.theInstallationsAreTheOnlyDirectoriesOpened`

#### Scenario: A leftover is reported with the installation it belongs to

- **WHEN** a scan of the maps tree finds `.berlin.backup`
- **THEN** one diagnostics line names the leftover and the installation it belongs to
- **Case**: `MapTreeDiscoveryTest.aLeftoverIsReportedWithTheInstallationItBelongsTo`

#### Scenario: The native installed list is classified by the same rule

- **WHEN** the map manager is handed an installed list from the native scan that holds a `.backup` and an
  `.incoming`
- **THEN** only the installation is kept
- **Case**: `MapTreeDiscoveryTest.aNativeInstalledListIsClassifiedByTheSameRule`
