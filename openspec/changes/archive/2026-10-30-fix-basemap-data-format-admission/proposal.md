# Proposal — fix-basemap-data-format-admission

## Why

**Observed (#23, `§99`, on `emulator-5554`, Automotive_Distant_Display, x86_64, SDK 33).** The app opened its
installed region (`openDatabase(nordrhein-westfalen-27-20260820-0826) -> true`) while the **basemap** was
refused by the native reader at every start:

```
E NaviVeylin: File '…/files/maps/basemap/basemap/types.dat' does not have the expected format version!
              Actual 26, expected: 27
W NaviVeylin: Cannot open db '…/files/maps/basemap/basemap'!
```

Map rendering worked (the region carries the tiles), but everything that needs the basemap — sea/land
background, borders, country names, and the phone's "basemap present" paths — silently degraded, and the only
trace was that native error line, repeated per start on stderr, which reads as an app defect rather than as
stale data.

**Root cause, one sentence.** `MapDownloadModule.provideOSMscoutClient` decides the basemap lookup directory on
the mere existence of the directory (`app/src/main/java/com/naviveylin/di/MapDownloadModule.kt:60-65`: `Files.isDirectory(basemapDir)`),
and `BasemapRegistrar.apply` hands any directory it is given to the client unconditionally — so no app-side
decision ever compares the data format version the basemap carries with the one this build's reader expects,
and the refusal happens inside the native open where no surface and no diagnostics reader can see it.

**Red on HEAD.** `app/src/test/java/com/naviveylin/data/BasemapRegistrarTest.kt` —
`aBasemapThatCarriesAnotherBuildsDataFormatIsNotHandedToTheClient`: with a basemap whose `types.dat` names
version 26, the registrar must hand the client the unload value; HEAD hands it the directory.
`tests="4" skipped="0" failures="1" errors="0"`, `timestamp="2026-10-30T16:03:19.238Z"`
(`evidence/red-on-head-BasemapRegistrarTest.xml`). The failure is an assertion, not a compile or parse failure.
Repro:

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.BasemapRegistrarTest"
```

**Surface count.** `grep -rln "setBasemapLookupDirectory\|withBasemapLookupDirectory" app/src/main` → 2
registration sites (`MapDownloadModule.kt`, `BasemapRegistrar.kt`) plus the fake that records them
(`FakeOSMScoutClient.kt`); `grep -rln "BasemapRegistrar(" app/src/test` → 12 fixture files; the cases that assert
a registered directory are 3 files (`BasemapViewModelTest`, `MapManagerSourceSwitchPlanTest`,
`MapDownloadModuleClientConfigTest`). Surface: 2 registration sites changed, 1 case added per site, 1 fixture
realigned (`MapManagerSourceSwitchPlanTest.markBasemap` wrote a zero-filled `types.dat`, i.e. version 0).

## What Changes

- **One admission decision** — `app/src/main/java/com/naviveylin/data/BasemapDataFormat.kt` reads the data
  format version the installed basemap carries (the first little-endian `uint32` of `types.dat`, the only
  database file with a version header — `app/src/main/cpp/libosmscout/guidelines/FileFormatVersion.md`) and
  returns `Absent`, `Admitted` or `Rejected(installedVersion, expectedVersion)`. Both on-device layouts are
  probed: the flattened `basemap/types.dat` the tar.gz install produces and the slot-nested
  `basemap/basemap/types.dat` the AAOS AVD carries. The expected version is
  `MapDownloadManager.DATABASE_FORMAT_VERSION`, the bridge's single source for that number.
- **The startup path registers only readable data** — `provideOSMscoutClient` configures the builder's basemap
  lookup directory through `admittedBasemapLookupDirectory`, so a refused basemap is never handed to the native
  build as the overlay directory. The refusal is recorded once, on the `WARMUP` diagnostics tag, naming both
  versions and the remedy. The native reader's own refusal line for the same directory can still appear: the
  client also scans the maps directory for databases and opens what it finds there, including a stale basemap
  directory (residual, `design.md` D6 — that scan is native code in the submodule).
- **The runtime path takes the same decision** — `BasemapRegistrar.apply` unloads a directory it cannot read
  instead of handing it over, so a download or a source switch cannot re-register unreadable data.
- **The map manager's basemap section says why** — `BasemapUiState.outdatedFormat` carries the refusal and
  `BasemapSection` shows one line naming both versions and the remedy (delete, then download again), with the
  string in `values/strings.xml` and `values-de/strings.xml`.
- **The next person reads the remedy where the bump happens** — the post-update checklist of the
  `update-to-current-libosmscout-master` skill gains the re-download step, and `guidelines/Build.md` §10j's
  AAOS-AVD note stops calling the refusal an unexplained defect.
- **Specs** — `basemap-loading`: the registration and the reload requirements are MODIFIED (registration
  conditioned on the data format; the reload path unloads unreadable data); `basemap-ui`: one ADDED requirement
  ("Report a basemap this build cannot read"). Every shipped scenario of both MODIFIED requirements is carried
  unchanged, with one scenario added to each.

## Impact

Files:

- `openspec/specs/basemap-loading/spec.md`, `openspec/specs/basemap-ui/spec.md` (via this change's deltas)
- `app/src/main/java/com/naviveylin/data/BasemapDataFormat.kt` (new — the admission decision and the version read)
- `app/src/main/java/com/naviveylin/data/BasemapRegistrar.kt` (unload unreadable data)
- `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` (`admittedBasemapLookupDirectory`)
- `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapViewModel.kt` (`outdatedFormat`)
- `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapSection.kt` (the notice)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` (`basemap_outdated_format`)
- `app/src/test/java/com/naviveylin/data/BasemapDataFormatTest.kt` (new)
- `app/src/test/java/com/naviveylin/data/BasemapRegistrarTest.kt` (new — the red case)
- `app/src/test/java/com/naviveylin/di/MapDownloadModuleBasemapAdmissionTest.kt` (new)
- `app/src/test/java/com/naviveylin/ui/mapmanager/BasemapSectionComposeTest.kt` (one case)
- `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerSourceSwitchPlanTest.kt` (fixture realigned)
- `.pi/skills/update-to-current-libosmscout-master/SKILL.md` (post-bump step)
- `guidelines/Build.md` §10j (one sentence, the AAOS-AVD note)

Guidelines read for this change: `guidelines/MapRendering.md` §16b (basemap stylesheet/registration side),
`guidelines/Design.md` §5 (the bridge's two sides — no native method is added and the submodule stays
untouched), `guidelines/Logging.md` §1 (what a log line may carry: a database path, never a coordinate),
`guidelines/Build.md` §2a (the iteration protocol), §6a (the JNI/classloader rule the new cases follow).

**Shape.** Additive: no existing behaviour is removed, no file layout changes, no native method and no
submodule edit. The only behaviour that changes is one no case relied on — registering a basemap this build
cannot open. Rollback is the revert of this change; a device whose basemap is refused by the new decision is
exactly the device the native reader refused before.

## Out of scope

- **Automatic re-download of a refused basemap.** Detecting the mismatch is cheap and local; fetching several
  hundred megabytes because a data format moved is a product decision (metered connections, unasked data
  spend) and would need its own requirement. The remedy stays the existing delete + download control, which
  the notice names.
- **Why the AAOS AVD's basemap is nested (`maps/basemap/basemap`).** `BasemapManager.flattenSingleSubdir`
  flattens only a single-child archive, so an archive with more than one top-level entry stays nested; both
  shapes are admitted here, and repairing the install layout would need the archive itself.
- **Version checks for regional databases.** A repository download is addressed by version slot
  (`RepositoryUrlPlanner`, spec `map-repository-source`), so it cannot install another version's data.
