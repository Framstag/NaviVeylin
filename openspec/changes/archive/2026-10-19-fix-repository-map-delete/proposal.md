# Deleting an installed map leaves no half-deleted directory behind

## Why

Issue **#119** (`§185 A map installed from a repository cannot be deleted — the native DeleteDatabase() does not
know the app-added db.json or the slot's ptroutes.dat`), observed on `emulator-5554` during the device run of
`add-map-update-check` (tasks 7.2/7.4): deleting the repository-installed `europe-germany-bremen` row showed
`Failed to delete map: europe-germany-bremen` and left the directory holding two files — `db.json` and
`ptroutes.dat` — after the other 22 files of the slot were removed. The next attempt logged
`Can't use db …/europe-germany-bremen, some mandatory files are missing.` The row stayed, the directory was
invalid, and the available list excludes installed maps, so the user could neither use nor re-download it.

**Root cause — the native delete removes only the files it knows and then needs the directory to be empty.**
`MapDirectory::DeleteDatabase()` (`app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/MapDirectory.cpp:144-174`)
deletes `MandatoryFiles()`, `OptionalFiles()` and `MetadataFiles()` and then calls `RemoveFile(GetDirStr())`,
which fails on a non-empty directory (`MapDirectory.cpp:171` is the `Failed to remove db directory completely`
line from the log). A repository installation always holds files outside those three lists: the app's own
`db.json` (`core/src/main/java/com/naviveylin/core/mapsource/DatabaseMetadata.kt:73`, written by
`DatabaseDownloader.writeMetadata`), the source marker `.source.json`
(`core/src/main/java/com/naviveylin/core/mapsource/MapSourceMarker.kt:61`), and the mapgen slot's
`ptroutes.dat` (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/db/PTRouteDataFile.cpp:24`, in none of
the three lists). So the native call can never complete for a repository map, and
`MapDownloadManager.deleteMap` (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java:312-314`)
returns that `false` straight to `MapManagerViewModel.deleteMap`, which raises the banner and leaves the
half-deleted directory in place.

**The red test** — `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerDeleteRepositoryMapTest.kt`, both
cases fail on the base commit `7ddf7ab8c83474218473626bc3ff4a054d426cca` with plain assertion failures (no JNI
involved):

```
tests="2" skipped="0" failures="2" errors="0" timestamp="2026-10-19T06:39:52.013Z"
  deletingARepositoryInstallationRemovesItsDirectory
    java.lang.AssertionError: the directory the native delete cannot empty must be gone after the app's own removal
  theDeleteIsRepeatedSoTheMapManagerDropsTheRegistration
    java.lang.AssertionError: the map manager is asked again once the directory is gone, which is what drops its registration
      expected:<[…/maps/europe-germany-bremen, …/maps/europe-germany-bremen]> but was:<[…/maps/europe-germany-bremen]>
```

XML as produced (excerpt: the `expected:`/`was:` line elided to its directory-name tail; the canonical copy is
this change's `evidence/red-on-head/`). The third case in the file —
`aDirectoryOutsideTheMapsRootIsNotSwept` — is the guard the fix introduces, added after the red run, which holds
two cases and predates it.

**Owning capability**: `map-download-infrastructure` — its requirement *Delete removes map manager
registration* ("the map's directory is removed from the map manager's lookup directories … AND the map no
longer appears in the installed list") and *Clean redownload* are the ones the defect breaks. **Owning
guideline sections**: `guidelines/Design.md` §3 "ViewModel & state" (the delete action and its explicit error
state live in `MapManagerViewModel`) and §5 "Native boundary" (the native file lists are the library's
contract; the app completes its own installation's removal), `guidelines/Design.md` §11 "Testing" and
`guidelines/Build.md` §6 (the host stub exports no JNI symbols, so the completion is asserted where a host test
can reach it).

## What changes

1. **`MapManagerViewModel.deleteMapDirectory(path)` completes the removal.** When the native delete reports
   failure, the app removes the directory tree itself — the same recursive removal
   `BasemapManager.deleteDirectory` already performs for the basemap — and then asks the map manager once more:
   on an absent directory `DeleteDatabase()` succeeds, because `RemoveFile` treats a missing file as removed
   (`File.cpp:42-52`), and that second call is what runs `RemoveLookupDirectory` + `LookupDatabases`
   (`OSMScoutClient.cpp:9234-9239`). The delete is reported as successful once the directory is gone.
2. **The sweep is bounded to the maps root.** A path outside `storageManager.mapsRootDir` is not the app's to
   remove recursively, so a native failure there stays a failure.
3. **Both delete call sites use it**: the map manager row (`deleteMap`) and the source switch's unregister step
   (`applySourceSwitch`), which reported a failure for every repository map it tried to clean up.

## Impact

| file | change |
|---|---|
| `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` | `deleteMapDirectory` + `removeDirectoryTree`; `deleteMap` and the source switch's `unregister` call the first |
| `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerDeleteRepositoryMapTest.kt` | the two red cases, and the guard case for the maps-root bound |
| `app/src/test/java/com/framstag/libosmscout/client/FakeMapDownloadManager.kt` | the fake's `deleteMap` answer becomes a parameter, so a case can mirror the native rule |
| `openspec/specs/map-download-infrastructure/spec.md` | one requirement added by the delta ("Deleting an installation removes the whole directory") |

No native file, no JNI signature, no settings format and no UI string changes: the banner keeps its wording
and simply stops appearing for a repository map. **Rollback**: point the two call sites back at
`downloadManager.deleteMap` and drop the two private helpers — the spec delta then names a rule the tree does
not satisfy, which is the state HEAD is in.

This change touches compiled files, so the gate applies: the focused `:app` cases, the `ui.mapmanager` slice,
and the full `./gradlew test -PforceTests --rerun-tasks`.
