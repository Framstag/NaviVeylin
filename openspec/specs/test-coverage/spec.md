# test-coverage Specification

## Purpose

The build system measures JVM unit test code coverage with Kover across all modules, producing per-module and aggregated HTML/XML reports that CI publishes as artifacts — giving the project visibility into test coverage without gating builds.

## Requirements

### Requirement: All test-bearing modules produce coverage metrics
Every module with JVM unit tests SHALL produce coverage metrics for the unit test execution of an
invocation that requests coverage (including Robolectric tests running on the host JVM): `app`, `auto`,
and `core` measured by the Kover Gradle plugin; `osmscout-client-java` (pure Java module without the
Kotlin plugin, which Kover cannot measure) measured by the standard Gradle JaCoCo plugin. An invocation
that does not request coverage SHALL execute the same test classes with the coverage instrumentation
agent detached, and the tests' results SHALL be unaffected by the choice.

#### Scenario: App module records coverage
- **WHEN** `koverHtmlReport` runs for the `:app` module after an invocation that requested coverage
- **THEN** an HTML report SHALL exist under the app module's Kover report output directory with non-empty coverage for app sources

#### Scenario: Kotlin library modules record coverage
- **WHEN** Kover report tasks run for `:auto` and `:core`
- **THEN** each module SHALL produce a report covering its own production sources

#### Scenario: Java module records coverage via JaCoCo
- **WHEN** `:osmscout-client-java:jacocoTestReport` runs
- **THEN** an HTML and an XML report SHALL exist with non-empty coverage counters for the module's classes

#### Scenario: Instrumentation is attached only when coverage is requested
- **WHEN** a unit-test invocation runs without requesting coverage
- **THEN** the test JVM is launched with no coverage instrumentation agent attached

#### Scenario: Test results are the same with and without the agent
- **WHEN** the same suite runs once with and once without the coverage instrumentation agent
- **THEN** the sets of failing classes are identical, and the suite still completes in a single
  invocation in both cases

#### Scenario: A coverage-bearing invocation feeds the reports without re-running tests
- **WHEN** a coverage report task runs after an invocation that requested coverage
- **THEN** the report is produced from that invocation's execution data without executing the tests
  again

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

- **WHEN** a basemap directory holds map data and the screen asks for its description
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

### Requirement: Aggregated multi-module report
A single aggregated coverage report SHALL combine the unit test coverage of the Kotlin modules `app`, `auto`, and `core` so the whole Kotlin surface can be judged from one report (the pure-Java module is reported separately by JaCoCo).

#### Scenario: Aggregated report covers all Kotlin modules
- **WHEN** the aggregated Kover report task completes
- **THEN** the report SHALL list classes from `app`, `auto`, and `core` and SHALL show a combined line/branch coverage percentage without double-counting shared classes

### Requirement: HTML and XML report formats
Kover SHALL emit reports in both HTML (human-readable) and XML (machine-readable, e.g. for CI/artifact consumption) formats.

#### Scenario: HTML report generated
- **WHEN** a Kover report task completes
- **THEN** an HTML report file SHALL exist in the configured report output directory

#### Scenario: XML report generated
- **WHEN** a Kover report task completes
- **THEN** an XML report file SHALL exist in the configured report output directory

### Requirement: Generated code excluded from metrics
Coverage metrics SHALL exclude generated code that is not hand-written production logic: `BuildConfig`, `R` classes, and Dagger/Hilt/KSP-generated classes.

#### Scenario: Generated classes absent from report
- **WHEN** coverage reports are generated
- **THEN** entries for `BuildConfig`, `R` (and R-style resource classes), and Dagger/Hilt/KSP-generated classes SHALL NOT appear in the report's coverage metrics

### Requirement: CI publishes coverage artifacts
The CI workflow SHALL generate the coverage reports after its unit test step and upload the HTML and XML outputs as a downloadable workflow artifact.

#### Scenario: CI workflow produces coverage artifact
- **WHEN** CI runs its unit test step successfully
- **THEN** a coverage report step SHALL run after it and a workflow artifact containing HTML and XML coverage reports SHALL be uploaded

#### Scenario: CI stays green without thresholds
- **WHEN** CI runs with coverage enabled
- **THEN** the workflow SHALL NOT fail the build due to coverage percentages — measurement is report-only
