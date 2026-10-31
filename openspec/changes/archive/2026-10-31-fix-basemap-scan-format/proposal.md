# Proposal

## Why

Issue #716: the client's own scan of the maps directory still opens a stale basemap directory as a regular
database, so the native format-version refusal survives the app-side admission. The app withholds the basemap
*overlay* directory when the installed basemap's `types.dat` names a data format this build does not read
(`BasemapDataFormat`), but it cannot unsee the directory in the maps tree: the client registers the maps root
as its lookup directory and the native scan in `libosmscout-client` walks it recursively for any directory
holding a `types.dat`, then registers each one. `DBThread` exempts the basemap directory from that loop only
while a basemap lookup directory is configured and canonicalises to the same path — exactly the setting the
app leaves unset when it refuses — so the refused directory is registered as a regular database and the native
reader prints its own `File '…/types.dat' does not have the expected format version!` pair on the next open.

The app-side admission therefore removes one symptom (no basemap overlay) and leaves the other (a database in
the registered set that can never open, plus its native error pair).

## What Changes

- The recursive scan in `MapManager::LookupDatabases()` reads each candidate directory's data format version
  (`TypeConfig::GetDatabaseFileFormatVersion`) before it constructs a `MapDirectory`, and leaves out a
  directory whose version differs from this build's `FILE_FORMAT_VERSION`. One `Warn` line names the skipped
  directory and both versions; the directory joins the scan's existing `invalidCount`.
- A directory whose version cannot be read — a missing, short or unreadable `types.dat` — stays published
  exactly as today. The native reader remains the authority on such a directory, and the scan does not turn a
  read error into a refusal.
- No app-side behaviour changes: the admission in `BasemapDataFormat` stays as it is, and the basemap overlay
  path (`withBasemapLookupDirectory`) is untouched.

## Impact

- Affected specs: `native-database-open` — one added requirement and three scenarios.
- Affected code: `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/MapManager.cpp` (the
  scan), `app/src/main/cpp/libosmscout/Tests/src/MapManagerTest.cpp` (two cases). The submodule commit and the
  gitlink bump carry the change; no Kotlin, no JNI signature, no rendering behaviour changes.
- Verification: two native Catch2 cases over real temporary directories (red first, then green); the app-side
  gate for the bump; one `adb logcat -s NaviVeylin` check on the emulator with a stale-version basemap in place
  (`guidelines/BuildNative.md` §10d).
