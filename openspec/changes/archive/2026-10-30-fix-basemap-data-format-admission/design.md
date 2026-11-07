# Design — fix-basemap-data-format-admission

## D1 — The decision is taken in the app, not in the bridge or the submodule

`TypeConfig::GetDatabaseFileFormatVersion(directory)` exists as a query API
(`app/src/main/cpp/libosmscout/guidelines/FileFormatVersion.md`, upstream commit `8c55d7bfe`), but it is a C++
API with no JNI entry point in this project's bridge, and adding one would mean editing
`libosmscout-client-java/src/OSMScoutClient.cpp` inside the submodule — the tree the update skill keeps clean
and the pin rules forbid mixing (`guidelines/Design.md` §5).

The app already knows the number it expects: `MapDownloadManager.DATABASE_FORMAT_VERSION` is the bridge's single
constant for it, used to address provider listings and repository version slots (spec
`map-download-infrastructure` — "Database format version has one source of truth"). The read itself is four
bytes at a fixed offset, which `java.nio` does without any native call, so the whole decision lives in
`com.naviveylin.data.BasemapDataFormat`, where a plain JVM case can exercise every shape.

## D2 — The version is the first little-endian `uint32` of `types.dat`, and an undecidable version admits

`TypeConfig::StoreToDataFile()` writes the version as the file's first field and `LoadFromDataFile()` reads it
with `FileScanner::ReadUInt32()`, which assembles the four bytes little-endian
(`libosmscout/src/osmscout/io/FileScanner.cpp:969`). That layout is the database's only version header — the
data and index files carry none — so a missing, truncated or unreadable `types.dat` says nothing about
compatibility.

Three answers follow, and only one of them refuses:

| on disk | decision |
|---|---|
| no basemap directory | `Absent` — register nothing |
| `types.dat` names this build's version | `Admitted` — register the directory |
| `types.dat` names another version | `Rejected(installed, expected)` — register nothing, report once |
| `types.dat` missing/short/unreadable | `Admitted` — the native reader stays the authority |

The last row is deliberate: this seam removes exactly the case it can decide. Refusing on an unreadable file
would turn a file-permission or partially-written directory into a *different* silent degradation, and the
native open already reports those.

## D3 — Both on-device layouts are probed, and the shallow one decides

Two shapes exist:

- **flattened** — `{mapsDir}/basemap/types.dat`: what `BasemapManager.downloadAndExtract` installs, because it
  flattens an archive with a single top-level directory (`flattenSingleSubdir`);
- **slot-nested** — `{mapsDir}/basemap/basemap/types.dat`: what the AAOS AVD of issue #23 carries, and what an
  archive with more than one top-level entry leaves behind, since flattening only applies to a single child.

The native client's basemap scan finds a database at any depth below the lookup directory (the issue's own log
reports `…/maps/basemap` *and* `…/maps/basemap/basemap`), so the probe tries `types.dat` beside the lookup
directory first and one level down second. When both exist, the shallow one decides; a disagreement between the
two copies is not resolvable here and is not invented into a rule.

## D4 — No automatic re-download; the refusal is reported and the remedy is the existing control

Detecting a mismatch is local and free. Fetching the basemap is neither: the archives are hundreds of megabytes
and the download is the user's data spend, so an automatic re-fetch on every start would be a product decision
with its own requirement (metered connections, and a device that would download again whenever the provider's
slot lags). The change therefore stops at the refusal plus two reports: one diagnostics line naming both
versions and the remedy, and one line in the map manager's basemap section naming the same, with the existing
delete + download controls right beside it.

## D5 — The refusal happens per registration, never per render

Both registration sites take the same decision — the startup builder configuration and
`BasemapRegistrar.apply`, the one place a running session changes what the basemap is. Nothing in the render
path consults it: a basemap is either registered with the client or not, and the renderer keeps its current
behaviour. This keeps the change out of `guidelines/MapRendering.md` §1–§18 entirely; the only rendering-adjacent
document that mentions the condition is §16b's styling side, which is unaffected.

## D6 — Residual: the client's own scan of the maps directory still opens a stale basemap directory

The admission withholds the basemap *overlay* directory; it cannot unsee the directory in the maps tree. The
client registers the maps root as its lookup directory (`withMapLookupDirectories(mapsDir)`) and
the native side scans it recursively for any directory holding a `types.dat`
(`libosmscout-client/src/osmscoutclient/MapManager.cpp`), then opens each one
(`DBThread::OnDatabaseListChanged`, `libosmscout-client/src/osmscoutclient/DBThread.cpp:272`). The basemap
directory is exempted from that loop only while a basemap lookup directory is configured and canonicalises to
the same path (`DBThread.cpp:277-286`, the guard that keeps the world's tile data out of every render), so a
directory this build refuses is scanned as a regular database and the native reader prints its own
`File '…/types.dat' does not have the expected format version!` pair for it.

That is where issue #23's quoted lines came from: `Cannot open db '…/files/maps/basemap/basemap'!` is the
*regular* loop's message, while `LoadBasemap` would have said `Cannot open basemap db`. The nested
directory is not covered by the exemption even on HEAD, so this residual predates the change and the change
does not widen it: the refusal moves the flattened case from `Cannot open basemap db` to `Cannot open db`, the
same number of native lines either way, and the app's own line is the one that names the versions.

Removing the residual needs a native change (exempt the basemap subtree from the database scan, or skip a
directory whose format version is not the reader's) and belongs to the submodule, which this change keeps
untouched (`guidelines/Design.md` §5). It is therefore out of scope and recorded here rather than promised in
a scenario.

## D7 — What the diagnostics line may carry

One line, on the `WARMUP` tag, with a database path and two integers:
`Basemap not registered: <dir>/types.dat names data format 26, this build reads 27 — download the basemap again`.
A path is identity, and the numbers are a format version — no coordinate, no position, so the line satisfies
`guidelines/Logging.md` §1 and the `checkNoCoordinatesInLogs` gate. It is emitted from the two call sites, not
from the probe, so the pure probe stays free of Android dependencies and a plain JVM case can exercise it.
