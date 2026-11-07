# Tasks — fix-basemap-data-format-admission

## 1. Red on HEAD

- [x] 1.1 Add `app/src/test/java/com/naviveylin/data/BasemapRegistrarTest.kt`: with a basemap whose `types.dat`
  names version 26, `BasemapRegistrar.apply` must hand the client the unload value; the readable and the
  slot-nested readable shapes stay registered, and a blank directory unloads.
  *(spec: basemap-loading — Reload basemap after download or delete, scenario "A registered basemap of another
  data format is unloaded")*
- [x] 1.2 Red run on HEAD, with only that case in the tree (the main-source delta stashed):
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.BasemapRegistrarTest"` →
  `4 tests completed, 1 failed`, `BUILD FAILED in 7s`;
  `evidence/red-on-head-BasemapRegistrarTest.xml` `timestamp="2026-10-30T16:03:19.238Z"`,
  `tests="4" skipped="0" failures="1" errors="0"`, failure
  `a basemap whose types.dat names another data format cannot be opened by this build, so the unreadable
  directory must never reach the client (only the unload stays) expected:<[]> but was:<[…/basemap]>`.
  An assertion failure, not a compile failure.
  *(spec: basemap-loading — Reload basemap after download or delete)*

## 2. The admission decision

- [x] 2.1 `app/src/main/java/com/naviveylin/data/BasemapDataFormat.kt` (new): `admit(basemapDirectory,
  expectedVersion)` → `Absent | Admitted | Rejected(installedVersion, expectedVersion)`,
  `databaseDirectoryOf` (the shallow layout first, then `basemap/basemap`), `readFormatVersion` (first
  little-endian `uint32` of `types.dat`, null when missing/short/unreadable), and `logRejection` (one `WARMUP`
  line, database path + two versions, no coordinate).
  *(spec: basemap-loading — Register basemap directory at client initialization, scenarios "Installed basemap
  carries another build's data format", "Data format version is unreadable", "Basemap is nested one level
  below the lookup directory")*

## 3. The two registration sites

- [x] 3.1 `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt`: `admittedBasemapLookupDirectory(basemapDirectory,
  expectedVersion = MapDownloadManager.DATABASE_FORMAT_VERSION)` replaces the `Files.isDirectory` test;
  `provideOSMscoutClient` configures the builder only with an admitted directory, and a refusal logs once.
  *(spec: basemap-loading — Register basemap directory at client initialization)*
- [x] 3.2 `app/src/main/java/com/naviveylin/data/BasemapRegistrar.kt`: `apply` unloads (`""`) a directory the
  probe rejects, and keeps the reload/bump for every call, so a download or switch cannot register unreadable
  data. A path that cannot even be parsed keeps the previous behaviour.
  *(spec: basemap-loading — Reload basemap after download or delete, scenario "A registered basemap of another
  data format is unloaded")*

## 4. The surfaces

- [x] 4.1 `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapViewModel.kt`: `BasemapUiState.outdatedFormat`
  carries the refusal, filled by `outdatedFormat()` on both source branches of `refresh()`.
  *(spec: basemap-ui — Report a basemap this build cannot read)*
- [x] 4.2 `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapSection.kt`: the installed entry shows one line
  naming both versions and the remedy; `basemap_outdated_format` in `values/strings.xml` and
  `values-de/strings.xml`.
  *(spec: basemap-ui — Report a basemap this build cannot read, scenario "Outdated installed basemap")*

## 5. Cases

- [x] 5.1 `app/src/test/java/com/naviveylin/data/BasemapDataFormatTest.kt` (new, plain JVM): absent directory,
  flattened and slot-nested installs of both versions, a truncated `types.dat`, a missing `types.dat`,
  little-endian order, and the shallow-layout preference.
- [x] 5.2 `app/src/test/java/com/naviveylin/di/MapDownloadModuleBasemapAdmissionTest.kt` (new): an admitted
  basemap is configured and says nothing in diagnostics; a refused one configures nothing and leaves exactly
  one `Basemap not registered` line naming both versions; an absent directory configures nothing.
  *(spec: basemap-loading — Register basemap directory at client initialization, scenario "Installed basemap
  carries another build's data format")*
- [x] 5.3 `app/src/test/java/com/naviveylin/ui/mapmanager/BasemapSectionComposeTest.kt`: one case renders
  `BasemapUiState(outdatedFormat = Rejected(dir, 26, 27))` and asserts the notice names both versions and the
  remedy. `waitForText` gains a `substring` flag (the notice is one long line).
  *(spec: basemap-ui — Report a basemap this build cannot read)*
- [x] 5.4 Fixture realigned: `MapManagerSourceSwitchPlanTest.markBasemap` wrote a zero-filled `types.dat` (i.e.
  format version 0), which the new decision correctly refuses; it now writes
  `MapDownloadManager.DATABASE_FORMAT_VERSION` and pads to the requested size.

## 6. Documents

- [x] 6.1 `.pi/skills/update-to-current-libosmscout-master/SKILL.md`: a post-update step — when a bump moves
  `FILE_FORMAT_VERSION`, re-download the basemap (and the regions) on the devices and AVDs that carry installed
  maps, and read a `Basemap not registered: … names data format N, this build reads M` line as stale data, not
  as an app defect.
- [x] 6.2 `guidelines/Build.md` §10j: the AAOS-AVD sentence now says the app refuses such a basemap and names
  the line that says so, instead of leaving the reader with an unexplained failure.

## 7. Verification

- [x] 7.1 Focused round (green): `BasemapDataFormatTest`, `BasemapRegistrarTest`,
  `MapDownloadModuleBasemapAdmissionTest`, `BasemapSectionComposeTest`, `BasemapViewModelTest`,
  `MapManagerSourceSwitchPlanTest`, `MapManagerUpdatesQueueTest`, `MapManagerDeleteRepositoryMapTest`,
  `MapManagerRepositoryTreeTest` — tallies in `evidence/focused-run.txt`.
- [x] 7.2 Falsification: mutate the admission once (accept a mismatched version) and confirm
  `aBasemapThatCarriesAnotherBuildsDataFormatIsNotHandedToTheClient`,
  `aBasemapOfAnotherFormatIsNotConfiguredAndIsReportedOnce` and the probe's two rejection cases fail, then
  restore and re-run forced green — `evidence/falsification.txt`.
- [x] 7.3 The gate: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` over both `:app` flavors and
  the other modules — 6 053 tests across 794 classes, `failures=0 errors=0`, `BUILD SUCCESSFUL in 5m 14s`
  — `evidence/gate.txt`.
- [x] 7.4 Artifact hygiene: `openspec validate fix-basemap-data-format-admission --strict` (valid),
  `openspec doctor` (root ok), `tools/check-doc-routes.sh` (169 section references, 4 skill references, all
  resolve), `:app:checkNoCoordinatesInLogs` (green — the new line carries a path and two integers).
- [x] 7.5 Independent review of the diff (read-only reviewer, the change's evidence by path). Its blocking
  finding was the claim that the native error line disappears; corrected here — the app withholds the overlay
  directory and records its own line, while the client's recursive maps-directory scan can still print the
  native refusal (`design.md` D6). Two notes were applied as well: a `BasemapViewModelTest` case for the state
  seam, and the import order of `MapDownloadModule.kt` (plus the fully-qualified names in
  `MapManagerSourceSwitchPlanTest.markBasemap`). A second, corrections-only round then found that the KDoc of
  `BasemapDataFormat` still promised the native reader would stop failing; corrected there in the same words
  as `design.md` D6.
