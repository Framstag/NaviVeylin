# Tasks

## 1. The detector in `:core` (spec `map-updates` — an installed database that predates the shipped stylesheet is reported)

- [x] 1.1 Add `TypeConfigNames` in `core/src/main/java/com/naviveylin/core/mapsource/` — reads an installed
  database's `types.dat` and answers its type-name set, mirroring `TypeConfig::LoadFromDataFile`
  (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/TypeConfig.cpp:1470`): the format version first, then
  the feature count (skipped), then each type record (skipped via its own field set) and its feature names. KDoc
  names the owning spec (`map-updates`) and design D1. Verify: `./gradlew :core:testDebugUnitTest --tests
  "com.naviveylin.core.mapsource.*"` compiles and the golden-fixture case below reads the expected count.
- [x] 1.2 Add `DeclaredTypes` — the names a bundled `map.ost` declares (`TYPE <name>` lines, comments and the
  `Feature`/`TYPE` distinction respected), reading the file `AssetCopier.ensureStylesheets()` already mirrors.
  Verify: a fixture `map.ost` yields exactly its declared names; the packaged file is read through the same
  path the app uses on device (`AssetCopier.stylesheetsDirectory()`, the seam the view model reads).
- [x] 1.3 Add `StaleInstallCheck` — the pure comparison: a database whose name set lacks declared names answers
  `Missing(count, sample)`, one that carries every name answers `Current`, and a `types.dat` whose format
  version is not `MapDownloadManager.DATABASE_FORMAT_VERSION` answers `Unknown`. Verify: cases named per
  scenario, below.
- [x] 1.4 Tests in `core/src/test/java/com/naviveylin/core/mapsource/` with a golden `types.dat` fixture cut
  from a real installation (`core/src/test/resources/mapsource/golden-types.dat`, the libosmscout test region's
  format-27 file `app/src/main/cpp/libosmscout/Tests/data/testregion/types.dat`) and a small fixture `map.ost`
  — one case per delta scenario:
  `StaleInstallTest#aDatabaseShortOfTheDeclaredTypesIsReported` (scenario 1, the count is the missing number),
  `StaleInstallTest#aDatabaseCarryingEveryDeclaredNameIsNotReported` (scenario 2),
  `StaleInstallTest#aForeignFormatVersionIsNotJudged` and
  `StaleInstallTest#theGoldenTypesDataFileYieldsItsNameSet` (scenario 3 — the second case is what fails on a
  `FILE_FORMAT_VERSION` bump instead of every name reading as missing). Verify: 4 cases green —
  `TEST-com.naviveylin.core.mapsource.StaleInstallTest.xml` tests=4 failures=0 (2026-11-14T01:37:27.530Z),
  copied to `.pi/issue-loop/items/880/TEST-StaleInstallTest-continuation.xml`.
- [x] 1.5 Revert-check (one mutation): make the version gate answer `Current` instead of `Unknown` and confirm
  `StaleInstallTest#aForeignFormatVersionIsNotJudged` fails; restore, then re-run forced with `-PforceTests
  --no-build-cache`. Record the failing case name and the XML tally in this task. **Recorded**: the mutation
  fails `StaleInstallTest#aForeignFormatVersionIsNotJudged` (`expected:<Unknown> but was:<Current>`, tests=4
  failures=1, 2026-11-14T01:32:47.096Z — `.pi/issue-loop/items/880/TEST-StaleInstallTest-revert-check-mutation.xml`),
  and the restored tree re-runs green (tests=4 failures=0, 2026-11-14T01:37:27.530Z — the tallies above).

## 2. The phone surface (spec `map-updates` — the same requirement, the map manager's half)

- [x] 2.1 `MapManagerViewModel` computes the verdict for each installed database inside its existing
  `withContext(ioDispatcher)` section and carries it in the immutable UI state (spec `map-updates` — scenario 1;
  design D2, D3: computed on read, never persisted, never on the main thread). Verify:
  `MapManagerStaleRowTest#theInstalledRowNamesTheStylesheetItPredates` reads the state the view model produces
  for a stale directory, with the count in it — green (tests=3 failures=0, 2026-11-14T01:32:17.408Z,
  `.pi/issue-loop/items/880/TEST-com.naviveylin.ui.mapmanager.MapManagerStaleRowTest.xml`), and the whole
  map-manager package stays green after the constructor seam moved (19 suites, 115 cases, 0 failures,
  `--tests "com.naviveylin.ui.mapmanager.*"`, 2026-11-14T01:36Z).
- [x] 2.2 `MapManagerScreen` renders the notice on the installed row, with the existing download action as the
  offered remedy; the current case renders the row exactly as before (spec `map-updates` — scenario 2). Strings
  added to `values/strings.xml` and `values-de/strings.xml`. Verify: the Compose case
  `MapManagerStaleNoticeComposeTest` (this change's own file, beside the other map-manager cases) asserts the
  notice's text for a stale verdict and its absence for a current one; both string tables carry the key
  (`strings.xml` parity is a build gate). **Recorded**: `theInstalledRowNamesTheShortfallAndItsCount` (which
  also asserts the row offers the `Download` action) and `aCurrentInstallationRowCarriesNoNotice` green
  (tests=2 failures=0, 2026-11-14T01:35:26.647Z,
  `.pi/issue-loop/items/880/TEST-com.naviveylin.ui.mapmanager.MapManagerStaleNoticeComposeTest.xml`).
- [x] 2.3 Build both flavors with all three ABIs, no new warnings from the changed files:
  `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`. Verify: both succeed and the changed files
  add no Kotlin warning (the repo's warning-free-build rule). **Recorded**: `BUILD SUCCESSFUL` for both
  assemblies (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`), no `w:` line from the changed files.
- [ ] 2.4 Suite gate over the tests this change can affect, with the counts read from
  `build/test-results/*/`: `./gradlew :core:testDebugUnitTest :app:testMobileDebugUnitTest
  :app:testAutomotiveDebugUnitTest -PforceTests --no-build-cache`. Verify: 0 failures, and the map manager and
  `:core` mapsource suites report the new cases. **Status: left to the gate child** — the run deferred this
  item's single gate to a fresh child that runs it after the reviewer's findings are applied (issue #837), so it
  is not run here; the focused cases above (`StaleInstallTest`, `MapManagerStaleRowTest`,
  `MapManagerStaleNoticeComposeTest`) are the ones this phase owed.

## 3. On-device measurement (device-gated)

- [ ] 3.1 Phone recipe `guidelines/BuildNative.recipes.md` §10e, run twice on `emulator-5554`: (a) with the
  stale set installed (`files/andorra-stale` from the 2026-11-13 run is such a set), the map manager's installed
  row names the map and the count equals the 225-name shortfall measured on 2026-11-13; (b) after the
  re-download from the repository source, the row carries no notice and `adb logcat -s NaviVeylin | grep -c
  "Unknown types in"` counts only the 2 by-design names. Verify: both UI dumps recorded in the change's
  evidence directory, with the row's text verbatim. **Status at planning time: NOT RUN** — record the blocker
  rather than claiming a pass. **Status now: still NOT RUN** — no device is attached to this run, so this task
  is the item's open verification, not a pass.

## 4. Guidelines and bookkeeping

- [x] 4.1 The guideline that owns the map manager's installed-map row state gains the new row state, and
  `guidelines/BuildNative.recipes.md` §10e names it as the user-visible half of the log-line recipe it already
  carries. **Recorded**: no `guidelines/UIPhone.md` section owns this surface — the plan's pointer was stale
  (`tools/look.sh 'map manager' --path guidelines/UIPhone.md` → 0 matches), so the row state is recorded where
  the map manager's screens are: the parity table's map-manager entry in `guidelines/UI.why.md`, whose new
  "Why the stale-map notice is phone-only" section names the row, the wording and the download remedy. The
  rendering half is §16b of `guidelines/MapStyling.md`, which gains the sentence naming the on-device copy the
  detector reads (the copy itself is unchanged). Verify:
  `tools/check-doc-routes.sh` and `tools/check-doc-values.sh` pass — `check-doc-routes: all 340 section
  references resolve, all 7 skill references resolve`, `LOOP-CHILD-TURNS-MAX: 6 roles, prose matches config`.
- [x] 4.2 `openspec validate report-a-stale-map-install --strict`, and `tools/check-change-readiness.sh
  openspec/changes/report-a-stale-map-install` reports `LOOP-CHANGE-READY: ok`. Verify: both are green at
  planning time and again at archive time. **Recorded**: `Change 'report-a-stale-map-install' is valid` and
  `LOOP-CHANGE-READY: ok` (re-run after this phase's edits, 2026-11-14).
