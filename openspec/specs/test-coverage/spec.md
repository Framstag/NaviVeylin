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
test can reach — the local overrides and the submodule's Java client API beside them. Its coverage SHALL be
measured per provenance, one coverage-row key each, and each key SHALL stay at or above the row's floor. A
class that only the native side can reach SHALL NOT be counted as coverable, and no case SHALL be asked to
execute native code.

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

- **WHEN** the module's own JaCoCo report is read after its suite has run
- **THEN** the percentage the coverage row reads under the module's own key is at or above the row's floor
- **Case**: `coverage-floor-probe: the own-code instruction coverage reaches the 70% floor`

#### Scenario: The client's own methods are exercised

- **WHEN** one of `OSMScoutClient`'s convenience methods runs on a client whose native entry points answer from
  a fake
- **THEN** the method hands the native call the arguments and the request generation it promises, and it ends
  the request it began even when the native call fails
- **Case**: `OSMScoutClientWrappersTest.aStandaloneSearchBeginsAndEndsItsOwnRequest`

#### Scenario: The settings builder stores what it is handed

- **WHEN** a caller chains the builder's setters before `build()`
- **THEN** every setter returns the same builder, and a repeated custom-POI registration appends instead of
  replacing the first
- **Case**: `OSMScoutClientBuilderTest.everySettingIsChainableAndKeepsItsValue`

#### Scenario: The two provenances are two row keys

- **WHEN** the coverage summary is written from the module's own report and its vendored report
- **THEN** the summary carries a percentage for each provenance under its own key, and a missing vendored report
  is refused instead of read as the module's mixed number
- **Case**: `inputs#splitsTheJavaModulesCoverageByProvenance`

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

### Requirement: A module's suite reaches the coverage row's floor

A module's unit suite SHALL cover the classes a host test can reach, so the instruction coverage that the
sensor registry's coverage row reads from that module's own report does not fall below that row's floor. A case
added for that coverage SHALL assert a behaviour the capability owns, never that a line ran; a class only the
native side can reach SHALL NOT be covered from a host test; and a case that builds a component with long-lived
background work SHALL release it before it returns.

#### Scenario: The coverage probe passes on the change

- **WHEN** the coverage row's arithmetic — the last instruction counter of the module's Kover report against the
  row's 70 % floor — is applied to the `auto` module's report
- **THEN** the module SHALL be at or above the floor
- **Case**: `coverage-floor-probe#theAutoModulesInstructionCoverageReachesTheFloor`

#### Scenario: The free-driving screen's template is the map-only template

- **WHEN** the free-driving screen is asked for its template
- **THEN** the template SHALL be a `NavigationTemplate`, the one template with no content slot; its map
  action strip SHALL carry two actions, and its action strip the zoom-in and zoom-out actions in that order
- **Case**: `FreeDrivingScreenHostTest#theTemplateIsTheBareNavigationTemplateCarryingItsActions`

#### Scenario: A started screen takes the session surface and a stopped one releases it

- **WHEN** the free-driving screen reaches the started state and later the stopped state
- **THEN** it SHALL call the session surface's attach exactly once on start and its detach exactly once on
  stop, so no screen holds the surface while the session draws through another
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenTakesTheSessionSurfaceAndStopsReleasing`

#### Scenario: A started screen observes fixes and map-data revisions

- **WHEN** the shared location source publishes a fix and the basemap notifier publishes a revision while the
  free-driving screen is started
- **THEN** the screen SHALL process both without faulting and still answer a `NavigationTemplate`
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenObservesFixesAndBasemapRevisions`

#### Scenario: The screen votes for free driving on entry

- **WHEN** the free-driving screen is constructed
- **THEN** it SHALL publish the car's free-driving flag, so the shared indicator and the ongoing notification
  follow the surface that is driving
- **Case**: `FreeDrivingScreenHostTest#anEnteredScreenVotesTheFreeDrivingFlagOn`

#### Scenario: A destroyed screen drops the road status it published

- **WHEN** the shared status provider carries the car's road/speed status while a free-driving screen is
  started, and the screen is destroyed
- **THEN** it SHALL clear that status, so no road from an ended session survives into the notification
- **Case**: `FreeDrivingScreenHostTest#aDestroyedScreenDropsTheRoadStatusItPublishedAndItsSurface`
