# Spec Delta

## Purpose

Consume a libosmscout mapgen map repository: read its region index and per-database metadata, download a database's files with integrity verification, and install the repository's basemap version slot — so a self-hosted repository serves the same map data the built-in provider does.

## ADDED Requirements

### Requirement: Region index drives the available-maps tree

The app SHALL fetch the region index (`names.json`) from the repository's base URL, validate its schema version, and render the hierarchy it defines, showing each node's name in the user's language and falling back to another name the node carries when that language is absent. The index's leaf identifiers SHALL define the placement path of the corresponding database.

#### Scenario: Tree rendered with localized names

- **WHEN** the region index contains nested regions and the user's language is present in a node's names
- **THEN** the available-maps tree shows that node with the name of the user's language

#### Scenario: Preferred language absent falls back

- **WHEN** a node carries no name in the user's language but carries another
- **THEN** the tree shows one of the names the node does carry
- **AND** the node is not hidden

#### Scenario: Unsupported index is reported, not rendered

- **WHEN** the fetched document is not valid JSON, is not a region index, or carries an unsupported schema version
- **THEN** the app reports that the repository's index is unusable together with the URL
- **AND** no partial tree is shown

### Requirement: Database metadata is fetched on demand per leaf

The app SHALL NOT require the repository's whole tree to be reachable in order to show it. For a leaf, the app SHALL fetch that database's metadata (`db.json`) from the leaf's own version slot when the user expands or selects it, and SHALL report its size, database version and change time from that metadata.

#### Scenario: Expanding a leaf fetches its metadata

- **WHEN** the user expands or selects a leaf of the repository's tree
- **THEN** the app requests that leaf's metadata from its version slot
- **THEN** the row shows the size and version the metadata names

#### Scenario: Leaf published for another database version

- **WHEN** the leaf's version slot for this client's database format version answers with not-found
- **THEN** the row states that no database is published for this app's database version
- **AND** no global error banner is shown and the rest of the tree stays usable

### Requirement: Download follows the metadata's file inventory and verifies every file

A repository download SHALL fetch exactly the files the database's metadata names, each verified against the size and CRC-32 the metadata states, from the same base URL the metadata came from. A file whose verification fails SHALL be discarded and SHALL fail the download with a user-actionable message naming that file, and the app SHALL NOT install a directory containing a file it could not verify.

#### Scenario: Verified download completes

- **WHEN** a repository download runs and every file matches the size and CRC-32 of the metadata
- **THEN** the map directory is installed and reported as complete
- **AND** the map appears in the installed list without an app restart

#### Scenario: A corrupt file fails the download

- **WHEN** a downloaded file's CRC-32 does not match the metadata
- **THEN** the download fails with a message naming that file
- **AND** the incomplete map directory is removed
- **AND** no map is reported as installed

#### Scenario: Only the files the metadata names are fetched

- **WHEN** a repository download runs for a database whose metadata names a file set
- **THEN** the download requests exactly those file names
- **AND** does not request a fixed file list or a file the metadata does not name

#### Scenario: Files arrive from the metadata's own version slot

- **WHEN** a database's metadata was fetched from a leaf's version slot
- **THEN** every data file of that database is requested from the same version slot
- **AND** no file is requested from a different host than the region index's base URL

#### Scenario: The type configuration is installed last

- **WHEN** a repository download writes the files the metadata names
- **THEN** the database's type configuration is written last
- **AND** a directory that is complete apart from it is not recognised as a map

#### Scenario: Files are not installed before the type configuration

- **WHEN** a repository download is interrupted after some files but before the type configuration was written
- **THEN** the affected directory is not recognised as an installed map

### Requirement: A repository database's directory name comes from the index, not the display name

An installed repository database SHALL live in a directory named after the index leaf's identifier path (for example `europe-germany-berlin`), so that the installed map's identity does not depend on the user's language or on a localized display name.

#### Scenario: Install with a localized display name

- **WHEN** the user installs the leaf `berlin` while the app's language is German
- **THEN** the installed directory is named from the leaf's identifier path
- **AND** the row still displays the German name

#### Scenario: Language switch does not orphan an installed map

- **WHEN** a repository map is installed
- **WHEN** the user switches the app's language
- **THEN** the map is still reported as installed
- **AND** its directory is unchanged

### Requirement: The repository source installs its own basemap version slot

When the repository source is active, the app SHALL read the basemap availability manifest (`basemap/index.json`), select the newest published basemap version this client can read, download that version's files from `basemap/v<version>/` with the same integrity verification as a regional database, and install it as the basemap. A missing manifest SHALL be reported as "basemap unavailable" and never as an error.

#### Scenario: Newest supported basemap version is selected

- **WHEN** the manifest lists several basemap versions and some of them are unreadable by this client
- **THEN** the newest version this client can read is offered for download

#### Scenario: Basemap slot installs and renders

- **WHEN** a basemap slot download completes and verifies
- **THEN** the basemap is installed at the basemap directory
- **AND** the basemap is reloaded so the current view renders it without an app restart

#### Scenario: Missing manifest is not an error

- **WHEN** the repository does not publish a basemap manifest, or the request fails with not-found
- **THEN** the basemap is reported as unavailable for this source
- **AND** the app shows no error dialog and the map manager stays usable

### Requirement: Cancelling or failing a repository download leaves no partial data

A cancelled or failed repository download SHALL leave no partially installed map directory and SHALL leave any previously installed basemap untouched. A subsequent attempt at the same database SHALL start from a clean state.

#### Scenario: Cancel during download

- **WHEN** the user cancels a repository download
- **THEN** the transfer stops and the partial directory is removed
- **AND** the entry returns to its available state

#### Scenario: Failed basemap replacement keeps the old basemap

- **WHEN** a repository basemap download or verification fails
- **THEN** the previously installed basemap remains installed and renderable

### Requirement: The repository source performs no update check

The repository source SHALL NOT compare an installed database's change time or version against the repository's, and SHALL NOT present an update-available state. The repository's version slots SHALL be read only to locate a database for download.

#### Scenario: Reinstalling an installed database is the user's action

- **WHEN** a database installed from the repository exists and the repository publishes a newer change time for it
- **THEN** the app shows the map as installed and offers no update action
- **AND** no comparison request is issued on opening the map manager or on refresh
