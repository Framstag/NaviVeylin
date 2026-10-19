# Design — fix-repository-map-delete

Scope: `MapManagerViewModel.kt` (two call sites, two private helpers), one test file, one test-support fake,
one spec requirement. Compiled, so the Gradle gate applies.

## The root cause

`MapDirectory::DeleteDatabase()` (`MapDirectory.cpp:144-174`) removes the files in `MandatoryFiles()`,
`OptionalFiles()` and `MetadataFiles()` and then needs the directory to be empty. A repository installation
always holds three files outside those lists — `db.json` (the app's metadata,
`DatabaseMetadata.METADATA_FILE_NAME`), `.source.json` (`MapSourceMarker.FILE_NAME`) and the mapgen slot's
`ptroutes.dat` (`PTRouteDataFile.cpp:24`) — so the native call removes the 22 files it knows, fails on the
directory, and `nativeDeleteMap` returns `false` without running `RemoveLookupDirectory`/`LookupDatabases`
(`OSMScoutClient.cpp:9234-9241`). One mechanism explains both symptoms in the report: the banner (the `false`
travels unchanged to `MapManagerViewModel.deleteMap`) and the invalid left-over directory (the native delete
already removed the mandatory files).

## The single fix, and where it lives

**Chosen**: `MapManagerViewModel.deleteMapDirectory` completes the removal in the app —
`downloadManager.deleteMap(path)`, and on `false` a recursive removal of the directory tree followed by one
more `downloadManager.deleteMap(path)`, which now sees an absent directory and reports success.

| alternative the evidence eliminated | why |
|---|---|
| patch `MapDirectory::DeleteDatabase()` in the submodule to treat a directory with unknown files as deletable | `guidelines/Design.md` §5 keeps the submodule pinned and clean: an uncommitted submodule change is built neither by CI nor by a fresh clone, so the fix would not exist for anyone else. It is also the wrong shape for `db.json`, which is the app's file, not the library's. |
| patch `MapDownloadManager.deleteMap` in `:osmscout-client-java` (the issue's candidate (b)) | the module's host test stub exports no JNI symbols (`MapDownloadManagerRegisterTest`'s own KDoc), and `deleteMap` calls the native function first: a host test can only observe the throw. The completion has to be assertable, and the retry that drops the registration is a map-manager concern the ViewModel already owns (`refreshInstalledMaps`, the same `deleteMap` action). |
| delete only the three known leftovers (`db.json`, `.source.json`, `ptroutes.dat`) before calling native | a list the app has to keep in step with two other layers, and it leaves every future slot file with the same defect; the recursive removal is what `BasemapManager.deleteDirectory` already does for the basemap one module up. |
| report the native `false` but delete the directory anyway (no second native call) | the directory would be gone while the map manager keeps it in `databaseLookupDirs`; the installed list is served from `GetDatabaseDirectories()` (`OSMScoutClient.cpp:9200`), so the row the user just deleted would stay until an unrelated rescan. |
| leave the source-switch path alone (the issue's "hit worse" case) | it is the same call with the same argument (`applySourceSwitch`'s `unregister` lambda), and a fix that leaves half the surface failing is not the smallest change — it is two behaviours. |

**Why the second native call succeeds on an absent directory** — the reason this fix needs no new JNI entry
point: `MapManager::RemoveFile` returns `true` whether or not the file existed (`File.cpp:42-52` catches only
`filesystem_error`, which `std::filesystem::remove` does not throw for a missing path), so `DeleteDatabase()`
walks its lists, finds nothing to remove, removes the already absent directory and returns `true`, which is what
runs `RemoveLookupDirectory` + `LookupDatabases` in `nativeDeleteMap`.

## Files and mechanisms

| file | change |
|---|---|
| `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` | `deleteMapDirectory(path): Boolean` — native call, then on failure `removeDirectoryTree(Paths.get(path))` and one more native call, reporting whether the directory is gone; `removeDirectoryTree(directory): Boolean` — `Files.walk(...).sorted(reverseOrder())` with `deleteIfExists`, and a guard that the path is inside `storageManager.mapsRootDir`; `deleteMap` and `applySourceSwitch`'s `unregister` call `deleteMapDirectory` |
| `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerDeleteRepositoryMapTest.kt` | two cases: the directory is gone with no banner; the native delete is issued twice and the map leaves the installed list |
| `app/src/test/java/com/framstag/libosmscout/client/FakeMapDownloadManager.kt` | `deleteSucceeds: (String) -> Boolean = { true }` — the case passes `{ path -> !Files.exists(Paths.get(path)) }`, which is the native rule itself |

## Blast radius

The two delete call sites in one ViewModel and nothing else: no native file, no JNI signature, no settings
format, no UI string, no other module. `deleteMap`'s public behaviour changes in exactly one way — it now
reports success for a directory that is gone, whatever the native file lists knew.

## Risk and rollback

The sweep is a recursive delete, which is why it is guarded by the maps root: a path outside it returns the
native failure unchanged, so a wrong path cannot delete anything else. Inside the maps root the directory is
the map's own installation, and `deleteMap` was already documented as deleting that installation. Rollback:
point the two call sites back at `downloadManager.deleteMap` and drop the two helpers.

One measurement to keep honest: the first post-fix run of the new class reported one failure in the last
assertion of the second case; three consecutive `--rerun-tasks` runs of the class afterwards were green
(`evidence/green-on-fix/`). The state the case reads (`installedMapPaths`) is written by `refreshInstalledMaps`
on the same test dispatcher, and no wall-clock wait is involved, so the case is deterministic as written; the
single failure is recorded here rather than explained away.

## Threading, lifecycle, platform

Unchanged. `deleteMapDirectory` runs on the ViewModel's `ioDispatcher` (`guidelines/Design.md` §4), inside the
existing `viewModelScope.launch`. No native call moves to the main thread, no surface, no lifecycle owner and
no platform object is touched.

## Verification

| what | how | evidence |
|---|---|---|
| the defect, on the base commit | `./gradlew :app:testMobileDebugUnitTest --tests "…MapManagerDeleteRepositoryMapTest"` → `tests="2" … failures="2"` | `evidence/red-on-head/` |
| the fix | the same command → `tests="2" … failures="0"` | `evidence/green-on-fix/` |
| the neighbours | `--tests "com.naviveylin.ui.mapmanager.*"` → green before the gate; the gate's own set contains the same suites | `evidence/gate/` |
| the run's gate | `./gradlew test -PforceTests --rerun-tasks` → 704 suites, tests=5676, failures=0 | `evidence/gate/` |
