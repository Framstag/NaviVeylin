# A leftover staging directory is staging data where it is read, not only in the update check

## Why

Issue **#116** (`§182 A leftover staging directory of an update is discoverable as an installed map until it is
swept`), found while applying `add-map-update-check` (task 3.8): an update stages a transfer in
`.<name>.incoming` and keeps the previous installation in `.<name>.backup` beside its target inside
`filesDir/maps/`. A `.backup` holds a complete database and therefore carries the `types.dat` that
`InstalledMaps.findDatabaseDirectories` treats as the marker of an installed map, so a swap whose cleanup
failed in an earlier session is discovered as a map until `MapUpdates.sweepStagingLeftovers` removes it on the
next app start — and the sweep runs on the IO dispatcher from `NaviVeylinApp.onCreate`, so a screen that reads
the tree early can see it first.

**Root cause — one mechanism.** The shared discovery reports *every* directory holding a `types.dat`, and no
consumer of it applies the staging vocabulary the maps tree already owns. `InstalledMaps.scan`
(`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/InstalledMaps.java:43-58`) adds any
directory with a `types.dat` and knows nothing about `SlotInstall`
(`core/src/main/java/com/naviveylin/core/mapsource/SlotInstall.kt:43-60`), so all five readers of it —
`StartMapResolver.resolve`, `MapCanvasViewModel.initMap`, the car warmup in
`AutoServiceModule.provideAutoClientProvider`, `MapDataRegistrar.apply` and `firstInstalledAutoMapBbox`
(`auto/src/main/java/com/naviveylin/auto/AutoInitialViewport.kt`) — return the leftover as a database to
open, list, centre on and record. The map manager's installed list is the sixth reader and reads the tree a
second way, through the native `MapManager::LookupDatabases` scan
(`app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/MapManager.cpp:76-84`, which walks
`recursive_directory_iterator` for `TypeConfig::FILE_TYPES_DAT`), so
`MapManagerViewModel.refreshInstalledMaps` builds a phantom row from it too.

Only the update check classifies today: `MapUpdateCheck.discoveredStaging`
(`core/src/main/java/com/naviveylin/core/mapsource/MapUpdateCheck.kt:172-175`) uses
`SlotInstall.stagingTargetOf` and reports what it leaves out — so the app already has the vocabulary, applied
at one of seven places.

**The red test** — two cases, both failing on the base commit `9aac52b5d9778a6d5cc2a92cb95a35dc9c7eb00e` with
plain assertion failures (no JNI, no device):

```
TEST-com.naviveylin.data.StartMapResolverTest.xml
  tests="9" skipped="0" failures="1" errors="0" timestamp="2026-10-22T12:18:16.786Z"
  a leftover staging directory is never the start map
    org.junit.ComparisonFailure: expected:<…/files/maps/[nordrhein-westfalen]>
                                      but was:<…/files/maps/[.nordrhein-westfalen.backup]>

TEST-com.naviveylin.ui.mapmanager.MapManagerSourceLabelTest.xml
  tests="6" skipped="0" failures="1" errors="0" timestamp="2026-10-22T12:18:16.786Z"
  leftoverStagingIsNotAnInstalledMap
    java.lang.AssertionError: the leftover is not an installed map
```

Copies: this change's `evidence/red-on-head/` and `.pi/issue-loop/items/116/`.

**Owning capability**: `map-updates` — its requirement *Data left by an interrupted update is recognised, not
mistaken for a map* states the general rule ("the app SHALL recognise … as exactly that") and its check half
today; the delta adds the discovery half. **Owning guideline sections**: `guidelines/Design.md` §9 "Data,
persistence & search" (the maps tree, `SlotInstall`, and the rule that a leftover is *recognised* rather than
hidden), §5 "Native boundary" (the native scan that feeds the map manager's list), §11 "Testing" and
`guidelines/Build.md` §6 (host cases over a real temporary tree; the stub exports no JNI).

## What changes

1. **One classification seam, `core/…/MapTreeDiscovery`.** It scans through the existing
   `InstalledMaps.findDatabaseDirectories`, splits the result with `SlotInstall.isStaging`, keeps both halves
   in `Found(installed, staging)`, and writes one identity-only diagnostics line per leftover
   ("map discovery: leftover staging `.berlin.backup` of berlin — not opened or listed"). The scan itself is
   unchanged: the leftover is still *discovered* and still *reported*, which is what the owner asked for.
2. **The five discovery consumers read `Found.installed`**: the start map, the phone canvas's
   additional-database batch, the car warmup's database batch, the post-update re-open, and the car's initial
   viewport.
3. **The map manager's installed list is classified by the same rule**
   (`MapTreeDiscovery.installedOnly`), because it comes from the native scan rather than from the shared
   discovery, and its phantom row is the symptom the issue names first.

`MapUpdates` keeps its own classification: its discovered set is what `MapUpdateCheck` splits, and the check's
own report line is the one the spec promises for it. Feeding it the filtered set would delete the leftover
report the check already emits.

## Impact

| file | change |
|---|---|
| `core/src/main/java/com/naviveylin/core/mapsource/MapTreeDiscovery.kt` | new: `under`, `installedOnly`, `isStaging`, `reportLine` |
| `app/src/main/java/com/naviveylin/data/StartMapResolver.kt` | discovery through `MapTreeDiscovery.under` |
| `app/src/main/java/com/naviveylin/data/MapDataRegistrar.kt` | discovery through `MapTreeDiscovery.under` |
| `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` | discovery through `MapTreeDiscovery.under` |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` | discovery through `MapTreeDiscovery.under` |
| `auto/src/main/java/com/naviveylin/auto/AutoInitialViewport.kt` | discovery through `MapTreeDiscovery.under` |
| `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` | installed list through `MapTreeDiscovery.installedOnly` |
| `app/src/test/…/data/StartMapResolverTest.kt` | one case: a leftover is never the start map |
| `app/src/test/…/ui/mapmanager/MapManagerSourceLabelTest.kt` | one case: a leftover is not an installed map row |
| `core/src/test/…/core/mapsource/MapTreeDiscoveryTest.kt` | four cases: the split, the report line, the basemap exclusion, the native list |
| `openspec/specs/map-updates/spec.md` | one requirement added by the delta |

No native file, no JNI signature, no settings format, no UI string and no sweep-timing change. **Rollback**:
point the six call sites back at `InstalledMaps.findDatabaseDirectories` / `downloadManager.installedMaps` and
drop `MapTreeDiscovery` — the spec delta then names a rule only the update check satisfies, which is the state
HEAD is in.

This change touches compiled files, so the gate applies: the focused `:core` and `:app` cases, and
`./tools/loop/gate.sh` once over the change's own pathspec.

**Surface counted before the plan** (`grep -rn "findDatabaseDirectories" --include=*.kt --include=*.java
app/src/main auto/src/main core/src/main osmscout-client-java/src`; `… "downloadManager.installedMaps" app/src/main`):
6 production call sites of the shared discovery, 1 production reader of the native installed list, 4
`androidTest` fixtures that call the primitive directly (unchanged: they build the tree they scan), and 1
existing consumer that already classifies (`MapUpdates` → `MapUpdateCheck`). So: 1 helper added, 6 call sites
rewritten, 2 red cases + 4 new cases added, and no fixture touched.

## Corrections after review round 1

The reviewer refused the first round on one deviation: `MapTreeDiscovery.installedOnly` dropped the leftover
silently (`discovered.filterNot { isStaging(it) }` — no `reportLine`, no `DiagnosticsLog`), while the
requirement's second sentence says such a directory "SHALL still be named in the diagnostics together with the
installation it belongs to", and the item's fix candidate says the leftover is reported while it is not
listed. The class's own doc claimed the opposite ("a consumer that has to *report* the staging data … still
sees it"), and the delta's fifth scenario asserted only the kept list.

Changed in round 2, and nothing beyond the reviewer's named corrections plus its two NOTEs:

1. `installedOnly(discovered, report = null)` now emits one `reportLine` per dropped leftover through a sink
   that defaults to `DiagnosticsLog`, exactly as `under(...)` does; `MapManagerViewModel.refreshInstalledMaps`
   keeps its one-argument call and gets the default sink.
2. `MapTreeDiscoveryTest.aNativeInstalledListIsClassifiedByTheSameRule` asserts the two report lines beside
   the kept list — the diagnostics clause now has a case on the native-list path.
3. The requirement names "the classification the update machinery defines" instead of `SlotInstall`, so it
   survives a rename of the seam; the fifth scenario gains the diagnostics `AND`.
4. `guidelines/Design.md` §9 now carries the rule: every reader that opens, lists, centres on or records a
   database goes through `MapTreeDiscovery`, and the leftovers it drops are named in the diagnostics.

The class doc's update-check claim is corrected with it: the update check scans the tree itself
(`MapUpdates.installedDatabaseDirectories`) and reports on its own; it never calls this class.

surface: 1 production function given a reporting sink, 1 case rewritten (its assertion extended), 2 spec
texts realigned (the delta and the shipped requirement), 1 guideline section given the rule —
`grep -rn "installedOnly" app core auto --include=*.kt`

## Open items considered and left out

- `InstalledMaps` itself stays unclassified. Hiding the staging directories inside the primitive is the
  dot-prefix filter the owner refused, and it would remove the leftover from the update check's report, which
  reads the primitive through `MapUpdates`.
- The native `MapManager::LookupDatabases` scan stays as it is. It is the library's scan of a lookup
  directory, the app's own list is classified at its reader, and a submodule change is built by neither CI nor
  a fresh clone (`guidelines/Design.md` §5).
- The sweep's ordering against early screens is not changed. Once no reader opens a leftover, the ordering
  stops mattering for what the user sees; the sweep still removes the data.
