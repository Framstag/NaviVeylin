## ADDED Requirements

### Requirement: The pure-Java module's suite covers the classes it ships

The `:osmscout-client-java` module's JVM suite SHALL exercise every class the module compiles that a host
test can reach — the local overrides and the submodule's Java client API beside them — so that the module's
measured instruction coverage stays at or above the coverage row's floor. A class that only the native side
can reach SHALL NOT be counted as coverable, and no case SHALL be asked to execute native code.

#### Scenario: The POI category table is exercised

- **WHEN** the table that maps a POI search category to its OSM type names is read
- **THEN** the table carries one entry per documented category id, and every entry names at least one
  well-formed OSM type
- **Case**: `PoiCategoriesTest.everyCategoryMapsToAtLeastOneOsmType`

#### Scenario: The lane-turn ids match the native enum

- **WHEN** a lane turn is marshalled back from the numeric id the engine reports
- **THEN** every constant comes back from its own numeric id
- **Case**: `LaneTurnTest.everyLaneTurnRoundTripsThroughItsNumericId`

#### Scenario: The navigation callbacks' defaults are callable

- **WHEN** a listener implements no callback of its own
- **THEN** every callback of the navigation and download listeners can be invoked without effect, so an
  implementation that overrides one method still receives the rest
- **Case**: `ClientValueTypesTest.theCallbackInterfacesCarryDefaultMethodsTheClientCanLeaveAlone`

#### Scenario: The installed basemap is described

- **WHEN** a basemap directory holds map data and the settings screen asks for its description
- **THEN** the file count and the summed size are the directory's own
- **Case**: `BasemapManagerInstalledTest.theInstalledInfoCountsFilesAndSumsTheirSizes`

#### Scenario: A downloaded basemap is installed

- **WHEN** a provider serves a basemap archive and the download completes
- **THEN** the archive's content is installed at the basemap directory and the completion names that
  directory
- **Case**: `BasemapManagerTest.testSuccessfulDownloadInstallsTheBasemap`

#### Scenario: The provider's listing is what the screen offers

- **WHEN** the basemap probe reads the provider's directory listing
- **THEN** the archives the provider's listing carries are offered, and `BaseMap-2026-02-23.tar.gz` is
  the latest basemap
- **Case**: `BasemapManagerTest.testFetchAvailableBasemapsReadsTheDirectoryListing`

#### Scenario: The installed-map scan finds every database directory

- **WHEN** the shared scan walks a maps root
- **THEN** every directory holding `types.dat` at any depth is a map, and a directory without it is not
- **Case**: `InstalledMapsTest.everyDirectoryHoldingTypesDatIsAMap`

#### Scenario: A failing download reports instead of escaping

- **WHEN** the download worker's first fallible step fails
- **THEN** the listener is handed the failure and the message names the step that failed, so a worker that
  dies is a download error the caller sees rather than an uncaught throwable on a thread nobody awaits
- **Case**: `MapDownloadManagerListingTest.aFailedNativePreparationIsReportedThroughTheListener`

#### Scenario: The module's coverage is measured against the row's floor

- **WHEN** the module's JaCoCo report is read after its suite has run
- **THEN** the module's measured instruction coverage is at or above the coverage row's floor
- **Case**: `coverage-floor-probe: the module instruction coverage reaches the 70% floor`
