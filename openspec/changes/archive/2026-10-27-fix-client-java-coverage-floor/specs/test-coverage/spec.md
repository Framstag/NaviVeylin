## ADDED Requirements

### Requirement: The pure-Java module's suite covers the classes it ships

The `:osmscout-client-java` module's JVM suite SHALL exercise every class the module compiles that a host
test can reach — the local overrides and the submodule's Java client API beside them — so that the module's
measured instruction coverage stays at or above the coverage row's floor. The number SHALL be the row's own:
the last root `INSTRUCTION` counter of
`osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml`, which
`tools/build-inputs.sh`'s `xml_instruction_percentage` writes into the coverage summary, compared against
`tools/sensors/sensors.json`'s `threshold` by `tools/sensors/metrics/coverage.sh`. A class that only the
native side can reach (the JNI surface whose library has no stub on this module's test path) SHALL NOT be
counted as coverable, and nothing in this requirement asks a case to execute native code.

#### Scenario: The POI category table is exercised

- **WHEN** the table that maps a POI search category to its OSM type names is read
- **THEN** every documented category id is a key of the table, every category names at least one type, a
  caller cannot edit the table through the array it was handed, and an unknown category has no type names
- **Case**: `PoiCategoriesTest.everyCategoryMapsToAtLeastOneOsmType`

#### Scenario: The lane-turn ids match the native enum

- **WHEN** a lane turn is marshalled back from the numeric id the engine reports
- **THEN** every constant comes back from its own id, the ids follow the native order, and an id the engine
  does not know resolves to `UNKNOWN`
- **Case**: `LaneTurnTest.everyLaneTurnRoundTripsThroughItsNumericId`

#### Scenario: The navigation callbacks' defaults are callable

- **WHEN** a listener implements no callback of its own
- **THEN** every callback of the navigation and download listeners can be invoked without effect, so an
  implementation that overrides one method still receives the rest
- **Case**: `ClientValueTypesTest.theCallbackInterfacesCarryDefaultMethodsTheClientCanLeaveAlone`

#### Scenario: The installed basemap is described

- **WHEN** a basemap directory holds map data and the settings screen asks for its description
- **THEN** the file count and the summed size are the directory's own, the human size names its unit, and a
  missing directory yields no description, no size and "not installed"
- **Case**: `BasemapManagerInstalledTest.theInstalledInfoCountsFilesAndSumsTheirSizes`

#### Scenario: A downloaded basemap is installed

- **WHEN** a provider serves a basemap archive and the download completes
- **THEN** the archive's content is installed at the basemap directory, the completion names that directory,
  and an archive that wraps its content in one directory is flattened into it
- **Case**: `BasemapManagerTest.testSuccessfulDownloadInstallsTheBasemap`

#### Scenario: The provider's listing is what the screen offers

- **WHEN** the basemap probe reads the provider's directory listing
- **THEN** every `.tar.gz` entry is offered newest first and the other files are not, and a provider that
  fails or cannot be reached yields no entry rather than an exception
- **Case**: `BasemapManagerTest.testFetchAvailableBasemapsReadsTheDirectoryListing`

#### Scenario: The installed-map scan finds every database directory

- **WHEN** the shared scan walks a maps root
- **THEN** every directory holding `types.dat` at any depth is a map, a directory without it is not, and the
  excluded directory is skipped with its whole subtree
- **Case**: `InstalledMapsTest.everyDirectoryHoldingTypesDatIsAMap`

#### Scenario: A failing download reports instead of escaping

- **WHEN** the download worker's first fallible step fails
- **THEN** the listener is handed the failure and the message names the step that failed, so a worker that
  dies is a download error the caller sees rather than an uncaught throwable on a thread nobody awaits
- **Case**: `MapDownloadManagerListingTest.aFailedNativePreparationIsReportedThroughTheListener`

#### Scenario: The module's coverage is measured against the row's floor

- **WHEN** the module's JaCoCo report is read after its suite has run
- **THEN** the module's instruction coverage is at or above the coverage row's floor, measured with the row's
  own arithmetic
- **Case**: `coverage-floor-probe: the module instruction coverage reaches the 70% floor`
