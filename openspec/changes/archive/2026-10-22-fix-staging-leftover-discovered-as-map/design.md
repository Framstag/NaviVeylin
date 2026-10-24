# Design — fix-staging-leftover-discovered-as-map

Scope: one new `:core` seam, six production call sites, three test files, one spec requirement. Compiled, so
the Gradle gate applies.

## The root cause

`InstalledMaps.scan` (`InstalledMaps.java:43-58`) answers "every directory under the root that holds a
`types.dat`", and it mirrors the native `MapManager::LookupDatabases`
(`MapManager.cpp:76-84`). A `.backup` of a swap holds a complete database, so it *is* such a directory: the
leftover is indistinguishable from an installation to every reader of that scan. `SlotInstall` already names
the difference (`stagingTargetOf`, `SlotInstall.kt:43-60`) and the maps tree already carries it — the update
check applies it (`MapUpdateCheck.discoveredStaging`) — but no *discovery* consumer did. One mechanism
explains every symptom in the report: the phantom start map, the phantom row in the map manager, the
additional database the renderer opens, the car's warmup batch, and the car viewport centred on the
installation a failed swap replaced.

## The single fix, and where it lives

**Chosen**: one classification seam in `:core`, `MapTreeDiscovery`, that every reader of the maps tree goes
through. `under(...)` scans through the unchanged `InstalledMaps.findDatabaseDirectories`, splits the result
with `SlotInstall.isStaging`, returns both halves (`Found.installed`, `Found.staging`) and reports one
identity-only diagnostics line per leftover. `installedOnly(...)` applies the same rule to the native installed
list the map manager is handed, and names each leftover it drops through the same `reportLine` sink. The five
shared-discovery consumers read `.installed`; the map manager reads `installedOnly(downloadManager.installedMaps)`.

| alternative the evidence eliminated | why |
|---|---|
| filter inside `InstalledMaps.findDatabaseDirectories` (a dot-prefix filter) | it hides the leftover from *every* reader, including `MapUpdates` → `MapUpdateCheck`, which must still report it; the owner's instruction on `add-map-update-check` was that the analysis understands those directories rather than hides them, and a dot-prefix test is not the classification `SlotInstall` already owns (a dot directory with no staging suffix is an installation, and a prefix test would drop it) |
| filter inside `InstalledMaps` with the staging suffixes re-implemented in Java | two copies of the vocabulary in two modules, and the Java module cannot call `SlotInstall`; the second copy is the one that drifts |
| classify inside `MapUpdates`'s discovery and hand the filtered set to the check | the check's leftover report has no input left: `updateCheckLines` would lose the line the spec promises ("the diagnostics name it and the installation it belongs to") and `MapUpdateCheck`'s two staging cases would fail |
| patch the native `MapManager::LookupDatabases` to skip dot directories | a submodule change is built by neither CI nor a fresh clone (`Design.md` §5), and it changes what the *library* reports rather than what the app opens — the map manager's own reader is one line away |
| fix the ordering instead: make the app-start sweep finish before any screen reads the tree | the sweep is safe only before a transfer of the process exists, and the reader that runs first is not always the app-start path (a car-only session warms up from `AutoServiceModule`); once no reader opens a leftover, the ordering stops being a correctness question. The sweep is unchanged |
| drop the leftover from discovery and let the next sweep report it | a leftover is removed on the app-start path only, so a directory left by a *failed* delete or a read-only tree would be invisible in the logs; the requirement asks for it to be named |

## Files and mechanisms

| file | change |
|---|---|
| `core/…/mapsource/MapTreeDiscovery.kt` | `Found`, `under(mapsRoot, excludeDirectory, report)`, `installedOnly(discovered, report)`, `isStaging`, `reportLine`; the default sink of both readers is `DiagnosticsLog` with tag `MapTreeDiscovery` |
| `app/…/data/StartMapResolver.kt` | `MapTreeDiscovery.under(mapsRoot, mapsRoot.resolve("basemap")).installed` |
| `app/…/data/MapDataRegistrar.kt` | the same, with `RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME` |
| `app/…/di/AutoServiceModule.kt` | the same, over `File(filesDir, "maps").toPath()` |
| `app/…/ui/map/MapCanvasViewModel.kt` | the same, before `StartMapSelection.databaseDirectoryFor` |
| `auto/…/AutoInitialViewport.kt` | the same, before `.sorted()` — so the bbox loop cannot pick a leftover that sorts first |
| `app/…/ui/mapmanager/MapManagerViewModel.kt` | `MapTreeDiscovery.installedOnly(downloadManager.installedMaps)` — the default sink reports the leftovers it drops |
| `core/src/test/…/MapTreeDiscoveryTest.kt` | the split (nested, `.backup`, `.incoming`, plain dot), the report line, the basemap exclusion, the native list |
| `app/src/test/…/StartMapResolverTest.kt` | the red case: a leftover that sorts first is never the start map |
| `app/src/test/…/MapManagerSourceLabelTest.kt` | the red case: a leftover is neither a path nor a row |

## Blast radius

Seven production files, all of them readers of the maps tree; one new class; no native file, no JNI signature,
no settings format, no UI string, no change to the sweep or to `MapUpdates`. The one behavioural change is
that a directory named `.<name>.incoming` / `.<name>.backup` is no longer opened, listed, recorded or centred
on. Everything else the scan reports is unchanged — including a dot directory with no staging suffix, which is
why the classification is `SlotInstall`'s and not a prefix test.

## Risk and rollback

The classification is `SlotInstall.stagingTargetOf`'s own rule, so a name it does not call staging is still an
installation: a map genuinely named `.backup`-suffixed is the only way to lose a database, and that name is what
`SlotInstall` already refuses to install into. The diagnostics line carries a directory name and a target name
only — no coordinate (`guidelines/Logging.md` §3). Rollback: point the six call sites back at
`InstalledMaps.findDatabaseDirectories` / `downloadManager.installedMaps` and delete `MapTreeDiscovery`; the
delta then names a rule only the update check satisfies, which is the base commit's state.

## Correction after review round 1

**What the review found.** `installedOnly` was written as a bare `filterNot`: it removed the leftover from the
map manager's list and said nothing about it. The requirement this change adds has two clauses — never opened
or listed, *and* still named in the diagnostics — and only the first was true on that path. Two supporting
claims were false with it: the class doc said a consumer that has to report the staging data still sees it
through this class (the update check never calls it; it scans the tree itself), and the fifth scenario
asserted only the kept list, so the diagnostics clause had no case there.

**The fix, one mechanism and no fork.** Both readers now go through one path: partition the discovered names
with `SlotInstall.isStaging`, report each dropped name with `reportLine` through a sink that defaults to
`DiagnosticsLog`, return the kept names. `under(...)` already had exactly that shape, so the correction is
`installedOnly` adopting it rather than a new rule — one call and one assertion, no option list.

**Why the alternatives the review named were eliminated.** Narrowing the requirement to "the scan it
performs" would leave `MapManagerViewModel` dropping the leftover silently, which contradicts both the
requirement's own second sentence and the item's fix candidate ("treat staging data as not a map to open or
list *while still reporting it*"). Abandoning the change leaves the phantom row the issue names first. The
third alternative — keeping the silent drop and accepting it — is the deviation the reviewer refused.

**Blast radius.** One production function gains a parameter and three lines, its one call site is unchanged
(the parameter defaults), one test case grows one assertion, the delta and the shipped requirement are
realigned, and `guidelines/Design.md` §9 carries the rule. No call site, no other consumer, no native code.

**Rollback.** Revert `installedOnly` to `discovered.filterNot { isStaging(it) }` and drop the `AND` from the
fifth scenario; the classification half of the change stands, and the map manager is back to a silent drop.
